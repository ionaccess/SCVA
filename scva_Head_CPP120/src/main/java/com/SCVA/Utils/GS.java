package com.SCVA.Utils;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;

import com.SCVA.Modem;
import com.SCVA.Processor;
import com.SCVA.R;
import com.SCVA.models.Configuration;

import java.util.concurrent.Semaphore;

/**
 * Created by §∞§ on 17/05/2026.
 */
public class GS
{
	private int keyId = 0;
    public int chatId = 0;
    private Context context;
    public String monitor = "";
    public String plainKey = "";
    public String TXmonitor = "";
    public String TermWindow = "";
    private static GS ourInstance;
    private Notification myNotification;

	public int getKeyId()
	{
		return keyId;
	}

	public void setKeyId(int keyId)
	{
		if (keyId != this.keyId)
		{
			setPlainKey("");
		}
		this.keyId = keyId;
	}

	public int getChatId()
	{
		return chatId;
	}

	public void setChatId(int chatId)
	{
		if (chatId != this.chatId)
		{
			setPlainKey("");
		}
		this.chatId = chatId;
	}

	public String getPlainKey()
	{
		return plainKey;
	}

	public void setPlainKey(String plainKey)
	{
		this.plainKey = plainKey;
	}

	public boolean isAdvanceMode()
	{
		return getValue().getBoolean("beginner_mode", false);
	}

	public int currentView = 0;

	public int getCurrentView()
	{
		return currentView;
	}

	public void setCurrentView(int currentView)
	{
		this.currentView = currentView;
	}

	private boolean RXParamsChanged = false;

	public boolean isModemPaused()
	{
		return modemPaused;
	}

	public void setModemPaused(boolean modemPaused)
	{
		this.modemPaused = modemPaused;
	}

	private boolean modemPaused = false;

	public boolean isUsbAudioEnabled()
	{
		return usbAudioEnabled;
	}

	public void setUsbAudioEnabled(boolean usbAudioEnabled)
	{
		this.usbAudioEnabled = usbAudioEnabled;
	}

	private boolean usbAudioEnabled = false;

	public boolean isRXParamsChanged()
	{
		return RXParamsChanged;
	}

	public void setRXParamsChanged(boolean RXParamsChanged)
	{
		this.RXParamsChanged = RXParamsChanged;
	}

	// For providing a progress on the number of messages and for each message the % complete
	public String txMessageCount = "";

	public String getTxMessageCount()
	{
		return txMessageCount;
	}

	public void setTxMessageCount(String txMessageCount)
	{
		this.txMessageCount = txMessageCount;
	}

	public String getTxProgressCount()
	{
		return txProgressCount;
	}

	public void setTxProgressCount(String txProgressCount)
	{
		this.txProgressCount = txProgressCount;
	}

	public String txProgressCount = "";

	public int getCurrentImageSequenceNo()
	{
		return currentImageSequenceNo;
	}

	public void setCurrentImageSequenceNo(int currentImageSequenceNo)
	{
		this.currentImageSequenceNo = currentImageSequenceNo;
	}

	public int currentImageSequenceNo = 0;

	public String[] getLastMessageImgFieldName()
	{
		return lastMessageImgFieldName;
	}

	public void setLastMessageImgFieldName(String[] lastMessageImgFieldName)
	{
		this.lastMessageImgFieldName = lastMessageImgFieldName;
	}

	public String[] lastMessageImgFieldName = new String[10];// Max 10 images per message

	public long getLastMessageEndTxTime()
	{
		return lastMessageEndTxTime;
	}

	public void setLastMessageEndTxTime(long lastMessageEndTxTime)
	{
		this.lastMessageEndTxTime = lastMessageEndTxTime;
	}

	public long lastMessageEndTxTime = 0;

	public int getLastMessageNoExpectedImages()
	{
		return lastMessageNoExpectedImages;
	}

	public void setLastMessageNoExpectedImages(int lastMessageNoExpectedImages)
	{
		this.lastMessageNoExpectedImages = lastMessageNoExpectedImages;
	}

	public int lastMessageNoExpectedImages = 0;

	public String getLastReceivedMessageFname()
	{
		return lastReceivedMessageFname;
	}

	public void setLastReceivedMessageFname(String lastReceivedMessageFname)
	{
		lastReceivedMessageFname = lastReceivedMessageFname;
	}

	// File name of last message received for appending a newly received picture
	public String lastReceivedMessageFname = "";

	public boolean isPictureRxInTime()
	{
		return pictureRxInTime;
	}

	public void setPictureRxInTime(boolean pictureRxInTime)
	{
		this.pictureRxInTime = pictureRxInTime;
	}

	// Less than 20 seconds between the end of the text message and
	// the start of mfsk picture transmission
	public boolean pictureRxInTime = false;

	public Semaphore getRestartRxModem()
	{
		return restartRxModem;
	}

	public void setRestartRxModem(Semaphore restartRxModem)
	{
		this.restartRxModem = restartRxModem;
	}

	// Semaphores to instruct the RxTx Thread to start or stop
	public Semaphore restartRxModem = new Semaphore(1, false);
	public int CPULoad;

	public int getCPULoad()
	{
		return CPULoad;
	}

	public void setCPULoad(int CPULoad)
	{
		this.CPULoad = CPULoad;
	}

	private String status = ""; // getContext().getString(R.string.txt_Listening);

	public String getStatus()
	{
		return status;
	}

	public void setStatus(String status)
	{
		this.status = status;
	}

	public int getDCDthrow()
	{
		return DCDthrow;
	}

	public void setDCDthrow(int DCDthrow)
	{
		this.DCDthrow = DCDthrow;
	}

	private int DCDthrow;

	public boolean isTXActive()
	{
		return TXActive;
	}

	public void setTXActive(boolean TXActive)
	{
		this.TXActive = TXActive;
	}

	public boolean TXActive = false;

	public String getCrcString()
	{
		return CrcString;
	}

	public void setCrcString(String crcString)
	{
		CrcString = crcString;
	}

	public String CrcString = "";

	public String getFileNameString()
	{
		return FileNameString;
	}

	public void setFileNameString(String fileNameString)
	{
		FileNameString = fileNameString;
	}

	public String FileNameString = "";

	public boolean isReceivingForm()
	{
		return ReceivingForm;
	}

	public void setReceivingForm(boolean receivingForm)
	{
		ReceivingForm = receivingForm;
	}

	public boolean ReceivingForm = false;

	public boolean isProcessorON()
	{
		return ProcessorON;
	}

	public void setProcessorON(boolean processorON)
	{
		ProcessorON = processorON;
	}

	// Member object for processing of Rx and Tx
	// Can be stopped (i.e no RX) to save battery and allow Android to reclaim
	// resources if not visible to the user
	private boolean ProcessorON = false;

	public String getMonitor()
	{
		return monitor;
	}

	public void setMonitor(String monitor)
	{
		this.monitor = monitor;
	}

	public String getTXmonitor()
	{
		return TXmonitor;
	}

	public void setTXmonitor(String TXmonitor)
	{
		this.TXmonitor = TXmonitor;
	}

	public String getTermWindow()
	{
		return TermWindow;
	}

	public void setTermWindow(String termWindow)
	{
		TermWindow = termWindow;
	}

	public int getRxModem()
	{
		return RxModem;
	}

	public void setRxModem(int rxModem)
	{
		RxModem = rxModem;
	}

	private int RxModem = Modem.customModeListInt[0];

	public Notification getMyNotification()
	{
		return myNotification;
	}

	public void setMyNotification(Notification myNotification)
	{
		this.myNotification = myNotification;
	}

	public Context getContext()
	{
		return context;
	}

	public void setContext(Context context)
	{
		this.context = context;
	}

	public static GS gI()
	{
		if (ourInstance == null) ourInstance = new GS();
		return ourInstance;
	}

	public SharedPreferences.Editor getEditor()
	{
		SharedPreferences prefs = context.getSharedPreferences(Constants.DEFAULT_PREFERENCES, Context.MODE_PRIVATE);
		return prefs.edit();
	}

	public SharedPreferences getValue()
	{
		return context.getSharedPreferences(Constants.DEFAULT_PREFERENCES, Context.MODE_PRIVATE);
	}

	public void applyConfig(Configuration configuration, boolean image)
	{
		getEditor().putBoolean("RSFEC", true).apply();// ReedSolomn
		getEditor().putBoolean("TXRSID", configuration.istXRsID());
		getEditor().putBoolean("RXRSID", configuration.isrXRsID());

		getEditor().putString("LASTMODEUSED", "" + Modem.getMode(configuration.getModulationType())).apply();

		getEditor().putString("AFREQUENCY", "" + configuration.getFrequency()).apply();// AFREQUENCY

		if (getValue().getBoolean("RSFEC_V2_ENABLED", false))
			getEditor().putString("RSFEC_V2_PRESET", configuration.getrSErrorCorrection()).apply();
		else
			getEditor().putString("RSFEC_LEVEL", configuration.getrSErrorCorrection()).apply();
		getEditor().putBoolean("use_enc", configuration.isEncryption()).apply(); // use_enc | key = use_enc value = true
		getEditor().putString("enc_algorithm", configuration.getEncryptionType()).apply();// key = enc_algorithm value = aes
		getEditor().putFloat("WFMAXVALUE", Float.parseFloat("" + configuration.getAudioGain())).apply();// WFMAXVALUE | not in use in saved file
		getEditor().putFloat("SQUELCHVALUE", configuration.getSquelch()).apply();// SQUELCHVALUE | not in use in saved file
		getEditor().putBoolean("AFCONOFF", configuration.isAfc()).apply();// AFCONOFF | key = AFCONOFF value = false

		GS.gI().setRxModem(Modem.getMode(configuration.getModulationType()));

		if (image)
		{
			getEditor().putBoolean("TXRSID", true);
			getEditor().putString("LASTMODEUSED", "" + Modem.getMode("MFSK64")).apply();
			GS.gI().setRxModem(Modem.getMode("MFSK64"));
			getEditor().putString("AFREQUENCY", "1500").apply();
			Modem.frequency = 1500;
		}

		if (configuration.getId() == 9)
		{
			// disable Reed-Solomon Forward Error Correction in ANDFLmsg config
			getEditor().putBoolean("RSFEC", false).apply();
		}
		// to make the changes effective
		Modem.changemode(GS.gI().getRxModem());
	}
}
