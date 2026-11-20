package com.SCVA.activities;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.SCVA.BackendSelection;
import com.SCVA.EncryptedMessageHistory;
import com.SCVA.GgWaveBackend;
import com.SCVA.GgWaveDiagnostics;
import com.SCVA.MessageEncryptionMetadata;
import com.SCVA.Modem;
import com.SCVA.Modem73Crypto;
import com.SCVA.Modem73ImagePayload;
import com.SCVA.Modem73KeyPayload;
import com.SCVA.Modem73Packet;
import com.SCVA.Processor;
import com.SCVA.R;
import com.SCVA.waterfallView;
import com.SCVA.Interfaces.CallBack;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.Interfaces.UICallBack;
import com.SCVA.UIViews.KnobModel;
import com.SCVA.UIViews.KnobView;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.ProgressDialog;
import com.SCVA.Utils.TruSDXCat;
import com.SCVA.Utils.Utils;
import com.SCVA.adapters.MessageAdapter;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ConfigurationsDataSource;
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
import com.SCVA.models.Configuration;
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
	private boolean phoneWaterfallVisibilityCaptured;
	private boolean radioWaterfallWasVisible;
	private long lastPhoneSpectrumSequence;
	private long lastModem73WaterfallDiagnosticMs;
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
	private volatile Processor processor;
	private volatile BackendSelection pendingSelection;
	private volatile long uiGeneration;
	private volatile boolean chatResumed;
	private boolean serviceBinding;
	private volatile boolean automationDispatch;
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
	private String pendingPhonePrepared;
	private String pendingPhoneVisibleText;
	private Uri pendingPhoneImageUri;
	private int pendingPhoneImageWidth = 96, pendingPhoneImageHeight = 96;
	private long pendingPhoneOperationId;
	private final LinkedHashMap<String, Long> phoneReceivedLogRows = new LinkedHashMap<>();
	private final EncryptedMessageHistory.Session historySession = new EncryptedMessageHistory.Session();
	public static void clearReusableTxUnlocks()
	{
		GS.gI().setPlainKey("");
	}
	private final LinkedHashMap<Integer, EncryptedMessageHistory.Row> historyRows = new LinkedHashMap<>();
	private final LinkedHashMap<Integer, EncryptedMessageHistory.Status> historyStates = new LinkedHashMap<>();
	private java.util.List<EncryptedMessageHistory.KeyRef> historyKeys = new ArrayList<>();
	private boolean historyOnlyRefresh;
	private String legacySecret, legacyWrapped;
	private int legacyUnlockedKeyId, legacyUnlockedContactId;
	private PendingStoredTx pendingRadioTx, pendingPhoneTx, pendingModem73Tx;
	private Processor.MessageContext radioReceiveContext;
	private int radioReceiveKeyId;
	private static final class PendingStoredTx
	{
		final String stored;
		final int type;
		final MessageEncryptionMetadata.Snapshot metadata;
		final BackendSelection selection;
		PendingStoredTx(String stored, int type, MessageEncryptionMetadata.Snapshot metadata, BackendSelection selection)
		{
			MessageEncryptionMetadata.validatePrepared(metadata, stored);
			this.stored = stored;
			this.type = type;
			this.metadata = metadata;
			this.selection = selection;
		}
	}
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
		// Acquire the lock to keep the screen on
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SCVA::SendImage");
		wakeLock.acquire();

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
		try
		{
			messagesDataSource.requireMessageMetadataSchema();
		}
		catch (com.SCVA.db.SqliteHelper.UnsupportedMessageSchemaException unsupported)
		{
			chatResumed = false;
			Toast.makeText(this, unsupported.getMessage(), Toast.LENGTH_LONG).show();
			return;
		}
		chatResumed = true;
		++uiGeneration;
		phoneReceivedLogRows.clear();
		radioReceiveContext = null;
		bufferText = "";
		if (processor != null) processor.detachUI(this);
		pendingSelection = null;
		chat = chatsDataSource.getChatAgainstId(GS.gI().getValue().getInt("CHAT_ID", 1));
		if (chat == null)
		{
			Toast.makeText(this, "No chat configuration selected", Toast.LENGTH_LONG).show();
			return;
		}
		GS.gI().setChatId(chat.getId());
		titleText.setText(chat.getTitle());
		profile.setText("" + chat.getTitle().substring(0, 1).toUpperCase());
		contact = contactDataSource.getContactAgainstId(chat.getContactId());
		if (contact == null)
		{
			Toast.makeText(this, "No contact configuration selected", Toast.LENGTH_LONG).show();
			return;
		}
		chat.setContact(contact);
		historySession.begin(contact.getId());
		tmpConfig = configurationsDataSource.getConfiguration(contact.getConfigId());
		refreshEncryptedHistory(); // Reading history does not require a ready modem or an unlocked TX key.
		try
		{
			String localKeyContext = "";
			if ("MODEM73".equals(tmpConfig.getModulationType()) && tmpConfig.isEncryption())
			{
				Key configuredKey = keyDataSource.getDefaultKey(contact.getId());
				localKeyContext = configuredKey == null ? "missing" : Modem73Crypto.keyContext(configuredKey.getId(), configuredKey.getKeyText());
			}
			pendingSelection = new BackendSelection(contact.getId(), tmpConfig, localKeyContext);
		}
		catch (IllegalArgumentException error)
		{
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
			return;
		}
		submitSelection();
	}

	private void submitSelection()
	{
		if (!chatResumed || !isBound || processor == null || pendingSelection == null) return;
		processor.attachUI(this, pendingSelection.contactId);
		if (!processor.applySelection(pendingSelection))
		{
			Toast.makeText(this, processor.selectionError(), Toast.LENGTH_LONG).show();
			return;
		}
		finishConfigurationResume();
		if (isPhoneConfiguration() || isModem73Configuration())
		{
			showPhoneWaterfall();
			View dial = findViewById(R.id.dialView);
			if (dial != null) dial.setVisibility(View.GONE);
			if (btnAttach != null) btnAttach.setVisibility(tmpConfig.getId() != 9 ? View.VISIBLE : View.GONE);
			postRadioUi(updateTitle);
		}
		else
			displayModem(true);
	}

	private void finishConfigurationResume()
	{
		if (!isPhoneConfiguration() && !isModem73Configuration()) runOnUiThread(markRTxRsID);
		if (tmpConfig.getId() == 9) btnAttach.setVisibility(View.GONE);
		boolean encryptSelection = isModem73Configuration() ? tmpConfig.isEncryption()
				: isPhoneConfiguration() ? tmpConfig.isEncryption() : GS.gI().getValue().getBoolean("use_enc", false);
		if (isModem73Configuration())
		{
			if (!tmpConfig.isEncryption())
			{
				runOnUiThread(populate);
				return;
			}
			key = keyDataSource.getDefaultKey(contact.getId());
			if (key == null)
			{
				Toast.makeText(this, "Choose this contact's default key before encrypted MODEM73 transmission.", Toast.LENGTH_LONG).show();
				runOnUiThread(populate);
				return;
			}
			GS.gI().setKeyId(key.getId());
			String cached = GS.gI().getPlainKey();
			if (!cached.isEmpty())
			{
				modem73Secret = cached;
				modem73WrappedKey = key.getKeyText();
				modem73UnlockedKeyId = key.getId();
				modem73KeyContactId = contact.getId();
				rememberHistoryUnlock(key, cached);
				runOnUiThread(populate);
				return;
			}
			if (modem73UnlockedKeyId != key.getId() || modem73KeyContactId != contact.getId() || modem73Secret == null
					|| !java.util.Objects.equals(key.getKeyText(), modem73WrappedKey))
				showPasswordDialog();
			else
				runOnUiThread(populate);
			return;
		}
		if (encryptSelection)
		{
			key = keyDataSource.getDefaultKey(chat.getContactId());
			if (key != null) GS.gI().setKeyId(key.getId());
			if (key == null)
			{
				GS.gI().setKeyId(0);
				encryptor = null; // Keep encryption selected; missing TX keys must not prevent opening history or downgrade TX.
				Toast.makeText(UserChatActivity.this, "No Default Key is found.", Toast.LENGTH_LONG).show();
				runOnUiThread(this::refreshEncryptedHistory);
				return;
			}
			String cached = GS.gI().getPlainKey();
			if (cached.isEmpty() || legacySecret == null || legacyUnlockedKeyId != key.getId() || legacyUnlockedContactId != contact.getId()
					|| !java.util.Objects.equals(legacyWrapped, key.getKeyText()))
			{
				if (!cached.isEmpty())
				{
					legacySecret = cached;
					legacyWrapped = key.getKeyText();
					legacyUnlockedKeyId = key.getId();
					legacyUnlockedContactId = contact.getId();
					GS.gI().setKeyId(key.getId());
					GS.gI().setPlainKey(cached);
					rememberHistoryUnlock(key, cached);
					chat.setKey(key);
					initEncryption();
				}
				else
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
			if (!chatResumed || contact == null) return;
			try
			{
				messagesDataSource.requireMessageMetadataSchema();
			}
			catch (com.SCVA.db.SqliteHelper.UnsupportedMessageSchemaException unsupported)
			{
				Toast.makeText(UserChatActivity.this, unsupported.getMessage(), Toast.LENGTH_LONG).show();
				return;
			}
			historyKeys = historyKeyReferences(keyDataSource.getUserKeys(contact.getId()));
			historyRows.clear();
			historyStates.clear();
			String date = "";
			String prevDate = "prev";
			allItems.clear();
			if (!historyOnlyRefresh) txProgress.setVisibility(View.GONE);
			// entryAdapter.clearItems();
			String timePattern = "HH:mm";
			SimpleDateFormat timeFormatter = new SimpleDateFormat(timePattern, Locale.US);
			String datePattern = "yyyy-MM-dd";
			SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern, Locale.US);
			ArrayList<TextMessage> messages = messagesDataSource.getUserMessages(contact.getId());
			for (int i = 0; i < messages.size(); i++)
			{
				messages.get(i).setHeader(false);
				String phoneRowDate = messages.get(i).getDate();

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
                Utils.println(eMessage);
				Long phoneOperation = isPhoneConfiguration() ? phoneReceivedLogRows.remove(phoneRowDate) : null;

				EncryptedMessageHistory.Row historyRow = historyRow(messages.get(i));
				historyRows.put(historyRow.id, historyRow);
				EncryptedMessageHistory.Result historyResult = displayEncryptedHistory(messages.get(i));
				historyStates.put(historyRow.id, historyResult.status);
				String dMessage = historyResult.display();
				if (isPhoneConfiguration() && Modem73KeyPayload.isKey(dMessage)
						&& (messages.get(i).getType() == TextMessage.MY_TEXT || messages.get(i).getType() == TextMessage.HIS_TEXT))
					messages.get(i).setType(messages.get(i).getType() == TextMessage.MY_TEXT ? TextMessage.MY_KEY : TextMessage.HIS_KEY);
				if (messages.get(i).getType() == TextMessage.HIS_TEXT && Modem73ImagePayload.isImage(dMessage, 4096)) messages.get(i).setType(TextMessage.HIS_IMAGE);
				if (messages.get(i).getType() == TextMessage.MY_KEY || messages.get(i).getType() == TextMessage.HIS_KEY) dMessage = Modem73KeyPayload.display(dMessage);
				if (phoneOperation != null)
				{
					phoneTrace("GGWAVE_RX",
							"stage=application_display op=" + phoneOperation + " result=" + historyResult.status + " encrypted=" + messages.get(i).isEncrypted()
									+ " storedUtf8Bytes=" + eMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " displayUtf8Bytes="
									+ dMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
					GgWaveDiagnostics.event(UserChatActivity.this, "phone.rx.decrypt", phoneOperation, "local", tmpConfig.getGgWaveSpeed(),
							"\"outcome\":\"" + (messages.get(i).isEncrypted() ? (historyResult.opened() ? "complete" : "unavailable") : "plaintext") + "\"");
				}
				if (messages.get(i).isEncrypted() && (MessageEncryptionMetadata.MODEM73.equals(messages.get(i).getCiphertextFormat()) || isPhoneConfiguration())
						&& !historyResult.opened() && (messages.get(i).getType() == TextMessage.MY_IMAGE || messages.get(i).getType() == TextMessage.HIS_IMAGE))
					messages.get(i).setType(messages.get(i).getType() == TextMessage.MY_IMAGE ? TextMessage.MY_TEXT : TextMessage.HIS_TEXT);
				messages.get(i).setMessage(dMessage);
				dMessage = "";
			}
			Collections.reverse(messages);
			allItems.addAll(messages);

			// message count to enable/disable RTXRsID
			messageCount = messages.size();
			if (!historyOnlyRefresh) runOnUiThread(markRTxRsID);
			entryAdapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onClick(View view)
	{
		if (view == wFbox)
		{
			if (tmpConfig.getId() < 10)
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
		}
		if (view == txView)
		{
			if (isPhoneConfiguration() || isModem73Configuration())
			{
				// Toast.makeText(this, "Scheduled and beacon sends are unavailable for GGWave.", Toast.LENGTH_LONG).show();
				return;
			}
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
			if (!messageStorageReady()) return;
			if (isModem73Configuration())
			{
				if (automationDispatch)
				{
					Toast.makeText(this, "MODEM73 automation is unavailable.", Toast.LENGTH_LONG).show();
					return;
				}
				if (processor == null || contact == null || !processor.matchesSelection(pendingSelection))
				{
					Toast.makeText(this, "Wait for the MODEM73 backend to become ready.", Toast.LENGTH_LONG).show();
					return;
				}
				if (processor.backendBusy(contact.getId()))
				{
					processor.cancelRadio(contact.getId());
					runOnUiThread(clearInput());
					return;
				}
				if (actionType == ActionType.KEY) sendModem73Key();
				else if (actionType == ActionType.IMAGE) sendModem73Image();
				else
					sendText();
				return;
			}
			if (isPhoneConfiguration() || isModem73Configuration())
			{
				if (automationDispatch)
				{
					// Toast.makeText(this, "Scheduled and beacon sends are unavailable for GGWave.", Toast.LENGTH_LONG).show();
					return;
				}
				if (processor == null || !processor.matchesSelection(pendingSelection) || contact == null)
				{
					// Toast.makeText(this, "Wait for the selected GGWave configuration.", Toast.LENGTH_LONG).show();
					return;
				}
				if (actionType == ActionType.KEY)
				{
					if (msgToSend == null || msgToSend.trim().isEmpty())
					{
						Toast.makeText(this, "Select and unlock a key first.", Toast.LENGTH_SHORT).show();
						return;
					}
				}
				if (processor.backendBusy(contact.getId()))
				{
					processor.cancelRadio(contact.getId());
					return;
				}
				sendText(); // Phone TX never enters the Radio CAT/PTT delay path.
				return;
			}
			if (GS.gI().isTXActive())
			{
				runOnUiThread(clearInput());
				return;
			}
			if (!requireRadioSelection()) return;
			if ((actionType == ActionType.TEXT && GS.gI().getValue().getBoolean("use_enc", false) || actionType == ActionType.IMAGE && imageEncryption) && !legacyTxKeyReady())
				return;
			if (settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL, "0").equalsIgnoreCase("1"))
			{
				TruSDXCat.getInstance().pttOn();
				long delay = Long.parseLong(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL_DELAY, "0"));
				final BackendSelection delayedSelection = pendingSelection;
				new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						if (pendingSelection == null || !pendingSelection.sameAs(delayedSelection))
						{
							TruSDXCat.getInstance().pttOff();
							return;
						}
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
			Class<?> editor = isPhoneConfiguration() ? GGWaveConfigurationActivity.class
					: isModem73Configuration() ? Modem73ConfigurationActivity.class : AddConfigurationActivity.class;
			Intent intent = new Intent(this, editor);
			intent.putExtra("OBJECT", (Parcelable) tmpConfig);
			intent.putExtra("CONTACT", (Parcelable) contact);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("TYPE", "CONTACT");
			startActivity(intent);
		}
	}

	public boolean changeRadioFrequency(double value, String savedValue)
	{
		if (!requireRadioSelection()) return false;
		try
		{
			processor.changeFrequency(value, savedValue);
			return true;
		}
		catch (RuntimeException error)
		{
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
			return false;
		}
	}

	private boolean requireRadioSelection()
	{
		try
		{
			if (processor == null || contact == null || !processor.matchesSelection(pendingSelection)) throw new IllegalStateException("Wait for the configured modem service");
			processor.requireRadio(contact.getId());
			return true;
		}
		catch (RuntimeException error)
		{
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
			return false;
		}
	}

	public void send()
	{
		if (!requireRadioSelection())
		{
			TruSDXCat.getInstance().pttOff();
			return;
		}
		try
		{
			sendRadioRequest();
		}
		catch (RuntimeException error)
		{
			TruSDXCat.getInstance().pttOff();
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void sendRadioRequest()
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

			processor.applyRadioConfiguration(contact.getId(), true);
			// enable TXRSID to send Image

			processor.setRsid(contact.getId(), false, true);
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
		if (!requireRadioSelection()) return;
		if (!changeRadioFrequency(currentFrequency + (value / 1000), "" + (currentFrequency + (value / 1000)))) return;

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
		if (isPhoneConfiguration())
		{
			new AlertDialog.Builder(this).setMessage("Share a compact image or a key over GGWave.").setPositiveButton("Share an Image", (dialog, which) -> {
				actionType = ActionType.IMAGE;
				Intent intent = new Intent(this, CustomizeImageActivity.class);
				intent.putExtra("ggwave", true);
				intent.putExtra("modem73Encrypted", tmpConfig.isEncryption());
				startActivityForResult(intent, 123);
			}).setNegativeButton("Share a Key", (dialog, which) -> {
				actionType = ActionType.KEY;
				msgToSend = "";
				openFilePicker();
			}).setNeutralButton("Cancel", null).show();
			return;
		}
		if (isModem73Configuration())
		{
			new AlertDialog.Builder(this).setMessage("Share an image or a key over MODEM73.").setPositiveButton("Share an Image", (dialog, which) -> {
				actionType = ActionType.IMAGE;
				Intent intent = new Intent(this, CustomizeImageActivity.class);
				intent.putExtra("modem73", true);
				intent.putExtra("modem73Encrypted", tmpConfig.isEncryption());
				startActivityForResult(intent, 123);
			}).setNegativeButton("Share a Key", (dialog, which) -> {
				actionType = ActionType.KEY;
				msgToSend = "";
				openFilePicker();
			}).setNeutralButton("Cancel", null).show();
			return;
		}
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
						if (isPhoneConfiguration() || isModem73Configuration())
						{
							/* Toast.makeText(UserChatActivity.this, "Scheduled and beacon sends are unavailable for GGWave.", Toast.LENGTH_LONG).show(); */ return;
						}
						// String msgToSend =
						// settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_MESSAGE,
						// "BEACON");
						etMessage.setText("BEACON " + msgToSend);
						automationDispatch = true;
						try
						{
							send.performClick();
						}
						finally
						{
							automationDispatch = false;
						}
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
						if (isPhoneConfiguration())
						{
							/* Toast.makeText(UserChatActivity.this, "Scheduled and beacon sends are unavailable for GGWave.", Toast.LENGTH_LONG).show(); */ return;
						}
						etMessage.setText(msgToSend);
						automationDispatch = true;
						try
						{
							send.performClick();
							txView.performClick();
						}
						finally
						{
							automationDispatch = false;
						}
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
						if (isPhoneConfiguration())
						{
							/* Toast.makeText(UserChatActivity.this, "Scheduled and beacon sends are unavailable for GGWave.", Toast.LENGTH_LONG).show(); */ return;
						}
						etMessage.setText(msgToSend);
						automationDispatch = true;
						try
						{
							send.performClick();
							txView.performClick();
						}
						finally
						{
							automationDispatch = false;
						}
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
			if (processor == null || contact == null || !processor.hasSelectionFor(contact.getId())) return;
			if (isPhoneConfiguration() || isModem73Configuration())
			{
				findViewById(R.id.rxId).setVisibility(View.GONE);
				findViewById(R.id.txId).setVisibility(View.GONE);
				return;
			}
			if (messageCount < 2)
			{
				findViewById(R.id.rxId).setVisibility(View.VISIBLE);

				processor.setRsid(contact.getId(), true, true);

				findViewById(R.id.txId).setVisibility(View.VISIBLE);

				processor.setRsid(contact.getId(), false, true);
			}
			else
			{
				if (tmpConfig.isrXRsID())
				{
					findViewById(R.id.rxId).setVisibility(View.VISIBLE);

					processor.setRsid(contact.getId(), true, true);
				}
				else
				{
					findViewById(R.id.rxId).setVisibility(View.GONE);

					tmpConfig.setrXRsID(false);
					processor.setRsid(contact.getId(), true, false);
				}
				if (tmpConfig.istXRsID())
				{
					findViewById(R.id.txId).setVisibility(View.VISIBLE);

					processor.setRsid(contact.getId(), false, true);
				}
				else
				{
					findViewById(R.id.txId).setVisibility(View.GONE);

					tmpConfig.settXRsID(false);
					processor.setRsid(contact.getId(), false, false);
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
		EncryptedMessageHistory.Row history = historyRows.get(tmpMessage.getId());
		EncryptedMessageHistory.Status state = historyStates.get(tmpMessage.getId());
		if (history != null && history.encrypted && (tmpMessage.getType() == TextMessage.MY_TEXT || tmpMessage.getType() == TextMessage.HIS_TEXT
				|| state != EncryptedMessageHistory.Status.OPENED && state != EncryptedMessageHistory.Status.OPENED_LEGACY_PADDING))
		{
			openEncryptedHistory(history);
			return;
		}
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
		if (tmpMessage.getType() == TextMessage.MY_IMAGE || tmpMessage.getType() == TextMessage.HIS_IMAGE)
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
		final long generation = uiGeneration;
		Runnable clearInput = new Runnable()
		{
			@Override
			public void run()
			{
				if (generation != uiGeneration) return;
				if (GS.gI().isTXActive())
				{
					if (processor != null && contact != null) processor.cancelRadio(contact.getId());
				}
				if (!isPhoneConfiguration() && !isModem73Configuration()) try
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
				if (isModem73Configuration() || isPhoneConfiguration())
				{
					actionType = ActionType.TEXT;
					if (isModem73Configuration()) etMessage.setText("");
					pendingModem73ImageUri = null;
					pendingPhoneImageUri = null;
					base64String = null;
				}
				clear.setVisibility(View.GONE);
				etMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				etMessage.setHint(R.string.type_a_message);
				if (!isPhoneConfiguration() && !isModem73Configuration()) restoreRadioConfiguration();
				msgToSend = "";
				send.setImageResource(R.drawable.send);
			}
		};
		return clearInput;
	}

	public void sendImage()
	{
		if (!messageStorageReady() || imageEncryption && !legacyTxKeyReady()) return;
		MessageEncryptionMetadata.ChunkLedger ledger = new MessageEncryptionMetadata.ChunkLedger();
		// Acquire the WakeLock
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SCVA::SendImage");
		wakeLock.acquire();
		Utils.hideKeyboard(this, etMessage);

		Utils.println("Before Enc KB " + utils.getBase64ImageSizeInKB(msgToSend));
		try
		{
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
					ledger.add(encryptedChunk, j - i);
				}
				msgToSend = finalIntext.toString();
			}

			/* Utils.println(msgToSend); // Message/key contents: no default logging. */
			Utils.println("After Enc KB " + utils.getBase64ImageSizeInKB(msgToSend));
			// STAR: SENDER
			if (!GS.gI().isReceivingForm())
			{
				// The service binds completion to the selected contact and attached UI.
				MessageEncryptionMetadata.Snapshot metadata = imageEncryption ? legacyTxMetadata(ledger) : MessageEncryptionMetadata.plaintext(contact.getId(), 0);
				PendingStoredTx candidate = new PendingStoredTx(msgToSend, TextMessage.MY_IMAGE, metadata, pendingSelection);
				processor.sendImage(contact.getId(), msgToSend.trim() + "~");
				pendingRadioTx = candidate;
			}
			else
			{
				Toast.makeText(this, getString(R.string.txt_NotInMiddleOfMessage), Toast.LENGTH_SHORT).show();
			}
			/* Utils.println(Processor.TX_Text); // Message/key contents: no default logging. */
			/* Utils.println(Processor.TX_Text); // Message/key contents: no default logging. */
			// Clear the text field
			// etMessage.setEnabled(false);

			// txProgress.setVisibility(View.VISIBLE);
			handler.post(showTxProgress);
		}
		catch (RuntimeException unavailable)
		{
			pendingRadioTx = null;
			if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
			restoreRadioConfiguration();
			rejectMessagePreparation();
		}
	}

	private String fitGgWaveImage()
	{
		if (pendingPhoneImageUri == null) throw new IllegalArgumentException("Select an image first.");
		if (tmpConfig.isEncryption() && (!legacyTxKeyReady() || encryptor == null)) throw new IllegalStateException("Unlock the selected encryption key first.");
		final int byteBudget = tmpConfig.isEncryption() ? 512 : 256; // At most 14 or 7 GW2 data fragments.
		int width = pendingPhoneImageWidth, height = pendingPhoneImageHeight;
		while (true)
		{
			Bitmap source = utils.createThumbnailFromUri(this, pendingPhoneImageUri, width, height);
			if (source == null) break;
			Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
			for (int quality : new int[]
			{ 35, 20, 10 })
			{
				java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
				if (!scaled.compress(Bitmap.CompressFormat.WEBP, quality, output)) continue;
				String candidate = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP);
				int bytes = 2; // UTF-8 § terminator.
				if (tmpConfig.isEncryption())
				{
					int chunkSize = encryptor.getEncryptorChunkSize();
					if (chunkSize < 1) throw new IllegalStateException("GGWave encryption unavailable.");
					for (int i = 0; i < candidate.length() && bytes <= byteBudget; i += chunkSize)
						bytes += encryptor.encrypt(candidate.substring(i, Math.min(candidate.length(), i + chunkSize))).replace("\n", "¢")
								.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
				}
				else
					bytes += candidate.length();
				if (bytes <= byteBudget) return candidate;
			}
			if (width == 1 && height == 1) break;
			width = Math.max(1, width * 3 / 4);
			height = Math.max(1, height * 3 / 4);
		}
		throw new IllegalArgumentException("Image cannot fit the fast GGWave size limit. Choose a simpler image.");
	}

	public void sendText()
	{
		if (isModem73Configuration())
		{
			sendModem73Text();
			return;
		}
		if (!messageStorageReady()) return;
		if (processor.backendBusy(contact.getId()))
		{
			Toast.makeText(this, "Transmitter busy.", Toast.LENGTH_LONG).show();
			return;
		}
		Utils.hideKeyboard(this, etMessage);
		try
		{
			if (isPhoneConfiguration() && actionType == ActionType.IMAGE) msgToSend = fitGgWaveImage();
			else if (isPhoneConfiguration() && actionType == ActionType.KEY) msgToSend = Modem73KeyPayload.wire(msgToSend);
			else
				msgToSend = etMessage.getText().toString();
		}
		catch (RuntimeException rejected)
		{
			Toast.makeText(this, rejected.getMessage(), Toast.LENGTH_LONG).show();
			return;
		}
		final String visiblePhoneText = msgToSend;
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
				// The service binds completion to the selected contact and attached UI.
				PendingStoredTx candidate = new PendingStoredTx(msgToSend, TextMessage.MY_TEXT, MessageEncryptionMetadata.plaintext(contact.getId(), 0), pendingSelection);
				processor.sendMessage(contact.getId(), msgToSend + "\n");
				pendingRadioTx = candidate;

				// Processor.TX_Text += (msgToSend + "§");
				// Modem.txData("", "", msgToSend + "§", 0, 0, false, "");
			}
			return;
		}
		// encryption part before send
		boolean encryptSelection = isPhoneConfiguration() ? tmpConfig.isEncryption() : GS.gI().getValue().getBoolean("use_enc", false);
		final boolean phoneText = isPhoneConfiguration();
		final long phoneOperationId = phoneText ? GgWaveDiagnostics.nextOperationId() : 0;
		if (phoneText)
		{
			String algorithm = tmpConfig.getEncryptionType();
			if (algorithm == null || !algorithm.matches("[A-Za-z0-9_-]{1,24}")) algorithm = "other";
			GgWaveDiagnostics.event(this, "phone.tx.input", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(),
					"\"inputBytes\":" + visiblePhoneText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
			GgWaveDiagnostics.event(this, "phone.tx.prepare", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(),
					"\"encryption\":" + encryptSelection + ",\"algorithm\":\"" + algorithm + "\",\"outcome\":\"started\"");
			if (actionType == ActionType.TEXT) GgWaveDiagnostics.content(this, "phone.tx.input", phoneOperationId, "originalText", visiblePhoneText);
			phoneTrace("GGWAVE_TX", "stage=input op=" + phoneOperationId + " inputUtf8Bytes=" + visiblePhoneText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
					+ " encrypted=" + encryptSelection + " speed=" + tmpConfig.getGgWaveSpeed());
		}
		int encryptedChunkCount = 0;
		MessageEncryptionMetadata.ChunkLedger ledger = new MessageEncryptionMetadata.ChunkLedger();
		if (encryptSelection)
		{
			if (!legacyTxKeyReady()) return;
			if (encryptor == null)
			{
				if (phoneText) phoneTrace("GGWAVE_TX", "stage=encryption result=failed reason=key_unavailable");
				if (phoneText)
					GgWaveDiagnostics.event(this, "phone.tx.prepare", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(), "\"outcome\":\"failed\",\"error\":\"key_unavailable\"");
				Toast.makeText(this, "The selected encryption key is not ready.", Toast.LENGTH_LONG).show();
				return;
			}
			StringBuilder finalIntext = new StringBuilder();
			try
			{
				for (int i = 0; i < msgToSend.length(); i += encryptor.getEncryptorChunkSize())
				{
					int j = 0;
					if (i + encryptor.getEncryptorChunkSize() > msgToSend.length()) j = msgToSend.length();
					else
						j = i + encryptor.getEncryptorChunkSize();
					String encryptedChunk = encryptor.encrypt(msgToSend.substring(i, j));
					encryptedChunk = encryptedChunk.replace("\n", "¢");
					finalIntext.append(encryptedChunk);
					ledger.add(encryptedChunk, j - i);
					encryptedChunkCount++;
				}
			}
			catch (RuntimeException preparationFailure)
			{
				if (phoneText) phoneTrace("GGWAVE_TX", "stage=encryption result=failed exception=" + preparationFailure.getClass().getSimpleName());
				if (phoneText) GgWaveDiagnostics.event(this, "phone.tx.prepare", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(),
						"\"outcome\":\"failed\",\"error\":\"encryption_or_transform\"");
				rejectMessagePreparation();
				return;
			}
			msgToSend = finalIntext.toString();
			if (phoneText) phoneTrace("GGWAVE_TX",
					"stage=encryption_complete op=" + phoneOperationId + " chunks=" + encryptedChunkCount + " transformedUtf8Bytes="
							+ msgToSend.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " algorithm="
							+ (tmpConfig.getEncryptionType() == null ? "unknown" : tmpConfig.getEncryptionType().replaceAll("[^A-Za-z0-9_-]", "other")));
		}
		if (phoneText && actionType == ActionType.TEXT) GgWaveDiagnostics.content(this, "phone.tx.prepare", phoneOperationId, "postEncryption", msgToSend);

		// STAR: SENDER
		if (!GS.gI().isReceivingForm())
		{
			// The service binds completion to the selected contact and attached UI.
			String prepared = msgToSend + "§";
			try
			{
				MessageEncryptionMetadata.Snapshot metadata = encryptSelection ? legacyTxMetadata(ledger) : MessageEncryptionMetadata.plaintext(contact.getId(), 0);
				int phoneType = actionType == ActionType.KEY ? TextMessage.MY_KEY : actionType == ActionType.IMAGE ? TextMessage.MY_IMAGE : TextMessage.MY_TEXT;
				PendingStoredTx candidate = new PendingStoredTx(msgToSend, phoneText ? phoneType : TextMessage.MY_TEXT, metadata, pendingSelection);
				if (phoneText) phoneTrace("GGWAVE_TX", "stage=prepared op=" + phoneOperationId + " preparedUtf8Bytes="
						+ prepared.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " includesTerminator=true encrypted=" + encryptSelection);
				if (phoneText) GgWaveDiagnostics.event(this, "phone.tx.prepared", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(),
						"\"preparedBytes\":" + prepared.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + ",\"terminator\":true");
				if (phoneText)
				{
					pendingPhonePrepared = prepared;
					pendingPhoneVisibleText = visiblePhoneText;
					pendingPhoneOperationId = phoneOperationId;
				}
				if (phoneText)
				{
					processor.sendPhoneText(contact.getId(), prepared, phoneOperationId, encryptSelection, tmpConfig.getEncryptionType());
					pendingPhoneTx = candidate;
					send.setImageResource(R.drawable.stop);
				}
				else
				{
					processor.sendMessage(contact.getId(), prepared);
					pendingRadioTx = candidate;
				}
				if (phoneText) phoneTrace("GGWAVE_TX", "stage=submit op=" + phoneOperationId + " result=accepted");
			}
			catch (IllegalArgumentException | IllegalStateException rejected)
			{
				if (phoneText)
				{
					phoneTrace("GGWAVE_TX", "stage=submit op=" + phoneOperationId + " result=rejected exception=" + rejected.getClass().getSimpleName() + " preparedBytes="
							+ (prepared == null ? -1 : prepared.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
					pendingPhonePrepared = null;
					pendingPhoneVisibleText = null;
					pendingPhoneOperationId = 0;
					pendingPhoneTx = null;
					GgWaveDiagnostics.event(this, "phone.tx.admission", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(),
							"\"outcome\":\"rejected\",\"reason\":\"admission_or_payload\"");
				}
				rejectMessagePreparation();
				return;
			}

			// Processor.TX_Text += (msgToSend + "§");
			// Modem.txData("", "", msgToSend + "§", 0, 0, false, "");
		}
		else
		{
			Toast.makeText(this, getString(R.string.txt_NotInMiddleOfMessage), Toast.LENGTH_SHORT).show();
			if (phoneText) return; // No GGWave worker was queued; do not display false TX progress.
		}
		if (phoneText && GS.gI().isReceivingForm())
			GgWaveDiagnostics.event(this, "phone.tx.admission", phoneOperationId, "tx", tmpConfig.getGgWaveSpeed(), "\"outcome\":\"rejected\",\"reason\":\"receive_in_progress\"");
		if (phoneText && GS.gI().isReceivingForm()) phoneTrace("GGWAVE_TX", "stage=submit op=" + phoneOperationId + " result=rejected reason=receive_in_progress");
		/* Radio payload logging suppressed. */
		// Clear the text field
		// etMessage.setEnabled(false);

		if (phoneText) txProgress.setProgress(0);
		txProgress.setVisibility(View.VISIBLE);
		if (!phoneText) handler.post(showTxProgress);
	}

	private int modem73UnlockedKeyId, modem73KeyContactId, pendingModem73KeyId;
	private String modem73Secret, modem73WrappedKey;
	private Key requireModem73Key()
	{
		Key selected = keyDataSource.getDefaultKey(contact.getId());
		if (selected == null || selected.getId() <= 0 || selected.getKeyText() == null || selected.getId() != modem73UnlockedKeyId || modem73KeyContactId != contact.getId()
				|| modem73Secret == null || modem73Secret.isEmpty() || !selected.getKeyText().equals(modem73WrappedKey))
			throw new IllegalStateException("Unlock this contact's current default key in chat before encrypted MODEM73 use");
		if (pendingSelection == null || !pendingSelection.matchesModem73KeyContext(Modem73Crypto.keyContext(selected.getId(), selected.getKeyText())))
			throw new IllegalStateException("MODEM73 contact key changed; return to this chat to apply the new selection");
		return selected;
	}

	private String decryptModem73(Modem73Packet.Received record)
	{
		if (!record.encrypted) return record.stored;
		if (!tmpConfig.isEncryption()) throw new IllegalArgumentException("Encrypted MODEM73 reception requires an encrypted selection");
		requireModem73Key();
		return Modem73Crypto.decrypt(record.secret, record.algorithm, modem73Secret);
	}

	private EncryptedMessageHistory.Row historyRow(TextMessage message)
	{
		return new EncryptedMessageHistory.Row(message.getId(), message.getContactId(), message.getKeyId(), message.isEncrypted(), message.getMessage(),
				message.getEncryptionAlgorithm(), message.getCiphertextFormat(), message.getCiphertextVersion(), message.getEncryptionParameters());
	}

	private boolean messageStorageReady()
	{
		try
		{
			messagesDataSource.requireMessageMetadataSchema();
			return true;
		}
		catch (com.SCVA.db.SqliteHelper.UnsupportedMessageSchemaException unsupported)
		{
			Toast.makeText(this, unsupported.getMessage(), Toast.LENGTH_LONG).show();
			return false;
		}
	}

	private void rejectMessagePreparation()
	{
		if (!isPhoneConfiguration() && !isModem73Configuration() && processor != null && contact != null && processor.matchesSelection(pendingSelection)
				&& !processor.backendBusy(contact.getId()))
			TruSDXCat.getInstance().pttOff();
		Toast.makeText(this, "Message preparation/admission failed; check the selected contact key and encryption settings.", Toast.LENGTH_LONG).show();
	}

	private boolean storeMessage(TextMessage message)
	{
		try
		{
			messagesDataSource.create(message);
			return true;
		}
		catch (com.SCVA.db.SqliteHelper.UnsupportedMessageSchemaException unsupported)
		{
			Toast.makeText(this, unsupported.getMessage(), Toast.LENGTH_LONG).show();
			return false;
		}
		catch (RuntimeException unavailable)
		{
			Utils.println("MESSAGE_STORAGE", "result=failed category=" + unavailable.getClass().getSimpleName());
			Toast.makeText(this, "Message was not saved: metadata, storage or referenced contact key is unavailable.", Toast.LENGTH_LONG).show();
			return false;
		}
	}

	private MessageEncryptionMetadata.Snapshot legacyTxMetadata(MessageEncryptionMetadata.ChunkLedger ledger)
	{
		if (contact == null || legacySecret == null || legacyUnlockedContactId != contact.getId()) throw new IllegalStateException("Unlock this contact's encryption key first");
		Key bound = null;
		for (Key candidate : keyDataSource.getUserKeys(contact.getId()))
			if (candidate.getId() == legacyUnlockedKeyId && java.util.Objects.equals(candidate.getKeyText(), legacyWrapped)) bound = candidate;
		if (bound == null) throw new IllegalStateException("The prepared encryption key is unavailable");
		return MessageEncryptionMetadata.chunks(contact.getId(), bound.getId(), MessageEncryptionMetadata.algorithmOf(encryptor), ledger.parameters());
	}

	private boolean legacyTxKeyReady()
	{
		key = keyDataSource.getDefaultKey(contact.getId());
		if (key == null)
		{
			Toast.makeText(this, "Choose this contact's encryption key first.", Toast.LENGTH_LONG).show();
			return false;
		}
		if (legacySecret != null && legacyUnlockedContactId == contact.getId() && legacyUnlockedKeyId == key.getId() && java.util.Objects.equals(legacyWrapped, key.getKeyText()))
		{
			String algorithm = isPhoneConfiguration() ? tmpConfig.getEncryptionType() : GS.gI().getValue().getString("enc_algorithm", "");
			if (encryptor == null || !MessageEncryptionMetadata.algorithmOf(encryptor).equals(algorithm)) initEncryption();
			if (encryptor != null) return true;
		}
		encryptor = null;
		showPasswordDialog();
		return false;
	}

	private boolean acceptRadioMetadataChunk(String chunk)
	{
		if (radioReceiveContext != null && !processor.matchesMessageContext(radioReceiveContext))
		{
			bufferText = "";
			radioReceiveContext = null;
		}
		if (chunk != null && !chunk.isEmpty() && radioReceiveContext == null)
		{
			try
			{
				radioReceiveContext = processor.captureMessageContext(contact.getId(), pendingSelection);
				Key configured = keyDataSource.getDefaultKey(contact.getId());
				radioReceiveKeyId = configured == null ? 0 : configured.getId();
			}
			catch (RuntimeException stale)
			{
				bufferText = "";
				radioReceiveContext = null;
				return false;
			}
		}
		return true;
	}

	private boolean storeRadioReceived(TextMessage row, boolean keyTransfer, boolean image)
	{
		try
		{
			if (radioReceiveContext == null || !processor.matchesMessageContext(radioReceiveContext)) throw new IllegalStateException("Stale receive context");
			boolean encrypted = !keyTransfer && radioReceiveContext.selection.configuration().getId() != 9 && radioReceiveContext.selection.configuration().isEncryption()
					&& MessageEncryptionMetadata.looksLikeChunks(row.getMessage());
			MessageEncryptionMetadata.Snapshot metadata;
			if (encrypted)
			{
				metadata = radioReceiveContext.metadata;
				if (metadata == null || !metadata.encrypted) throw new IllegalStateException("Receiving key context unavailable");
			}
			else
				metadata = MessageEncryptionMetadata.plaintext(contact.getId(),
						radioReceiveContext.selection.configuration().getId() != 9 && (keyTransfer || image || radioReceiveContext.selection.configuration().isEncryption())
								? radioReceiveKeyId
								: 0);
			metadata.applyTo(row);
			return storeMessage(row);
		}
		catch (RuntimeException unavailable)
		{
			Toast.makeText(this, "Received message was not saved: metadata/key or selection context unavailable.", Toast.LENGTH_LONG).show();
			return false;
		}
		finally
		{
			radioReceiveContext = null;
			bufferText = "";
		}
	}

	private java.util.List<EncryptedMessageHistory.KeyRef> historyKeyReferences(java.util.List<Key> keys)
	{
		java.util.List<EncryptedMessageHistory.KeyRef> refs = new ArrayList<>();
		if (contact != null) for (Key item : keys)
			if (item.getUserGroupId() == contact.getId()) refs.add(new EncryptedMessageHistory.KeyRef(item.getId(), item.getUserGroupId(), item.getKeyText()));
		return refs;
	}

	private EncryptedMessageHistory.Result displayEncryptedHistory(TextMessage message)
	{
		return historySession.display(historyRow(message), historyKeys);
	}

	private void refreshEncryptedHistory()
	{
		boolean previous = historyOnlyRefresh;
		historyOnlyRefresh = true;
		try
		{
			populate.run();
		}
		finally
		{
			historyOnlyRefresh = previous;
		}
	}

	private void rememberHistoryUnlock(Key selected, String material)
	{
		if (contact == null || selected == null || selected.getUserGroupId() != contact.getId()) return;
		java.util.List<EncryptedMessageHistory.KeyRef> live = historyKeyReferences(keyDataSource.getUserKeys(contact.getId()));
		historySession.unlock(historySession.token(), new EncryptedMessageHistory.KeyRef(selected.getId(), selected.getUserGroupId(), selected.getKeyText()), material, live);
	}

	private boolean historyContextCurrent(long token, long generation, int contactId)
	{
		return chatResumed && contact != null && contact.getId() == contactId && generation == uiGeneration && historySession.current(token, contactId);
	}

	private void openEncryptedHistory(EncryptedMessageHistory.Row row)
	{
		final long token = historySession.token(), generation = uiGeneration;
		if (!historyContextCurrent(token, generation, row.contactId)) return;
		EncryptedMessageHistory.Result result = historySession.display(row, historyKeys);
		if (row.metadataPresent)
		{
			if (result.status == EncryptedMessageHistory.Status.LOCKED_KEY)
			{
				Key referenced = null;
				for (Key candidate : keyDataSource.getUserKeys(row.contactId))
					if (candidate.getId() == row.keyId && candidate.getUserGroupId() == row.contactId) referenced = candidate;
				if (referenced != null)
					showHistoryPassword(row, referenced, new EncryptedMessageHistory.KeyRef(referenced.getId(), row.contactId, referenced.getKeyText()), token, generation);
			}
			else if (!result.opened()) new AlertDialog.Builder(this).setTitle("Message history").setMessage(result.display()).setPositiveButton("OK", null).show();
			return;
		}
		if (result.status == EncryptedMessageHistory.Status.MALFORMED_RECORD || result.status == EncryptedMessageHistory.Status.UNSUPPORTED_FORMAT)
		{
			new AlertDialog.Builder(this).setTitle("Encrypted history").setMessage(result.display()).setPositiveButton("OK", null).show();
			return;
		}
		if (EncryptedMessageHistory.isModern(row))
		{
			chooseHistoryKey(row, token, generation);
			return;
		}
		final String[] labels =
		{ "AES", "RC6", "Twofish", "Serpent (legacy read only)", "GOST28147", "Blowfish (legacy read only)" };
		final String[] algorithms =
		{ "aes", "rc6", "twofish", "serpent", "gost28147", "blowfish" };
		int checked = java.util.Arrays.asList(algorithms).indexOf(historySession.algorithm(row));
		new AlertDialog.Builder(this).setTitle("Legacy history algorithm — history only").setSingleChoiceItems(labels, checked, (dialog, which) -> {
			dialog.dismiss();
			if (!historyContextCurrent(token, generation, row.contactId) || !historySession.selectLegacyAlgorithm(token, row, algorithms[which])) return;
			Toast.makeText(this, "History only; transmission settings are unchanged. Legacy padding may be unrecoverable for Serpent, GOST or Blowfish.", Toast.LENGTH_LONG).show();
			refreshEncryptedHistory();
			chooseHistoryKey(row, token, generation);
		}).setNegativeButton("CANCEL", null).show();
	}

	private void chooseHistoryKey(EncryptedMessageHistory.Row row, long token, long generation)
	{
		if (!historyContextCurrent(token, generation, row.contactId)) return;
		final ArrayList<Key> available = keyDataSource.getUserKeys(row.contactId);
		for (int i = available.size() - 1; i >= 0; i--)
			if (available.get(i).getUserGroupId() != row.contactId) available.remove(i);
		if (available.isEmpty())
		{
			Toast.makeText(this, "No keys for this contact. Add or import the matching key, then reopen history.", Toast.LENGTH_LONG).show();
			return;
		}
		String[] names = new String[available.size()];
		int checked = -1;
		for (int i = 0; i < available.size(); i++)
		{
			Key k = available.get(i);
			names[i] = k.getName() + (k.getId() == row.keyId ? " (message reference)" : "") + (k.isDefault() ? " (current default)" : "");
			if (k.getId() == row.keyId) checked = i;
		}
		new AlertDialog.Builder(this).setTitle("Choose contact key — history only").setSingleChoiceItems(names, checked, (dialog, which) -> {
			dialog.dismiss();
			if (!historyContextCurrent(token, generation, row.contactId)) return;
			Key selected = available.get(which);
			EncryptedMessageHistory.KeyRef reference = new EncryptedMessageHistory.KeyRef(selected.getId(), selected.getUserGroupId(), selected.getKeyText());
			historyKeys = historyKeyReferences(keyDataSource.getUserKeys(row.contactId));
			if (!historySession.selectKey(token, row, reference, historyKeys)) return;
			if (historySession.display(row, historyKeys).status == EncryptedMessageHistory.Status.LOCKED_KEY) showHistoryPassword(row, selected, reference, token, generation);
			else
				runOnUiThread(this::refreshEncryptedHistory);
		}).setNegativeButton("CANCEL", null).show();
	}

	private void showHistoryPassword(EncryptedMessageHistory.Row row, Key selected, EncryptedMessageHistory.KeyRef reference, long token, long generation)
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.reveal_key_dialog);
		dialog.setCancelable(false);
		((TextView) dialog.findViewById(R.id.keyName)).setText(selected.getName());
		dialog.findViewById(R.id.cancel).setOnClickListener(v -> dialog.dismiss());
		dialog.findViewById(R.id.unlock).setOnClickListener(v -> {
			dialog.dismiss();
			if (!historyContextCurrent(token, generation, row.contactId)) return;
			String material = null;
			try
			{
				material = selected.getPlainKey(((EditText) dialog.findViewById(R.id.password)).getText().toString());
			}
			catch (RuntimeException unavailable)
			{
				/* Report no key/password/provider details. */ }
			historyKeys = historyKeyReferences(keyDataSource.getUserKeys(row.contactId));
			if (!historySession.unlock(token, reference, material, historyKeys))
			{
				Toast.makeText(this, "History key could not be unlocked or the contact/key changed.", Toast.LENGTH_LONG).show();
				return;
			}
			runOnUiThread(this::refreshEncryptedHistory);
		});
		dialog.show();
	}

	private long pendingModem73OperationId;
	private String pendingModem73VisibleText;
	private Uri pendingModem73ImageUri;
	private int pendingModem73ImageSide = 96;
	private void sendModem73Key()
	{
		if (msgToSend == null || msgToSend.trim().isEmpty())
		{
			Toast.makeText(this, "Select and unlock a key first.", Toast.LENGTH_SHORT).show();
			return;
		}
		sendModem73Content(Modem73KeyPayload.wire(msgToSend), TextMessage.MY_KEY);
	}

	private void sendModem73Image()
	{
		if (!messageStorageReady() || pendingModem73ImageUri == null)
		{
			Toast.makeText(this, "Select an image first.", Toast.LENGTH_SHORT).show();
			return;
		}
		try
		{
			if (tmpConfig.isEncryption()) requireModem73Key();
			sendModem73Content(fitModem73Image(pendingModem73ImageUri), TextMessage.MY_IMAGE);
		}
		catch (RuntimeException unavailable)
		{
			Toast.makeText(this, unavailable.getMessage(), Toast.LENGTH_LONG).show();
			if (tmpConfig.isEncryption())
			{
				Key current = keyDataSource.getDefaultKey(contact.getId());
				if (current != null && (modem73Secret == null || modem73UnlockedKeyId != current.getId() || modem73KeyContactId != contact.getId()
						|| !java.util.Objects.equals(current.getKeyText(), modem73WrappedKey)))
				{
					key = current;
					showPasswordDialog();
				}
			}
		}
	}

	private String fitModem73Image(Uri uri)
	{
		for (int side = pendingModem73ImageSide; side >= 12; side = side * 3 / 4)
		{
			Bitmap source = utils.createThumbnailFromUri(this, uri, side, side);
			if (source == null) break;
			Bitmap scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.min(side, source.getWidth())), Math.max(1, Math.min(side, source.getHeight())), true);
			for (int quality : new int[]
			{ 65, 45, 25, 12 })
			{
				java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
				if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)) continue;
				String candidate = android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP);
				try
				{
					if (tmpConfig.isEncryption()) Modem73Crypto.prepare(tmpConfig.getModem73Callsign(), tmpConfig.getEncryptionType(), modem73Secret, candidate);
					else if (5 + tmpConfig.getModem73Callsign().length() + candidate.length() > 2020) throw new IllegalArgumentException("Image exceeds MODEM73 record");
					return candidate;
				}
				catch (IllegalArgumentException tooLarge)
				{
					if (tmpConfig.isEncryption() && !tooLarge.getMessage().contains("exceeds 2020 bytes")) throw tooLarge;
				}
			}
		}
		throw new IllegalArgumentException("Image cannot fit one MODEM73 record. Choose a simpler image.");
	}

	private void sendModem73Text()
	{
		if (!messageStorageReady()) return;
		Utils.hideKeyboard(this, etMessage);
		String text = etMessage.getText().toString();
		sendModem73Content(text, TextMessage.MY_TEXT);
	}

	private void sendModem73Content(String text, int messageType)
	{
		if (text.length() == 0)
		{
			Toast.makeText(this, "Type some text to send", Toast.LENGTH_SHORT).show();
			return;
		}
		try
		{
			final BackendSelection preparedSelection = pendingSelection;
			if (!processor.matchesSelection(preparedSelection)) throw new IllegalStateException("MODEM73 selection changed; resume the matching chat");
			long operation;
			int localKey = 0;
			String historyStored = text;
			MessageEncryptionMetadata.Snapshot metadata;
			PendingStoredTx candidate;
			if (tmpConfig.isEncryption())
			{
				localKey = requireModem73Key().getId();
				byte[] record = Modem73Crypto.prepare(tmpConfig.getModem73Callsign(), tmpConfig.getEncryptionType(), modem73Secret, text);
				historyStored = Modem73Crypto.parse(record).stored();
				metadata = MessageEncryptionMetadata.modem73(contact.getId(), localKey, Modem73Crypto.parse(record).algorithm);
				candidate = new PendingStoredTx(historyStored, messageType, metadata, preparedSelection);
				operation = processor.sendModem73Record(contact.getId(), preparedSelection, record);
			}
			else
			{
				metadata = MessageEncryptionMetadata.plaintext(contact.getId(), 0);
				candidate = new PendingStoredTx(text, messageType, metadata, preparedSelection);
				operation = processor.sendModem73Text(contact.getId(), text);
			}
			pendingModem73Tx = candidate;
			pendingModem73KeyId = localKey;
			pendingModem73OperationId = operation;
			pendingModem73VisibleText = text;
			send.setImageResource(R.drawable.stop);
			txProgress.setProgress(0);
			txProgress.setVisibility(View.VISIBLE);
		}
		catch (RuntimeException rejected)
		{
			Toast.makeText(this, rejected.getMessage(), Toast.LENGTH_LONG).show();
			if (tmpConfig.isEncryption())
			{
				Key current = keyDataSource.getDefaultKey(contact.getId());
				if (current != null && (modem73Secret == null || modem73UnlockedKeyId != current.getId() || modem73KeyContactId != contact.getId()
						|| !java.util.Objects.equals(current.getKeyText(), modem73WrappedKey)))
				{
					key = current;
					showPasswordDialog();
				}
			}
		}
	}

	public void sendKey()
	{
		if (!messageStorageReady()) return;
		Utils.hideKeyboard(this, etMessage);
		// STAR: SENDER
		if (!GS.gI().isReceivingForm())
		{
			// The service binds completion to the selected contact and attached UI.
			Key referenced = keyDataSource.getDefaultKey(contact.getId());
			PendingStoredTx candidate = new PendingStoredTx(msgToSend, TextMessage.MY_KEY,
					MessageEncryptionMetadata.plaintext(contact.getId(), referenced == null ? 0 : referenced.getId()), pendingSelection);
			processor.sendKey(contact.getId(), msgToSend.trim() + "√");
			pendingRadioTx = candidate;
		}
		else
		{
			Toast.makeText(this, getString(R.string.txt_NotInMiddleOfMessage), Toast.LENGTH_SHORT).show();
		}
		/* Utils.println(Processor.TX_Text); // Message/key contents: no default logging. */
		txProgress.setVisibility(View.VISIBLE);
		handler.post(showTxProgress);
	}

	Runnable showTxProgress = new Runnable()
	{
		@Override
		public void run()
		{
			if (isPhoneConfiguration()) return;
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
		final long passwordGeneration = uiGeneration;
		final int passwordContact = contact.getId(), passwordKeyId = key.getId();
		final String passwordWrapped = key.getKeyText();
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
				if (!chatResumed || contact == null || passwordGeneration != uiGeneration || contact.getId() != passwordContact) return;
				Key currentPasswordKey = keyDataSource.getDefaultKey(passwordContact);
				if (currentPasswordKey == null || currentPasswordKey.getId() != passwordKeyId || !java.util.Objects.equals(currentPasswordKey.getKeyText(), passwordWrapped))
				{
					Toast.makeText(UserChatActivity.this, "Contact key changed; reopen the unlock dialog.", Toast.LENGTH_LONG).show();
					return;
				}
				if (isModem73Configuration())
				{
					Key selectedKey = keyDataSource.getDefaultKey(contact.getId());
					String secret = selectedKey == null ? null : selectedKey.getPlainKey(((EditText) dialog.findViewById(R.id.password)).getText().toString());
					if (secret == null || secret.isEmpty())
					{
						modem73Secret = null;
						modem73UnlockedKeyId = 0;
						Toast.makeText(UserChatActivity.this, "Key could not be unlocked.", Toast.LENGTH_LONG).show();
						runOnUiThread(populate);
						return;
					}
					modem73Secret = secret;
					modem73WrappedKey = selectedKey.getKeyText();
					modem73UnlockedKeyId = selectedKey.getId();
					modem73KeyContactId = contact.getId();
					key = selectedKey;
					chat.setKey(key);
					rememberHistoryUnlock(selectedKey, secret);
					GS.gI().setKeyId(selectedKey.getId());
					GS.gI().setPlainKey(secret);
					initEncryption();
					return;
				}
				key = keyDataSource.getDefaultKey(chat.getContactId());
				GS.gI().setKeyId(key.getId());
				GS.gI().setPlainKey(key.getPlainKey(((EditText) dialog.findViewById(R.id.password)).getText().toString()));

				if (GS.gI().getPlainKey().length() > 0)
				{
					legacySecret = GS.gI().getPlainKey();
					legacyWrapped = key.getKeyText();
					legacyUnlockedKeyId = key.getId();
					legacyUnlockedContactId = contact.getId();
					rememberHistoryUnlock(key, GS.gI().getPlainKey());
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
				if (!chatResumed || contact == null || passwordGeneration != uiGeneration || contact.getId() != passwordContact) return;
				runOnUiThread(UserChatActivity.this::refreshEncryptedHistory); // History unlock is separate from TX.
			}
		});
		dialog.show();
	}

	void initEncryption()
	{
		if (isModem73Configuration())
		{
			runOnUiThread(populate);
			return;
		}
		if (isPhoneConfiguration() ? tmpConfig.isEncryption() : Utils.getValue(this).getBoolean("use_enc", false))
		{
			String algo = isPhoneConfiguration() ? tmpConfig.getEncryptionType() : Utils.getValue(this).getString("enc_algorithm", "");
			String key = legacySecret;
			Key selected = keyDataSource.getDefaultKey(contact.getId());
			encryptor = null;
			if (key != null && !key.isEmpty() && selected != null && selected.getId() == legacyUnlockedKeyId && legacyUnlockedContactId == contact.getId()
					&& java.util.Objects.equals(selected.getKeyText(), legacyWrapped))
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
		if (isPhoneConfiguration() || isModem73Configuration()) return; // Phone backends own their TX outcome and PTT lifecycle.
		if (processor == null || !processor.matchesSelection(pendingSelection))
		{
			TruSDXCat.getInstance().pttOff();
			return;
		}
		if (type.equalsIgnoreCase("PROGRESS_HIDE"))
		{
			pendingRadioTx = null;
			clear.performClick();
			return;
		}
		TruSDXCat.getInstance().pttOff();
		handler.removeCallbacks(showTxProgress);
		PendingStoredTx completed = pendingRadioTx;
		pendingRadioTx = null;
		if (completed == null || !processor.matchesSelection(completed.selection) || completed.metadata.contactId != contact.getId()) return;
		if (completed.type == TextMessage.MY_TEXT)
		{
			if (completed.selection.configuration().getId() == 9)
			{
				TextMessage textMessage = new TextMessage();
				textMessage.setType(TextMessage.MY_TEXT);
				textMessage.setMessage(completed.stored);
				textMessage.setContactId(contact.getId());
				textMessage.setKeyId(0);
				textMessage.setEncrypted(false);
				textMessage.setDate("" + System.currentTimeMillis());
				completed.metadata.applyTo(textMessage);
				if (!storeMessage(textMessage)) return;
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						etMessage.setText("");
					}
				});

				chat.setDate("" + System.currentTimeMillis());
				chat.setMessage(completed.stored);
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
				postRadioUi(populate);
				return;
			}
			else
			{
				TextMessage textMessage = new TextMessage();
				textMessage.setType(TextMessage.MY_TEXT);
				textMessage.setMessage(completed.stored);
				textMessage.setContactId(contact.getId());
				textMessage.setDate("" + System.currentTimeMillis());
				completed.metadata.applyTo(textMessage);
				if (!storeMessage(textMessage)) return;
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
		if (completed.type == TextMessage.MY_KEY)
		{
			TextMessage textMessage = new TextMessage();
			textMessage.setMessage(completed.stored);
			textMessage.setType(TextMessage.MY_KEY);
			textMessage.setContactId(contact.getId());

			textMessage.setDate("" + System.currentTimeMillis());

			completed.metadata.applyTo(textMessage);
			if (!storeMessage(textMessage)) return;
			msgToSend = "";

			chat.setDate("" + System.currentTimeMillis());
			chat.setMessage("Sent a key");
			chatsDataSource.update(chat);
			postRadioUi(clearInput());
		}
		if (completed.type == TextMessage.MY_IMAGE)
		{
			TextMessage textMessage = new TextMessage();
			textMessage.setMessage(completed.stored);
			textMessage.setType(TextMessage.MY_IMAGE);
			textMessage.setContactId(contact.getId());
			textMessage.setDate("" + System.currentTimeMillis());
			completed.metadata.applyTo(textMessage);
			if (!storeMessage(textMessage))
			{
				if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
				restoreRadioConfiguration();
				return;
			}
			msgToSend = "";
			postRadioUi(clearInput());
			// Always release the lock when your task finishes.
			if (wakeLock != null && wakeLock.isHeld())
			{
				wakeLock.release();
			}
			restoreRadioConfiguration();
			// disable TXRSID after image is sent

			processor.setRsid(contact.getId(), false, false);
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
		postRadioUi(populate);
	}

	private boolean isPhoneConfiguration()
	{
		return tmpConfig != null && "GGWave".equals(tmpConfig.getModulationType());
	}

	private boolean isModem73Configuration()
	{
		return tmpConfig != null && "MODEM73".equals(tmpConfig.getModulationType());
	}

	private static void phoneTrace(String tag, String message)
	{
		try
		{
			Utils.println(tag, message);
		}
		catch (RuntimeException ignored)
		{
		}
	}

	private void persistPhoneReceived(long operationId)
	{
		if (!chatResumed || !isPhoneConfiguration() || contact == null || processor == null || !processor.matchesSelection(pendingSelection))
		{
			phoneTrace("GGWAVE_RX", "stage=chat_persist op=" + operationId + " result=stale_or_unavailable_context");
			return;
		}
		MessageEncryptionMetadata.Delivery<GgWaveBackend.ReceivedText> delivery = processor.consumePhoneMessage(operationId);
		GgWaveBackend.ReceivedText received = delivery == null ? null : delivery.content;
		if (received == null || received.format == GgWaveBackend.ReceivedText.Format.REJECTED)
		{
			phoneTrace("GGWAVE_RX", "stage=chat_persist op=" + operationId + " result=processor_rejected");
			return;
		}
		String text = received.text();
		if (text == null || received.format == GgWaveBackend.ReceivedText.Format.SCVA_PREPARED && !text.endsWith("§"))
		{
			phoneTrace("GGWAVE_RX", "stage=chat_persist op=" + operationId + " result=invalid_application_message");
			return;
		}
		if (delivery.metadata == null || delivery.metadata.contactId != contact.getId()) return;
		boolean encrypted = delivery.metadata.encrypted;

		TextMessage message = new TextMessage();
		// § closes the prepared over-air message; keep it out of the stored
		// ciphertext so the existing ¢-chunk decryptor sees only encoded chunks.
		String storedText = received.format == GgWaveBackend.ReceivedText.Format.SCVA_PREPARED && text.endsWith("§") ? text.substring(0, text.length() - 1) : text;
		phoneTrace("GGWAVE_RX",
				"stage=chat_persist op=" + operationId + " result=storing format=" + received.format + " receivedBytes=" + received.bytes().length + " storedUtf8Bytes="
						+ storedText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " encrypted=" + encrypted + " terminatorRemoved="
						+ (storedText.length() != text.length()));
		boolean sharedKey = Modem73KeyPayload.isKey(storedText);
		message.setType(sharedKey ? TextMessage.HIS_KEY : !encrypted && Modem73ImagePayload.isImage(storedText, 4096) ? TextMessage.HIS_IMAGE : TextMessage.HIS_TEXT);
		message.setMessage(storedText);
		message.setEncrypted(encrypted);
		message.setContactId(contact.getId());
		delivery.metadata.applyTo(message);
		message.setDate(String.valueOf(System.currentTimeMillis()));
		String format = received.format == GgWaveBackend.ReceivedText.Format.RAW_PLAINTEXT ? "raw_plaintext_delivery" : "prepared_message_delivery";
		GgWaveDiagnostics.event(this, "phone.rx.delivery", operationId, "rx", tmpConfig.getGgWaveSpeed(),
				"\"outcome\":\"" + format + "\",\"textBytes\":" + received.bytes().length);
		phoneReceivedLogRows.put(message.getDate(), operationId);
		while (phoneReceivedLogRows.size() > 64)
			phoneReceivedLogRows.remove(phoneReceivedLogRows.keySet().iterator().next());
		if (!storeMessage(message)) return;
		phoneTrace("GGWAVE_RX", "stage=storage_api op=" + operationId + " result=called durable=false");
		GgWaveDiagnostics.event(this, "phone.rx.delivery", operationId, "rx", tmpConfig.getGgWaveSpeed(), "\"outcome\":\"store_api_called\",\"encrypted\":" + encrypted);
		chat.setDate(String.valueOf(System.currentTimeMillis()));
		chat.setMessage(sharedKey ? "Received a key" : "Received a Phone message");
		chatsDataSource.update(chat);
		GgWaveDiagnostics.event(this, "phone.rx.delivery", operationId, "rx", tmpConfig.getGgWaveSpeed(), "\"outcome\":\"chat_refresh_requested\"");
		postRadioUi(populate);
		phoneTrace("GGWAVE_RX", "stage=chat_refresh op=" + operationId + " result=requested");
	}

	private void persistPhoneSent(String prepared)
	{
		PendingStoredTx completed = pendingPhoneTx;
		if (completed == null || processor == null || !processor.matchesSelection(completed.selection)) return;
		if (!chatResumed || !isPhoneConfiguration() || contact == null || processor == null || !processor.matchesSelection(pendingSelection) || prepared == null
				|| !prepared.equals(pendingPhonePrepared))
			return;
		// Completion is a playback result; hide the activity indicator before local persistence work.
		send.setImageResource(R.drawable.send);
		txProgress.setProgress(0);
		txProgress.setVisibility(View.GONE);
		boolean encrypted = completed.metadata.encrypted;
		// § frames the over-air/application-prepared message; it is not chat content.
		String storedText = prepared.endsWith("§") ? prepared.substring(0, prepared.length() - 1) : prepared;
		TextMessage message = new TextMessage();
		message.setType(completed.type);
		if (!storedText.equals(completed.stored)) return;
		pendingPhoneTx = null;
		message.setMessage(completed.stored);
		message.setEncrypted(encrypted);
		message.setContactId(contact.getId());
		completed.metadata.applyTo(message);
		message.setDate(String.valueOf(System.currentTimeMillis()));
		GgWaveDiagnostics.event(this, "phone.tx.audio", pendingPhoneOperationId, "tx", tmpConfig.getGgWaveSpeed(), "\"outcome\":\"local_store_api_called\",\"peerDelivery\":false");
		if (!storeMessage(message))
		{
			pendingPhonePrepared = null;
			pendingPhoneVisibleText = null;
			pendingPhoneOperationId = 0;
			return;
		}
		phoneTrace("GGWAVE_TX", "stage=local_storage_api op=" + pendingPhoneOperationId + " result=called durable=false preparedBytes="
				+ prepared.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " encrypted=" + encrypted);
		chat.setDate(String.valueOf(System.currentTimeMillis()));
		chat.setMessage(completed.type == TextMessage.MY_KEY ? "Sent a key" : "Played GGWave message");
		chatsDataSource.update(chat);
		if (completed.type == TextMessage.MY_IMAGE || completed.type == TextMessage.MY_KEY) runOnUiThread(clearInput());
		else if (etMessage.getText().toString().equals(pendingPhoneVisibleText)) etMessage.setText("");
		pendingPhonePrepared = null;
		pendingPhoneVisibleText = null;
		pendingPhoneOperationId = 0;
		pendingPhoneTx = null;
		// Toast.makeText(this, "GGWave playback finished; peer delivery is not confirmed.", Toast.LENGTH_LONG).show();
		postRadioUi(populate);
	}

	private void persistModem73Received(long operationId)
	{
		if (!chatResumed || !isModem73Configuration() || contact == null || processor == null || !processor.matchesSelection(pendingSelection))
		{
			if (processor != null) processor.modem73UiDiagnostic("chat_delivery_rejected", operationId, "reason=activity_or_selection_context_stale");
			return;
		}
		MessageEncryptionMetadata.Delivery<Modem73Packet.Received> delivery = processor.consumeModem73Message(operationId);
		Modem73Packet.Received record = delivery == null ? null : delivery.content;
		if (delivery != null && (delivery.metadata == null || delivery.metadata.contactId != contact.getId())) return;
		String received = record == null ? null : record.stored;
		String displayContent = received;
		int localKey = 0;
		if (record != null && record.encrypted)
		{
			try
			{
				localKey = requireModem73Key().getId();
				if (localKey != delivery.metadata.keyId) throw new IllegalStateException("Receiving key changed");
				displayContent = decryptModem73(record);
				processor.modem73UiDiagnostic("application_decrypt_returned", operationId, "authenticated=false algorithm=" + record.algorithm);
			}
			catch (RuntimeException failure)
			{
				processor.modem73UiDiagnostic("application_decrypt_rejected", operationId, "reason=key_algorithm_or_decryption");
				Toast.makeText(this, "Encrypted MODEM73 message unavailable: check matching algorithm and unlocked contact key.", Toast.LENGTH_LONG).show();
				return;
			}
		}
		if (received == null)
		{
			processor.modem73UiDiagnostic("chat_delivery_rejected", operationId, "reason=processor_event_unavailable_or_stale");
			return;
		}
		processor.modem73UiDiagnostic("chat_delivery_accepted", operationId,
				"encrypted=" + record.encrypted + " storedUtf8Bytes=" + received.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
		TextMessage message = new TextMessage();
		boolean image = Modem73ImagePayload.isImage(displayContent);
		boolean sharedKey = Modem73KeyPayload.isKey(displayContent);
		message.setType(sharedKey ? TextMessage.HIS_KEY : image ? TextMessage.HIS_IMAGE : TextMessage.HIS_TEXT);
		message.setMessage(received);
		message.setEncrypted(record.encrypted);
		delivery.metadata.applyTo(message);
		message.setContactId(contact.getId());
		message.setDate(String.valueOf(System.currentTimeMillis()));
		processor.modem73UiDiagnostic("storage_api_call", operationId,
				"encrypted=" + record.encrypted + " storedBytes=" + received.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
		if (!storeMessage(message)) return;
		processor.modem73RecordStorageOutcome(false);
		processor.modem73UiDiagnostic("storage_api_returned", operationId, "api=MessagesDataSource.create durability=unconfirmed");
		chat.setDate(message.getDate());
		chat.setMessage(record.encrypted ? "encrypted msg" : sharedKey ? "Received a key" : image ? "Received an image" : received);
		chatsDataSource.update(chat);
		processor.modem73RecordStorageOutcome(true);
		processor.modem73UiDiagnostic("chat_refresh_requested", operationId, "api=ChatsDataSource.update_and_populate");
		postRadioUi(populate);
	}

	private void finishModem73Tx(long operationId, String result)
	{
		if (!isModem73Configuration() || operationId != pendingModem73OperationId || processor == null || contact == null || !processor.matchesSelection(pendingSelection)) return;
		PendingStoredTx completed = pendingModem73Tx;
		pendingModem73Tx = null;
		if (completed == null || !processor.matchesSelection(completed.selection)) return;
		String outcome = result == null ? "" : result.split(":", 2)[0];
		Modem73Packet.Received record = processor.consumeModem73Record(operationId);
		String sent = record == null ? null : record.stored;
		if (record != null && (record.encrypted != completed.metadata.encrypted || !record.stored.equals(completed.stored)
				|| record.encrypted && !record.algorithm.equals(completed.metadata.algorithm)))
			sent = null;
		send.setImageResource(R.drawable.send);
		txProgress.setProgress(0);
		txProgress.setVisibility(View.GONE);
		if ("AUDIO_COMPLETED".equals(outcome) && sent != null)
		{
			TextMessage message = new TextMessage();
			message.setType(completed.type);
			message.setMessage(sent);
			message.setEncrypted(record.encrypted);
			completed.metadata.applyTo(message);
			message.setContactId(contact.getId());
			message.setDate(String.valueOf(System.currentTimeMillis()));
			if (!storeMessage(message))
			{
				pendingModem73OperationId = 0;
				pendingModem73VisibleText = null;
				pendingModem73KeyId = 0;
				return;
			}
			chat.setDate(message.getDate());
			chat.setMessage(
					record.encrypted ? "encrypted msg" : completed.type == TextMessage.MY_KEY ? "Sent a key" : completed.type == TextMessage.MY_IMAGE ? "Sent an image" : sent);
			chatsDataSource.update(chat);
			if (completed.type == TextMessage.MY_IMAGE || completed.type == TextMessage.MY_KEY) runOnUiThread(clearInput());
			else if (etMessage.getText().toString().equals(pendingModem73VisibleText)) etMessage.setText("");
			postRadioUi(populate);
		}
		else if ("CANCELLED".equals(outcome)) runOnUiThread(clearInput());
		else
			Toast.makeText(this, "MODEM73 transmission failed; message was not saved as sent.", Toast.LENGTH_LONG).show();
		pendingModem73OperationId = 0;
		pendingModem73VisibleText = null;
		pendingModem73KeyId = 0;
	}

	@Override
	public void post(String type, String data)
	{
		if (!chatResumed) return;
		// if (type.equalsIgnoreCase("processWrapBlock"))
		// Utils.println("CallBack = " +
		// type + " | " + data);
		if (type.equalsIgnoreCase("PostToModem")) postRadioUi(addToModem);
		if (type.equalsIgnoreCase("PostPhoneText"))
		{
			try
			{
				final long operationId = Long.parseLong(data);
				postRadioUi(() -> persistPhoneReceived(operationId));
			}
			catch (NumberFormatException invalidOperation)
			{
				/* Malformed internal receive token is discarded. */ }
		}
		if (type.equalsIgnoreCase("PostModem73Rx"))
		{
			try
			{
				long id = Long.parseLong(data);
				postRadioUi(() -> persistModem73Received(id));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		if (type.equalsIgnoreCase("PostModem73Tx"))
		{
			int separator = data == null ? -1 : data.indexOf(':');
			if (separator > 0) try
			{
				long id = Long.parseLong(data.substring(0, separator));
				String outcome = data.substring(separator + 1);
				postRadioUi(() -> finishModem73Tx(id, outcome));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		if (type.equalsIgnoreCase("PostModem73Progress"))
		{
			int separator = data == null ? -1 : data.indexOf(':');
			if (separator > 0) try
			{
				long id = Long.parseLong(data.substring(0, separator));
				int percent = Integer.parseInt(data.substring(separator + 1));
				postRadioUi(() -> {
					if (isModem73Configuration() && id == pendingModem73OperationId)
					{
						txProgress.setProgress(percent);
						txProgress.setVisibility(View.VISIBLE);
					}
				});
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		if (type.equalsIgnoreCase("PostMessageMetadataError")) postRadioUi(() -> Toast.makeText(this, data, Toast.LENGTH_LONG).show());
		if (type.equalsIgnoreCase("PostModem73ReceiveError")) postRadioUi(() -> Toast.makeText(this, "MODEM73: " + data, Toast.LENGTH_LONG).show());
		if (type.equalsIgnoreCase("PostModem73Error")) postRadioUi(() -> {
			send.setImageResource(R.drawable.send);
			txProgress.setProgress(0);
			txProgress.setVisibility(View.GONE);
			String reason = data == null || data.trim().isEmpty() ? "MODEM73 audio operation failed." : "MODEM73: " + data;
			Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
		});
		if (type.equalsIgnoreCase("PostPhoneTxProgress"))
		{
			int separator = data == null ? -1 : data.indexOf(':');
			if (separator > 0)
			{
				try
				{
					final long operationId = Long.parseLong(data.substring(0, separator));
					final int percent = Integer.parseInt(data.substring(separator + 1));
					postRadioUi(() -> {
						if (!isPhoneConfiguration() || operationId != pendingPhoneOperationId) return;
						txProgress.setProgress(Math.max(0, Math.min(100, percent)));
						txProgress.setVisibility(View.VISIBLE);
					});
				}
				catch (NumberFormatException invalidProgress)
				{
					/* Ignore malformed internal progress. */ }
			}
		}
		if (type.equalsIgnoreCase("PostPhoneTxComplete")) postRadioUi(() -> persistPhoneSent(data));
		if (type.equalsIgnoreCase("PostPhoneError")) postRadioUi(() -> {
			if (pendingPhoneOperationId > 0)
			{
				send.setImageResource(R.drawable.send);
				pendingPhonePrepared = null;
				pendingPhoneVisibleText = null;
				pendingPhoneOperationId = 0;
				pendingPhoneTx = null;
			}
			txProgress.setProgress(0);
			txProgress.setVisibility(View.GONE);
			// Toast.makeText(this, data, Toast.LENGTH_LONG).show();
		});
		if (type.equalsIgnoreCase("PostPhoneCancelled")) postRadioUi(() -> {
			if (pendingPhoneOperationId > 0)
			{
				pendingPhonePrepared = null;
				pendingPhoneVisibleText = null;
				pendingPhoneOperationId = 0;
				pendingPhoneTx = null;
			}
			runOnUiThread(clearInput());
			// Toast.makeText(this, data, Toast.LENGTH_SHORT).show();
		});
		if (type.equalsIgnoreCase("BackendSelectionReady")) postRadioUi(() -> {
			finishConfigurationResume();
			if (isPhoneConfiguration() || isModem73Configuration())
			{
				showPhoneWaterfall();
				View dial = findViewById(R.id.dialView);
				if (dial != null) dial.setVisibility(View.GONE);
				if (btnAttach != null) btnAttach.setVisibility(tmpConfig.getId() != 9 ? View.VISIBLE : View.GONE);
				postRadioUi(updateTitle);
			}
			else
				displayModem(true);
		});
		if (type.equalsIgnoreCase("BackendSelectionFailed") || type.equalsIgnoreCase("BackendStopPending")) postRadioUi(() -> Toast.makeText(this, data, Toast.LENGTH_LONG).show());
		if (type.equalsIgnoreCase("updateTitle")) postRadioUi(updateTitle);
		if (type.equalsIgnoreCase("updateCPULoad")) postRadioUi(updateCPULoad);
		if (type.equalsIgnoreCase("PostToTerminal")) postRadioUi(addToTerminal);
		if (type.equalsIgnoreCase("updatewaterfall") || type.equalsIgnoreCase("PhoneSpectrum")) postRadioUi(updateWaterFall);
		if (type.equalsIgnoreCase("updateSignalQuality")) postRadioUi(updateSignalQuality);
		if (type.equalsIgnoreCase("updateWFFrequency")) postRadioUi(() -> updateWFFrequency(Double.parseDouble(data)));

		// if (type.equalsIgnoreCase("updateMfskPicture"))
		// mHandler.post(updateMfskPicture);
		// if (type.equalsIgnoreCase("updateAFCCheckBox"))
		// mHandler.post(updateAFCCheckBox);
	}

	private void postRadioUi(Runnable action)
	{
		final long generation = uiGeneration;
		final BackendSelection expected = pendingSelection;
		final Processor owner = processor;
		if (expected == null || owner == null) return;
		handler.post(() -> {
			if (generation == uiGeneration && processor == owner && pendingSelection != null && pendingSelection.sameAs(expected) && owner.hasSelectionFor(expected.contactId))
				action.run();
		});
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
			if (tmpConfig == null)
			{
				((TextView) findViewById(R.id.indicators)).setText("SCVA - Select a configured chat");
				return;
			}
			if (isPhoneConfiguration())
			{
				String protection = tmpConfig.isEncryption() ? "Encrypted" : "Unencrypted";
				((TextView) findViewById(R.id.indicators)).setText("SCVA - GGWave (48 kHz) - " + protection + "");
				return;
			}
			if (isModem73Configuration())
			{
				String protection = tmpConfig.isEncryption() ? "Encrypted" : "Unencrypted";
				((TextView) findViewById(R.id.indicators)).setText("SCVA - modem73 OFDM QPSK 1/2 normal - " + protection);
				return;
			}
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
			if (isPhoneConfiguration())
			{
				if (cpuLoad != null && processor != null) cpuLoad.setProgress(processor.phoneCpuLoad());
				return;
			}
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
			if (isPhoneConfiguration() || isModem73Configuration()) return;
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
			processor.attachUI(UserChatActivity.this, pendingSelection == null ? -1 : pendingSelection.contactId);
			if (Constants.devMode) Toast.makeText(UserChatActivity.this, msg, Toast.LENGTH_SHORT).show();
			submitSelection();
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			processor = null;
			isBound = false;
		}
	};

	@Override
	public void onStart()
	{
		super.onStart();

		Intent intent = new Intent(this, Processor.class);
		serviceBinding = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

		// Apply Configuration here to make it effective.
		// tmpConfig =
		// configurationsDataSource.getConfiguration(chat.getContact().getConfigId());
		// restoreRadioConfiguration();
		performOnStart();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		chatResumed = false;
		historySession.suspend();
		++uiGeneration;
		// txProgressThread.interrupt();
		if (processor != null) processor.detachUI(this);
		if (serviceBinding)
		{
			unbindService(serviceConnection);
			serviceBinding = false;
		}
		isBound = false;
		// Keep the previously bound service reference for existing background timers.
		// Its selection/readiness/shutdown gates still apply to every operation.
	}

	// Create runnable for updating the waterfall display
	public final Runnable updateWaterFall = new Runnable()
	{
		public void run()
		{
			if (wFbox != null)
			{
				if ((isPhoneConfiguration() || isModem73Configuration()) && processor != null && contact != null)
				{
					Processor.PhoneSpectrumSnapshot snapshot = processor.phoneSpectrumSnapshot(contact.getId());
					if (snapshot != null && snapshot.sequence != lastPhoneSpectrumSequence)
					{
						lastPhoneSpectrumSequence = snapshot.sequence;
						int phoneS2n = wFbox.setPhoneSpectrum(snapshot.copyLevelsDbfs(), snapshot.sampleRate, snapshot.fftSize);
						if (signalQuality != null)
						{
							// Current GGWave idle spectra measure above 70 on the raw 0-100
							// estimate; use that as the noise gate and map stronger signals to 0-100.
							int phoneS2nDisplay = Math.max(0, Math.min(100, (phoneS2n - 70) * 100 / 30));
							signalQuality.setProgress(phoneS2nDisplay);
							signalQuality.setSecondaryProgress(0);
							if (phoneS2nDisplay > 80 && wfView != null && isPhoneConfiguration()) wfView.setVisibility(View.VISIBLE);
						}
						if (isModem73Configuration())
						{
							long now = android.os.SystemClock.elapsedRealtime();
							if (now - lastModem73WaterfallDiagnosticMs >= 5000)
							{
								lastModem73WaterfallDiagnosticMs = now;
								processor.modem73UiDiagnostic("waterfall_view_update", 0,
										"sequence=" + snapshot.sequence + " shown=" + (wfView != null && wfView.isShown()) + " rowsDrawn=" + wFbox.phoneRowsDrawn());
							}
						}
					}
					else if (snapshot == null && isModem73Configuration())
					{
						lastPhoneSpectrumSequence = 0;
						wFbox.clearPhoneSpectrum();
						if (signalQuality != null) signalQuality.setProgress(0);
						long now = android.os.SystemClock.elapsedRealtime();
						if (now - lastModem73WaterfallDiagnosticMs >= 5000)
						{
							lastModem73WaterfallDiagnosticMs = now;
							processor.modem73UiDiagnostic("waterfall_waiting", 0, "shown=" + (wfView != null && wfView.isShown()) + " rowsDrawn=" + wFbox.phoneRowsDrawn());
						}
					}
				}
				wFbox.invalidate();
				wFbox.postInvalidate();
			}
		}
	};

	private void showPhoneWaterfall()
	{
		if (!phoneWaterfallVisibilityCaptured && wfView != null)
		{
			radioWaterfallWasVisible = wfView.getVisibility() == View.VISIBLE;
			phoneWaterfallVisibilityCaptured = true;
		}
		if (wFbox != null)
		{
			wFbox.setPhoneMode(true);
			wFbox.setPhoneDisplayProfile(isModem73Configuration() ? "MODEM73" : isPhoneConfiguration() && tmpConfig != null ? tmpConfig.getGgWaveSpeed() : null);
			if (isModem73Configuration()) wFbox.clearPhoneSpectrum();
		}
		lastPhoneSpectrumSequence = 0;
		if (signalQuality != null)
		{
			signalQuality.setProgress(0);
			signalQuality.setSecondaryProgress(0);
		}
		if (wfView != null) wfView.setVisibility(isModem73Configuration() ? View.VISIBLE : View.GONE);
	}

	// STAR: Display the Modem layout and associate it's buttons
	private void displayModem(boolean withWaterfall)
	{
		GS.gI().setCurrentView(Constants.MODEMVIEWwithWF);
		wFbox = (waterfallView) findViewById(R.id.WFbox);
		if (wFbox != null) wFbox.setPhoneMode(false);
		if (phoneWaterfallVisibilityCaptured && wfView != null)
		{
			wfView.setVisibility(radioWaterfallWasVisible ? View.VISIBLE : View.GONE);
			phoneWaterfallVisibilityCaptured = false;
		}
		handler.post(addToModem);

		// Initialize waterfall selected frequency
		if (wFbox != null) wFbox.selectedFrequency = Modem.frequency;
	}

	// STAR: RECEIVER
	public final Runnable addToModem = new Runnable()
	{
		public void run()
		{
			if (isPhoneConfiguration() || isModem73Configuration()) return;
			if (processor == null || contact == null || !processor.matchesSelection(pendingSelection)) return;
			if (tmpConfig.getId() == 9)
			{
				String temp = GS.gI().getMonitor();
				if (!acceptRadioMetadataChunk(temp)) return;
				bufferText = bufferText + temp;
				ModemBuffer += temp;

				GS.gI().setMonitor("");
				// Noted a slowing down of Gui after a large number of
				// characters are received
				// Reduced the buffer size if (ModemBuffer.length() > 60000)
				if (ModemBuffer.length() > 10000) ModemBuffer = ModemBuffer.substring(5000);
				// Reassign only the size limited buffer

				// /* Utils.println(bufferText); // Message/key contents: no default logging. */
				if (bufferText.endsWith("\n"))
				{
					TextMessage textMessage = new TextMessage();
					textMessage.setType(TextMessage.HIS_TEXT);
					textMessage.setMessage(bufferText.trim());
					textMessage.setEncrypted(false);
					textMessage.setContactId(contact.getId());
					textMessage.setKeyId(0);
					textMessage.setDate("" + System.currentTimeMillis());
					if (!storeRadioReceived(textMessage, false, false)) return;
					decryptedTxt = "";
					bufferText = "";

					runOnUiThread(populate);
					rxView.performClick();
				}
			}
			else
			{
				String temp = GS.gI().getMonitor();
				if (!acceptRadioMetadataChunk(temp)) return;
				bufferText = bufferText + temp;
				ModemBuffer += temp;
				/* Utils.println(bufferText); // Message/key contents: no default logging. */
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
				/* Utils.println(bufferText); // Message/key contents: no default logging. */
				if (bufferText.endsWith("§"))
				{
					// Text message is received
					if (bufferText.contains("Hz>"))
					{
						// Remove tuning already processed
						bufferText = bufferText.substring(bufferText.indexOf("Hz>") + 3, bufferText.length());
					}
					// debug only
					// /* Utils.println(bufferText); // Message/key contents: no default logging. */
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

					textMessage.setContactId(contact.getId());

					textMessage.setDate("" + System.currentTimeMillis());
					if (!storeRadioReceived(textMessage, false, false)) return;
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
					/* Utils.println(bufferText); // Message/key contents: no default logging. */
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

					textMessage.setDate("" + System.currentTimeMillis());
					if (!storeRadioReceived(textMessage, true, false)) return;
					bufferText = "";
					runOnUiThread(populate);
					rxView.performClick();
				}
				if (bufferText.endsWith("~"))
				{
					// Image is received
					/* Utils.println(bufferText); // Message/key contents: no default logging. */
					if (bufferText.contains("Hz>"))
					{
						// Remove tuning already processed
						bufferText = bufferText.substring(bufferText.indexOf("Hz>") + 3, bufferText.length());
					}
					bufferText = bufferText.substring(0, bufferText.length() - 2);

					TextMessage textMessage = new TextMessage();
					textMessage.setType(TextMessage.HIS_IMAGE);
					textMessage.setMessage(bufferText);

					textMessage.setContactId(contact.getId());

					textMessage.setDate("" + System.currentTimeMillis());
					if (!storeRadioReceived(textMessage, false, true)) return;
					bufferText = "";
					runOnUiThread(populate);
					rxView.performClick();
				}
			}
			// else
			// {
			// /* Utils.println(bufferText); // Message/key contents: no default logging. */
			// bufferText = "";
			// }
			// }
		}
	};

	@Override
	protected void onDestroy()
	{
		historySession.clear();
		legacySecret = null;
		legacyWrapped = null;
		legacyUnlockedKeyId = 0;
		legacyUnlockedContactId = 0;
		modem73Secret = null;
		modem73WrappedKey = null;
		modem73UnlockedKeyId = 0;
		modem73KeyContactId = 0;
		historyRows.clear();
		historyStates.clear();
		historyKeys.clear();
		chatResumed = false;
		if (processor != null)
		{
			processor.detachUI(this);
			processor.detachCompletion(this);
			processor.stopFromChat();
		}
		pendingSelection = null;
		processor = null;
		++uiGeneration;
		utils.hideNotification(this);
		// Reset flag then stop modem
		GS.gI().setRXParamsChanged(false);
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
				pendingModem73ImageUri = isModem73Configuration() ? selectedFile : null;
				pendingPhoneImageUri = isPhoneConfiguration() ? selectedFile : null;
				pendingPhoneImageWidth = Math.max(1, Math.min(96, data.getIntExtra("imageWidth", 96)));
				pendingPhoneImageHeight = Math.max(1, Math.min(96, data.getIntExtra("imageHeight", 96)));
				pendingModem73ImageSide = Math.max(12, Math.min(96, data.getIntExtra("modem73Side", 96)));
				prepairImageToSend(selectedFile);
				if (!isModem73Configuration() && !isPhoneConfiguration()) Utils.println("On Receive KB " + utils.getBase64ImageSizeInKB(base64String));
			}
		}
		else
		{
			if (requestCode == 123 && (isModem73Configuration() || isPhoneConfiguration()))
			{
				actionType = ActionType.TEXT;
				pendingModem73ImageUri = null;
				pendingPhoneImageUri = null;
			}
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
		if (bitmap == null)
		{
			Toast.makeText(this, "Unable to open image.", Toast.LENGTH_SHORT).show();
			if (isModem73Configuration() || isPhoneConfiguration()) actionType = ActionType.TEXT;
			pendingModem73ImageUri = null;
			pendingPhoneImageUri = null;
			return;
		}
		Drawable drawable = new BitmapDrawable(getResources(), bitmap);
		etMessage.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);

		clear.setVisibility(View.VISIBLE);
		etMessage.setHint("");
		etMessage.setEnabled(false);
		msgToSend = base64String;
		tmpConfig = configurationsDataSource.getConfiguration(chat.getContact().getConfigId());
		/* Utils.println(base64String); // Message/key contents: no default logging. */
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
			/* Utils.println("QR_CODE  Content: " + qrCodeText); // Message/key contents: no default logging. */

			// keyText = qrCodeText;
			/* Utils.println(qrCodeText); // Message/key contents: no default logging. */
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
		// Engine startup waits for onResume selection and the binder callback.
		handler.post(updateTitle);
	}

	private void restoreRadioConfiguration()
	{
		if (processor == null || contact == null || !processor.hasSelectionFor(contact.getId())) return;
		try
		{
			processor.applyRadioConfiguration(contact.getId(), false);
		}
		catch (RuntimeException error)
		{
			Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
		}
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
				if (!requireRadioSelection()) return;
				if (!changeRadioFrequency(Double.parseDouble(frequency), frequency)) return;

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
