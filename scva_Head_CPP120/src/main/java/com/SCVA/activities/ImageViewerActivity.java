package com.SCVA.activities;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.File;
import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.models.TextMessage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by §∞§ on 03/06/2026.
 */
public class ImageViewerActivity extends Activity implements View.OnClickListener
{
	private ImageView img, close, save;

	private Bitmap decodedImage;

	private TextMessage textMessage;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_image_viewer);

		textMessage = getIntent().getParcelableExtra("OBJECT");

		img = findViewById(R.id.img);

		try
		{
			byte[] imageBytes = Base64.decode(textMessage.getMessage(), Base64.DEFAULT);
			decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
			img.setImageBitmap(decodedImage);
		}
		catch (Exception e)
		{
			if (Constants.devMode) e.printStackTrace();
		}

		close = findViewById(R.id.close);
		close.setOnClickListener(this);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);
	}

	@Override
	public void onClick(View view)
	{
		if (view == close)
		{
			finish();
		}
		if (view == save)
		{
			saveBitmapToDownloads(decodedImage, "" + textMessage.getContactId() + "_" + textMessage.getDate() + "_" + textMessage.getTime());
		}
	}

	public void saveBitmapToDownloads(Bitmap bitmap, String fileName)
	{
		// Appends .png extension if missing
		if (!fileName.endsWith(".png"))
		{
			fileName += ".png";
		}

		OutputStream outputStream = null;
		Uri fileUri = null;

		try
		{
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
			{
				// Android 10 (API 29) and above: Use MediaStore (Scoped Storage)
				ContentResolver resolver = getContentResolver();
				ContentValues contentValues = new ContentValues();

				contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
				contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
				// Saves directly to the public "Download" directory
				contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

				// Insert metadata into the MediaStore collection
				fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

				if (fileUri != null)
				{
					outputStream = resolver.openOutputStream(fileUri);
				}
			}
			else
			{
				// Android 9 (API 28) and below: Legacy file storage
				File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
				if (!downloadsDir.exists())
				{
					downloadsDir.mkdirs();
				}
				File file = new File(downloadsDir, fileName);
				outputStream = new FileOutputStream(file);
			}

			// Compress bitmap and write it to the output stream
			if (outputStream != null)
			{
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
				outputStream.flush();
				Toast.makeText(this, "Image saved to Downloads!", Toast.LENGTH_SHORT).show();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
		}
		finally
		{
			if (outputStream != null)
			{
				try
				{
					outputStream.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
