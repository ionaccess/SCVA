#pragma once

#include <jni.h>
#include <cstdint>
#include <memory>
#include <vector>
#include "ggwave/ggwave.h"

namespace scva_phone_ggwave {

struct FeedResult {
    bool codecRejected = false;
    std::vector<std::vector<uint8_t>> packets;
};

class Codec {
public:
    Codec(int direction, GGWave::ProtocolId protocol);
    Codec(int direction, GGWave::ProtocolId protocol, const std::vector<GGWave::ProtocolId> & receiveProtocols);
    bool ready() const { return codec_ != nullptr; }
    std::vector<int16_t> encode(const std::vector<uint8_t> & packet);
    std::vector<std::vector<uint8_t>> feed(const int16_t * samples, size_t count);
    FeedResult feedDetailed(const int16_t * samples, size_t count);
    size_t residualSamples() const { return residual_.size(); }
private:
    std::unique_ptr<GGWave> codec_;
    std::vector<int16_t> residual_;
    int direction_;
    GGWave::ProtocolId protocol_;
};

// Native contexts are opaque to Java and are closed only after the owning
// backend has quiesced its RX/TX workers.
jlong create(int direction, GGWave::ProtocolId protocol);
jlong create(int direction, GGWave::ProtocolId protocol, const std::vector<GGWave::ProtocolId> & receiveProtocols);
jint protocolFromToken(const char * token, GGWave::ProtocolId & protocol);
void close(jlong handle);
jshortArray encode(JNIEnv * env, jlong handle, jbyteArray packet);
jobjectArray feed(JNIEnv * env, jlong handle, jshortArray samples, jint count);

} // namespace scva_phone_ggwave
