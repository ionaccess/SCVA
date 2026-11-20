#include "V2ModemWrapper.h"
#include "v2_fec/AndroidBridge.h"
#include "SCVA_Fldigi_Interface.h"
#include "Thor22V2Wrapper.h"
#include "Thor50x1V2Wrapper.h"
#include "Thor50x2V2Wrapper.h"
#include "Thor8V2Wrapper.h"
#include "Thor100V2Wrapper.h"
#include "Bpsk31V2Wrapper.h"
#include "Bpsk125V2Wrapper.h"
#include "Bpsk500V2Wrapper.h"
#include "Mfsk8V2Wrapper.h"
#include "Mfsk32V2Wrapper.h"
#include "Mfsk64V2Wrapper.h"
#include "Mfsk128V2Wrapper.h"

namespace scva_v2_modem {
bool configureReceive(JNIEnv* env, jclass modemClass,
                      bool selected, bool supported, bool debug) {
    const int mode = active_modem ? active_modem->get_mode() : MODE_NULL;
    switch (mode) {
    case MODE_THOR22:
        return scva_v2_thor22::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_THOR50x1:
        return scva_v2_thor50x1::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_THOR50x2:
        return scva_v2_thor50x2::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_THOR8:
        return scva_v2_thor8::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_THOR100:
        return scva_v2_thor100::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_PSK31:
        return scva_v2_bpsk31::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_PSK125:
        return scva_v2_bpsk125::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_PSK500:
        return scva_v2_bpsk500::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_MFSK8:
        return scva_v2_mfsk8::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_MFSK32:
        return scva_v2_mfsk32::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_MFSK64:
        return scva_v2_mfsk64::configureReceive(env, modemClass, selected, supported, debug);
    case MODE_MFSK128:
        return scva_v2_mfsk128::configureReceive(env, modemClass, selected, supported, debug);
    default:
        return scva_v2::configureReceive(env, modemClass, selected, supported, debug);
    }
}

bool receiveCharacter(int character, bool fromRsid) {
    const int mode = active_modem ? active_modem->get_mode() : MODE_NULL;
    switch (mode) {
    case MODE_THOR22:
        return scva_v2_thor22::receiveCharacter(character, fromRsid);
    case MODE_THOR50x1:
        return scva_v2_thor50x1::receiveCharacter(character, fromRsid);
    case MODE_THOR50x2:
        return scva_v2_thor50x2::receiveCharacter(character, fromRsid);
    case MODE_THOR8:
        return scva_v2_thor8::receiveCharacter(character, fromRsid);
    case MODE_THOR100:
        return scva_v2_thor100::receiveCharacter(character, fromRsid);
    case MODE_PSK31:
        return scva_v2_bpsk31::receiveCharacter(character, fromRsid);
    case MODE_PSK125:
        return scva_v2_bpsk125::receiveCharacter(character, fromRsid);
    case MODE_PSK500:
        return scva_v2_bpsk500::receiveCharacter(character, fromRsid);
    case MODE_MFSK8:
        return scva_v2_mfsk8::receiveCharacter(character, fromRsid);
    case MODE_MFSK32:
        return scva_v2_mfsk32::receiveCharacter(character, fromRsid);
    case MODE_MFSK64:
        return scva_v2_mfsk64::receiveCharacter(character, fromRsid);
    case MODE_MFSK128:
        return scva_v2_mfsk128::receiveCharacter(character, fromRsid);
    default:
        return scva_v2::receiveCharacter(character, fromRsid);
    }
}

bool transmit(JNIEnv* env, jclass modemClass,
              jbyteArray data, int length, int type) {
    const int mode = active_modem ? active_modem->get_mode() : MODE_NULL;
    switch (mode) {
    case MODE_THOR22:
        return scva_v2_thor22::transmit(env, modemClass, data, length, type);
    case MODE_THOR50x1:
        return scva_v2_thor50x1::transmit(env, modemClass, data, length, type);
    case MODE_THOR50x2:
        return scva_v2_thor50x2::transmit(env, modemClass, data, length, type);
    case MODE_THOR8:
        return scva_v2_thor8::transmit(env, modemClass, data, length, type);
    case MODE_THOR100:
        return scva_v2_thor100::transmit(env, modemClass, data, length, type);
    case MODE_PSK31:
        return scva_v2_bpsk31::transmit(env, modemClass, data, length, type);
    case MODE_PSK125:
        return scva_v2_bpsk125::transmit(env, modemClass, data, length, type);
    case MODE_PSK500:
        return scva_v2_bpsk500::transmit(env, modemClass, data, length, type);
    case MODE_MFSK8:
        return scva_v2_mfsk8::transmit(env, modemClass, data, length, type);
    case MODE_MFSK32:
        return scva_v2_mfsk32::transmit(env, modemClass, data, length, type);
    case MODE_MFSK64:
        return scva_v2_mfsk64::transmit(env, modemClass, data, length, type);
    case MODE_MFSK128:
        return scva_v2_mfsk128::transmit(env, modemClass, data, length, type);
    default:
        return scva_v2::transmit(env, modemClass, data, length, type);
    }
}
}
