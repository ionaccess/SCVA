package com.SCVA.activities;

import java.io.IOException;

import com.SCVA.R;
import com.SCVA.Utils.Utils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Created by §∞§ on 03/06/2026.
 */
public class CustomizeImageActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener
{
	private int width;

	private Utils utils;

	private int height;

	private ImageView image;

	double rate = 22.90526; // Using the rate from your example

	private long totalBytes;

	private CheckBox useEnc;

	private Uri selectedFile;

	private Button send, cancel;

	private Bitmap bitmapToSend;

	private String base64String;

	private TextView timeRequired, progress, resolution;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.image_customization);

		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image/*");
		startActivityForResult(Intent.createChooser(intent, "Select an Image to share"), 123);

		utils = new Utils();

		image = findViewById(R.id.image);
		progress = findViewById(R.id.progress);
		resolution = findViewById(R.id.resolution);
		timeRequired = findViewById(R.id.time_required);
		useEnc = findViewById(R.id.useEnc);
		useEnc.setOnCheckedChangeListener(this);

		SeekBar seekBar = findViewById(R.id.seekBar);
		seekBar.setOnSeekBarChangeListener(this);

		send = (Button) findViewById(R.id.send);
		send.setOnClickListener(this);

		cancel = (Button) findViewById(R.id.cancel);
		cancel.setOnClickListener(this);
	}

	@Override
	public void onClick(View view)
	{
		if (view == send)
		{
			Intent intent = new Intent();
            intent.setData(selectedFile);
            intent.putExtra("data", base64String);
            intent.putExtra("imageEncryption", useEnc.isChecked());
            setResult(RESULT_OK, intent);
			finish();
		}
		if (view == cancel)
		{
			setResult(RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b)
	{
		if (b)
		{
			totalBytes = (long) (utils.getBase64ImageSizeInKB(base64String) * 1024);
			double timeInSeconds = utils.getTimeRequired((long) (totalBytes * 1.888), rate);
			System.out.printf("Time required: %.2f seconds%n", timeInSeconds);

			timeRequired.setText("" + utils.formatMillis((long) (timeInSeconds * 1000)));
		}
		else
		{
			totalBytes = (long) (utils.getBase64ImageSizeInKB(base64String) * 1024);
			double timeInSeconds = utils.getTimeRequired(totalBytes, rate);
			System.out.printf("Time required: %.2f seconds%n", timeInSeconds);

			timeRequired.setText("" + utils.formatMillis((long) (timeInSeconds * 1000)));
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int i, boolean b)
	{
		int progressValue = seekBar.getProgress();
		progress.setText(progressValue + "%");

		Size size = utils.downgradeByPercentage(width, height, i);
		resolution.setText(size.getWidth() + " x " + size.getHeight());

		bitmapToSend = utils.createThumbnailFromUri(CustomizeImageActivity.this, selectedFile, size.getWidth(), size.getHeight());
		Drawable drawable = new BitmapDrawable(getResources(), bitmapToSend);

		ImageView image = findViewById(R.id.image);
		image.setBackground(drawable);

		base64String = utils.bitmapToBase64(bitmapToSend);

		totalBytes = (long) (utils.getBase64ImageSizeInKB(base64String) * 1024);
		double timeInSeconds = utils.getTimeRequired(totalBytes, rate);
		System.out.printf("Time required: %.2f seconds%n", timeInSeconds);

		timeRequired.setText("" + utils.formatMillis((long) (timeInSeconds * 1000)));
		// Utils.println("KB " + utils.getBase64ImageSizeInKB(base64String));
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 123 && resultCode == RESULT_OK)
		{
			selectedFile = data.getData();
			try
			{
				bitmapToSend = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedFile);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			Drawable drawable = new BitmapDrawable(getResources(), bitmapToSend);

			image.setBackground(drawable);

			width = bitmapToSend.getWidth();
			height = bitmapToSend.getHeight();
			resolution.setText(width + " X " + height);
			base64String = utils.bitmapToBase64(bitmapToSend);

			totalBytes = (long) (utils.getBase64ImageSizeInKB(base64String) * 1024);
			double timeInSeconds = utils.getTimeRequired(totalBytes, rate);
			System.out.printf("Time required: %.2f seconds%n", timeInSeconds);

			timeRequired.setText("" + utils.formatMillis((long) (timeInSeconds * 1000)));
			// Utils.println("KB " + utils.getBase64ImageSizeInKB(base64String));
		}
	}
}
