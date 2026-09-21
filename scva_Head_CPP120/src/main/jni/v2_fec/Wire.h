// Shared wire codec: approved V2 format. No Android/test dependencies.
#ifndef SCVA_V2_WIRE_H
#define SCVA_V2_WIRE_H
#include <algorithm>
#include <array>
#include <cstdint>
#include <string>
#include <vector>
#include "ezpwd/rs"
#include "rs_decode_checked.h"

namespace scva_v2 {
using Bytes = std::vector<uint8_t>;
void notifyEncodeTrace(const Bytes& input,const Bytes& word);
static const std::string alphabet = "abcdefghijklmnopqrstuvwxyz234567";
inline char fold(char c) { return c >= 'A' && c <= 'Z' ? c + ('a'-'A') : c; }
inline uint32_t crc32c(const Bytes& data) {
    uint32_t crc = 0xffffffffu;
    for (uint8_t b : data) {
        crc ^= b;
        for (int j=0; j<8; ++j) crc=(crc>>1)^((crc&1)?0x82f63b78u:0u);
    }
    return crc^0xffffffffu;
}
inline void append32(Bytes& b, uint32_t x) {
    for(int shift=24; shift>=0; shift-=8) b.push_back(uint8_t(x>>shift));
}
inline uint32_t read32(const Bytes& b, size_t off) {
    if(off>b.size() || b.size()-off<4) return 0;
    uint32_t x=0; for(size_t i=0;i<4;++i) x=(x<<8)|b[off+i]; return x;
}
inline uint32_t checksum(const std::string& domain, const Bytes& h, const Bytes& data={}) {
    Bytes input(domain.begin(),domain.end()); input.insert(input.end(),h.begin(),h.end());
    input.insert(input.end(),data.begin(),data.end()); return crc32c(input);
}
inline std::string base32(const Bytes& input) {
    std::string out; uint32_t acc=0; int bits=0;
    for(uint8_t c: input) {
        acc=(acc<<8)|c; bits+=8;
        while(bits>=5) {bits-=5;out+=alphabet[(acc>>bits)&31];}
    }
    if(bits) out+=alphabet[(acc<<(5-bits))&31];
    return out;
}
inline bool unbase32(const std::string& text, Bytes& output) {
    output.clear(); uint32_t acc=0; int bits=0;
    for(char c: text) {
        const size_t value=alphabet.find(fold(c));
        if(value==std::string::npos) {output.clear();return false;}
        acc=(acc<<5)|uint32_t(value);bits+=5;
        if(bits>=8) {bits-=8;output.push_back(uint8_t(acc>>bits));}
    }
    // Reject impossible lengths and nonzero pad bits; no '=' or ignored text.
    if(text.size()!=(output.size()*8+4)/5 || (bits && (acc&((1u<<bits)-1)))) {
        output.clear();return false;
    }
    return true;
}
struct CodecDescription {
    uint8_t id;
    uint16_t k;
    uint16_t parity;
    uint16_t applicationCapacity;
    const char* preset;
};
inline const CodecDescription* codecDescription(int id) {
    static const CodecDescription codecs[] = {
        {1,112,16,88,"10%"},
        {2,96,32,72,"Balanced"},
        {3,80,48,56,"Strong"},
        {4,90,38,66,"30%"},
        {5,77,51,53,"40%"},
        {6,64,64,40,"50%"},
        {7,51,77,27,"60%"},
        {8,38,90,14,"70%"},
        {9,32,96,8,"90%"}
    };
    for(const auto& codec:codecs) if(codec.id==id) return &codec;
    return nullptr;
}
inline int parity(int codec) {
    const CodecDescription* description=codecDescription(codec);
    return description ? description->parity : 0;
}
inline bool validHeader(const Bytes& h) {
    const CodecDescription* description=h.size()==16 ? codecDescription(h[3]) : nullptr;
    if(!description || h[0]!=0x53 || h[1]!=0x32 || h[2]!=2 || h[14]!=128) return false;
    const int c=h[15], len=(h[12]<<8)|h[13];
    return (c==1 || c==4 || c==8 || c==16 || c==32) &&
           len>=1 && len<=description->k-4 &&
           std::any_of(h.begin()+4,h.begin()+12,[](uint8_t b){return b!=0;});
}
inline Bytes header(uint64_t id, int codec, int length, int chunk=16) {
    Bytes h{0x53,0x32,2,uint8_t(codec)};
    for(int shift=56;shift>=0;shift-=8) h.push_back(uint8_t(id>>shift));
    h.push_back(uint8_t(length>>8));h.push_back(uint8_t(length));h.push_back(128);h.push_back(uint8_t(chunk));
    if(length<1 || chunk<1 || chunk>32 || !codecDescription(codec) || !validHeader(h))
        return {};
    return h;
}
inline bool encodeRS(int codec, Bytes& message) {
    const CodecDescription* description=codecDescription(codec);
    if(!description || message.empty() || message.size()>description->k) return false;
    int n=-1;
    if(codec==1) {static const ezpwd::RS<255,239> rs;n=rs.encode(message);}
    if(codec==2) {static const ezpwd::RS<255,223> rs;n=rs.encode(message);}
    if(codec==3) {static const ezpwd::RS<255,207> rs;n=rs.encode(message);}
    if(codec==4) {static const ezpwd::RS<255,217> rs;n=rs.encode(message);}
    if(codec==5) {static const ezpwd::RS<255,204> rs;n=rs.encode(message);}
    if(codec==6) {static const ezpwd::RS<255,191> rs;n=rs.encode(message);}
    if(codec==7) {static const ezpwd::RS<255,178> rs;n=rs.encode(message);}
    if(codec==8) {static const ezpwd::RS<255,165> rs;n=rs.encode(message);}
    if(codec==9) {static const ezpwd::RS<255,159> rs;n=rs.encode(message);}
    return n > 0 && n==parity(codec);
}
inline int decodeRS(int codec, Bytes& word, const std::vector<int>& erasures, std::vector<int>& positions) {
    const CodecDescription* description=codecDescription(codec);
    if(!description || erasures.size()>size_t(description->parity)) {word.clear();positions.clear();return -1;}
    if(codec==1) {static const ezpwd::RS<255,239> rs;return scva::decodeChecked(rs,word,128,112,erasures,positions);}
    if(codec==2) {static const ezpwd::RS<255,223> rs;return scva::decodeChecked(rs,word,128,96,erasures,positions);}
    if(codec==3) {static const ezpwd::RS<255,207> rs;return scva::decodeChecked(rs,word,128,80,erasures,positions);}
    if(codec==4) {static const ezpwd::RS<255,217> rs;return scva::decodeChecked(rs,word,128,90,erasures,positions);}
    if(codec==5) {static const ezpwd::RS<255,204> rs;return scva::decodeChecked(rs,word,128,77,erasures,positions);}
    if(codec==6) {static const ezpwd::RS<255,191> rs;return scva::decodeChecked(rs,word,128,64,erasures,positions);}
    if(codec==7) {static const ezpwd::RS<255,178> rs;return scva::decodeChecked(rs,word,128,51,erasures,positions);}
    if(codec==8) {static const ezpwd::RS<255,165> rs;return scva::decodeChecked(rs,word,128,38,erasures,positions);}
    if(codec==9) {static const ezpwd::RS<255,159> rs;return scva::decodeChecked(rs,word,128,32,erasures,positions);}
    word.clear();positions.clear();return -1;
}
inline Bytes codeword(const Bytes& h, const Bytes& payload) {
    if(!validHeader(h) || payload.size()!=size_t((h[12]<<8)|h[13])) return {};
    Bytes message=payload;message.resize(128-parity(h[3])-4,0);
    append32(message,checksum("SCV2P",h,message));
    notifyEncodeTrace(message,Bytes());
    if(!encodeRS(h[3],message)) return {};
    notifyEncodeTrace(message,message);return message;
}
constexpr size_t HeaderRecordChars=1+5+((20*8+4)/5)+1;
constexpr size_t DataRecordChars=1+5+((29*8+4)/5)+1;
constexpr size_t EndRecordChars=HeaderRecordChars;
constexpr size_t DataRecordCount=8;
constexpr size_t HeaderCopies=2;
constexpr size_t EndCopies=2;
constexpr size_t EncodedFrameChars=HeaderCopies*HeaderRecordChars+DataRecordCount*DataRecordChars+EndCopies*EndRecordChars;
inline std::string record(char type, const Bytes& b) {return ":scv2"+std::string(1,type)+base32(b)+":";}
inline std::string headerRecord(const Bytes& h, char type) {
    Bytes b=h;append32(b,checksum(type=='h'?"SCV2H":"SCV2E",h));return record(type,b);
}
inline std::string dataRecord(const Bytes& h, int index, const Bytes& word) {
    if(!validHeader(h)) return {};
    const int c=h[15];
    if(index<0 || index>=128/c || word.size()!=128) return {};
    Bytes b(h.begin()+4,h.begin()+12);b.push_back(uint8_t(index));
    b.insert(b.end(),word.begin()+index*c,word.begin()+(index+1)*c);
    append32(b,checksum("SCV2D",h,b));return record('d',b);
}
inline std::vector<std::string> records(const Bytes& h,const Bytes& word) {
    if(!validHeader(h) || word.size()!=128) return {};
    const std::string header=headerRecord(h,'h');
    const std::string end=headerRecord(h,'e');
    std::vector<std::string> out{header,header};
    for(int i=0;i<128/h[15];++i) out.push_back(dataRecord(h,i,word));
    out.push_back(end);out.push_back(end);return out;
}
inline std::string join(const std::vector<std::string>& records) {
    std::string out;for(const auto& r:records) out+=r;return out;
}
struct Outcome {
    std::vector<int> erasures,positions;
    int corrected=-1;
    bool crc=false, delivered=false;
    std::string reason;
};
inline Bytes recover(const Bytes& h, Bytes& word,const std::vector<int>& erasures,Outcome& result) {
    if(!validHeader(h)) {result.reason="invalid header";return {};}
    result.erasures=erasures;
    result.corrected=decodeRS(h[3],word,erasures,result.positions);
    if(result.corrected<0) {result.reason="RS rejected";return {};}
    int errors=0;
    for(int p:result.positions) if(std::find(erasures.begin(),erasures.end(),p)==erasures.end()) ++errors;
    if(2*errors+int(erasures.size())>parity(h[3])) {result.reason="bound exceeded";return {};}
    Bytes body(word.begin(),word.end()-4);
    result.crc=checksum("SCV2P",h,body)==read32(word,word.size()-4);
    if(!result.crc) {result.reason="final CRC rejected";return {};}
    const int len=(h[12]<<8)|h[13];
    if(len<1 || size_t(len)>body.size() || !std::all_of(body.begin()+len,body.end(),[](uint8_t b){return b==0;})) {
        result.reason="length/padding rejected";return {};
    }
    body.resize(len); result.delivered=true;result.reason="delivered";return body;
}
}
#endif
