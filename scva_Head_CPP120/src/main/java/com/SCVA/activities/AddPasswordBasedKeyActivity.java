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
import com.google.zxing.BarcodeFormat;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class AddPasswordBasedKeyActivity extends Activity implements View.OnClickListener
{
	private ImageView save;

	private EditText name;

	private ImageView qrCode;

	private KeysDataSource keyStore;

	private int keyLenght = 32;

	private boolean blur = true;

	private Bitmap qrCodeImage = null;

	private Key key = null;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_password_key);

		name = findViewById(R.id.name);
		keyStore = new KeysDataSource(this);
		qrCode = findViewById(R.id.qrCode);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);

		showPasswordDialog();
	}

	@Override
	public void onClick(View view)
	{
		if (view == save)
		{
			Utils.println("Random Key");
			if (name.getText().toString().length() < 5)
			{
				Toast.makeText(AddPasswordBasedKeyActivity.this, "Please enter at least 5 characters", Toast.LENGTH_LONG).show();
				return;
			}

			key.setName(name.getText().toString());
			keyStore.create(key);

			Toast.makeText(AddPasswordBasedKeyActivity.this, "Key saved.", Toast.LENGTH_LONG).show();
			finish();
		}
	}

	public Bitmap getQrCode(String key, int dimension)
	{
		try
		{
			// String b64data = Base64.toBase64String(data);
			QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(key, null, Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(), dimension);
			return qrCodeEncoder.encodeAsBitmap();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public void showPasswordDialog()
	{
		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setCancelable(true);
		dialog.setContentView(R.layout.password_dialog);

		if (Constants.devMode)
		{
			((EditText) dialog.findViewById(R.id.password)).setText("abc123");
			((EditText) dialog.findViewById(R.id.repeatPassword)).setText("abc123");
		}

		Button cancel = (Button) dialog.findViewById(R.id.cancel);
		cancel.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				AddPasswordBasedKeyActivity.this.finish();
			}
		});
		Button continu = (Button) dialog.findViewById(R.id.continu);
		continu.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dialog.dismiss();
				Utils.println("Password Key");
				if (!((EditText) dialog.findViewById(R.id.password)).getText().toString()
						.equalsIgnoreCase(((EditText) dialog.findViewById(R.id.repeatPassword)).getText().toString()))
				{
					Toast.makeText(AddPasswordBasedKeyActivity.this, "Password did not matched.", Toast.LENGTH_LONG).show();
					return;
				}
				String keyText = new Utils().generateMd5(((EditText) dialog.findViewById(R.id.password)).getText().toString());
				Aes256Encryptor aes256Encryptor = new Aes256Encryptor(keyText);

				key = new Key();
				key.setDefault(false);

				String kk = aes256Encryptor.encrypt(keyText);
				key.setKeyText("" + kk);

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
				}
				Utils.println(kk);
				qrCodeImage = getQrCode(key.getKeyText(), 180);
				qrCode.setImageBitmap(qrCodeImage);

				Toast.makeText(AddPasswordBasedKeyActivity.this, "Password Based Key Generated", Toast.LENGTH_SHORT).show();
			}
		});
		dialog.show();
	}
}
