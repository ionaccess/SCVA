#pragma once
#include <algorithm>
#include <memory>
#include <string>
#include "kiss_tnc.hh"

// Adapter framing policy around the unchanged pinned Fragmenter and wire format.
// Caller serializes access; sender is fresh, unauthenticated PHY metadata.
namespace scva_m73 {
constexpr size_t kRecordLimit = 2020, kFrameLimit = 510, kParts = 32, kPending = 8;
constexpr int64_t kReassemblyIdleMs = 180000, kReassemblyMaxMs = 600000;
inline int64_t nowMs() { return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
inline std::vector<std::vector<uint8_t>> packetize(const std::vector<uint8_t>& record, size_t frameLimit = kFrameLimit) {
    if (frameLimit < Frag::HEADER_SIZE + 1 || frameLimit > kFrameLimit) return {};
    if (record.empty() || record.size() > kRecordLimit) return {};
    if (record.size() <= frameLimit) return {record};
    // Process-lifetime ID sequence survives backend recreation; only admission uses this lock.
    static Fragmenter ids; static std::mutex mutex;
    std::lock_guard<std::mutex> lock(mutex);
    return ids.fragment(record, frameLimit);
}
class Assemblies {
    struct Pending {
        std::map<int, std::vector<uint8_t>> pieces;
        int last = -1; size_t bytes = 0; int64_t first = 0, seen = 0;
    };
    using Key = std::pair<std::string, int>;
    std::map<Key, std::unique_ptr<Pending>> pending_;
    std::map<Key, int64_t> retired_;
    void retire(const Key& key, int64_t now) {
        pending_.erase(key);
        // Bounded tombstones. Saturation closes fragment admission until the oldest expires;
        // never evict an unexpired ID and accidentally accept its late duplicate set.
        if(retired_.size()<128) retired_[key]=now;
        else saturatedUntil_=std::max(saturatedUntil_,now+kReassemblyMaxMs);
    }
    int64_t saturatedUntil_=0;
    const size_t frameLimit_;
public:
    explicit Assemblies(size_t frameLimit = kFrameLimit) : frameLimit_(frameLimit) {}
    // status: 0 idle/unfragmented, 1 pending, 2 complete, 3 duplicate, 4 rejected, 5 expired.
    int64_t completed=0,rejected=0,expired=0,lastId=-1,lastSeq=-1,status=0,lastReject=0;
    void expire(int64_t now) {
        for(auto it=retired_.begin();it!=retired_.end();) {if(now-it->second>kReassemblyMaxMs)it=retired_.erase(it);else ++it;}
        std::vector<Key> stale;
        for(const auto& item:pending_)if(now-item.second->seen>kReassemblyIdleMs || now-item.second->first>kReassemblyMaxMs)stale.push_back(item.first);
        for(const auto& key:stale){lastId=key.second;lastSeq=-1;retire(key,now);++expired;status=5;}
    }
    size_t count() const{return pending_.size();}
    size_t bytes() const{size_t n=0;for(const auto& p:pending_)n+=p.second->bytes;return n;}
    std::vector<uint8_t> process(const std::vector<uint8_t>& frame,const std::string& sender,int64_t now) {
        expire(now);
        if(frame.empty())return {};
        if(frame[0]!=Frag::MAGIC){status=0;return frame;}
        auto reject=[this](int why=1){++rejected;status=4;lastReject=why;return std::vector<uint8_t>();};
        if(frame.size()<6 || frame.size()>frameLimit_ || sender.empty())return reject(sender.empty()?2:1);
        int id=(frame[1]<<8)|frame[2], seq=frame[3], flags=frame[4];lastId=id;lastSeq=seq;
        Key key{sender,id};
        if(retired_.count(key)){status=3;return {};}
        if(now<saturatedUntil_)return reject(4);
        bool more=flags&Frag::FLAG_MORE_FRAGMENTS;
        if((flags&~3) || bool(flags&2)!=(seq==0) || seq>=int(kParts) || (more&&(frame.size()!=frameLimit_||seq>=int(kParts)-1)) || (!more&&seq==0)) {retire(key,now);return reject();}
        auto found=pending_.find(key);
        if(found==pending_.end()) {
            if(pending_.size()>=kPending || retired_.size()>=128)return reject(5);
            auto fresh=std::unique_ptr<Pending>(new Pending());fresh->first=fresh->seen=now;found=pending_.emplace(key,std::move(fresh)).first;
        }
        Pending& p=*found->second;
        auto duplicate=p.pieces.find(seq);
        if(duplicate!=p.pieces.end()) {
            if(duplicate->second==frame){p.seen=now;status=3;return {};}
            retire(key,now);return reject(6);
        }
        if((p.last>=0&&(seq>p.last||(!more&&seq!=p.last))) || (!more&&!p.pieces.empty()&&p.pieces.rbegin()->first>seq) || p.bytes+frame.size()-5>kRecordLimit){retire(key,now);return reject(6);}
        if(!more)p.last=seq;
        p.pieces[seq]=frame;p.bytes+=frame.size()-5;p.seen=now;
		if(p.last>=0 && p.pieces.size()==static_cast<size_t>(p.last+1)) {
			std::vector<uint8_t> complete;
			complete.reserve(p.bytes);
			for(int i=0;i<=p.last;++i){auto piece=p.pieces.find(i);if(piece==p.pieces.end()){status=1;return {};}complete.insert(complete.end(),piece->second.begin()+5,piece->second.end());}
			retire(key,now);if(complete.size()<=frameLimit_||complete.size()>kRecordLimit)return reject(8);++completed;status=2;return complete;
		}
        status=1;return {};
    }
};
}
