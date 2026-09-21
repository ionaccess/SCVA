package com.SCVA.activities;

import java.util.ArrayList;

import com.SCVA.R;
import com.SCVA.Interfaces.CallBack;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.Contents;
import com.SCVA.Utils.QRCodeEncoder;
import com.SCVA.Utils.SketchSheetView;
import com.SCVA.Utils.Utils;
import com.SCVA.db.KeysDataSource;
import com.SCVA.models.Contact;
import com.SCVA.models.Key;
import com.SCVA.encryption.Aes256Encryptor;
import com.google.zxing.BarcodeFormat;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class AddKeyActivity extends Activity implements View.OnClickListener, CallBack
{
	private ImageView save;

	private EditText name;

	private String rand = "";

	private ImageView qrCode;

	private KeysDataSource keyStore;

	private int keyLenght = 32;

	private boolean blur = true;

	private ProgressBar progressBar;

	private Bitmap qrCodeImage = null;

	private LinearLayout signatureView;

	public static SharedPreferences mysp;

	private SketchSheetView canvas;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_key);

		mysp = PreferenceManager.getDefaultSharedPreferences(this);

		name = findViewById(R.id.name);
		keyStore = new KeysDataSource(this);

		signatureView = findViewById(R.id.signatureView);
		progressBar = findViewById(R.id.progressBar);
		clearSignature(null);

		qrCode = findViewById(R.id.qrCode);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);
	}

	@Override
	public void onClick(View view)
	{
		if (view == save)
		{
			Utils.println("Random Key");
			if (name.getText().toString().length() < 5)
			{
				Toast.makeText(AddKeyActivity.this, "Please enter at least 5 characters", Toast.LENGTH_SHORT).show();
				return;
			}
			showPasswordDialog();
		}
	}

	public void clearSignature(View view)
	{
		signatureView.removeAllViews();
		canvas = new SketchSheetView(this);
		canvas.setCallBack(this);
		canvas.setEditable(true);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		signatureView.addView(canvas, params);
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
		dialog.setCancelable(false);
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
					Toast.makeText(AddKeyActivity.this, "Password did not matched.", Toast.LENGTH_LONG).show();
					return;
				}

				// RandomString rn = new RandomString(keyLenght);
				// String kk = rn.nextString();

				Key key = new Key();
				key.setDefault(false);

				// new
				// Aes256Encryptor(Base64.toBase64String("1234567812345678".getBytes())).encrypt("hello");

				Aes256Encryptor aes256Encryptor = new Aes256Encryptor(new Utils().generateMd5(((EditText) dialog.findViewById(R.id.password)).getText().toString()));
				key.setKeyText("" + aes256Encryptor.encrypt(rand));

				// Aes256Encryptor aes256Encryptord = new
				// Aes256Encryptor(generateMd5(((EditText)
				// dialog.findViewById(R.id.password)).getText().toString()));
				// Utils.println("RND " + kk);
				// Utils.println("ENC " + aes256Encryptor.encrypt(kk));
				// Utils.println("DCP " + aes256Encryptord.decrypt(pas));

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

				key.setName(name.getText().toString());
				key.setCreatedOn("" + System.currentTimeMillis());
				keyStore.create(key);

				Utils.println(rand);
				// qrCodeImage = getQrCode(rand, 1);
				// qrCode.setImageBitmap(qrCodeImage);
				//
				// Toast.makeText(AddKeyActivity.this, "Random Encryption Key Generated",
				// Toast.LENGTH_SHORT).show();
				AddKeyActivity.this.finish();
			}
		});
		dialog.show();
	}

	@Override
	public void notify(Object obj, String type)
	{
		int progress = ((ArrayList) obj).size();
		progress = progress / 5;
		progressBar.setProgress(progress);
		if (progress > 99)
		{
			rand = new Utils().generateMd5(canvas.getRandom() + System.currentTimeMillis());
			Utils.println(rand);
			qrCodeImage = getQrCode(rand, 1);
			qrCode.setImageBitmap(qrCodeImage);

			findViewById(R.id.inst).setVisibility(View.GONE);
			signatureView.setVisibility(View.GONE);
			qrCode.setVisibility(View.VISIBLE);

			Toast.makeText(AddKeyActivity.this, "Random Encryption Key Generated", Toast.LENGTH_SHORT).show();
		}
	}
}
