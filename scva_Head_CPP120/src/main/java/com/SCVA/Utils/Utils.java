package com.SCVA.Utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;

import com.SCVA.R;
import com.SCVA.SCVA;
import com.SCVA.activities.AllChatsActivity;
import com.SCVA.activities.UserChatActivity;
import com.SCVA.models.Configuration;
import com.SCVA.models.Key;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class Utils
{
	private String chanId = "0";
	public static boolean log = true;
	private AlertDialog.Builder alrtdialog;
	public NotificationManager mNotificationManager;
	public static Notification myNotification = null;
	public String NOTIFICATION_CHANNEL_ID = "com.scva";
	public static String DATE_FORMAT = "dd-MM-yyyy hh:mm";
	public static SimpleDateFormat sdf = new SimpleDateFormat(Utils.DATE_FORMAT);
	public static final String APP_DIRECTORY_ROOT = "SCVA";
	private static File sdCardRoot = Environment.getExternalStorageDirectory();// SCVA.myContext.getDataDir();//ExternalFilesDir(Environment.DIRECTORY_PICTURES);

	public static void println(Object obj)
	{
		if (log)
		{
			System.out.println(obj);
			saveFileUsingFileObject(obj.toString() + "\n");
		}
	}

	public static void println(Object tag, Object obj)
	{
		if (log)
		{
			System.out.println(tag + " | " + obj);
			saveFileUsingFileObject(tag + " | " + obj.toString() + "\n");
		}
	}

	public static void println(Object tag, Object obj, Object error)
	{
		if (log)
		{
			System.out.println(tag + " | " + obj + " = " + error);
			saveFileUsingFileObject(tag + " | " + obj.toString() + " = " + error + "\n");
		}
	}

	public static void saveFileUsingFileObject(String fileContents)
	{
		String fLog = GS.gI().getContext().getString(R.string.txt_app_name) + " | " + sdf.format(System.currentTimeMillis()) + " | " + fileContents;
		if (GS.gI().getValue().getBoolean("PERMISSION", false))
		{
			crashToFile(GS.gI().getContext().getString(R.string.txt_app_name), fileContents);
		}
		try (FileOutputStream fos = GS.gI().getContext().openFileOutput("log.txt", Context.MODE_APPEND))
		{
			fos.write(fLog.getBytes());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static void clearFileUsingFileObject()
	{
		try (FileOutputStream fos = GS.gI().getContext().openFileOutput("log.txt", Context.MODE_PRIVATE))
		{
			fos.write("CLEAR\n".getBytes());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static String readFileFromInternalStorage()
	{
		StringBuilder stringBuilder = new StringBuilder();
		try (FileInputStream fis = GS.gI().getContext().openFileInput("log.txt");
				InputStreamReader isr = new InputStreamReader(fis);
				BufferedReader reader = new BufferedReader(isr))
		{

			String line;
			while ((line = reader.readLine()) != null)
			{
				stringBuilder.append(line).append("\n");
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return stringBuilder.toString();
	}

	public static void writeToFile(String data)
	{
		try
		{
			File yourDir = new File(sdCardRoot, APP_DIRECTORY_ROOT + "/");

			File[] fileList = yourDir.listFiles();
			if (yourDir != null && fileList != null)
			{
			}
			else
			{
				yourDir.mkdirs();
			}
			File file = new File(yourDir, "msgs.log");
			FileOutputStream stream = new FileOutputStream(file, true);
			try
			{
				stream.write(new String(sdf.format(System.currentTimeMillis()) + " | " + data + "\n").getBytes());
			}
			finally
			{
				stream.close();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			Utils.println("Exception", "File write failed: " + e.toString());
		}
	}

	public String readLogFile(Context context)
	{
		StringBuilder text = new StringBuilder();
		File yourDir = new File(sdCardRoot, APP_DIRECTORY_ROOT + "/");

		File[] fileList = yourDir.listFiles();
		if (yourDir != null && fileList != null)
		{
		}
		else
		{
			yourDir.mkdirs();
		}
		File file = new File(yourDir, "msgs.log");
		if (file.exists())
		{
			try
			{
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line;
				while ((line = br.readLine()) != null)
				{
					text.append(line);
					text.append('\n');
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		if (text.toString().length() == 0)
		{
			text.append("Nothing to show here...");
		}
		return text.toString();
	}

	public void burnLogs(Context context)
	{
		try
		{
			File yourDir = new File(sdCardRoot, APP_DIRECTORY_ROOT + "/");
			File[] fileList = yourDir.listFiles();
			if (yourDir != null && fileList != null)
			{
			}
			else
			{
				yourDir.mkdirs();
			}
			File file = new File(yourDir, "msgs.log");
			if (file.exists()) file.delete();
			if (yourDir.exists()) yourDir.delete();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Utils.println("Exception", "File write failed: " + e.toString());
		}
	}

	public static void crashToFile(String version, String cause)
	{
		try
		{
			File yourDir = new File(sdCardRoot, "/");
			File[] fileList = yourDir.listFiles();
			if (yourDir != null && fileList != null)
			{
			}
			else
			{
				yourDir.mkdirs();
			}
			File file = new File(yourDir, "crashes.log");
			FileOutputStream stream = new FileOutputStream(file, true);
			try
			{
				stream.write(new String(sdf.format(System.currentTimeMillis()) + " | " + cause + "\n========================" + version + "========================\n").getBytes());
			}
			finally
			{
				stream.close();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			Utils.println("Exception", "File write failed: " + e.toString());
		}
	}

	public String generateMd5(String username)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("MD5");
			digest.update(username.getBytes());
			final byte[] bytes = digest.digest();
			final StringBuffer buffer = new StringBuffer();

			for (int i = 0; i < bytes.length; i++)
			{
				String hex = Integer.toHexString(0xFF & bytes[i]);
				if (hex.length() == 1)
				{
					hex = "0" + hex;
				}
				buffer.append(hex);
			}
			return buffer.toString();
		}
		catch (Exception e)
		{
			return "";
		}
	}

	public static SharedPreferences getValue(Context context)
	{
		return context.getSharedPreferences(Constants.DEFAULT_PREFERENCES, Context.MODE_PRIVATE);
	}

	public void showNotification(Context context, String name)
	{
		String keyName = "";
		if (name == null)
		{
			keyName = "No key is Unlocked";
		}
		else
		{
			keyName = "Active key is " + name;
		}
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		int notificationId = 0;
		String channelId = "KeyManager";
		String channelName = "Key Manager";
		int importance = 0;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
		{
			importance = NotificationManager.IMPORTANCE_HIGH;
		}

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
		{
			NotificationChannel mChannel = new NotificationChannel(channelId, channelName, importance);
			mChannel.setSound(null, null); // no sound
			notificationManager.createNotificationChannel(mChannel);
		}

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_key).setSound(null)
				.setContentTitle(context.getString(R.string.txt_app_name)).setContentText(keyName).setOngoing(true);
		notificationManager.notify(notificationId, mBuilder.build());
	}

	public void hideNotification(Context context)
	{
		mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(0);
	}

	public Runnable showQuestionDialog(final String title, final String message, final Context context, final Runnable yes, final Runnable no)
	{
		Runnable populate = new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					final Handler handler = new Handler();
					alrtdialog = new AlertDialog.Builder(context);
					alrtdialog.setTitle(title);
					alrtdialog.setMessage(message);
					alrtdialog.setCancelable(true).setNeutralButton("Yes", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int id)
						{
							if (yes != null) handler.post(yes);
							dialog.dismiss();
							dialog = null;
						}
					});
					alrtdialog.setCancelable(true).setPositiveButton("No", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int id)
						{
							if (no != null) handler.post(no);
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

	public static void hideKeyboard(Context context, View view)
	{
		((InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
	}

	public static void showKeyboard(Context context, View view)
	{
		((InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(view, 0);
	}

	public String formatMillis(long millis)
	{
		long hours = (millis / (1000 * 60 * 60)) % 24;
		long minutes = (millis / (1000 * 60)) % 60;
		long seconds = (millis / 1000) % 60;

		// Format into HH:mm:ss string

		// Output: 00:01:00
		return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
	}

	public String bitmapToBase64(Bitmap bitmap)
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		// Compress the bitmap. Choose JPEG or PNG, and set quality (0-100)
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
		byte[] byteArray = byteArrayOutputStream.toByteArray();

		return Base64.encodeToString(byteArray, Base64.DEFAULT);
	}

	public String convertUriToBase64(Context context, Uri uri)
	{
		try
		{
			InputStream inputStream = context.getContentResolver().openInputStream(uri);
			ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

			int bufferSize = 1024;
			byte[] buffer = new byte[bufferSize];

			int len;
			while ((len = inputStream.read(buffer)) != -1)
			{
				byteBuffer.write(buffer, 0, len);
			}

			byte[] imageBytes = byteBuffer.toByteArray();
			inputStream.close();

			return Base64.encodeToString(imageBytes, Base64.DEFAULT);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public double getBase64ImageSizeInKB(String base64String)
	{
		if (base64String == null || base64String.isEmpty())
		{
			return 0.0;
		}

		// 1. Get the total number of characters
		int stringLength = base64String.length();

		// 2. Adjust for padding characters ('=') at the end of the string
		int padding = 0;
		if (base64String.endsWith("=="))
		{
			padding = 2;
		}
		else if (base64String.endsWith("="))
		{
			padding = 1;
		}

		// 3. Calculate actual file size in bytes
		int sizeInBytes = (int) ((stringLength * 0.75) - padding);

		// 4. Convert bytes to kilobytes
		return sizeInBytes / 1024;
	}

	public static Size downgradeByPercentage(int originalWidth, int originalHeight, double percentToReduce)
	{
		Size size = new Size(0, 0);
		// 2. Perform the scale math forcing floating-point numbers (100.0)
		double scaleFactor = (100.0 - percentToReduce) / 100.0;

		// 3. Calculate the target width and height
		int newWidth = (int) Math.round(originalWidth * scaleFactor);
		int newHeight = (int) Math.round(originalHeight * scaleFactor);

		// Safety fallback checks
		if (newWidth < 1) newWidth = 1;
		if (newHeight < 1) newHeight = 1;

		return new Size(newWidth, newHeight);
	}

	public Bitmap loadBitmapFromUri(ContentResolver contentResolver, Uri imageUri)
	{
		Bitmap bitmap = null;
		try
		{
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
			{
				// Use ImageDecoder for Android 9.0 (API 28) and above
				ImageDecoder.Source source = ImageDecoder.createSource(contentResolver, imageUri);
				bitmap = ImageDecoder.decodeBitmap(source);
			}
			else
			{
				// Use BitmapFactory via InputStream for older Android versions
				InputStream inputStream = contentResolver.openInputStream(imageUri);
				bitmap = BitmapFactory.decodeStream(inputStream);
				if (inputStream != null)
				{
					inputStream.close();
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return bitmap;
	}

	public Bitmap createThumbnailFromUri(Context context, Uri uri, int reqWidth, int reqHeight)
	{
		try
		{
			// 1. Decode with inJustDecodeBounds=true to check dimensions safely
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;

			// Open stream from ContentResolver instead of using a file path
			InputStream input = context.getContentResolver().openInputStream(uri);
			BitmapFactory.decodeStream(input, null, options);
			if (input != null) input.close();

			// 2. Calculate the optimal down sampling ratio
			options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

			// 3. Decode the actual downsampled bitmap
			options.inJustDecodeBounds = false;

			// Re-open the input stream to read the actual pixels
			input = context.getContentResolver().openInputStream(uri);
			Bitmap thumbnail = BitmapFactory.decodeStream(input, null, options);
			if (input != null) input.close();

			return thumbnail;

		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
	{
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth)
		{
			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth)
			{
				inSampleSize *= 2;
			}
		}
		return inSampleSize;
	}

	public double getTimeRequired(long bytes, double bytesPerSecond)
	{
		if (bytesPerSecond <= 0)
		{
			throw new IllegalArgumentException("Transmission rate must be greater than zero.");
		}
		if (bytes < 0)
		{
			throw new IllegalArgumentException("Bytes cannot be negative.");
		}
		return bytes / bytesPerSecond;
	}

	public Notification getNotification(Context context)
	{
		String NOTIFICATION_CHANNEL_ID = "com.SCVA";
		String channelName = "Background Modem";
		NotificationChannel chan = null;
		NotificationCompat.Builder mBuilder;
		String chanId = "";
		// New code for support of Android version 8+
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
			NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			chanId = chan.getId();
		}
		mBuilder = new NotificationCompat.Builder(context, chanId).setSmallIcon(R.drawable.notificationicon).setContentTitle(context.getString(R.string.txt_ModemON))
				.setContentText(context.getString(R.string.txt_FldigiModemOn)).setOngoing(true);
		// Creates an explicit intent for an Activity in your app
		Intent notificationIntent = new Intent(context, UserChatActivity.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(AllChatsActivity.class);
		stackBuilder.addNextIntent(notificationIntent);

		PendingIntent pIntent = stackBuilder.getPendingIntent(0, 0);
		// mBuilder.setContentIntent(pIntent);
		return mBuilder.build();
	}
}
