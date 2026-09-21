#ifndef DROIDDSP_FLDIGI_INTERFACE_H
#define DROIDDSP_FLDIGI_INTERFACE_H


#include <jni.h>
#include "rsid.h"
//@! Added log library
//#include <android/log.h>

using std::string;

extern modem *active_modem;
//@! Moved the following declaration
extern rs_fft_type aFFTAmpl[RSID_FFT_SIZE];
//@! Added the following declaration
extern rs_fft_type pwr[RSID_FFT_SIZE];

extern void txModulate(double *buffer, int len);
extern void put_rx_char(int receivedChar, bool fromRSID = false);
void put_utf8_char(int receivedChar);
extern void put_echo_char(unsigned int txedChar);
extern int  get_tx_char();
extern bool getNewAmplReady();
extern void updateWaterfallBuffer(double *processedFft);
void updatePower();
extern string getPreferenceS2(string preferenceString, string defaultValue);
extern int getPreferenceI(string preferenceString, int defaultValue);
extern double getPreferenceD(string preferenceString, double defaultValue);
extern bool getPreferenceB(string preferenceString, bool defaultValue);
extern bool setPreferenceS(string preferenceString, string newValue);
extern void change_CModem(int newMode, double newFrequency);
extern void androidShowRxViewer(int picW, int picH);
extern void androidSaveLastPicture();
extern void androidUpdateRxPic(int data, int pos);
void rsFECEncode();
bool rsFECDecode();
#endif
