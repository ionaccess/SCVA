package com.SCVA.activities;

import java.util.ArrayList;

import com.SCVA.Modem;
import com.SCVA.Processor;
import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.SwipeToDeleteCallback;
import com.SCVA.Utils.TruSDXAudio;
import com.SCVA.Utils.TruSDXCat;
import com.SCVA.Utils.Utils;
import com.SCVA.adapters.ChatAdapter;
import com.SCVA.config;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.MessagesDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.models.Chat;
import com.SCVA.models.Contact;
import com.SCVA.myPreferences;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class AllChatsActivity extends BaseActivity implements View.OnClickListener, OnItemClickListener
{
	private enum CallType
	{
		ALL, LAUNCH_CHAT
	};

	private Utils utils;
	private Toolbar toolbar;
	private TextView titleText;
	private int warningCount = 0;
	private EditText keywordView;
	private RecyclerView listView;
	private String warningTxt = "";
	private ChatAdapter chatAdapter;
	private ArrayList<Object> allItems;
	private ChatsDataSource chatsDataSource;
	private CallType callType = CallType.ALL;
	private int CONTACT_SELECTION_REQUEST = 435;
	private MessagesDataSource messagesDataSource;
	private SettingsDataSource settingsDataSource;
	private ImageView addContact, settings, startNewChat;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.all_chats_activity);

		if (getIntent().getBooleanExtra("TYPE_CALL", false))
		{
			if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("LAUNCH_CHAT")) callType = CallType.LAUNCH_CHAT;
		}

		GS.gI().setContext(this);
		utils = new Utils();

		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		chatsDataSource = new ChatsDataSource(this);
		messagesDataSource = new MessagesDataSource(this);
		settingsDataSource = new SettingsDataSource(this);

		titleText = findViewById(R.id.titleText);

		listView = findViewById(R.id.listView);

		addContact = findViewById(R.id.addEntry);
		addContact.setOnClickListener(this);

		startNewChat = findViewById(R.id.startNewChat);
		startNewChat.setOnClickListener(this);

		settings = findViewById(R.id.settings);
		settings.setOnClickListener(this);

		if (callType == CallType.ALL)
		{
			// titleText.setText(R.string.all_contacts);
			keywordView = findViewById(R.id.keywordView);
		}

		allItems = new ArrayList<Object>();
		chatAdapter = new ChatAdapter(allItems, this);
		listView.setAdapter(chatAdapter);
		setupSwipeToDelete();

		// Update the list of available modems
		Modem.updateModemCapabilityList();

		// added this to get pass permissions screen before trying to write on Storage.
		GS.gI().getEditor().putBoolean("PERMISSION", true).apply();
	}

	private void setupSwipeToDelete()
	{
		SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this)
		{
			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction)
			{
				int position = viewHolder.getPosition();
				deleteChat(position);
			}
		};

		// Connect it to the active UI component container
		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
		itemTouchHelper.attachToRecyclerView(listView);
	}

	private void deleteChat(int position)
	{
		new AlertDialog.Builder(this).setMessage("Are you sure you want to delete this chat ?").setCancelable(true).setPositiveButton("Yes", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				Chat tmpChat = ((Chat) allItems.get(position));
				messagesDataSource.deleteUserMessages(tmpChat.getContactId());
				chatsDataSource.deleteChatAgainstContactId(tmpChat.getContactId());

				// 1. Delete item from your specific data source array
				allItems.remove(position);
				// 2. Notify your adapter to run native deletion animations
				chatAdapter.notifyItemRemoved(position);
			}
		}).setNegativeButton("No", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialogInterface, int i)
			{
				runOnUiThread(populate);
			}
		}).show();
	}

	Runnable populate = new Runnable()
	{
		@Override
		public void run()
		{
			chatAdapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onClick(View view)
	{
		if (view == addContact)
		{
			Intent intent = new Intent(this, ContactsActivity.class);
			intent.putExtra("TYPE", "ALL");
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivityForResult(intent, CONTACT_SELECTION_REQUEST);
		}
		if (view == startNewChat)
		{
			Intent intent = new Intent(this, ContactsActivity.class);
			intent.putExtra("TYPE", "SELECTION");
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivityForResult(intent, CONTACT_SELECTION_REQUEST);
		}
		if (view == settings)
		{
			Intent intent = new Intent(this, myPreferences.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivityForResult(intent, CONTACT_SELECTION_REQUEST);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		// new Utils().showNotification(this, null);
		if (callType == CallType.LAUNCH_CHAT)
		{
			// We are coming from an activity far far away and want to launch the chat
			// screen. change callType to ALL so that when user press back from Chat screen
			// it wont push to Chat again

			callType = CallType.ALL;
			Intent intent = new Intent(this, UserChatActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
		allItems.clear();
		if (callType == CallType.ALL)
		{
			allItems.addAll(chatsDataSource.getAllChats());
		}
		chatAdapter.notifyDataSetChanged();

		if (settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL, "0").equalsIgnoreCase("1"))
		{
			// Start rig connection if already enabled
			new Thread(() -> {
				boolean ok = TruSDXCat.connect(this);
				Utils.println("TRUSDX | AutoConnect=" + ok);
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						if (ok)
						{
							Toast.makeText(AllChatsActivity.this, "Rig Connected", Toast.LENGTH_LONG).show();
							if (settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_AUDIO_OVER_USB, "0").equalsIgnoreCase("1"))
							{
								TruSDXAudio.getInstance().enableAudioMode();
							}
						}
					}
				});
			}).start();
		}
	}

	@Override
	public void onLoadMore(int position)
	{

	}

	@Override
	public void onItemClicked(Object model, String type)
	{
		if (callType == CallType.ALL)
		{
			Chat chat = ((Chat) model);
			GS.gI().getEditor().putInt("CHAT_ID", chat.getId()).apply();
			GS.gI().setChatId(chat.getId());

			Intent intent = new Intent(this, UserChatActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
	}

	@Override
	public void onItemLongClicked(Object model)
	{

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CONTACT_SELECTION_REQUEST && resultCode == RESULT_OK)
		{
			int chatId = 0;
			Contact tmpContact = data.getParcelableExtra("OBJECT");

			Chat tmpChat = new Chat();
			tmpChat.setContact(tmpContact);
			tmpChat.setMessage("Greetings.");
			tmpChat.setDate("" + System.currentTimeMillis());

			if (isUnique(tmpChat))
			{
				chatId = chatsDataSource.create(tmpChat);
				GS.gI().getEditor().putInt("CHAT_ID", chatId).apply();

				Intent intent = new Intent(this, UserChatActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}
			else
			{
				chatId = getChatId(tmpChat);
				GS.gI().getEditor().putInt("CHAT_ID", chatId).apply();

				Intent intent = new Intent(this, UserChatActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}
			chatAdapter.notifyDataSetChanged();
		}
	}

	public boolean isUnique(Chat tmpChat)
	{
		for (int i = 0; i < allItems.size(); i++)
		{
			if (((Chat) allItems.get(i)).getContactId() == tmpChat.getContactId())
			{
				return false;
			}
		}
		return true;
	}

	public int getChatId(Chat tmpChat)
	{
		for (int i = 0; i < allItems.size(); i++)
		{
			if (((Chat) allItems.get(i)).getContactId() == tmpChat.getContactId())
			{
				return ((Chat) allItems.get(i)).getId();
			}
		}
		return 0;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		if (!Constants.devMode)
		{
			menu.removeItem(R.id.test);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	// Option Screen handler
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case R.id.prefs:
			Intent OptionsActivity = new Intent(AllChatsActivity.this, myPreferences.class);
			startActivity(OptionsActivity);
			break;
		case R.id.burnPreferences:
			burnEveryThing();
			break;
		case R.id.usb:
			getUSB();
			break;
		case R.id.exit:
			exit();
			break;
		case R.id.test:
			startActivity(new Intent(this, TestActivity.class));
			break;
		}
		return true;
	}

	private void getUSB()
	{
		startActivity(new Intent(this, RigControlActivity.class));
	}

	@Override
	public void onBackPressed()
	{
		exit();
	}

	public void exit()
	{
		new AlertDialog.Builder(this).setMessage("Are you sure you want to exit ?").setCancelable(true).setPositiveButton("Yes", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				new Utils().hideNotification(AllChatsActivity.this);

				// Stop the Modem and Listening Service
				// if (ProcessorON)
				// {
				// stopService(new Intent(SCVA.this, Processor.class));
				// ProcessorON = false;
				// }

				// Close that activity and return to previous screen
				finish();
				// Kill the process
				android.os.Process.killProcess(android.os.Process.myPid());
			}
		}).setNegativeButton("No", null).show();
	}

	private void burnEveryThing()
	{
		runOnUiThread(new Utils().showQuestionDialog("Warning", "Are your sure your want to \n\uD83D\uDD25 ❓", this, burn, noBurn));
	}

	// 🔥🔥🔥 ask for pin verification kanwood ts480
	Runnable burn = new Runnable()
	{
		@Override
		public void run()
		{
			if (warningCount == 2)
			{
				Utils.println("<<<<<<\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25>>>>>>");
				warningCount = 0;

				// Burning the keys
				new KeysDataSource(AllChatsActivity.this).burnKeyStore();

				new SettingsDataSource(AllChatsActivity.this).burnSettings();

				new ChatsDataSource(AllChatsActivity.this).burnChats();

				new ContactsDataSource(AllChatsActivity.this).burnContacts();

				new ConfigurationsDataSource(AllChatsActivity.this).burnConfigurations();

				new MessagesDataSource(AllChatsActivity.this).burnMessages();

				utils.showNotification(AllChatsActivity.this, "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25");

				// Burning the Preferences
				SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(AllChatsActivity.this);
				settings.edit().clear().apply();

				// Burning the BackupFile
				config.burnBackupFile("SettingsBackup.bin");

				// Burning the logs
				utils.burnLogs(AllChatsActivity.this);

				// Stop the Modem and Listening Service
				if (GS.gI().isProcessorON())
				{
					stopService(new Intent(AllChatsActivity.this, Processor.class));
					GS.gI().setProcessorON(false);
				}
				Toast.makeText(AllChatsActivity.this, "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25", Toast.LENGTH_LONG).show();
				utils.hideNotification(AllChatsActivity.this);

				// Exiting the app dumps RAM
				finish();
				android.os.Process.killProcess(android.os.Process.myPid());
			}
			else
			{
				warningCount++;
				if (warningCount == 1) warningTxt = "\n\uD83D\uDD25\uD83D\uDD25";
				if (warningCount == 2) warningTxt = "\n\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25";
				runOnUiThread(new Utils().showQuestionDialog("Warning", "Are your sure your want to " + warningTxt + " ❓", AllChatsActivity.this, burn, noBurn));
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
}