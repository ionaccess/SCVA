package com.SCVA.activities;

import com.SCVA.BackendSelection;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Configuration editor for the Phone Modem GGWave backend. */
public final class GGWaveConfigurationActivity extends BaseActivity implements View.OnClickListener
{
	private static final String[] SPEED_TOKENS =
	{ "AUDIBLE_NORMAL", "AUDIBLE_FAST", "AUDIBLE_FASTEST", "ULTRASOUND_NORMAL", "ULTRASOUND_FAST", "ULTRASOUND_FASTEST", "DT_NORMAL", "DT_FAST", "DT_FASTEST" };
	private static final String[] SPEED_LABELS =
	{ "Audible Normal", "Audible Fast", "Audible Fastest", "U Normal", "U Fast", "U Fastest", "DT Normal", "DT Fast", "DT Fastest" };
	private Configuration configuration;
	private ConfigurationsDataSource configurations;
	private EditText name, description;
	private CheckBox encryption;
	private TextView speed, receiveProtocols, encryptionType;
	private boolean isNew;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.ggwave_configuration_activity);
		configurations = new ConfigurationsDataSource(this);
		configuration = getIntent().getParcelableExtra("OBJECT");

		isNew = configuration == null;
		if (isNew)
		{
			configuration = new Configuration();
			configuration.setName("GGWave DT Fastest");
			configuration.setDescription("");
			configuration.setModulationType("GGWave");
			configuration.setV2FecEnabled(false);
			configuration.setGgWaveSpeed("DT_FASTEST");
			configuration.setGgWaveRxProtocols("DT_FASTEST");
			configuration.setFrequency(0);
			configuration.setrSErrorCorrection("1");
			configuration.setEncryption(false);
			configuration.setEncryptionType("aes");
			configuration.setAudioGain(0);
			configuration.setSquelch(0);
			configuration.setAfc(false);
			configuration.setrXRsID(false);
			configuration.settXRsID(false);
		}

		name = findViewById(R.id.ggConfigName);
		description = findViewById(R.id.ggConfigDescription);
		encryption = findViewById(R.id.ggConfigEncryption);
		speed = findViewById(R.id.ggConfigSpeed);
		receiveProtocols = findViewById(R.id.ggConfigReceiveProtocols);
		encryptionType = findViewById(R.id.ggConfigEncryptionType);

		ImageView save = findViewById(R.id.save);
		View key = findViewById(R.id.keys);

		if (save != null) save.setOnClickListener(this);
		if (key != null) key.setOnClickListener(this);
		speed.setOnClickListener(this);
		receiveProtocols.setOnClickListener(this);
		encryptionType.setOnClickListener(this);
		name.setText(configuration.getName());
		description.setText(configuration.getDescription());
		encryption.setChecked(configuration.isEncryption());
		speed.setText(speedLabel(configuration.getGgWaveSpeed()));
		receiveProtocols.setText(receiveLabel(configuration.getGgWaveRxProtocols(), configuration.getGgWaveSpeed()));
		encryptionType.setText(encryptionLabel(configuration.getEncryptionType()));
	}

	@Override
	public void onClick(View view)
	{
		if (view.getId() == R.id.ggConfigSpeed)
		{
			chooseSpeed();
		}
		else if (view.getId() == R.id.ggConfigReceiveProtocols)
		{
			chooseReceiveProtocols();
		}
		else if (view.getId() == R.id.ggConfigEncryptionType)
		{
			chooseEncryptionType();
		}
		else if (view.getId() == R.id.save)
		{
			saveConfiguration();
		}
		else if (view.getId() == R.id.keys)
		{
			Intent intent = new Intent(this, KeysActivity.class);
			intent.putExtra("CONTACT", (Parcelable) getIntent().getParcelableExtra("CONTACT"));
			intent.putExtra("TYPE", "CONTACT");
			startActivity(intent);
		}
	}

	private void chooseSpeed()
	{
		int selected = 0;
		for (int i = 0; i < SPEED_TOKENS.length; i++)
			if (SPEED_TOKENS[i].equals(configuration.getGgWaveSpeed())) selected = i;
		new AlertDialog.Builder(this).setTitle("GGWave transmit protocol").setSingleChoiceItems(SPEED_LABELS, selected, (dialog, which) -> {
			configuration.setGgWaveSpeed(SPEED_TOKENS[which]);
			speed.setText(SPEED_LABELS[which]);
			dialog.dismiss();
		}).setNegativeButton("CANCEL", null).show();
	}

	private void chooseReceiveProtocols()
	{
		String configured = configuration.getGgWaveRxProtocols();
		if (configured == null || configured.length() == 0) configured = configuration.getGgWaveSpeed();
		final boolean[] checked = new boolean[SPEED_TOKENS.length];
		for (String token : configured.split(",", -1))
			for (int i = 0; i < SPEED_TOKENS.length; i++)
				if (SPEED_TOKENS[i].equals(token)) checked[i] = true;
		new AlertDialog.Builder(this).setTitle("Listen for GGWave protocols").setMultiChoiceItems(SPEED_LABELS, checked, (dialog, which, value) -> checked[which] = value)
				.setPositiveButton("SAVE", (dialog, which) -> {
					StringBuilder tokens = new StringBuilder();
					int count = 0;
					for (int i = 0; i < checked.length; i++)
						if (checked[i])
						{
							if (count++ > 0) tokens.append(',');
							tokens.append(SPEED_TOKENS[i]);
						}
					if (count == 0)
					{
						Toast.makeText(this, "Select at least one receive protocol.", Toast.LENGTH_LONG).show();
						return;
					}
					configuration.setGgWaveRxProtocols(tokens.toString());
					receiveProtocols.setText(receiveLabel(tokens.toString(), configuration.getGgWaveSpeed()));
				}).setNegativeButton("CANCEL", null).show();
	}

	private void chooseEncryptionType()
	{
		String[] labels = getResources().getStringArray(R.array.enc_algos);
		String[] values = getResources().getStringArray(R.array.enc_algos_values);
		int selected = 0;
		for (int i = 0; i < values.length; i++)
			if (values[i].equalsIgnoreCase(configuration.getEncryptionType())) selected = i;
		final int current = selected;
		new AlertDialog.Builder(this).setTitle(R.string.encryption_algorithm).setSingleChoiceItems(labels, current, (dialog, which) -> {
			configuration.setEncryptionType(values[which]);
			encryptionType.setText(labels[which]);
			dialog.dismiss();
		}).setNegativeButton("CANCEL", null).show();
	}

	private void saveConfiguration()
	{
		String configName = name.getText().toString().trim();
		if (configName.length() == 0)
		{
			Toast.makeText(this, "Please enter a configuration name.", Toast.LENGTH_SHORT).show();
			return;
		}
		if (!BackendSelection.isKnownGgWaveSpeed(configuration.getGgWaveSpeed()))
		{
			Toast.makeText(this, "Select a valid GGWave transmit protocol.", Toast.LENGTH_LONG).show();
			return;
		}
		String rx = configuration.getGgWaveRxProtocols() == null ? configuration.getGgWaveSpeed() : configuration.getGgWaveRxProtocols();
		if (!BackendSelection.areKnownGgWaveRxProtocols(rx))
		{
			Toast.makeText(this, "Select at least one valid GGWave receive protocol.", Toast.LENGTH_LONG).show();
			return;
		}
		configuration.setName(configName);
		configuration.setDescription(description.getText().toString());
		configuration.setModulationType("GGWave");
		configuration.setV2FecEnabled(false);
		configuration.setEncryption(encryption.isChecked());
		if (isNew)
		{
			configuration.setId((int) configurations.create(configuration));
			Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
		}
		else
		{
			configurations.update(configuration);
			Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
		}
		if ("CONTACT".equalsIgnoreCase(getIntent().getStringExtra("TYPE")))
		{
			Intent result = new Intent();
			result.putExtra("OBJECT", (Parcelable) configuration);
			setResult(Activity.RESULT_OK, result);
		}
		finish();
	}

	private static String receiveLabel(String tokenList, String txToken)
	{
		if (tokenList == null || tokenList.length() == 0) return speedLabel(txToken);
		String[] tokens = tokenList.split(",", -1);
		if (tokens.length == 1) return speedLabel(tokens[0]);
		return tokens.length + " protocols selected";
	}

	private static String speedLabel(String token)
	{
		for (int i = 0; i < SPEED_TOKENS.length; i++)
			if (SPEED_TOKENS[i].equals(token)) return SPEED_LABELS[i];
		return "Unknown protocol: " + token;
	}

	private String encryptionLabel(String value)
	{
		String[] names = getResources().getStringArray(R.array.enc_algos);
		String[] values = getResources().getStringArray(R.array.enc_algos_values);
		for (int i = 0; i < values.length; i++)
			if (values[i].equalsIgnoreCase(value)) return names[i];
		return names.length > 0 ? names[0] : "AES";
	}
}
