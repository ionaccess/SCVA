#pragma once

#include <cstdint>
#include <array>
#include <atomic>
#include <memory>
#include <mutex>
#include <vector>

#include "modem.hh"
#include "phy/robust_modem.hh"
#include "Modem73Fragments.h"

class Modem73CodecContext
{
public:
	struct EncodeTimingsNs { int64_t lockWait = 0, encode = 0; };
	static constexpr int kSampleRate = 48000;
	static constexpr int kCenterFrequencyHz = 1500;
	static constexpr int kMaxTncPayloadBytes = 510;
	static constexpr int kNormalQpskHalfRate = 0x11;
	static constexpr int kRobustQpskQuarterLong = 0x99;
	static constexpr int kRepeatedQpskQuarterLong = 0x9d;
	static constexpr int kQpskQuarterNormal = 0x19;
	static constexpr int kBpskQuarterNormal = 0x09;
	static constexpr int kRobustRdm600 = 0x10001;
	static constexpr int kRobustRdmn300 = 0x10003;

	static std::unique_ptr<Modem73CodecContext> create(bool receive, const char* callsign, int profileMode);
	std::vector<float> encode(const uint8_t* payload, size_t length, EncodeTimingsNs* timings = nullptr);
	std::vector<std::vector<uint8_t>> feed(const float* samples, size_t count);
	bool resetReceiver();
	void receiveStats(int64_t values[9]) const;
	void expireAssemblies();
	void assemblyStats(int64_t values[9]);
	void setJniCopyTimings(int64_t inputCopyNs, int64_t outputCopyNs);
	void encodeTimings(int64_t values[4]) const;
	static int maxTncPayloadBytes(int profileMode);

private:
	Modem73CodecContext(bool receive, int64_t callsign, int profileMode);
	scva_m73::Assemblies assemblies_;
	bool receive_;
	int64_t callsign_;
	int profileMode_;
	mutable std::mutex operationMutex_;
	std::unique_ptr<Encoder48k> encoder_;
	std::unique_ptr<Decoder48k> decoder_;
	std::unique_ptr<RobustEncoder> robustEncoder_;
	std::unique_ptr<RobustDecoder> robustDecoder_;
	std::atomic<uint64_t> decodedCallbacks_{0};
	std::atomic<uint64_t> acceptedFrames_{0};
	std::atomic<uint64_t> rejectedFrames_{0};
	std::atomic<int64_t> syncCount_{0}, preambleErrors_{0}, symbolErrors_{0}, crcErrors_{0}, erasedSymbols_{0}, lastSnrMilliDb_{0};
	std::atomic<int64_t> encodeLockWaitNs_{0}, encodeNs_{0}, jniInputCopyNs_{0}, jniOutputCopyNs_{0};
};
