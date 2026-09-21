package com.SCVA.activities;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.Contents;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.QRCodeEncoder;
import com.SCVA.Utils.Utils;
import com.SCVA.db.ChatsDataSource;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.db.KeysDataSource;
import com.SCVA.db.SettingsDataSource;
import com.SCVA.models.Chat;
import com.SCVA.models.Contact;
import com.SCVA.models.Key;
import com.google.zxing.BarcodeFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class KeyInfoActivity extends Activity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener
{
	private Key key;
	private Button save;
	private ImageView qrCode, startChat;
	private CheckBox isDefault, isActive;
	private KeysDataSource keyStore;
	private Bitmap qrCodeImage = null;
	private Button reveal, share;
	private Contact selectedContact;
	private AlertDialog.Builder alrtdialog;
	private TextView name, date, contactName;
	private ContactsDataSource contactsDataSource;
	private SettingsDataSource settingsDataSource;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_key_info);

		keyStore = new KeysDataSource(this);
		key = getIntent().getParcelableExtra("KEY");

		contactsDataSource = new ContactsDataSource(this);
		settingsDataSource = new SettingsDataSource(this);

		name = findViewById(R.id.name);
		name.setText("" + key.getName());

		contactName = findViewById(R.id.contactName);
		selectedContact = contactsDataSource.getContactAgainstId(key.getUserGroupId());

		if (selectedContact != null)
		{
			contactName.setText("" + contactsDataSource.getContactAgainstId(key.getUserGroupId()).getName());
		}

		date = findViewById(R.id.date);
		SimpleDateFormat sdf = new SimpleDateFormat(Utils.DATE_FORMAT);
		date.setText("" + sdf.format(new Date(Long.parseLong(key.getCreatedOn()))));

		qrCode = findViewById(R.id.qrCode);
		qrCodeImage = getQrCode(key.getKeyText(), 1);
		qrCode.setImageBitmap(qrCodeImage);

		isDefault = findViewById(R.id.isDefault);
		isDefault.setChecked(key.isDefault());
		isDefault.setOnCheckedChangeListener(this);

		isActive = findViewById(R.id.isActive);
		isActive.setChecked(key.isActive());
		isActive.setOnCheckedChangeListener(this);
		if (Constants.devMode) isActive.setVisibility(View.VISIBLE);

		startChat = findViewById(R.id.startChat);
		startChat.setOnClickListener(this);

		save = findViewById(R.id.save);
		save.setOnClickListener(this);

		reveal = findViewById(R.id.reveal);
		reveal.setOnClickListener(this);

		share = findViewById(R.id.share);
		share.setOnClickListener(this);
	}

	@Override
	public void onClick(View view)
	{
		if (view == startChat)
		{
			if (selectedContact != null)
			{
				ChatsDataSource chatsDataSource = new ChatsDataSource(this);
				int chatId = chatsDataSource.getChatIdAgainstContact(selectedContact.getId());
				GS.gI().getEditor().putInt("CHAT_ID", chatId).apply();

				Intent intent = new Intent(this, AllChatsActivity.class);
				intent.putExtra("TYPE_CALL", true);
				intent.putExtra("TYPE", "LAUNCH_CHAT");
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
		}
		if (view == save)
		{
			Utils.println("Delete Key");
			runOnUiThread(showWarningDialog());
		}
		if (view == reveal)
		{
			showPasswordDialog();
		}
		if (view == share)
		{
			String path = MediaStore.Images.Media.insertImage(getContentResolver(), getQrCode(key.getKeyText(), 180), "Image Description", null);
			Uri uri = Uri.parse(path);

			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("image/jpeg");
			intent.putExtra(Intent.EXTRA_STREAM, uri);
			startActivity(Intent.createChooser(intent, "Share Key"));
		}
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

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
	{
		if (buttonView == isDefault)
		{
			keyStore.markKeyAsDefault(key);
		}
		if (buttonView == isActive)
		{
			if (isChecked)
			{
				keyStore.markKeyActive(key);
				getSharedPreferences(Constants.DEFAULT_PREFERENCES, Context.MODE_PRIVATE).edit().putString("enc_key", GS.gI().getPlainKey()).apply();
			}
		}
	}

	public Runnable showWarningDialog()
	{
		Runnable populate = new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					final Handler handler = new Handler();
					alrtdialog = new AlertDialog.Builder(KeyInfoActivity.this);
					alrtdialog.setTitle("Warning");
					alrtdialog.setMessage("Are you sure you want to delete this key");
					alrtdialog.setCancelable(true).setNegativeButton("YES", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int id)
						{
							keyStore.delete(key);
							dialog.dismiss();
							dialog = null;
							new Utils().showNotification(KeyInfoActivity.this, key.getName());
							KeyInfoActivity.this.finish();
						}
					});
					alrtdialog.setCancelable(true).setPositiveButton("NO", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int id)
						{
							dialog.dismiss();
							dialog = null;
						}
					});
					alrtdialog.show();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		return populate;
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
				String plainKey = key.getPlainKey(((EditText) dialog.findViewById(R.id.password)).getText().toString());
				if (plainKey.length() > 0)
				{
					GS.gI().setPlainKey(plainKey);
					isActive.setEnabled(true);

					qrCodeImage = getQrCode(key.getKeyText(), 256);
					qrCode.setImageBitmap(qrCodeImage);
					reveal.setVisibility(View.GONE);
					share.setVisibility(View.VISIBLE);
					new Utils().showNotification(KeyInfoActivity.this, key.getName());
					Toast.makeText(KeyInfoActivity.this, "Key is unlocked.", Toast.LENGTH_LONG).show();
				}
				else
					Toast.makeText(KeyInfoActivity.this, "Password error.", Toast.LENGTH_LONG).show();
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
}