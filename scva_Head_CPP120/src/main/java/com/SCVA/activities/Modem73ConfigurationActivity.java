package com.SCVA.activities;

import com.SCVA.Modem73Crypto;
import com.SCVA.R;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.models.Configuration;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Narrow configuration editor for the plaintext MODEM73 trial profile. */
public final class Modem73ConfigurationActivity extends BaseActivity
{
	private static final String[] PROFILE_VALUES = { Configuration.MODEM73_PROFILE_DEFAULT, Configuration.MODEM73_PROFILE_QPSK_14_NORMAL,
			Configuration.MODEM73_PROFILE_BPSK_14_NORMAL, Configuration.MODEM73_PROFILE_ROBUST,
			Configuration.MODEM73_PROFILE_REPEATED, Configuration.MODEM73_PROFILE_RDM_600,
			Configuration.MODEM73_PROFILE_RDMN_300 };
	private Configuration configuration;
	private EditText name, callsign, voxLeadMs;
	private boolean fresh;
	private CheckBox encryption;
	private TextView algorithm;
	private Switch voxEnabled;
	private Spinner profile;
	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.modem73_configuration_activity);
		configuration = getIntent().getParcelableExtra("OBJECT");
		fresh = configuration == null;
		if (fresh)
		{
			configuration = new Configuration();
			configuration.setName("MODEM73");
			configuration.setModulationType("MODEM73");
			configuration.setEncryption(Configuration.DEFAULT_MODEM73_ENCRYPTION);
			configuration.setEncryptionType("aes");
		}
		name = findViewById(R.id.configurationName);
		callsign = findViewById(R.id.modem73Callsign);
		profile = findViewById(R.id.modem73Profile);
		voxEnabled = findViewById(R.id.voxEnabled);
		voxLeadMs = findViewById(R.id.voxLeadMs);
		encryption = findViewById(R.id.encryptionEnabled);
		algorithm = findViewById(R.id.encryptionAlgorithm);
		TextView profileDescription = findViewById(R.id.profileDescription);
		TextView voxDescription = findViewById(R.id.voxDescription);
		TextView configurationDescription = findViewById(R.id.configurationDescription);
		View save = findViewById(R.id.save);
		TextView keys = findViewById(R.id.contactEncryptionKey);
		name.setText(configuration.getName());
		callsign.setText(configuration.getModem73Callsign());
		String[] profileLabels = { "Standard — OFDM QPSK 1/2 NORMAL (2.73 s/frame)",
				"OFDM QPSK 1/4 NORMAL (2.73 s, 254-byte frame payload)",
				"OFDM BPSK 1/4 NORMAL (2.73 s, 126-byte frame payload)",
				"OFDM QPSK 1/4 LONG (4.92 s/frame)",
				"OFDM QPSK 1/4 ×2 LONG (9.29 s/frame)",
				"ROBUST RDM-600 (about 7.0 s/frame)",
				"ROBUST RDMN-300 narrow (about 13.8 s/frame)" };
		ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profileLabels);
		profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		profile.setAdapter(profileAdapter);
		String savedProfile = configuration.getModem73Profile();
		int savedProfileIndex = java.util.Arrays.asList(PROFILE_VALUES).indexOf(savedProfile);
		profile.setSelection(savedProfileIndex < 0 ? 0 : savedProfileIndex);
		profileDescription.setText("The profile is selected per configuration; use the same named profile at both stations. All OFDM choices retain the same 2400 Hz carrier allocation. ROBUST RDMN-300 occupies about 600 Hz and uses the narrow ROBUST receiver; RDM-600 uses the wide ROBUST family. Normal 1/4 OFDM frames have smaller payloads and use the existing fragment header with more fragments. Approximate frame airtime excludes channel access and VOX lead. These are pinned-codec options, not HF reliability claims.");
		voxEnabled.setChecked(configuration.isModem73VoxEnabled());
		voxLeadMs.setText(Integer.toString(configuration.getModem73VoxLeadMs()));
		voxDescription.setText("When enabled, MODEM73 plays a 1200 Hz tone for 550 ms by default before each independently keyed frame. Choose 100–3000 ms. VOX takes precedence over configured CAT/serial PTT for this configuration; the two keying methods are not combined.");
		voxEnabled.setOnCheckedChangeListener((button, checked) -> voxLeadMs.setEnabled(checked));
		voxLeadMs.setEnabled(voxEnabled.isChecked());
		encryption.setChecked(configuration.isEncryption());
		algorithm.setText("Encryption Type: " + configuration.getEncryptionType());
		algorithm.setOnClickListener(v -> {
			String[] labels = getResources().getStringArray(R.array.enc_algos), values = getResources().getStringArray(R.array.enc_algos_values);
			java.util.ArrayList<String> names = new java.util.ArrayList<>(), tokens = new java.util.ArrayList<>();
			for (int i = 0; i < values.length; i++)
				if (Modem73Crypto.supports(values[i]))
				{
					names.add(labels[i]);
					tokens.add(values[i]);
				}
			new AlertDialog.Builder(this).setTitle(R.string.encryption_algorithm)
					.setSingleChoiceItems(names.toArray(new String[0]), tokens.indexOf(configuration.getEncryptionType()), (dialog, which) -> {
						configuration.setEncryptionType(tokens.get(which));
						algorithm.setText("Encryption Type: " + names.get(which));
						dialog.dismiss();
					}).setNegativeButton("CANCEL", null).show();
		});
		keys.setOnClickListener(v -> {
			android.os.Parcelable contact = getIntent().getParcelableExtra("CONTACT");
			if (contact == null)
			{
				Toast.makeText(this, "Keys belong to contacts. Assign the configuration, then open chat settings to choose the default key.", Toast.LENGTH_LONG).show();
				return;
			}
			Intent intent = new Intent(this, KeysActivity.class);
			intent.putExtra("TYPE", "CONTACT");
			intent.putExtra("CONTACT", contact);
			startActivity(intent);
		});
		configurationDescription.setText("Both choices use the pinned OFDM PHY at 48 kHz mono float and preserve the 510-byte fragment limit. Messages over 510 bytes are sent in up to four frames. Encrypted messages require the matching supported algorithm and unlocked contact key. Transmission completion confirms local audio playback, not peer delivery.");
		save.setOnClickListener(v -> save());
	}

	private void save()
	{
		String callsignText = callsign.getText().toString().trim();
		if (callsignText.isEmpty()) callsignText = Configuration.DEFAULT_MODEM73_CALLSIGN;
		if (name.getText().toString().trim().isEmpty())
		{
			Toast.makeText(this, "Enter a configuration name.", Toast.LENGTH_LONG).show();
			return;
		}
		if (!validCallsign(callsignText))
		{
			Toast.makeText(this, "Enter a valid modem73 callsign (1–9 ASCII characters).", Toast.LENGTH_LONG).show();
			return;
		}
		int leadMs;
		try
		{
			leadMs = Integer.parseInt(voxLeadMs.getText().toString().trim());
		}
		catch (NumberFormatException invalid)
		{
			leadMs = -1;
		}
		if (leadMs < 100 || leadMs > 3000)
		{
			Toast.makeText(this, "VOX lead duration must be between 100 and 3000 milliseconds.", Toast.LENGTH_LONG).show();
			return;
		}
		if (encryption.isChecked() && !Modem73Crypto.supports(configuration.getEncryptionType()))
		{
			Toast.makeText(this, "Select a supported encryption algorithm.", Toast.LENGTH_LONG).show();
			return;
		}
		configuration.setName(name.getText().toString().trim());
		configuration.setDescription("Modem73 chat trial");
		configuration.setModulationType("MODEM73");
		configuration.setModem73Callsign(callsignText);
		int profileIndex = profile.getSelectedItemPosition();
		configuration.setModem73Profile(profileIndex >= 0 && profileIndex < PROFILE_VALUES.length ? PROFILE_VALUES[profileIndex] : Configuration.MODEM73_PROFILE_DEFAULT);
		configuration.setEncryption(encryption.isChecked());
		configuration.setV2FecEnabled(false);
		configuration.setModem73VoxEnabled(voxEnabled.isChecked());
		configuration.setModem73VoxLeadMs(leadMs);
		ConfigurationsDataSource db = new ConfigurationsDataSource(this);
		if (fresh)
		{
			int id = (int) db.create(configuration);
			if (id <= 0)
			{
				Toast.makeText(this, "Could not save MODEM73 configuration.", Toast.LENGTH_LONG).show();
				return;
			}
			configuration.setId(id);
		}
		else if (!db.update(configuration))
		{
			Toast.makeText(this, "Could not update MODEM73 configuration.", Toast.LENGTH_LONG).show();
			return;
		}
		if ("CONTACT".equalsIgnoreCase(getIntent().getStringExtra("TYPE")))
		{
			Intent result = new Intent();
			result.putExtra("OBJECT", (Parcelable) configuration);
			setResult(Activity.RESULT_OK, result);
		}
		finish();
	}

	private static boolean validCallsign(String value)
	{
		if (value == null || value.length() < 1 || value.length() > 9) return false;
		boolean nonSpace = false;
		for (int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			if (!(c == '/' || c == ' ' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) return false;
			if (c != ' ') nonSpace = true;
		}
		return nonSpace;
	}
}
