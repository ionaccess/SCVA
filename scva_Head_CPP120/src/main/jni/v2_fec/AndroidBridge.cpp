#include "AndroidBridge.h"
#include "Transport.h"
#include "SCVA_Fldigi_Interface.h"
#include <android/log.h>
#include <chrono>
#include <cstdio>
#include <atomic>
extern signed char* txDataBuffer;
extern int txDataBufferLength,txCounter;
extern void flushTxSoundBuffer();
namespace scva_v2 {
static uint64_t clockMs(){return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
static JNIEnv* rxEnv=nullptr;
static jclass rxClass=nullptr; // Local ref borrowed only during rxCProcess.
static jmethodID rxCharacterMethod=nullptr;
static jmethodID traceMethod=nullptr;
static JNIEnv* txEnv=nullptr;
static jclass txClass=nullptr;
static jmethodID txTraceMethod=nullptr;
static thread_local bool txContext=false;
static bool selected=false,supported=false,debug=false;
static int txCodec=2;
static std::atomic<unsigned long> procedureCounter{0};
static unsigned long rxProcedure=0;
static unsigned long txProcedureActive=0;
static bool isV2SupportedThorMode() {
    return active_modem &&
           (active_modem->get_mode() == MODE_THOR22 ||
            active_modem->get_mode() == MODE_THOR50x1);
}
static const char* v2ModemName() {
    return active_modem && active_modem->get_mode() == MODE_THOR50x1 ? "THOR50x1" : "THOR22";
}
static std::string proc(unsigned long p){char b[32];std::snprintf(b,sizeof(b),"P%06lu",p);return b;}
static std::string hex(const Bytes& b,size_t begin,size_t end){static const char h[]="0123456789ABCDEF";std::string s;end=std::min(end,b.size());for(size_t i=begin;i<end;++i){if(!s.empty())s+=' ';s+=h[b[i]>>4];s+=h[b[i]&15];}return s;}
static void trace(const char* text);
static void trace(const std::string& text);
static void encodeTrace(void*,const Bytes& input,const Bytes& word) {
    const std::string p=proc(txProcedureActive);
    const CodecDescription* description=codecDescription(txCodec);
    if(word.empty()) trace("[SCVA-V2-FEC][PROC="+p+"][TX][STEP=03 RS-ENCODE] codec="+std::to_string(txCodec)+" n=128 k="+std::to_string(description->k)+" parity="+std::to_string(description->parity)+" applicationCapacity="+std::to_string(description->applicationCapacity)+" RS_INPUT="+hex(input,0,input.size()));
    else trace("[SCVA-V2-FEC][PROC="+p+"][TX][STEP=03 RS-ENCODE] CODEWORD[000..127]="+hex(word,0,128));
}
static void traceString(const std::string& text) {
    JNIEnv* env=txContext ? txEnv : rxEnv;
    jclass cls=txContext ? txClass : rxClass;
    jmethodID method=txContext ? txTraceMethod : traceMethod;
    if(!env || !cls || !method || env->ExceptionCheck()) return;
    // JNI NewStringUTF requires Modified UTF-8. Diagnostic strings can
    // contain arbitrary RF/parser bytes, so escape non-ASCII/control bytes
    // at this common logging boundary without changing parser data.
    std::string safe;
    for(unsigned char byte : text) {
        if(byte >= 32 && byte <= 126) safe.push_back(char(byte));
        else if(byte == '\n' || byte == '\r' || byte == '\t') safe.push_back(char(byte));
        else { char b[5]; std::snprintf(b,sizeof(b),"\\x%02X",byte); safe+=b; }
    }
    jstring value=env->NewStringUTF(safe.c_str());
    if(!value) return;
    env->CallStaticVoidMethod(cls,method,value);
    env->DeleteLocalRef(value);
}
static void trace(const char* text) { traceString(std::string(text ? text : "")); }
static void trace(const std::string& text) { traceString(text); }
static void wire(void*,const std::string& token) {
    std::string escaped; escaped.reserve(token.size());
    for(unsigned char c:token) {
        if(c=='\\') escaped+="\\\\";
        else if(c=='\"') escaped+="\\\"";
        else if(c>=32 && c<=126) escaped+=char(c);
        else { char b[8]; std::snprintf(b,sizeof(b),"\\x%02X",c); escaped+=b; }
    }
    trace("[SCVA-V2-FEC][RX][STEP=06 RX-WIRE] candidate chars="+std::to_string(token.size())+" record=\""+escaped+"\"");
}
static uint64_t budgetStart=0;
static unsigned logCount=0;
// Boundary diagnostics are rate-limited but remain visible even when the
// optional verbose preference is off; no payload/codeword data is emitted.
static bool allowLog(){uint64_t now=clockMs();if(now-budgetStart>=1000){budgetStart=now;logCount=0;}return logCount++<16;}
static void message(void*,const Bytes& bytes,uint8_t type,uint64_t id) {
    const std::string p=proc(rxProcedure);
    trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=13 REASSEMBLY] MSG="+std::to_string(id)+" status=COMPLETE recoveredLength="+std::to_string(bytes.size())+" expectedSegments=COMPLETE");
    trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=14 DELIVERY] MESSAGE RECEIVED AND VERIFIED MSG="+std::to_string(id)+" len="+std::to_string(bytes.size())+" type="+std::string(type==1?"UTF8":type==0?"BINARY":"DIAGNOSTIC"));
    if(type==1) __android_log_print(ANDROID_LOG_INFO,"SCVA-V2-TRACE","messageComplete type=1 bytes=%zu",bytes.size());
    if(type==2) {
        uint32_t seq=0;bool correct=checkDiagnostic(bytes,seq);
        __android_log_print(ANDROID_LOG_INFO,"SCVA_V2","diagnostic id=%016llx sequence=%u bytes=%zu correct=%d",(unsigned long long)id,seq,bytes.size(),correct);
    }
    if(!rxEnv || !rxClass)return;
    jmethodID method=rxEnv->GetStaticMethodID(rxClass,"onV2Message","([BIJ)V");
    if(!method)return;
    jbyteArray array=rxEnv->NewByteArray(bytes.size());if(!array)return;
    rxEnv->SetByteArrayRegion(array,0,bytes.size(),reinterpret_cast<const jbyte*>(bytes.data()));
    if(!rxEnv->ExceptionCheck())rxEnv->CallStaticVoidMethod(rxClass,method,array,jint(type),jlong(id));
    rxEnv->DeleteLocalRef(array);
}
static Reassembler messages(message,nullptr);
static void frame(void*,const Bytes& payload,const FrameInfo& info){messages.accept(payload,clockMs(),info.codec);}
static void event(void*,const char* what,const FrameInfo& info,int index) {
    if(!allowLog())return;
    std::string missing,positions;
    for(int i=0;i<8;++i)if(!(info.chunks&(1u<<i)))missing+=std::to_string(i)+",";
    for(int p:info.result.positions)positions+=std::to_string(p)+",";
    __android_log_print(ANDROID_LOG_DEBUG,"SCVA_V2",
        "%s id=%016llx chunk=%d occupied=%02x missing=[%s] erasures=%zu corrected=%d positions=[%s] crc=%d delivered=%d",
        what,(unsigned long long)info.id,index,info.chunks,missing.c_str(),info.result.erasures.size(),info.result.corrected,positions.c_str(),info.result.crc,info.result.delivered);
    std::string line="[SCVA-V2-FEC][RX][STEP=07 RECORD-VALIDATION] event=";
    line+=what; line+=" index="+std::to_string(index)+" occupied="+std::to_string(info.chunks);
    if(!info.result.reason.empty()) line+=" reason="+info.result.reason;
    trace(line);
    if(std::string(what)=="header") rxProcedure=++procedureCounter;
    const std::string p=proc(rxProcedure);
    if(std::string(what)=="header") {
        const CodecDescription* description=codecDescription(info.codec);
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=07 RECORD-VALIDATION] type=HEADER status=VALID BLOCK="+std::to_string(info.id)+" codec="+std::to_string(info.codec)+" preset="+description->preset+" n=128 k="+std::to_string(description->k)+" parity="+std::to_string(description->parity)+" applicationCapacity="+std::to_string(description->applicationCapacity)+" chunkSize=16");
    } else if(std::string(what)=="erasure-map") {
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=08 ERASURE-MAP] erasureCount="+std::to_string(info.result.erasures.size()));
        std::string ers="[";for(size_t i=0;i<info.result.erasures.size();++i){if(i)ers+=",";ers+=std::to_string(info.result.erasures[i]);}ers+="]";
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=08 ERASURE-MAP] erasures="+ers);
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=09 RS-INPUT] BEFORE_RS[000..127]="+hex(info.beforeRs,0,128));
    } else if(std::string(what)=="rs-result") {
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=10 RS-DECODE] result="+std::string(info.result.corrected<0?"FAILURE":"SUCCESS")+" return="+std::to_string(info.result.corrected));
        if(info.result.corrected<0) return;
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=11 RS-OUTPUT] AFTER_RS[000..127]="+hex(info.afterRs,0,128));
        size_t changed=0;for(size_t i=0;i<info.beforeRs.size()&&i<info.afterRs.size();++i)if(info.beforeRs[i]!=info.afterRs[i])++changed;
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=11 RS-OUTPUT] changedBytes="+std::to_string(changed));
        trace("[SCVA-V2-FEC][PROC="+p+"][RX][STEP=12 FINAL-CRC] result="+std::string(info.result.crc?"PASS":"FAIL"));
    }
}
static Receiver receiver(frame,event,nullptr,wire);
void resetReceive(){receiver.reset();messages.reset();}
bool configureReceive(JNIEnv* env,jclass cls,bool use,bool modeOk,bool logging) {
    rxEnv=env;rxClass=cls;debug=logging;
    rxCharacterMethod=env->GetStaticMethodID(cls,"onV2DecodedCharacter","(I)V");
    traceMethod=env->GetStaticMethodID(cls,"onV2Trace","(Ljava/lang/String;)V");
    bool changed=use!=selected || modeOk!=supported;
    if(changed) {
        resetReceive();
        __android_log_print(ANDROID_LOG_INFO,"SCVA_V2","V2 RX state reset on mode change");
    }
    selected=use;supported=modeOk;
    receiver.tick(clockMs());messages.tick(clockMs());return changed;
}
void endReceive(){rxEnv=nullptr;rxClass=nullptr;rxCharacterMethod=nullptr;traceMethod=nullptr;}
bool receiveCharacter(int c,bool rsid) {
    if(rsid || !selected)return false;
    if(supported && rxEnv && !rxEnv->ExceptionCheck()) {
        if(rxCharacterMethod) rxEnv->CallStaticVoidMethod(rxClass,rxCharacterMethod,jint(c&255));
        if(!rxEnv->ExceptionCheck()) {
            receiver.feed(char(c&255),clockMs());
        }
    }
    return true; // Unsupported V2 modem must not fall through to legacy delivery.
}
bool transmit(JNIEnv* env,jclass cls,jbyteArray array,int length,int type) {
    if(!array || length<1 || length>int(MaxMessage) || length>env->GetArrayLength(array) || type<0 || type>2 || !isV2SupportedThorMode())return false;
    txEnv=env;txClass=cls;
    txCodec=getPreferenceI("RSFEC_V2_PRESET",2);
    if(!codecDescription(txCodec)) txCodec=2;
    txTraceMethod=env->GetStaticMethodID(cls,"onV2Trace","(Ljava/lang/String;)V");
    txContext=true;
    const unsigned long txProcedure=++procedureCounter; txProcedureActive=txProcedure;
    setEncodeTrace(encodeTrace,nullptr);
    trace("[SCVA-V2-FEC][PROC="+proc(txProcedure)+"][TX][STEP=01 APPLICATION] len="+std::to_string(length)+" type="+std::to_string(type));
    Bytes data(length);env->GetByteArrayRegion(array,0,length,reinterpret_cast<jbyte*>(data.data()));
    if(env->ExceptionCheck()){txContext=false;return false;}
    std::string wire;if(!encodeMessage(data.data(),data.size(),uint8_t(type),wire,randomId,txCodec)){txContext=false;return false;}
    trace("[SCVA-V2-FEC][PROC="+proc(txProcedure)+"][TX][STEP=02 SEGMENT] segments="+std::to_string(wire.size()/EncodedFrameChars)+" applicationBytes="+std::to_string(length));
    size_t at=0;int recordNo=0;
    while(at<wire.size()) { size_t end=wire.find(':',at+1); if(end==std::string::npos) break; ++end;
        trace("[SCVA-V2-FEC][PROC="+proc(txProcedure)+"][TX][STEP=04 WIRE] record="+std::to_string(recordNo++)+" len="+std::to_string(end-at)+" record=\""+wire.substr(at,end-at)+"\""); at=end;
    }
    trace("[SCVA-V2-FEC][PROC="+proc(txProcedure)+"][TX][STEP=05 TX] MESSAGE TRANSMISSION START modem="+v2ModemName());
    bool logging=getPreferenceB("RSFEC_V2_DEBUG",false);
    if(logging) {
        for(size_t offset=0;offset<wire.size();offset+=EncodedFrameChars) {
            Bytes h;unbase32(wire.substr(offset+6,32),h);
            uint64_t id=0;for(size_t i=4;i<12;++i)id=(id<<8)|h[i];
            const CodecDescription* description=codecDescription(h[3]);
            __android_log_print(ANDROID_LOG_DEBUG,"SCVA_V2","TX id=%016llx segment=%zu/%zu frame_payload=%u wire=%zu codec=%u k=%u parity=%u applicationCapacity=%u C=16",(unsigned long long)id,offset/EncodedFrameChars,wire.size()/EncodedFrameChars,h[13],EncodedFrameChars,h[3],description->k,description->parity,description->applicationCapacity);
        }
    }
    // The existing character getter mutates its pointer, so retain ownership here.
    txDataBuffer=reinterpret_cast<signed char*>(&wire[0]);txDataBufferLength=wire.size();txCounter=0;
    resetReceive();
    while(active_modem->tx_process()>=0) {
        if(env->ExceptionCheck()){txDataBuffer=nullptr;return false;}
    }
    bool complete=txCounter==txDataBufferLength;
    txDataBuffer=nullptr;flushTxSoundBuffer();
    if(complete) trace("[SCVA-V2-FEC][PROC="+proc(txProcedure)+"][TX][STEP=05 TX] MESSAGE HANDED TO "+v2ModemName());
    bool ok=complete && !env->ExceptionCheck();
    txContext=false;
    txEnv=nullptr;txClass=nullptr;txTraceMethod=nullptr;
    return ok;
}
}
