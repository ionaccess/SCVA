package com.SCVA.activities;

import com.SCVA.Processor;
import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;
import com.SCVA.config;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ConfigurationsDataSource;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.MessagesDataSource;
import com.SCVA.db.SettingsDataSource;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class PinVerificationActivity extends BaseActivity implements View.OnClickListener, TextWatcher
{
	private int wrongCount = 0;

	private ImageView burner;

	private Button confirm;

	private String pinCode = "1234";

	private EditText pinCodeView;

	private TextView first, second, third, fourth;

	private SettingsDataSource settingsDataSource;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.activity_pin_verification);

		settingsDataSource = new SettingsDataSource(this);
		if (settingsDataSource.getValueAgainstKey(Constants.SETTINGS_FIRST_RUN, "1").equalsIgnoreCase("1"))
		{
			Intent intent = new Intent(this, ChangeVerificationPINActivity.class);
			intent.putExtra("TYPE", "ALL");
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
			finish();
		}

		burner = findViewById(R.id.burner);
		pinCode = settingsDataSource.getValueAgainstKey(Constants.SETTINGS_USER_PIN, "0");

		LinearLayout otpContainer = findViewById(R.id.otpContainer);

		pinCodeView = findViewById(R.id.pinCodeView);
		pinCodeView.addTextChangedListener(this);

		// Open keyboard when user taps boxes
		otpContainer.setOnClickListener(v -> {
			pinCodeView.requestFocus();
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null)
			{
				imm.showSoftInput(pinCodeView, InputMethodManager.SHOW_IMPLICIT);
			}
		});

		first = findViewById(R.id.first);
		second = findViewById(R.id.second);
		third = findViewById(R.id.third);
		fourth = findViewById(R.id.fourth);

		confirm = findViewById(R.id.confirm);
		confirm.setOnClickListener(this);

		requestFocus();

		if (Constants.devMode)
		{
			pinCodeView.setText("1234");
			confirm.performClick();
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == confirm)
		{
			Utils.hideKeyboard(this, pinCodeView);

			String pin = pinCodeView.getText().toString();
			if (pinCode.equalsIgnoreCase(pin))
			{
				Utils.hideKeyboard(this, first);
				Intent intent = new Intent(this, PermissionActivity.class);
				intent.putExtra("TYPE", "ALL");
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
				finish();
			}
			else
			{
				wrongCount++;
				Toast.makeText(this, "Wrong Pin. " + wrongCount, Toast.LENGTH_SHORT).show();

				first.setText("");
				second.setText("");
				third.setText("");
				fourth.setText("");
				pinCodeView.setText("");

				if (wrongCount > 3)
				{
					burnEveryThing();
				}
				else
				{
					requestFocus();
				}
			}
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

	@Override
	public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2)
	{

	}

	@Override
	public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
	{
		updateOtpBoxes(charSequence.toString());
	}

	@Override
	public void afterTextChanged(Editable editable)
	{

	}

	private void updateOtpBoxes(String otp)
	{
		first.setText("");
		second.setText("");
		third.setText("");
		fourth.setText("");

		if (otp.length() > 0)
		{
			first.setText(String.valueOf(otp.charAt(0)));
		}

		if (otp.length() > 1)
		{
			second.setText(String.valueOf(otp.charAt(1)));
		}

		if (otp.length() > 2)
		{
			third.setText(String.valueOf(otp.charAt(2)));
		}

		if (otp.length() > 3)
		{
			fourth.setText(String.valueOf(otp.charAt(3)));

			// OTP Complete
			confirm.setEnabled(true);
		}
	}

	public void requestFocus()
	{
		// Auto focus on start
		pinCodeView.requestFocus();
		pinCodeView.post(() -> {

			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

			if (imm != null)
			{
				imm.showSoftInput(pinCodeView, InputMethodManager.SHOW_IMPLICIT);
			}
		});
	}

	public void burnEveryThing()
	{
		burner.setVisibility(View.VISIBLE);
		burner.setBackgroundResource(R.drawable.fire_animation);
		AnimationDrawable frameAnimation = (AnimationDrawable) burner.getBackground();
		frameAnimation.start();

		int totalDuration = 0;
		for (int i = 0; i < frameAnimation.getNumberOfFrames(); i++)
		{
			totalDuration += frameAnimation.getDuration(i);
		}

		// Trigger complete event via Handler
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
		{
			@Override
			public void run()
			{
				onAnimationComplete();
			}
		}, totalDuration * 2);

		Utils utils = new Utils();
		// Burning the keys
		new KeysDataSource(this).burnKeyStore();

		new SettingsDataSource(this).burnSettings();

		new ChatsDataSource(this).burnChats();

		new ContactsDataSource(this).burnContacts();

		new ConfigurationsDataSource(this).burnConfigurations();

		new MessagesDataSource(this).burnMessages();

		utils.showNotification(this, "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25");

		// Burning the Preferences
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.edit().clear().apply();

		// Burning the BackupFile
		config.burnBackupFile("SettingsBackup.bin");

		// Burning the logs
		utils.burnLogs(this);

		// Stop the Modem and Listening Service
		if (GS.gI().isProcessorON())
		{
			stopService(new Intent(this, Processor.class));
			GS.gI().setProcessorON(false);
		}
		Toast.makeText(this, "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25", Toast.LENGTH_LONG).show();
		utils.hideNotification(this);

		// // Exiting the app dumps RAM
		// finish();
		// android.os.Process.killProcess(android.os.Process.myPid());
	}

	private void onAnimationComplete()
	{
		// Exiting the app dumps RAM
		finish();
		android.os.Process.killProcess(android.os.Process.myPid());
	}
}