package com.SCVA.activities;

import com.SCVA.Utils.Utils;
import com.SCVA.GgWaveDiagnostics;
import com.SCVA.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;

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
        TextView ggWaveLog = findViewById(R.id.ggWaveLogText);
        ggWaveLog.setText(GgWaveDiagnostics.read(this));
        Button clear = findViewById(R.id.clearGgWaveLog);
        clear.setOnClickListener(v -> {
            GgWaveDiagnostics.clear(this);
            ggWaveLog.setText("GGWave log cleared");
        });

	}
}
