package com.SCVA.Utils;

/**
 * Created by §∞§ on 10/02/2026.
 */
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.SCVA.R;


public class NotificationHelper
{

	private static final String DEFAULT_CHANNEL_ID = "missing_channel_id";

	private static final String DEFAULT_CHANNEL_NAME = "Missing Entry Channel";

	private static final String SILENT_CHANNEL_ID = "silent_channel_id";

	private static final String SILENT_CHANNEL_NAME = "Silent Channel";

	private Context context;

	public NotificationHelper(Context context)
	{
		this.context = context;
		createSilentChannel();
		createNotificationChannel();
	}

	// Create channel for API 26+
	private void createNotificationChannel()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			NotificationManager manager = context.getSystemService(NotificationManager.class);
			if (manager != null)
			{
				NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
				channel.setDescription("Channel for app notifications");
				manager.createNotificationChannel(channel);
			}
		}
	}

	// Show simple notification
	public void showNotification(int notificationId, String title, String message, Intent intent)
	{
		PendingIntent pendingIntent = null;
		if (intent != null)
		{
			pendingIntent = PendingIntent.getActivity(context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID).setSmallIcon(R.drawable.ic_key) // replace with your icon
				.setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true);

		if (pendingIntent != null)
		{
			builder.setContentIntent(pendingIntent);
		}

		NotificationManagerCompat manager = NotificationManagerCompat.from(context);
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
		{
			// TODO: Consider calling
			// ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			// public void onRequestPermissionsResult(int requestCode, String[] permissions,
			// int[] grantResults)
			// to handle the case where the user grants the permission. See the
			// documentation
			// for ActivityCompat#requestPermissions for more details.
			return;
		}
		manager.notify(notificationId, builder.build());
	}

	public void showPersistentNotification(int notificationId, String title, String message, Intent intent)
	{
		PendingIntent pendingIntent = null;
		if (intent != null)
		{
			pendingIntent = PendingIntent.getActivity(context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID).setSmallIcon(R.drawable.ic_key) // replace with your icon
				.setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_LOW) // often better for ongoing notifications
				.setOngoing(true) // <-- makes it non-swipable
				.setAutoCancel(false).setSound(null); // <-- prevents dismissal on tap

		if (pendingIntent != null)
		{
			builder.setContentIntent(pendingIntent);
		}

		NotificationManagerCompat manager = NotificationManagerCompat.from(context);
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
		{
			// TODO: Consider calling
			// ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			// public void onRequestPermissionsResult(int requestCode, String[] permissions,
			// int[] grantResults)
			// to handle the case where the user grants the permission. See the
			// documentation
			// for ActivityCompat#requestPermissions for more details.
			return;
		}
		manager.notify(notificationId, builder.build());
	}

	// Call this to hide/cancel a notification
	public static void hideNotification(Context context, int notificationId)
	{
		NotificationManagerCompat manager = NotificationManagerCompat.from(context);
		manager.cancel(notificationId);
	}

	// Silent notification channel
	private void createSilentChannel()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			NotificationChannel channel = new NotificationChannel(SILENT_CHANNEL_ID, SILENT_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW // low importance = no sound
			);
			channel.setDescription("Channel for silent notifications");
			channel.setSound(null, null); // no sound
			channel.enableVibration(false); // no vibration
			NotificationManager manager = context.getSystemService(NotificationManager.class);
			manager.createNotificationChannel(channel);
		}
	}

	/**
	 * Show a silent notification.
	 *
	 * @param notificationId
	 *            Unique ID for the notification
	 * @param title
	 *            Notification title
	 * @param message
	 *            Notification message
	 * @param intent
	 *            Optional: activity to open when tapped (can be null)
	 * @param persistent
	 *            If true, notification is non-cancelable
	 */
	public void showSilentNotification(int notificationId, String title, String message, int number, Intent intent, boolean persistent)
	{
		PendingIntent pendingIntent = null;
		if (intent != null)
		{
			pendingIntent = PendingIntent.getActivity(context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context, SILENT_CHANNEL_ID).setSmallIcon(R.drawable.ic_key) // replace with your icon
				.setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_LOW).setSound(null) // ensures silence on pre-Oreo devices
				.setVibrate(new long[]
				{ 0L }) // no vibration
				.setNumber(number).setAutoCancel(!persistent).setOngoing(persistent);
		// non-cancelable if persistent = true

		if (pendingIntent != null)
		{
			builder.setContentIntent(pendingIntent);
		}

		NotificationManagerCompat manager = NotificationManagerCompat.from(context);
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
		{
			// ActivityCompat#requestPermissions

			// here to request the missing permissions, and then overriding
			// public void onRequestPermissionsResult(int requestCode, String[] permissions,
			// int[] grantResults)
			// to handle the case where the user grants the permission. See the
			// documentation
			// for ActivityCompat#requestPermissions for more details.
			return;
		}
		manager.notify(notificationId, builder.build());
	}
}
