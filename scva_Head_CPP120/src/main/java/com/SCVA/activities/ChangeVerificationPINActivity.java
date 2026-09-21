package com.SCVA.activities;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.db.SettingsDataSource;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class ChangeVerificationPINActivity extends BaseActivity implements View.OnClickListener
{
	private boolean firstRun = false;

	private Button confirm;

	private TextView current;

	private SettingsDataSource settingsDataSource;

	private EditText pinCodeView, newPin, confirmPin;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.activity_change_pin);

		settingsDataSource = new SettingsDataSource(this);

		current = findViewById(R.id.current);
		pinCodeView = findViewById(R.id.pinCodeView);
		newPin = findViewById(R.id.newPin);
		confirmPin = findViewById(R.id.confirmPin);
		confirm = findViewById(R.id.confirm);
		confirm.setOnClickListener(this);

		firstRun = settingsDataSource.getValueAgainstKey(Constants.SETTINGS_FIRST_RUN, "1").equalsIgnoreCase("1");
		if (firstRun)
		{
			((TextView) findViewById(R.id.title)).setText(getString(R.string.set_your_pin));
			current.setText(getString(R.string.please_type_a_4_digit_pin_code_to_secure_your_data));
			current.setGravity(Gravity.CENTER);
			pinCodeView.setVisibility(View.GONE);
			confirm.setText(getString(R.string.confirm));

            if (Constants.devMode)
            {
                newPin.setText("1234");
                confirmPin.setText("1234");
            }
        }
	}

	@Override
	public void onClick(View view)
	{
		if (view == confirm)
		{
			if (firstRun)
			{
				if (newPin.getText().toString().length() < 4)
				{
					Toast.makeText(this, "PIN should be exactly 4 digit long", Toast.LENGTH_SHORT).show();
					return;
				}
				if (!newPin.getText().toString().equals(confirmPin.getText().toString()))
				{
					Toast.makeText(this, "New PIN didn't match with confirm PIN", Toast.LENGTH_SHORT).show();
					return;
				}
				else
				{
					settingsDataSource.updateSettingValue(Constants.SETTINGS_USER_PIN, newPin.getText().toString());
					settingsDataSource.updateSettingValue(Constants.SETTINGS_FIRST_RUN, "0");
					Toast.makeText(this, "PIN saved successfully.", Toast.LENGTH_SHORT).show();
					Intent intent = new Intent(this, PermissionActivity.class);
					intent.putExtra("TYPE", "ALL");
					intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					startActivity(intent);
					finish();
				}
			}
			else
			{
				String pinCode = settingsDataSource.getValueAgainstKey(Constants.SETTINGS_USER_PIN, "0");
				if (!pinCodeView.getText().toString().equalsIgnoreCase(pinCode))
				{
					Toast.makeText(this, "Type valid current PIN to proceed.", Toast.LENGTH_SHORT).show();
					return;
				}
				if (newPin.getText().toString().length() < 4)
				{
					Toast.makeText(this, "PIN should be exactly 4 digit long", Toast.LENGTH_SHORT).show();
					return;
				}
				if (!newPin.getText().toString().equals(confirmPin.getText().toString()))
				{
					Toast.makeText(this, "New PIN didn't match with confirm PIN", Toast.LENGTH_SHORT).show();
					return;
				}
				else
				{
					settingsDataSource.updateSettingValue(Constants.SETTINGS_USER_PIN, newPin.getText().toString());
					Toast.makeText(this, "PIN changed successfully.", Toast.LENGTH_SHORT).show();
					finish();
				}
			}
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

}