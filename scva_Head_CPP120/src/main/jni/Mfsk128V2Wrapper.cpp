#include "Mfsk128V2Wrapper.h"
#include "v2_fec/AndroidBridge.h"

namespace scva_v2_mfsk128 {
bool configureReceive(JNIEnv* env, jclass modemClass,
                      bool selected, bool supported, bool debug) {
    return scva_v2::configureReceive(env, modemClass, selected, supported, debug);
}

bool receiveCharacter(int character, bool fromRsid) {
    return scva_v2::receiveCharacter(character, fromRsid);
}

bool transmit(JNIEnv* env, jclass modemClass,
              jbyteArray data, int length, int type) {
    return scva_v2::transmit(env, modemClass, data, length, type);
}
}
