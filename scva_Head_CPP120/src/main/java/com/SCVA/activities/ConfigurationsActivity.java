package com.SCVA.activities;

import java.util.ArrayList;

import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.Utils.GS;
import com.SCVA.adapters.ConfigurationAdapter;
import com.SCVA.adapters.ContactAdapter;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.models.Configuration;
import com.SCVA.models.Contact;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class ConfigurationsActivity extends BaseActivity implements View.OnClickListener, OnItemClickListener
{
	private enum CallType
	{
		ALL, CONTACT, SELECTION
	};
	private boolean doEdit = false;
	private CallType callType = CallType.ALL;
	private ImageView search, addContact;
	private ArrayList<Object> allItems;
	private TextView titleText;
	private RecyclerView listView;
	private EditText keywordView;
	private ConfigurationAdapter entryAdapter;
	private ConfigurationsDataSource configurationsDataSource;
	private Contact selectedContact;
	private static final int CONFIG_SELECTION_REQUEST = 765;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.configurations_activity);

		configurationsDataSource = new ConfigurationsDataSource(this);
		if (configurationsDataSource.getAllConfigurations().size() == 0)
		{
			addConfigurations();
		}

		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("ALL")) callType = CallType.ALL;
		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("CONTACT")) callType = CallType.CONTACT;
		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("SELECTION")) callType = CallType.SELECTION;

		titleText = findViewById(R.id.titleText);

		listView = findViewById(R.id.listView);

		addContact = findViewById(R.id.addEntry);
		addContact.setOnClickListener(this);

		search = findViewById(R.id.settings);
		search.setOnClickListener(this);

		if (callType == CallType.ALL)
		{
			titleText.setText(R.string.configurations);
			search.setVisibility(View.INVISIBLE);

			keywordView = findViewById(R.id.keywordView);
		}
		if (callType == CallType.CONTACT)
		{
			selectedContact = getIntent().getParcelableExtra("CONTACT");
		}
		if (callType == CallType.SELECTION)
		{
			addContact.setVisibility(View.VISIBLE);
            selectedContact = getIntent().getParcelableExtra("CONTACT");
			if (getIntent().getBooleanExtra("STATUS", false))
			{
				search.setVisibility(View.VISIBLE);
			}
		}

		allItems = new ArrayList<Object>();
		entryAdapter = new ConfigurationAdapter(allItems, this);
		listView.setAdapter(entryAdapter);
	}

	Runnable populate = new Runnable()
	{
		@Override
		public void run()
		{
			entryAdapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onClick(View view)
	{
		if (view == addContact)
		{
			if (doEdit == false)
			{
				doEdit = true;
				addContact.setBackgroundColor(Color.RED);
				addContact.setAlpha(0.5f);
				Toast.makeText(this, "Now tap on a configuration to edit", Toast.LENGTH_SHORT).show();
				return;
			}
			// Configuration tmpConfig = new Configuration();
			// // tmpConfig.setContactId("" + selectedContact.getId());
			// int configId = (int) entryDataSource.create(tmpConfig);
			// tmpConfig.setId(configId);

			// Intent intent = new Intent(this, AddConfigurationActivity.class);
			// // intent.putExtra("OBJECT", (Parcelable) tmpConfig);
			// intent.putExtra("CONTACT", (Parcelable) selectedContact);
			// intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			// intent.putExtra("TYPE", "CONTACT");
			// startActivity(intent);
		}
		if (view == search)
		{
            int chatId = new ChatsDataSource(this).getChatIdAgainstContact(selectedContact.getId());
            GS.gI().getEditor().putInt("CHAT_ID", chatId).apply();

            Intent intent = new Intent(this, AllChatsActivity.class);
            intent.putExtra("TYPE_CALL", true);
            intent.putExtra("TYPE", "LAUNCH_CHAT");
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		allItems.clear();

		if (callType == CallType.SELECTION)
		{
			allItems.addAll(configurationsDataSource.getAllConfigurations());
		}
		if (callType == CallType.ALL)
		{
			allItems.addAll(configurationsDataSource.getAllConfigurations());
		}
		if (callType == CallType.CONTACT)
		{
			allItems.addAll(configurationsDataSource.getContactConfigurations(selectedContact.getId()));
		}
		entryAdapter.notifyDataSetChanged();
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
			Intent intent = new Intent(this, AddConfigurationActivity.class);
			intent.putExtra("OBJECT", ((Parcelable) model));
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
		if (callType == CallType.CONTACT)
		{
			Intent intent = new Intent(this, AddConfigurationActivity.class);
			intent.putExtra("OBJECT", ((Parcelable) model));
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
		if (callType == CallType.SELECTION)
		{
			if (doEdit)
			{
				Configuration tmpConfig = ((Configuration) model);
				Intent intent = new Intent(this, AddConfigurationActivity.class);
				intent.putExtra("TYPE", "CONTACT");
				intent.putExtra("OBJECT", (Parcelable) tmpConfig);
				intent.putExtra("CONTACT", (Parcelable) selectedContact);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivityForResult(intent, CONFIG_SELECTION_REQUEST);
			}
			else
			{
				Configuration tmpContact = ((Configuration) model);
				Intent intent = new Intent();
				intent.putExtra("OBJECT", ((Parcelable) model));
				setResult(Activity.RESULT_OK, intent);
				finish();
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK)
		{
			Configuration tmpContact = data.getParcelableExtra("OBJECT");
			Intent intent = new Intent();
			intent.putExtra("OBJECT", ((Parcelable) tmpContact));
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	}

	@Override
	public void onItemLongClicked(Object model)
	{

	}

	public void addConfigurations()
	{
		Configuration configuration = new Configuration();

		configuration.setName("PHONE 1");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("THOR50x1");
		configuration.setFrequency(2500);// AFREQUENCY
		configuration.setrSErrorCorrection("1");// RSFEC_LEVEL MEDIUM = 1 | key = RSFEC_LEVEL value = 2
		configuration.setEncryption(true);// use_enc | key = use_enc value = true
		configuration.setEncryptionType("aes"); // key = enc_algorithm value = aes
		configuration.setAudioGain(12); // WFMAXVALUE | not in use in saved file
		configuration.setSquelch(30); // SQUELCHVALUE | not in use in saved file
		configuration.setAfc(false); // AFCONOFF | key = AFCONOFF value = false
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("PHONE 2");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("BPSK31");
		configuration.setFrequency(1000);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(13);
		configuration.setSquelch(30);
		configuration.setAfc(false);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("UHF 1");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("BPSK31");
		configuration.setFrequency(1000);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(13);
		configuration.setSquelch(30);
		configuration.setAfc(false);//   configuration.setAfc(true);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("UHF 2");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("BPSK125");
		configuration.setFrequency(2000);
		configuration.setrSErrorCorrection("1");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(12);
		configuration.setSquelch(30);
		configuration.setAfc(false);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("VHF 1");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("MFSK32");
		configuration.setFrequency(2000);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(12);
		configuration.setSquelch(30);
		configuration.setAfc(false);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("VHF 2");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("THOR22");
		configuration.setFrequency(1500);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(13);
		configuration.setSquelch(30);
		configuration.setAfc(false);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("HF 1");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("BPSK31");
		configuration.setFrequency(1500);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(12);
		configuration.setSquelch(30);
		configuration.setAfc(false);// configuration.setAfc(true);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration = new Configuration();

		configuration.setName("HF 2");
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("BPSK125");
		configuration.setFrequency(800);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(true);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(13);
		configuration.setSquelch(30);
		configuration.setAfc(false);// configuration.setAfc(true);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);

		configuration.setName("AndFlmsg");// DroidPSK
		configuration.setDefault(true);
		configuration.setDescription("");
		configuration.setModulationType("BPSK31");
		configuration.setFrequency(1500);
		configuration.setrSErrorCorrection("2");
		configuration.setEncryption(false);
		configuration.setEncryptionType("aes");
		configuration.setAudioGain(12);
		configuration.setSquelch(30);
		configuration.setAfc(false);
		configuration.setrXRsID(false);
		configuration.settXRsID(false);
		configurationsDataSource.create(configuration);
	}
}