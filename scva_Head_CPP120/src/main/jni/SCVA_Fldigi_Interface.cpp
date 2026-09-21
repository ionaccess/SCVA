/*
 *  Based on Fldigi source code version 3.21.82
 *
 *
 * Copyright (C) 2014 John Douyere (VK2ETA)
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */
#include "SCVA_Fldigi_Interface.h"
//#include "modem.h"
#include "psk.h"
#include "mt63.h"
#include "thor.h"
#include "mfsk.h"
#include "olivia.h"
#include "contestia.h"
#include "dominoex.h"
#include "rsid.h"
#include <jni.h>

#include <ezpwd/rs>
#include <ezpwd/output>
#include <android/log.h>
#include <ctype.h>

#include <algorithm>    // std::shuffle
#include "rs_decode_checked.h"
#include "v2_fec/AndroidBridge.h"
#include <random>       // std::default_random_engine
#include <chrono>       // std::chrono::system_clock

using std::string;

cRsId *RsidModem = NULL;
modem *active_modem = NULL;
//@! Moved the following declaration
rs_fft_type aFFTAmpl[RSID_FFT_SIZE] = {0};
//@! Added the following declaration
rs_fft_type pwr[RSID_FFT_SIZE] = {0};
char decodedBuffer[65536];
char utf8PartialBuffer[4];
int utfExpectedChars = 0;
int utfFoundChars = 0;
int lastCharPos;
JNIEnv *gEnv = NULL;
jobject gJobject = NULL;
//Separate variable for Tx thread as Env are thread sensitive
//JNIEnv* txEnv = NULL;
//For Tx buffer handling
signed char *txDataBuffer = NULL;
int txDataBufferLength = 0;
int txCounter = 0;
static bool forcePlainTextTx = false;
//MFSK Picture Rx/Tx
int pictureRow[4096 * 3];
int pictureW = 0;
int pictureH = 0;

//VK2ETA Needs to be made bullet proof
double audioBuffer[24000]; //3 seconds of audio, in case we have a delay in the Java rx thread
int sizeOfAudioBuffer = sizeof(audioBuffer) / sizeof(int);
int audioBufferIndex;

// Reed-Solomon Forward Error Correction
std::vector<int8_t> rxCodeword;
int rxCodewordIndex = 0;
string startSequence;
string stopSequence;
int startSequenceIndex = 0;
bool codewordReceiving = false;
// Reed-Solomon Special Codes
ezpwd::RS<15, 15 - 8> rs158;
ezpwd::RS<15, 15 - 4> rs154;
ezpwd::RS<15, 15 - 2> rs152;
ezpwd::RS<31, 31 - 16> rs3116;
ezpwd::RS<31, 31 - 8> rs318;
ezpwd::RS<31, 31 - 4> rs314;
ezpwd::RS<63, 63 - 32> rs6332;
ezpwd::RS<63, 63 - 16> rs6316;
ezpwd::RS<63, 63 - 8> rs638;
ezpwd::RS<127, 127 - 64> rs12764;
ezpwd::RS<127, 127 - 32> rs12732;
ezpwd::RS<127, 127 - 16> rs12716;
ezpwd::RS<255, 255 - 128> rs255128;
ezpwd::RS<255, 255 - 64> rs25564;
ezpwd::RS<255, 255 - 32> rs25532;


//***************** FROM C++ to JAVA ******************

//Get the value of Java boolean static variable newAmplReady of the Java Modem class
extern bool getNewAmplReady() {
    jclass myClass = NULL;
    myClass = gEnv->FindClass("com/SCVA/Modem");
    jfieldID myFid = NULL;
    //Find Java field newAmplReady of type boolean
    myFid = gEnv->GetStaticFieldID(myClass, "newAmplReady", "Z");
    bool newAmplReady = gEnv->GetStaticBooleanField(myClass, myFid);
    return newAmplReady;
    return true;
}

//Update the waterfall array of amplitudes in Java class Modem
extern void updateWaterfallBuffer(double *aFFTAmpl) {
    //Find Java Modem class
    jclass cls = gEnv->FindClass("com/SCVA/Modem");
    //Find Static Java method updateWaterfall (argument is an array of double and returns Void)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "updateWaterfall", "([D)V");
    //Create a Java array and copy the C++ array into it
    jdoubleArray jaFFTAmpl = gEnv->NewDoubleArray(RSID_FFT_SIZE);
    gEnv->SetDoubleArrayRegion(jaFFTAmpl, 0, RSID_FFT_SIZE, aFFTAmpl);
    //Call the method with the Java array
    gEnv->CallStaticVoidMethod(cls, mid, jaFFTAmpl);
    //Release the intermediate array.
    gEnv->DeleteLocalRef(jaFFTAmpl);
}

//Update the pwr array
void updatePower() {
    std::memcpy(pwr, aFFTAmpl, RSID_FFT_SIZE * sizeof(rs_fft_type));
}

//Access to Java config class methods for accessing String preferences
extern string getPreferenceS(string preferenceString, string defaultValue) {

    jobject returnedObject;
    jstring jprefStr;
    jstring jdefVal;

    //Check that we have valid data
    if (gEnv == NULL) return NULL;
    if (gJobject == NULL) return NULL;

    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/config");

    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "getPreferenceS",
                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");

    //Convert strings to jstrings
    jprefStr = gEnv->NewStringUTF(preferenceString.c_str());
    jdefVal = gEnv->NewStringUTF(defaultValue.c_str());
    //Call the method
    returnedObject = gEnv->CallStaticObjectMethod(cls, mid, jprefStr, jdefVal);
    //Convert the returned jstring to C string
    const char *str = gEnv->GetStringUTFChars((jstring) returnedObject, NULL);

    //Release the intermediate variable
    gEnv->DeleteLocalRef(returnedObject);
    gEnv->DeleteLocalRef(jprefStr);
    gEnv->DeleteLocalRef(jdefVal);

    //Return the result...that simple...phew
    return str;
}


//Access to Java config class methods for accessing Boolean preferences
extern bool getPreferenceB(string preferenceString, bool defaultValue) {
    jboolean returnedValue;
    jstring jprefStr;

    //Check that we have valid data
    if (gEnv == NULL) return false;
    if (gJobject == NULL) return false;

    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/config");

    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "getPreferenceB", "(Ljava/lang/String;Z)Z");

    //Convert strings to jstrings
    jprefStr = gEnv->NewStringUTF(preferenceString.c_str());
    jboolean jDefault = defaultValue;
    //Call the method
    returnedValue = gEnv->CallStaticBooleanMethod(cls, mid, jprefStr, jDefault);

    //Release the intermediate variable
    gEnv->DeleteLocalRef(jprefStr);

    //Return the result
    return returnedValue;
}


//Access to Java config class methods for accessing Double preferences
extern double getPreferenceD(string preferenceString, double defaultValue) {
    jdouble returnedValue;
    jstring jprefStr;

    //Check that we have valid data
    if (gEnv == NULL) return 0.0f;
    if (gJobject == NULL) return 0.0f;

    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/config");

    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "getPreferenceD", "(Ljava/lang/String;D)D");

    //Convert strings to jstrings
    jprefStr = gEnv->NewStringUTF(preferenceString.c_str());
    jdouble jDefault = defaultValue;
    //Call the method
    returnedValue = gEnv->CallStaticDoubleMethod(cls, mid, jprefStr, jDefault);

    //Release the intermediate variable
    gEnv->DeleteLocalRef(jprefStr);

    //Return the result
    return returnedValue;
}


//Access to Java config class methods for accessing Int preferences
extern int getPreferenceI(string preferenceString, int defaultValue) {
    jint returnedValue;
    jstring jprefStr;

    //Check that we have valid data
    if (gEnv == NULL) return 0;
    if (gJobject == NULL) return 0;

    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/config");

    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "getPreferenceI", "(Ljava/lang/String;I)I");

    //Convert strings to jstrings
    jprefStr = gEnv->NewStringUTF(preferenceString.c_str());
    jint jDefault = defaultValue;
    //Call the method
    returnedValue = gEnv->CallStaticIntMethod(cls, mid, jprefStr, jDefault);

    //Release the intermediate variable
    gEnv->DeleteLocalRef(jprefStr);

    //Return the result
    return returnedValue;
}

//Access to Java config class methods for setting Int preferences
extern bool setPreferenceS(string preferenceString, string newValue) {
    jboolean returnedValue;
    jstring jprefStr;
    jstring jnewVal;

    //Check that we have valid data
    if (gEnv == NULL) return false;
    if (gJobject == NULL) return false;

    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/config");

    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "setPreferenceS",
                                            "(Ljava/lang/String;Ljava/lang/String;)Z");

    //Convert strings to jstrings
    jprefStr = gEnv->NewStringUTF(preferenceString.c_str());
    jnewVal = gEnv->NewStringUTF(newValue.c_str());
    //Call the method
    returnedValue = gEnv->CallStaticBooleanMethod(cls, mid, jprefStr, jnewVal);

    //Release the intermediate variable
    gEnv->DeleteLocalRef(jprefStr);
    gEnv->DeleteLocalRef(jnewVal);

    //Return the result
    return returnedValue;
}

//Access to Android make toast
extern void makeToast(string message) {
    jstring jtext;

    //Check that we have valid data
    if (gEnv == NULL) return;
    if (gJobject == NULL) return;

    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/activities/UserChatActivity");

    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "logFromC", "(Ljava/lang/String;)V");

    //Convert string to jstring
    jtext = gEnv->NewStringUTF(message.c_str());
    //Call the method
    gEnv->CallStaticVoidMethod(cls, mid, jtext);

    //Release the intermediate variable
    gEnv->DeleteLocalRef(jtext);
}

//RX Section of the C++ modems
static bool isV2SupportedThorMode() {
    return active_modem &&
           (active_modem->get_mode() == MODE_THOR22 ||
            active_modem->get_mode() == MODE_THOR50x1);
}

extern void put_rx_char(int receivedChar, bool fromRSID) {
    if (scva_v2::receiveCharacter(receivedChar, fromRSID)) return;
    if (!getPreferenceB("RSFEC", false) || fromRSID) {
        put_utf8_char(receivedChar);

        rxCodewordIndex = 0;
        startSequenceIndex = 0;
    } else {
        if (getPreferenceB("RSFEC_DEBUG", false)) {
            static char hexChars[16] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
                                        'C', 'D', 'E', 'F'};
            if (isprint(receivedChar)) {
                decodedBuffer[lastCharPos++] = receivedChar;
            } else {
                decodedBuffer[lastCharPos++] = '<';
                decodedBuffer[lastCharPos++] = hexChars[(receivedChar & 0xF0) >> 4];
                decodedBuffer[lastCharPos++] = hexChars[receivedChar & 0x0F];
                decodedBuffer[lastCharPos++] = '>';
            }
        }
        //Reed-Solomon FEC Decode
        if (receivedChar == startSequence[startSequenceIndex]) {
            startSequenceIndex++;
        } else {
            startSequenceIndex = 0;
        }
        if (startSequenceIndex >= startSequence.length()) {
            if (codewordReceiving && rxCodewordIndex > 0) {
                makeToast("Invalid Codeword");
            }
            startSequenceIndex = 0;
            rxCodewordIndex = 0;
            rxCodeword.clear();
            rxCodeword.resize(getPreferenceI("RSFEC_BLOCKLENGTH", 31) + stopSequence.length());
            codewordReceiving = true;
            return;
        }
        if (codewordReceiving) {
            rxCodeword[rxCodewordIndex++] = receivedChar;
        }
        //__android_log_print(ANDROID_LOG_VERBOSE, "rsFECDecode", "rxCodeword[%d]: %x, %c\n", rxCodewordIndex, receivedChar, receivedChar);

//        // 1. Create a safe buffer for the formatted text
//        char buffer[128];
//
//// 2    //. Extract the byte cleanly as an unsigned integer to prevent sign extension
//        unsigned char safeChar = (unsigned char)receivedChar;
//
//// 3    //. Format WITHOUT using %c (which creates the illegal UTF-8 byte)
//// W    //e print the character index, its hex value, and its integer value instead.
//        std::snprintf(buffer, sizeof(buffer), "rxCodeword[%d]: 0x%02X (dec: %d)\n",
//                      rxCodewordIndex,
//                      safeChar,
//                      safeChar);
//
//// 4    //. Send the perfectly safe C++ string to makeToast
//        std::string toastMessage(buffer);
//        makeToast(toastMessage);

        char buffer[128];

        // If the character falls out of normal ASCII bounds, clean it up for the string renderer
        char safePrintChar = (receivedChar >= 32 && receivedChar <= 126) ? receivedChar : '?';

        std::snprintf(buffer, sizeof(buffer), "%c",
//                      rxCodewordIndex,  //[%d]: 0x%02x,
//                      (unsigned char)receivedChar,
                      safePrintChar);

        std::string toastMessage(buffer);
        makeToast(toastMessage);


        if (rxCodewordIndex >= getPreferenceI("RSFEC_BLOCKLENGTH", 31) + stopSequence.length()) {
            bool validCodeword = true;
            for (int i = 0; i < stopSequence.length(); i++) {
                if (rxCodeword[getPreferenceI("RSFEC_BLOCKLENGTH", 31) + i] != stopSequence[i]) {
                    validCodeword = false;
                }
            }
            if (!validCodeword) {
                makeToast("Invalid Codeword");
            } else {
                rxCodeword.resize(getPreferenceI("RSFEC_BLOCKLENGTH", 31));
                if (rsFECDecode()) {
                    for (int i = 0; i < rxCodeword.size(); i++) {
                        put_utf8_char(rxCodeword[i]);
                    }
                } else {
                    makeToast("RS decode failed; block discarded");
                }
            }
            rxCodewordIndex = 0;
            rxCodeword.clear();
            codewordReceiving = false;
        }
    }
}

void put_utf8_char(int receivedChar) {
    //Only send complete UTF-8 sequences to Java
    //Behaviour on invalid combinations: discard and re-sync only on valid characters to
    //  avoid exceptions in upstream methods
    //decodedBuffer[lastCharPos++] = receivedChar & 0x7f;
    if (utfExpectedChars < 1) { //Normally zero
        //We are not in a multi-byte sequence yet
        if ((receivedChar & 0xE0) == 0xC0) {
            utfExpectedChars = 1; //One extra characters
            utfFoundChars = 0;
            utf8PartialBuffer[utfFoundChars++] = receivedChar;
        } else if ((receivedChar & 0xF0) == 0xE0) {
            utfExpectedChars = 2; //Two extra characters
            utfFoundChars = 0;
            utf8PartialBuffer[utfFoundChars++] = receivedChar;
        } else if ((receivedChar & 0xF8) == 0xF0) {
            utfExpectedChars = 3; //Three extra characters
            utfFoundChars = 0;
            utf8PartialBuffer[utfFoundChars++] = receivedChar;
        } else if ((receivedChar & 0xC0) == 0x80) { //Is it a follow-on character?
            //Should not be there (missing first character in sequence), discard and reset just in case
            utfExpectedChars = utfFoundChars = 0;
        } else if ((receivedChar & 0x80) ==
                   0x00) { //Could be a single Chareacter, check it is legal
            decodedBuffer[lastCharPos++] = receivedChar;
            utfExpectedChars = utfFoundChars = 0; //Reset to zero in case counter is negative (just in case)
        } else { //Not a legal case, ignore and reset counter
            utfExpectedChars = utfFoundChars = 0; //Reset to zero in case counter is negative (just in case)
        }
    } else { //we are still expecting follow-up UTF-8 characters
        if ((receivedChar & 0xC0) == 0x80) { //Valid follow-on character, store it
            utfExpectedChars--;
            utf8PartialBuffer[utfFoundChars++] = receivedChar;
        } else { //Invalid sequence, discard it and start from scratch
            utfExpectedChars = utfFoundChars = 0;
        }
        //If we have a complete sequence, add to receive buffer
        if (utfExpectedChars < 1 && utfFoundChars > 0) {
            for (int i = 0; i < utfFoundChars; i++) {
                decodedBuffer[lastCharPos++] = utf8PartialBuffer[i];
            }
            utfExpectedChars = utfFoundChars = 0; //Reset
        }
    }
/* Code for Debugging
 * 		decodedBuffer[lastCharPos++] = '(';
		decodedBuffer[lastCharPos++] = 'U';
		decodedBuffer[lastCharPos++] = ')';
		//debug
		 	 	 char hexstr[10];
			 	 int cvlen = sprintf(hexstr, "%0004x", utf8PartialBuffer[i]);
				decodedBuffer[lastCharPos++] = '[';
				for (int i = 0; i < cvlen; i++) {
					decodedBuffer[lastCharPos++] = hexstr[i];
				}
				decodedBuffer[lastCharPos++] = ']';
 *
*/
}

//Call the Java class that echoes the transmitted characters
extern void put_echo_char(unsigned int txedChar) {
    //  Do nothing for now
    //  Find Java Modem class
//    jclass cls = gEnv->FindClass("com/SCVA/Modem");
    //  Find Static Java method updateWaterfall (argument is an array of double and returns Void)
//    jmethodID mid = gEnv->GetStaticMethodID(cls, "putEchoChar", "(I)V");
    //  Call the method
//    gEnv->CallStaticVoidMethod(cls, mid, (txedChar & 0x7F));
}


//Prepare a new Picture receiving file and display screen for MFSK picture Rx
void androidShowRxViewer(int picW, int picH) {
    //Save picture size for later
    pictureW = picW;
    pictureH = picH;
    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/Modem");
    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "showRxViewer", "(II)V");
    //Call the method with the parameters
    gEnv->CallStaticVoidMethod(cls, mid, picW, picH);
    //Release the variables
    gEnv->DeleteLocalRef(cls);
}


//Save the last Picture to file (MFSK picture Rx)
void androidSaveLastPicture() {
    //Find the Java class
    jclass cls = gEnv->FindClass("com/SCVA/Modem");
    //Find the static Java method (see signature)
    jmethodID mid = gEnv->GetStaticMethodID(cls, "saveLastPicture", "()V");
    //Call the method with the parameters
    gEnv->CallStaticVoidMethod(cls, mid);
    //Release the variables
    gEnv->DeleteLocalRef(cls);

}


//Update one datum of Current Rx picture(MFSK picture Rx)
void androidUpdateRxPic(int data, int pixelNumber) {

    if (pixelNumber < 4096 * 3) {
        pictureRow[pixelNumber] = data;
    }
    if (pixelNumber == 3 * pictureW - 1) { //Full row of bytes (3 per pixels), update bitmap.
        //Find the Java class
        jclass cls = gEnv->FindClass("com/SCVA/Modem");
        //Find the static Java method (see signature)
        jmethodID mid = gEnv->GetStaticMethodID(cls, "updatePicture", "([II)V");
        //Create a Java array and copy the C++ array into it
        jintArray jPictureRow = gEnv->NewIntArray(pictureW * 3);
        gEnv->SetIntArrayRegion(jPictureRow, 0, pictureW * 3, pictureRow);
        //Call the method with the parameters
        gEnv->CallStaticVoidMethod(cls, mid, jPictureRow, pictureW);
        //Release the variables
        gEnv->DeleteLocalRef(cls);
        gEnv->DeleteLocalRef(jPictureRow);
    }

}



//----- TX section of the C++ modems ----

//Get one character from the input buffer
extern int get_tx_char() {
    int returnValue;

    returnValue = GET_TX_CHAR_ETX;
    if (txDataBuffer != NULL) {
        if (txCounter < txDataBufferLength) {
            returnValue = (int) (*txDataBuffer++);
            txCounter++;
        }
    }

    return returnValue;
}


//Calls the Java Method that sends the buffer to the sound device
void txModulate(double *buffer, int len) {
    //Accumulate samples
    for (int i = 0; i < len; i++) {
        //Skip samples if we can't transmit fast enough. There is no point in accumulating any further.
        if (i < (sizeOfAudioBuffer - 1)) {
            audioBuffer[audioBufferIndex + i] = buffer[i];
        }
    }
    audioBufferIndex += len;
    if (audioBufferIndex > 2000) {//2000/8000 = 1/4 second of sound samples at 8Khz audio
        //Find the Java class
        jclass cls = gEnv->FindClass("com/SCVA/Modem");
        //Find the static Java method (see signature)
        jmethodID mid = gEnv->GetStaticMethodID(cls, "txModulate", "([DI)V");
        //Create a Java array and copy the C++ array into it
        jdoubleArray jBuffer = gEnv->NewDoubleArray(audioBufferIndex);
        gEnv->SetDoubleArrayRegion(jBuffer, 0, audioBufferIndex, audioBuffer);
        //Call the method with the Java array
        gEnv->CallStaticVoidMethod(cls, mid, jBuffer, audioBufferIndex);
        //Release the intermediate array and the other variables
        gEnv->DeleteLocalRef(jBuffer);
        gEnv->DeleteLocalRef(cls);
        audioBufferIndex = 0;
    }
}


//Flushes the end of the sound buffer
void flushTxSoundBuffer() {
    if (audioBufferIndex > 0) {
        //Find the Java class
        jclass cls = gEnv->FindClass("com/SCVA/Modem");
        //Find the static Java method (see signature)
        jmethodID mid = gEnv->GetStaticMethodID(cls, "txModulate", "([DI)V");
        //Create a Java array and copy the C++ array into it
        jdoubleArray jBuffer = gEnv->NewDoubleArray(audioBufferIndex);
        gEnv->SetDoubleArrayRegion(jBuffer, 0, audioBufferIndex, audioBuffer);
        //Call the method with the Java array
        gEnv->CallStaticVoidMethod(cls, mid, jBuffer, audioBufferIndex);
        //Release the intermediate array and the other variables
        gEnv->DeleteLocalRef(jBuffer);
        gEnv->DeleteLocalRef(cls);
        audioBufferIndex = 0;
    }
}


extern void change_CModem(int modemCode, double newFrequency) {
    //Delete previously created modem to recover memory used
    if (active_modem) {
        delete active_modem;
        active_modem = NULL;
    }
    //Now re-create with new mode
    if (modemCode >= MODE_PSK_FIRST && modemCode <= MODE_PSK_LAST) {
        active_modem = new psk(modemCode);
    } else if (modemCode >= MODE_DOMINOEX_FIRST && modemCode <= MODE_DOMINOEX_LAST) {
        active_modem = new dominoex(modemCode);
    } else if (modemCode >= MODE_THOR_FIRST && modemCode <= MODE_THOR_LAST) {
        active_modem = new thor(modemCode);
    } else if (modemCode >= MODE_MFSK_FIRST && modemCode <= MODE_MFSK_LAST) {
        active_modem = new mfsk(modemCode);
    } else if (modemCode >= MODE_MT63_FIRST && modemCode <= MODE_MT63_LAST) {
        active_modem = new mt63(modemCode);
    } else if (modemCode >= MODE_OLIVIA_FIRST && modemCode <= MODE_OLIVIA_LAST) {
        active_modem = new olivia(modemCode);
    } else if (modemCode >= MODE_CONTESTIA_FIRST && modemCode <= MODE_CONTESTIA_LAST) {
        active_modem = new contestia(modemCode);
    }
    //Do the initializations
    if (active_modem != NULL) {
        //First overall modem initilisation
        active_modem->init();
        //Moved before rx_init as per Fldigi
        //Then set RX frequency
        active_modem->set_freq(newFrequency);
        //Then init of RX side
        active_modem->rx_init();
        scva_v2::resetReceive();
        //Reset UTF-8 sequence monitor
        utfExpectedChars = utfFoundChars = 0;
    }
}

//***************** FROM JAVA to C++  ******************


//Save Tx thread environment
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_saveEnv(JNIEnv *env, jclass thishere) {
    gEnv = env;
}


//Fast modem change (for Txing image for example) change_CModem
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_changeCModem(JNIEnv *env, jclass thishere, jint modemCode,
                                 jdouble newFrequency) {
    gEnv = env;

    //change_CModem(modemCode, active_modem->get_freq()); //Same frequency as previous modem
    change_CModem(modemCode, newFrequency); //Same frequency as previous modem
}


//Creates the RSID receive Modem
extern "C" JNIEXPORT jstring
Java_com_SCVA_Modem_createRsidModem(JNIEnv *env, jclass thishere) {
    //Save environment if we need to call the preference methods in Java
    gEnv = env;
    gJobject = thishere;

    //Delete previously created RSID modem to recover memory used
    if (RsidModem) {
        delete RsidModem;
        RsidModem = NULL;
    }
    RsidModem = new cRsId();
    if (RsidModem != NULL) {
        return (env)->NewStringUTF("RSID Modem created");
    } else {
        return (env)->NewStringUTF("ERROR: RSID Modem Not created");
    }
}


//Returns an array of modem names (uses the RSID list in rsid_def.cxx)
extern "C" JNIEXPORT jobjectArray
Java_com_SCVA_Modem_getModemCapListString(JNIEnv *env, jclass thishere) {
    char *modeCapListString[MAXMODES];
    jobjectArray returnedArray;
    int i;
    int j = 0;

    returnedArray = (jobjectArray) env->NewObjectArray(MAXMODES,
                                                       env->FindClass("java/lang/String"),
                                                       env->NewStringUTF(""));
    for (i = 0; i < RsidModem->rsid_ids_size1; i++) {
        if (RsidModem->rsid_ids_1[i].mode != NUM_MODES) {
            env->SetObjectArrayElement(returnedArray, j++,
                                       env->NewStringUTF(RsidModem->rsid_ids_1[i].name));
        }
    }

    //do the same for 2nd list
    for (i = 0; i < RsidModem->rsid_ids_size2; i++) {
        if (RsidModem->rsid_ids_2[i].mode != NUM_MODES) {
            env->SetObjectArrayElement(returnedArray, j++,
                                       env->NewStringUTF(RsidModem->rsid_ids_2[i].name));
        }
    }
//Android debug  env->SetObjectArrayElement(returnedArray,j++,env->NewStringUTF("Bpsk31"));

    //Finalise the array
    env->SetObjectArrayElement(returnedArray, j, env->NewStringUTF("END"));

    return (returnedArray);

}


//Returns an array of modem codes (uses the RSID list in rsid_def.cxx)
extern "C" JNIEXPORT jintArray
Java_com_SCVA_Modem_getModemCapListInt(JNIEnv *env, jclass thishere) {

    jintArray returnedArray;
    int i;
    int j = 0;

    returnedArray = env->NewIntArray(MAXMODES);
    jint temp[MAXMODES];

    for (i = 0; i < RsidModem->rsid_ids_size1; i++) {
        if (RsidModem->rsid_ids_1[i].mode != NUM_MODES) {
            temp[j++] = RsidModem->rsid_ids_1[i].mode;
        }
    }

    //do the same for 2nd list
    for (i = 0; i < RsidModem->rsid_ids_size2; i++) {
        if (RsidModem->rsid_ids_2[i].mode != NUM_MODES) {
            temp[j++] = RsidModem->rsid_ids_2[i].mode;
        }
    }

    //Finalise the array with a negative value
    temp[j] = -1;

    //Copy to Java structure
    env->SetIntArrayRegion(returnedArray, 0, MAXMODES, temp);

    return (returnedArray);

}


//Creates the requested modem in C++
extern "C" JNIEXPORT jstring
Java_com_SCVA_Modem_createCModem(JNIEnv *env, jclass thishere,
                                 jint modemCode) {
    //Save environment if we need to call the preference methods in Java
    gEnv = env;
    gJobject = thishere;

    //Delete previously created modem to recover memory used
    if (active_modem) {
        delete active_modem;
        active_modem = NULL;
    }
    //Now re-create with new mode
    if (modemCode >= MODE_PSK_FIRST && modemCode <= MODE_PSK_LAST) {
        active_modem = new psk(modemCode);
    } else if (modemCode >= MODE_DOMINOEX_FIRST && modemCode <= MODE_DOMINOEX_LAST) {
        active_modem = new dominoex(modemCode);
    } else if (modemCode >= MODE_THOR_FIRST && modemCode <= MODE_THOR_LAST) {
        active_modem = new thor(modemCode);
    } else if (modemCode >= MODE_MFSK_FIRST && modemCode <= MODE_MFSK_LAST) {
        active_modem = new mfsk(modemCode);
    } else if (modemCode >= MODE_MT63_FIRST && modemCode <= MODE_MT63_LAST) {
        active_modem = new mt63(modemCode);
    } else if (modemCode >= MODE_OLIVIA_FIRST && modemCode <= MODE_OLIVIA_LAST) {
        active_modem = new olivia(modemCode);
    } else if (modemCode >= MODE_CONTESTIA_FIRST && modemCode <= MODE_CONTESTIA_LAST) {
        active_modem = new contestia(modemCode);
    } else {
        return (env)->NewStringUTF("ERROR: Modem NOT created");
    }
    return (env)->NewStringUTF("Modem created");
}


//Initializes the RX section of the requested modem in C++
extern "C" JNIEXPORT jstring
Java_com_SCVA_Modem_initCModem(JNIEnv *env, jclass thishere,
                               jdouble frequency) {
    //Save environment if we need to call the preference methods in Java
    gEnv = env;
    gJobject = thishere;

    if (active_modem != NULL) {
        //First overall modem initialisation
        active_modem->init();
        //Moved from after rx_init as per Fldigi
        //Then set RX frequency
        active_modem->set_freq(frequency);
        //Then init of RX side
        active_modem->rx_init();
        lastCharPos = 0;
        //Reset UTF-8 sequence monitor
        utfExpectedChars = utfFoundChars = 0;
        return (env)->NewStringUTF("Modem Initialized");
    } else {
        return (env)->NewStringUTF("ERROR: Modem NOT Initialized");
    }
}

//Processes the audio buffer through the current modem in C++
extern "C" JNIEXPORT jstring
Java_com_SCVA_Modem_rxCProcess(JNIEnv *env, jclass thishere,
                               jshortArray myjbuffer, jint length) {
    //Save environment if we need to call any methods in Java
    gEnv = env;
    gJobject = thishere;

    //Reset received characters buffer pointer
    lastCharPos = 0;
    //Convert to C++ type
    jshort *shortBuffer = env->GetShortArrayElements(myjbuffer, 0);

    if (scva_v2::configureReceive(env, thishere,
                                  getPreferenceB("RSFEC", false) &&
                                  getPreferenceB("RSFEC_V2_ENABLED", false),
                                  isV2SupportedThorMode(),
                                  getPreferenceB("RSFEC_V2_DEBUG", false))) {
        rxCodeword.clear();
        rxCodewordIndex = 0;
        startSequenceIndex = 0;
        codewordReceiving = false;
    }

    //Update RSFEC Start and Stop Sequence
    startSequence = getPreferenceS("RSFEC_STARTSEQUENCE", "START");
    stopSequence = getPreferenceS("RSFEC_STOPSEQUENCE", "STOP");

    //Process the buffer in C++
    if (active_modem != NULL) {
        active_modem->rx_process(shortBuffer, length);
        scva_v2::endReceive();
    } else {
        scva_v2::endReceive();
        env->ReleaseShortArrayElements(myjbuffer, shortBuffer, JNI_ABORT);
        return (env)->NewStringUTF("ERROR: Modem NOT Initialized");
    }
    //Release the passed parameters
    env->ReleaseShortArrayElements(myjbuffer, shortBuffer, 0);

    //Terminate the buffer
    decodedBuffer[lastCharPos] = 0;

//    std::string cppString(decodedBuffer);
//    makeToast(cppString);

    //Return the decoded data
    if (env->ExceptionCheck()) return nullptr;
    return env->NewStringUTF(decodedBuffer);
}


//Updates the modem with the latest GUI squelch level
extern "C" JNIEXPORT jint
Java_com_SCVA_Modem_setSquelchLevel(JNIEnv *env, jclass thishere,
                                    jdouble squelchLevel) {
    if (active_modem != NULL) {
        active_modem->set_squelchLevel(squelchLevel);
    }
    return 0;
}


//Returns the latest signal metric from the current modem in C++
extern "C" JNIEXPORT jdouble
Java_com_SCVA_Modem_getMetric(JNIEnv *env, jclass thishere) {
    double metric = 0.0;
    if (active_modem != NULL) {
        metric = active_modem->get_metric();
    }
    return metric;
}

//Processes the audio buffer through the RSID modem
extern "C" JNIEXPORT jstring
Java_com_SCVA_Modem_RsidCModemReceive(JNIEnv *env, jclass thishere,
                                      jfloatArray myfbuffer, jint length, jboolean doSearch) {
    //Save environment if we need to call any methods in Java
    gEnv = env;
    gJobject = thishere;

    //Convert to C++ type
    jboolean myjcopy = true;
    jfloat *floatBuffer = env->GetFloatArrayElements(myfbuffer, &myjcopy);
    //Reset received characters buffer pointer
    lastCharPos = 0;
    //Process the buffer in C++
    RsidModem->receive(floatBuffer, length, doSearch);
    //Release the passed parameters
    env->ReleaseFloatArrayElements(myfbuffer, floatBuffer, 0);
    //Terminate the buffer
    decodedBuffer[lastCharPos] = 0;

    return env->NewStringUTF(decodedBuffer);

}

//Returns the current modem frequency (for waterfall tracking after RSID)
extern "C" JNIEXPORT jdouble
Java_com_SCVA_Modem_getCurrentFrequency(JNIEnv *env, jclass thishere) {
    //@! Changed from active_modem->get_freq() to active_modem->frequency
    return active_modem->frequency;
}

//Returns the current modem bandwidth (for waterfall tracking after RSID)
extern "C" JNIEXPORT jdouble
Java_com_SCVA_Modem_getCurrentBandwidth(JNIEnv *env, jclass thishere) {
    return active_modem->get_bandwidth();
}

//Returns the current mode (for updating the Java side)
extern "C" JNIEXPORT jint
Java_com_SCVA_Modem_getCurrentMode(JNIEnv *env, jclass thishere) {
    if (active_modem == NULL) return -1;
    return active_modem->get_mode();
}

//not used - check code before use
//Returns the decoded characters resulting from the flushing of the rx pipe on RSID rx of new modem
extern "C" JNIEXPORT jint
Java_com_SCVA_Modem_getFlushedRxCharacters(JNIEnv *env, jclass thishere) {
    return active_modem->get_mode();
}




//Transmit section-------------------



//Send RSID of current mode
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_txRSID(JNIEnv *env, jclass thishere) {
    //Save environment if we need to call any methods in Java
    gEnv = env;
    gJobject = thishere;

    if (RsidModem != NULL && active_modem != NULL) {
        RsidModem->send(true); //Always true as we handle post-rsid decision in higher level
    }
}


//Initializes the TX section of the requested modem in C++
extern "C" JNIEXPORT jstring
Java_com_SCVA_Modem_txInit(JNIEnv *env, jclass thishere,
                           jdouble frequency) {
    //Save environment if we need to call any methods in Java
    gEnv = env;
    gJobject = thishere;

    //Init static variable
    audioBufferIndex = 0;

    if (active_modem != NULL) {
        //Then set TX frequency
        active_modem->set_freq(frequency);
        //Init of TX side
        active_modem->tx_init();
        return (env)->NewStringUTF("Tx Modem Initialized");
    } else {
        return (env)->NewStringUTF("ERROR: Tx Modem NOT Initialized");
    }
}

// Dedicated binary V2 entry: no String/UTF8/signed data-sentinel conversion.
extern "C" JNIEXPORT jboolean
Java_com_SCVA_Modem_txV2CProcess(JNIEnv *env, jclass cls, jbyteArray data, jint length, jint type) {
    gEnv = env;
    gJobject = cls;
    if (!getPreferenceB("RSFEC", false) || !getPreferenceB("RSFEC_V2_ENABLED", false))
        return JNI_FALSE;
    return scva_v2::transmit(env, cls, data, length, type) ? JNI_TRUE : JNI_FALSE;
}

//Processes the data buffer for TX through the current modem in C++
extern "C" JNIEXPORT jboolean
Java_com_SCVA_Modem_txCProcess(JNIEnv *env, jclass thishere,
                               jbyteArray myjbuffer, jint length) {
    //Save environment if we need to call any methods in Java
    gEnv = env;
    gJobject = thishere;

    if (!forcePlainTextTx && getPreferenceB("RSFEC", false) && getPreferenceB("RSFEC_V2_ENABLED", false)) {
        return scva_v2::transmit(env, thishere, myjbuffer, length, 1) ? JNI_TRUE : JNI_FALSE;
    }
    //Reset the static variables
    txCounter = 0;
    //Convert to C++ type
    txDataBuffer = env->GetByteArrayElements(myjbuffer, 0);
    txDataBufferLength = length;

    if (!forcePlainTextTx && getPreferenceB("RSFEC", false)) {
        //Reed-Solomon FEC Encode
        rsFECEncode();

    }

    /*
    for (int i = 0; i < txDataBufferLength; i++)
    {
        __android_log_print(ANDROID_LOG_VERBOSE, "rsFECEncode", "txDataBuffer[%d]: %x, %c \n", i, txDataBuffer[i], txDataBuffer[i]);
    }
    */

    /*
    for (uint16_t i = 0; i < 255; i++)
    {
        txDataBuffer[i] = txCodeword[i];
    }
    //__android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Encoded: %s with length: %d\n", txCodeword.c_str(), txCodeword.length());
    txDataBuffer = (signed char *) txCodeword.c_str();
    txDataBufferLength = txCodeword.length();
    __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "CastEncoded: %s with length: %d\n", txDataBuffer, txDataBufferLength);
    */

    /* EZPWD Reed-Solomon Test
    ezpwd::RS<255,253>		rs;		// 255 symbol codeword, up to 253 data == 2 symbols parity
    std::string			orig = "Hello, world!";

    // Most basic use of API to correct an error, in a dynamic container
    __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "\n\nSimple std::string container:\n");
    {
        std::string		copy	= orig; // working copy, copy of orig

        __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Original:  %s\n", copy.c_str());
        rs.encode( copy );				// 13 symbols copy + 2 symbols R-S parity added
        __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Encoded:   %s\n", copy.c_str());
        copy[3]				= 'x';	// Corrupt one symbol
        __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Corrupted: %s\n", copy.c_str());
        int			count	= rs.decode( copy );  // Correct any symbols possible
        __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Corrected: %s : %d  errors fixed\n", copy.c_str(), count);
        copy.resize( copy.size() - rs.nroots() );	// Discard added R-S parity symbols
        __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Restored:  %s\n", copy.c_str());
        if ( copy != orig ) {				// Ensure original copy is recovered
            __android_log_print(ANDROID_LOG_VERBOSE, "EZPWD", "Failed to restore origin data.\n");
        }
    }
    */

    //Process the buffer in C++
    if (active_modem != NULL) {
        //As there is no idle time in Flmsg context, keep
        //  processing until we have exhausted the data buffer
        while (active_modem->tx_process() >= 0) {  //Character processing is done in the test
        }
    } else {
        //Release the passed parameters
        env->ReleaseByteArrayElements(myjbuffer, txDataBuffer, 0);
        return JNI_FALSE;
    }
    //Flush the tx Sound Buffer
    flushTxSoundBuffer();
    //Release the passed parameters
    //Copied from RadioMSG to avoid seg fault
    //env->ReleaseByteArrayElements(myjbuffer, txDataBuffer, 0); OR
    //env->ReleaseByteArrayElements(myjbuffer, txDataBuffer, JNI_ABORT);
    env->DeleteLocalRef(myjbuffer);

    //Return the status
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean
Java_com_SCVA_Modem_txPlainCProcess(JNIEnv *env, jclass cls, jbyteArray buffer, jint length) {
    forcePlainTextTx = true;
    jboolean result = Java_com_SCVA_Modem_txCProcess(env, cls, buffer, length);
    forcePlainTextTx = false;
    return result;
}



//Returns the percent progress of TXing the buffer in characters (not time)
extern "C" JNIEXPORT jint
Java_com_SCVA_Modem_getTxProgressPercent(JNIEnv *env, jclass thishere) {
    int progressRatio = 0;
    if (txDataBufferLength > 0)
        progressRatio = (100 * txCounter) / txDataBufferLength;
    return progressRatio;
}


//Returns the percent progress of TXing the buffer in characters (not time)
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_setSlowCpuFlag(JNIEnv *env, jclass thishere,
                                   jboolean mSlowCpu) {
    progdefaults.slowcpu = mSlowCpu;
    return;
}

//Sets the AFC on/off state
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_setAfcOnOff(JNIEnv *env, jobject thishere,
                                bool mAfcOnOff) {
    progdefaults.afconoff = mAfcOnOff;
    return;
}

//Processes the picture data buffer for TX through the current MFSK modem in C++
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_txPicture(JNIEnv *env, jclass thishere,
                              jbyteArray txPictureBuffer, jint txPictureWidth, jint txPictureHeight,
                              jint txPictureTxSpeed, jint txPictureColour) {
    //Save environment if we need to call any methods in Java
    gEnv = env;
    gJobject = thishere;

    //Make sure we have the correct modem
    if (serviceme != active_modem) return;
    //Extract the data and a pointer
    signed char *inbuf = env->GetByteArrayElements(txPictureBuffer, 0);
    //Convert to C++ Picture Buffer
    int W, H, rowstart;
    W = txPictureWidth;
    H = txPictureHeight;
    serviceme->TXspp = txPictureTxSpeed;
//Problem: boolean parameter not passed correctly
    bool colour = (txPictureColour != 0);
    //bool colour = false;
    //Clear old transmit buffer
    if (xmtpicbuff) {
        delete[] xmtpicbuff;
        xmtpicbuff = NULL;
    }
    //Android Bitmap library: we always have a 4 bytes per pixel in RGBA order
    //   representing the bitmap picture regardless
    //   of the colour depth. Discard the Alpha and keep the RGB as the RGB values are pre-multiplied.
    int iy, ix, value;
    if (colour) {
        //RGB = 3 bytes per pixel
        xmtpicbuff = new unsigned char[W * H * 3];
        unsigned char *outbuf = xmtpicbuff;
        for (iy = 0; iy < H; iy++) {
            rowstart = iy * W;
            for (ix = 0; ix < W; ix++) {
                //Skip Alpha bytes and change from signed to unsigned char
                value = inbuf[(rowstart + ix) * 4];
                outbuf[(rowstart * 3 + ix)] = (unsigned char) (value < 0 ? value + 256 : value);
                value = inbuf[(rowstart + ix) * 4 + 1];
                outbuf[(rowstart * 3 + ix + W)] = (unsigned char) (value < 0 ? value + 256 : value);
                value = inbuf[(rowstart + ix) * 4 + 2];
                outbuf[(rowstart * 3 + ix + W + W)] = (unsigned char) (value < 0 ? value + 256
                                                                                 : value);
            }
        }
        serviceme->xmtbytes = W * H * 3;
        serviceme->color = true;
        if (serviceme->TXspp == 8)
            snprintf(serviceme->picheader, PICHEADER, "\nSending Pic:%dx%dC;", W, H);
        else
            snprintf(serviceme->picheader, PICHEADER, "\nSending Pic:%dx%dCp%d;", W, H,
                     serviceme->TXspp);
    } else {
        //Grey-Scale = single byte per pixel
        xmtpicbuff = new unsigned char[W * H];
        unsigned char *outbuf = xmtpicbuff;
        int greyTotal;
        for (iy = 0; iy < H; iy++) {
            rowstart = iy * W;
            for (ix = 0; ix < W; ix++) {
                value = inbuf[(rowstart + ix) * 4];
                greyTotal = 31 * (value < 0 ? value + 256 : value);
                value = inbuf[(rowstart + ix) * 4 + 1];
                greyTotal += 61 * (value < 0 ? value + 256 : value);
                value = inbuf[(rowstart + ix) * 4 + 2];
                greyTotal += 8 * (value < 0 ? value + 256 : value);
                outbuf[rowstart + ix] = (unsigned char) (greyTotal / 100);
            }
        }
        serviceme->xmtbytes = W * H;
        serviceme->color = false;
        if (serviceme->TXspp == 8) {
            snprintf(serviceme->picheader, PICHEADER, "\nSending Pic:%dx%d;", W, H);
        } else {
            snprintf(serviceme->picheader, PICHEADER, "\nSending Pic:%dx%dp%d;", W, H,
                     serviceme->TXspp);
        }
    }
    serviceme->rgb = 0;
    serviceme->col = 0;
    serviceme->row = 0;
    serviceme->pixelnbr = 0;

    // start the transmission
    serviceme->startpic = true;

    //Process the buffer in C++
    while (active_modem->tx_process() >= 0) {  //Character processing is done in the test
    }
    //Flush the tx Sound Buffer
    flushTxSoundBuffer();
    //Clear the transmit buffer
    if (xmtpicbuff) {
        delete[] xmtpicbuff;
        xmtpicbuff = NULL;
    }
    //Release the passed parameters
    //RadioSMS: avoid seg fault on release
    //env->ReleaseByteArrayElements(txPictureBuffer, inbuf, 0);
    //env->ReleaseByteArrayElements(txPictureBuffer, inbuf, JNI_ABORT);
    env->DeleteLocalRef(txPictureBuffer);

}

//@! Added Search Up and Down Function

//Search Up
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_searchUp(JNIEnv *env, jclass thishere) {
    gEnv = env;
    if (active_modem != NULL) {
        active_modem->searchUp();
    }
}

//Search Up
extern "C" JNIEXPORT void
Java_com_SCVA_Modem_searchDown(JNIEnv *env, jclass thishere) {
    gEnv = env;
    if (active_modem != NULL) {
        active_modem->searchDown();
    }
}

void rsFECEncode() {
    int blockLength = getPreferenceI("RSFEC_BLOCKLENGTH", 31);
    int mitigationLevel = getPreferenceI("RSFEC_LEVEL", 2);
    string startSequence = getPreferenceS("RSFEC_STARTSEQUENCE", "START");
    string stopSequence = getPreferenceS("RSFEC_STOPSEQUENCE", "STOP");
    string burnString = getPreferenceS("RSFEC_BURNSTRING", "BURN");
    int messageLength = 1;
    switch (mitigationLevel) {
        case 2:
            messageLength = blockLength - ((blockLength + 1) / 2);
            break;
        case 1:
            messageLength = blockLength - ((blockLength + 1) / 4);
            break;
        case 0:
            messageLength = blockLength - ((blockLength + 1) / 8);
            break;
    }
    int txDataBufferLength_temp = txDataBufferLength;
    signed char txDataBuffer_temp[txDataBufferLength_temp];
    for (int i = 0; i < txDataBufferLength; i++) {
        txDataBuffer_temp[i] = txDataBuffer[i];
    }
    int nBlocks = txDataBufferLength_temp / messageLength + 1;
    txDataBufferLength = burnString.length() +
                         nBlocks * (startSequence.length() + blockLength + stopSequence.length());
    delete[] txDataBuffer;
    txDataBuffer = new signed char[txDataBufferLength];
    for (int i = 0; i < burnString.length(); i++) {
        txDataBuffer[i] = burnString[i];
    }
    for (int i = 0; i < nBlocks; i++) {
        std::vector<int8_t> codeword(messageLength);
        for (int j = 0; j < messageLength; j++) {
            if (i * messageLength + j >= txDataBufferLength_temp) {
                //Pad with 0
                codeword[j] = 0;
                continue;
            }
            codeword[j] = txDataBuffer_temp[i * messageLength + j];
        }
        switch (blockLength) {
            case 15:
                switch (messageLength) {
                    case 15 - 8:
                        rs158.encode(codeword);
                        break;
                    case 15 - 4:
                        rs154.encode(codeword);
                        break;
                    case 15 - 2:
                        rs152.encode(codeword);
                        break;
                }
                break;
            case 31:
                switch (messageLength) {
                    case 31 - 16:
                        rs3116.encode(codeword);
                        break;
                    case 31 - 8:
                        rs318.encode(codeword);
                        break;
                    case 31 - 4:
                        rs314.encode(codeword);
                        break;
                }
                break;
            case 63:
                switch (messageLength) {
                    case 63 - 32:
                        rs6332.encode(codeword);
                        break;
                    case 63 - 16:
                        rs6316.encode(codeword);
                        break;
                    case 63 - 8:
                        rs638.encode(codeword);
                        break;
                }
                break;
            case 127:
                switch (messageLength) {
                    case 127 - 64:
                        rs12764.encode(codeword);
                        break;
                    case 127 - 32:
                        rs12732.encode(codeword);
                        break;
                    case 127 - 16:
                        rs12716.encode(codeword);
                        break;
                }
                break;
            case 255:
                switch (messageLength) {
                    case 255 - 128:
                        rs255128.encode(codeword);
                        break;
                    case 255 - 64:
                        rs25564.encode(codeword);
                        break;
                    case 255 - 32:
                        rs25532.encode(codeword);
                        break;
                }
                break;
        }
        //Fault Injection for Test
        int nErrorInjections = getPreferenceI("RSFEC_ERRORINJECTION", 0);
        if (nErrorInjections > 0) {
            std::vector<uint8_t> indices(messageLength);
            std::iota(indices.begin(), indices.end(), 0);
            // obtain a time-based seed:
            unsigned seed = std::chrono::system_clock::now().time_since_epoch().count();
            std::shuffle(indices.begin(), indices.end(), std::default_random_engine(seed));
            srand(seed);
            for (int i = 0; i < nErrorInjections; i++) {
                codeword[indices[i]] ^= 0x01 << (rand() % 8);
            }
        }
        for (int j = 0; j < startSequence.length(); j++) {
            txDataBuffer[burnString.length() +
                         i * (startSequence.length() + blockLength + stopSequence.length()) +
                         j] = startSequence[j];
        }
        for (int j = 0; j < blockLength; j++) {
            txDataBuffer[burnString.length() +
                         i * (startSequence.length() + blockLength + stopSequence.length()) +
                         startSequence.length() + j] = codeword[j];
        }
        for (int j = 0; j < stopSequence.length(); j++) {
            txDataBuffer[burnString.length() +
                         i * (startSequence.length() + blockLength + stopSequence.length()) +
                         startSequence.length() + blockLength + j] = stopSequence[j];
        }
    }

    // Logging must never rewrite the transmitted codeword or framing bytes.
    if (getPreferenceB("RSFEC_DEBUG", false)) {
        static const char hex[] = "0123456789ABCDEF";
        for (int offset = 0; offset < txDataBufferLength; offset += 32) {
            std::string line;
            for (int j = offset; j < txDataBufferLength && j < offset + 32; ++j) {
                const unsigned char byte = static_cast<unsigned char>(txDataBuffer[j]);
                line += hex[byte >> 4];
                line += hex[byte & 15];
                line += ' ';
            }
            __android_log_print(ANDROID_LOG_DEBUG, "rsFECEncode",
                                "TX bytes offset=%d total=%d: %s",
                                offset, txDataBufferLength, line.c_str());
        }
    }
}


bool rsFECDecode() {
    int blockLength = getPreferenceI("RSFEC_BLOCKLENGTH", 31);
    int mitigationLevel = getPreferenceI("RSFEC_LEVEL", 2);
    int messageLength = 1;
    int corrected = -1;
    // No modem event currently identifies a trustworthy outer symbol position.
    const std::vector<int> erasures;
    std::vector<int> correctedPositions;
    switch (mitigationLevel) {
        case 2: //HIGH
            messageLength = blockLength - ((blockLength + 1) / 2);
            break;
        case 1: //MEDIUM
            messageLength = blockLength - ((blockLength + 1) / 4);
            break;
        case 0: // LOW
            messageLength = blockLength - ((blockLength + 1) / 8);
            break;
    }
    switch (blockLength) {
        case 15:
            switch (messageLength) {
                case 15 - 8:
                    corrected = scva::decodeChecked(rs158, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 15 - 4:
                    corrected = scva::decodeChecked(rs154, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 15 - 2:
                    corrected = scva::decodeChecked(rs152, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
            }
            break;
        case 31:
            switch (messageLength) {
                case 31 - 16:
                    corrected = scva::decodeChecked(rs3116, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 31 - 8:
                    corrected = scva::decodeChecked(rs318, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 31 - 4:
                    corrected = scva::decodeChecked(rs314, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
            }
            break;
        case 63:
            switch (messageLength) {
                case 63 - 32:
                    corrected = scva::decodeChecked(rs6332, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 63 - 16:
                    corrected = scva::decodeChecked(rs6316, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 63 - 8:
                    corrected = scva::decodeChecked(rs638, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
            }
            break;
        case 127:
            switch (messageLength) {
                case 127 - 64:
                    corrected = scva::decodeChecked(rs12764, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 127 - 32:
                    corrected = scva::decodeChecked(rs12732, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 127 - 16:
                    corrected = scva::decodeChecked(rs12716, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
            }
            break;
        case 255:
            switch (messageLength) {
                case 255 - 128:
                    corrected = scva::decodeChecked(rs255128, rxCodeword, blockLength,
                                                    messageLength, erasures, correctedPositions);
                    break;
                case 255 - 64:
                    corrected = scva::decodeChecked(rs25564, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
                case 255 - 32:
                    corrected = scva::decodeChecked(rs25532, rxCodeword, blockLength, messageLength,
                                                    erasures, correctedPositions);
                    break;
            }
            break;
    }
    if (getPreferenceB("RSFEC_DEBUG", false)) {
        std::string positions;
        for (int position: correctedPositions) {
            if (!positions.empty()) positions += ",";
            positions += std::to_string(position);
        }
        __android_log_print(ANDROID_LOG_DEBUG, "rsFECDecode",
                            "RS(%d,%d) level=%d frameIndex=%d receiving=%d erasures=0 [] corrected=%d positions=[%s] %s",
                            blockLength, messageLength, mitigationLevel, rxCodewordIndex,
                            codewordReceiving, corrected, positions.c_str(),
                            corrected < 0 ? "FAILED: discard block" : "decoded");
    }
    if (corrected < 0) {
        rxCodeword.clear();
        return false;
    }
    return true;
}
