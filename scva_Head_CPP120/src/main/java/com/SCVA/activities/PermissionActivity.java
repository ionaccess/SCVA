package com.SCVA.activities;

/**
 * Created by §∞§ on 09/02/2026.
 */

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import com.SCVA.R;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionActivity extends Activity
{
	private static final int PERMISSION_REQUEST_CODE = 1001;

	private static final String[] REQUIRED_PERMISSIONS =
	{ Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.READ_LOGS,
			Manifest.permission.BROADCAST_STICKY, Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA };

	// private static final String[] REQUIRED_PERMISSIONS =
	// { Manifest.permission.WRITE_EXTERNAL_STORAGE };

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_permission);

		if (hasAllPermissions())
		{
			goToNextActivity();
		}
		else
		{

			requestPermissions();
		}
	}

	private boolean hasAllPermissions()
	{
		for (String permission : REQUIRED_PERMISSIONS)
		{
			if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
			{
				return false;
			}
		}
		return true;
	}

	private void requestPermissions()
	{
		ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == PERMISSION_REQUEST_CODE)
		{
			if (grantResults.length > 0 && allGranted(grantResults))
			{
				goToNextActivity();
			}
			else
			{
				// Permissions denied — handle gracefully
				// e.g. show dialog or close activity
				// runOnUiThread(() -> Toast.makeText(this, "Please grant all the permissions so
				// app can perform its full potential.", Toast.LENGTH_LONG).show());
				goToNextActivity();
			}
		}
	}

	private boolean allGranted(int[] grantResults)
	{
		for (int result : grantResults)
		{
			if (result != PackageManager.PERMISSION_GRANTED)
			{
				return false;
			}
		}
		return true;
	}

	private void goToNextActivity()
	{
		Intent intent = new Intent(this, AllChatsActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		startActivity(intent);
		finish();
	}
}
