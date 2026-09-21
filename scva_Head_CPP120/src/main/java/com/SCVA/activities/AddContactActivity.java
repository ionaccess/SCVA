package com.SCVA.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.MessagesDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.models.Chat;
import com.SCVA.models.Configuration;
import com.SCVA.models.Contact;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class AddContactActivity extends BaseActivity implements View.OnClickListener
{
	private int chatId = 0;

	private enum ActionType
	{
		NONE, NEW, EDIT, DELETE
	};

	private enum CallType
	{
		ALL, SELECTION
	};

	private Button save, startChat;
	private View keys, delete;
	private TextView configuration;
	private Configuration tmpConfig;
	private Contact selectedContact;
	private CallType callType = CallType.ALL;
	private ContactsDataSource contactsDataSource;
	private SettingsDataSource settingsDataSource;
	private ActionType actionType = ActionType.NEW;
	private EditText name, description, note, mobile;
	private static final int CONFIG_SELECTION_REQUEST = 765;
	private ConfigurationsDataSource configurationsDataSource;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.add_contact_activity);

		contactsDataSource = new ContactsDataSource(this);
		settingsDataSource = new SettingsDataSource(this);
		configurationsDataSource = new ConfigurationsDataSource(this);

		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("ALL")) callType = CallType.ALL;
		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("SELECTION")) callType = CallType.SELECTION;

		if (getIntent().getParcelableExtra("OBJECT") == null) actionType = ActionType.NEW;
		if (getIntent().getParcelableExtra("OBJECT") != null) actionType = ActionType.EDIT;

		keys = findViewById(R.id.keys);
		keys.setVisibility(View.INVISIBLE);
		keys.setOnClickListener(this);

		delete = findViewById(R.id.delete);
		delete.setOnClickListener(this);

		configuration = findViewById(R.id.configuration);
		configuration.setOnClickListener(this);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);

		startChat = findViewById(R.id.start_chat);
		startChat.setOnClickListener(this);

		name = findViewById(R.id.name);
		note = findViewById(R.id.note);
		mobile = findViewById(R.id.mobile);
		description = findViewById(R.id.description);
		// note.setVisibility(View.VISIBLE);

		if (callType == CallType.ALL)
		{
			delete.setVisibility(View.INVISIBLE);
		}
		if (callType == CallType.SELECTION)
		{

		}

		if (actionType == ActionType.EDIT)
		{
			selectedContact = (Contact) getIntent().getParcelableExtra("OBJECT");
			actionType = ActionType.EDIT;
			name.setText(selectedContact.getName());
			mobile.setText(selectedContact.getMobileNumber());
			description.setText(selectedContact.getDescription());
			tmpConfig = configurationsDataSource.getConfiguration(selectedContact.getConfigId());
			if (tmpConfig != null)
			{
				configuration.setText("" + tmpConfig.getName());
			}
			if (GS.gI().isAdvanceMode())
			{
				keys.setBackgroundResource(R.drawable.keys_icon_white);
				keys.setVisibility(View.VISIBLE);

				// keys.setBackgroundResource(R.drawable.settings);
				// keys.setVisibility(View.VISIBLE);
			}
			else
			{
				keys.setBackgroundResource(R.drawable.keys_icon_white);
				keys.setVisibility(View.VISIBLE);
			}
			delete.setVisibility(View.VISIBLE);
		}
		if (actionType == ActionType.NEW)
		{
			actionType = ActionType.NEW;
			keys.setVisibility(View.INVISIBLE);
			delete.setVisibility(View.INVISIBLE);
			selectedContact = new Contact();

			if (Constants.devMode)
			{
				name.setText("William Bros");
				mobile.setText("+954576385734");
				description.setText("A good friend");
			}
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == startChat)
		{
			if (actionType == ActionType.EDIT)
			{

			}
			if (actionType == ActionType.NEW)
			{
				GS.gI().getEditor().putInt("CHAT_ID", chatId).apply();

				Intent intent = new Intent(this, AllChatsActivity.class);
				intent.putExtra("TYPE_CALL", true);
				intent.putExtra("TYPE", "LAUNCH_CHAT");
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
		}
		if (view == configuration)
		{
			Intent intent = new Intent(this, ConfigurationsActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("CONTACT", (Parcelable) selectedContact);
			intent.putExtra("STATUS", selectedContact.getId() > 0);
			intent.putExtra("TYPE", "SELECTION");
			startActivityForResult(intent, CONFIG_SELECTION_REQUEST);
		}
		if (view == keys)
		{
			if (GS.gI().isAdvanceMode())
			{
				// Intent intent = new Intent(this, AddConfigurationActivity.class);
				// intent.putExtra("OBJECT", (Parcelable) tmpConfig);
				// intent.putExtra("CONTACT", (Parcelable) selectedContact);
				// intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				// intent.putExtra("TYPE", "CONTACT");
				// startActivity(intent);

				Intent intent = new Intent(this, KeysActivity.class);
				intent.putExtra("CONTACT", (Parcelable) selectedContact);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				intent.putExtra("TYPE", "CONTACT");
				startActivity(intent);
			}
			else
			{
				Intent intent = new Intent(this, KeysActivity.class);
				intent.putExtra("CONTACT", (Parcelable) selectedContact);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				intent.putExtra("TYPE", "CONTACT");
				startActivity(intent);
			}
		}
		if (view == delete)
		{
			new AlertDialog.Builder(this).setMessage("Your Chat and messages with this contact will also be deleted. Are you sure you want to delete ?").setCancelable(false)
					.setPositiveButton("Yes", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int id)
						{
							deleteEntry();
						}
					}).setNegativeButton("No", null).show();
		}
		if (view == save)
		{
			saveEntry();
		}
	}

	private void deleteEntry()
	{
		int contactId = selectedContact.getId();
		if (contactsDataSource.delete(selectedContact))
		{
			new KeysDataSource(this).deleteUserKeys(contactId);
			new MessagesDataSource(this).deleteUserMessages(contactId);
			new ChatsDataSource(this).deleteChatAgainstContactId(contactId);

			Toast.makeText(this, "Contact deleted.", Toast.LENGTH_LONG).show();
			setResult(RESULT_OK);
			finish();
		}
	}

	private void saveEntry()
	{
		if (name.getText().toString().length() == 0)
		{
			Toast.makeText(this, "Please type a valid name to save.", Toast.LENGTH_SHORT).show();
			return;
		}
		if (tmpConfig == null)
		{
			Toast.makeText(this, "Please select a configuration to proceed.", Toast.LENGTH_SHORT).show();
			return;
		}
		if (actionType == ActionType.NEW)
		{
			selectedContact.setName(name.getText().toString());
			selectedContact.setNote(note.getText().toString());
			selectedContact.setConfigId(tmpConfig.getId());
			selectedContact.setMobileNumber(mobile.getText().toString());
			selectedContact.setDescription(description.getText().toString());
			long lastId = contactsDataSource.create(selectedContact);
			selectedContact.setId((int) lastId);

			Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
			if (GS.gI().isAdvanceMode())
			{
				keys.setBackgroundResource(R.drawable.keys_icon_white);
				keys.setVisibility(View.VISIBLE);
			}
			else
			{
				keys.setBackgroundResource(R.drawable.keys_icon_white);
				keys.setVisibility(View.VISIBLE);
			}
            save.setVisibility(View.GONE);
            delete.setVisibility(View.VISIBLE);

			if (tmpConfig.getId() == 9)
			{
				// move the user to start chat if he choose AndFLmsg as config
				ChatsDataSource chatsDataSource = new ChatsDataSource(this);
				chatId = chatsDataSource.getChatIdAgainstContact(selectedContact.getId());
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
		}
		if (actionType == ActionType.EDIT)
		{
			selectedContact.setName(name.getText().toString());
			selectedContact.setConfigId(tmpConfig.getId());
			selectedContact.setNote(note.getText().toString());
			selectedContact.setMobileNumber(mobile.getText().toString());
			selectedContact.setDescription(description.getText().toString());

			contactsDataSource.update(selectedContact);

			Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
			if (callType == CallType.ALL) finish();
		}
		if (actionType == ActionType.DELETE)
		{
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CONFIG_SELECTION_REQUEST && resultCode == RESULT_OK)
		{
			tmpConfig = data.getParcelableExtra("OBJECT");
			configuration.setText("" + tmpConfig.getName());
		}
	}

	@Override
	public void onBackPressed()
	{
		if (new KeysDataSource(this).getDefaultKey(selectedContact.getId()) == null)
		{
			new androidx.appcompat.app.AlertDialog.Builder(this).setMessage("Are you sure you want to close without key generation").setCancelable(true)
					.setPositiveButton("Yes", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int id)
						{
							AddContactActivity.super.onBackPressed();
						}
					}).setNegativeButton("No", null).show();
		}
		else
			super.onBackPressed();
	}
}