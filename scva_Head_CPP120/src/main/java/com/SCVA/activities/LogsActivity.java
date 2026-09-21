package com.SCVA.activities;

import com.SCVA.Utils.Utils;
import com.SCVA.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;

public class LogsActivity extends Activity
{
	private EditText logText;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_logs);

		logText = findViewById(R.id.logText);

        logText.setText(""+ new Utils().readLogFile(this));

	}
}