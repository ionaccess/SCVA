package com.SCVA.activities;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.SCVA.Modem;
import com.SCVA.Processor;
import com.SCVA.R;
import com.SCVA.UIViews.KnobModel;
import com.SCVA.UIViews.KnobView;
import com.SCVA.Utils.ProgressDialog;
import com.SCVA.Utils.TruSDXCat;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.models.Configuration;
import com.SCVA.waterfallView;
import com.SCVA.Interfaces.CallBack;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.Interfaces.UICallBack;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;
import com.SCVA.adapters.MessageAdapter;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.MessagesDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.encryption.Aes256Encryptor;
import com.SCVA.encryption.BaseEncryptor;
import com.SCVA.encryption.BlowfishEncryptor;
import com.SCVA.encryption.Gost28147Encryptor;
import com.SCVA.encryption.Rc6Encryptor;
import com.SCVA.encryption.SerpentEncryptor;
import com.SCVA.encryption.TwofishEncryptor;
import com.SCVA.models.Chat;
import com.SCVA.models.Contact;
import com.SCVA.models.Key;
import com.SCVA.models.TextMessage;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class UserChatActivity extends BaseActivity implements View.OnClickListener, OnItemClickListener, CallBack, UICallBack, KnobModel.Listener
{
	private int v2TestSequence = 0;

	private enum ActionType
	{
		TEXT, IMAGE, KEY;

	};

	private Key key;
	int messageCount = 0;
	private Chat chat;
	private View wfView;
	private Utils utils;
	private boolean isBound;
	private Contact contact;
	private FrameLayout topBar;
	private Timer timer;
	private String msgToSend;
	private long timeRequired;
	private waterfallView wFbox;
	private TimerTask timerTask;
	private Bitmap bitmapToSend;
	private String base64String;
	private RecyclerView listView;
	private BaseEncryptor encryptor;
	private Configuration tmpConfig;
	private TextView titleText, rxView;
	private boolean isCATActive = false;
	private ArrayList<Object> allItems;
	private boolean imageEncryption = false;
	private ActionType actionType = ActionType.TEXT;
	private TextView profile;
	private Processor processor;
	private ProgressBar cpuLoad, signalQuality;
	private boolean bound = false;
	private TextView etMessage;
	private KeysDataSource keyDataSource;
	private MessageAdapter entryAdapter;
	public static final Handler handler = new Handler();
	private ContactsDataSource contactDataSource;
	private MessagesDataSource messagesDataSource;
	private SettingsDataSource settingsDataSource;
	private ImageView settings, send, btnAttach, clear, txView;
	private ConfigurationsDataSource configurationsDataSource;
	private ChatsDataSource chatsDataSource;
	private ProgressBar txProgress;
	private String bufferText = "";
	private String ModemBuffer;
	private String decryptedTxt;
	private String TerminalBuffer;
	private KnobView frequencyDial;
	private LocalDateTime dateTime;
	private PowerManager.WakeLock wakeLock;
	private static WeakReference<Context> contextRef;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.user_chat_activity);

		contextRef = new WeakReference<>(this);
		// Acuire the lock to keep the screen on
		// PowerManager powerManager = (PowerManager)
		// getSystemService(Context.POWER_SERVICE);
		// wakeLock =
		// powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
		// PowerManager.ON_AFTER_RELEASE, "SCVA::SendImage");
		// wakeLock.acquire();

		wfView = findViewById(R.id.wfView);

		utils = new Utils();
		wFbox = findViewById(R.id.WFbox);
		wFbox.setOnClickListener(this);
		wFbox.setOnTouchFrequencyChange(false);

		frequencyDial = (KnobView) findViewById(R.id.frequencyDial);

		txProgress = findViewById(R.id.tx_progress);
		chatsDataSource = new ChatsDataSource(this);
		keyDataSource = new KeysDataSource(this);
		messagesDataSource = new MessagesDataSource(this);
		settingsDataSource = new SettingsDataSource(this);
		contactDataSource = new ContactsDataSource(this);
		configurationsDataSource = new ConfigurationsDataSource(this);

		isCATActive = settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL, "0").equalsIgnoreCase("1");

		cpuLoad = findViewById(R.id.cpu_load);
		signalQuality = findViewById(R.id.signal_quality);

		topBar = findViewById(R.id.topBar);

		titleText = findViewById(R.id.title);
		titleText.setOnClickListener(this);

		profile = findViewById(R.id.profile);
		profile.setOnClickListener(this);

		etMessage = findViewById(R.id.etMessage);

		send = findViewById(R.id.send);
		send.setOnClickListener(this);

		btnAttach = findViewById(R.id.btnAttach);
		btnAttach.setOnClickListener(this);

		settings = findViewById(R.id.settings);
		settings.setOnClickListener(this);

		clear = findViewById(R.id.clear);
		clear.setOnClickListener(this);

		rxView = findViewById(R.id.rxView);
		rxView.setOnClickListener(this);

		txView = findViewById(R.id.txView);
		txView.setOnClickListener(this);

		allItems = new ArrayList<Object>();
		listView = findViewById(R.id.chatRecyclerView);
		entryAdapter = new MessageAdapter(allItems, this);
		listView.setAdapter(entryAdapter);
		entryAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver()
		{
			@Override
			public void onItemRangeInserted(int positionStart, int itemCount)
			{
				listView.scrollToPosition(entryAdapter.getItemCount() - 1);
			}
		});
		if (GS.gI().isAdvanceMode())
		{
			settings.setVisibility(View.VISIBLE);
		}
		else
		{
			settings.setVisibility(View.GONE);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		chat = chatsDataSource.getChatAgainstId(GS.gI().getValue().getInt("CHAT_ID", 1));
		if (chat != null)
		{
			GS.gI().setChatId(chat.getId());
			titleText.setText(chat.getTitle());
			profile.setText("" + chat.getTitle().substring(0, 1).toUpperCase());
			this.contact = contactDataSource.getContactAgainstId(chat.getContactId());
			this.chat.setContact(this.contact);
			tmpConfig = configurationsDataSource.getConfiguration(chat.getContact().getConfigId());
			if (!GS.gI().isTXActive())
			{
				// don't changemode while TXActive onResumed
				// Call when user press home button and then came back
				GS.gI().applyConfig(tmpConfig, false);
			}
			runOnUiThread(markRTxRsID);
			if (tmpConfig.getId() == 9)
			{
				btnAttach.setVisibility(View.GONE);// Enabled(false);
			}
		}

		if (GS.gI().getValue().getBoolean("use_enc", false))
		{
			key = keyDataSource.getDefaultKey(chat.getContactId());
			if (key != null) GS.gI().setKeyId(key.getId());
			if (key == null)
			{
				GS.gI().setKeyId(0);
				GS.gI().getEditor().putBoolean("use_enc", false).apply();
				Toast.makeText(UserChatActivity.this, "No Default Key is found.", Toast.LENGTH_LONG).show();
				return;
			}
			if (GS.gI().getPlainKey().length() == 0)
			{
				showPasswordDialog();
			}
			else
			{
				// maybe we are coming back from notification
				chat.setKey(key);
				initEncryption();
			}
		}
		else
		{
			runOnUiThread(populate);
		}
	}

	Runnable populate = new Runnable()
	{
		@Override
		public void run()
		{
			String date = "";
			String prevDate = "prev";
			allItems.clear();
			txProgress.setVisibility(View.GONE);
			// entryAdapter.clearItems();
			String timePattern = "HH:mm";
			SimpleDateFormat timeFormatter = new SimpleDateFormat(timePattern, Locale.US);
			String datePattern = "yyyy-MM-dd";
			SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern, Locale.US);
			ArrayList<TextMessage> messages = messagesDataSource.getUserMessages(contact.getId());
			for (int i = 0; i < messages.size(); i++)
			{
				messages.get(i).setHeader(false);

				date = dateFormatter.format(Long.parseLong(messages.get(i).getDate()));
				messages.get(i).setTime(timeFormatter.format(Long.parseLong(messages.get(i).getDate())));

				if (!date.equalsIgnoreCase(prevDate))
				{
					prevDate = date;
					messages.get(i).setDate(date);
					messages.get(i).setHeader(true);
				}
				// }
				String eMessage = messages.get(i).getMessage();
				String dMessage = "";
				// Check if encryption is on
				if (GS.gI().getValue().getBoolean("use_enc", false))
				{
					// Check if the message is encrypted
					if (messages.get(i).isEncrypted())
					{
						Utils.println("Populate: ", eMessage);
						// Check if the message is realy encrypted
						if (eMessage.contains("¢"))
						{
							String[] msgs = eMessage.split("¢");
							for (int j = 0; j < msgs.length; j++)
							{
								if (msgs[j].length() > 1)
								{
									dMessage = dMessage + encryptor.decrypt(msgs[j]);
								}
							}
							Utils.println(dMessage);
						}
					}
				}
				if (dMessage.length() == 0) dMessage = eMessage;
				messages.get(i).setMessage(dMessage);
				dMessage = "";
			}
			Collections.reverse(messages);
			allItems.addAll(messages);

			// message count to enable/disable RTXRsID
			messageCount = messages.size();
			runOnUiThread(markRTxRsID);
			entryAdapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onClick(View view)
	{
		if (view == wFbox)
		{
			findViewById(R.id.dialView).setVisibility(View.VISIBLE);
			frequencyDial.setVisibility(View.VISIBLE);
			frequencyDial.getModel().addListener(this);
			double currentFrequency = wFbox.getSelectedFrequency();
			((TextView) findViewById(R.id.freView)).setText(String.format("%.1f", currentFrequency) + "Hz");
			((TextView) findViewById(R.id.freView)).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					showFrequencyInputDialog(String.format("%.1f", currentFrequency) + "");
				}
			});
		}
		if (view == txView)
		{
			int checkedIndex = Integer.parseInt(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_SELECTION, "0"));
			if (checkedIndex == -1)
			{
				Intent intent = new Intent(UserChatActivity.this, ScheduleActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivityForResult(intent, 563);
			}
			else
			{
				stopRepeatingTask();
				if (wakeLock != null && wakeLock.isHeld())
				{
					wakeLock.release();
				}
				txView.setBackgroundResource(R.drawable.tower_white);
				settingsDataSource.updateSettingValue(Constants.SETTINGS_SCHEDULE_SELECTION, "-1");
			}
		}
		if (view == clear)
		{
			runOnUiThread(clearInput());
		}
		if (view == btnAttach)
		{
			// try
			// {
			// Modem.sendReadableRadioTest(v2TestSequence++);
			// }
			// catch (RuntimeException error)
			// {
			// error.printStackTrace();
			// }
			showAttachDialog();
		}
		if (view == rxView)
		{
			if (wfView.getVisibility() == View.VISIBLE)
			{
				wfView.setVisibility(View.GONE);
				bufferText = "";
			}
			else if (wfView.getVisibility() == View.GONE)
			{
				wfView.setVisibility(View.VISIBLE);
			}
		}
		if (view == send)
		{
			if (GS.gI().isTXActive())
			{
				runOnUiThread(clearInput());
				return;
			}
			if (settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL, "0").equalsIgnoreCase("1"))
			{
				TruSDXCat.getInstance().pttOn();
				long delay = Long.parseLong(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL_DELAY, "0"));
				new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						send();
					}
				}, delay * 1000);
			}
			else
			{
				send();
			}
		}
		if (view == profile || view == titleText)
		{
			Intent intent = new Intent(this, AddContactActivity.class);
			intent.putExtra("TYPE", "ALL");
			intent.putExtra("OBJECT", contact);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivityForResult(intent, 567);
		}
		if (view == settings)
		{
			Intent intent = new Intent(this, AddConfigurationActivity.class);
			intent.putExtra("OBJECT", (Parcelable) tmpConfig);
			intent.putExtra("CONTACT", (Parcelable) contact);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("TYPE", "CONTACT");
			startActivity(intent);
		}
	}

	public void send()
	{
		if (actionType == ActionType.KEY)
		{
			sendKey();
            send.setImageResource(R.drawable.stop);
            return;
		}
		if (actionType == ActionType.IMAGE)
		{
			ProgressDialog.show(this, "Please wait...");
			ProgressDialog.setCallBackListener(this);
			ProgressDialog.setCancelable(true);

			GS.gI().applyConfig(tmpConfig, true);
			// enable TXRSID to send Image
			GS.gI().getEditor().putBoolean("TXRSID", true);
			Modem.txRsidOn = true;
			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			sendImage();
			return;
		}
		if (etMessage.length() > 0)
		{
			actionType = ActionType.TEXT;
            send.setImageResource(R.drawable.stop);
			sendText();
		}
	}

	@Override
	public void onDialPositionChanged(KnobModel sender, int nicksChanged, double value)
	{
		double currentFrequency = wFbox.getSelectedFrequency();
		double newFrequency = currentFrequency + (sender.getCurrentValue() / 1000);
		Utils.println(currentFrequency + " | " + sender.getCurrentNick() + " | " + newFrequency);
		((TextView) findViewById(R.id.freView)).setText(String.format("%.1f", newFrequency) + "Hz");

		wFbox.setTouched(true);
		wFbox.setSelectedFrequency(newFrequency);
	}

	@Override
	public void onHideDialView(double value)
	{
		if (value == -1)
		{
			// to hide manually without saving values
			wFbox.setTouched(false);
			findViewById(R.id.dialView).setVisibility(View.GONE);
			runOnUiThread(updateTitle);
			return;
		}
		double currentFrequency = wFbox.getSelectedFrequency();
		GS.gI().getEditor().putString("AFREQUENCY", "" + ((currentFrequency + (value / 1000)))).apply();
		Modem.setFrequency((currentFrequency + (value / 1000)));
		Modem.pauseRxModem();
		Modem.unPauseRxModem();

		wFbox.setTouched(false);
		findViewById(R.id.dialView).setVisibility(View.GONE);
		runOnUiThread(updateTitle);
	}

	@Override
	protected void onUserLeaveHint()
	{
		onHideDialView(-1);
		super.onUserLeaveHint();
	}

	private void showAttachDialog()
	{
		new AlertDialog.Builder(this)// .setTitle("")
				.setMessage("You can attach an image \nOR a Key to send.").setCancelable(true).setPositiveButton("Share an Image", (dialog, which) -> {
					actionType = ActionType.IMAGE;
					Intent intent = new Intent(this, CustomizeImageActivity.class);
					intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivityForResult(intent, 123);
				}).setNegativeButton("Share a Key", (dialog, which) -> {
					actionType = ActionType.KEY;
					openFilePicker();
				}).setNeutralButton("Cancel", (dialog, which) -> {
					dialog.dismiss();
				}).show();
	}

	private void startBeaconTask(long delayInMs, long periodInMs, String msgToSend)
	{
		timer = new Timer();
		timerTask = new TimerTask()
		{
			@Override
			public void run()
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						// String msgToSend =
						// settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_MESSAGE,
						// "BEACON");
						etMessage.setText("BEACON " + msgToSend);
						send.performClick();
					}
				});
			}
		};
		// Schedule the task: (task, delayInMs, periodInMs)
		// This starts immediately (0ms delay) and repeats every 2000ms (2
		// seconds)
		timer.schedule(timerTask, delayInMs, periodInMs);
	}

	private void startDelayedTask(long delayInMs, String msgToSend)
	{
		timer = new Timer();
		timerTask = new TimerTask()
		{
			@Override
			public void run()
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						etMessage.setText(msgToSend);
						send.performClick();
						txView.performClick();
					}
				});
			}
		};
		// Schedule it to run exactly once after 1 minute (60,000 milliseconds)
		timer.schedule(timerTask, delayInMs);
	}

	private void startScheduleTask(long targetTime, String msgToSend)
	{
		timer = new Timer();
		timerTask = new TimerTask()
		{
			@Override
			public void run()
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						etMessage.setText(msgToSend);
						send.performClick();
						txView.performClick();
					}
				});
			}
		};
		// Schedule it to run on exac dateTime
		timer.schedule(timerTask, new Date(targetTime));
	}

	private void stopRepeatingTask()
	{
		// Cancel the individual task queue and clear the timer instance
		if (timer != null)
		{
			timer.cancel();
			timer = null;
		}
	}

	Runnable markRTxRsID = new Runnable()
	{
		@Override
		public void run()
		{
			if (messageCount < 2)
			{
				findViewById(R.id.rxId).setVisibility(View.VISIBLE);
				GS.gI().getEditor().putBoolean("RXRSID", true);
				Modem.rxRsidOn = true;

				findViewById(R.id.txId).setVisibility(View.VISIBLE);
				GS.gI().getEditor().putBoolean("TXRSID", true);
				Modem.txRsidOn = true;
			}
			else
			{
				if (tmpConfig.isrXRsID())
				{
					findViewById(R.id.rxId).setVisibility(View.VISIBLE);
					GS.gI().getEditor().putBoolean("RXRSID", true);
					Modem.rxRsidOn = true;
				}
				else
				{
					findViewById(R.id.rxId).setVisibility(View.GONE);
					GS.gI().getEditor().putBoolean("RXRSID", false);
					tmpConfig.setrXRsID(false);
					Modem.rxRsidOn = false;
				}
				if (tmpConfig.istXRsID())
				{
					findViewById(R.id.txId).setVisibility(View.VISIBLE);
					GS.gI().getEditor().putBoolean("TXRSID", true);
					Modem.txRsidOn = true;
				}
				else
				{
					findViewById(R.id.txId).setVisibility(View.GONE);
					GS.gI().getEditor().putBoolean("TXRSID", false);
					tmpConfig.settXRsID(false);
					Modem.txRsidOn = false;
				}
			}
		}
	};

	@Override
	public void onLoadMore(int position)
	{

	}

	@Override
	public void onItemClicked(Object model, String type)
	{
		TextMessage tmpMessage = (TextMessage) model;
		if (tmpMessage.getType() == TextMessage.HIS_KEY)
		{
			new AlertDialog.Builder(this).setMessage("Import Key to Keychain ?").setCancelable(true).setPositiveButton("Yes", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					Intent intent = new Intent(UserChatActivity.this, ImportKeyActivity.class);
					intent.putExtra("TYPE", "TEXT");
					intent.putExtra("TYPE_CALL", true);
					intent.putExtra("CONTACT", contact);
					intent.putExtra("KEY_TEXT", tmpMessage.getMessage());
					intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivity(intent);
				}
			}).setNegativeButton("No", null).show();
		}
		if (tmpMessage.getType() == TextMessage.MY_IMAGE)
		{
			Intent intent = new Intent(UserChatActivity.this, ImageViewerActivity.class);
			intent.putExtra("OBJECT", (Parcelable) model);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
	}

	@Override
	public void onItemLongClicked(Object model)
	{
		TextMessage tmpMessage = (TextMessage) model;
		messagesDataSource.delete(tmpMessage);
		runOnUiThread(populate);
	}

	public Runnable clearInput()
	{
		Runnable clearInput = new Runnable()
		{
			@Override
			public void run()
			{
				if (GS.gI().isTXActive())
				{
					Modem.stopTX = true;
				}
				try
				{
					Thread.sleep(3000);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				if (wakeLock != null && wakeLock.isHeld())
				{
					wakeLock.release();
				}
				handler.removeCallbacks(showTxProgress);
				runOnUiThread(populate);

				etMessage.setEnabled(true);
				clear.setVisibility(View.GONE);
				etMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				etMessage.setHint(R.string.type_a_message);
				GS.gI().applyConfig(tmpConfig, false);
				msgToSend = "";
				send.setImageResource(R.drawable.send);
			}
		};
		return clearInput;
	}

	public void sendImage()
	{
		// Acquire the WakeLock
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SCVA::SendImage");
		wakeLock.acquire();
		Utils.hideKeyboard(this, etMessage);

		Utils.println("Before Enc KB " + utils.getBase64ImageSizeInKB(msgToSend));
		// encryption part before send
		if (imageEncryption)
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
		}

		Utils.println(msgToSend);
		Utils.println("After Enc KB " + utils.getBase64ImageSizeInKB(msgToSend));
		// STAR: SENDER
		if (!GS.gI().isReceivingForm())
		{
			Modem.setListener(this);
			Processor.TX_Text += (msgToSend.trim() + "≥");
			Modem.txData("", "", msgToSend.trim() + "≥", 0, 0, false, "");
		}
		else
		{
			Toast.makeText(this, getString(R.string.txt_NotInMiddleOfMessage), Toast.LENGTH_SHORT).show();
		}
		Utils.println(Processor.TX_Text);
		Utils.println(Processor.TX_Text);
		// Clear the text field
		// etMessage.setEnabled(false);

		// txProgress.setVisibility(View.VISIBLE);
		handler.post(showTxProgress);
	}

	public void sendText()
	{
		Utils.hideKeyboard(this, etMessage);
		msgToSend = etMessage.getText().toString();
		if (msgToSend.length() == 0)
		{
			Toast.makeText(this, "Type some text to send", Toast.LENGTH_SHORT).show();
			return;
		}
		if (tmpConfig.getId() == 9)
		{
			// STAR: SENDER
			if (!GS.gI().isReceivingForm())
			{
				Modem.setListener(this);
				processor.sendMessage(msgToSend + "\n");

				// Processor.TX_Text += (msgToSend + "§");
				// Modem.txData("", "", msgToSend + "§", 0, 0, false, "");
			}
			return;
		}
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
		}

		// STAR: SENDER
		if (!GS.gI().isReceivingForm())
		{
			Modem.setListener(this);
			processor.sendMessage(msgToSend + "§");

			// Processor.TX_Text += (msgToSend + "§");
			// Modem.txData("", "", msgToSend + "§", 0, 0, false, "");
		}
		else
		{
			Toast.makeText(this, getString(R.string.txt_NotInMiddleOfMessage), Toast.LENGTH_SHORT).show();
		}
		Utils.println(Processor.TX_Text);
		// Clear the text field
		// etMessage.setEnabled(false);

		txProgress.setVisibility(View.VISIBLE);
		handler.post(showTxProgress);
	}

	public void sendKey()
	{
		Utils.hideKeyboard(this, etMessage);
		// STAR: SENDER
		if (!GS.gI().isReceivingForm())
		{
			Modem.setListener(this);
			Processor.TX_Text += (msgToSend.trim() + "√");
			Modem.txData("", "", msgToSend.trim() + "√", 0, 0, false, "");
		}
		else
		{
			Toast.makeText(this, getString(R.string.txt_NotInMiddleOfMessage), Toast.LENGTH_SHORT).show();
		}
		Utils.println(Processor.TX_Text);
		txProgress.setVisibility(View.VISIBLE);
		handler.post(showTxProgress);
	}

	Runnable showTxProgress = new Runnable()
	{
		@Override
		public void run()
		{
			// Now update progress info if we are TXing
			if (GS.gI().isTXActive() && !Modem.modemIsTuning)
			{
				txProgress.setVisibility(View.VISIBLE);
				Utils.hideKeyboard(etMessage.getContext(), etMessage);
				int percent = Modem.getTxProgressPercent();
				GS.gI().setTxProgressCount("" + Integer.toString(percent));
				txProgress.setProgress(percent);
				if (ProgressDialog.isShowing())
				{
					ProgressDialog.setMessage("Please wait..." + Integer.toString(percent) + "%");
				}
			}
			// Utils.println("TxProgress " + GS.gI().getTxProgressCount() + " |");
			handler.postDelayed(this, 500);
		}
	};

	public void showPasswordDialog()
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.reveal_key_dialog);
		dialog.setCancelable(false);

		if (Constants.devMode)
		{
			((EditText) dialog.findViewById(R.id.password)).setText("abc123");
		}

		TextView keyName = dialog.findViewById(R.id.keyName);
		keyName.setText("" + key.getName());

		Button unlock = (Button) dialog.findViewById(R.id.unlock);
		unlock.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				key = keyDataSource.getDefaultKey(chat.getContactId());
				GS.gI().setPlainKey(key.getPlainKey(((EditText) dialog.findViewById(R.id.password)).getText().toString()));

				if (GS.gI().getPlainKey().length() > 0)
				{
					utils.showNotification(UserChatActivity.this, key.getName());
					Toast.makeText(UserChatActivity.this, "Key is unlocked.", Toast.LENGTH_LONG).show();
					chat.setPassKey(((EditText) dialog.findViewById(R.id.password)).getText().toString());
					chat.setKey(key);
					initEncryption();
				}
				else
				{
					Toast.makeText(UserChatActivity.this, "Password error.", Toast.LENGTH_LONG).show();
					finish();
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
				finish();
			}
		});
		dialog.show();
	}

	void initEncryption()
	{
		if (Utils.getValue(this).getBoolean("use_enc", false))
		{
			String algo = Utils.getValue(this).getString("enc_algorithm", "");
			String key = GS.gI().getPlainKey();
			if (key.length() > 0)
			{
				// String key =
				// "1234567890123456789012345678901234567890123456789012";
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
			}
			// addTestMessages();
			runOnUiThread(populate);
		}
	}

	// Transmission is completed
	@Override
	public void notify(Object obj, String type)
	{
		if (type.equalsIgnoreCase("PROGRESS_HIDE"))
		{
			clear.performClick();
			return;
		}
		TruSDXCat.getInstance().pttOff();
		handler.removeCallbacks(showTxProgress);
		if (actionType == ActionType.TEXT)
		{
			if (tmpConfig.getId() == 9)
			{
				TextMessage textMessage = new TextMessage();
				textMessage.setType(TextMessage.MY_TEXT);
				textMessage.setMessage(msgToSend);
				textMessage.setContactId(contact.getId());
				textMessage.setKeyId(0);
				textMessage.setEncrypted(false);
				textMessage.setDate("" + System.currentTimeMillis());
				messagesDataSource.create(textMessage);
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						etMessage.setText("");
					}
				});

				chat.setDate("" + System.currentTimeMillis());
				chat.setMessage(msgToSend);
				chatsDataSource.update(chat);
				msgToSend = "";

				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						etMessage.setText("");
					}
				});
				runOnUiThread(populate);
				return;
			}
			else
			{
				TextMessage textMessage = new TextMessage();
				textMessage.setType(TextMessage.MY_TEXT);
				textMessage.setMessage(msgToSend);
				textMessage.setContactId(contact.getId());
				if (GS.gI().getValue().getBoolean("use_enc", false))
				{
					textMessage.setEncrypted(true);
					textMessage.setKeyId(keyDataSource.getDefaultKey(chat.getContactId()).getId());
				}
				textMessage.setDate("" + System.currentTimeMillis());
				messagesDataSource.create(textMessage);
				msgToSend = "";
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						etMessage.setText("");
					}
				});

				// update last message for chat list but ?
				chat.setDate("" + System.currentTimeMillis());
				chat.setMessage("encrypted msg");
				chatsDataSource.update(chat);
			}
		}
		if (actionType == ActionType.KEY)
		{
			TextMessage textMessage = new TextMessage();
			textMessage.setMessage(msgToSend);
			textMessage.setType(TextMessage.MY_KEY);
			textMessage.setContactId(contact.getId());
			textMessage.setKeyId(keyDataSource.getDefaultKey(chat.getContactId()).getId());
			textMessage.setDate("" + System.currentTimeMillis());

			messagesDataSource.create(textMessage);
			msgToSend = "";

			chat.setDate("" + System.currentTimeMillis());
			chat.setMessage("Sent a key");
			chatsDataSource.update(chat);
			runOnUiThread(clearInput());
		}
		if (actionType == ActionType.IMAGE)
		{
			TextMessage textMessage = new TextMessage();
			textMessage.setMessage(msgToSend);
			textMessage.setType(TextMessage.MY_IMAGE);
			textMessage.setContactId(contact.getId());
			textMessage.setDate("" + System.currentTimeMillis());
			if (imageEncryption)
			{
				textMessage.setEncrypted(true);
				textMessage.setKeyId(keyDataSource.getDefaultKey(chat.getContactId()).getId());
			}
			messagesDataSource.create(textMessage);
			msgToSend = "";
			runOnUiThread(clearInput());
			// Always release the lock when your task finishes.
			if (wakeLock != null && wakeLock.isHeld())
			{
				wakeLock.release();
			}
			GS.gI().applyConfig(tmpConfig, false);
			// disable TXRSID after image is sent
			GS.gI().getEditor().putBoolean("TXRSID", false);
			Modem.txRsidOn = false;
			if (ProgressDialog.isShowing()) ProgressDialog.hide();

			chat.setDate("" + System.currentTimeMillis());
			chat.setMessage("Sent an image");
			chatsDataSource.update(chat);
		}

		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				send.setImageResource(R.drawable.send);
			}
		});
		runOnUiThread(populate);
	}

	@Override
	public void post(String type, String data)
	{
		// if (type.equalsIgnoreCase("processWrapBlock"))
		// Utils.println("CallBack = " +
		// type + " | " + data);
		if (type.equalsIgnoreCase("PostToModem")) handler.post(addToModem);
		if (type.equalsIgnoreCase("updateTitle")) handler.post(updateTitle);
		if (type.equalsIgnoreCase("updateCPULoad")) handler.post(updateCPULoad);
		if (type.equalsIgnoreCase("PostToTerminal")) handler.post(addToTerminal);
		if (type.equalsIgnoreCase("updatewaterfall")) handler.post(updateWaterFall);
		if (type.equalsIgnoreCase("updateSignalQuality")) handler.post(updateSignalQuality);
		if (type.equalsIgnoreCase("updateWFFrequency")) updateWFFrequency(Double.parseDouble(data));

		// if (type.equalsIgnoreCase("updateMfskPicture"))
		// mHandler.post(updateMfskPicture);
		// if (type.equalsIgnoreCase("updateAFCCheckBox"))
		// mHandler.post(updateAFCCheckBox);
	}

	public void updateWFFrequency(double frequency)
	{
		if (wFbox != null) wFbox.selectedFrequency = frequency;
	}

	Runnable addToTerminal = new Runnable()
	{
		@Override
		public void run()
		{
			Utils.println("addToTerminal");
		}
	};
	Runnable updateTitle = new Runnable()
	{
		@Override
		public void run()
		{
			int mIndex = Modem.getModeIndexFullList(GS.gI().getRxModem());
			TextView indicators = findViewById(R.id.indicators);
			GS.gI().setTxProgressCount(""); // Reset percent

			String indicatorText = "";
			if (key == null)
			{
				indicatorText = "" + "SCVA - " + Modem.modemCapListString[mIndex] + " - " + GS.gI().getStatus() + String.format("@%.1fHz", Modem.frequency) + " | UnEncrypted.";
				SpannableString spannableString = new SpannableString(indicatorText);
				spannableString.setSpan(new ForegroundColorSpan(Color.RED), (indicatorText.length() - 12), indicatorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				indicators.setText(spannableString);
			}
			else
			{
				indicatorText = "" + "SCVA - " + Modem.modemCapListString[mIndex] + " - " + GS.gI().getStatus() + String.format("@%.1fHz", Modem.frequency) + " | " + key.getName();
				SpannableString spannableString = new SpannableString(indicatorText);
				spannableString.setSpan(new ForegroundColorSpan(Color.BLACK), (indicatorText.length() - 12), indicatorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				indicators.setText(spannableString);
			}
			// if (GS.gI().getValue().getBoolean("use_enc", false))
			// {
			// String keyName = "";
			// if (key != null) keyName = key.getName();
			// indicatorText = "" + "SCVA - " + Modem.modemCapListString[mIndex]
			// + " - " +
			// GS.gI().getStatus() + String.format("@%.1fHz", Modem.frequency) +
			// " | " +
			// keyName;
			// indicators.setText(indicatorText);
			// }
		}
	};

	// Runnable for updating the CPU load bar in Modem Window
	public final Runnable updateCPULoad = new Runnable()
	{
		public void run()
		{
			if ((cpuLoad != null) && ((GS.gI().getCurrentView() == Constants.MODEMVIEWnoWF) || (GS.gI().getCurrentView() == Constants.MODEMVIEWwithWF)))
			{
				cpuLoad.setProgress((int) GS.gI().getCPULoad());
			}
		}
	};

	// Runnable for updating the signal quality bar in Modem Window
	public final Runnable updateSignalQuality = new Runnable()
	{
		public void run()
		{
			if ((signalQuality != null) && ((GS.gI().getCurrentView() == Constants.MODEMVIEWnoWF) || (GS.gI().getCurrentView() == Constants.MODEMVIEWwithWF)))
			{
				if (Modem.metric > 80)
				{
					wfView.setVisibility(View.VISIBLE);
				}
				signalQuality.setProgress((int) Modem.metric);
				signalQuality.setSecondaryProgress((int) Modem.squelch);
				// Utils.println(Modem.metric + " | " + Modem.squelch);
				// if (Modem.metric < 1)
				// {
				// wfView.setVisibility(View.GONE);
				// }
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
			processor.setUIListener(UserChatActivity.this);
			if (Constants.devMode) Toast.makeText(UserChatActivity.this, msg, Toast.LENGTH_SHORT).show();
			displayModem(true);
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			isBound = false;
		}
	};

	@Override
	public void onStart()
	{
		super.onStart();

		Intent intent = new Intent(this, Processor.class);
		bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		// Apply Configuration here to make it effective.
		// tmpConfig =
		// configurationsDataSource.getConfiguration(chat.getContact().getConfigId());
		// GS.gI().applyConfig(tmpConfig, false);
		performOnStart();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		// txProgressThread.interrupt();
		if (isBound)
		{
			unbindService(serviceConnection);
			isBound = false;
		}
	}

	// Create runnable for updating the waterfall display
	public final Runnable updateWaterFall = new Runnable()
	{
		public void run()
		{
			if (wFbox != null)
			{
				wFbox.invalidate();
				wFbox.postInvalidate();
			}
		}
	};

	// STAR: Display the Modem layout and associate it's buttons
	private void displayModem(boolean withWaterfall)
	{
		GS.gI().setCurrentView(Constants.MODEMVIEWwithWF);
		wFbox = (waterfallView) findViewById(R.id.WFbox);
		handler.post(addToModem);

		// Initialize waterfall selected frequency
		if (wFbox != null) wFbox.selectedFrequency = Modem.frequency;
	}

	// STAR: RECEIVER
	public final Runnable addToModem = new Runnable()
	{
		public void run()
		{
			if (tmpConfig.getId() == 9)
			{
				String temp = GS.gI().getMonitor();
				bufferText = bufferText + GS.gI().getMonitor();
				ModemBuffer += temp;

				GS.gI().setMonitor("");
				// Noted a slowing down of Gui after a large number of
				// characters are received
				// Reduced the buffer size if (ModemBuffer.length() > 60000)
				if (ModemBuffer.length() > 10000) ModemBuffer = ModemBuffer.substring(5000);
				// Reassign only the size limited buffer

				// Utils.println(bufferText);
				if (bufferText.endsWith("\n"))
				{
					TextMessage textMessage = new TextMessage();
					textMessage.setType(TextMessage.HIS_TEXT);
					textMessage.setMessage(bufferText.trim());
					textMessage.setEncrypted(false);
					textMessage.setContactId(contact.getId());
					textMessage.setKeyId(0);
					textMessage.setDate("" + System.currentTimeMillis());
					messagesDataSource.create(textMessage);
					decryptedTxt = "";
					bufferText = "";

					runOnUiThread(populate);
					rxView.performClick();
				}
			}
			else
			{
				String temp = GS.gI().getMonitor();
				bufferText = bufferText + GS.gI().getMonitor();
				ModemBuffer += temp;
				Utils.println(bufferText);
				GS.gI().setMonitor("");
				// Noted a slowing down of Gui after a large number of
				// characters are received
				// Reduced the buffer size if (ModemBuffer.length() > 60000)
				if (ModemBuffer.length() > 10000) ModemBuffer = ModemBuffer.substring(5000);
				// Reassign only the size limited buffer

				// Every needs to be dumped in db decryption will be done on
				// populate
				// if (GS.gI().getValue().getBoolean("use_enc", false))
				// {
				decryptedTxt = "";
				Utils.println(bufferText);
				if (bufferText.endsWith("§"))
				{
					// Text message is received
					if (bufferText.contains("Hz>"))
					{
						// Remove tuning already processed
						bufferText = bufferText.substring(bufferText.indexOf("Hz>") + 3, bufferText.length());
					}
					// debug only
					// Utils.println(bufferText);
					// String[] msgs = bufferText.split("¢");
					// for (int i = 0; i < msgs.length; i++)
					// {
					// if (msgs[i].length() > 1)
					// {
					// decryptedTxt = decryptedTxt + encryptor.decrypt(msgs[i]);
					// }
					// }
					// Utils.println(decryptedTxt);

					TextMessage textMessage = new TextMessage();
					textMessage.setType(TextMessage.HIS_TEXT);
					textMessage.setMessage(bufferText);
					textMessage.setEncrypted(bufferText.contains("¢"));
					textMessage.setContactId(contact.getId());
					int keyId = 0;
					if (GS.gI().getValue().getBoolean("use_enc", false)) keyId = keyDataSource.getDefaultKey(chat.getContactId()).getId();
					textMessage.setKeyId(keyId);
					textMessage.setDate("" + System.currentTimeMillis());
					messagesDataSource.create(textMessage);
					decryptedTxt = "";
					bufferText = "";

					// update last message for chat list but ...?
					// chat.setMessage(bufferText);
					// chatsDataSource.update(chat);

					runOnUiThread(populate);
					rxView.performClick();
				}
				if (bufferText.endsWith("√"))
				{
					// Key is received
					Utils.println(bufferText);
					if (bufferText.contains("Hz>"))
					{
						// Remove tuning already processed
						bufferText = bufferText.substring(bufferText.indexOf("Hz>") + 3, bufferText.length());
					}
					bufferText = bufferText.substring(0, bufferText.length() - 2);

					TextMessage textMessage = new TextMessage();
					textMessage.setType(TextMessage.HIS_KEY);
					textMessage.setMessage(bufferText);
					textMessage.setContactId(contact.getId());
					textMessage.setKeyId(keyDataSource.getDefaultKey(chat.getContactId()).getId());
					textMessage.setDate("" + System.currentTimeMillis());
					messagesDataSource.create(textMessage);
					bufferText = "";
					runOnUiThread(populate);
					rxView.performClick();
				}
				if (bufferText.endsWith("≥"))
				{
					// Image is received
					Utils.println(bufferText);
					if (bufferText.contains("Hz>"))
					{
						// Remove tuning already processed
						bufferText = bufferText.substring(bufferText.indexOf("Hz>") + 3, bufferText.length());
					}
					bufferText = bufferText.substring(0, bufferText.length() - 2);

					TextMessage textMessage = new TextMessage();
					textMessage.setType(TextMessage.HIS_IMAGE);
					textMessage.setMessage(bufferText);
					textMessage.setEncrypted(bufferText.contains("¢"));
					textMessage.setContactId(contact.getId());
					textMessage.setKeyId(keyDataSource.getDefaultKey(chat.getContactId()).getId());
					textMessage.setDate("" + System.currentTimeMillis());
					messagesDataSource.create(textMessage);
					bufferText = "";
					runOnUiThread(populate);
					rxView.performClick();
				}
			}
			// else
			// {
			// Utils.println(bufferText);
			// bufferText = "";
			// }
			// }
		}
	};

	@Override
	protected void onDestroy()
	{
		utils.hideNotification(this);
		// Reset flag then stop modem
		GS.gI().setRXParamsChanged(false);
		// Cycle modem service off then on
		if (GS.gI().isProcessorON())
		{
			if (Modem.modemState == Modem.RXMODEMRUNNING)
			{
				Modem.stopRxModem();
				stopService(new Intent(UserChatActivity.this, Processor.class));
				GS.gI().setProcessorON(false);
				// Force garbage collection to prevent Out Of Memory errors
				// on small RAM devices
				System.gc();
			}
		}
		if (wakeLock != null && wakeLock.isHeld())
		{
			wakeLock.release();
		}
		stopRepeatingTask();
		super.onDestroy();
		// Clear the reference when the activity dies
		if (contextRef != null)
		{
			contextRef.clear();
		}
	}

	private void openFilePicker()
	{
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image/*"); // You can restrict to specific types like
									// "image/*" or "application/pdf"

		// Launch using the modern Activity Result API (preferred)
		startActivityForResult(Intent.createChooser(intent, "Select valid key to share"), 123);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 563 && resultCode == RESULT_OK)
		{
			String actionType = settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_SELECTION, "");
			if (actionType.equalsIgnoreCase("0"))
			{
				// DELAYED
				String msgToSend = data.getStringExtra("MESSAGE");
				long delayInMinutes = Integer.parseInt(data.getStringExtra("DELAY"));
				startDelayedTask(delayInMinutes * 60000, msgToSend);
				// to delay came in minutes so * to convert to minutes
				txView.setBackgroundResource(R.drawable.tower_red);
			}
			if (actionType.equalsIgnoreCase("1"))
			{
				// SCHEDULE
				String msgToSend = data.getStringExtra("MESSAGE");
				long timeToExec = data.getLongExtra("TIME_TO_EXEC", 0);
				startScheduleTask(timeToExec, msgToSend);
				txView.setBackgroundResource(R.drawable.tower_red);

				PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
				wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SCVA::SendImage");
				wakeLock.acquire();
			}
			if (actionType.equalsIgnoreCase("2"))
			{
				// BEACON
				long delayInMils = Integer.parseInt(data.getStringExtra("DELAY"));
				String msgToSend = data.getStringExtra("MESSAGE");
				startBeaconTask((1000), delayInMils * 60000, msgToSend);
				// to
				// delay
				// came
				// in
				// minutes
				// so *
				// to
				// convert
				// to
				// minutes
				txView.setBackgroundResource(R.drawable.tower_red);

				PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
				wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SCVA::SendImage");
				wakeLock.acquire();
			}
		}
		else if (requestCode == 567 && resultCode == RESULT_OK)
		{
			finish();
		}
		else if (requestCode == 123 && resultCode == RESULT_OK)
		{
			if (actionType == ActionType.KEY)
			{
				Uri selectedFile = data.getData();
				decodeQRCodeFromUri(selectedFile);
			}
			if (actionType == ActionType.IMAGE)
			{
				Uri selectedFile = data.getData();
				base64String = data.getStringExtra("data");
				imageEncryption = data.getBooleanExtra("imageEncryption", false);
				prepairImageToSend(selectedFile);
				Utils.println("On Receive KB " + utils.getBase64ImageSizeInKB(base64String));
			}
		}
		else
		{
			Toast.makeText(this, "Action Canceled.", Toast.LENGTH_SHORT).show();
		}
	}

	private void prepairImageToSend(Uri selectedFile)
	{
		// String imageText = utils.convertUriToBase64(this, selectedFile);
		etMessage.getKeepScreenOn();
		etMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_qr, 0, 0, 0);

		String filePath = selectedFile.getPath();
		Bitmap bitmap = utils.createThumbnailFromUri(this, selectedFile, 150, 150);
		Drawable drawable = new BitmapDrawable(getResources(), bitmap);
		etMessage.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);

		clear.setVisibility(View.VISIBLE);
		etMessage.setHint("");
		etMessage.setEnabled(false);
		msgToSend = base64String;
		tmpConfig = configurationsDataSource.getConfiguration(chat.getContact().getConfigId());
		Utils.println(base64String);
	}

	public void loadQRImage(Bitmap bitmap)
	{
		try
		{
			// 1. Get pixels
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int[] pixels = new int[width * height];
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

			// 2. Create LuminanceSource
			LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
			BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

			// 3. Decode
			Reader reader = new QRCodeReader();
			Result result = reader.decode(binaryBitmap);

			// 4. Result
			String qrCodeText = result.getText();
			Utils.println("QR_CODE  Content: " + qrCodeText);

			// keyText = qrCodeText;
			Utils.println(qrCodeText);
			if (result.getText().length() > 0)
			{
				unlockKeyForShare(qrCodeText);
			}
			else
			{
				Toast.makeText(UserChatActivity.this, getString(R.string.please_select_a_valid_key), Toast.LENGTH_LONG).show();
			}
		}
		catch (NotFoundException | ChecksumException | FormatException e)
		{
			e.printStackTrace();
			Utils.println("QR_CODE  Decoding failed");
			Toast.makeText(UserChatActivity.this, getString(R.string.please_select_a_valid_key), Toast.LENGTH_LONG).show();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// Inside your activity
	private void decodeQRCodeFromUri(Uri uri)
	{
		try
		{
			Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
			loadQRImage(bitmap);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void unlockKeyForShare(String keyText)
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
		keyName.setText(R.string.unlock_to_share);

		Button unlock = (Button) dialog.findViewById(R.id.unlock);
		unlock.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				Aes256Encryptor aes256Encryptor = new Aes256Encryptor(utils.generateMd5(((EditText) dialog.findViewById(R.id.password)).getText().toString()));
				String plainKey = aes256Encryptor.decrypt(keyText);
                if (plainKey.length() > 0)
				{
					Toast.makeText(UserChatActivity.this, "Key is unlocked.", Toast.LENGTH_LONG).show();
					etMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_qr, 0, 0, 0);
					clear.setVisibility(View.VISIBLE);
					etMessage.setHint("");
					etMessage.setEnabled(false);
					msgToSend = keyText;
				}
				else
				{
					Toast.makeText(UserChatActivity.this, "Password error.", Toast.LENGTH_LONG).show();
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

	void performOnStart()
	{
		// // Get the RSID flags from stored preferences
		// Modem.rxRsidOn = config.getPreferenceB("RXRSID", true);
		// Modem.txRsidOn = config.getPreferenceB("TXRSID", true);
		//
		// // Get the AFC flags from stored preferences
		// Modem.afcOn = config.getPreferenceB("AFCONOFF", true);
		// Modem.setAfc(Modem.afcOn);
		//
		// // Update the list of available modems
		// Modem.updateModemCapabilityList();
		//
		// Intent intent = new Intent(this, Processor.class);
		// bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		//
		// if (wFbox != null) wFbox.setSelectedFrequency(Modem.frequency);
		// // ---------------------------

		// Re-initialize modem when NOT busy to use the latest parameters
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
					stopService(new Intent(UserChatActivity.this, Processor.class));
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
			System.gc();
			GS.gI().setRxModem(Processor.TxModem = Modem.customModeListInt[Modem.getModeIndex(GS.gI().getRxModem())]);
			GS.gI().setProcessorON(true);

			// Finally, if we were on the modem screen AND we come back to it,
			// then redisplay in case we changed the waterfall frequency
			if (GS.gI().getCurrentView() == Constants.MODEMVIEWwithWF)
			{
				displayModem(true);
			}
		}
		else
		{
			// start if not ON yet AND we haven't paused the modem manually
			if (!GS.gI().isProcessorON() && !GS.gI().isModemPaused())
			{
				GS.gI().setMyNotification(utils.getNotification(this));
				System.gc();

				startService(new Intent(UserChatActivity.this, Processor.class));
				GS.gI().setProcessorON(true);
			}
		}
		handler.post(updateTitle);
	}

	@Override
	public void onBackPressed()
	{
		if (!settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_SELECTION, "").equalsIgnoreCase("-1"))
		{
			new AlertDialog.Builder(this).setMessage("Are you sure you want to suspend automation ?").setCancelable(true)
					.setPositiveButton("Yes", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int id)
						{
							txView.setBackgroundResource(R.drawable.tower_white);
							settingsDataSource.updateSettingValue(Constants.SETTINGS_SCHEDULE_SELECTION, "-1");
							UserChatActivity.super.onBackPressed();
						}
					}).setNegativeButton("No", null).show();
		}
		else
			super.onBackPressed();
	}

	private void showFrequencyInputDialog(String frequency)
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.frequency_input_dialog);
		dialog.setCancelable(true);
		((EditText) dialog.findViewById(R.id.frequency)).setText(frequency);

		Button unlock = (Button) dialog.findViewById(R.id.unlock);
		unlock.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				String frequency = ((EditText) dialog.findViewById(R.id.frequency)).getText().toString();

				double currentFrequency = wFbox.getSelectedFrequency();
				GS.gI().getEditor().putString("AFREQUENCY", "" + frequency).apply();
				Modem.setFrequency(Double.parseDouble(frequency));
				Modem.pauseRxModem();
				Modem.unPauseRxModem();

				wFbox.setTouched(false);
				findViewById(R.id.dialView).setVisibility(View.GONE);
				runOnUiThread(updateTitle);
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

	public static void logFromC(String text)
	{
		// Utils.println("" + text);
		// if (contextRef != null)
		// {
		// Context context = contextRef.get();
		// if (context != null)
		// {
		// // Toasts must run on the main UI thread
		// ((BaseActivity) context).runOnUiThread(() -> {
		// // if (text.length() > 0) Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
		// EditText logView = ((BaseActivity) context).findViewById(R.id.logView);
		// logView.append(text);
		// });
		// }
		// }
	}
}
