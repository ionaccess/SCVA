#include "Modem73Audio.h"

#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <new>
#include <thread>

namespace {
int64_t monotonicNs()
{
	return std::chrono::duration_cast<std::chrono::nanoseconds>(
		std::chrono::steady_clock::now().time_since_epoch()).count();
}

void throwAudioState(JNIEnv* env, const char* message)
{
	if (env->ExceptionCheck()) return;
	jclass cls = env->FindClass("java/lang/IllegalStateException");
	if (cls != nullptr) env->ThrowNew(cls, message);
}

void throwAudioArgument(JNIEnv* env, const char* message)
{
	if (env->ExceptionCheck()) return;
	jclass cls = env->FindClass("java/lang/IllegalArgumentException");
	if (cls != nullptr) env->ThrowNew(cls, message);
}

Modem73Audio* getAudio(jlong handle)
{
	return reinterpret_cast<Modem73Audio*>(handle);
}
}

Modem73Audio::Modem73Audio()
	: captureRing_(static_cast<size_t>(kRingFrames), 0.0f),
	  playbackRing_(static_cast<size_t>(kRingFrames), 0.0f)
{
}

Modem73Audio::~Modem73Audio() { close(); }

bool Modem73Audio::open()
{
	if (contextInitialized_) return false;
	stopping_.store(false, std::memory_order_release);
	suppressCapture_.store(false, std::memory_order_release);
	captureFailed_.store(false, std::memory_order_release);
	playbackFailed_.store(false, std::memory_order_release);
	playbackCancelled_.store(false, std::memory_order_release);
	captureRead_.store(0, std::memory_order_release);
	captureWrite_.store(0, std::memory_order_release);
	captureCallbacks_.store(0, std::memory_order_release);
	captureFrames_.store(0, std::memory_order_release);
	captureQueuedFrames_.store(0, std::memory_order_release);
	captureSuppressedFrames_.store(0, std::memory_order_release);
	captureDroppedFrames_.store(0, std::memory_order_release);
	captureNullCallbacks_.store(0, std::memory_order_release);
	captureFlushedFrames_.store(0, std::memory_order_release);
	captureRmsMicro_.store(0, std::memory_order_release);
	capturePeakMicro_.store(0, std::memory_order_release);
	lastCaptureNs_.store(0, std::memory_order_release);
	if (ma_context_init(nullptr, 0, nullptr, &context_) != MA_SUCCESS) return false;
	contextInitialized_ = true;

	ma_device_config captureConfig = ma_device_config_init(ma_device_type_capture);
	captureConfig.capture.format = ma_format_f32;
	captureConfig.capture.channels = 1;
	captureConfig.sampleRate = kRate;
	captureConfig.periodSizeInFrames = kPeriodFrames;
	captureConfig.periods = kPeriods;
	captureConfig.dataCallback = dataCallback;
	captureConfig.notificationCallback = notificationCallback;
	captureConfig.pUserData = this;
#ifdef __ANDROID__
	captureConfig.aaudio.inputPreset = ma_aaudio_input_preset_unprocessed;
	captureConfig.performanceProfile = ma_performance_profile_conservative;
#endif
	if (ma_device_init(&context_, &captureConfig, &captureDevice_) != MA_SUCCESS) { close(); return false; }
	captureInitialized_ = true;
	if (captureDevice_.sampleRate != kRate || captureDevice_.capture.format != ma_format_f32 || captureDevice_.capture.channels != 1) { close(); return false; }

	ma_device_config playbackConfig = ma_device_config_init(ma_device_type_playback);
	playbackConfig.playback.format = ma_format_f32;
	playbackConfig.playback.channels = 1;
	playbackConfig.sampleRate = kRate;
	playbackConfig.periodSizeInFrames = kPeriodFrames;
	playbackConfig.periods = kPeriods;
	playbackConfig.dataCallback = dataCallback;
	playbackConfig.notificationCallback = notificationCallback;
	playbackConfig.pUserData = this;
	if (ma_device_init(&context_, &playbackConfig, &playbackDevice_) != MA_SUCCESS) { close(); return false; }
	playbackInitialized_ = true;
	if (playbackDevice_.sampleRate != kRate || playbackDevice_.playback.format != ma_format_f32 || playbackDevice_.playback.channels != 1) { close(); return false; }

	if (ma_device_start(&captureDevice_) != MA_SUCCESS) { close(); return false; }
	if (ma_device_start(&playbackDevice_) != MA_SUCCESS) { close(); return false; }
	return true;
}

void Modem73Audio::requestStop()
{
	stopping_.store(true, std::memory_order_release);
	captureReady_.notify_all();
	playbackChanged_.notify_all();
}

void Modem73Audio::close()
{
	requestStop();
	if (playbackInitialized_) { ma_device_uninit(&playbackDevice_); playbackInitialized_ = false; }
	if (captureInitialized_) { ma_device_uninit(&captureDevice_); captureInitialized_ = false; }
	if (contextInitialized_) { ma_context_uninit(&context_); contextInitialized_ = false; }
}

void Modem73Audio::dataCallback(ma_device* device, void* output, const void* input, ma_uint32 frames)
{
	Modem73Audio* self = static_cast<Modem73Audio*>(device->pUserData);
	if (self == nullptr) return;
	if (device->type == ma_device_type_capture) self->capture(static_cast<const float*>(input), frames);
	else self->playback(static_cast<float*>(output), frames);
}

void Modem73Audio::notificationCallback(const ma_device_notification* notification)
{
	if (notification == nullptr || notification->pDevice == nullptr) return;
	Modem73Audio* self = static_cast<Modem73Audio*>(notification->pDevice->pUserData);
	if (self == nullptr) return;
	if (notification->type == ma_device_notification_type_stopped ||
		notification->type == ma_device_notification_type_unlocked)
	{
		if (notification->pDevice->type == ma_device_type_capture) {
			if (!self->stopping_.load(std::memory_order_acquire)) self->captureFailed_.store(true, std::memory_order_release);
		} else if (!self->stopping_.load(std::memory_order_acquire) && !self->playbackCancelled_.load(std::memory_order_acquire)) {
			self->playbackFailed_.store(true, std::memory_order_release);
		}
		self->captureReady_.notify_all();
		self->playbackChanged_.notify_all();
	}
}

void Modem73Audio::capture(const float* input, ma_uint32 frames)
{
	captureCallbacks_.fetch_add(1, std::memory_order_relaxed);
	lastCaptureNs_.store(monotonicNs(), std::memory_order_release);
	if (input == nullptr) { captureNullCallbacks_.fetch_add(1, std::memory_order_relaxed); return; }
	captureFrames_.fetch_add(frames, std::memory_order_relaxed);
	double sumSquares = 0.0;
	float peak = 0.0f;
	for (ma_uint32 i = 0; i < frames; ++i) {
		float sample = std::isfinite(input[i]) ? input[i] : 0.0f;
		sumSquares += static_cast<double>(sample) * sample;
		peak = std::max(peak, std::abs(sample));
	}
	if (frames != 0) {
		captureRmsMicro_.store(static_cast<int64_t>(std::sqrt(sumSquares / frames) * 1000000.0), std::memory_order_relaxed);
		capturePeakMicro_.store(static_cast<int64_t>(peak * 1000000.0f), std::memory_order_relaxed);
	}
	if (stopping_.load(std::memory_order_acquire)) return;
	if (suppressCapture_.load(std::memory_order_acquire)) { captureSuppressedFrames_.fetch_add(frames, std::memory_order_relaxed); return; }
	uint64_t read = captureRead_.load(std::memory_order_acquire);
	uint64_t write = captureWrite_.load(std::memory_order_relaxed);
	uint64_t used = write - read;
	if (used > kRingFrames || frames > kRingFrames - used)
	{
		captureDroppedFrames_.fetch_add(frames, std::memory_order_relaxed);
		captureFailed_.store(true, std::memory_order_release);
		captureReady_.notify_all();
		return;
	}
	for (ma_uint32 i = 0; i < frames; ++i) captureRing_[(write + i) % kRingFrames] = input[i];
	captureWrite_.store(write + frames, std::memory_order_release);
	captureQueuedFrames_.fetch_add(frames, std::memory_order_relaxed);
	captureReady_.notify_one();
}

void Modem73Audio::playback(float* output, ma_uint32 frames)
{
	if (output == nullptr) return;
	std::fill(output, output + frames, 0.0f);
	if (stopping_.load(std::memory_order_acquire) || playbackCancelled_.load(std::memory_order_acquire)) return;
	uint64_t read = playbackRead_.load(std::memory_order_relaxed);
	uint64_t write = playbackWrite_.load(std::memory_order_acquire);
	uint64_t available = write - read;
	ma_uint32 take = static_cast<ma_uint32>(std::min<uint64_t>(available, frames));
	for (ma_uint32 i = 0; i < take; ++i) output[i] = playbackRing_[(read + i) % kRingFrames];
	playbackRead_.store(read + take, std::memory_order_release);
	playbackChanged_.notify_all();
}

int Modem73Audio::readCapture(float* output, int requested, int timeoutMs)
{
	if (output == nullptr || requested <= 0 || timeoutMs < 0) return -3;
	std::unique_lock<std::mutex> lock(captureWaitMutex_);
	auto ready = [this]() {
		return stopping_.load(std::memory_order_acquire) || captureFailed_.load(std::memory_order_acquire) ||
			captureRead_.load(std::memory_order_relaxed) < captureWrite_.load(std::memory_order_acquire);
	};
	if (!ready() && !captureReady_.wait_for(lock, std::chrono::milliseconds(timeoutMs), ready)) return 0;
	if (stopping_.load(std::memory_order_acquire)) return -1;
	if (captureFailed_.load(std::memory_order_acquire)) return -2;
	uint64_t read = captureRead_.load(std::memory_order_relaxed);
	uint64_t write = captureWrite_.load(std::memory_order_acquire);
	int count = static_cast<int>(std::min<uint64_t>(write - read, static_cast<uint64_t>(requested)));
	for (int i = 0; i < count; ++i) output[i] = captureRing_[(read + static_cast<uint64_t>(i)) % kRingFrames];
	captureRead_.store(read + static_cast<uint64_t>(count), std::memory_order_release);
	return count;
}

int64_t Modem73Audio::elapsedSinceCaptureMs() const
{
	int64_t last = lastCaptureNs_.load(std::memory_order_acquire);
	return last <= 0 ? INT64_MAX : (monotonicNs() - last) / 1000000;
}

bool Modem73Audio::captureAlive() const
{
	return captureInitialized_ && captureFrames_.load(std::memory_order_acquire) > 0 &&
		!stopping_.load(std::memory_order_acquire) && !captureFailed_.load(std::memory_order_acquire) && elapsedSinceCaptureMs() <= 250;
}

void Modem73Audio::captureStats(int64_t values[19]) const
{
	if (values == nullptr) return;
	uint64_t read = captureRead_.load(std::memory_order_acquire);
	uint64_t write = captureWrite_.load(std::memory_order_acquire);
	values[0] = static_cast<int64_t>(captureCallbacks_.load(std::memory_order_acquire));
	values[1] = static_cast<int64_t>(captureFrames_.load(std::memory_order_acquire));
	values[2] = static_cast<int64_t>(captureQueuedFrames_.load(std::memory_order_acquire));
	values[3] = static_cast<int64_t>(captureSuppressedFrames_.load(std::memory_order_acquire));
	values[4] = static_cast<int64_t>(captureDroppedFrames_.load(std::memory_order_acquire));
	values[5] = static_cast<int64_t>(write >= read ? write - read : 0);
	values[6] = elapsedSinceCaptureMs();
	values[7] = captureRmsMicro_.load(std::memory_order_relaxed);
	values[8] = capturePeakMicro_.load(std::memory_order_relaxed);
	values[9] = captureInitialized_ ? captureDevice_.sampleRate : 0;
	values[10] = captureInitialized_ ? captureDevice_.capture.channels : 0;
	values[11] = captureInitialized_ ? captureDevice_.capture.format : 0;
	values[12] = contextInitialized_ ? static_cast<int64_t>(context_.backend) : -1;
	values[13] = playbackInitialized_ ? playbackDevice_.sampleRate : 0;
	values[14] = playbackInitialized_ ? playbackDevice_.playback.channels : 0;
	values[15] = playbackInitialized_ ? playbackDevice_.playback.format : 0;
	values[16] = contextInitialized_ ? static_cast<int64_t>(context_.backend) : -1;
	values[17] = static_cast<int64_t>(captureNullCallbacks_.load(std::memory_order_acquire));
	values[18] = static_cast<int64_t>(captureFlushedFrames_.load(std::memory_order_acquire));
}

const char* Modem73Audio::backendName() const
{
	return contextInitialized_ ? ma_get_backend_name(context_.backend) : "unavailable";
}

void Modem73Audio::flushCapture()
{
	uint64_t write = captureWrite_.load(std::memory_order_acquire);
	uint64_t read = captureRead_.load(std::memory_order_relaxed);
	if (write > read) captureFlushedFrames_.fetch_add(write - read, std::memory_order_relaxed);
	captureRead_.store(write, std::memory_order_release);
}

void Modem73Audio::suppressCapture(bool suppress)
{
	suppressCapture_.store(suppress, std::memory_order_release);
	if (suppress) flushCapture();
}

void Modem73Audio::beginPlayback()
{
	uint64_t write = playbackWrite_.load(std::memory_order_acquire);
	playbackRead_.store(write, std::memory_order_release);
	playbackCancelled_.store(false, std::memory_order_release);
}

int Modem73Audio::writePlayback(const float* samples, int frames)
{
	if (samples == nullptr || frames <= 0) return -1;
	if (stopping_.load(std::memory_order_acquire) || playbackCancelled_.load(std::memory_order_acquire) ||
		playbackFailed_.load(std::memory_order_acquire) || !playbackInitialized_) return -1;
	std::lock_guard<std::mutex> lock(playbackWriteMutex_);
	uint64_t read = playbackRead_.load(std::memory_order_acquire);
	uint64_t write = playbackWrite_.load(std::memory_order_relaxed);
	uint64_t used = write - read;
	if (used >= kRingFrames) return 0;
	int count = static_cast<int>(std::min<uint64_t>(static_cast<uint64_t>(frames), kRingFrames - used));
	for (int i = 0; i < count; ++i) playbackRing_[(write + static_cast<uint64_t>(i)) % kRingFrames] = samples[i];
	playbackWrite_.store(write + static_cast<uint64_t>(count), std::memory_order_release);
	return count;
}

int Modem73Audio::playbackQueueTailMs() const
{
	if (!playbackInitialized_ || playbackDevice_.playback.internalSampleRate == 0) return -1;
	uint64_t period = std::max<ma_uint32>(1, playbackDevice_.playback.internalPeriodSizeInFrames);
	uint64_t periods = std::max<ma_uint32>(1, playbackDevice_.playback.internalPeriods);
	// Wait for the configured device queue plus one scheduling period after the callback consumed the last sample.
	return static_cast<int>((period * (periods + 1) * 1000 + playbackDevice_.playback.internalSampleRate - 1) /
		playbackDevice_.playback.internalSampleRate);
}

int Modem73Audio::drainPlayback(int timeoutMs)
{
	uint64_t target = playbackWrite_.load(std::memory_order_acquire);
	auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
	while (playbackRead_.load(std::memory_order_acquire) < target)
	{
		if (stopping_.load(std::memory_order_acquire) || playbackCancelled_.load(std::memory_order_acquire)) return -1;
		if (playbackFailed_.load(std::memory_order_acquire)) return -2;
		if (std::chrono::steady_clock::now() >= deadline) return 0;
		std::unique_lock<std::mutex> lock(playbackWaitMutex_);
		playbackChanged_.wait_for(lock, std::chrono::milliseconds(5));
	}
	const int bufferTailMs = playbackQueueTailMs();
	if (bufferTailMs < 0) return -2;
	auto tailEnd = std::chrono::steady_clock::now() + std::chrono::milliseconds(bufferTailMs);
	while (std::chrono::steady_clock::now() < tailEnd)
	{
		if (stopping_.load(std::memory_order_acquire) || playbackCancelled_.load(std::memory_order_acquire)) return -1;
		if (playbackFailed_.load(std::memory_order_acquire)) return -2;
		std::this_thread::sleep_for(std::chrono::milliseconds(2));
	}
	return 1;
}

void Modem73Audio::requestPlaybackCancel()
{
	playbackCancelled_.store(true, std::memory_order_release);
	playbackChanged_.notify_all();
}

int Modem73Audio::abortPlayback()
{
	requestPlaybackCancel();
	uint64_t write = playbackWrite_.load(std::memory_order_acquire);
	playbackRead_.store(write, std::memory_order_release);
	playbackChanged_.notify_all();
	if (!playbackInitialized_ || stopping_.load(std::memory_order_acquire)) return -1;
	ma_result stopped = ma_device_stop(&playbackDevice_);
	if (stopped != MA_SUCCESS) { playbackFailed_.store(true, std::memory_order_release); return -2; }
	if (stopping_.load(std::memory_order_acquire)) return -1;
	if (ma_device_start(&playbackDevice_) != MA_SUCCESS) { playbackFailed_.store(true, std::memory_order_release); return -2; }
	return 1;
}

bool Modem73Audio::captureFailed() const { return captureFailed_.load(std::memory_order_acquire); }
bool Modem73Audio::playbackFailed() const { return playbackFailed_.load(std::memory_order_acquire); }
bool Modem73Audio::beginAccess(int frameAirMs, uint32_t seed, bool responder, int responderDitherMs)
{
	return access_.begin(frameAirMs, seed, responder, responderDitherMs);
}
int Modem73Audio::accessStep(float levelDb, bool txAllowed)
{
	return access_.step(levelDb, captureAlive(), txAllowed);
}

static void throwAudio(JNIEnv* env, const char* message)
{
	if (!env->ExceptionCheck()) {
		jclass cls = env->FindClass("java/lang/IllegalStateException");
		if (cls != nullptr) env->ThrowNew(cls, message);
	}
}

extern "C" JNIEXPORT jlong JNICALL Java_com_SCVA_Modem73Backend_nativeAudioCreate(JNIEnv* env, jclass)
{
	if (env->ExceptionCheck()) return 0;
	Modem73Audio* audio = new (std::nothrow) Modem73Audio();
	if (audio == nullptr) { throwAudio(env, "MODEM73 audio allocation failed"); return 0; }
	if (!audio->open()) { delete audio; throwAudio(env, "miniaudio could not open 48 kHz mono float capture and playback"); return 0; }
	return reinterpret_cast<jlong>(audio);
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeAudioStop(JNIEnv*, jclass, jlong handle)
{
	if (handle != 0) getAudio(handle)->requestStop();
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeAudioClose(JNIEnv*, jclass, jlong handle)
{
	delete getAudio(handle);
}

extern "C" JNIEXPORT jint JNICALL Java_com_SCVA_Modem73Backend_nativeAudioRead(JNIEnv* env, jclass, jlong handle,
	jfloatArray destination, jint count, jint timeoutMs)
{
	if (env->ExceptionCheck()) return -1;
	if (handle == 0 || destination == nullptr) { throwAudioArgument(env, "MODEM73 capture destination is required"); return -1; }
	jsize length = env->GetArrayLength(destination);
	if (count < 0 || count > length || timeoutMs < 0) { throwAudioArgument(env, "Invalid MODEM73 capture read bounds"); return -1; }
	std::vector<float> buffer(static_cast<size_t>(count));
	int result = getAudio(handle)->readCapture(buffer.data(), count, timeoutMs);
	if (result > 0) env->SetFloatArrayRegion(destination, 0, result, buffer.data());
	return env->ExceptionCheck() ? -1 : result;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_SCVA_Modem73Backend_nativeAudioAlive(JNIEnv*, jclass, jlong handle)
{
	return handle != 0 && getAudio(handle)->captureAlive() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_SCVA_Modem73Backend_nativeAudioStats(JNIEnv* env, jclass, jlong handle)
{
	if (env->ExceptionCheck()) return nullptr;
	if (handle == 0) { throwAudioArgument(env, "MODEM73 audio handle is unavailable"); return nullptr; }
	int64_t values[19] = {};
	getAudio(handle)->captureStats(values);
	jlong converted[19];
	for (size_t i = 0; i < 19; ++i) converted[i] = static_cast<jlong>(values[i]);
	jlongArray result = env->NewLongArray(19);
	if (result == nullptr || env->ExceptionCheck()) return nullptr;
	env->SetLongArrayRegion(result, 0, 19, converted);
	return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_SCVA_Modem73Backend_nativeAudioBackendName(JNIEnv* env, jclass, jlong handle)
{
	if (env->ExceptionCheck()) return nullptr;
	if (handle == 0) { throwAudioArgument(env, "MODEM73 audio handle is unavailable"); return nullptr; }
	const char* name = getAudio(handle)->backendName();
	return name == nullptr ? env->NewStringUTF("unknown") : env->NewStringUTF(name);
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeAudioFlushCapture(JNIEnv*, jclass, jlong handle)
{
	if (handle != 0) getAudio(handle)->flushCapture();
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeAudioSuppressCapture(JNIEnv*, jclass, jlong handle, jboolean suppress)
{
	if (handle != 0) getAudio(handle)->suppressCapture(suppress == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeAudioBeginPlayback(JNIEnv*, jclass, jlong handle)
{
	if (handle != 0) getAudio(handle)->beginPlayback();
}

extern "C" JNIEXPORT jint JNICALL Java_com_SCVA_Modem73Backend_nativeAudioWrite(JNIEnv* env, jclass, jlong handle,
	jfloatArray source, jint offset, jint count)
{
	if (env->ExceptionCheck()) return -1;
	if (handle == 0 || source == nullptr) { throwAudioArgument(env, "MODEM73 playback source is required"); return -1; }
	jsize length = env->GetArrayLength(source);
	if (offset < 0 || count < 0 || offset > length || count > length - offset) { throwAudioArgument(env, "Invalid MODEM73 playback bounds"); return -1; }
	std::vector<float> buffer(static_cast<size_t>(count));
	env->GetFloatArrayRegion(source, offset, count, buffer.data());
	if (env->ExceptionCheck()) return -1;
	return getAudio(handle)->writePlayback(buffer.data(), count);
}

extern "C" JNIEXPORT jint JNICALL Java_com_SCVA_Modem73Backend_nativeAudioDrain(JNIEnv*, jclass, jlong handle, jint timeoutMs)
{
	if (handle == 0 || timeoutMs < 0) return -2;
	return getAudio(handle)->drainPlayback(timeoutMs);
}

extern "C" JNIEXPORT jint JNICALL Java_com_SCVA_Modem73Backend_nativeAudioAbortPlayback(JNIEnv*, jclass, jlong handle)
{
	return handle == 0 ? -1 : getAudio(handle)->abortPlayback();
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeAudioRequestCancel(JNIEnv*, jclass, jlong handle)
{
	if (handle != 0) getAudio(handle)->requestPlaybackCancel();
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_SCVA_Modem73Backend_nativeAudioFailed(JNIEnv*, jclass, jlong handle)
{
	return handle == 0 || getAudio(handle)->captureFailed() || getAudio(handle)->playbackFailed() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL Java_com_SCVA_Modem73Backend_nativeAudioBeginAccess(JNIEnv*, jclass, jlong handle,
	jint frameAirMs, jlong seed, jboolean responder, jint responderDitherMs)
{
	if (handle == 0) return -1;
	return getAudio(handle)->beginAccess(frameAirMs, static_cast<uint32_t>(seed), responder == JNI_TRUE,
		responderDitherMs) ? 1 : 0;
}

extern "C" JNIEXPORT jint JNICALL Java_com_SCVA_Modem73Backend_nativeAudioAccessStep(JNIEnv*, jclass, jlong handle,
	jfloat levelDb, jboolean txAllowed)
{
	if (handle == 0) return Modem73ChannelAccess::NO_CAPTURE;
	return getAudio(handle)->accessStep(levelDb, txAllowed == JNI_TRUE);
}
