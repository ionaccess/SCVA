package com.SCVA.activities;

import com.SCVA.Modem;
import com.SCVA.R;
import com.SCVA.Utils.GS;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.models.Configuration;
import com.SCVA.models.Contact;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class AddConfigurationActivity extends BaseActivity implements View.OnClickListener
{
	private enum ActionType
	{
		NONE, NEW, EDIT, DELETE
	};

	private View keys, modulationType;
	private Contact selectedContact;
	private Configuration configuration;
	private ActionType actionType = ActionType.NEW;
	private ImageView save;
	private CheckBox afc, useEnc, txRsid, rxRsid;
	private TextView errorCorrection, encAlgoType;
	private EditText name, description, frequency, audioGain, squelch;
	private SettingsDataSource settingsDataSource;
	private ContactsDataSource contactsDataSource;
	private ConfigurationsDataSource configurationsDataSource;
	private static final int CONTACT_PICK_REQUEST = 100;
	private ArrayList<String> customModemsList;
	private String[] modNames = new String[0];

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.add_config_activity);

		contactsDataSource = new ContactsDataSource(this);
		settingsDataSource = new SettingsDataSource(this);
		configurationsDataSource = new ConfigurationsDataSource(this);

		if (getIntent().getParcelableExtra("OBJECT") == null) actionType = ActionType.NEW;
		if (getIntent().getParcelableExtra("OBJECT") != null) actionType = ActionType.EDIT;

		keys = findViewById(R.id.keys);
		keys.setOnClickListener(this);

		encAlgoType = findViewById(R.id.encAlgoType);
		encAlgoType.setOnClickListener(this);

		modulationType = findViewById(R.id.modulationType);
		modulationType.setOnClickListener(this);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);

		name = findViewById(R.id.name);
		frequency = findViewById(R.id.frequency);

		errorCorrection = findViewById(R.id.errorCorrection);
		errorCorrection.setOnClickListener(this);
		if (actionType == ActionType.NEW)
		{
			configuration = new Configuration();
			configuration.setrSErrorCorrection(isV2FecEnabled() ? "1" : "2");
			errorCorrection.setText(getErrorNames()[getErrorIndex()]);
		}

		audioGain = findViewById(R.id.audioGain);
		squelch = findViewById(R.id.squelch);

		afc = findViewById(R.id.afc);
		useEnc = findViewById(R.id.useEnc);

		rxRsid = findViewById(R.id.rxrsid);
		txRsid = findViewById(R.id.txrsid);

		description = findViewById(R.id.description);

		if (actionType == ActionType.EDIT)
		{
			configuration = getIntent().getParcelableExtra("OBJECT");
			selectedContact = (Contact) getIntent().getParcelableExtra("CONTACT");

			name.setText(configuration.getName());
			description.setText(configuration.getDescription());
			frequency.setText("" + configuration.getFrequency());

			String[] names = getErrorNames();
			errorCorrection.setText("" + names[getErrorIndex()]);
			audioGain.setText("" + configuration.getAudioGain());
			squelch.setText("" + configuration.getSquelch());
			afc.setChecked(configuration.isAfc());
			useEnc.setChecked(configuration.isEncryption());

			rxRsid.setChecked(configuration.isrXRsID());
			txRsid.setChecked(configuration.istXRsID());

			String[] encNames = getResources().getStringArray(R.array.enc_algos);
			encAlgoType.setText("" + encNames[getAlgoIndex()]);

			// keys.setVisibility(View.VISIBLE);
			keys.setVisibility(View.INVISIBLE);
			// delete.setVisibility(View.INVISIBLE);

			((TextView) modulationType).setText("Use " + configuration.getModulationType());
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == errorCorrection)
		{
			showErrorCorrection();
		}
		if (view == modulationType)
		{
			showModes();
		}
		if (view == encAlgoType)
		{
			showEncryptionTypes();
		}
		if (view == keys)
		{
			Intent intent = new Intent(this, KeysActivity.class);
			intent.putExtra("CONTACT", (Parcelable) selectedContact);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			intent.putExtra("TYPE", "CONTACT");
			startActivity(intent);
		}
		// if (view == delete)
		// {
		// new AlertDialog.Builder(this).setMessage("Are you sure you want to delete ?").setCancelable(false).setPositiveButton("Yes", new DialogInterface.OnClickListener()
		// {
		// public void onClick(DialogInterface dialog, int id)
		// {
		// deleteEntry();
		// }
		// }).setNegativeButton("No", null).show();
		// }
		if (view == save)
		{
			saveEntry();
		}
	}

	private void deleteEntry()
	{
		if (configurationsDataSource.delete(configuration))
		{
			Toast.makeText(this, "Configuration deleted.", Toast.LENGTH_LONG).show();
			finish();
		}
	}

	private void saveEntry()
	{
		if (actionType == ActionType.NEW)
		{
			configuration.setName(name.getText().toString());
			// configuration.setContactId("" + selectedContact.getId());
			configuration.setDescription(description.getText().toString());
			long lastId = configurationsDataSource.create(configuration);
			selectedContact.setId((int) lastId);

			Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
			// keys.setVisibility(View.VISIBLE);
			keys.setVisibility(View.INVISIBLE);
			// delete.setVisibility(View.VISIBLE);
		}
		if (actionType == ActionType.EDIT)
		{
			configuration.setName(name.getText().toString());
			configuration.setDescription(description.getText().toString());

			configuration.setFrequency(Integer.parseInt(frequency.getText().toString()));
			// error correction
			configuration.setEncryption(useEnc.isChecked());
			// enc algo
			configuration.setAudioGain(Integer.parseInt(audioGain.getText().toString()));
			configuration.setSquelch(Integer.parseInt(squelch.getText().toString()));
			configuration.setAfc(afc.isChecked());

			configuration.setrXRsID(rxRsid.isChecked());
			configuration.settXRsID(txRsid.isChecked());

			configurationsDataSource.update(configuration);

			// configuration.setContactId("" + selectedContact.getId());
			Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
			try
			{
				if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("CONTACT"))
				{
					Intent intent = new Intent();
					intent.putExtra("OBJECT", configuration);
					setResult(Activity.RESULT_OK, intent);
					finish();
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		if (actionType == ActionType.DELETE)
		{
		}
	}

	public void showEncryptionTypes()
	{
		String[] names = getResources().getStringArray(R.array.enc_algos);
		String[] values = getResources().getStringArray(R.array.enc_algos_values);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.encryption_algorithm).setNegativeButton("CANCEL", (dialog, which) -> {

		}).setSingleChoiceItems(R.array.enc_algos, getAlgoIndex(), (dialog, which) -> {
			((TextView) encAlgoType).setText("" + names[which]);
			configuration.setEncryptionType(values[which]);
			dialog.dismiss();
		});

		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public void showErrorCorrection()
	{
		String[] names = getErrorNames();
		String[] values = getErrorValues();

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Level of Error Mitigation").setNegativeButton("CANCEL", (dialog, which) -> {
		}).setSingleChoiceItems(names, getErrorIndex(), (dialog, which) -> {
			((TextView) errorCorrection).setText("" + names[which]);
			configuration.setrSErrorCorrection(values[which]);
			dialog.dismiss();
		});

		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public void showModes()
	{
		if (GS.gI().getValue().getBoolean("USEMODELIST", false))
		{
			customModemsList = new ArrayList<>();
			for (int i = 0; i < Modem.modemCapListString.length; i++)
			{
				if (GS.gI().getValue().getBoolean("USE" + Modem.modemCapListString[i], false))
				{
					customModemsList.add(Modem.modemCapListString[i]);
				}
			}
			modNames = customModemsList.toArray(new String[0]);
		}
		else
		{
			customModemsList = new ArrayList<>();
			for (int i = 0; i < Modem.modemCapListString.length; i++)
			{
				if (Modem.modemCapListString[i].length() > 0) customModemsList.add(Modem.modemCapListString[i]);
			}
			customModemsList.remove(customModemsList.size() - 1);
			modNames = customModemsList.toArray(new String[0]);

		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Modulation Type").setNegativeButton("CANCEL", (dialog, which) -> {
		}).setSingleChoiceItems(modNames, getModulationIndex(), (dialog, which) -> {
			((TextView) modulationType).setText("Use " + modNames[which]);
			// GS.gI().getEditor().putString("LASTMODEUSED", "" + Modem.getMode(names[which])).apply();
			configuration.setModulationType(modNames[which]);

			dialog.dismiss();
		});

		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public int getErrorIndex()
	{
		int checkedIndex = 0;
		String[] values = getErrorValues();
		for (int i = 0; i < values.length; i++)
		{
			if (values[i].equalsIgnoreCase(configuration.getrSErrorCorrection())) checkedIndex = i;
		}
		return checkedIndex;
	}

	private boolean isV2FecEnabled()
	{
		return GS.gI().getValue().getBoolean("RSFEC_V2_ENABLED", false);
	}

	private String[] getErrorNames()
	{
		return getResources().getStringArray(isV2FecEnabled() ? R.array.rsfec_v2_preset_names : R.array.rsfec_p_levels_list);
	}

	private String[] getErrorValues()
	{
		return getResources().getStringArray(isV2FecEnabled() ? R.array.rsfec_v2_preset_values : R.array.rsfec_p_levels_values);
	}

	public int getAlgoIndex()
	{
		int checkedIndex = 0;
		String[] names = getResources().getStringArray(R.array.enc_algos);
		String[] values = getResources().getStringArray(R.array.enc_algos_values);
		for (int i = 0; i < values.length; i++)
		{
			if (values[i].equalsIgnoreCase(configuration.getEncryptionType())) checkedIndex = i;
		}
		return checkedIndex;
	}

	public int getModulationIndex()
	{
		if (configuration.getModulationType() == null) return 0;
		int checkedIndex = 0;
		for (int i = 0; i < customModemsList.size(); i++)
		{
			if (customModemsList.get(i).equalsIgnoreCase(configuration.getModulationType())) checkedIndex = i;
		}
		return checkedIndex;
	}

	@Override
	public void onBackPressed()
	{
		// if (new KeysDataSource(this).getDefaultKey(selectedContact.getId())
		// == null)
		// {
		// Toast.makeText(this, "Please create at-least one key to proceed",
		// Toast.LENGTH_LONG).show();
		// return;
		// }
		// super.onBackPressed();

		// if (new KeysDataSource(this).getDefaultKey(selectedContact.getId()) == null)
		// {
		// new androidx.appcompat.app.AlertDialog.Builder(this).setMessage("Are you sure you want to close without key generation").setCancelable(true)
		// .setPositiveButton("Yes", new DialogInterface.OnClickListener()
		// {
		// public void onClick(DialogInterface dialog, int id)
		// {
		// AddConfigurationActivity.super.onBackPressed();
		// }
		// }).setNegativeButton("No", null).show();
		// }
		// else
		super.onBackPressed();
	}
}
