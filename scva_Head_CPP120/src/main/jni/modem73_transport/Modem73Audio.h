#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <vector>

#include "Modem73ChannelAccess.h"
#include "deps/miniaudio.h"

class Modem73Audio
{
public:
	static constexpr int kRate = 48000;
	static constexpr int kPeriodFrames = 480;
	static constexpr int kPeriods = 4;
	static constexpr uint64_t kRingFrames = 96000;

	Modem73Audio();
	~Modem73Audio();
	bool open();
	void requestStop();
	void close();
	int readCapture(float* output, int requestedFrames, int timeoutMs);
	bool captureAlive() const;
	void captureStats(int64_t values[19]) const;
	const char* backendName() const;
	void flushCapture();
	void suppressCapture(bool suppress);
	void beginPlayback();
	int writePlayback(const float* samples, int frames);
	int drainPlayback(int timeoutMs);
	void requestPlaybackCancel();
	int abortPlayback();
	int playbackQueueTailMs() const;
	bool captureFailed() const;
	bool playbackFailed() const;
	bool beginAccess(int frameAirMs, uint32_t seed, bool responder, int responderDitherMs);
	int accessStep(float levelDb, bool txAllowed);

private:
	static void dataCallback(ma_device* device, void* output, const void* input, ma_uint32 frames);
	static void notificationCallback(const ma_device_notification* notification);
	void capture(const float* input, ma_uint32 frames);
	void playback(float* output, ma_uint32 frames);
	int64_t elapsedSinceCaptureMs() const;

	ma_context context_{};
	ma_device captureDevice_{};
	ma_device playbackDevice_{};
	bool contextInitialized_ = false;
	bool captureInitialized_ = false;
	bool playbackInitialized_ = false;
	std::vector<float> captureRing_;
	std::vector<float> playbackRing_;
	std::atomic<uint64_t> captureRead_{0};
	std::atomic<uint64_t> captureWrite_{0};
	std::atomic<uint64_t> playbackRead_{0};
	std::atomic<uint64_t> playbackWrite_{0};
	std::atomic<bool> stopping_{false};
	std::atomic<bool> suppressCapture_{false};
	std::atomic<bool> captureFailed_{false};
	std::atomic<bool> playbackFailed_{false};
	std::atomic<bool> playbackCancelled_{false};
	std::atomic<int64_t> lastCaptureNs_{0};
	std::atomic<uint64_t> captureCallbacks_{0};
	std::atomic<uint64_t> captureFrames_{0};
	std::atomic<uint64_t> captureQueuedFrames_{0};
	std::atomic<uint64_t> captureSuppressedFrames_{0};
	std::atomic<uint64_t> captureDroppedFrames_{0};
	std::atomic<uint64_t> captureNullCallbacks_{0};
	std::atomic<uint64_t> captureFlushedFrames_{0};
	std::atomic<int64_t> captureRmsMicro_{0};
	std::atomic<int64_t> capturePeakMicro_{0};
	std::mutex captureWaitMutex_;
	std::condition_variable captureReady_;
	std::mutex playbackWaitMutex_;
	std::condition_variable playbackChanged_;
	std::mutex playbackWriteMutex_;
	Modem73ChannelAccess access_;
};
