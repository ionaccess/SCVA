#include "Transport.h"
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <cstdio>
namespace scva_v2 {
static uint64_t idAt(const Bytes& b,size_t at) {uint64_t id=0;for(size_t i=0;i<8;++i)id=(id<<8)|b[at+i];return id;}
static void append16(Bytes& b,size_t x){b.push_back(uint8_t(x>>8));b.push_back(uint8_t(x));}
static unsigned read16(const Bytes& b,size_t at){return (unsigned(b[at])<<8)|b[at+1];}
static EncodeTrace encodeTrace=nullptr; static void* encodeContext=nullptr;
void setEncodeTrace(EncodeTrace sink,void* context){encodeTrace=sink;encodeContext=context;}
void notifyEncodeTrace(const Bytes& input,const Bytes& word){if(encodeTrace)encodeTrace(encodeContext,input,word);}
void Receiver::emit(const char* event,int index) {
    info_.chunks=occupied_;if(event_)event_(context_,event,info_,index);
}
void Receiver::abandon(const char* reason) {
    if(active_) {info_.result.reason=reason;emit(reason);}
    active_=false;header_.clear();word_.clear();occupied_=0;
}
void Receiver::reset(const char* reason) {
    abandon(reason);scanning_=false;overflow_=false;token_.clear();
}
void Receiver::tick(uint64_t now) {
    now_=now;
    if(active_ && (now<started_ || now-started_>=FrameLifeMs || now-lastProgress_>=FrameIdleMs)) reset("timeout");
}
void Receiver::feed(char c,uint64_t now) {
    tick(now);c=fold(c);
    if(c==':') {
        if(scanning_ && !overflow_ && !token_.empty())parse();
        scanning_=true;overflow_=false;token_.clear();return;
    }
    if(!scanning_ || overflow_)return;
    if(token_.size()==77) {overflow_=true;token_.clear();return;}
    token_+=c;
}
void Receiver::parse() {
    if(wire_) wire_(context_,token_);
    auto reject=[this](const char* reason){info_.result.reason=reason;emit("malformed");};
    if(token_.size()<5){reject("TOO_SHORT");return;}
    if(token_.compare(0,4,"scv2")!=0){reject("BAD_PREFIX");return;}
    if(token_[4]!='h' && token_[4]!='d' && token_[4]!='e'){reject("UNKNOWN_RECORD_TYPE");return;}
    const std::string encoded=token_.substr(5);
    for(size_t i=0;i<encoded.size();++i) {
        if(alphabet.find(fold(encoded[i]))==std::string::npos) {
            char byteText[5]; std::snprintf(byteText,sizeof(byteText),"0x%02X",static_cast<unsigned char>(encoded[i]));
            std::string reason="INVALID_BASE32_CHARACTER offset="+std::to_string(i)+" char="+byteText;
            info_.result.reason=reason; emit("malformed"); return;
        }
    }
    Bytes b;if(!unbase32(encoded,b)){reject("BASE32_DECODE_FAILED");return;}
    char kind=token_[4];
    if(kind=='h') {
        if(b.size()!=20){reject("HEADER_LENGTH");return;}
        Bytes h(b.begin(),b.begin()+16);
        if(checksum("SCV2H",h)!=read32(b,16)){reject("HEADER_CRC_FAILED");return;}
        if(!validHeader(h) || h[15]!=16){reject("HEADER_FIELDS_INVALID");return;}
        abandon("resynchronized");info_=FrameInfo();info_.id=idAt(h,4);info_.codec=h[3];
        header_=h;word_.assign(128,0);occupied_=0;active_=true;started_=lastProgress_=now_;emit("header");return;
    }
    if(!active_){reject("NO_ACTIVE_HEADER");return;}
    if(kind=='d') {
        if(b.size()!=29){reject("CHUNK_LENGTH");return;}
        Bytes body(b.begin(),b.end()-4);
        if(checksum("SCV2D",header_,body)!=read32(b,25)){reject("CHUNK_CRC_FAILED");return;}
        if(idAt(b,0)!=info_.id){reject("BLOCK_ID_MISMATCH");return;}
        if(b[8]>=8){reject("INDEX_OUT_OF_RANGE");return;}
        unsigned j=b[8];
        if(occupied_&(1u<<j)) {
            if(!std::equal(b.begin()+9,b.begin()+25,word_.begin()+j*16))abandon("conflicting duplicate");
            else emit("duplicate",j);
            return;
        }
        std::copy(b.begin()+9,b.begin()+25,word_.begin()+j*16);occupied_|=1u<<j;lastProgress_=now_;emit("chunk",j);
    } else if(kind=='e') {
        if(b.size()!=20){reject("END_LENGTH");return;}
        if(!std::equal(header_.begin(),header_.end(),b.begin())){reject("END_HEADER_MISMATCH");return;}
        if(checksum("SCV2E",header_)!=read32(b,16)){reject("END_CRC_FAILED");return;}
        std::vector<int> missing;
        for(int i=0;i<128;++i)if(!(occupied_&(1u<<(i/16))))missing.push_back(i);
        info_.result.erasures=missing;
        info_.beforeRs=word_;
        emit("erasure-map");
        Bytes payload=recover(header_,word_,missing,info_.result);
        info_.afterRs=word_;
        emit("rs-result");
        active_=false;emit(info_.result.delivered?"frame delivered":"frame discarded");
        header_.clear();word_.clear();occupied_=0;
        if(info_.result.delivered && sink_)sink_(context_,payload,info_);
    }
}
bool encodeFrame(uint64_t id,const uint8_t* data,size_t length,std::string& wire,int codec) {
    wire.clear();const CodecDescription* description=codecDescription(codec);
    if(!data || !description || length<1 || length>size_t(description->k-4) || id==0)return false;
    Bytes h=header(id,codec,int(length),16),payload(data,data+length);auto word=codeword(h,payload);
    if(word.size()!=128)return false;
    wire=join(records(h,word));return wire.size()==EncodedFrameChars;
}
bool randomId(uint64_t& id) {
    id=0;int fd=open("/dev/urandom",O_RDONLY|O_CLOEXEC);if(fd<0)return false;
    for(int attempt=0;attempt<8;++attempt) {
        uint8_t bytes[8];size_t used=0;
        while(used<8) {
            ssize_t n=read(fd,bytes+used,8-used);
            if(n<0 && errno==EINTR)continue;
            if(n<=0){close(fd);return false;}used+=size_t(n);
        }
        for(uint8_t byte:bytes)id=(id<<8)|byte;
        if(id){close(fd);return true;}
    }
    close(fd);return false;
}
bool encodeMessage(const uint8_t* data,size_t length,uint8_t type,std::string& wire,IdSource ids,int codec) {
    wire.clear();const CodecDescription* description=codecDescription(codec);
    if(!data || !length || length>MaxMessage || type>2 || !ids || !description)return false;
    uint64_t messageId=0;if(!ids(messageId) || !messageId)return false;
    const size_t capacity=description->applicationCapacity;
    size_t count=(length+capacity-1)/capacity;std::string result;result.reserve(count*EncodedFrameChars);
    for(size_t i=0;i<count;++i) {
        Bytes segment{'S','M',1,type};
        for(int shift=56;shift>=0;shift-=8)segment.push_back(uint8_t(messageId>>shift));
        append16(segment,i);append16(segment,count);append16(segment,length);
        size_t n=std::min(capacity,length-i*capacity);segment.push_back(uint8_t(n));segment.push_back(0);
        segment.insert(segment.end(),data+i*capacity,data+i*capacity+n);
        uint64_t blockId=0;std::string frame;
        if(!ids(blockId) || !encodeFrame(blockId,segment.data(),segment.size(),frame,codec))return false;
        result+=frame;
    }
    wire.swap(result);return true;
}
void Reassembler::reset(){id_=0;count_=length_=capacity_=0;codec_=type_=0;data_.clear();have_.fill(false);}
void Reassembler::tick(uint64_t now) {
    if(id_ && (now<start_ || now-start_>=MessageLifeMs || now-progress_>=MessageIdleMs))reset();
}
void Reassembler::accept(const Bytes& b,uint64_t now,int codec) {
    tick(now);
    if(b.size()<21 || b[0]!='S' || b[1]!='M' || b[2]!=1 || b[3]>2 || b[19])return;
    const CodecDescription* description=codecDescription(codec);
    if(!description || description->applicationCapacity==0)return;
    const size_t applicationCapacity=description->applicationCapacity;
    uint64_t id=idAt(b,4);unsigned index=read16(b,12),count=read16(b,14),len=read16(b,16),n=b[18];
    if(!id || !len || len>MaxMessage)return;
    const size_t expectedCount=(size_t(len)-1)/applicationCapacity+1;
    if(expectedCount>MaxMessage || count!=expectedCount || index>=count)return;
    const size_t offset=size_t(index)*applicationCapacity;
    if(offset>=len)return;
    const size_t expectedLength=std::min(applicationCapacity,size_t(len)-offset);
    if(n!=expectedLength || b.size()!=20+expectedLength)return;
    if(std::find(completed_.begin(),completed_.end(),id)!=completed_.end())return;
    if(id_!=id) {reset();id_=id;count_=count;length_=len;capacity_=description->applicationCapacity;codec_=description->id;type_=b[3];data_.assign(len,0);start_=progress_=now;}
    if(count!=count_ || len!=length_ || b[3]!=type_ || codec_!=description->id || capacity_!=applicationCapacity) {completed_[completedNext_++%16]=id;reset();return;}
    if(have_[index]) {
        if(!std::equal(b.begin()+20,b.end(),data_.begin()+offset)) {completed_[completedNext_++%16]=id;reset();}
        return;
    }
    std::copy(b.begin()+20,b.end(),data_.begin()+offset);have_[index]=true;progress_=now;
    if(!std::all_of(have_.begin(),have_.begin()+count,[](bool x){return x;}))return;
    Bytes message;message.swap(data_);uint8_t type=type_;completed_[completedNext_++%16]=id;reset();
    if(sink_)sink_(context_,message,type,id);
}
Bytes diagnostic(uint32_t sequence) {
    Bytes b{'S','V','T',1};append32(b,sequence);
    for(unsigned i=8;i<64;++i)b.push_back(uint8_t(sequence+i*73));return b;
}
bool checkDiagnostic(const Bytes& b,uint32_t& sequence) {
    if(b.size()!=64 || b[0]!='S' || b[1]!='V' || b[2]!='T' || b[3]!=1)return false;
    sequence=read32(b,4);return b==diagnostic(sequence);
}
}
