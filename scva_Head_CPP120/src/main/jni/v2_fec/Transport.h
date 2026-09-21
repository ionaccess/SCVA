#ifndef SCVA_V2_TRANSPORT_H
#define SCVA_V2_TRANSPORT_H
#include "Wire.h"

namespace scva_v2 {
constexpr size_t MaxMessage=4096;
constexpr uint64_t FrameIdleMs=300000, FrameLifeMs=600000;
constexpr uint64_t MessageIdleMs=600000, MessageLifeMs=7200000;

// One instance per serialized RX stream. Callbacks are synchronous/non-reentrant.
// Caller supplies monotonic milliseconds and calls tick even when no chars arrive.
struct FrameInfo {
    uint64_t id=0;
    uint8_t codec=2;
    uint8_t chunks=0;
    Outcome result;
    Bytes beforeRs, afterRs;
};
using FrameSink=void(*)(void*,const Bytes&,const FrameInfo&);
using EventSink=void(*)(void*,const char*,const FrameInfo&,int);
using WireSink=void(*)(void*,const std::string&);
class Receiver {
    bool scanning_=false,overflow_=false,active_=false;
    std::string token_;
    Bytes header_,word_;
    uint8_t occupied_=0;
    uint64_t started_=0,lastProgress_=0,now_=0;
    FrameSink sink_; EventSink event_; WireSink wire_; void* context_;
    FrameInfo info_;
    void parse();
    void emit(const char*,int=-1);
    void abandon(const char*);
public:
    Receiver(FrameSink sink,EventSink event,void* context,WireSink wire=nullptr):sink_(sink),event_(event),wire_(wire),context_(context) {token_.reserve(77);}
    void feed(char c,uint64_t now);
    void tick(uint64_t now);
    void reset(const char* reason="reset");
    const FrameInfo& lastInfo() const {return info_;}
    size_t buffered() const {return token_.size()+header_.size()+word_.size();}
};
// Returns false on invalid input; no partial wire on failure. ID is caller-owned.
bool encodeFrame(uint64_t id,const uint8_t* data,size_t length,std::string& wire,int codec=2);
using EncodeTrace=void(*)(void*,const Bytes&,const Bytes&);
void setEncodeTrace(EncodeTrace,void* context);
void notifyEncodeTrace(const Bytes& input,const Bytes& word);
bool randomId(uint64_t& id);

// Application envelope INSIDE V2 payload, never a transport header change.
// The 20-byte SM/1 header leaves codec-dependent application capacity per segment.
using MessageSink=void(*)(void*,const Bytes&,uint8_t,uint64_t);
using IdSource=bool(*)(uint64_t&);
bool encodeMessage(const uint8_t*,size_t,uint8_t,std::string&,IdSource=randomId,int codec=2);
class Reassembler {
    uint64_t id_=0,start_=0,progress_=0;
    uint16_t count_=0,length_=0,capacity_=0; uint8_t type_=0,codec_=0;
    Bytes data_; std::array<bool,MaxMessage+1> have_{};
    std::array<uint64_t,16> completed_{};size_t completedNext_=0;
    MessageSink sink_;void* context_;
public:
    Reassembler(MessageSink sink,void* context):sink_(sink),context_(context) {}
    void accept(const Bytes&,uint64_t now,int codec=2);
    void tick(uint64_t now);
    void reset();
    size_t buffered() const {return data_.size();}
};
Bytes diagnostic(uint32_t sequence);
bool checkDiagnostic(const Bytes&,uint32_t& sequence);
}
#endif
