/*
 * SCVA.java
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
 */

package com.SCVA;

/**
 * @author John Douyere <vk2eta@gmail.com>
 */

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.SCVA.Interfaces.CallBack;
import com.SCVA.Interfaces.UICallBack;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;
import com.SCVA.activities.LogsActivity;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.encryption.Aes256Encryptor;
import com.SCVA.encryption.BaseEncryptor;
import com.SCVA.encryption.BlowfishEncryptor;
import com.SCVA.encryption.Gost28147Encryptor;
import com.SCVA.encryption.Rc6Encryptor;
import com.SCVA.encryption.SerpentEncryptor;
import com.SCVA.encryption.TwofishEncryptor;
import com.SCVA.models.Chat;
import com.SCVA.models.Key;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

public class SCVA extends AppCompatActivity implements CallBack, UICallBack
{
	Processor processor;
	boolean isBound = false;
	public static BaseEncryptor encryptor;
	private String msgToSend;
	private String msgToReturn;
	private static Utils utils;
	private Chat chat;
	private static Key activeKey;
	public static Context myContext;
	public static SCVA myInstance = null;
	private int warningCount = 0;
	private static boolean havePassedAllPermissionsTest = false;
	private static boolean havePassedCriticalPermissionsTest = false;
	public static Window myWindow = null;

	// Last screen used (Messages, Terminal, Modem, etc...)
	private int lastMsgScreen = Constants.INBOX_VIEW; // default to start with
	// private static ArrayList<email> emaillist;
	// private static LayoutInflater inflater = null;
	// private static PopupWindow pw;
	// Are we in an individual form display screen?
	private static boolean inFormDisplay = false;
	private static boolean hasDisplayedWelcome = false;

	// Horizontal Fling detection despite scrollview
	private GestureDetector mGesture;

	// screen transitions / animations
	private static final int NORMAL = 0;
	private static final int LEFT = 1;
	private static final int RIGHT = 2;
	private static final int TOP = 3;
	private static final int BOTTOM = 4;

	// Views values
	static ProgressBar SignalQuality = null;
	static ProgressBar CpuLoad = null;

	// Toast text display (declared here to allow for quick replacement rather
	// than queuing)
	private static Toast myToast;

	// To have a double back key press out of the data entry forms
	private static int backKeyPressCount = 0;
	private static long lastBackKeyTime = 0;

	// Forms display and forward
	private static String mDisplayForm = new String();
	private static String mFileName = new String();
	public static String mHeader = new String();

	// Layout Views
	private static TextView myTermTV;
	private static ScrollView myTermSC;
	private static TextView myModemTV;
	private static ScrollView myModemSC;
	public static waterfallView myWFView;
	public static View pwLayout;
	private static String savedTextMessage = "";
	private ListView msgListView;
	private static WebView mWebView;

	// Generic button variable. Just for callback initialisation
	private Button myButton;
	private Button sendText;
	private CheckBox checkbox = null;
	private static CheckBox checkBox_afc = null;
	private static int paramCount = 0;
	private static String arlMsg = "";
	private static AlertDialog myAlertDialog = null;
	private static int sharingAction = 0;
	public final static int FORSENDINGASATTACHMENT = 1;
	public final static int FORSENDINGASTEXT = 2;
	public final static int FORPRINTING = 3;
	private static String formattedMsg = "";
	private static String msgFieldToUpdate = "";
	private static String checkField;
	private static String checkFieldToUpdate;
	private static String _imgFieldToUpdate;
	private static String csvData;
	private static boolean updateCsvField = false;
	private static String jsEncodedImageLine = null;

	// public static LocationManager locationManager;

	// public GPSListener locationListener = new GPSListener();
	public static boolean GPSisON = false;
	public static boolean GPSTimeAcquired = false;
	public static int DeviceToGPSTimeCorrection = 0;
	public static String TerminalBuffer = "";
	public static String ModemBuffer = "";

	// For managing the "Send All Messages" process
	private static boolean SendingAllMessages = false;

	// Tx Buffer
	private static String bufferToTx;

	// Images sent in digital (Base64 encoded) or analog (MFSK image mode)
	static boolean digitalImages = false;
	static String imageMode = "";
	static String analogSpeedColour = "";
	static String decryptedTxt = "";

	// Array adapter for the messages' list
	public static ArrayAdapter<String> msgArrayAdapter;

	// Need handler for callbacks to the UI thread
	public static final Handler mHandler = new Handler();
	public static Thread displayMessagesThread = null;

	// public static Runnable displayMessagesRunnable = null;

	// Contact email picker
	// Not used private static final int CONTACT_PICKER_RESULT = 10101;
	// Share form action
	private static final int SHARE_MESSAGE_RESULT = 10202;

	// Share form action from outbox (so we can move the message to Sent folder)
	private static final int SHARE_OUTBOX_MESSAGE_RESULT = 10203;
	public static String lastSharedFileName = "";

	// Share form action
	private static final int EDIT_CSV_RESULT = 10303;

	// Camera/Gallery picture request
	private static final int PICTURE_REQUEST_CODE = 10404;

	// Temp picture attachment
	private static final String tempAttachPictureFname = "_imgCameraAttachment";

	// Bluetooth handfree / headset
	public static boolean scoChannelOn = false;
	public static boolean toBluetooth = false;
	public static AudioManager mAudioManager;

	// public static BluetoothAdapter mBluetoothAdapter = null;

	// Bluetooth file transfers
	// public static BroadcastReceiver mReceiver = null;

	// To monitor the incoming calls and disconnect Bluetooth so that we don't
	// send the phone call audio to the radio
	// private TelephonyManager tmgr = null;
	/*
	 * Parked code for later // Broadcast receiver for Bluetooth broadcasts // private final BroadcastReceiver myBroadcastReceiver = new mBroadcastReceiver();
	 *
	 * // Need to start an audio channel with the Bluetooth headset/handsfree // Either use startVoiceRecognition() (V3.0 onwards) // OR // StartBluetoothSCO() (V2.2 Onwards) BluetoothHeadset mBluetoothHeadset;
	 *
	 * private BluetoothHeadset.ServiceListener mBluetoothHeadsetServiceListener = new BluetoothHeadset.ServiceListener() { public void onServiceConnected() { if (mBluetoothHeadset != null && mBluetoothHeadset.getState() == BluetoothHeadset.STATE_CONNECTED) { //
	 * Utils.println(TAG, "######### STARTING VOICE RECOGNITION ############"); mBluetoothHeadset.startVoiceRecognition(); mAudioManager.setMode(AudioManager.MODE_IN_CALL); } } public void onServiceDisconnected() { int aaa = 0; }
	 *
	 * };
	 */
	private String password = "";
	private KeysDataSource keysDataSource;
	private SettingsDataSource settingsDataSource;

	// Create runnable for updating the waterfall display
	public static final Runnable updatewaterfall = new Runnable()
	{
		public void run()
		{
			if (myWFView != null)
			{
				myWFView.invalidate();
				myWFView.postInvalidate();
			}
		}
	};

	// Create runnable for changing the main window's title
	public static final Runnable updateTitle = new Runnable()
	{
		public void run()
		{
			// ((AppCompatActivity)
			// myContext).getSupportActionBar().setBackgroundDrawable(new
			// ColorDrawable(Color.parseColor("#48B04C")));
			String keyName = "No key is Unlocked";
			if (activeKey != null)
			{
				keyName = "Active key is " + activeKey.getName();
			}
			int mIndex = Modem.getModeIndexFullList(GS.gI().getRxModem());
			// SAMSUNG & WIKO appcompat BUG Workaround
			try
			{
				// Only if buttons shown in the action bar ((AppCompatActivity)
				// myContext).supportInvalidateOptionsMenu();
				if (GS.gI().isProcessorON())
				{
					if (GS.gI().isTXActive())
					{
						// myWindow.setTitleColor(Color.YELLOW); // #FFFF00
						((AppCompatActivity) myContext).getSupportActionBar().setTitle((Html.fromHtml("<font color=\"#FFFFFF\">"
								// Remove app name to make space for more information
								+ "SCVA - " + Modem.modemCapListString[mIndex] + " - " + GS.gI().getStatus() + String.format("@%.1fHz", Modem.frequency)
								+ GS.gI().getTxMessageCount() + GS.gI().getTxProgressCount() + " | " + keyName + "</font>")));
					}
					else
					{
						// myWindow.setTitleColor(Color.CYAN); //33D6FF
						((AppCompatActivity) myContext).getSupportActionBar().setTitle((Html.fromHtml("<font color=\"#FFFFFF\">"
								// Remove app name to make space for more information
								+ "SCVA - " + Modem.modemCapListString[mIndex] + " - " + GS.gI().getStatus() + String.format("@%.1fHz", Modem.frequency) + " | " + keyName
								+ "</font>")));
						GS.gI().setTxProgressCount(""); // Reset percent
						// complete of file
						// TX
					}
				}
				else
				{
					((AppCompatActivity) myContext).getSupportActionBar().setTitle((Html.fromHtml("<font color=\"#FFFFFF\">" +
					// + "SCVA - Modem OFF" + "</font>")));
							"Modem OFF" + "</font>")));
				}
			}
			catch (Throwable x)
			{
				x.printStackTrace();
				// loggingClass.writelog("\nDebug Information: Samsung/Wiko appcompat
				// compatibility issue workaround activated!", null, true);
			}
		}
	};

	// Runnable for updating the signal quality bar in Modem Window
	public static final Runnable updateSignalQuality = new Runnable()
	{
		public void run()
		{
			if ((SignalQuality != null) && ((GS.gI().getCurrentView() == Constants.MODEMVIEWnoWF) || (GS.gI().getCurrentView() == Constants.MODEMVIEWwithWF)))
			{
				SignalQuality.setProgress((int) Modem.metric);
				SignalQuality.setSecondaryProgress((int) Modem.squelch);
			}
		}
	};

	// Runnable for updating the CPU load bar in Modem Window
	public static final Runnable updateCPULoad = new Runnable()
	{
		public void run()
		{
			if ((CpuLoad != null) && ((GS.gI().getCurrentView() == Constants.MODEMVIEWnoWF) || (GS.gI().getCurrentView() == Constants.MODEMVIEWwithWF)))
			{
				CpuLoad.setProgress((int) GS.gI().getCPULoad());
			}
		}
	};

	// Create runnable for posting to terminal window
	public static final Runnable addToTerminal = new Runnable()
	{
		public void run()
		{
			// myTV.setText(Processor.TermWindow);
			if (myTermTV != null)
			{
				// Update done with setText below
				// myTermTV.append(Processor.TermWindow);
				TerminalBuffer += GS.gI().getTXmonitor();
				GS.gI().setTXmonitor("");
				if (TerminalBuffer.length() > 10000) TerminalBuffer = TerminalBuffer.substring(2000);
				myTermTV.setText(TerminalBuffer);
				// Then scroll to the bottom
				if (myTermSC != null)
				{
					myTermSC.post(new Runnable()
					{
						public void run()
						{
							myTermSC.fullScroll(View.FOCUS_DOWN);
						}
					});
				}
			}
		}
	};
	static String receive_buffer = "";
	static String bufferText = "";

	// STAR: RECEIVER Create runnable for posting to modem window
	public static final Runnable addToModem = new Runnable()
	{
		public void run()
		{
			// myTV.setText(Processor.TermWindow);
			if (myModemTV != null)
			{
				// Only add the size limited ModemBuffer otherwise we overload the textview
				// display
				// myModemTV.append(Processor.monitor);
				// J3/tnW4ajDzeef1f4P8vLhnYSGNjUXy3uBC1ye+Od48=\n fOTVjv+gHY6dDo3XefDhOw==\n
				// J3/tnW4ajDzeef1y3uBC1ye+Od48= fOTVjv+gHY6dDo3XefDhOw==
				String temp = GS.gI().getMonitor();
				bufferText = bufferText + GS.gI().getMonitor();
				ModemBuffer += temp;
				GS.gI().setMonitor("");
				// Noted a slowing down of Gui after a large number of characters are received
				// Reduced the buffer size if (ModemBuffer.length() > 60000)
				if (ModemBuffer.length() > 10000) ModemBuffer = ModemBuffer.substring(5000);
				// Reassign only the size limited buffer
				// Then scroll to the bottom
				if (myModemSC != null)
				{
					myModemSC.post(new Runnable()
					{
						public void run()
						{
							myModemSC.fullScroll(View.FOCUS_DOWN);
							// myModemSC.smoothScrollBy(20,0);
						}
					});
				}

				if (GS.gI().getValue().getBoolean("use_enc", false))
				{
					// Utils.println("|" + bufferText + "|");
					decryptedTxt = "";
					if (bufferText.endsWith("§"))
					{
						Utils.println(bufferText);
						String[] msgs = bufferText.split("¢");
						for (int i = 0; i < msgs.length; i++)
						{
							if (msgs[i].length() > 1)
							{
								decryptedTxt = decryptedTxt + encryptor.decrypt(msgs[i]);
							}
						}
						Utils.println(decryptedTxt);
						myModemTV.setText(decryptedTxt);
						// SCVA.utils.writeToFile("R: " + decryptedTxt, SCVA.myContext);
						// decryptedTxt = "";
						bufferText = "";
					}
				}
				else
				{
					Utils.println(bufferText);
					myModemTV.setText(bufferText);
					// SCVA.utils.writeToFile("R: " + bufferText, SCVA.myContext);
					bufferText = "";
				}
			}
		}
	};

	// Runnable for updating
	// the CPU load bar in Modem Window
	public static final Runnable updateMfskPicture = new Runnable()
	{
		public void run()
		{
			// Update displayed bitmap
			ImageView imageView = (ImageView) SCVA.pwLayout.findViewById(R.id.imageView1);
			if (imageView != null)
			{
				imageView.setImageBitmap(Modem.picBitmap);
			}
		}
	};

	@Override
	public void notify(Object obj, String type)
	{
		mHandler.post(updateTitle);
	}

	@Override
	public void post(String type, String data)
	{
		if (type.equalsIgnoreCase("PostToTerminal")) mHandler.post(addToTerminal);
		if (type.equalsIgnoreCase("PostV2Diagnostic"))
		{
			final String immutableText = data;
			mHandler.post(new Runnable()
			{
				public void run()
				{
					if (myTermTV != null)
					{
						TerminalBuffer += immutableText;
						if (TerminalBuffer.length() > 10000) TerminalBuffer = TerminalBuffer.substring(2000);
						myTermTV.setText(TerminalBuffer);
						if (myTermSC != null) myTermSC.fullScroll(View.FOCUS_DOWN);
					}
					android.util.Log.i("SCVA-V2-TRACE", "uiAppend chars=" + immutableText.length());
				}
			});
		}
		if (type.equalsIgnoreCase("PostToModem")) mHandler.post(addToModem);
		if (type.equalsIgnoreCase("updatewaterfall")) mHandler.post(updatewaterfall);
		if (type.equalsIgnoreCase("updateMfskPicture")) mHandler.post(updateMfskPicture);
		if (type.equalsIgnoreCase("updateTitle")) mHandler.post(updateTitle);
		if (type.equalsIgnoreCase("updateAFCCheckBox")) mHandler.post(updateAFCCheckBox);
		if (type.equalsIgnoreCase("updateCPULoad")) mHandler.post(updateCPULoad);
		if (type.equalsIgnoreCase("updateSignalQuality")) mHandler.post(updateSignalQuality);
		if (type.equalsIgnoreCase("updateWFFrequency")) updateWFFrequency(Double.parseDouble(data));
	}

	// Update the Outbox list view IF we are on that view AND NOT in a popup
	// Used when sending ALL Outbox messages and we stay on the Outbox view
	// This way the list shortens every time a message is sent
	// class DisplayMessagesRunner implements Runnable
	// {
	// // @Override
	// public void run()
	// {
	// runOnUiThread(new Runnable()
	// {
	// public void run()
	// {
	// if (currentView == OUTBOXVIEW && !inFormDisplay)
	// {
	// displayMessages(BOTTOM, currentView);
	// }
	// }
	// });
	// }
	// }

	public void DisplayTime()
	{
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				// Now update progress info in Title if we are TXing
				if (GS.gI().isTXActive() && !Modem.modemIsTuning)
				{
					int percent = Modem.getTxProgressPercent();
					GS.gI().setTxProgressCount(": " + Integer.toString(percent) + "%");
					SCVA.mHandler.post(SCVA.updateTitle);
				}
			}
		});
	}

	class DisplayTimeRunner implements Runnable
	{
		// @Override
		public void run()
		{
			while (!Thread.currentThread().isInterrupted())
			{
				try
				{
					// Update time display in Terminal window
					DisplayTime();
					// Check every second if we have a new message Rx timeout
					Modem.checkExtractTimeout();
					Thread.sleep(1000);
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				catch (Exception e)
				{
				}
			}
		}
	}

	@Override
	public void onBackPressed()
	{
		if (SCVA.inFormDisplay)
		{ // In "popup" screen displaying individual messages
			/*
			 * if (currentView != FORMSVIEW && currentView != TEMPLATESVIEW && currentView != DRAFTSVIEW ) { displayMessages(BOTTOM, currentView); } else {
			 */
			if (++backKeyPressCount < 2)
			{
				lastBackKeyTime = System.currentTimeMillis();
			}
			else
			{ // Was the second key press "in-time"?
				if (System.currentTimeMillis() > lastBackKeyTime + 1200)
				{
					// Not in time, reset the count to zero. Need two new Back key presses.
					Toast.makeText(this, getString(R.string.txt_PressBackTwice), Toast.LENGTH_SHORT).show();
					backKeyPressCount = 1;
					// Count this back press as one
					lastBackKeyTime = System.currentTimeMillis();
				}
				else
				{
					// In time, therefore return
					// displayMessages(BOTTOM, currentView);
					// backKeyPressCount = 0;
				}
			}
		}
		else
		{
			Toast.makeText(this, getString(R.string.txt_PleaseUseMenuExit), Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev)
	{
		boolean handled = super.dispatchTouchEvent(ev);
		handled = mGesture.onTouchEvent(ev);
		return handled;
	}

	private SimpleOnGestureListener mOnGesture = new GestureDetector.SimpleOnGestureListener()
	{
		private float xDistance, yDistance, lastX, lastY;

		@Override
		public boolean onDown(MotionEvent e)
		{
			xDistance = yDistance = 0f;
			lastX = e.getX();
			lastY = e.getY();
			return false;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			final float curX = e2.getX();
			final float curY = e2.getY();
			xDistance += (curX - lastX);
			yDistance += (curY - lastY);
			lastX = curX;
			lastY = curY;
			if (!SCVA.inFormDisplay)
			{
				if (Math.abs(xDistance) > Math.abs(yDistance) && Math.abs(velocityX) > 1500)
				{
					navigateScreens((int) xDistance);
					return false;
				}
			}
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
		{
			return false;
		}
		/*
		 * @Override public boolean onDoubleTap(MotionEvent e) { if (currentView == MODEMVIEWwithWF) { LinearLayout wfBox = (LinearLayout) findViewById(R.id.FrameLayout3B); int startX, endX, startY, endY; int[] startLocation = new int[2]; wfBox.getLocationOnScreen(startLocation);
		 * startX = startLocation[0]; endX = startLocation[0] + wfBox.getWidth(); startY = startLocation[1]; endY = startLocation[1] + wfBox.getHeight(); if (e.getY() > startY && e.getY() < endY) { if (e.getX() > startX && e.getX() < endX) { if (e.getX() < ((startX + endX) / 2))
		 * { Modem.searchDown(); } else { Modem.searchUp(); } } } } return super.onDoubleTap(e); }
		 */
	};

	// Swipe (fling) handling to move from screen to screen
	private void navigateScreens(int flingDirection)
	{
		// Navigate between screens by gesture (on top of menu button acces)
		if (flingDirection > 0)
		{
			// swipe/fling right
			switch (GS.gI().getCurrentView())
			{
			case Constants.TERMVIEW:
				// displayMessages(RIGHT, lastMsgScreen);
				displayModem(RIGHT, true);
				break;

			case Constants.MODEMVIEWnoWF:
			case Constants.MODEMVIEWwithWF:
				displayTerminal(RIGHT);
				break;

			case Constants.INBOX_VIEW:
			case Constants.DRAFTS_VIEW:
			case Constants.OUTBOX_VIEW:
			case Constants.FORMSVIEW:
			case Constants.TEMPLATESVIEW:
			case Constants.SENTITEMSVIEW:
				displayModem(RIGHT, true);
				break;

			// case NEWMSGVIEW :
			// do nothing
			// break;

			default:
				displayTerminal(RIGHT); // Just in case

			}
		}
		else
		{
			// swipe/fling left
			switch (GS.gI().getCurrentView())
			{
			case Constants.TERMVIEW:
				displayModem(LEFT, true);
				break;
			case Constants.MODEMVIEWnoWF:
			case Constants.MODEMVIEWwithWF:
				// displayAPRS(LEFT);
				// displayMessages(LEFT, lastMsgScreen);
				displayTerminal(LEFT);
				break;

			case Constants.INBOX_VIEW:
			case Constants.DRAFTS_VIEW:
			case Constants.OUTBOX_VIEW:
			case Constants.FORMSVIEW:
			case Constants.TEMPLATESVIEW:
			case Constants.SENTITEMSVIEW:
				displayTerminal(LEFT);
				break;
			// case NEWMSGVIEW :
			// do nothing
			// break;
			default:
				displayTerminal(LEFT); // Just in case
			}
		}
	}

	public static boolean bluetoothPermit = false;
	public static boolean bluetoothAdminPermit = false;
	public static boolean readLogsPermit = false;
	public static boolean fineLocationPermit = false;

	// Redundant: public static boolean readExtStoragePermit = false;
	public static boolean writeExtStoragePermit = false;
	public static boolean cameraPermit = false;
	public static boolean recordAudioPermit = false;
	public static boolean modifyAudioSettingsPermit = false;
	public static boolean internetPermit = false;
	public static boolean broadcastStickyPermit = false;
	public static boolean readPhoneStatePermit = false;
	private final int REQUEST_PERMISSIONS = 15556;
	public final String[] permissionList =
	{
			// Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH,
			// Manifest.permission.BLUETOOTH_ADMIN,Manifest.permission.INTERNET,
			// Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.READ_LOGS,
			Manifest.permission.BROADCAST_STICKY, Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA };

	// Request permission from the user
	private void requestAllCriticalPermissions()
	{
		AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
		myAlertDialog.setMessage(getString(R.string.txt_PermissionsExplained));
		myAlertDialog.setCancelable(false);
		myAlertDialog.setPositiveButton(getString(R.string.bt_Continue), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				ActivityCompat.requestPermissions(myInstance, permissionList, REQUEST_PERMISSIONS);
			}
		});
		myAlertDialog.show();
	}

	private boolean allPermissionsOk()
	{
		final int granted = PackageManager.PERMISSION_GRANTED;

		// fineLocationPermit = ContextCompat.checkSelfPermission(myContext,
		// Manifest.permission.ACCESS_FINE_LOCATION) == granted;
		// bluetoothPermit = ContextCompat.checkSelfPermission(myContext,
		// Manifest.permission.BLUETOOTH) == granted;
		// bluetoothAdminPermit = ContextCompat.checkSelfPermission(myContext,
		// Manifest.permission.BLUETOOTH_ADMIN) == granted;
		// readExtStoragePermit = ContextCompat.checkSelfPermission(myContext,
		// Manifest.permission.READ_EXTERNAL_STORAGE) == granted;
		writeExtStoragePermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == granted;
		recordAudioPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.RECORD_AUDIO) == granted;
		modifyAudioSettingsPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.MODIFY_AUDIO_SETTINGS) == granted;
		readLogsPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.READ_LOGS) == granted;
		broadcastStickyPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.BROADCAST_STICKY) == granted;
		// internetPermit = ContextCompat.checkSelfPermission(myContext,
		// Manifest.permission.INTERNET) == granted;
		readPhoneStatePermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.READ_PHONE_STATE) == granted;
		cameraPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.CAMERA) == granted;
		// Redundant read permission
		// return fineLocationPermit && bluetoothPermit && bluetoothAdminPermit &&
		// readExtStoragePermit && writeExtStoragePermit
		return cameraPermit && fineLocationPermit && bluetoothPermit && bluetoothAdminPermit && writeExtStoragePermit && recordAudioPermit && modifyAudioSettingsPermit // &&
		// readLogsPermit
		// never
		// granted in later versions
		// of Android
				&& broadcastStickyPermit && internetPermit && readPhoneStatePermit;
	}

	private boolean criticalPermissionsOk()
	{
		final int granted = PackageManager.PERMISSION_GRANTED;

		recordAudioPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.RECORD_AUDIO) == granted;
		modifyAudioSettingsPermit = ContextCompat.checkSelfPermission(myContext, Manifest.permission.MODIFY_AUDIO_SETTINGS) == granted;

		return recordAudioPermit && modifyAudioSettingsPermit;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		int granted = PackageManager.PERMISSION_GRANTED;
		for (int i = 0; i < grantResults.length; i++)
		{
			if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION))
			{
				fineLocationPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.BLUETOOTH))
			{
				bluetoothPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.BLUETOOTH_ADMIN))
			{
				bluetoothAdminPermit = grantResults[i] == granted;
				// Redundant as we have write permission below
				// } else if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE))
				// {
				// readExtStoragePermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE))
			{
				writeExtStoragePermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.CAMERA))
			{
				cameraPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.RECORD_AUDIO))
			{
				recordAudioPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.MODIFY_AUDIO_SETTINGS))
			{
				modifyAudioSettingsPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.READ_LOGS))
			{
				readLogsPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.BROADCAST_STICKY))
			{
				broadcastStickyPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.INTERNET))
			{
				internetPermit = grantResults[i] == granted;
			}
			else if (permissions[i].equals(Manifest.permission.READ_PHONE_STATE))
			{
				readPhoneStatePermit = grantResults[i] == granted;
			}
			else
			{
				// Nothing so far
			}
		}
		// Re-do overall check
		havePassedAllPermissionsTest = allPermissionsOk();
		havePassedCriticalPermissionsTest = criticalPermissionsOk();
		if (true)
		{// havePassedCriticalPermissionsTest && requestCode == REQUEST_PERMISSIONS) {
			// //Only if requested at OnCreate time
			performOnCreate();
			performOnStart();
		}
		else
		{
			// Close that activity and return to previous screen
			finish();
			// Kill the process
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	}

	public void showPasswordDialog()
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.reveal_key_dialog);
		dialog.setCancelable(true);

		if (Constants.devMode)
		{
			((EditText) dialog.findViewById(R.id.password)).setText("abc123");
		}

		TextView keyName = dialog.findViewById(R.id.keyName);
		keyName.setText("" + activeKey.getName());

		Button unlock = (Button) dialog.findViewById(R.id.unlock);
		unlock.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				String plainKey = activeKey.getPlainKey(((EditText) dialog.findViewById(R.id.password)).getText().toString());
				if (plainKey != null)
				{
					GS.gI().setPlainKey(plainKey);
					new Utils().showNotification(SCVA.this, activeKey.getName());
					Toast.makeText(SCVA.this, "Key is unlocked.", Toast.LENGTH_LONG).show();
					password = ((EditText) dialog.findViewById(R.id.password)).getText().toString();
					init_encryption();
				}
				else
				{
					Toast.makeText(SCVA.this, "Password error.", Toast.LENGTH_LONG).show();
				}
			}
		});
		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
			}
		});
		dialog.show();
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		GS.gI().setContext(this);
		utils = new Utils();

		keysDataSource = new KeysDataSource(this);
		settingsDataSource = new SettingsDataSource(this);

		// Avoid app restart if already running and pressing on the app icon again
		if (!isTaskRoot())
		{
			final Intent intent = getIntent();
			final String intentAction = intent.getAction();
			if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction.equals(Intent.ACTION_MAIN))
			{
				// Utils.println(LOG_TAG, "Main Activity is not the root. Finishing Main Activity
				// instead of launching.");
				finish();
				return;
			}
		}

		// Debug only: Menu hack to force showing the overflow button (aka menu button)
		// on the
		// action bar EVEN IF there is a hardware button
		try
		{
			ViewConfiguration config = ViewConfiguration.get(this);
			Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");

			if (menuKeyField != null)
			{
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(config, false);
			}
		}
		catch (Exception e)
		{
			// presumably, not relevant
		}

		// Enable progress bar in action bar
		// supportRequestWindowFeature(Window.FEATURE_PROGRESS);
		// supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		// Get a static copy of the base Context
		myContext = SCVA.this;

		// Get a static copy of the activity instance
		this.myInstance = this;

		havePassedAllPermissionsTest = allPermissionsOk();
		havePassedCriticalPermissionsTest = criticalPermissionsOk();
		if (havePassedCriticalPermissionsTest)
		{
			performOnCreate();
		}
		else
		{
			requestAllCriticalPermissions();
		}
	}

	// Could be executed only when all necessary permissions are allowed
	private void performOnCreate()
	{
		// Call the folder handling method
		// Processor.handleFolderStructure();

		// Get new gesture detector for flings over scrollviews
		mGesture = new GestureDetector(this, mOnGesture);

		// Initialize Toast for use in the Toast display routines below
		myToast = Toast.makeText(SCVA.this, "", Toast.LENGTH_SHORT);

		// Debug
		// middleToastText("ONCREATE CALLED");

		// Launch task for time display (Time is GPS time aligned if requested)
		Runnable displayTimeRunnable = new DisplayTimeRunner();
		Thread displayTimeThread = new Thread(displayTimeRunnable);
		displayTimeThread.start();

		// Get last mode (if not set, returns -1)
		GS.gI().setRxModem(Processor.TxModem = config.getPreferenceI("LASTMODEUSED", -1));

		// Get the RSID flags from stored preferences
		Modem.rxRsidOn = config.getPreferenceB("RXRSID", true);
		Modem.txRsidOn = config.getPreferenceB("TXRSID", true);

		// Get the AFC flags from stored preferences
		Modem.afcOn = config.getPreferenceB("AFCONOFF", true);
		Modem.setAfc(Modem.afcOn);

		// Update the list of available modems
		Modem.updateModemCapabilityList();

		// If we do not have a last mode, this is the first time in the app (use NBEMS
		// safest default: MT63-2000-Long)
		if (GS.gI().getRxModem() == -1) GS.gI().setRxModem(Modem.getMode("MT63_2000_LG"));

		// We start with the Terminal screen
		// STAR:
		displayTerminal(NORMAL);
	}

	/**
	 * Called when the activity is (re)started (to foreground)
	 **/
	@Override
	public void onStart()
	{
		super.onStart();

		Intent intent = new Intent(this, Processor.class);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		// Conditional to having passed the permission tests
		if (havePassedCriticalPermissionsTest)
		{
			performOnStart();
		}
	}

	// Only when all permissions are agreed on
	@TargetApi(Build.VERSION_CODES.O)
	void performOnStart()
	{
		// Refresh defaults since we could be coming back
		// from the preference activity

		// Re-initilize modem when NOT busy to use the latest parameters
		if (!GS.gI().isReceivingForm() && GS.gI().isRXParamsChanged())
		{
			// Reset flag then stop and restart modem
			GS.gI().setRXParamsChanged(false);
			// Cycle modem service off then on
			if (GS.gI().isProcessorON())
			{
				if (Modem.modemState == Modem.RXMODEMRUNNING)
				{
					Modem.stopRxModem();
					stopService(new Intent(SCVA.this, Processor.class));
					GS.gI().setProcessorON(false);
					// Force garbage collection to prevent Out Of Memory errors
					// on small RAM devices
					System.gc();
				}
			}
			// Wait for modem to stop and then restart
			while (Modem.modemState != Modem.RXMODEMIDLE)
			{
				try
				{
					Thread.sleep(250);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			// Force garbage collection to prevent Out Of Memory errors on small
			// RAM devices
			System.gc();

			// Get current mode index (returns first position if not in list
			// anymore in case we changed to custom mode list on the current
			// mode)
			GS.gI().setRxModem(Processor.TxModem = Modem.customModeListInt[Modem.getModeIndex(GS.gI().getRxModem())]);

			GS.gI().setProcessorON(true);
			// startService(new Intent(SCVA.this, Processor.class));

			// Finally, if we were on the modem screen AND we come back to it,
			// then redisplay in case we changed the waterfall frequency
			if (GS.gI().getCurrentView() == Constants.MODEMVIEWwithWF)
			{
				displayModem(NORMAL, true);
			}
		}
		else
		{ // start if not ON yet AND we haven't paused the modem manually
			if (!GS.gI().isProcessorON() && !GS.gI().isModemPaused())
			{
				GS.gI().setMyNotification(utils.getNotification(this));
				// Force garbage collection to prevent Out Of Memory errors on
				// small RAM devices
				System.gc();
				startService(new Intent(SCVA.this, Processor.class));
				GS.gI().setProcessorON(true);
			}
		}
		SCVA.mHandler.post(SCVA.updateTitle);
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if (GS.gI().getValue().getBoolean("use_enc", false))
		{
			activeKey = keysDataSource.getActiveKey();
			if (password.length() == 0)
			{
				showPasswordDialog();
			}
		}
		// Update the field in the webview
		// Call a Javascript on the webview to add to the arl text field
		if (updateCsvField)
		{
			// escape characters as we display in a Webview
			// Done on the activityResult csvData = Message.escape(csvData);
			SCVA.this.runOnUiThread(new Runnable()
			{
				public void run()
				{
					mWebView.loadUrl("javascript:setField('" + ":mg:" + "','" + csvData + "')");
					// Reset field to make sure
					SCVA.csvData = "";
				}
			});
			// Reset update flag for subsequent calls of onResume
			updateCsvField = false;
		}

		// try
		// {
		// init_encryption();
		// }
		// catch (Exception e)
		// {
		// Utils.println("EncInitErr", e);
		// Toast.makeText(this, "Encryptor initialization error\nEncryption now is
		// disabled\ncheck settings", Toast.LENGTH_LONG);
		// GS.gI().getEditor().putBoolean("use_enc", false).apply();
		// }
	}

	void init_encryption()
	{
		// For Encryptor initialization, TODO: implements all algorithm
		if (!GS.gI().getValue().getBoolean("use_enc", false));
		else
		{
			String algo = GS.gI().getValue().getString("enc_algorithm", "");
			if (activeKey == null)
			{
				encryptor = null;
				Toast.makeText(this, "No key is available to communicate", Toast.LENGTH_LONG);
			}
			else
			{
				String key = activeKey.getPlainKey(password);
				if (key.length() > 0)
				{
					// String key = "1234567890123456789012345678901234567890123456789012";
					switch (algo)
					{
					case "aes":
						encryptor = new Aes256Encryptor(key);
						break;
					case "rc6":
						encryptor = new Rc6Encryptor(key);
						break;
					case "twofish":
						encryptor = new TwofishEncryptor(key);
						break;
					case "serpent":
						encryptor = new SerpentEncryptor(key);
						break;
					case "gost28147":
						encryptor = new Gost28147Encryptor(key);
						break;
					case "blowfish":
						encryptor = new BlowfishEncryptor(key);
						break;
					}

					String tmp = encryptor.encrypt("Salam");
					String dtmp = encryptor.decrypt(tmp);
					Utils.println("tr " + tmp + "\t" + dtmp);

					// Utils.println("hello " + encryptor.encrypt("hello") + "" +
					// encryptor.decrypt(encryptor.encrypt("hello")));
					// Utils.println("hello how are " + encryptor.encrypt("hello how are") + ""
					// + encryptor.decrypt(encryptor.encrypt("hello how are")));
					// Utils.println("hello how are you " + encryptor.encrypt("hello how are
					// you") + "" + encryptor.decrypt(encryptor.encrypt("hello how are you")));
				}
				utils.showNotification(this, activeKey.getName());
			}
		}
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access Framework Documents, as well as the _data field for the MediaStore and other file-based ContentProviders.
	 *
	 * @param context
	 *            The context.
	 * @param uri
	 *            The Uri to query.
	 * @author paulburke
	 */
	@TargetApi(Build.VERSION_CODES.KITKAT)
	public static String getPath(final Context context, final Uri uri)
	{
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri))
		{
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri))
			{
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type))
				{
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri))
			{
				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri))
			{
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type))
				{
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				}
				else if ("video".equals(type))
				{
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				}
				else if ("audio".equals(type))
				{
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[]
				{ split[1] };

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme()))
		{
			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme()))
		{
			return uri.getPath();
		}

		return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context
	 *            The context.
	 * @param uri
	 *            The Uri to query.
	 * @param selection
	 *            (Optional) Filter used in the query.
	 * @param selectionArgs
	 *            (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs)
	{

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection =
		{ column };

		try
		{
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
			if (cursor != null && cursor.moveToFirst())
			{
				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		}
		finally
		{
			if (cursor != null) cursor.close();
		}
		return null;
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri)
	{
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri)
	{
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri
	 *            The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri)
	{
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		final int myrc = requestCode;

		// We were editing a CSV data set in an outside app (e.g. OfficeSuite)
		if (myrc == EDIT_CSV_RESULT)
		{
			String sBuffer = "";
			// Read csv file back
			try
			{
				File csvFile = new File(Constants.HomePath + Constants.Dirprefix + Constants.DirTemp + Constants.Separator + Constants.tempCSVfn);
				if (csvFile.isFile())
				{
					FileInputStream fileISi = new FileInputStream(csvFile);
					BufferedReader buf0 = new BufferedReader(new InputStreamReader(fileISi));
					String readString0 = new String();
					while ((readString0 = buf0.readLine()) != null)
					{
						sBuffer += readString0 + "\n";
					}
					// Flag onResume to update the field containing the CSV data in the webview
					updateCsvField = true;
					// Store in static String
					csvData = sBuffer;
					// Escape as we don't use quotes around the TEXTAREA fields
					csvData = Message.escape(csvData);
				}
			}
			catch (IOException e)
			{
				loggingClass.writelog("Error reading back CSV file from spreadsheet editor" + e.getMessage(), null, true);
			}
		}

		// Share message action when done from the Outbox (we move the message to Sent
		// box)
		if (requestCode == SHARE_OUTBOX_MESSAGE_RESULT)
		{
			// Contrary to ALL OTHER apps, Gmail does not return RESULT_OK if the
			// user presses the Send action in the email....Grrrrrr
			// So we can't use this callback to automate the transfer to the sent folder!!!!
			// Solution: we ask the user if the code is not explicitly RESULT_OK
			if (resultCode == RESULT_OK)
			{
				if (lastSharedFileName.length() > 0)
				{
					// moveToSentFolder();
				}
			}
			else
			{
				// Ask first
				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
				alertDialogBuilder.setMessage(myContext.getString(R.string.txt_DoYouWantToMoveMsgToSentFolder));
				alertDialogBuilder.setPositiveButton(myContext.getString(R.string.txt_Yes), new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface arg0, int arg1)
					{
						// moveToSentFolder();
						// Need to clear the message display as well as it is not in the Outbox
						// anymore!!!
						// returnFromFormView();
					}
				});

				alertDialogBuilder.setNegativeButton(myContext.getString(R.string.txt_No), new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						dialog.dismiss();
					}
				});

				AlertDialog alertDialog = alertDialogBuilder.create();
				alertDialog.show();
			}
		}

		// Camera/Gallery/File picture request
		if (requestCode == PICTURE_REQUEST_CODE)
		{
			if (resultCode == RESULT_OK)
			{
				// final boolean isCamera;
				boolean isCamera; // data.getData().getPath()
				if (data == null)
				{
					isCamera = true;
				}
				else if (data.getData() == null)
				{
					isCamera = true;
				}
				else if (data.getAction() == null || data.getData().getPath() != null)
				{
					// if (action == null) {
					// isCamera = false;
					// } else {
					isCamera = false;
				}
				else
				{
					final String action = data.getAction();
					isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
				}
				// Uri selectedImageUri;
				String imageFullFilePath;

				// To replace the Android Webview unconsistent HTML5 readAsDataURL(file)
				jsEncodedImageLine = "";
				try
				{
					if (isCamera)
					{
						// selectedImageUri = outputFileUri;
						imageFullFilePath = outputFilePath;
					}
					else
					{
						// selectedImageUri = data == null ? null : data.getData();
						imageFullFilePath = getPath(SCVA.myContext, data.getData()); // .getPath(); //getPath(SCVA.myContext, selectedImageUri);
					}
					// Scale down image if likely to be too large (max megapixel size in User
					// preference)
					// String imageFullFilePath = getPath(SCVA.myContext, selectedImageUri);
					BitmapFactory.Options bmOptions = new BitmapFactory.Options();
					// Just get the size without the picture data
					bmOptions.inJustDecodeBounds = true;
					BitmapFactory.decodeFile(imageFullFilePath, bmOptions);
					int photoW = bmOptions.outWidth;
					int photoH = bmOptions.outHeight;
					// Adjust if required (powers of 2 only for scale factor)
					double targetMaxMegaPixel = config.getPreferenceD("TARGETMAXMEGAPIXELS", 0.5);
					int scaleFactor = 1;
					if (photoW * photoH > targetMaxMegaPixel)
					{
						scaleFactor = (int) ((photoW * photoH) / (targetMaxMegaPixel * 1000000));
					}
					// Now real decode with sub-sampling
					bmOptions.inJustDecodeBounds = false;
					bmOptions.inSampleSize = scaleFactor;
					bmOptions.inPurgeable = true;
					Bitmap tempImageBitmap = BitmapFactory.decodeFile(imageFullFilePath, bmOptions);
					// Extract file name by itself
					String imageFileName = imageFullFilePath;
					if (imageFullFilePath.indexOf("/") != -1)
					{
						imageFileName = imageFullFilePath.substring(imageFullFilePath.lastIndexOf("/") + 1);
					}
					// Save the picture in jpg and png format and choose the smallest file
					String tempPngImageFn = Constants.HomePath + Constants.Dirprefix + Constants.DirTemp + Constants.Separator + imageFileName + ".png";

					File dest = new File(tempPngImageFn);
					FileOutputStream out = new FileOutputStream(dest);
					tempImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
					out.flush();
					out.close();
					// Get the size of the compressed image in png format
					File pngFile = new File(tempPngImageFn);
					int pngImageSize = (int) pngFile.length();
					// Repeat compression with jpeg format
					String tempJpgImageFn = Constants.HomePath + Constants.Dirprefix + Constants.DirTemp + Constants.Separator + imageFileName + ".jpg";

					dest = new File(tempJpgImageFn);
					out = new FileOutputStream(dest);
					int jpegQuality = config.getPreferenceI("JPEGQUALITY", 70);
					if (jpegQuality < 0) jpegQuality = 0;
					if (jpegQuality > 100) jpegQuality = 100;
					tempImageBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
					out.flush();
					out.close();
					// Get the size of the compressed image in png format
					File jpgFile = new File(tempJpgImageFn);
					int jpgImageSize = (int) jpgFile.length();
					// Choose smallest
					String smallestImageFn = tempJpgImageFn;
					if (pngImageSize <= jpgImageSize * 1.1)
					{ // 10% allowance for better image quality
						smallestImageFn = tempPngImageFn;
					}
					else
					{
						// We have jpeg image, copy the important Exif tags in the shrinked picture
						// copy paste exif information from original file to new
						// file
						ExifInterface oldexif = new ExifInterface(imageFullFilePath);
						ExifInterface newexif = new ExifInterface(tempJpgImageFn);

						int build = Build.VERSION.SDK_INT;

						// From API 11
						if (build >= 11)
						{
							if (oldexif.getAttribute("FNumber") != null)
							{
								newexif.setAttribute("FNumber", oldexif.getAttribute("FNumber"));
							}
							if (oldexif.getAttribute("ExposureTime") != null)
							{
								newexif.setAttribute("ExposureTime", oldexif.getAttribute("ExposureTime"));
							}
							if (oldexif.getAttribute("ISOSpeedRatings") != null)
							{
								newexif.setAttribute("ISOSpeedRatings", oldexif.getAttribute("ISOSpeedRatings"));
							}
						}
						// From API 9
						if (build >= 9)
						{
							if (oldexif.getAttribute("GPSAltitude") != null)
							{
								newexif.setAttribute("GPSAltitude", oldexif.getAttribute("GPSAltitude"));
							}
							if (oldexif.getAttribute("GPSAltitudeRef") != null)
							{
								newexif.setAttribute("GPSAltitudeRef", oldexif.getAttribute("GPSAltitudeRef"));
							}
						}
						// From API 8
						if (build >= 8)
						{
							if (oldexif.getAttribute("FocalLength") != null)
							{
								newexif.setAttribute("FocalLength", oldexif.getAttribute("FocalLength"));
							}
							if (oldexif.getAttribute("GPSDateStamp") != null)
							{
								newexif.setAttribute("GPSDateStamp", oldexif.getAttribute("GPSDateStamp"));
							}
							if (oldexif.getAttribute("GPSProcessingMethod") != null)
							{
								newexif.setAttribute("GPSProcessingMethod", oldexif.getAttribute("GPSProcessingMethod"));
							}
							if (oldexif.getAttribute("GPSTimeStamp") != null)
							{
								newexif.setAttribute("GPSTimeStamp", "" + oldexif.getAttribute("GPSTimeStamp"));
							}
						}
						if (oldexif.getAttribute("DateTime") != null)
						{
							newexif.setAttribute("DateTime", oldexif.getAttribute("DateTime"));
						}
						if (oldexif.getAttribute("Flash") != null)
						{
							newexif.setAttribute("Flash", oldexif.getAttribute("Flash"));
						}
						if (oldexif.getAttribute("GPSLatitude") != null)
						{
							newexif.setAttribute("GPSLatitude", oldexif.getAttribute("GPSLatitude"));
						}
						if (oldexif.getAttribute("GPSLatitudeRef") != null)
						{
							newexif.setAttribute("GPSLatitudeRef", oldexif.getAttribute("GPSLatitudeRef"));
						}
						if (oldexif.getAttribute("GPSLongitude") != null)
						{
							newexif.setAttribute("GPSLongitude", oldexif.getAttribute("GPSLongitude"));
						}
						if (oldexif.getAttribute("GPSLatitudeRef") != null)
						{
							newexif.setAttribute("GPSLongitudeRef", oldexif.getAttribute("GPSLongitudeRef"));
						}

						// Need to update it, with your new height width
						// Not yet new exif.setAttribute("ImageLength",
						// "200");
						// Not yet newexif.setAttribute("ImageWidth",
						// "200");

						if (oldexif.getAttribute("Make") != null)
						{
							newexif.setAttribute("Make", oldexif.getAttribute("Make"));
						}
						if (oldexif.getAttribute("Model") != null)
						{
							newexif.setAttribute("Model", oldexif.getAttribute("Model"));
						}
						if (oldexif.getAttribute("Orientation") != null)
						{
							newexif.setAttribute("Orientation", oldexif.getAttribute("Orientation"));
						}
						if (oldexif.getAttribute("WhiteBalance") != null)
						{
							newexif.setAttribute("WhiteBalance", oldexif.getAttribute("WhiteBalance"));
						}

						newexif.saveAttributes();
					}
					//
					File imgFile = new File(smallestImageFn);
					// Read the compressed image file
					long fileSize = imgFile.length();
					// Read the full file in a byte buffer
					byte[] rawPictureBuffer = new byte[(int) fileSize];
					FileInputStream fileIs = new FileInputStream(imgFile);
					fileIs.read(rawPictureBuffer);
					// Encode in Base64 to make it text compatible
					jsEncodedImageLine = "data:image/png;base64," + Base64.encodeToString(rawPictureBuffer, Base64.NO_WRAP);
					fileIs.close();
					rawPictureBuffer = null;
					System.gc();
				}
				catch (Exception e)
				{
					loggingClass.writelog("Failed to read and encode picture file" + e.getMessage(), null, true);
				}

				// Removed as not necessary as there are never ever any "\" or "\n" or "\r" in
				// the base64 string
				// jsEncodedImageLine = Message.escape(jsEncodedImageLine);

				// Update the fields in the UI thread
				// final String myImageFile = "file://" + SCVA.getPath(SCVA.myContext,
				// selectedImageUri);
				// getRealPathFromURI(selectedImageUri); ///.getLastPathSegment();
				// ///.getPath(); ////.toString();
				((Activity) SCVA.myContext).runOnUiThread(new Runnable()
				{

					public void run()
					{
						// Update the <IMG>.src image preview
						// Since we use a temporary file, load with the compressed info, not the file
						// name
						// String jsAction = "javascript:setImgSource('"
						// + _imgFieldToUpdate + "','" + myImageFile + "')";
						String jsAction = "javascript:setImgSource('" + _imgFieldToUpdate + "','" + jsEncodedImageLine + "')";
						mWebView.loadUrl(jsAction);
						jsAction = null;
						System.gc();
						// Update the Textarea containing the base64 encoded image
						jsAction = "javascript:setImgTextarea('" + _imgFieldToUpdate + "','" + jsEncodedImageLine + "')";
						mWebView.loadUrl(jsAction);
						// "Free" the large string variables (changed from final to static)
						jsAction = null;
						jsEncodedImageLine = null;
						System.gc();
					}
				});
			}
		}
	}

	// Use external apps to select an image file (Camera, file system or Gallery)
	// Based on code from www.stackoverflow.com (thank you David Manpearl and Austyn
	// Mahoney)
	private Uri outputFileUri;
	private String outputFilePath = "";

	private void openImageIntent()
	{
		// Determine Uri of camera image to save.
		// Android 11 tightening os file access means we have to use a shared directory
		// for the camera to write to
		// final File root = new File(Constants.HomePath + Constants.Dirprefix
		// + Processor.DirTemp + Constants.Separator);
		// final File root = new
		// File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
		// Constants.HomePath);
		// String rootPath =
		// getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/" +
		// "picFolder/";
		String rootPath = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/";
		File root = new File(rootPath);
		// File root = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		// final File root = new File(Environment.getExternalStorageDirectory(),
		// "pictures/");
		if (!root.exists())
		{
			root.mkdir();
		}

		/*
		 *
		 * File f = new File(rootPath + "mttext.txt"); if (f.exists()) { f.delete(); } f.createNewFile();
		 */
		// final File sdImageFile = new File(root, tempAttachPictureFname);
		final File sdImageFile;
		try
		{
			// sdImageFile = File.createTempFile(tempAttachPictureFname, ".jpg", root);
			sdImageFile = new File(rootPath + tempAttachPictureFname + ".jpg");
			if (sdImageFile.exists())
			{
				sdImageFile.delete();
			}
			sdImageFile.createNewFile();
			// File image = File.createTempFile(
			// imageFileName, /* prefix */
			// ".jpg", /* suffix */
			// storageDir /* directory */ );
			// sdImageFile = File.createTempFile(tempAttachPictureFname, ".jpg");
			// outputFileUri = Uri.fromFile(sdImageFile);
			// Context context = getActivity().getBaseContext();
			// outputFileUri = FileProvider.getUriForFile(myContext,
			// myContext.getApplicationContext().getPackageName() + ".myFileProvider",
			// sdImageFile);
			outputFileUri = FileProvider.getUriForFile(myContext, myContext.getApplicationContext().getPackageName() + ".provider", sdImageFile);
			outputFilePath = sdImageFile.getAbsolutePath();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		// Camera
		final List<Intent> cameraIntents = new ArrayList<Intent>();
		final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
		captureIntent.setFlags(FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION);
		final PackageManager packageManager = getPackageManager();
		final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
		for (ResolveInfo res : listCam)
		{
			final String packageName = res.activityInfo.packageName;
			myContext.grantUriPermission(packageName, outputFileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
			final Intent intent = new Intent(captureIntent);
			intent.setFlags(FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION);
			intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
			intent.setPackage(packageName);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
			cameraIntents.add(intent);
		}

		// Filesystem.
		final Intent galleryIntent = new Intent();
		galleryIntent.setFlags(FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION);
		galleryIntent.setType("image/*");
		galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

		// Chooser of filesystem options.
		final Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.txt_SelectSource));

		// Add the camera options.
		chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[cameraIntents.size()]));

		startActivityForResult(chooserIntent, PICTURE_REQUEST_CODE);
	}

	// Option Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	// Customize the Option Menu at run time
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{

		/*
		 * if (Modem.canSendPicture(Processor.RxModem)) menu.findItem(R.id.sendimage).setEnabled(true); else menu.findItem(R.id.sendimage).setEnabled(false);
		 */
		if (!GS.gI().isAdvanceMode())
		{
			// menu.removeItem(R.id.seeLog);
			menu.removeItem(R.id.burnPreferences);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	// Option Screen handler
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
		switch (item.getItemId())
		{
		case R.id.prefs:
			Intent OptionsActivity = new Intent(SCVA.this, myPreferences.class);
			startActivity(OptionsActivity);
			break;
		// case R.id.savePreferences:
		// config.saveSharedPreferencesToFile("SettingsBackup.bin");
		// break;
		// case R.id.restorePreferences:
		// config.loadSharedPreferencesFromFile("SettingsBackup.bin");
		// break;
		// case R.id.defaultPreferences:
		// config.restoreSettingsToDefault();
		// break;
		case R.id.burnPreferences:
			burnEveryThing();
			break;
		// case R.id.BTon:
		// mBluetoothHeadset.startVoiceRecognition();
		// if (mBluetoothAdapter != null)
		// {
		// // Code that works (Manual connect to BT device)
		// if (android.os.Build.VERSION.SDK_INT >= 8)
		// {
		// mAudioManager.startBluetoothSco();
		// }
		// mAudioManager.setMode(AudioManager.MODE_IN_CALL);
		// mAudioManager.setBluetoothScoOn(true);
		// toBluetooth = true;
		// }
		// break;
		// case R.id.BToff:
		// toBluetooth = false;
		// mAudioManager.setMode(AudioManager.MODE_NORMAL);
		// mAudioManager.setBluetoothScoOn(false);
		// if (android.os.Build.VERSION.SDK_INT >= 8)
		// {
		// mAudioManager.stopBluetoothSco();
		// }
		// break;
		case R.id.reloadforms:
			myAlertDialog.setMessage(getString(R.string.txt_AreYouSureOverwriteForms));
			myAlertDialog.setCancelable(false);
			myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					// First clear the flag
					GS.gI().getEditor().putBoolean("HAVERUNONCEBEFORE", false).apply();
					// Processor.handleFolderStructure();
				}
			});
			myAlertDialog.setNegativeButton(getString(R.string.txt_No), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					dialog.cancel();
				}
			});
			myAlertDialog.show();
			break;
		case R.id.exit:
			myAlertDialog.setMessage(getString(R.string.txt_AreYouSureExit));
			myAlertDialog.setCancelable(false);
			myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					utils.hideNotification(SCVA.this);
					// Toast.makeText(this, "GoodBye", Toast.LENGTH_SHORT).show();
					// Stop the Modem and Listening Service
					if (GS.gI().isProcessorON())
					{
						stopService(new Intent(SCVA.this, Processor.class));
						GS.gI().setProcessorON(false);
					}
					// Stop the GPS if running
					// if (locationManager != null) {
					// locationManager.removeUpdates(locationListener);
					// }
					// Close that activity and return to previous screen
					finish();
					// Kill the process
					android.os.Process.killProcess(android.os.Process.myPid());
				}
			});
			myAlertDialog.setNegativeButton(getString(R.string.txt_No), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					dialog.cancel();
				}
			});
			myAlertDialog.show();
			break;
		case R.id.About:
			displayAbout();
			break;
		}
		return true;
	}

	private void seeLog()
	{
		Intent intent = new Intent(this, LogsActivity.class);
		startActivity(intent);
	}

	private void burnEveryThing()
	{
		runOnUiThread(new Utils().showQuestionDialog("Warning", "Are your sure your want to burn ?", this, burn, noBurn));
	}

	Runnable burn = new Runnable()
	{
		@Override
		public void run()
		{
			if (warningCount == 2)
			{
				Utils.println("<<<<<<");
				warningCount = 0;
				// Burning the keys
				KeysDataSource keyStore = new KeysDataSource(SCVA.this);
				keyStore.burnKeyStore();

				new Utils().showNotification(SCVA.this, null);
				utils.hideNotification(SCVA.this);

				// Burning the Preferences
				SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(SCVA.this);
				settings.edit().clear().commit();

				// Burning the BackupFile
				config.burnBackupFile("SettingsBackup.bin");

				// Burning the logs
				SCVA.utils.burnLogs(SCVA.myContext);

				// Stop the Modem and Listening Service
				if (GS.gI().isProcessorON())
				{
					stopService(new Intent(SCVA.this, Processor.class));
					GS.gI().setProcessorON(false);
				}
				Toast.makeText(SCVA.this, "GoodBye :D :D :D", Toast.LENGTH_LONG).show();

				// Exiting the app dumps RAM
				finish();
				android.os.Process.killProcess(android.os.Process.myPid());
			}
			else
			{
				warningCount++;
				runOnUiThread(new Utils().showQuestionDialog("Warning", "Are your sure your want to burn ?", SCVA.this, burn, noBurn));
			}
		}
	};
	Runnable noBurn = new Runnable()
	{
		@Override
		public void run()
		{
			warningCount = 0;
		}
	};

	// Simple text transparent popups (Top of screen)
	public static void topToastText(String message)
	{
		try
		{
			myToast.setText(message);
			myToast.setGravity(Gravity.TOP, 0, 100);
			myToast.show();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	// Simple text transparent popups TOWARDS MIDDLE OF SCREEN
	public static void middleToastText(String message)
	{
		try
		{
			myToast.setText(message);
			myToast.setGravity(Gravity.CENTER, 0, 0);
			myToast.show();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	// Simple text transparent popups (Bottom of screen)
	public static void bottomToastText(String message)
	{
		try
		{
			myToast.setText(message);
			myToast.setGravity(Gravity.BOTTOM, 0, 100);
			myToast.show();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public static void screenAnimation(ViewGroup panel, int screenAnimation)
	{
		AnimationSet set = new AnimationSet(true);
		Animation animation = new AlphaAnimation(0.0f, 1.0f);
		animation.setDuration(100);
		set.addAnimation(animation);

		switch (screenAnimation)
		{
		case NORMAL:
			return;
		// break;
		case RIGHT:
			animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
					0.0f);
			break;
		case LEFT:
			animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
					0.0f);
			break;
		case TOP:
			animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF,
					0.0f);
			break;
		case BOTTOM:
			animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF,
					0.0f);
			break;
		}
		animation.setDuration(200);
		set.addAnimation(animation);

		LayoutAnimationController controller = new LayoutAnimationController(set, 0.25f);
		if (panel != null)
		{
			panel.setLayoutAnimation(controller);
		}
	}

	// Set the button text size based on user's preferences
	private void setTextSize(Button thisButton)
	{
		int textSize = config.getPreferenceI("BUTTONTEXTSIZE", 12);
		if (textSize < 7) textSize = 7;
		if (textSize > 20) textSize = 20;
		thisButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
	}

	// STAR: Display the Terminal layout and associate it's buttons
	private void displayTerminal(int screenMovement)
	{
		// Ensure we reset the swipe action
		SCVA.inFormDisplay = false;

		// Change layout and remember which one we are on
		GS.gI().setCurrentView(Constants.TERMVIEW);
		setContentView(R.layout.terminal);
		screenAnimation((ViewGroup) findViewById(R.id.termscreen), screenMovement);
		myTermTV = (TextView) findViewById(R.id.terminalview);
		myTermTV.setHorizontallyScrolling(false);
		myTermTV.setTextSize(16);
		myWindow = getWindow();

		// VK2ETA Delay title update until after modem init (capability list is NULL)
		// SCVA.mHandler.post(SCVA.updatetitle);

		// VK2ETA Debug test
		// setSupportProgressBarIndeterminateVisibility(true);
		// setProgressBarIndeterminateVisibility(true);
		// setSupportProgressBarVisibility(true);
		// setSupportProgressBarIndeterminate(false);
		// setSupportProgress(95);

		// If blank (on start), display version
		final String welcomeString = "\n" + SCVA.myContext.getString(R.string.txt_WelcomeToSCVA) + " " + Processor.version;
		// + SCVA.myContext.getString(R.string.txt_WelcomeIntro);
		if (TerminalBuffer.length() == 0 && !hasDisplayedWelcome)
		{
			TerminalBuffer = welcomeString;
		}
		else
		{
			if (TerminalBuffer.equals(welcomeString))
			{
				TerminalBuffer = "";
			}
		}
		hasDisplayedWelcome = true;
		// Reset terminal display in case it was blanked out by a new oncreate
		// call
		myTermTV.setText(TerminalBuffer);
		myTermSC = (ScrollView) findViewById(R.id.terminalscrollview);
		// update with whatever we have already accumulated then scroll
		SCVA.mHandler.post(SCVA.addToTerminal);
		// Advise which screen we are in
		middleToastText(getString(R.string.txt_TerminalScreen));

		// Restore the data entry field at the bottom
		EditText myView = (EditText) findViewById(R.id.edit_text_out);
		myView.setText(savedTextMessage);
		myView.setText("");
		// STAR:
		if (Constants.devMode)
		{
			// myView.setText("This is a very long message in fact it has 126 characters in
			// it and it is successfully transferred. this is for demo purposes.");
		}
		myView.setSelection(myView.getText().length());
		// Add a text watcher to save the text as it is being typed
		myView.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(Editable arg0)
			{
			}

			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
			{
			}

			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
			{
				savedTextMessage = arg0.toString();
			}
		});

		// JD Initialize the MODE UP button
		myButton = (Button) findViewById(R.id.button_modeUP);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						GS.gI().setRxModem(Processor.TxModem = Modem.getModeUpDown(GS.gI().getRxModem(), +1));
						Modem.changemode(GS.gI().getRxModem()); // to make the changes effective
						saveLastModeUsed(GS.gI().getRxModem());
						SCVA.mHandler.post(SCVA.updateTitle);
						SCVA.mHandler.post(SCVA.updateAFCCheckBox);
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the MODE DOWN button
		myButton = (Button) findViewById(R.id.button_modeDOWN);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						GS.gI().setRxModem(Processor.TxModem = Modem.getModeUpDown(GS.gI().getRxModem(), -1));
						Modem.changemode(GS.gI().getRxModem()); // to make the changes effective
						saveLastModeUsed(GS.gI().getRxModem());
						SCVA.mHandler.post(SCVA.updateTitle);
						SCVA.mHandler.post(SCVA.updateAFCCheckBox);
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the TUNE button
		myButton = (Button) findViewById(R.id.button_tune);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (!GS.gI().isTXActive() && !GS.gI().isReceivingForm() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						Modem.TxTune();
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the STOP TX button
		myButton = (Button) findViewById(R.id.button_stopTX);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (GS.gI().isTXActive())
					{
						Modem.stopTX = true;
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// STAR: JD Initialize the Send Text button (commands in connected mode)
		sendText = (Button) findViewById(R.id.button_sendtext);
		setTextSize(myButton);
		sendText.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				// Send a message using content of the edit text widget
				TextView view = (TextView) findViewById(R.id.edit_text_out);
				msgToSend = view.getText().toString();

				// encryption part before send
				if (GS.gI().getValue().getBoolean("use_enc", false))
				{
					StringBuilder finalIntext = new StringBuilder();
					for (int i = 0; i < msgToSend.length(); i += encryptor.getEncryptorChunkSize())
					{
						int j = 0;
						if (i + encryptor.getEncryptorChunkSize() > msgToSend.length()) j = msgToSend.length();
						else
							j = i + encryptor.getEncryptorChunkSize();

						String encryptedChunk = encryptor.encrypt(msgToSend.substring(i, j));
						encryptedChunk = encryptedChunk.replace("\n", "¢");
						finalIntext.append(encryptedChunk);
					}
					msgToSend = finalIntext.toString();
					msgToReturn = msgToSend;
				}

				// STAR: SENDER
				if (!GS.gI().isReceivingForm())
				{
					Modem.setListener(SCVA.this);
					processor.sendMessage(msgToSend + "§");
					// Processor.TX_Text += (msgToSend + "§");
					// // Processor.q.send_txrsid_command("ON");
					// Modem.txData("", "", msgToSend + "§", 0, 0, false, "");
					// SCVA.utils.writeToFile("S: " + view.getText().toString(), SCVA.myContext);
					msgToSend = "";
				}
				else
				{
					bottomToastText(getString(R.string.txt_NotInMiddleOfMessage));
				}
				Utils.println(msgToSend);
				// Clear the text field
				view.setText("");
				savedTextMessage = "";
			}
		});
	}

	private void fileNameDialog(String filePath, String templateFileName, String dataBuffer)
	{
		final String savedFilePath = filePath;
		final String savedDataBuffer = dataBuffer;

		AlertDialog.Builder myAlertBuilder = new AlertDialog.Builder(this);
		LayoutInflater myInflater = LayoutInflater.from(this);
		final View fileNameDialogView = myInflater.inflate(R.layout.filenamedialog, null);
		myAlertBuilder.setView(fileNameDialogView).setCancelable(true).setNegativeButton(getString(R.string.txt_Cancel), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				// Hide the keyboard since we handle it manually
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(fileNameDialogView.getWindowToken(), 0);
				dialog.cancel();
			}
		}).setPositiveButton(getString(R.string.txt_Save), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				TextView view = (TextView) fileNameDialogView.findViewById(R.id.edit_text_input);
				String newFileName = view.getText().toString().trim();
				if (newFileName.length() == 0)
				{
					// Hide the keyboard since we handle it
					// manually
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(fileNameDialogView.getWindowToken(), 0);
					dialog.cancel();
					middleToastText(getString(R.string.txt_FileNameNotBlank));
				}
				else
				{
					// Hide the keyboard since we handle it
					// manually
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(fileNameDialogView.getWindowToken(), 0);
					// Do something with the file name
					Message.saveMessageAsFile(savedFilePath, newFileName, savedDataBuffer);
					// returnFromFormView();
				}
			}
		});

		AlertDialog myFileNameAlert = myAlertBuilder.create();
		EditText view = (EditText) fileNameDialogView.findViewById(R.id.edit_text_input);
		view.setText(templateFileName);
		myFileNameAlert.setTitle(getString(R.string.txt_EnterFileName));
		myFileNameAlert.show();

	}

	static final String injectJSforImages = "<script>\n" + "	   \n" + "	//Run on page load (right after loading - see the body declaration above) \n"
			+ "	//  Copies the base64 encoded data from the TEXTAREA element to the IMG element of \n" + "	//  the same name if it exists\n"
			+ "	function presetImageFields() {\n" + "		var x = document.forms[0];\n" + "		var i = x.length;\n" + "		var myValue = \"\";\n" + "		\n" + "		\n"
			+ "		var lookingForDisabled = true;//Disabling file input. Temporary solution.\n" + "\n" + "		while (i--) {\n" + "			\n"
			+ "			//Disabling file input. Temporary solution until we disable the file input element by program.\n"
			+ "			//Look for first input field (most likely field in the document) to determine if it is readonly or not\n"
			+ "			if (lookingForDisabled && x.elements[i].type == 'text') {\n" + "				//Found the first one, use it's readOnly flag\n"
			+ "				lookingForDisabled = false; //Stop looking, skip this section\n" + "				//Disabled? then disable all file type INPUTs as well\n"
			+ "				if (x.elements[i].readOnly) {\n" + "					var k = x.length;\n" + "					while (k--) {\n"
			+ "						if (x.elements[k].type == \"file\") {\n" + "							x.elements[k].disabled = true;\n" + "						}\n"
			+ "					}\n" + "				}\n" + "			}\n" + "			\n" + "			//Preset the image with the textarea value (encoded picture)\n"
			+ "			if (x.elements[i].type == 'textarea') {\n" + "				//Does the data of the TEXTAREA look consistent with an image data?\n"
			+ "				myValue = '' + x.elements[i].value.substring(0, 10);\n" + "				if (myValue == 'data:image') {\n"
			+ "					//Try to find an image element (<IMG... ) of the same name\n"
			+ "					//All <IMG elements need to be made part of the \"image\" class\n" + "					var y = document.getElementsByClassName('image');\n"
			+ "					var j = y.length;\n" + "					while (j--) {\n" + "						//If same NAME for both fields, then copy\n"
			+ "						if (y[j].name == x[i].name) {\n"
			+ "							//Copy Textarea containing Base64 compressed image data into the image preview element\n"
			+ "							y[j].src = x[i].value;\n" + "						}\n" + "					}\n" + "				}\n" + "			}\n"
			+ "		}\n" + "	}\n" + " 	\n" + "</script>\n" + "";
	String jsSendNewFormDataInject;
	String jsSendDraftFormDataInject;

	// Must match the declaration in flmsg.h:
	// enum hdr_reason {NEW = 1, CHANGED = 2, FROM = 3};
	public static final int header_reason_NEW = 1;
	public static final int header_reason_CHANGED = 2;
	public static final int header_reason_FROM = 3;

	// Allows Javascript to call Android methods for submitting form data,
	// perforn form specific actions, pre-fill date and time fields, etc...
	// public class JavascriptAccess
	// {
	// Context mContext;
	// /**
	// * Instantiate the interface and set the context
	// */
	// JavascriptAccess(Context c)
	// {
	// mContext = c;
	// }
	//
	// // Extracts the GPS coordinates of an attached picture (if available)
	// // Returns the full image path if one is selected, or an empty string
	// @JavascriptInterface
	// public String getPosFromImage(String imageFieldValue)
	// {
	// String latStr = "";
	// String lonStr = "";
	// String fullFileName = "";
	//
	// try
	// {
	// if (imageFieldValue.startsWith("file://"))
	// {// Has just been attached for the first time
	// fullFileName = imageFieldValue.substring(7);
	// }
	// else if (imageFieldValue.startsWith("data:image"))
	// { // Was saved before and is being re-edited
	// // Save image data into a file. Android libraries can only read Exif tags
	// from a
	// // file.
	// fullFileName = Constants.HomePath + Constants.Dirprefix + Constants.DirTemp +
	// Constants.Separator + Constants.tempPicfn;
	// File fileToSave = new File(fullFileName);
	// if (fileToSave.exists())
	// {
	// fileToSave.delete();
	// }
	// FileOutputStream fileOutputStrm = new FileOutputStream(fullFileName);
	// String encodedImage = imageFieldValue.substring(imageFieldValue.indexOf(",")
	// + 1);
	// fileOutputStrm.write(Base64.decode(encodedImage, Base64.NO_WRAP));
	// fileOutputStrm.close();
	// }
	// // Now read the Exif tags of the attached picture (embedded or still on
	// sdcard)
	// ExifInterface exifInterface = new ExifInterface(fullFileName);
	// String dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME);
	// String orient = exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION);
	// latStr = exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
	// String latref =
	// exifInterface.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
	// lonStr = exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
	// String lonref =
	// exifInterface.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
	// float[] latLonFloat = new float[2];
	// boolean hasLatLon = exifInterface.getLatLong(latLonFloat);
	// if (hasLatLon)
	// {
	// latStr = Float.toString(latLonFloat[0]);
	// lonStr = Float.toString(latLonFloat[1]);
	// }
	// }
	// catch (IOException e)
	// {
	// loggingClass.writelog("Error saving temporary PIC file or Reading Exif tags
	// in Picture", e, true);
	// }
	// if (latStr != null && lonStr != null)
	// {
	// return latStr + "|" + lonStr;
	// }
	// else
	// {
	// return "|";
	// }
	// }
	//
	// // Escape the string to replace special (mostly control) characters by their
	// // text equivalent
	// @JavascriptInterface
	// public String escape(String fieldToEscape)
	// {
	// return Message.escape(fieldToEscape);
	// }
	//
	// // Bring-up the file dialog to pick a PNG picture
	// // Returns the full image path if one is selected, or an empty string
	// @JavascriptInterface
	// public void pickImage(String imgFieldToSet)
	// {
	// _imgFieldToUpdate = imgFieldToSet;
	// openImageIntent(); // Camera, Gallery, File manager
	// }
	//
	// // Edit CSV data in an external app (with auto update of edited text via
	// // listener for
	// // intent results). Html form MUST USE the :mg: field to display/enter CSV
	// data.
	// @JavascriptInterface
	// public void editInSpreadsheet(String allFields)
	// {
	// // Save data to file
	// // Escaped Textarea field, no double quotes int mgFieldPos =
	// // allFields.indexOf(":mg:,\"");
	// int mgFieldPos = allFields.indexOf(":mg:,");
	// if (mgFieldPos == -1)
	// {
	// csvData = ""; // Blank form, nothing entered yet
	// }
	// else
	// {
	// // Extract the text of the ":mg:" field
	// csvData = allFields.substring(mgFieldPos + 5);
	// // Remove the double quotes surrounding the data
	// // Escaped Textarea field, no double quotes if (csvData.length() > 1) csvData
	// =
	// // csvData.substring(1, csvData.length() - 2);
	// // But need to "unescape"
	// csvData = Message.unescape(csvData);
	// }
	// // Prepare temporary csv file
	// String filePath = Constants.HomePath + Constants.Dirprefix +
	// Constants.DirTemp + Constants.Separator;
	// FileWriter out = null;
	// File fileToSave = new File(filePath + Constants.tempCSVfn);
	// try
	// {
	// if (fileToSave.exists())
	// {
	// fileToSave.delete();
	// }
	// out = new FileWriter(fileToSave, true);
	// out.write(csvData);
	// out.close();
	// // Create an intent for Android to satisfy with whatever chosen CSV editing
	// app
	// // (user selected)
	// Intent editCsvIntent = new Intent(Intent.ACTION_EDIT);
	// editCsvIntent.setDataAndType(Uri.fromFile(fileToSave), "text/csv");
	// startActivityForResult(editCsvIntent, EDIT_CSV_RESULT);
	// }
	// catch (IOException e)
	// {
	// loggingClass.writelog("Error saving temporary CSV file", e, true);
	// }
	//
	// }
	//
	// // Shows the dialog for selecting the ARL messages (with parameters if
	// // required)
	// @JavascriptInterface
	// public void arlmessagesdialog(String msgField)
	// {
	// showArlMsgsDialog(msgField);
	// }
	//
	// // Shows the dialog for selecting the ARL messages (with parameters if
	// // required)
	// @JavascriptInterface
	// public void arlhxcodesdialog(String msgField)
	// {
	// showArlHxDialog(msgField);
	// }
	//
	// // Calls ARL radiogram formatting function
	// // Updates both the check field and the message itself once formatted
	// @JavascriptInterface
	// public void arlmsgcheck(String msgFieldName, String msgFieldContent, String
	// checkFieldName, String stdFormatStr)
	// {
	// boolean stdFormat = stdFormatStr.toUpperCase(Locale.US).equals("ON");
	// checkField = Message.cbrgcheck(msgFieldContent, stdFormat);
	// checkFieldToUpdate = checkFieldName;
	// msgFieldToUpdate = msgFieldName;
	// formattedMsg = Message.gettxtrgmsg();
	// SCVA.this.runOnUiThread(new Runnable()
	// {
	// public void run()
	// {
	// mWebView.loadUrl("javascript:setField('" + checkFieldToUpdate + "','" +
	// checkField + "')");
	// mWebView.loadUrl("javascript:setField('" + msgFieldToUpdate + "','" +
	// formattedMsg + "')");
	// }
	// });
	// }
	//
	// // Returns the current date as per the format preferences
	// @JavascriptInterface
	// public String dateprefill()
	// {
	// return Message.dateStamp();
	// }
	//
	// // Returns the current time as per the format preferences
	// @JavascriptInterface
	// public String timeprefill()
	// {
	// return Message.timeStamp();
	// }
	//
	// // Returns the current date and time combination as per the format
	// // preferences
	// @JavascriptInterface
	// public String datetimeprefill()
	// {
	// String ms = Message.dateTimeStamp();
	// return ms;
	// }
	//
	// // Processes the received data from the form.
	// // The string format received is the same as the content of the :mg:
	// // field of the custom forms
	// @JavascriptInterface
	// public void submitFormData(String formData, String jsAction, String jsNewDoc)
	// {
	// String dataBuffer;
	// String customField;
	// String extension;
	// String fileName;
	//
	// // Update the JNI Env stored for retrieving preferences in Java
	// Message.saveEnv();
	// // debug: Processor.PostToTerminal("\nReceived Data from Form: <" + formData
	// +
	// // ">\n");
	// // Define file name
	// if (jsNewDoc.equalsIgnoreCase("New"))
	// {
	// fileName = Message.namedFile();
	// extension = Message.getExtension(Message.formName, false);// Message
	// // extension
	// if (!fileName.equals(""))
	// {
	// fileName += extension;
	// }
	// else
	// fileName = "new-" + config.getPreferenceS("SERNBR", "1") + extension;
	// // Remove spaces from file name
	// fileName = fileName.replaceAll(" ", "_");
	// }
	// else
	// {
	// fileName = mFileName; // Updated before we entered the web editing step
	// // Just in case, remove spaces from file name
	// fileName = fileName.replaceAll(" ", "_");
	// extension = Message.getExtension(Message.formName, false);// Message
	// extension
	// }
	// // Save data as a message
	// if (Message.isCustomForm(extension))
	// {
	// /*
	// * Not used as we store the encoded image in a textarea field of the same name
	// * //First replace the IMG source attibutes with the base64 encoded image if
	// any
	// * Pattern psc = Pattern.compile("((^_img.*),file://(.+))",
	// Pattern.MULTILINE);
	// * Matcher msc = psc.matcher(formData); boolean keepLooking = true; for (int
	// * start = 0; keepLooking;) { keepLooking = msc.find(start); if (keepLooking)
	// {
	// * String imageLine = msc.group(1); String imgField = msc.group(2); String
	// * imageFileName = msc.group(3); String encodedImageLine = ""; try { File
	// * imgFile = new File(imageFileName); //Read the compressed image file long
	// * fileSize = imgFile.length(); //Read the full file in a byte buffer byte[]
	// * rawPictureBuffer = new byte[(int) fileSize]; FileInputStream fileIs = new
	// * FileInputStream(imgFile); fileIs.read(rawPictureBuffer); //Encode in Base64
	// * to make it text compatible encodedImageLine = imgField +
	// * ",data:image/png;base64," + Base64.encodeToString(rawPictureBuffer,
	// * Base64.NO_WRAP) + "\n"; //Replace field + filename, with field + base64
	// * encoded file content formData = formData.replace(imageLine,
	// * encodedImageLine); } catch (Exception ex) {
	// * loggingClass.writelog("Failed to read and encode picture file" +
	// * ex.getMessage(), null, true); } start = msc.end(); } }
	// */
	// // Add the first line of the custom field (e.g. CUSTOM_FORM,ICS218.html)
	// customField = "CUSTOM_FORM," + Message.formName + "\n" + formData;
	// // Build message buffer
	// dataBuffer = Message.createCustomBuffer("", header_reason_NEW, customField);
	// }
	// else
	// { // Hard-Coded forms. E.g. <radiogram>
	// // Extract the form name as the character sequence before the first dot.
	// // Always in lower case
	// String myHCForm = Message.formName.substring(0,
	// Message.formName.indexOf(".")).toLowerCase(Locale.US);
	// // Build message header first
	// dataBuffer = Message.createHardCodedHeader("", header_reason_NEW, myHCForm);
	// // Now build the message body from the form data, one field at a time
	// Pattern psc = Pattern.compile(
	// // Made compatible with FLMSG 2.0.12. All fields are escaped im javaScript
	// then
	// // unescaped here
	// // "(^:.+:),\\\"((?:(?:.*[^\\\"]\\n)*)[^\\\"]*)\\\"|(^:.+:),(.*)",
	// "(^:.+:),(.*)", Pattern.MULTILINE);
	// Matcher msc = psc.matcher(formData);
	// boolean keepLooking = true;
	// String element;
	// String value;
	// for (int start = 0; keepLooking;)
	// {
	// keepLooking = msc.find(start);
	// if (keepLooking)
	// {
	// // String group0 = msc.group(0);
	// element = msc.group(1);
	// value = msc.group(2);
	// // Un-escape
	// value = Message.unescape(value);
	// dataBuffer += element + Integer.toString(value.length()) + " " + value +
	// "\n";
	// start = msc.end();
	// }
	// }
	// // Now check for specific Hard Coded Forms processing BEFORE
	// // storing the buffer in a file
	// if (myHCForm.equals("radiogram"))
	// {
	// // Reformat the :msg: field and Update the :ck: (check)
	// // field
	// String msgFieldContent = Message.getHcfFieldContent(dataBuffer, "msg");
	// // Check if we have checked the "STD Format" button
	// boolean stdFormat = (dataBuffer.indexOf(":std:2 ON") != -1);
	// checkField = Message.cbrgcheck(msgFieldContent, stdFormat);
	// // replace in buffer
	// dataBuffer = Message.setHcfFieldContent(dataBuffer, "ck", checkField);
	// formattedMsg = Message.gettxtrgmsg();
	// // Remove escaped characters
	// formattedMsg = Message.unescape(formattedMsg);
	// dataBuffer = Message.setHcfFieldContent(dataBuffer, "msg", formattedMsg);
	// // Reformat the two boolean fields
	// if (dataBuffer.indexOf(":std:2 ON") != -1) dataBuffer =
	// dataBuffer.replace(":std:2 ON", ":std:1 T");
	// if (dataBuffer.indexOf(":svc:2 ON") != -1) dataBuffer =
	// dataBuffer.replace(":svc:2 ON", ":svc:1 T");
	// }
	// else if (myHCForm.equals("ics206"))
	// {
	// // Reformat the boolean variables
	// for (int i = 0; i < 5; i++)
	// {
	// if (dataBuffer.indexOf(":mpar" + Integer.toString(i) + ":2 ON") != -1)
	// dataBuffer = dataBuffer.replace(":mpar" + Integer.toString(i) + ":2 ON",
	// ":mpar" + Integer.toString(i) + ": T");
	// if (dataBuffer.indexOf(":tpara" + Integer.toString(i) + ":2 ON") != -1)
	// dataBuffer = dataBuffer.replace(":tpara" + Integer.toString(i) + ":2 ON",
	// ":tpara" + Integer.toString(i) + ": T");
	// if (dataBuffer.indexOf(":apara" + Integer.toString(i) + ":2 ON") != -1)
	// dataBuffer = dataBuffer.replace(":apara" + Integer.toString(i) + ":2 ON",
	// ":apara" + Integer.toString(i) + ": T");
	// if (dataBuffer.indexOf(":hheli" + Integer.toString(i) + ":2 ON") != -1)
	// dataBuffer = dataBuffer.replace(":hheli" + Integer.toString(i) + ":2 ON",
	// ":hheli" + Integer.toString(i) + ": T");
	// if (dataBuffer.indexOf(":hburn" + Integer.toString(i) + ":2 ON") != -1)
	// dataBuffer = dataBuffer.replace(":hburn" + Integer.toString(i) + ":2 ON",
	// ":hburn" + Integer.toString(i) + ": T");
	// }
	// }
	// else if (myHCForm.equals("hics213"))
	// {
	// // Reformat the boolean variables
	// if (dataBuffer.indexOf(":5.a:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":5.a:7 CHECKED", ":5a:1 X");
	// if (dataBuffer.indexOf(":5.b:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":5.b:7 CHECKED", ":5b:1 X");
	// if (dataBuffer.indexOf(":5.c:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":5.c:7 CHECKED", ":5c:1 X");
	// if (dataBuffer.indexOf(":6.a:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":6.a:7 CHECKED", ":6a:1 X");
	// if (dataBuffer.indexOf(":6.b:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":6.b:7 CHECKED", ":6b:1 X");
	// if (dataBuffer.indexOf(":7.a:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":7.a:7 CHECKED", ":7a:1 X");
	// if (dataBuffer.indexOf(":7.b:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":7.b:7 CHECKED", ":7b:1 X");
	// if (dataBuffer.indexOf(":7.c:7 CHECKED") != -1) dataBuffer =
	// dataBuffer.replace(":7.c:7 CHECKED", ":7c:1 X");
	// }
	// }
	// // Then save the file
	// String filePath = "";
	// if (jsAction.equals("Queue"))
	// {
	// filePath = Constants.HomePath + Constants.Dirprefix + Constants.DirOutbox +
	// Constants.Separator;
	// if (jsNewDoc.equals("New"))
	// {
	// Message.saveMessageAsFile(filePath, fileName, dataBuffer);
	// }
	// else
	// {
	// Message.deleteFile(Constants.DirDrafts, fileName, false);
	// Message.saveDataStringAsFile(filePath, fileName, dataBuffer);
	// }
	// // Return to list
	// returnFromFormView();
	// // Android to-do: start TX of outbox in order of creation
	// }
	// else if (jsAction.equals("Discard"))
	// {
	// // Return to list
	// returnFromFormView();
	// }
	// else if (jsAction.equals("Templates"))
	// {
	// filePath = Constants.HomePath + Constants.Dirprefix + Constants.DirTemplates
	// + Constants.Separator;
	// // Alert with new file name
	// if (!jsNewDoc.equals("New"))
	// {
	// Message.deleteFile(Constants.DirDrafts, fileName, false);
	// }
	// fileNameDialog(filePath, fileName, dataBuffer);
	// }
	// else
	// { // We have a simple directory path: use it (Outbox, Drafts etc..)
	// filePath = Constants.HomePath + Constants.Dirprefix + jsAction +
	// Constants.Separator;
	// if (jsNewDoc.equals("New"))
	// {
	// Message.saveMessageAsFile(filePath, fileName, dataBuffer);
	// }
	// else
	// {
	// Message.deleteFile(Constants.DirDrafts, fileName, false);
	// Message.saveDataStringAsFile(filePath, fileName, dataBuffer);
	// }
	// // Return to list
	// returnFromFormView();
	// }
	// }
	// }

	// Background task to format for displaying only. Result is updated webview.
	// class backgroundFormatForDisplay extends AsyncTask<Void, Integer, String>
	// {
	// String workingDir = "";
	//
	// // ProgressDialog progress;
	//
	// public backgroundFormatForDisplay(String mDir)
	// {
	// workingDir = mDir;
	// // progress = new ProgressDialog(SCVA.myContext);
	// }
	//
	// @Override
	// protected void onPreExecute()
	// {
	// // progress.setTitle("Formatting...");
	// // progress.setMessage("Wait while Formatting...");
	// // progress.show();
	// }
	//
	// @Override
	// protected String doInBackground(Void... arg0)
	// {
	//
	// mDisplayForm = Message.formatForDisplay(workingDir, mFileName, ".html");
	// return "";
	// }
	//
	// protected void onProgressUpdate(Integer... a)
	// {
	// }
	//
	// protected void onPostExecute(String result)
	// {
	// // progress.dismiss();
	// mWebView.loadDataWithBaseURL("", mDisplayForm, "text/html", "UTF-8", "");
	// // Special processing for html forms received over radio
	// // Replace copy to drafts with "Install Form"
	// if (currentView == INBOXVIEW && Message.formName.startsWith("html_form."))
	// {
	// // Change Copy to Drafts button
	// myButton = (Button) findViewById(R.id.button_cpdrafts);
	// myButton.setText("Inst. Form");
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Prompt to confirm that we will overwrite forms in the DisplayForms and
	// // EntryForms folders
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage("This will install the received form in the
	// DisplayForms and EntryForms folders, OVERWRITTING any existing forms.
	// Proceed?");
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// // Copy the extracted form into both folders
	// String filePath = Constants.HomePath + Constants.Dirprefix +
	// Constants.DirEntryForms + Constants.Separator;
	// Message.saveDataStringAsFile(filePath, mFileName, mDisplayForm);
	// filePath = Constants.HomePath + Constants.Dirprefix +
	// Constants.DirDisplayForms + Constants.Separator;
	// Message.saveDataStringAsFile(filePath, mFileName, mDisplayForm);
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	//
	// }
	// // Debug
	// // Processor.PostToTerminal("\nForm:\n" + myDisplayForm +"\n");
	// }
	// }

	// Background task to format for editing from a new form (new message). Result
	// is updated webview.
	// class backgroundNewFormDisplay extends AsyncTask<Void, Integer, String>
	// {
	//
	// // ProgressDialog progress;
	//
	// public backgroundNewFormDisplay()
	// {
	// // progress = new ProgressDialog(SCVA.myContext);
	// }
	//
	// @Override
	// protected void onPreExecute()
	// {
	// // progress.setTitle("Formatting...");
	// // progress.setMessage("Wait while Formatting...");
	// // progress.show();
	// }
	//
	// @Override
	// protected String doInBackground(Void... arg0)
	// {
	// mDisplayForm = "";
	// try
	// {
	// // Store the form name for later (when we post the form for
	// // example)
	// Message.formName = mFileName;
	// // Read the form into a string
	// File f = new File(Constants.HomePath + Constants.Dirprefix +
	// Constants.DirEntryForms + Constants.Separator + mFileName);
	// FileInputStream fileIS = new FileInputStream(f);
	// BufferedReader buf = new BufferedReader(new InputStreamReader(fileIS));
	// String readString = new String();
	// while ((readString = buf.readLine()) != null)
	// {
	// mDisplayForm += readString + "\n";
	// }
	// }
	// catch (FileNotFoundException e)
	// {
	// loggingClass.writelog("File Not Found error in 'backgroundNewFormDisplay' " +
	// e.getMessage(), null, true);
	// }
	// catch (IOException e)
	// {
	// loggingClass.writelog("IO Exception error in 'backgroundNewFormDisplay' " +
	// e.getMessage(), null, true);
	// }
	// // Inject the JavaScript into the form to get the data when the user
	// // selects the Submit button
	// if (mDisplayForm.contains("</FORM>"))
	// {
	// mDisplayForm = mDisplayForm.replaceFirst("</FORM>", "</FORM>" +
	// jsSendNewFormDataInject);
	// }
	// else if (mDisplayForm.contains("</form>"))
	// {
	// mDisplayForm = mDisplayForm.replaceFirst("</form>", "</form>" +
	// jsSendNewFormDataInject);
	// }
	// return "";
	// }
	//
	// protected void onProgressUpdate(Integer... a)
	// {
	// }
	//
	// protected void onPostExecute(String result)
	// {
	// // progress.dismiss();
	// String filePath = Constants.HomePath + Constants.Dirprefix +
	// Constants.DirImages + Constants.Separator;
	// mWebView.loadDataWithBaseURL("", mDisplayForm, "text/html", "UTF-8", null);
	// }
	// }

	// Background task to format for editing. Result is updated webview.
	// class backgroundFormatForEditing extends AsyncTask<Void, Integer, String>
	// {
	//
	// String workingDir = "";
	//
	// String injectJS = "";
	//
	// // ProgressDialog progress;
	//
	// public backgroundFormatForEditing(String mDir, String mInjectString)
	// {
	// workingDir = mDir;
	// injectJS = mInjectString;
	// // progress = new ProgressDialog(SCVA.myContext);
	// }
	//
	// @Override
	// protected void onPreExecute()
	// {
	// // progress.setTitle("Formatting...");
	// // progress.setMessage("Wait while Formatting...");
	// // progress.show();
	// }
	//
	// @Override
	// protected String doInBackground(Void... arg0)
	// {
	// // Format for display and sharing
	// // mDisplayForm = Message.formatForEditing(Processor.DirDrafts,
	// // mFileName);
	// mDisplayForm = Message.formatForEditing(workingDir, mFileName);
	// // Inject the JavaScript into the form to get the data when the user
	// // selects the Submit button
	// if (mDisplayForm.contains("</FORM>"))
	// {
	// mDisplayForm = mDisplayForm.replaceFirst("</FORM>", "</FORM>" + injectJS);
	// }
	// else if (mDisplayForm.contains("</form>"))
	// {
	// mDisplayForm = mDisplayForm.replaceFirst("</form>", "</form>" + injectJS);
	// }
	// return "";
	// }
	//
	// protected void onProgressUpdate(Integer... a)
	// {
	// }
	//
	// protected void onPostExecute(String result)
	// {
	// // progress.dismiss();
	// mWebView.loadDataWithBaseURL("", mDisplayForm, "text/html", "UTF-8", "");
	// // Debug
	// // Processor.PostToTerminal("\nForm:\n" + mDisplayForm +"\n");
	// }
	// }

	// Popup Windows Dismiss listener
	OnDismissListener myDismissListerner = new OnDismissListener()
	{
		public void onDismiss()
		{
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					// Re-display the lists in case we deleted or moved an item
					// displayMessages(BOTTOM, currentView);
					// Update the title if we changed the mode (in Outbox view)
					SCVA.mHandler.post(SCVA.updateTitle);
				}
			});
		}
	};

	// public void returnFromFormView()
	// {
	// runOnUiThread(new Runnable()
	// {
	// public void run()
	// {
	// inFormDisplay = false;
	// // Return to lists display
	// displayMessages(BOTTOM, currentView);
	// // Update the title if we changed the mode (in Outbox view)
	// SCVA.mHandler.post(SCVA.updateTitle);
	// }
	// });
	// }

	// Specific to ARL Radiogram: brings up the ARL pre-defined messages dialog
	// private void showArlMsgsDialog(String msgField)
	// {
	// final String fieldToUpdate = msgField;
	// AlertDialog.Builder myAlertDialogBuilder = new
	// AlertDialog.Builder(SCVA.this);
	// LayoutInflater myLi = LayoutInflater.from(SCVA.this);
	// final View arlMsgsView = myLi.inflate(R.layout.arlmsgsdialog, null);
	// myAlertDialogBuilder.setView(arlMsgsView);
	// myAlertDialogBuilder.setMessage(getString(R.string.txt_SelectAnARLmessage));
	// myAlertDialogBuilder.setCancelable(false);
	// myAlertDialogBuilder.setPositiveButton(getString(R.string.bt_AddMessage), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// // Extract the message from the returned value
	// arlMsg = arlMsg.substring(0, arlMsg.indexOf(":"));
	// EditText parameter1 = (EditText) myAlertDialog.findViewById(R.id.parameter1);
	// EditText parameter2 = (EditText) myAlertDialog.findViewById(R.id.parameter2);
	// EditText parameter3 = (EditText) myAlertDialog.findViewById(R.id.parameter3);
	// EditText parameter4 = (EditText) myAlertDialog.findViewById(R.id.parameter4);
	// // Enable the right number of parameter fields
	// String arlMsgParameters = "";
	// if (paramCount == 1)
	// {
	// arlMsgParameters = " " + parameter1.getText().toString();
	// }
	// else if (paramCount == 2)
	// {
	// arlMsgParameters = " " + parameter1.getText().toString() + " " +
	// parameter2.getText().toString();
	// }
	// else if (paramCount == 3)
	// {
	// arlMsgParameters = " " + parameter1.getText().toString() + " " +
	// parameter2.getText().toString() + " " + parameter3.getText().toString();
	// }
	// else if (paramCount == 4)
	// {
	// arlMsgParameters = " " + parameter1.getText().toString() + " " +
	// parameter2.getText().toString() + " " + parameter3.getText().toString() + " "
	// + parameter4.getText().toString();
	// }
	// if (paramCount > 0)
	// {
	// arlMsg += arlMsgParameters;
	// }
	// // Call a Javascript on the webview to add to the arl text field
	// SCVA.this.runOnUiThread(new Runnable()
	// {
	// public void run()
	// {
	// mWebView.loadUrl("javascript:appendToField('" + fieldToUpdate + "',' ARL " +
	// arlMsg + "')");
	// }
	// });
	// dialog.cancel();
	// }
	// });
	// myAlertDialogBuilder.setNegativeButton(getString(R.string.txt_Cancel), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// // Need to hide the keyboard?
	// }
	// });
	//
	// myAlertDialog = myAlertDialogBuilder.create();
	// myAlertDialog.show();
	// // Populate the spinner list of messages
	// Spinner spinner = (Spinner) myAlertDialog.findViewById(R.id.arlmsgsspinner);
	// ArrayList<String> arlMsgsList = new ArrayList<String>();
	// String[] arlArrayText = Message.getArlMsgTextList();
	// for (int ii = 0; ii < arlArrayText.length; ii++)
	// {
	// if (!arlArrayText[ii].equals(""))
	// {
	// arlMsgsList.add(arlArrayText[ii]);
	// }
	// }
	//
	// ArrayAdapter<String> adp = new ArrayAdapter<String>(SCVA.this,
	// R.layout.multilinespinnerdropdownitem, arlMsgsList);
	// spinner.setAdapter(adp);
	// spinner.setVisibility(View.VISIBLE);
	// // Set listener Called when the item is selected in spinner
	// spinner.setOnItemSelectedListener(new OnItemSelectedListener()
	// {
	// public void onItemSelected(AdapterView<?> parent, View view, int position,
	// long arg3)
	// {
	// String thisArlMsg = parent.getItemAtPosition(position).toString();
	// // Save value for "Add Msg" Button use
	// arlMsg = thisArlMsg;
	// // Count the number of parameters
	// paramCount = 0;
	// while (thisArlMsg.length() > 0 && thisArlMsg.contains("____"))
	// {
	// paramCount++;
	// thisArlMsg = thisArlMsg.substring(thisArlMsg.indexOf("____") + 4);
	// }
	// EditText parameter1 = (EditText) myAlertDialog.findViewById(R.id.parameter1);
	// EditText parameter2 = (EditText) myAlertDialog.findViewById(R.id.parameter2);
	// EditText parameter3 = (EditText) myAlertDialog.findViewById(R.id.parameter3);
	// EditText parameter4 = (EditText) myAlertDialog.findViewById(R.id.parameter4);
	// // Enable the right number of parameter fields
	// if (paramCount == 0)
	// {
	// parameter1.setEnabled(false);
	// parameter2.setEnabled(false);
	// parameter3.setEnabled(false);
	// parameter4.setEnabled(false);
	// }
	// else if (paramCount == 1)
	// {
	// parameter1.setEnabled(true);
	// parameter1.requestFocus();
	// parameter2.setEnabled(false);
	// parameter3.setEnabled(false);
	// parameter4.setEnabled(false);
	// }
	// else if (paramCount == 2)
	// {
	// parameter1.setEnabled(true);
	// parameter1.requestFocus();
	// parameter2.setEnabled(true);
	// parameter3.setEnabled(false);
	// parameter4.setEnabled(false);
	// }
	// else if (paramCount == 3)
	// {
	// parameter1.setEnabled(true);
	// parameter1.requestFocus();
	// parameter2.setEnabled(true);
	// parameter3.setEnabled(true);
	// parameter4.setEnabled(false);
	// }
	// else if (paramCount == 4)
	// {
	// parameter1.setEnabled(true);
	// parameter1.requestFocus();
	// parameter2.setEnabled(true);
	// parameter3.setEnabled(true);
	// parameter4.setEnabled(true);
	// }
	// }
	//
	// public void onNothingSelected(AdapterView<?> arg0)
	// {
	// // TODO Auto-generated method stub
	// }
	// });
	//
	// }

	// Specific to ARL Radiogram: brings up the ARL delivery (HX) coded messages
	// dialog
	private void showArlHxDialog(String hxField)
	{
		final String fieldToUpdate = hxField;
		AlertDialog.Builder myAlertDialogBuilder = new AlertDialog.Builder(SCVA.this);
		LayoutInflater myLi = LayoutInflater.from(SCVA.this);
		final View arlMsgsView = myLi.inflate(R.layout.arlhxdialog, null);
		myAlertDialogBuilder.setView(arlMsgsView);
		myAlertDialogBuilder.setMessage(getString(R.string.txt_SelectARLDeliveryCodes));
		myAlertDialogBuilder.setCancelable(false);
		myAlertDialogBuilder.setPositiveButton(getString(R.string.bt_AddToHXField), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				final EditText hxField = (EditText) myAlertDialog.findViewById(R.id.hxfield);
				// Call a Javascript on the webview to add to the arl
				// text field
				SCVA.this.runOnUiThread(new Runnable()
				{
					public void run()
					{
						mWebView.loadUrl("javascript:appendToField('" + fieldToUpdate + "','" + hxField.getText().toString() + "')");
					}
				});
				dialog.cancel();
			}
		});
		myAlertDialogBuilder.setNegativeButton(getString(R.string.txt_Cancel), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
				// Need to hide the keyboard?
			}
		});

		myAlertDialog = myAlertDialogBuilder.create();
		myAlertDialog.show();
		// Populate the spinner list of messages
		Spinner spinner = (Spinner) myAlertDialog.findViewById(R.id.arlhxspinner);
		ArrayList<String> arlMsgsList = new ArrayList<String>();
		String[] arlArrayText = Message.getArlHxTextList();
		for (int ii = 0; ii < arlArrayText.length; ii++)
		{
			if (!arlArrayText[ii].equals(""))
			{
				arlMsgsList.add(arlArrayText[ii]);
			}
		}

		ArrayAdapter<String> adp = new ArrayAdapter<String>(SCVA.this, R.layout.multilinespinnerdropdownitem, arlMsgsList);
		spinner.setAdapter(adp);
		spinner.setVisibility(View.VISIBLE);
		// Set listener Called when the item is selected in spinner
		spinner.setOnItemSelectedListener(new OnItemSelectedListener()
		{
			public void onItemSelected(AdapterView<?> parent, View view, int position, long arg3)
			{
				String thisArlMsg = parent.getItemAtPosition(position).toString();
				// Save value for "Add Msg" Button use
				arlMsg = thisArlMsg.substring(0, thisArlMsg.indexOf(":"));
			}

			public void onNothingSelected(AdapterView<?> arg0)
			{
				// TODO Auto-generated method stub
			}
		});
		// Add HX code to HX field button
		myButton = (Button) myAlertDialog.findViewById(R.id.button_addhxcode);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				// Format for display and sharing
				EditText hxField = (EditText) myAlertDialog.findViewById(R.id.hxfield);
				if (hxField.getText().toString() != "")
				{
					hxField.setText(hxField.getText().toString() + " " + arlMsg);
				}
			}
		});

	}

	// Display one of the Messages layouts and associate it's buttons
	// private void displayMessages(int screenAnimation, int msgScreen)
	// {
	// // Ensure we reset the swipe action
	// SCVA.inFormDisplay = false;
	//
	// setContentView(R.layout.messages);
	// screenAnimation((ViewGroup) findViewById(R.id.mailscreen), screenAnimation);
	//
	// // Initialize the adapter for the exchange thread with server
	// msgArrayAdapter = new ArrayAdapter<String>(this, R.layout.maillistview);
	// msgListView = (ListView) findViewById(R.id.maillist);
	// msgListView.setAdapter(msgArrayAdapter);
	// msgListView.setDividerHeight(8);
	//
	// currentView = msgScreen;
	// lastMsgScreen = currentView; // remember which message folder we were in
	// // We are not in a single form dislay screen
	// SCVA.inFormDisplay = false;
	//
	// switch (msgScreen)
	// {
	// case INBOXVIEW:
	// // Disable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(false);
	// // Load Received Messages
	// Message.loadFileListFromFolder(Constants.DirInbox,
	// Message.SORTBYDATEREVERSED);
	// msgListView.setSelection(0);
	// // Set listener for item selection
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required
	// middleToastText(getString(R.string.txt_InboxViewscreen));
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	// public boolean onItemLongClick(AdapterView<?> parent,
	// // msgListView.setOnItemClickListener(new
	// // OnItemClickListener() {
	// // public boolean onItemLongClick(AdapterView<?> parent,
	// View view, final int position, long id)
	// {
	// // When LONG clicked, display the message
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.inboxmenupopup);
	//
	// // View as HTML delivery button
	// myButton = (Button) findViewById(R.id.button_viewashtmldelivery);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Format for display and sharing
	// mDisplayForm = Message.formatForDisplay(Constants.DirInbox, mFileName,
	// ".html");
	// // Android Bug in Webview: Line Feeds are not rendered in loadData but are
	// // OK in loadDataWithBaseURL
	// // mWebView.loadData(mDisplayForm, "text/html", "UTF-8");
	// mWebView.loadDataWithBaseURL("", mDisplayForm, "text/html", "UTF-8", "");
	// // Debug Android Webview horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// }
	// });
	// // View as HTML File Copy button
	// myButton = (Button) findViewById(R.id.button_viewashtmlfilecopy);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Format for display and sharing
	// mDisplayForm = Message.formatForDisplay(Constants.DirInbox, mFileName,
	// ".fc.html");
	// // Android Bug in Webview: Line Feeds are not rendered in loadData but are
	// // OK in loadDataWithBaseURL
	// // mWebView.loadData(mDisplayForm, "text/html", "UTF-8");
	// mWebView.loadDataWithBaseURL("", mDisplayForm, "text/html", "UTF-8", "");
	// // Debug Android Webview horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// }
	// });
	// // View as Plain text button
	// myButton = (Button) findViewById(R.id.button_viewasplaintext);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Format for display and sharing
	// String textDisplayForm = Message.formatForDisplay(Constants.DirInbox,
	// mFileName, ".txt");
	// // For display in a webview, replace all Line Feeds with the "<br>" sequence
	// // Done automatically in Message.formatForDisplay now
	// // textDisplayForm = textDisplayForm.replaceAll("\n", "<br>");
	// mWebView.loadDataWithBaseURL("", textDisplayForm, "text/html", "UTF-8", "");
	// // Debug Android Webview horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// }
	// });
	// // Share/Send button
	// myButton = (Button) findViewById(R.id.button_shareforward);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Prompt for sharing format
	// sharingAction = FORPRINTING; // To match the default in the sharedialog
	// layout
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setView(((Activity)
	// SCVA.myContext).getLayoutInflater().inflate(R.layout.sharedialog, null));
	// myAlertDialog.setPositiveButton(getString(R.string.txt_HTML), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.formatForDisplay(Constants.DirInbox, mFileName,
	// ".html");
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// ".html", sharingAction);
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": Shared " + mFileName);
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_RawFlmsg), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.readFile(Constants.DirInbox, mFileName);
	// // Extract extension for the shareInfoIntent method
	// String extension = ".raw";
	// if (mFileName.indexOf(".") != -1)
	// { // any dot
	// // if (mFileName.lastIndexOf(".") != mFileName.length()) //Dot is not last
	// // character
	// extension = mFileName.substring(mFileName.lastIndexOf("."));
	// }
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// extension, sharingAction);
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": Shared " + mFileName);
	// }
	// });
	// myAlertDialog.setNeutralButton(getString(R.string.txt_WRAP), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.formatForTx(Constants.DirInbox, mFileName, true,
	// false);// Pictures in digital form, NO compression
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// ".wrap", sharingAction);
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": Shared " + mFileName);
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Copy to Drafts button
	// myButton = (Button) findViewById(R.id.button_cpdrafts);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// String newFileName = "Copy_of_" + mFileName;
	// if (Message.copyAnyFile(Constants.DirInbox, mFileName, Constants.DirDrafts,
	// newFileName))
	// {
	// SCVA.middleToastText(
	// getString(R.string.txt_CopiedFile) + ": " + mFileName + " " +
	// getString(R.string.txt_ToDraftsFolderAs) + " " + newFileName + "\n");
	// }
	// }
	// });
	// // Copy to Outbox button
	// myButton = (Button) findViewById(R.id.button_cpoutbox);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// if (Message.copyAnyFile(Constants.DirInbox, mFileName, Constants.DirOutbox,
	// mFileName))
	// {
	// SCVA.middleToastText(getString(R.string.txt_CopiedFile) + ": " + mFileName +
	// getString(R.string.txt_ToOutboxFolder) + "\n");
	// }
	// }
	// });
	// // Delete button
	// myButton = (Button) findViewById(R.id.button_delete);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_AreYouSureDeleteMessage));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// Message.deleteFile(Constants.DirInbox, mFileName, true);// Advise deletion
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// mWebView = (WebView) findViewById(R.id.htmlwebview1);
	// // Android Webview horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// // Enable javaScript for loading of pictures opening the page
	// mWebView.getSettings().setJavaScriptEnabled(true);
	// mWebView.setWebViewClient(new WebViewClient()); // wouldn't work without
	// this!
	// // mWebView.addJavascriptInterface(new JavascriptAccess(activityBaseContext),
	// // "JavascriptAccess");
	// // Prevent caching of pictures
	// mWebView.clearCache(false);
	// mFileName = (String) parent.getItemAtPosition(position);
	// // Pre-load a "Formating..." Message
	// mWebView.loadData(getString(R.string.txt_FormattingWait), "text/html",
	// "UTF-8");
	// // Format for display and sharing
	// new backgroundFormatForDisplay(Constants.DirInbox).execute();
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'INBOX VIEW' " + e.getMessage(), null, true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// case FORMSVIEW:
	// // Disable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(false);
	// // Load Html forms for edit of new messages
	// Message.loadFileListFromFolder(Constants.DirEntryForms, Message.SORTBYNAME);
	// // Scroll to top
	// msgListView.setSelection(0);
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required
	// middleToastText(getString(R.string.txt_ComposeViewLongPress));
	// // Set listener for item selection
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	// public boolean onItemLongClick(AdapterView<?> parent, View view, final int
	// position, long id)
	// {
	// // When LONG clicked, display the file
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.formsmenupopup);
	// // Initialize the return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// // Initialize the transfer over the radio button
	// myButton = (Button) findViewById(R.id.button_transferoverradio);
	// setTextSize(myButton);
	// // myButton.setEnabled(false);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Check that we are dealing with a custom form. There is no value in
	// // transfering pre-defined aka "hard-coded" forms without editing them first.
	// if (Message.isCustomForm(Message.getExtension(Message.formName, false)))
	// {
	// // Prompt to confirm that we want to send the raw form, not to submit the
	// data
	// // entered
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_WarningSharingFormOnly));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// // Wrap the HTML form in an "html_form" Flmsg format
	// // Build message header first
	// String rawForm = Message.createHardCodedHeader("", header_reason_NEW,
	// "html_form");
	// // Then add the html form itself
	// // rawForm += mDisplayForm; //Can't use this as it has the Submit buttons
	// added
	// // in
	// try
	// {
	// // Read the form into a string
	// File f = new File(Constants.HomePath + Constants.Dirprefix +
	// Constants.DirEntryForms + Constants.Separator + mFileName);
	// FileInputStream fileIS = new FileInputStream(f);
	// BufferedReader buf = new BufferedReader(new InputStreamReader(fileIS));
	// String readString = new String();
	// while ((readString = buf.readLine()) != null)
	// {
	// rawForm += readString + "\n";
	// }
	// }
	// catch (FileNotFoundException e)
	// {
	// loggingClass.writelog("File Not Found Error in 'Send Raw Form' " +
	// e.getMessage(), null, true);
	// }
	// catch (IOException e)
	// {
	// loggingClass.writelog("IO Exception Error in 'Send Raw Form' " +
	// e.getMessage(), null, true);
	// }
	// // Copy the result into the Outbox folder ready for TXing
	// String filePath = Constants.HomePath + Constants.Dirprefix +
	// Constants.DirOutbox + Constants.Separator;
	// Message.saveDataStringAsFile(filePath, mFileName, rawForm);
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// else
	// {
	// // Let the user know that we can't transfer that form
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_SorryHardCodedForm));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setNegativeButton(getString(R.string.txt_Ok), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// }
	// });
	// // Initialize the Share over the Internet button
	// myButton = (Button) findViewById(R.id.button_shareoverinternet);
	// setTextSize(myButton);
	// myButton.setEnabled(false);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// mWebView = (WebView) findViewById(R.id.htmlwebview1);
	// setTextSize(myButton);
	// // JD check if warranted or not
	// // Prevent thread exception
	// Activity activityBaseContext = SCVA.this;
	// while (activityBaseContext.getParent() != null)
	// {
	// Utils.println("ActivityTree", activityBaseContext.getClass().getSimpleName());
	// activityBaseContext = activityBaseContext.getParent();
	// }
	// mWebView.getSettings().setJavaScriptEnabled(true);
	// mWebView.setWebViewClient(new WebViewClient()); // wouldn't work without
	// this!
	// // Not needed yet (No Discard Button in form)
	// // mWebView.setWebChromeClient(new WebChromeClient());
	// mWebView.addJavascriptInterface(new JavascriptAccess(activityBaseContext),
	// "JavascriptAccess");
	// // Prevent caching of pictures
	// mWebView.clearCache(false);
	// // Android horizontal scroll issue
	// // mWebView.getSettings().setLoadWithOverviewMode(false);
	// // mWebView.getSettings().setUseWideViewPort(true);
	// // JD debug webview not updating on orientation change sometimes
	// // mWebView.getSettings().setRenderPriority(RenderPriority.HIGH);
	// // mWebView.getSettings().setLoadWithOverviewMode(false);
	// // mWebView.getSettings().setUseWideViewPort(true);
	//
	// // Pre-load a "Formating..." Message
	// mWebView.loadData(getString(R.string.txt_FormattingWait), "text/html",
	// "UTF-8");
	// // Format for entering new message
	// mFileName = (String) parent.getItemAtPosition(position);
	// new backgroundNewFormDisplay().execute();
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'FORMSVIEW' " + e.getMessage(), null, true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// case DRAFTSVIEW:
	// // Disable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(false);
	// // Load Draft Messages
	// Message.loadFileListFromFolder(Constants.DirDrafts,
	// Message.SORTBYDATEREVERSED);
	// // Scroll to top
	// msgListView.setSelection(0);
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required for web pages
	// middleToastText(getString(R.string.txt_DraftViewLongPress));
	// // Set listener for item selection
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	//
	// public boolean onItemLongClick(AdapterView<?> parent,
	// // msgListView.setOnItemClickListener(new
	// // OnItemClickListener() {
	// // public boolean onItemLongClick(AdapterView<?> parent,
	// View view, final int position, long id)
	// {
	// // When LONG clicked, display the message
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.draftsmenupopup);
	//
	// // Delete button
	// myButton = (Button) findViewById(R.id.button_delete);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_AreYouSureDeleteMessage));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// Message.deleteFile(Constants.DirDrafts, mFileName, true);// Advise deletion
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// mWebView = (WebView) findViewById(R.id.htmlwebview1);
	// mWebView.getSettings().setJavaScriptEnabled(true);
	// mWebView.setWebViewClient(new WebViewClient()); // wouldn't work without
	// this!
	// // mWebView.setWebChromeClient(new WebChromeClient());
	// mWebView.addJavascriptInterface(new JavascriptAccess(SCVA.myContext),
	// "JavascriptAccess");
	// // Android horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// // Prevent caching of pictures
	// mWebView.clearCache(false);
	// // Pre-load a "Formating..." Message
	// mWebView.loadData(getString(R.string.txt_FormattingWait), "text/html",
	// "UTF-8");
	// // Get the file name selected in the list
	// mFileName = (String) parent.getItemAtPosition(position);
	// new backgroundFormatForEditing(Constants.DirDrafts,
	// jsSendDraftFormDataInject).execute();
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'DRAFTSVIEW' " + e.getMessage(), null, true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// case TEMPLATESVIEW:
	// // Disable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(false);
	// // Load Template Messages
	// Message.loadFileListFromFolder(Constants.DirTemplates, Message.SORTBYNAME);
	// // Scroll to top
	// msgListView.setSelection(0);
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required for web pages
	// middleToastText(getString(R.string.txt_TemplatesView));
	// // Set listener for item selection
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	//
	// public boolean onItemLongClick(AdapterView<?> parent, View view, final int
	// position, long id)
	// {
	// // When LONG clicked, display the message
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.draftsmenupopup);
	//
	// // Delete button
	// myButton = (Button) findViewById(R.id.button_delete);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_AreYouSureDeleteMessage));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// Message.deleteFile(Constants.DirTemplates, mFileName, true);// Advise
	// deletion
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// mWebView = (WebView) findViewById(R.id.htmlwebview1);
	// mWebView.getSettings().setJavaScriptEnabled(true);
	// mWebView.setWebViewClient(new WebViewClient()); // wouldn't work without
	// this!
	// // mWebView.setWebChromeClient(new WebChromeClient());
	// // Android horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// // Prevent caching of pictures
	// mWebView.clearCache(false);
	// mWebView.addJavascriptInterface(new JavascriptAccess(SCVA.myContext),
	// "JavascriptAccess");
	// mWebView.loadData(getString(R.string.txt_FormattingWait), "text/html",
	// "UTF-8");
	// // Get the file name selected in the list
	// mFileName = (String) parent.getItemAtPosition(position);
	// new backgroundFormatForEditing(Constants.DirTemplates,
	// jsSendNewFormDataInject).execute();
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'TEMPLATESVIEW' " + e.getMessage(), null,
	// true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// case OUTBOXVIEW:
	// // Enable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(true);
	// // Load list of messages in outbox
	// Message.loadFileListFromFolder(Constants.DirOutbox,
	// Message.SORTBYDATEREVERSED);
	// // Scroll to top
	// msgListView.setSelection(0);
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required
	// middleToastText(getString(R.string.txt_OutboxView));
	// // Set listener for item selection
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	//
	// public boolean onItemLongClick(AdapterView<?> parent, View view, final int
	// position, long id)
	// {
	// // When LONG clicked, display the file
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.outboxmenupopup);
	//
	// // Initialize the return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// // Initialize the transfer over the radio button
	// myButton = (Button) findViewById(R.id.button_sendoverradio);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Check that we can use the current mode to send messages
	// int mIndex = Modem.getModeIndexFullList(GS.gI().getRxModem());
	// String modemStr = Modem.modemCapListString[mIndex];
	// if (modemStr.startsWith("CONTESTIA"))
	// {
	// middleToastText(getString(R.string.txt_PleaseChangeMode));
	// }
	// else
	// {
	// if (!GS.gI().isTXActive() && !SCVA.SendingAllMessages)
	// {
	// returnFromFormView();
	// // prepare a runnable for updating the GUI list
	// displayMessagesRunnable = new DisplayMessagesRunner();
	// // MFSK picture Tx addition
	// Modem.txData(Constants.DirOutbox, mFileName, "\n\n\n... start\n" + bufferToTx
	// + "... end\n\n\n",
	// (digitalImages ? 0 : Message.attachedPictureCount),
	// Message.attachedPictureTxSPP, Message.attachedPictureColour,
	// Modem.modemCapListString[Constants.imageTxModemIndex]);
	// }
	// else
	// {
	// topToastText(getString(R.string.txt_PleaseWaitTxing));
	// }
	// }
	// }
	// });
	// // Initialize the Share over the Internet button
	// myButton = (Button) findViewById(R.id.button_shareoverinternet);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// // Added options in wrap and raw format
	// public void onClick(View v)
	// {
	// // Prompt for sharing format
	// sharingAction = FORPRINTING; // To match the default in the sharedialog
	// layout
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setView(((Activity)
	// SCVA.myContext).getLayoutInflater().inflate(R.layout.sharedialog, null));
	// myAlertDialog.setPositiveButton(getString(R.string.txt_HTML), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.formatForDisplay(Constants.DirOutbox, mFileName,
	// ".html");
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// ".html", sharingAction);
	// // Save file name in global variable in case we need it in onActivityResult
	// SCVA.lastSharedFileName = mFileName;
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_OUTBOX_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": " +
	// getString(R.string.txt_Shared) + " " + mFileName);
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_RawFlmsg), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.readFile(Constants.DirOutbox, mFileName);
	// // Extract extension for the shareInfoIntent method
	// String extension = ".raw";
	// if (mFileName.indexOf(".") != -1)
	// { // any dot
	// // if (mFileName.lastIndexOf(".") != mFileName.length()) //Dot is not last
	// // character
	// extension = mFileName.substring(mFileName.lastIndexOf("."));
	// }
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// extension, sharingAction);
	// // Save file name in global variable in case we need it in onActivityResult
	// SCVA.lastSharedFileName = mFileName;
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_OUTBOX_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": " +
	// getString(R.string.txt_Shared) + " " + mFileName);
	// }
	// });
	// myAlertDialog.setNeutralButton(getString(R.string.txt_WRAP), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.formatForTx(Constants.DirOutbox, mFileName, true,
	// false);// Images in digital form, NO compression
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// ".wrap", sharingAction);
	// // Save file name in global variable in case we need it in onActivityResult
	// SCVA.lastSharedFileName = mFileName;
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_OUTBOX_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": " +
	// getString(R.string.txt_Shared) + " " + mFileName);
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Delete button
	// myButton = (Button) findViewById(R.id.button_delete);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_AreYouSureDeleteMessage));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// Message.deleteFile(Constants.DirOutbox, mFileName, true);// Advise deletion
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	//
	// // JD Initialize the MODE UP button
	// myButton = (Button) findViewById(R.id.button_nextmode);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// try
	// {
	// if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() &&
	// !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
	// {
	// GS.gI().setRxModem(Processor.TxModem =
	// Modem.getModeUpDown(GS.gI().getRxModem(), +1));
	// Modem.changemode(GS.gI().getRxModem()); // to
	// // make the changes effective
	// saveLastModeUsed(GS.gI().getRxModem());
	// // Update the time required to Tx
	// String modeStr =
	// Modem.modemCapListString[Modem.getModeIndexFullList(GS.gI().getRxModem())];
	// TextView myModeView = (TextView) findViewById(R.id.modetext);
	// myModeView.setText(modeStr);
	// TextView estimateView = (TextView) findViewById(R.id.estimatetext);
	// String estimateStr = Message.estimate(modeStr, bufferToTx);
	// estimateView.setText(estimateStr);
	// SCVA.mHandler.post(SCVA.updateTitle);
	// }
	// }
	// catch (Exception ex)
	// {
	// loggingClass.writelog("Button Execution error: " + ex.getMessage(), null,
	// true);
	// }
	// }
	// });
	//
	// // JD Initialize the MODE DOWN button
	// myButton = (Button) findViewById(R.id.button_prevmode);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// try
	// {
	// if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() &&
	// !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
	// {
	// GS.gI().setRxModem(Processor.TxModem =
	// Modem.getModeUpDown(GS.gI().getRxModem(), -1));
	// Modem.changemode(GS.gI().getRxModem()); // to make the changes effective
	// saveLastModeUsed(GS.gI().getRxModem());
	// // Update the time required to Tx
	// String modeStr =
	// Modem.modemCapListString[Modem.getModeIndexFullList(GS.gI().getRxModem())];
	// TextView myModeView = (TextView) findViewById(R.id.modetext);
	// myModeView.setText(modeStr);
	// TextView estimateView = (TextView) findViewById(R.id.estimatetext);
	// String estimateStr = Message.estimate(modeStr, bufferToTx);
	// estimateView.setText(estimateStr);
	// SCVA.mHandler.post(SCVA.updateTitle);
	// }
	// }
	// catch (Exception ex)
	// {
	// loggingClass.writelog("Button Execution error: " + ex.getMessage(), null,
	// true);
	// }
	// }
	// });
	//
	// // JD Initialize the IMAGE MODE button
	// myButton = (Button) findViewById(R.id.button_imagemode);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// try
	// {
	// int timeToTxImages = 0;
	// if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() &&
	// !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
	// {
	// if (++Constants.imageTxModemIndex > Modem.maxImageModeIndex)
	// Constants.imageTxModemIndex = Modem.minImageModeIndex - 1;// Indicates TX in
	// // digital form (all
	// // text Tx)
	// // Check if we have selected Digital transmission of images
	// TextView totalEstimateView = (TextView) findViewById(R.id.totalestimatetext);
	// TextView myImageModeView = (TextView) findViewById(R.id.imagemodetext);
	// if (Constants.imageTxModemIndex < Modem.minImageModeIndex)
	// {
	// digitalImages = true;
	// // Disable the other image buttons
	// View itemToChange = findViewById(R.id.button_speedcolour);
	// itemToChange.setVisibility(View.INVISIBLE);
	// itemToChange = findViewById(R.id.totalestimatetext);
	// itemToChange.setVisibility(View.INVISIBLE);
	// // Re-build the bufferToTx to include the images in text mode (digital mode)
	// bufferToTx = Message.formatForTx(Constants.DirOutbox, mFileName,
	// digitalImages, true); // Allow compression
	// imageMode = getString(R.string.txt_Digital);
	// totalEstimateView.setText("");
	// myImageModeView.setText(imageMode);
	// }
	// else
	// {
	// digitalImages = false;
	// // Re-enable the button and total time field (Analog Tx of image)
	// View itemToChange = findViewById(R.id.button_speedcolour);
	// itemToChange.setVisibility(View.VISIBLE);
	// itemToChange = findViewById(R.id.totalestimatetext);
	// itemToChange.setVisibility(View.VISIBLE);
	// // If we just changed from Digital to Analog mode, rebuild buffer
	// if (Constants.imageTxModemIndex == Modem.minImageModeIndex)
	// {
	// bufferToTx = Message.formatForTx(Constants.DirOutbox, mFileName,
	// digitalImages, true); // Allow compression
	// }
	// imageMode = Modem.modemCapListString[Constants.imageTxModemIndex];
	// // Calculate the time required to Tx the images
	// int imgIndex = Message.attachedPictureCount;
	// while (--imgIndex >= 0)
	// {
	// timeToTxImages += Message.attachedPictureWidth[imgIndex] *
	// Message.attachedPictureHeight[imgIndex] * Message.attachedPictureTxSPP
	// * 0.000125 * (Message.attachedPictureColour ? 3 : 1) + 3 + 1;
	// }
	// int[] SPPtoSpeed =
	// { 0, 8, 4, 0, 2, 0, 0, 0, 1 }; // map from SPP to Xy speed display
	// // Bug found by Liu Xinnan (BH5HDE), thanks:
	// // "X1...X4" not displayed when clicking on the "Image mode" button and
	// sending
	// // method is Color
	// analogSpeedColour = ((Message.attachedPictureColour ?
	// getString(R.string.txt_Color) : getString(R.string.txt_Grey)) + ",X"
	// + Integer.toString(SPPtoSpeed[Message.attachedPictureTxSPP]));
	// totalEstimateView.setText(Integer.toString(timeToTxImages) +
	// getString(R.string.txt_Secs));
	// myImageModeView.setText(imageMode + "," + analogSpeedColour);
	// }
	// // Update the time required to Tx Text, Image Mode, Image Tx Time
	// TextView estimateView = (TextView) findViewById(R.id.estimatetext);
	// String modeStr =
	// Modem.modemCapListString[Modem.getModeIndexFullList(GS.gI().getRxModem())];
	// String estimateStr = Message.estimate(modeStr, bufferToTx);
	// estimateView.setText(estimateStr);
	// SCVA.mHandler.post(SCVA.updateTitle);
	// }
	// }
	// catch (Exception ex)
	// {
	// loggingClass.writelog("Button Execution error: " + ex.getMessage(), null,
	// true);
	// }
	// }
	// });
	//
	// // JD Initialize the SPEED/COLOUR button
	// myButton = (Button) findViewById(R.id.button_speedcolour);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// try
	// {
	// if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() &&
	// !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
	// {
	// if (Message.attachedPictureColour == false && Message.attachedPictureTxSPP >=
	// 8)
	// {
	// Message.attachedPictureColour = false;
	// Message.attachedPictureTxSPP = 4;
	// }
	// else if (Message.attachedPictureColour == false &&
	// Message.attachedPictureTxSPP == 4)
	// {
	// Message.attachedPictureColour = false;
	// Message.attachedPictureTxSPP = 2;
	// }
	// else if (Message.attachedPictureColour == false &&
	// Message.attachedPictureTxSPP == 2)
	// {
	// /*
	// * Remove X8 speed for now Message.attachedPictureColour = false;
	// * Message.attachedPictureTxSPP = 1; } else if ( Message.attachedPictureColour
	// * == false && Message.attachedPictureTxSPP <= 1) {
	// */
	// Message.attachedPictureColour = true;
	// Message.attachedPictureTxSPP = 8;
	// }
	// else if (Message.attachedPictureColour == true &&
	// Message.attachedPictureTxSPP >= 8)
	// {
	// Message.attachedPictureColour = true;
	// Message.attachedPictureTxSPP = 4;
	// }
	// else if (Message.attachedPictureColour == true &&
	// Message.attachedPictureTxSPP == 4)
	// {
	// Message.attachedPictureColour = true;
	// Message.attachedPictureTxSPP = 2;
	// }
	// else if (Message.attachedPictureColour == true &&
	// Message.attachedPictureTxSPP == 2)
	// {
	// /*
	// * Message.attachedPictureColour = true; Message.attachedPictureTxSPP = 1; }
	// * else if ( Message.attachedPictureColour == true &&
	// * Message.attachedPictureTxSPP <= 1) {
	// */
	// Message.attachedPictureColour = false;
	// Message.attachedPictureTxSPP = 8;
	// }
	// int timeToTxImages = 0;
	// // Calculate the time required to Tx the images
	// int imgIndex = Message.attachedPictureCount;
	// while (--imgIndex >= 0)
	// {
	// timeToTxImages += Message.attachedPictureWidth[imgIndex] *
	// Message.attachedPictureHeight[imgIndex] * Message.attachedPictureTxSPP
	// * 0.000125 * (Message.attachedPictureColour ? 3 : 1) + 3 + 1;
	// }
	// // Update display
	// int[] SPPtoSpeed =
	// { 0, 8, 4, 0, 2, 0, 0, 0, 1 }; // map from SPP to Xy speed display
	// analogSpeedColour = (Message.attachedPictureColour ?
	// getString(R.string.txt_Color) : getString(R.string.txt_Grey)) + ",X"
	// + Integer.toString(SPPtoSpeed[Message.attachedPictureTxSPP]);
	// TextView totalEstimateView = (TextView) findViewById(R.id.totalestimatetext);
	// totalEstimateView.setText(Integer.toString(timeToTxImages) +
	// getString(R.string.txt_Secs));
	// TextView myImageModeView = (TextView) findViewById(R.id.imagemodetext);
	// myImageModeView.setText(imageMode + "," + analogSpeedColour);
	// }
	// }
	// catch (Exception ex)
	// {
	// loggingClass.writelog("Button Execution error: " + ex.getMessage(), null,
	// true);
	// }
	// }
	// });
	//
	// mWebView = (WebView) findViewById(R.id.htmlwebview1);
	// // Android Webview horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// // Enable javaScript for loading of pictures opening the page
	// mWebView.getSettings().setJavaScriptEnabled(true);
	// mWebView.setWebViewClient(new WebViewClient()); // wouldn't work without
	// this!
	// // Prevent caching of pictures
	// mWebView.clearCache(false);
	// mFileName = (String) parent.getItemAtPosition(position);
	// // Pre-load a "Formating..." Message
	// mWebView.loadData(getString(R.string.txt_FormattingWait), "text/html",
	// "UTF-8");
	// // Format for display and sharing
	// new backgroundFormatForDisplay(Constants.DirOutbox).execute();
	// // Update mode and Tx time information
	// bufferToTx = Message.formatForTx(Constants.DirOutbox, mFileName,
	// digitalImages, true); // Allow compression
	// // Show/Hide the image options buttons
	// View showHide = findViewById(R.id.imageOptionsLayout);
	// if (Message.attachedPictureCount > 0)
	// {
	// showHide.setVisibility(View.VISIBLE);
	// // Show mode etc...
	// TextView myImageModeView = (TextView) findViewById(R.id.imagemodetext);
	// TextView totalEstimateView = (TextView) findViewById(R.id.totalestimatetext);
	// int timeToTxImages = 0;
	// if (digitalImages)
	// {
	// imageMode = getString(R.string.txt_Digital);
	// // Disable the other image buttons
	// View itemToChange = findViewById(R.id.button_speedcolour);
	// itemToChange.setVisibility(View.INVISIBLE);
	// itemToChange = findViewById(R.id.totalestimatetext);
	// itemToChange.setVisibility(View.INVISIBLE);
	// myImageModeView.setText(imageMode);
	// totalEstimateView.setText("");
	// }
	// else
	// {
	// // Calculate the time required to Tx the images
	// int imgIndex = Message.attachedPictureCount;
	// while (--imgIndex >= 0)
	// {
	// timeToTxImages += Message.attachedPictureWidth[imgIndex] *
	// Message.attachedPictureHeight[imgIndex] * Message.attachedPictureTxSPP *
	// 0.000125
	// * (Message.attachedPictureColour ? 3 : 1) + 3 + 1;// Text header + RSID
	// (average)
	// }
	// imageMode = Modem.modemCapListString[Constants.imageTxModemIndex];
	// // Update display
	// int[] SPPtoSpeed =
	// { 0, 8, 4, 0, 2, 0, 0, 0, 1 }; // map from SPP to Xy speed display
	// analogSpeedColour = "," + (Message.attachedPictureColour ?
	// getString(R.string.txt_Color) : getString(R.string.txt_Grey)) + ",X"
	// + Integer.toString(SPPtoSpeed[Message.attachedPictureTxSPP]);
	// myImageModeView.setText(imageMode + analogSpeedColour);
	// totalEstimateView.setText(Integer.toString(timeToTxImages) +
	// getString(R.string.txt_Secs));
	// }
	// }
	// else
	// {
	// showHide.setVisibility(View.GONE);
	// }
	// String modeStr =
	// Modem.modemCapListString[Modem.getModeIndexFullList(GS.gI().getRxModem())];
	// TextView myModeView = (TextView) findViewById(R.id.modetext);
	// myModeView.setText(modeStr);
	// TextView estimateView = (TextView) findViewById(R.id.estimatetext);
	// String estimateStr = Message.estimate(modeStr, bufferToTx);
	// estimateView.setText(estimateStr);
	//
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'OUTBOXVIEW' " + e.getMessage(), null, true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// case SENTITEMSVIEW:
	// // Disable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(false);
	// // Load list of messages in Sent folder
	// Message.loadFileListFromFolder(Constants.DirSent,
	// Message.SORTBYDATEREVERSED);
	// // Scroll to top
	// msgListView.setSelection(0);
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required
	// middleToastText(getString(R.string.txt_SentItemsView));
	// // Set listener for item selection
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	//
	// public boolean onItemLongClick(AdapterView<?> parent, View view, final int
	// position, long id)
	// {
	// // When LONG clicked, display the file
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.sentitemsmenupopup);
	//
	// // Initialize the return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// // Initialize the "Copy back to Outbox" button
	// myButton = (Button) findViewById(R.id.button_backtooutbox);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// Message.copyAnyFile(Constants.DirSent, mFileName, Constants.DirOutbox, true);
	// Message.deleteFile(Constants.DirSent, mFileName, false);// Don't advise
	// deletion
	// returnFromFormView();
	// }
	// });
	// // Initialize the Share over the Internet button
	// myButton = (Button) findViewById(R.id.button_shareoverinternet);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// /*
	// * public void onClick(View v) { // Already done on entry: mDisplayForm = //
	// * Message.formatForDisplay(Constants.DirInbox, // mFileName, ".html"); Intent
	// * shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName, ".html"); //
	// * Monitor is the "sharing" is done as we then move the file to the sent
	// folder
	// * // startActivity(Intent.createChooser(share, "Send Form..."));
	// * startActivityForResult(Intent.createChooser(shareIntent, "Send Form..."),
	// * SHARE_MESSAGE_RESULT); }
	// */
	// // Added wrap and raw format options
	// public void onClick(View v)
	// {
	// // Prompt for sharing format
	// sharingAction = FORPRINTING; // To match the default in the sharedialog
	// layout
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setView(((Activity)
	// SCVA.myContext).getLayoutInflater().inflate(R.layout.sharedialog, null));
	// myAlertDialog.setPositiveButton(getString(R.string.txt_HTML), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.formatForDisplay(Constants.DirSent, mFileName,
	// ".html");
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// ".html", sharingAction);
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": " +
	// getString(R.string.txt_Shared) + " " + mFileName);
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_RawFlmsg), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.readFile(Constants.DirSent, mFileName);
	// // Extract extension for the shareInfoIntent method
	// String extension = ".raw";
	// if (mFileName.indexOf(".") != -1)
	// { // any dot
	// // if (mFileName.lastIndexOf(".") != mFileName.length()) //Dot is not last
	// // character
	// extension = mFileName.substring(mFileName.lastIndexOf("."));
	// }
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// extension, sharingAction);
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": Shared " + mFileName);
	// }
	// });
	// myAlertDialog.setNeutralButton("WRAP", new DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// mDisplayForm = Message.formatForTx(Constants.DirSent, mFileName, true,
	// false);// Images in digital form, NO compression
	// Intent shareIntent = Message.shareInfoIntent(mDisplayForm, mFileName,
	// ".wrap", sharingAction);
	// startActivityForResult(Intent.createChooser(shareIntent,
	// getString(R.string.txt_SendForm)), SHARE_MESSAGE_RESULT);
	// Message.addEntryToLog(Message.dateTimeStamp() + ": " +
	// getString(R.string.txt_Shared) + " " + mFileName);
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Delete button
	// myButton = (Button) findViewById(R.id.button_delete);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_AreYouSureDeleteMessage));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// Message.deleteFile(Constants.DirSent, mFileName, true);
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// mWebView = (WebView) findViewById(R.id.htmlwebview1);
	// // Android Webview horizontal scroll issue
	// mWebView.getSettings().setLoadWithOverviewMode(false);
	// mWebView.getSettings().setUseWideViewPort(true);
	// // Enable javaScript for loading of pictures opening the page
	// mWebView.getSettings().setJavaScriptEnabled(true);
	// mWebView.setWebViewClient(new WebViewClient()); // wouldn't work without
	// this!
	// // Prevent caching of pictures
	// mWebView.clearCache(false);
	// mFileName = (String) parent.getItemAtPosition(position);
	// // Pre-load a "Formating..." Message
	// mWebView.loadData(getString(R.string.txt_FormattingWait), "text/html",
	// "UTF-8");
	// // Format for display and sharing
	// new backgroundFormatForDisplay(Constants.DirSent).execute();
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'SENTITEMSVIEW' " + e.getMessage(), null,
	// true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// case LOGSVIEW:
	// // Disable the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// myButton.setEnabled(false);
	// // Load log text file
	// Message.loadFileListFromFolder(Processor.DirLogs,
	// Message.SORTBYDATEREVERSED);
	// // Scroll to top
	// msgListView.setSelection(0);
	// // Set listener for item selection
	// msgListView.performHapticFeedback(MODE_APPEND);
	// // Advise user of long pressed required
	// middleToastText(getString(R.string.txt_LogsView));
	// msgListView.setOnItemLongClickListener(new OnItemLongClickListener()
	// {
	//
	// public boolean onItemLongClick(AdapterView<?> parent, View view, final int
	// position, long id)
	// {
	// String mLogText = "";
	// // When LONG clicked, display the file
	// try
	// {
	// // Prevent swipping out of the WebView
	// inFormDisplay = true;
	// // Show the form and associated buttons
	// setContentView(R.layout.logsmenupopup);
	//
	// // TextView emailText = (TextView)
	// // layout.findViewById(R.id.emailtext);
	// // String myDisplayForm = new String();
	// mDisplayForm = "";
	// try
	// {
	// String mFileName = (String) parent.getItemAtPosition(position);
	// // Read the log file
	// File f = new File(Constants.HomePath + Constants.Dirprefix +
	// Processor.DirLogs + Constants.Separator + mFileName);
	// FileInputStream fileIS = new FileInputStream(f);
	// BufferedReader buf = new BufferedReader(new InputStreamReader(fileIS));
	// String readString = new String();
	// while ((readString = buf.readLine()) != null)
	// {
	// mLogText += readString + "\n";
	// }
	// }
	// catch (FileNotFoundException e)
	// {
	// loggingClass.writelog("File Not Found Error in 'LOGSVIEW' " + e.getMessage(),
	// null, true);
	// }
	// catch (IOException e)
	// {
	// loggingClass.writelog("IO Exception Error in 'LOGSVIEW' " + e.getMessage(),
	// null, true);
	// }
	// TextView logTextView = (TextView) findViewById(R.id.logtext);
	// logTextView.setText(mLogText);
	// // Delete button
	// myButton = (Button) findViewById(R.id.button_delete);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(SCVA.this);
	// myAlertDialog.setMessage(getString(R.string.txt_AreYouSureDeleteLogFile));
	// myAlertDialog.setCancelable(false);
	// myAlertDialog.setPositiveButton(getString(R.string.txt_Yes), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// Message.deleteFile(Processor.DirLogs, Processor.messageLogFile, true);
	// returnFromFormView();
	// }
	// });
	// myAlertDialog.setNegativeButton(getString(R.string.txt_No), new
	// DialogInterface.OnClickListener()
	// {
	// public void onClick(DialogInterface dialog, int id)
	// {
	// dialog.cancel();
	// }
	// });
	// myAlertDialog.show();
	// }
	// });
	// // Initialize the return button
	// myButton = (Button) findViewById(R.id.button_return);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// returnFromFormView();
	// }
	// });
	// }
	// catch (Exception e)
	// {
	// loggingClass.writelog("Error in 'LOGSVIEW' " + e.getMessage(), null, true);
	// }
	// return false;
	// }
	// });
	// break;
	//
	// default:
	// break;
	//
	// }
	//
	// // Initialize the common buttons on the Message layout
	//
	// // Initialize the Inbox view button
	// myButton = (Button) findViewById(R.id.button_inbox);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	//
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, INBOXVIEW);
	// }
	// });
	//
	// // Initialize the Forms view button
	// myButton = (Button) findViewById(R.id.button_forms);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, FORMSVIEW);
	// }
	// });
	//
	// // Initialize the Template view button
	// myButton = (Button) findViewById(R.id.button_templates);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, TEMPLATESVIEW);
	// }
	// });
	//
	// // Initialize the Drafts view button
	// myButton = (Button) findViewById(R.id.button_drafts);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, DRAFTSVIEW);
	// }
	// });
	//
	// // Initialize the Outbox view button
	// myButton = (Button) findViewById(R.id.button_outbox);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, OUTBOXVIEW);
	// }
	// });
	//
	// // Initialize the Forms view button
	// myButton = (Button) findViewById(R.id.button_sentitems);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, SENTITEMSVIEW);
	// }
	// });
	//
	// // Initialize the Template view button
	// myButton = (Button) findViewById(R.id.button_logs);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// displayMessages(BOTTOM, LOGSVIEW);
	// }
	// });
	//
	// // Initialize the send all messages button
	// myButton = (Button) findViewById(R.id.button_sendall);
	// setTextSize(myButton);
	// myButton.setOnClickListener(new OnClickListener()
	// {
	// public void onClick(View v)
	// {
	// // Check that we can use the current mode to send messages
	// int mIndex = Modem.getModeIndexFullList(GS.gI().getRxModem());
	// String modemStr = Modem.modemCapListString[mIndex];
	// if (modemStr.startsWith("CONTESTIA"))
	// {
	// middleToastText(getString(R.string.txt_PleaseChangeMode));
	// }
	// else
	// {
	// if (!GS.gI().isTXActive() && !SCVA.SendingAllMessages)
	// {
	// new backgroundSendAllMessages().execute();
	// }
	// else
	// {
	// topToastText(getString(R.string.txt_PleaseWaitTxing));
	// }
	// }
	// }
	// });
	//
	// }

	public void onRadioButtonClicked(View view)
	{
		// Is the button now checked?
		boolean checked = ((RadioButton) view).isChecked();

		// Check which radio button was clicked
		switch (view.getId())
		{
		case R.id.forPrinting:
			if (checked) sharingAction = FORPRINTING;
			break;
		case R.id.forSendingAsAttachment:
			if (checked) sharingAction = FORSENDINGASATTACHMENT;
			break;
		case R.id.forSendingAsText:
			if (checked) sharingAction = FORSENDINGASTEXT;
			break;
		}
	}

	// Send all Outbox messages in sequence automatically
	class backgroundSendAllMessages extends AsyncTask<Void, Integer, String>
	{
		public backgroundSendAllMessages()
		{
		}

		@Override
		protected void onPreExecute()
		{
			SendingAllMessages = true;
		}

		@Override
		protected String doInBackground(Void... arg0)
		{
			// Send sequentially all messages in the outbox
			String[] msgToSend = Message.createFileListFromFolder(Constants.DirOutbox);
			if (msgToSend != null)
			{
				for (int i = 0; i < msgToSend.length; i++)
				{
					if (!Modem.stopTX)
					{
						GS.gI().setTxMessageCount(": " + Integer.toString(i + 1) + "/" + Integer.toString(msgToSend.length));
						// Use the last selection of method, modes, speed and color for sending all
						// attached images
						String imgModeForAllMsg = "MFSK64";
						digitalImages = true;
						if (Constants.imageTxModemIndex >= Modem.minImageModeIndex)
						{
							digitalImages = false;
							imgModeForAllMsg = Modem.modemCapListString[Constants.imageTxModemIndex];
						}
						String bufferToTx = Message.formatForTx(Constants.DirOutbox, msgToSend[i], digitalImages, true); // Allow compression
						// displayMessagesRunnable = new DisplayMessagesRunner();
						// Attached Images addition
						Modem.txData(Constants.DirOutbox, msgToSend[i], "\n\n\n... start\n" + bufferToTx + "... end\n\n\n", (digitalImages ? 0 : Message.attachedPictureCount),
								Message.attachedPictureTxSPP, Message.attachedPictureColour, imgModeForAllMsg);
						// Wait until the end of Transmission to queue the next file
						try
						{
							Thread.sleep(500);
						}
						catch (InterruptedException e)
						{
							e.printStackTrace();
						}
						// Added condition waiting for RX to have properly stopped
						while ((GS.gI().isTXActive() || (Modem.modemState != Modem.RXMODEMRUNNING)) && !Modem.stopTX)
						{
							try
							{
								Thread.sleep(500);
							}
							catch (InterruptedException e)
							{
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						// Wait 1 second for the RX modem thread to settle again before the next
						// transmission
						try
						{
							Thread.sleep(1000);
						}
						catch (InterruptedException e)
						{
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			return "";
		}

		protected void onProgressUpdate(Integer... a)
		{
		}

		protected void onPostExecute(String result)
		{
			// Reset flags so that "send all messages" can be re-launched
			Modem.stopTX = false;
			SendingAllMessages = false;
			GS.gI().setTxMessageCount("");
			SCVA.mHandler.post(SCVA.updateTitle);
		}
	}

	// Save last mode used for next app start
	public static void saveLastModeUsed(int modemCode)
	{
		GS.gI().getEditor().putString("LASTMODEUSED", Integer.toString(modemCode)).apply();
	}

	public void updateWFFrequency(double frequency)
	{
		if (SCVA.myWFView != null) SCVA.myWFView.selectedFrequency = frequency;
	}

	// STAR: Display the Modem layout and associate it's buttons
	private void displayModem(int screenAnimation, boolean withWaterfall)
	{
		// Ensure we reset the swipe action
		SCVA.inFormDisplay = false;

		if (withWaterfall)
		{
			GS.gI().setCurrentView(Constants.MODEMVIEWwithWF);
			setContentView(R.layout.modemwithwf);
			screenAnimation((ViewGroup) findViewById(R.id.modemwwfscreen), screenAnimation);
			// Get the waterfall view object for the runnable
			myWFView = (waterfallView) findViewById(R.id.WFbox);
			// myWFView.setOnTouchListener(new View.OnTouchListener() {
			// @Override
			// public boolean onTouch(View v, MotionEvent event) {
			// Utils.println("WFOnTouch", String.valueOf(event.getX()));
			// return true;
			// }
			// });
		}
		else
		{
			GS.gI().setCurrentView(Constants.MODEMVIEWnoWF);
			setContentView(R.layout.modemwithoutwf);
			screenAnimation((ViewGroup) findViewById(R.id.modemnwfscreen), screenAnimation);
			myWFView = null;
		}

		myModemTV = (TextView) findViewById(R.id.modemview);
		myModemTV.setHorizontallyScrolling(false);
		myModemTV.setTextSize(16);

		// Allow select/copy
		if (android.os.Build.VERSION.SDK_INT >= 11)
		{
			myModemTV.setTextIsSelectable(false);
		}

		// initialise CPU load bar display
		CpuLoad = (ProgressBar) findViewById(R.id.cpu_load);

		// initialise squelch and signal quality dislay
		SignalQuality = (ProgressBar) findViewById(R.id.signal_quality);

		// Reset modem display in case it was blanked out by a new oncreate call
		myModemTV.setText(decryptedTxt);
		myModemSC = (ScrollView) findViewById(R.id.modemscrollview);
		// update with whatever we have already accumulated then scroll
		SCVA.mHandler.post(SCVA.addToModem);

		// Advise user of which screen we are in
		middleToastText(getString(R.string.txt_ModemScreen));

		if (withWaterfall)
		{ // initialise two extra buttons

			// JD Initialize the Frequency UP button
			myButton = (Button) findViewById(R.id.button_frequp);
			myButton.setOnClickListener(new OnClickListener()
			{
				public void onClick(View v)
				{
					if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						Modem.setFrequency(Modem.frequency + 1);
						config.setPreferenceS("AFREQUENCY", Integer.toString((int) Modem.frequency));
						Modem.pauseRxModem();
						Modem.unPauseRxModem();
						SCVA.mHandler.post(SCVA.updateTitle);
						if (myWFView != null) myWFView.selectedFrequency = Modem.frequency;
					}
				}
			});
			myButton.setOnLongClickListener(new View.OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					Modem.searchUp();
					return false;
				}
			});

			// JD Initialize the Frequency DOWN button
			myButton = (Button) findViewById(R.id.button_freqdown);
			myButton.setOnClickListener(new OnClickListener()
			{
				public void onClick(View v)
				{
					if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						Modem.setFrequency(Modem.frequency - 1);
						config.setPreferenceS("AFREQUENCY", Integer.toString((int) Modem.frequency));
						Modem.pauseRxModem();
						Modem.unPauseRxModem();
						SCVA.mHandler.post(SCVA.updateTitle);
						if (myWFView != null) myWFView.selectedFrequency = Modem.frequency;
					}
				}
			});
			myButton.setOnLongClickListener(new View.OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					Modem.searchDown();
					return false;
				}
			});

			// JD Initialize the Waterfall Sensitivity UP button
			myButton = (Button) findViewById(R.id.button_wfsensup);
			myButton.setOnClickListener(new OnClickListener()
			{
				public void onClick(View v)
				{
					try
					{
						if (myWFView != null)
						{
							myWFView.maxvalue /= 1.25;
							if (myWFView.maxvalue < 1) myWFView.maxvalue = 1.0;
							// store value into preferences
							GS.gI().getEditor().putFloat("WFMAXVALUE", (float) myWFView.maxvalue).apply();
						}
					}
					// JD fix this catch action
					catch (Exception ex)
					{
						loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
					}
				}
			});

			// JD Initialize the Waterfall Sensitivity DOWN button
			myButton = (Button) findViewById(R.id.button_wfsensdown);
			myButton.setOnClickListener(new OnClickListener()
			{
				public void onClick(View v)
				{
					try
					{
						if (myWFView != null)
						{
							myWFView.maxvalue *= 1.25;
							if (myWFView.maxvalue > 40) myWFView.maxvalue = 40.0;
							// store value into preferences
							GS.gI().getEditor().putFloat("WFMAXVALUE", (float) myWFView.maxvalue).apply();
						}
					}
					// JD fix this catch action
					catch (Exception ex)
					{
						loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
					}
				}
			});

		}

		// Initialize the RxRSID check box
		checkbox = (CheckBox) findViewById(R.id.rxrsid);
		Modem.rxRsidOn = config.getPreferenceB("RXRSID", true);
		checkbox.setChecked(Modem.rxRsidOn);
		checkbox.setOnClickListener(new OnClickListener()
		{

			public void onClick(View v)
			{
				if (((CheckBox) v).isChecked())
				{
					// Store preference
					config.setPreferenceB("RXRSID", true);
					Modem.rxRsidOn = true;
				}
				else
				{
					// Store preference
					config.setPreferenceB("RXRSID", false);
					Modem.rxRsidOn = false;
				}
			}
		});

		// Initialize the TxRSID check box
		checkbox = (CheckBox) findViewById(R.id.txrsid);
		Modem.txRsidOn = config.getPreferenceB("TXRSID", true);
		checkbox.setChecked(Modem.txRsidOn);
		checkbox.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				if (((CheckBox) v).isChecked())
				{
					// Store preference
					config.setPreferenceB("TXRSID", true);
					Modem.txRsidOn = true;
				}
				else
				{
					// Store preference
					config.setPreferenceB("TXRSID", false);
					Modem.txRsidOn = false;
				}
			}
		});

		// Initialize the AFC check box
		checkBox_afc = (CheckBox) findViewById(R.id.afc);
		if (Modem.customModeListString[Modem.getModeIndex(GS.gI().getRxModem())].contains("PSK"))
		{
			checkBox_afc.setVisibility(View.VISIBLE);
		}
		else
		{
			checkBox_afc.setVisibility(View.GONE);
		}
		Modem.afcOn = config.getPreferenceB("AFCONOFF", true);
		checkBox_afc.setChecked(Modem.afcOn);
		checkBox_afc.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				if (((CheckBox) v).isChecked())
				{
					// Store preference
					config.setPreferenceB("AFCONOFF", true);
					Modem.setAfc(true);
				}
				else
				{
					// Store preference
					config.setPreferenceB("AFCONOFF", false);
					Modem.setAfc(false);
				}
			}
		});

		// Initialize waterfall selected frequency
		if (myWFView != null) myWFView.selectedFrequency = Modem.frequency;

		// JD Initialize the MODEM RX ON/OFF button
		myButton = (Button) findViewById(R.id.button_modemONOFF);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (GS.gI().isProcessorON())
					{
						if (Modem.modemState == Modem.RXMODEMRUNNING && !GS.gI().isReceivingForm() && !GS.gI().isTXActive())
						{
							GS.gI().setModemPaused(true);
							Modem.stopRxModem();
							stopService(new Intent(SCVA.this, Processor.class));
							// Force garbage collection to prevent Out Of Memory
							// errors on small RAM devices
							System.gc();
							GS.gI().setProcessorON(false);
							// Set modem text as selectable
							TextView myTempModemTV = (TextView) findViewById(R.id.modemview);
							if (myTempModemTV != null)
							{
								if (android.os.Build.VERSION.SDK_INT >= 11)
								{
									myTempModemTV.setTextIsSelectable(true);
								}
							}
						}
						else
						{
							bottomToastText(getString(R.string.txt_ModemCannotStopNow));
						}
					}
					else
					{
						if (Modem.modemState == Modem.RXMODEMIDLE)
						{
							GS.gI().setModemPaused(false);
							// Force garbage collection to prevent Out Of Memory
							// errors on small RAM devices
							System.gc();
							startService(new Intent(SCVA.this, Processor.class));
							GS.gI().setProcessorON(true);
							// Set modem text as NOT selectable (Only for Android 3.0 and UP)
							TextView myTempModemTV = (TextView) findViewById(R.id.modemview);
							if (myTempModemTV != null)
							{
								if (android.os.Build.VERSION.SDK_INT >= 11)
								{
									myTempModemTV.setTextIsSelectable(false);
								}
							}
						}
					}
					SCVA.mHandler.post(SCVA.updateTitle);
				}
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the MODE UP button
		myButton = (Button) findViewById(R.id.button_modeUP);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						GS.gI().setRxModem(Processor.TxModem = Modem.getModeUpDown(GS.gI().getRxModem(), +1));
						Modem.changemode(GS.gI().getRxModem()); // to make the changes effective
						saveLastModeUsed(GS.gI().getRxModem());
						SCVA.mHandler.post(SCVA.updateTitle);
						SCVA.mHandler.post(SCVA.updateAFCCheckBox);
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the MODE DOWN button
		myButton = (Button) findViewById(R.id.button_modeDOWN);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
					{
						GS.gI().setRxModem(Processor.TxModem = Modem.getModeUpDown(GS.gI().getRxModem(), -1));
						Modem.changemode(GS.gI().getRxModem()); // to make the changes effective
						saveLastModeUsed(GS.gI().getRxModem());
						SCVA.mHandler.post(SCVA.updateTitle);
						SCVA.mHandler.post(SCVA.updateAFCCheckBox);
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the Squelch UP button
		myButton = (Button) findViewById(R.id.button_squelchUP);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					Modem.AddtoSquelch(5.0);
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the Squelch DOWN button
		myButton = (Button) findViewById(R.id.button_squelchDOWN);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					Modem.AddtoSquelch(-5.0);
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});

		// JD Initialize the WATERFALL ON/OFF button
		myButton = (Button) findViewById(R.id.button_waterfallONOFF);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				try
				{
					if (GS.gI().getCurrentView() == Constants.MODEMVIEWnoWF)
					{
						displayModem(NORMAL, true);
					}
					else
					{
						displayModem(NORMAL, false);
					}
				}
				// JD fix this catch action
				catch (Exception ex)
				{
					loggingClass.writelog("Button Execution error: " + ex.getMessage(), null, true);
				}
			}
		});
	}

	// Display the About screen
	private void displayAbout()
	{
		GS.gI().setCurrentView(Constants.ABOUTVIEW);

		// Open APRS layout by default until we have other activities defined
		setContentView(R.layout.about);
		TextView myversion = (TextView) findViewById(R.id.versiontextview);
		myversion.setText("          " + Processor.version);

		// JD Initialize the return to terminal button
		myButton = (Button) findViewById(R.id.button_returntoterminal);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				displayTerminal(BOTTOM);
			}
		});

	}

	// Display Picture popup window
	public void rxPicturePopup(int picW, int picH)
	{
		// LayoutInflater for popup windows
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		pwLayout = inflater.inflate(R.layout.rxpicturepopup, (ViewGroup) findViewById(R.id.html_popup));
		Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
		/* Now we can retrieve all display-related infos */
		int winWidth = display.getWidth();
		int winHeight = display.getHeight();
		// int orientation = display.getOrientation();
		// create a large PopupWindow (it will be clipped automatically if larger than
		// main window)
		final PopupWindow pw = new PopupWindow(pwLayout, winWidth, winHeight, true);
		pw.setBackgroundDrawable(new BitmapDrawable()); // to
		// allow click event to be active, display the popup in the center
		pw.showAtLocation(pwLayout, Gravity.TOP, 0, 0);
		ImageView imageView = (ImageView) SCVA.pwLayout.findViewById(R.id.imageView1);
		// TextView emailText = (TextView) layout.findViewById(R.id.emailtext);
		// mWebView = (WebView) layout.findViewById(R.id.imageView1);
		// Return button init
		myButton = (Button) pwLayout.findViewById(R.id.button_close);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				pw.dismiss();
			}
		});
		// Slant Left button init
		myButton = (Button) pwLayout.findViewById(R.id.button_slant_left);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				Modem.deSlant(+2);
			}
		});
		// Slant Right button init
		myButton = (Button) pwLayout.findViewById(R.id.button_slant_right);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				Modem.deSlant(-2);
			}
		});
		// Save Again button init
		myButton = (Button) pwLayout.findViewById(R.id.button_save_again);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				Modem.saveAnalogPicture(false); // Not a new picture
			}
		});

		// imageView.scrollBy(x, y);

	}

	// Display Picture popup window
	public void txPicturePopup(int picW, int picH)
	{
		// LayoutInflater for popup windows
		LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		pwLayout = inflater.inflate(R.layout.txpicturepopup, (ViewGroup) findViewById(R.id.html_popup));
		Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
		/* Now we can retrieve all display-related infos */
		int winWidth = display.getWidth();
		int winHeight = display.getHeight();
		// int orientation = display.getOrientation();
		// create a large PopupWindow (it will be clipped automatically if larger than
		// main window)
		final PopupWindow pw = new PopupWindow(pwLayout, winWidth, winHeight, true);
		pw.setBackgroundDrawable(new BitmapDrawable()); // to
		// allow click event to be active, display the popup in the center
		pw.showAtLocation(pwLayout, Gravity.TOP, 0, 0);
		ImageView imageView = (ImageView) SCVA.pwLayout.findViewById(R.id.imageView1);
		// TextView emailText = (TextView) layout.findViewById(R.id.emailtext);
		// mWebView = (WebView) layout.findViewById(R.id.imageView1);
		// Return button init
		myButton = (Button) pwLayout.findViewById(R.id.button_close);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				pw.dismiss();
			}
		});
		// Return button init
		myButton = (Button) pwLayout.findViewById(R.id.button_close);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				pw.dismiss();
			}
		});
		// Return button init
		myButton = (Button) pwLayout.findViewById(R.id.button_close);
		setTextSize(myButton);
		myButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				pw.dismiss();
			}
		});

		// imageView.scrollBy(x, y);

	}

	public static final Runnable updateAFCCheckBox = new Runnable()
	{
		public void run()
		{
			if (checkBox_afc == null) return;
			if (Modem.customModeListString[Modem.getModeIndex(GS.gI().getRxModem())].contains("PSK"))
			{
				checkBox_afc.setVisibility(View.VISIBLE);
			}
			else
			{
				checkBox_afc.setVisibility(View.GONE);
			}
		}
	};

	// Connection object
	private ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Processor.LocalBinder binder = (Processor.LocalBinder) service;

			processor = binder.getService();
			isBound = true;

			String msg = processor.getMessage();
			processor.setUIListener(SCVA.this);
			if (Constants.devMode) Toast.makeText(SCVA.this, msg, Toast.LENGTH_SHORT).show();
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			isBound = false;
		}
	};

	@Override
	protected void onStop()
	{
		super.onStop();

		if (isBound)
		{
			unbindService(serviceConnection);
			isBound = false;
		}
	}
} // end of SCVA class
