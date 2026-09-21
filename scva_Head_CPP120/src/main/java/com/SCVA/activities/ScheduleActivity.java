package com.SCVA.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.Utils;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.models.TextMessage;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by §∞§ on 03/06/2026.
 */
public class ScheduleActivity extends Activity implements View.OnClickListener
{
	private enum CallType
	{
		DELAYED, SCHEDULE, BEACON
	};

	private CallType callType;

	private Button pickTime;

	private ImageView save;

	private TextView time;

	private long timeToExec = 0;

	private EditText msgToSend, delay;

	private Calendar alarmCalendar;

	private SettingsDataSource settingsDataSource;

	private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-dd-MM hh:mm a", Locale.US);

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.schedule_activity);

		settingsDataSource = new SettingsDataSource(this);

		msgToSend = findViewById(R.id.msgToSend);
		// msgToSend.setText(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_MESSAGE,
		// ""));

		time = findViewById(R.id.time);
		// time.setText(formatter.format(Long.parseLong(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_TIME,
		// ""))));

		delay = findViewById(R.id.delay);
		// delay.setText("" +
		// Integer.parseInt(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_DELAY,
		// "")));

		pickTime = findViewById(R.id.pickTime);
		pickTime.setOnClickListener(this);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);

		showTransmitOptions();
	}

	@Override
	public void onClick(View view)
	{
		if (view == save)
		{
			if (msgToSend.getText().toString().length() == 0)
			{
				Toast.makeText(this, "Type some text to send", Toast.LENGTH_SHORT).show();
				return;
			}
			if (callType == CallType.BEACON || callType == CallType.DELAYED)
			{
				if (delay.getText().toString().length() == 0)
				{
					Toast.makeText(this, "Type some delay time", Toast.LENGTH_SHORT).show();
					return;
				}
			}
			if (callType == CallType.SCHEDULE)
			{
				if (time.getText().toString().length() == 0)
				{
					Toast.makeText(this, "Set some time to exec", Toast.LENGTH_SHORT).show();
					return;
				}
			}
			settingsDataSource.updateSettingValue(Constants.SETTINGS_SCHEDULE_MESSAGE, "" + msgToSend.getText().toString());
			settingsDataSource.updateSettingValue(Constants.SETTINGS_SCHEDULE_DELAY, "" + delay.getText().toString());

			Intent intent = new Intent();
			intent.putExtra("DELAY", delay.getText().toString());
			intent.putExtra("MESSAGE", msgToSend.getText().toString());
			intent.putExtra("TIME_TO_EXEC", timeToExec);
			setResult(RESULT_OK, intent);
			finish();
		}
		if (view == pickTime)
		{
			Utils.hideKeyboard(this, msgToSend);
			showDatePicker();
		}
	}

	private void showDatePicker()
	{
		final Calendar c = Calendar.getInstance();
		alarmCalendar = Calendar.getInstance();

		new DatePickerDialog(this, R.style.DialogTheme, (view, year, month, day) -> {
			alarmCalendar.set(year, month, day);
			showTimePicker(); // Chain to time picker
		}, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
	}

	private void showTimePicker()
	{
		final Calendar c = Calendar.getInstance();

		new TimePickerDialog(this, R.style.DialogTheme, (view, hour, minute) -> {
			alarmCalendar.set(Calendar.HOUR_OF_DAY, hour);
			alarmCalendar.set(Calendar.MINUTE, minute);
			alarmCalendar.set(Calendar.SECOND, 0);

			settingsDataSource.updateSettingValue(Constants.SETTINGS_SCHEDULE_TIME, "" + alarmCalendar.getTimeInMillis());
            timeToExec = alarmCalendar.getTimeInMillis();
			time.setText(formatter.format(alarmCalendar.getTimeInMillis()));
		}, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

	public void initView()
	{
		if (callType == CallType.DELAYED)
		{
			// Delay yes single send
			findViewById(R.id.timeView).setVisibility(View.GONE);
		}
		if (callType == CallType.SCHEDULE)
		{
			// Schedule could also be reoccurring, like every Monday at 3pm
			findViewById(R.id.delayView).setVisibility(View.GONE);

		}
		if (callType == CallType.BEACON)
		{
			// Beacon yes exactly, repeat n minutes until canceled
			findViewById(R.id.timeView).setVisibility(View.GONE);
		}
	}

	public void showTransmitOptions()
	{
		String[] names = getResources().getStringArray(R.array.schedule_list);
		int checkedIndex = Integer.parseInt(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_SCHEDULE_SELECTION, "0"));
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this).setCancelable(false);
		builder.setTitle("Select Type").setNegativeButton("CLOSE", (dialog, which) -> {
			finish();
		}).setItems(R.array.schedule_list, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				if (which == 0)
				{
					callType = CallType.DELAYED;
				}
				if (which == 1)
				{
					callType = CallType.SCHEDULE;
				}
				if (which == 2)
				{
					callType = CallType.BEACON;
				}
				settingsDataSource.updateSettingValue(Constants.SETTINGS_SCHEDULE_SELECTION, "" + which);
				initView();
				dialog.dismiss();
			}
		});
		android.app.AlertDialog dialog = builder.create();
		dialog.show();
	}
}
