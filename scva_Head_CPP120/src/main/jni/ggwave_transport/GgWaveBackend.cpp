#include "GgWaveBackend.h"
#include "ggwave/ggwave.h"

#include <algorithm>
#include <array>
#include <cstring>
#include <memory>
#include <limits>
#include <mutex>
#include <new>
#include <vector>

namespace {
// Pinned GGWave also has process-wide protocol/FFT helpers. Serialize every
// codec call across instances until upstream concurrency is proven safe.
std::mutex gCodecMutex;

struct Context {
    std::unique_ptr<scva_phone_ggwave::Codec> codec;
    int direction;
    GGWave::ProtocolId protocol;
};

std::mutex gHandleMutex;
// This backend owns exactly one RX and one TX codec. Fixed slots avoid a
// process-lifetime registry allocation and make invalid/duplicate handles fail closed.
std::array<std::unique_ptr<Context>, 2> gContexts;

GGWave::Parameters parameters(int direction) {
    const auto & d = GGWave::getDefaultParameters();
    return GGWave::Parameters {
        -1, 48000.0f, 48000.0f, 48000.0f, 1024,
        d.soundMarkerThreshold, GGWAVE_SAMPLE_FORMAT_I16,
        GGWAVE_SAMPLE_FORMAT_I16,
        direction == 1 ? GGWAVE_OPERATING_MODE_RX : GGWAVE_OPERATING_MODE_TX
    };
}

Context * get(jlong handle) {
    if (handle < 1 || handle > static_cast<jlong>(gContexts.size())) return nullptr;
    return gContexts[static_cast<size_t>(handle - 1)].get();
}
}

namespace scva_phone_ggwave {
Codec::Codec(int direction, GGWave::ProtocolId protocol) : Codec(direction, protocol, std::vector<GGWave::ProtocolId>{protocol}) {}

Codec::Codec(int direction, GGWave::ProtocolId protocol, const std::vector<GGWave::ProtocolId> & receiveProtocols) : direction_(direction), protocol_(protocol) {
    if (direction != 1 && direction != 2) return;
    std::lock_guard<std::mutex> codecLock(gCodecMutex);
    codec_.reset(new (std::nothrow) GGWave(parameters(direction)));
    if (!codec_ || codec_->heapSize() <= 0) { codec_.reset(); return; }
    if (direction == 1) {
        if (receiveProtocols.empty()) { codec_.reset(); return; }
        for (const auto id : receiveProtocols) {
            if (id < 0 || id >= GGWAVE_PROTOCOL_COUNT || !codec_->rxProtocols()[id].enabled) { codec_.reset(); return; }
        }
        codec_->rxProtocols().disableAll();
        for (const auto id : receiveProtocols) codec_->rxProtocols().toggle(id, true);
    }
}

std::vector<int16_t> Codec::encode(const std::vector<uint8_t> & packet) {
    if (!codec_ || direction_ != 2 || packet.empty() || packet.size() > 64) return {};
    std::lock_guard<std::mutex> codecLock(gCodecMutex);
    if (!codec_->init(static_cast<int>(packet.size()), reinterpret_cast<const char *>(packet.data()), protocol_, 25)) return {};
    const uint32_t actualBytes = codec_->encode();
    if (actualBytes == 0 || actualBytes % sizeof(int16_t) != 0) return {};
    const size_t actualSamples = actualBytes / sizeof(int16_t);
    const auto * waveform = static_cast<const int16_t *>(codec_->txWaveform());
    if (!waveform) return {};
    return std::vector<int16_t>(waveform, waveform + actualSamples);
}

std::vector<std::vector<uint8_t>> Codec::feed(const int16_t * samples, size_t count) {
    return feedDetailed(samples, count).packets;
}

FeedResult Codec::feedDetailed(const int16_t * samples, size_t count) {
    FeedResult result;
    if (!codec_ || direction_ != 1 || (!samples && count != 0)) { result.codecRejected = true; return result; }
    std::lock_guard<std::mutex> codecLock(gCodecMutex);
    if (count != 0) residual_.insert(residual_.end(), samples, samples + count);
    while (residual_.size() >= 1024) {
        const bool ok = codec_->decode(residual_.data(), 1024 * sizeof(int16_t));
        residual_.erase(residual_.begin(), residual_.begin() + 1024);
        if (!ok) { result.codecRejected = true; continue; }
        GGWave::TxRxData view;
        const int n = codec_->rxTakeData(view);
        if (n > 0) result.packets.emplace_back(view.begin(), view.end());
        else if (n < 0) result.codecRejected = true;
    }
    return result;
}

jint protocolFromToken(const char * token, GGWave::ProtocolId & protocol) {
    if (!token) return 0;
    if (std::strcmp(token, "AUDIBLE_NORMAL") == 0) protocol = GGWAVE_PROTOCOL_AUDIBLE_NORMAL;
    else if (std::strcmp(token, "AUDIBLE_FAST") == 0) protocol = GGWAVE_PROTOCOL_AUDIBLE_FAST;
    else if (std::strcmp(token, "AUDIBLE_FASTEST") == 0) protocol = GGWAVE_PROTOCOL_AUDIBLE_FASTEST;
    else if (std::strcmp(token, "ULTRASOUND_NORMAL") == 0) protocol = GGWAVE_PROTOCOL_ULTRASOUND_NORMAL;
    else if (std::strcmp(token, "ULTRASOUND_FAST") == 0) protocol = GGWAVE_PROTOCOL_ULTRASOUND_FAST;
    else if (std::strcmp(token, "ULTRASOUND_FASTEST") == 0) protocol = GGWAVE_PROTOCOL_ULTRASOUND_FASTEST;
    else if (std::strcmp(token, "DT_NORMAL") == 0) protocol = GGWAVE_PROTOCOL_DT_NORMAL;
    else if (std::strcmp(token, "DT_FAST") == 0) protocol = GGWAVE_PROTOCOL_DT_FAST;
    else if (std::strcmp(token, "DT_FASTEST") == 0) protocol = GGWAVE_PROTOCOL_DT_FASTEST;
    else return 0;
    return 1;
}

jlong create(int direction, GGWave::ProtocolId protocol) {
    return create(direction, protocol, std::vector<GGWave::ProtocolId>{protocol});
}

jlong create(int direction, GGWave::ProtocolId protocol, const std::vector<GGWave::ProtocolId> & receiveProtocols) {
    if (direction != 1 && direction != 2) return 0;
    std::lock_guard<std::mutex> handles(gHandleMutex);
    const size_t slot = static_cast<size_t>(direction - 1);
    if (gContexts[slot]) return 0;
    std::unique_ptr<Context> ctx(new (std::nothrow) Context());
    if (!ctx) return 0;
    ctx->codec.reset(new (std::nothrow) Codec(direction, protocol, receiveProtocols));
    if (!ctx->codec || !ctx->codec->ready()) return 0;
    ctx->direction = direction;
    ctx->protocol = protocol;
    gContexts[slot] = std::move(ctx);
    return static_cast<jlong>(slot + 1);
}

void close(jlong handle) {
    std::lock_guard<std::mutex> handles(gHandleMutex);
    if (handle <= 0) return;
    const size_t i = static_cast<size_t>(handle - 1);
    if (i < gContexts.size()) gContexts[i].reset();
}

jshortArray encode(JNIEnv * env, jlong handle, jbyteArray packet) {
    if (!env || !packet) return nullptr;
    const jsize n = env->GetArrayLength(packet);
    if (n < 1 || n > 64) return nullptr;
    std::vector<char> bytes(static_cast<size_t>(n));
    env->GetByteArrayRegion(packet, 0, n, reinterpret_cast<jbyte *>(bytes.data()));
    if (env->ExceptionCheck()) return nullptr;
    std::lock_guard<std::mutex> handles(gHandleMutex);
    Context * ctx = get(handle);
    if (!ctx || ctx->direction != 2) return nullptr;
    std::vector<uint8_t> packetBytes(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) packetBytes[static_cast<size_t>(i)] = static_cast<uint8_t>(bytes[static_cast<size_t>(i)]);
    std::vector<int16_t> waveform = ctx->codec->encode(packetBytes);
    if (waveform.empty() || waveform.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    jshortArray out = env->NewShortArray(static_cast<jsize>(waveform.size()));
    if (!out) return nullptr;
    env->SetShortArrayRegion(out, 0, static_cast<jsize>(waveform.size()), reinterpret_cast<const jshort *>(waveform.data()));
    return env->ExceptionCheck() ? nullptr : out;
}

jobjectArray feed(JNIEnv * env, jlong handle, jshortArray samples, jint count) {
    if (!env || !samples || count < 0 || count > env->GetArrayLength(samples)) return nullptr;
    jclass byteArrayClass = env->FindClass("[B");
    if (!byteArrayClass) return nullptr;
    std::vector<jshort> input(static_cast<size_t>(count));
    if (count) env->GetShortArrayRegion(samples, 0, count, input.data());
    if (env->ExceptionCheck()) return nullptr;

    std::lock_guard<std::mutex> handles(gHandleMutex);
    Context * ctx = get(handle);
    if (!ctx || ctx->direction != 1) return env->NewObjectArray(0, byteArrayClass, nullptr);
    FeedResult result = ctx->codec->feedDetailed(input.data(), input.size());
    const auto & decoded = result.packets;
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(decoded.size() + 1), byteArrayClass, nullptr);
    if (!out) return nullptr;
    const uint8_t status = !decoded.empty() ? 1 : result.codecRejected ? 2 : 0; // 0 pending; 1 raw packet(s); 2 codec rejection.
    jbyteArray statusBytes = env->NewByteArray(1);
    if (!statusBytes) return nullptr;
    const jbyte statusValue = static_cast<jbyte>(status);
    env->SetByteArrayRegion(statusBytes, 0, 1, &statusValue);
    env->SetObjectArrayElement(out, 0, statusBytes);
    env->DeleteLocalRef(statusBytes);
    if (env->ExceptionCheck()) return nullptr;
    for (jsize i = 0; i < static_cast<jsize>(decoded.size()); ++i) {
        const auto & bytes = decoded[static_cast<size_t>(i)];
        if (bytes.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
        jbyteArray item = env->NewByteArray(static_cast<jsize>(bytes.size()));
        if (!item) return nullptr;
        env->SetByteArrayRegion(item, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte *>(bytes.data()));
        env->SetObjectArrayElement(out, i + 1, item);
        env->DeleteLocalRef(item);
        if (env->ExceptionCheck()) return nullptr;
    }
    return out;
}
} // namespace scva_phone_ggwave

extern "C" JNIEXPORT jlong JNICALL Java_com_SCVA_GgWaveBackend_nativeCreate(JNIEnv * env, jclass, jint direction, jstring protocolToken, jobjectArray receiveProtocolTokens) {
    if (!env || !protocolToken || (direction != 1 && direction != 2)) return 0;
    const char * token = env->GetStringUTFChars(protocolToken, nullptr);
    if (!token) return 0;
    GGWave::ProtocolId protocol;
    const bool valid = scva_phone_ggwave::protocolFromToken(token, protocol) != 0;
    env->ReleaseStringUTFChars(protocolToken, token);
    if (!valid || env->ExceptionCheck()) return 0;
    std::vector<GGWave::ProtocolId> rxProtocols;
    if (direction == 1) {
        if (!receiveProtocolTokens) return 0;
        const jsize count = env->GetArrayLength(receiveProtocolTokens);
        if (count < 1 || count > 9) return 0;
        for (jsize i = 0; i < count; ++i) {
            jstring receiveToken = static_cast<jstring>(env->GetObjectArrayElement(receiveProtocolTokens, i));
            if (!receiveToken) return 0;
            const char * receiveChars = env->GetStringUTFChars(receiveToken, nullptr);
            if (!receiveChars) { env->DeleteLocalRef(receiveToken); return 0; }
            GGWave::ProtocolId receiveProtocol;
            const bool receiveValid = scva_phone_ggwave::protocolFromToken(receiveChars, receiveProtocol) != 0;
            env->ReleaseStringUTFChars(receiveToken, receiveChars);
            env->DeleteLocalRef(receiveToken);
            if (!receiveValid || env->ExceptionCheck() || std::find(rxProtocols.begin(), rxProtocols.end(), receiveProtocol) != rxProtocols.end()) return 0;
            rxProtocols.push_back(receiveProtocol);
        }
    }
    return scva_phone_ggwave::create(direction, protocol, rxProtocols);
}
extern "C" JNIEXPORT void JNICALL Java_com_SCVA_GgWaveBackend_nativeClose(JNIEnv *, jclass, jlong handle) {
    scva_phone_ggwave::close(handle);
}
extern "C" JNIEXPORT jshortArray JNICALL Java_com_SCVA_GgWaveBackend_nativeEncode(JNIEnv * env, jclass, jlong handle, jbyteArray packet) {
    return scva_phone_ggwave::encode(env, handle, packet);
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_com_SCVA_GgWaveBackend_nativeFeed(JNIEnv * env, jclass, jlong handle, jshortArray samples, jint count) {
    return scva_phone_ggwave::feed(env, handle, samples, count);
}
