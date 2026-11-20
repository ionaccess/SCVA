#include "Modem73Backend.h"

#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <new>
#include <string>
#include <unordered_map>

namespace {
using Clock = std::chrono::steady_clock;
int64_t elapsedNs(Clock::time_point start)
{
	return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
}
std::mutex& handleMutex() { static std::mutex value; return value; }
std::unordered_map<jlong, std::shared_ptr<Modem73CodecContext>>& handles()
{
	static std::unordered_map<jlong, std::shared_ptr<Modem73CodecContext>> value;
	return value;
}
std::atomic<jlong>& nextHandle() { static std::atomic<jlong> value{1}; return value; }
std::shared_ptr<Modem73CodecContext> acquire(jlong handle)
{
	std::lock_guard<std::mutex> lock(handleMutex());
	auto found = handles().find(handle);
	return found == handles().end() ? std::shared_ptr<Modem73CodecContext>() : found->second;
}
jlong publish(std::shared_ptr<Modem73CodecContext> context)
{
	jlong handle = nextHandle().fetch_add(1, std::memory_order_relaxed);
	if (handle <= 0) return 0;
	std::lock_guard<std::mutex> lock(handleMutex());
	handles().emplace(handle, std::move(context));
	return handle;
}
}

Modem73CodecContext::Modem73CodecContext(bool receive, int64_t callsign, int profileMode)
	: assemblies_(maxTncPayloadBytes(profileMode)), receive_(receive), callsign_(callsign), profileMode_(profileMode)
{
	const bool robust = profileMode_ == kRobustRdm600 || profileMode_ == kRobustRdmn300;
	if (robust && receive_)
	{
		robustDecoder_.reset(new (std::nothrow) RobustDecoder(kCenterFrequencyHz, profileMode_ == kRobustRdmn300));
		if (robustDecoder_) robustDecoder_->debug_log = false;
	}
	else if (robust)
		robustEncoder_.reset(new (std::nothrow) RobustEncoder());
	else if (receive_)
	{
		decoder_.reset(new (std::nothrow) Decoder48k());
		if (decoder_) decoder_->configure_frontend(kCenterFrequencyHz, true);
	}
	else
		encoder_.reset(new (std::nothrow) Encoder48k());
}

int Modem73CodecContext::maxTncPayloadBytes(int profileMode)
{
	if (profileMode == kBpskQuarterNormal) return 126; // 128 byte PHY frame minus length prefix
	if (profileMode == kQpskQuarterNormal) return 254; // 256 byte PHY frame minus length prefix
	if (profileMode == kNormalQpskHalfRate || profileMode == kRobustQpskQuarterLong ||
		profileMode == kRepeatedQpskQuarterLong || profileMode == kRobustRdm600 ||
		profileMode == kRobustRdmn300) return kMaxTncPayloadBytes;
	return 0;
}

std::unique_ptr<Modem73CodecContext> Modem73CodecContext::create(bool receive, const char* callsign, int profileMode)
{
	if (maxTncPayloadBytes(profileMode) == 0) return std::unique_ptr<Modem73CodecContext>();
	if (!receive && !ModemConfig::valid_callsign(callsign)) return std::unique_ptr<Modem73CodecContext>();
	int64_t encoded = receive ? 0 : ModemConfig::encode_callsign(callsign);
	if (!receive && encoded < 0) return std::unique_ptr<Modem73CodecContext>();
	std::unique_ptr<Modem73CodecContext> result(new (std::nothrow) Modem73CodecContext(receive, encoded, profileMode));
	if (!result || (receive ? (profileMode >= 0x10000 ? !result->robustDecoder_ : !result->decoder_)
		: (profileMode >= 0x10000 ? !result->robustEncoder_ : !result->encoder_))) return std::unique_ptr<Modem73CodecContext>();
	return result;
}

std::vector<float> Modem73CodecContext::encode(const uint8_t* payload, size_t length, EncodeTimingsNs* timings)
{
	if (receive_ || payload == nullptr || length < 1 || length > maxTncPayloadBytes(profileMode_)) return {};
	auto waitStart = Clock::now();
	std::lock_guard<std::mutex> contextLock(operationMutex_);
	int64_t waitNs = elapsedNs(waitStart);
	const size_t framedLength = length + 2;
	std::vector<uint8_t> framed(framedLength);
	framed[0] = static_cast<uint8_t>((length >> 8) & 0xff);
	framed[1] = static_cast<uint8_t>(length & 0xff);
	for (size_t i = 0; i < length; ++i) framed[i + 2] = payload[i];
	auto encodeStart = Clock::now();
	std::vector<float> result;
	if (robustEncoder_)
	{
		RobustMode mode = profileMode_ == kRobustRdmn300 ? RobustMode::RDMN_300 : RobustMode::RDM_600;
		result = robustEncoder_->encode(framed.data(), framed.size(), kCenterFrequencyHz, mode);
	}
	else result = encoder_->encode(framed.data(), framed.size(), kCenterFrequencyHz, callsign_, profileMode_, false);
	int64_t encodeNs = elapsedNs(encodeStart);
	encodeLockWaitNs_.store(waitNs, std::memory_order_release);
	encodeNs_.store(encodeNs, std::memory_order_release);
	if (timings != nullptr) { timings->lockWait = waitNs; timings->encode = encodeNs; }
	return result;
}

std::vector<std::vector<uint8_t>> Modem73CodecContext::feed(const float* samples, size_t count)
{
	std::vector<std::vector<uint8_t>> complete;
	if (!receive_ || (!decoder_ && !robustDecoder_) || samples == nullptr || count == 0) return complete;
	std::lock_guard<std::mutex> contextLock(operationMutex_);
	assemblies_.expire(scva_m73::nowMs());
	auto onFrame = [this, &complete](const uint8_t* data, size_t length) {
		decodedCallbacks_.fetch_add(1, std::memory_order_relaxed);
		const size_t expectedFrameBytes = profileMode_ >= 0x10000 ? 512 : maxTncPayloadBytes(profileMode_) + 2;
		if (data == nullptr || length != expectedFrameBytes) { rejectedFrames_.fetch_add(1, std::memory_order_relaxed); return; }
		size_t declared = (static_cast<size_t>(data[0]) << 8) | data[1];
		if (declared == 0 || declared > maxTncPayloadBytes(profileMode_) || declared > length - 2) { rejectedFrames_.fetch_add(1, std::memory_order_relaxed); return; }
		acceptedFrames_.fetch_add(1, std::memory_order_relaxed);
		std::vector<uint8_t> frame(data + 2, data + 2 + declared);
		std::string sender = robustDecoder_ ? "ROBUST" : (decoder_->last_call_fresh_ ? decoder_->last_call_ : "");
		auto first = sender.find_first_not_of(' ');
		sender = first == std::string::npos ? "" : sender.substr(first, sender.find_last_not_of(' ') - first + 1);
		if (!ModemConfig::valid_callsign(sender.c_str())) sender.clear();
		auto record = assemblies_.process(frame, sender, scva_m73::nowMs());
		if (!record.empty()) complete.push_back(std::move(record));
	};
	if (robustDecoder_) robustDecoder_->process(samples, count, onFrame);
	else decoder_->process(samples, count, onFrame);
	if (robustDecoder_) {
		syncCount_.store(robustDecoder_->stats_sync_count, std::memory_order_release);
		preambleErrors_.store(robustDecoder_->stats_preamble_errors, std::memory_order_release);
		symbolErrors_.store(-1, std::memory_order_release);
		crcErrors_.store(robustDecoder_->stats_crc_errors, std::memory_order_release);
		erasedSymbols_.store(-1, std::memory_order_release);
		lastSnrMilliDb_.store(static_cast<int64_t>(robustDecoder_->get_last_snr() * 1000.0), std::memory_order_release);
	} else {
		syncCount_.store(decoder_->stats_sync_count, std::memory_order_release);
		preambleErrors_.store(decoder_->stats_preamble_errors, std::memory_order_release);
		symbolErrors_.store(decoder_->stats_symbol_errors, std::memory_order_release);
		crcErrors_.store(decoder_->stats_crc_errors, std::memory_order_release);
		erasedSymbols_.store(decoder_->stats_erased_symbols, std::memory_order_release);
		lastSnrMilliDb_.store(static_cast<int64_t>(decoder_->get_last_snr() * 1000.0), std::memory_order_release);
	}
	return complete;
}

bool Modem73CodecContext::resetReceiver()
{
	if (!receive_) return false;
	if (robustDecoder_) {
		std::unique_ptr<RobustDecoder> replacement(new (std::nothrow) RobustDecoder(kCenterFrequencyHz, profileMode_ == kRobustRdmn300));
		if (!replacement) return false;
		replacement->debug_log = false;
		robustDecoder_.swap(replacement);
		syncCount_.store(0, std::memory_order_release); preambleErrors_.store(0, std::memory_order_release);
		symbolErrors_.store(-1, std::memory_order_release); crcErrors_.store(0, std::memory_order_release);
		erasedSymbols_.store(-1, std::memory_order_release); lastSnrMilliDb_.store(0, std::memory_order_release);
		return true;
	}
	std::unique_ptr<Decoder48k> replacement(new (std::nothrow) Decoder48k());
	if (!replacement) return false;
	replacement->configure_frontend(kCenterFrequencyHz, true);
	decoder_.swap(replacement);
	syncCount_.store(0, std::memory_order_release); preambleErrors_.store(0, std::memory_order_release);
	symbolErrors_.store(0, std::memory_order_release); crcErrors_.store(0, std::memory_order_release);
	erasedSymbols_.store(0, std::memory_order_release); lastSnrMilliDb_.store(0, std::memory_order_release);
	return true;
}

void Modem73CodecContext::receiveStats(int64_t values[9]) const
{
	if (values == nullptr) return;
	std::lock_guard<std::mutex> contextLock(operationMutex_);
	values[0] = static_cast<int64_t>(decodedCallbacks_.load(std::memory_order_acquire));
	values[1] = static_cast<int64_t>(acceptedFrames_.load(std::memory_order_acquire));
	values[2] = static_cast<int64_t>(rejectedFrames_.load(std::memory_order_acquire));
	values[3] = syncCount_.load(std::memory_order_acquire);
	values[4] = preambleErrors_.load(std::memory_order_acquire);
	values[5] = symbolErrors_.load(std::memory_order_acquire);
	values[6] = crcErrors_.load(std::memory_order_acquire);
	values[7] = erasedSymbols_.load(std::memory_order_acquire);
	values[8] = lastSnrMilliDb_.load(std::memory_order_acquire);
}

void Modem73CodecContext::setJniCopyTimings(int64_t inputCopyNs, int64_t outputCopyNs)
{
	jniInputCopyNs_.store(inputCopyNs, std::memory_order_release);
	jniOutputCopyNs_.store(outputCopyNs, std::memory_order_release);
}

void Modem73CodecContext::encodeTimings(int64_t values[4]) const
{
	if (values == nullptr) return;
	values[0] = encodeLockWaitNs_.load(std::memory_order_acquire);
	values[1] = encodeNs_.load(std::memory_order_acquire);
	values[2] = jniInputCopyNs_.load(std::memory_order_acquire);
	values[3] = jniOutputCopyNs_.load(std::memory_order_acquire);
}

static void throwIllegalArgument(JNIEnv* env, const char* message)
{
	if (env->ExceptionCheck()) return;
	jclass cls = env->FindClass("java/lang/IllegalArgumentException");
	if (cls != nullptr) env->ThrowNew(cls, message);
}

static void throwIllegalState(JNIEnv* env, const char* message)
{
	if (env->ExceptionCheck()) return;
	jclass cls = env->FindClass("java/lang/IllegalStateException");
	if (cls != nullptr) env->ThrowNew(cls, message);
}

extern "C" JNIEXPORT jlong JNICALL Java_com_SCVA_Modem73Backend_nativeCreate(JNIEnv* env, jclass,
		jboolean receive, jstring callsignValue, jint profileMode)
{
	if (env->ExceptionCheck()) return 0;
	std::string callsign;
	if (!receive)
	{
		if (callsignValue == nullptr) { throwIllegalArgument(env, "MODEM73 callsign is required"); return 0; }
		const char* chars = env->GetStringUTFChars(callsignValue, nullptr);
		if (chars == nullptr) return 0;
		callsign.assign(chars);
		env->ReleaseStringUTFChars(callsignValue, chars);
		if (env->ExceptionCheck()) return 0;
		if (!ModemConfig::valid_callsign(callsign.c_str())) { throwIllegalArgument(env, "Invalid MODEM73 callsign"); return 0; }
	}
	auto uniqueContext = Modem73CodecContext::create(receive == JNI_TRUE, callsign.c_str(), profileMode);
	std::shared_ptr<Modem73CodecContext> context(std::move(uniqueContext));
	if (!context) { throwIllegalState(env, "MODEM73 codec initialization failed"); return 0; }
	jlong handle = publish(std::move(context));
	if (handle == 0) throwIllegalState(env, "MODEM73 codec handle space exhausted");
	return handle;
}

extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeClose(JNIEnv*, jclass, jlong handle)
{
	std::lock_guard<std::mutex> lock(handleMutex());
	handles().erase(handle);
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_SCVA_Modem73Backend_nativeEncode(JNIEnv* env, jclass, jlong handle, jbyteArray payload)
{
	if (env->ExceptionCheck()) return nullptr;
	if (handle == 0 || payload == nullptr) { throwIllegalArgument(env, "MODEM73 payload is required"); return nullptr; }
	jsize length = env->GetArrayLength(payload);
	if (length < 1 || length > Modem73CodecContext::kMaxTncPayloadBytes)
	{
		throwIllegalArgument(env, "MODEM73 frame must contain 1..510 bytes"); return nullptr;
	}
	std::vector<uint8_t> bytes(static_cast<size_t>(length));
	auto inputCopyStart = Clock::now();
	env->GetByteArrayRegion(payload, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
	if (env->ExceptionCheck()) return nullptr;
	int64_t inputCopyNs = elapsedNs(inputCopyStart);
	auto context = acquire(handle);
	if (!context) { throwIllegalArgument(env, "MODEM73 codec handle is unavailable"); return nullptr; }
	std::vector<float> samples;
	samples = context->encode(bytes.data(), bytes.size(), nullptr);
	if (env->ExceptionCheck()) return nullptr;
	if (samples.empty()) { throwIllegalState(env, "MODEM73 encoding failed"); return nullptr; }
	jfloatArray result = env->NewFloatArray(static_cast<jsize>(samples.size()));
	if (result == nullptr || env->ExceptionCheck()) return nullptr;
	auto outputCopyStart = Clock::now();
	env->SetFloatArrayRegion(result, 0, static_cast<jsize>(samples.size()), samples.data());
	context->setJniCopyTimings(inputCopyNs, elapsedNs(outputCopyStart));
	return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_SCVA_Modem73Backend_nativeResetRx(JNIEnv* env, jclass, jlong handle)
{
	if (env->ExceptionCheck()) return JNI_FALSE;
	auto context = acquire(handle);
	if (!context) { throwIllegalArgument(env, "MODEM73 receiver handle is unavailable"); return JNI_FALSE; }
	return context->resetReceiver() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL Java_com_SCVA_Modem73Backend_nativeFeed(JNIEnv* env, jclass, jlong handle, jfloatArray input, jint count)
{
	if (env->ExceptionCheck()) return nullptr;
	if (handle == 0 || input == nullptr) { throwIllegalArgument(env, "MODEM73 capture samples are required"); return nullptr; }
	jsize arrayLength = env->GetArrayLength(input);
	if (count < 0 || count > arrayLength) { throwIllegalArgument(env, "Invalid MODEM73 sample count"); return nullptr; }
	if (count == 0) return env->NewObjectArray(0, env->FindClass("[B"), nullptr);
	std::vector<float> samples(static_cast<size_t>(count));
	env->GetFloatArrayRegion(input, 0, count, samples.data());
	if (env->ExceptionCheck()) return nullptr;
	auto context = acquire(handle);
	if (!context) { throwIllegalArgument(env, "MODEM73 receiver handle is unavailable"); return nullptr; }
	std::vector<std::vector<uint8_t>> packets = context->feed(samples.data(), samples.size());
	if (env->ExceptionCheck()) return nullptr;
	jclass byteArrayClass = env->FindClass("[B");
	if (byteArrayClass == nullptr || env->ExceptionCheck()) return nullptr;
	jobjectArray result = env->NewObjectArray(static_cast<jsize>(packets.size()), byteArrayClass, nullptr);
	if (result == nullptr || env->ExceptionCheck()) return nullptr;
	for (size_t i = 0; i < packets.size(); ++i)
	{
		jbyteArray packet = env->NewByteArray(static_cast<jsize>(packets[i].size()));
		if (packet == nullptr || env->ExceptionCheck()) return nullptr;
		env->SetByteArrayRegion(packet, 0, static_cast<jsize>(packets[i].size()), reinterpret_cast<const jbyte*>(packets[i].data()));
		if (env->ExceptionCheck()) { env->DeleteLocalRef(packet); return nullptr; }
		env->SetObjectArrayElement(result, static_cast<jsize>(i), packet);
		env->DeleteLocalRef(packet);
		if (env->ExceptionCheck()) return nullptr;
	}
	return result;
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_SCVA_Modem73Backend_nativeCodecStats(JNIEnv* env, jclass, jlong handle)
{
	if (env->ExceptionCheck()) return nullptr;
	if (handle == 0) { throwIllegalArgument(env, "MODEM73 codec handle is unavailable"); return nullptr; }
	auto context = acquire(handle);
	if (!context) { throwIllegalArgument(env, "MODEM73 codec handle is unavailable"); return nullptr; }
	int64_t values[9] = {};
	context->receiveStats(values);
	jlong converted[9];
	for (int i = 0; i < 9; ++i) converted[i] = static_cast<jlong>(values[i]);
	jlongArray result = env->NewLongArray(9);
	if (result == nullptr || env->ExceptionCheck()) return nullptr;
	env->SetLongArrayRegion(result, 0, 9, converted);
	return env->ExceptionCheck() ? nullptr : result;
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_SCVA_Modem73Backend_nativeEncodeTimings(JNIEnv* env, jclass, jlong handle)
{
	if (env->ExceptionCheck()) return nullptr;
	auto context = acquire(handle);
	if (!context) { throwIllegalArgument(env, "MODEM73 codec handle is unavailable"); return nullptr; }
	int64_t values[4] = {};
	context->encodeTimings(values);
	jlong converted[4] = {static_cast<jlong>(values[0]), static_cast<jlong>(values[1]), static_cast<jlong>(values[2]), static_cast<jlong>(values[3])};
	jlongArray result = env->NewLongArray(4);
	if (result == nullptr || env->ExceptionCheck()) return nullptr;
	env->SetLongArrayRegion(result, 0, 4, converted);
	return env->ExceptionCheck() ? nullptr : result;
}

void Modem73CodecContext::expireAssemblies() {
 std::lock_guard<std::mutex> lock(operationMutex_);assemblies_.expire(scva_m73::nowMs());
}
void Modem73CodecContext::assemblyStats(int64_t v[9]) {
 std::lock_guard<std::mutex> lock(operationMutex_);
 v[0]=assemblies_.count();v[1]=assemblies_.bytes();v[2]=assemblies_.completed;v[3]=assemblies_.rejected;v[4]=assemblies_.expired;v[5]=assemblies_.lastId;v[6]=assemblies_.lastSeq;v[7]=assemblies_.status;v[8]=assemblies_.lastReject;
}
extern "C" JNIEXPORT void JNICALL Java_com_SCVA_Modem73Backend_nativeExpire(JNIEnv* env,jclass,jlong handle) {
 auto c=acquire(handle);if(!c){throwIllegalArgument(env,"MODEM73 receiver unavailable");return;}c->expireAssemblies();
}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_SCVA_Modem73Backend_nativeAssemblyStats(JNIEnv* env,jclass,jlong handle) {
 auto c=acquire(handle);if(!c){throwIllegalArgument(env,"MODEM73 receiver unavailable");return nullptr;}int64_t v[9]={};c->assemblyStats(v);jlong j[9];for(int i=0;i<9;i++)j[i]=v[i];auto result=env->NewLongArray(9);if(result)env->SetLongArrayRegion(result,0,9,j);return result;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_com_SCVA_Modem73Backend_nativePacketize(JNIEnv* env,jclass,jbyteArray input,jint profileMode) {
 if(!input||env->ExceptionCheck())return nullptr;jsize n=env->GetArrayLength(input);
 if(n<1||n>int(scva_m73::kRecordLimit)){throwIllegalArgument(env,"MODEM73 complete record exceeds 2020 bytes");return nullptr;}
 std::vector<uint8_t> bytes(n);env->GetByteArrayRegion(input,0,n,reinterpret_cast<jbyte*>(bytes.data()));if(env->ExceptionCheck())return nullptr;
	 auto parts=scva_m73::packetize(bytes, Modem73CodecContext::maxTncPayloadBytes(profileMode));if(parts.empty()){throwIllegalArgument(env,"MODEM73 record exceeds selected profile packet capacity");return nullptr;}jclass type=env->FindClass("[B");if(!type)return nullptr;
 auto result=env->NewObjectArray(parts.size(),type,nullptr);if(!result)return nullptr;
 for(size_t i=0;i<parts.size();i++){auto part=env->NewByteArray(parts[i].size());if(!part)return nullptr;env->SetByteArrayRegion(part,0,parts[i].size(),reinterpret_cast<const jbyte*>(parts[i].data()));if(env->ExceptionCheck())return nullptr;env->SetObjectArrayElement(result,i,part);env->DeleteLocalRef(part);if(env->ExceptionCheck())return nullptr;}return result;
}
