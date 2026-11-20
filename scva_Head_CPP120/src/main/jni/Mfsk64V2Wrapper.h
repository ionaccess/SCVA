#ifndef SCVA_MFSK64_V2_WRAPPER_H
#define SCVA_MFSK64_V2_WRAPPER_H
#include <jni.h>

namespace scva_v2_mfsk64 {
bool configureReceive(JNIEnv* env, jclass modemClass,
                      bool selected, bool supported, bool debug);
bool receiveCharacter(int character, bool fromRsid);
bool transmit(JNIEnv* env, jclass modemClass,
              jbyteArray data, int length, int type);
}
#endif
