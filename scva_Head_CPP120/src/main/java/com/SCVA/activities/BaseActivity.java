package com.SCVA.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.Utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by §∞§ on 25/02/2026.
 */
public class BaseActivity extends AppCompatActivity
{
	// @Override
	// protected void attachBaseContext(Context baseContext)
	// {
	// super.attachBaseContext(context);
	// }
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

		if (!Constants.devMode) errorLogToFile(this);
		// ((TextView) findViewById(R.id.actions)).setText("Crash");
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

	public void errorLogToFile(final Context context)
	{
		Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
		{
			@Override
			public void uncaughtException(Thread thread, Throwable ex)
			{
				PrintWriter pw;
				try
				{
					StringWriter errors = new StringWriter();
					ex.printStackTrace(new PrintWriter(errors));
					// return errors.toString();
					Utils.println(errors.toString());
					Utils.crashToFile(getString(R.string.txt_app_name), errors.toString());
					// WhatsAppHelper.sendTextMessage(BaseActivity.this, Constants.logNumber, errors.toString());
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		});
	}
}