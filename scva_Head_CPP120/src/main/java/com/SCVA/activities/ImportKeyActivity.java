package com.SCVA.activities;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.Contents;
import com.SCVA.Utils.QRCodeEncoder;
import com.SCVA.Utils.Utils;
import com.SCVA.db.KeysDataSource;
import com.SCVA.encryption.Aes256Encryptor;
import com.SCVA.models.Contact;
import com.SCVA.models.Key;
import com.dlazaro66.qrcodereaderview.QRCodeReaderView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class ImportKeyActivity extends Activity implements View.OnClickListener, QRCodeReaderView.OnQRCodeReadListener
{
	private enum ActionType
	{
		CAMERA, FILE, TEXT
	};

	private ImageView save;
	private ImageView load, qrCode;
	private EditText name;
	private String keyText = "";
	private String plainKey = "";
	private boolean found = false;
	private KeysDataSource keyStore;
	private Bitmap qrCodeImage = null;
	private QRCodeReaderView qrCodeReaderView;
	private ActionType actionType = ActionType.CAMERA;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		setContentView(R.layout.activity_import_key);

		keyStore = new KeysDataSource(this);

		if (getIntent().getBooleanExtra("TYPE_CALL", false))
		{
			if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("TEXT")) actionType = ActionType.TEXT;
		}

		name = findViewById(R.id.name);
		qrCode = findViewById(R.id.qrCode);

		load = findViewById(R.id.keys);
		load.setOnClickListener(this);

		if (actionType == ActionType.CAMERA)
		{
			qrCodeReaderView = (QRCodeReaderView) findViewById(R.id.qrdecoderview);
			qrCodeReaderView.setOnQRCodeReadListener(this);
			// Use this function to enable/disable decoding
			qrCodeReaderView.setQRDecodingEnabled(true);
			// Use this function to change the autofocus interval (default is 5 secs)
			qrCodeReaderView.setAutofocusInterval(2000L);
			// Use this function to enable/disable Torch
			// qrCodeReaderView.setTorchEnabled(true);
			// Use this function to set front camera preview
			// qrCodeReaderView.setFrontCamera();
			// Use this function to set back camera preview
			qrCodeReaderView.setBackCamera();
		}

		save = findViewById(R.id.save);
		save.setOnClickListener(this);

		if (actionType == ActionType.TEXT)
		{
			keyText = getIntent().getStringExtra("KEY_TEXT");
			Utils.println(keyText);
			if (keyText.length() > 0)
			{
				found = true;
				showPasswordDialog();
			}
		}
		if (Constants.devMode)
		{
			name.setText("FileKey");
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == load)
		{
			openFilePicker();
		}
		if (view == save)
		{
			if (keyText.length() == 0)
			{
				Toast.makeText(ImportKeyActivity.this, "Key is empty please Scan OR Import to proceed.", Toast.LENGTH_SHORT).show();
				return;
			}
			if (name.getText().toString().length() < 5)
			{
				Toast.makeText(ImportKeyActivity.this, "Please enter at least 5 characters", Toast.LENGTH_SHORT).show();
				return;
			}
			Key key = new Key();
			key.setDefault(false);

			key.setName(name.getText().toString());
			key.setKeyText("" + keyText);
			key.setCreatedOn("" + System.currentTimeMillis());
			try
			{
				if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("CONTACT"))
				{
					Contact tmpContact = getIntent().getParcelableExtra("CONTACT");
					key.setUserGroupId(tmpContact.getId());
					if (keyStore.getUserKeyCount(tmpContact.getId()) == 0) key.setDefault(true);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			if (actionType == ActionType.TEXT)
			{
				Contact tmpContact = getIntent().getParcelableExtra("CONTACT");
				key.setUserGroupId(tmpContact.getId());
			}
			keyStore.create(key);

			Toast.makeText(ImportKeyActivity.this, "Key is Imported.", Toast.LENGTH_LONG).show();
			ImportKeyActivity.this.finish();
		}
	}

	// Called when a QR is decoded
	// "text" : the text encoded in QR
	// "points" : points where QR control points are placed in View
	@Override
	public void onQRCodeRead(String text, PointF[] points)
	{
		keyText = text;
		Utils.println(keyText);
		if (!found)
		{
			found = true;
			showPasswordDialog();
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (actionType == ActionType.CAMERA)
		{
			qrCodeReaderView.startCamera();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (actionType == ActionType.CAMERA)
		{
			qrCodeReaderView.stopCamera();
		}
	}

	public void showPasswordDialog()
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.reveal_key_dialog);
		dialog.setCancelable(true);

		if (Constants.devMode)
		{
			((EditText) dialog.findViewById(R.id.password)).setText("abc123");
		}

		Button unlock = (Button) dialog.findViewById(R.id.unlock);
		unlock.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();

				Aes256Encryptor aes256Encryptor = new Aes256Encryptor(new Utils().generateMd5(((EditText) dialog.findViewById(R.id.password)).getText().toString()));
				plainKey = aes256Encryptor.decrypt(keyText);

				if (actionType == ActionType.TEXT)
				{
					textKeyImport(plainKey);
				}
				if (plainKey != null)
				{
					Toast.makeText(ImportKeyActivity.this, "Key Unlocked.", Toast.LENGTH_LONG).show();
				}
				else
				{
					found = false;
					Toast.makeText(ImportKeyActivity.this, "Password error.", Toast.LENGTH_LONG).show();
				}
			}
		});
		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
			}
		});
		dialog.show();
	}

	public void loadQRImage(Bitmap bitmap)
	{
		try
		{
			// 1. Get pixels
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int[] pixels = new int[width * height];
			bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

			// 2. Create LuminanceSource
			LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
			BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

			// 3. Decode
			Reader reader = new QRCodeReader();
			Result result = reader.decode(binaryBitmap);

			// 4. Result
			String qrCodeText = result.getText();
			Utils.println("QR_CODE  Content: " + qrCodeText);

			keyText = qrCodeText;
			Utils.println(keyText);
			if (result.getText().length() > 0)
			{
				found = true;
				showPasswordDialog();
			}
		}
		catch (NotFoundException | ChecksumException | FormatException e)
		{
			e.printStackTrace();
			Utils.println("QR_CODE  Decoding failed");
			Toast.makeText(ImportKeyActivity.this, "Invalid Key Error.", Toast.LENGTH_LONG).show();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// Inside your activity
	private void decodeQRCodeFromUri(Uri uri)
	{
		try
		{
			// InputStream inputStream = getContentResolver().openInputStream(uri);
			// Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

			Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
			loadQRImage(bitmap);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void openFilePicker()
	{
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("image/*"); // You can restrict to specific types like "image/*" or "application/pdf"

		// Launch using the modern Activity Result API (preferred)
		startActivityForResult(Intent.createChooser(intent, "Select a file"), 123);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 123 && resultCode == RESULT_OK)
		{
			Uri selectedFile = data.getData(); // The uri with the location of the file
			decodeQRCodeFromUri(selectedFile);
		}
	}

	public void textKeyImport(String key)
	{
		qrCodeImage = getQrCode(key, 256);
		qrCode.setImageBitmap(qrCodeImage);
		// qrCodeReaderView.setVisibility(View.GONE);
		qrCode.setVisibility(View.VISIBLE);
	}

	public Bitmap getQrCode(String key, int dimension)
	{
		try
		{
			QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(key, null, Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), dimension);
			return qrCodeEncoder.encodeAsBitmap();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
}
