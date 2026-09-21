#ifndef SCVA_V2_ANDROID_BRIDGE_H
#define SCVA_V2_ANDROID_BRIDGE_H
#include <jni.h>
namespace scva_v2 {
// Called only by the serialized modem RX/TX worker, never the UI thread.
bool configureReceive(JNIEnv*,jclass,bool selected,bool supported,bool debug);
bool receiveCharacter(int,bool fromRsid);
void resetReceive();
void endReceive();
bool transmit(JNIEnv*,jclass,jbyteArray,int,int type);
}
#endif
