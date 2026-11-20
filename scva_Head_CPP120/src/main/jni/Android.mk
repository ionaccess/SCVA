LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := ggwave_phone_core
LOCAL_CPP_EXTENSION := .cxx .cpp .cc
LOCAL_SRC_FILES := third_party/ggwave/src/ggwave.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/third_party/ggwave/include $(LOCAL_PATH)/third_party/ggwave/src
LOCAL_CPPFLAGS += -std=c++11
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := modem73_phone_core
LOCAL_CPP_EXTENSION := .cxx .cpp .cc
LOCAL_SRC_FILES := modem73_transport/Modem73Backend.cpp modem73_transport/Modem73ChannelAccess.cpp modem73_transport/Modem73Audio.cpp third_party/modem73/deps/miniaudio.c
LOCAL_CPPFLAGS += -std=c++17 -O2
LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/modem73_transport $(LOCAL_PATH)/third_party/modem73 $(LOCAL_PATH)/third_party/modem73/phy \
	$(LOCAL_PATH)/third_party/modem73/deps/aicodix/dsp $(LOCAL_PATH)/third_party/modem73/deps/aicodix/code $(LOCAL_PATH)/third_party/modem73/deps/aicodix/modem
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := SCVA_Modem_Interface
LOCAL_CPP_EXTENSION := .cxx .cpp .cc
LOCAL_SRC_FILES := SCVA_Fldigi_Interface.cpp V2ModemWrapper.cpp Thor22V2Wrapper.cpp Thor50x1V2Wrapper.cpp Thor50x2V2Wrapper.cpp Thor8V2Wrapper.cpp Thor100V2Wrapper.cpp Bpsk31V2Wrapper.cpp Bpsk125V2Wrapper.cpp Bpsk500V2Wrapper.cpp Mfsk8V2Wrapper.cpp Mfsk32V2Wrapper.cpp Mfsk64V2Wrapper.cpp Mfsk128V2Wrapper.cpp ggwave_transport/GgWaveBackend.cpp v2_fec/Transport.cpp v2_fec/AndroidBridge.cpp fldigi/modem.cxx \
			fldigi/psk/psk.cxx fldigi/psk/pskcoeff.cxx fldigi/psk/pskvaricode.cxx \
			fldigi/filters/filters.cxx fldigi/filters/viterbi.cxx fldigi/filters/fftfilt.cxx \
			fldigi/mfsk/interleave.cxx fldigi/mfsk/mfskvaricode.cxx fldigi/mfsk/mfsk.cxx \
			fldigi/misc/misc.cxx fldigi/misc/util.cxx fldigi/misc/configuration.cxx \
			fldigi/mt63/dsp.cxx fldigi/mt63/mt63base.cxx fldigi/mt63/mt63.cxx \
		    fldigi/rsid/rsid.cxx \
			fldigi/fft/fft.cxx \
			fldigi/dominoex/dominoex.cxx fldigi/dominoex/dominovar.cxx \
			fldigi/thor/thor.cxx fldigi/thor/thorvaricode.cxx \
			fldigi/olivia/olivia.cxx \
			fldigi/contestia/contestia.cxx
LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/fldigi/include $(LOCAL_PATH)/third_party/ggwave/include $(LOCAL_PATH)/third_party/ggwave/src $(LOCAL_PATH)/ggwave_transport $(LOCAL_PATH)/modem73_transport
LOCAL_LDLIBS  := -llog -ldl -lm
LOCAL_STATIC_LIBRARIES := ggwave_phone_core
LOCAL_WHOLE_STATIC_LIBRARIES := modem73_phone_core
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE    := SCVA_Flmsg_Interface
LOCAL_CPP_EXTENSION := .cxx .cpp .cc
LOCAL_SRC_FILES := 	SCVA_Flmsg_Interface.cpp flmsg/flmsg.cxx \
					flmsg/Fltk/fltk_extracts.cxx \
					flmsg/utils/status.cxx  \
					flmsg/utils/time_table.cxx \
					flmsg/utils/ext_string.cxx \
					flmsg/utils/base64.cxx flmsg/utils/base128.cxx flmsg/utils/base256.cxx \
					flmsg/lzma/Alloc.c flmsg/lzma/LzFind.c flmsg/lzma/LzmaDec.c \
					flmsg/lzma/LzmaEnc.c flmsg/lzma/LzmaLib.c \
					flmsg/utils/util.cxx flmsg/utils/wrap.cxx \
					flmsg/custom/custom.cxx \
					flmsg/radiogram/radiogram.cxx flmsg/radiogram/arl_msgs.cxx flmsg/radiogram/hx_msgs.cxx
LOCAL_C_INCLUDES := $(LOCAL_PATH)/flmsg/include
LOCAL_LDLIBS  := -llog
include $(BUILD_SHARED_LIBRARY)
