package com.SCVA.activities;

import java.util.ArrayList;

import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;
import com.SCVA.R;
import com.SCVA.adapters.KeyAdapter;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.models.Chat;
import com.SCVA.models.Contact;
import com.SCVA.models.Key;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class KeysActivity extends Activity implements View.OnClickListener, AdapterView.OnItemClickListener
{
	private enum CallType
	{
		ALL, CONTACT
	};

	private CallType callType = CallType.ALL;

	private ImageView addKey, startNewChat;

	private ListView listView;

	private KeysDataSource keyStore;

	private KeyAdapter adapter;

	private ArrayList<Key> keysList;

	private String keys = "";

	private Contact selectedContact;

	private SettingsDataSource settingsDataSource;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_keys);

		settingsDataSource = new SettingsDataSource(this);

		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("ALL")) callType = CallType.ALL;
		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("CONTACT")) callType = CallType.CONTACT;

		if (callType == CallType.CONTACT)
		{
			selectedContact = getIntent().getParcelableExtra("CONTACT");
			((TextView) findViewById(R.id.title)).setText(selectedContact.getName() + "'s Keys");
		}
		keysList = new ArrayList<>();

		keyStore = new KeysDataSource(this);
		keysList = keyStore.getAllKeys();

		addKey = findViewById(R.id.addKey);
		addKey.setOnClickListener(this);

		startNewChat = findViewById(R.id.startNewChat);
		startNewChat.setVisibility(View.INVISIBLE);
		startNewChat.setOnClickListener(this);

		listView = findViewById(R.id.keysList);

		adapter = new KeyAdapter(this, R.layout.keys_list_item, keysList, listView);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);

		runOnUiThread(populate);

		// Utils.println(new Utils().generateMd5("hello"));
		// Utils.println(new Utils().generateMd5("hello how are you hello how are
		// you hello how are you").length());

		// String ba = Base64.toBase64String("1234567812345678".getBytes());
		// Utils.println(ba);
		// Utils.println(Base64.decode(ba));
	}

	@Override
	public void onClick(View view)
	{
		if (view == startNewChat)
		{
			ChatsDataSource chatsDataSource = new ChatsDataSource(this);
            int chatId = chatsDataSource.getChatIdAgainstContact(selectedContact.getId());
            if (chatId == 0)
			{
                // create a chat if not already available
                Chat tmpChat = new Chat();
                tmpChat.setContact(selectedContact);
				tmpChat.setMessage("Greetings.");
				tmpChat.setDate("" + System.currentTimeMillis());
                chatId = chatsDataSource.create(tmpChat);
            }
			GS.gI().getEditor().putInt("CHAT_ID", chatId).apply();

			Intent intent = new Intent(this, AllChatsActivity.class);
			intent.putExtra("TYPE_CALL", true);
			intent.putExtra("TYPE", "LAUNCH_CHAT");
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
		if (view == addKey)
		{
			showKeyDialog();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (keyStore.getUserKeys(selectedContact.getId()).size() > 0)
		{
			startNewChat.setVisibility(View.VISIBLE);
		}
		runOnUiThread(populate);
	}

	Runnable populate = new Runnable()
	{
		@Override
		public void run()
		{
			adapter.clear();
			if (callType == CallType.ALL)
			{
				keysList = keyStore.getAllKeys();
				for (int i = 0; i < keysList.size(); i++)
				{
					adapter.add(keysList.get(i));
				}
			}
			if (callType == CallType.CONTACT)
			{
				keysList = keyStore.getUserKeys(selectedContact.getId());

				for (int i = 0; i < keysList.size(); i++)
				{
					adapter.add(keysList.get(i));
				}
			}
			adapter.notifyDataSetChanged();
		}
	};

	public void showKeyDialog()
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setCancelable(true);
		dialog.setContentView(R.layout.new_key_dialog);

		ViewGroup rKey = (ViewGroup) dialog.findViewById(R.id.rKey);
		rKey.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				Intent intent = new Intent(KeysActivity.this, AddKeyActivity.class);
				if (callType == CallType.CONTACT)
				{
					intent.putExtra("CONTACT", (Parcelable) selectedContact);
					intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					intent.putExtra("TYPE", "CONTACT");
				}
				startActivity(intent);
			}
		});
		ViewGroup pKey = (ViewGroup) dialog.findViewById(R.id.pKey);
		pKey.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				Utils.println("Password Key");

				Intent intent = new Intent(KeysActivity.this, AddPasswordBasedKeyActivity.class);
				if (callType == CallType.CONTACT)
				{
					intent.putExtra("CONTACT", (Parcelable) selectedContact);
					intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					intent.putExtra("TYPE", "CONTACT");
				}
				startActivity(intent);
			}
		});
		ViewGroup iKey = (ViewGroup) dialog.findViewById(R.id.iKey);
		iKey.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Utils.println("Import Key");
				dialog.dismiss();

				Intent intent = new Intent(KeysActivity.this, ImportKeyActivity.class);
				if (callType == CallType.CONTACT)
				{
					intent.putExtra("CONTACT", (Parcelable) selectedContact);
					intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					intent.putExtra("TYPE", "CONTACT");
				}
				startActivity(intent);
			}
		});
		dialog.show();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		Intent intent = new Intent(KeysActivity.this, KeyInfoActivity.class);
		intent.putExtra("KEY", keysList.get(position));
		if (callType == CallType.CONTACT)
		{
			intent.putExtra("CONTACT", (Parcelable) selectedContact);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("TYPE", "CONTACT");
		}
		startActivity(intent);

	}
}
