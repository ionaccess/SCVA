/*
 * Processor.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA)
 * Based on Pskmail from Per Crusefalk and Rein Couperus
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.SCVA;

import java.io.File;
import java.io.FileWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.SCVA.Interfaces.ProcessorCallBack;
import com.SCVA.Interfaces.UICallBack;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * @author John Douyere (VK2ETA)
 */
public class Processor extends Service implements ProcessorCallBack
{
	private UICallBack uiCallBack;
	private final IBinder binder = new LocalBinder();
	static String application = "Secure Comms via Audio 3.0.0";
	static String version = "Version 3.0";
	static boolean onWindows = true;
	static String ModemPreamble = ""; // String to send before any Tx Buffer
	static String ModemPostamble = ""; // String to send after any Tx buffer
	static final String DirLogs = "Logs";
	static final String messageLogFile = "messagelog.txt";
	static boolean compressedMsg = false;
	static int DCD = 0;

	// JD temp FIX: init as first modem in list
	public static int TxModem = Modem.customModeListInt[0];

	// globals for communication
	static String mycall; // my call sign from options
	public static String TX_Text; // output queue
	// Error handling and logging object
	static loggingClass log;

	public static void processor()
	{
		// Nothing as this is a service
	}

	@Override
	public void onCreate()
	{
		// Save Environment if we need to access Java code/variables from C++
		Message.saveEnv();

		// Create error handling class
		log = new loggingClass("SCVA");

		// Get settings and initialize
		handleInitialization();

		// Initialize Modem (creates the various type of modem objects)
		Modem.ModemInit();
		Modem.setProcessorCallBack(Processor.this);

		// Check that we have a current mode, otherwise take the first one in the list
		// (useful when we have a NIL list of custom modes)
		GS.gI().setRxModem(Processor.TxModem = Modem.customModeListInt[Modem.getModeIndex(GS.gI().getRxModem())]);

		// Set the image modes defaults and limits
		Constants.imageTxModemIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK64"));
		Modem.minImageModeIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK16"));
		Modem.maxImageModeIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK128"));

		// Reset frequency and squelch
		Modem.reset();

		// Make sure the display strings are blank
		GS.gI().setMonitor("");
		GS.gI().setTXmonitor("");
		GS.gI().setTermWindow("");
		Modem.setProcessorCallBack(Processor.this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		// Start the RxThread
		Modem.startModem();
		Modem.setProcessorCallBack(Processor.this);

		// Make sure Android keeps this running even if resources are limited
		// Display the notification in the system bar at the top at the same time
		startForeground(1, GS.gI().getMyNotification());

		// Keep this service running until it is explicitly stopped, so use sticky.
		// VK2ETA To-DO: Check if START_STICKY causes the service restart on ACRA report
		// VK2ETA return START_STICKY;
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		// Kill the Rx Modem thread
		Modem.stopRxModem();
	}

	// Post to main terminal window
	// public void PostToTerminal(String text)
	// {
	// GS.gI().TermWindow += text;
	// scvaCallBack.post(null, "addToTerminal");
	// }

	// Post to main terminal window
	// public static void PostToModem(String text)
	// {
	// GS.gI().monitor += text;
	// SCVA.mHandler.post(SCVA.addToModem);
	// }

	// Process one block of received data. Called from Modem.RxBlock when a complete
	// block is received
	public void processWrapBlock(String Blockline)
	{
		FileWriter out = null;
		try
		{
			boolean unwrapResult = Message.ProcessWrapBuffer(Blockline);
			if (unwrapResult)
			{
				// Save file and log it
				String resultString = Message.getUnwrapText();
				String resultFilename = Message.getUnwrapFilename();
				GS.gI().setLastReceivedMessageFname(resultFilename);
				String inboxFolderPath = Constants.HomePath + Constants.Dirprefix + Constants.DirInbox + Constants.Separator;
				File msgReceivedFile = new File(inboxFolderPath + GS.gI().getLastReceivedMessageFname());
				if (msgReceivedFile.exists())
				{
					msgReceivedFile.delete();
				}
				out = new FileWriter(msgReceivedFile, true);
				out.write(resultString);
				out.close();
				// We now check how many, if any, attached image are expected
				Pattern psc = Pattern.compile("(^_img.*),<analog>(\\d+)", Pattern.MULTILINE);
				Matcher msc = psc.matcher(resultString);
				GS.gI().setLastMessageNoExpectedImages(0);
				GS.gI().setCurrentImageSequenceNo(0);
				boolean keepLooking = true;
				for (int start = 0; keepLooking;)
				{
					keepLooking = msc.find(start);
					if (keepLooking)
					{
						// Store the field name for this image (sequence in form = sequence of Tx)
						GS.gI().getLastMessageImgFieldName()[GS.gI().lastMessageNoExpectedImages++] = msc.group(1);
						start = msc.end();
					}
				}
				// Save the time of end of Rx (any attached picture must be send within a set
				// time)
				GS.gI().setLastMessageEndTxTime(System.currentTimeMillis());
				// Advise reception of message
				post("PostToTerminal", GS.gI().getContext().getString(R.string.txt_ChecksumOK));
				post("PostToTerminal", "\n" + GS.gI().getContext().getString(R.string.txt_SavedFile) + ": " + resultFilename);
				Message.addEntryToLog(GS.gI().getContext().getString(R.string.txt_ReceivedMessage) + ": " + resultFilename);
			}
			else
			{
				post("PostToTerminal", "\n " + GS.gI().getContext().getString(R.string.txt_BadCrcFileContent) + "\n" + Message.geterrtext());
				// Save file anyway into a Blank form file for further usage
				String resultString = Message.geterrtext();
				// Remove the WRAP section and try to store as a normal form
				int formStart = resultString.indexOf("]<flmsg>");
				if (formStart != -1)
				{
					resultString = resultString.substring(formStart + 1);
				}
				File msgReceivedFile = new File(Constants.HomePath + Constants.Dirprefix + Constants.DirInbox + Constants.Separator + "Last_Bad_CrC_Rx");
				if (msgReceivedFile.exists())
				{
					msgReceivedFile.delete();
				}
				out = new FileWriter(msgReceivedFile, true);
				out.write(resultString);
				out.close();
				post("PostToTerminal", "\n\n" + GS.gI().getContext().getString(R.string.txt_SavedRecoveredData));
				Message.addEntryToLog(GS.gI().getContext().getString(R.string.txt_SavedRecoveredData));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void handleInitialization()
	{
		try
		{
			// Initialize send queue
			TX_Text = "";
			ModemPreamble = config.getPreferenceS("MODEMPREAMBLE", "");
			ModemPostamble = config.getPreferenceS("MODEMPOSTAMBLE", "");
			// Compression settings
			compressedMsg = config.getPreferenceB("COMPRESSED");
			Processor.mycall = config.getPreferenceS("CALL");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			loggingClass.writelog("Problems with config parameter.", e, true);
		}
	}

	@Override
	public void post(String type, String data)
	{
		if (type.equalsIgnoreCase("PostV2Diagnostic"))
		{
			if (uiCallBack != null) uiCallBack.post("PostV2Diagnostic", data);
		}
		if (type.equalsIgnoreCase("PostToTerminal"))
		{
			GS.gI().TermWindow += data;
			if (uiCallBack != null) uiCallBack.post("PostToTerminal", data);
		}
		if (type.equalsIgnoreCase("PostTo"))
		{
			GS.gI().monitor += data;
			if (uiCallBack != null) uiCallBack.post("PostToModem", data);
		}
		if (type.equalsIgnoreCase("processWrapBlock"))
		{
			processWrapBlock(data);
		}
		else
		{
			if (uiCallBack != null) uiCallBack.post(type, data);
		}
	}

	public void sendMessage(String msgToSend)
	{
		Processor.TX_Text += msgToSend;
		Modem.txData("", "", msgToSend, 0, 0, false, "");
	}

	public void setUIListener(UICallBack uiCallBack)
	{
		this.uiCallBack = uiCallBack;
	}

	// Binder class
	public class LocalBinder extends Binder
	{
		public Processor getService()
		{
			return Processor.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	// Example method
	public String getMessage()
	{
		Utils.println("Service Connected");
		return "Service Connected";
	}
}
