package com.SCVA.Utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class StarProgressDialog
{
	private static Dialog dialog;
	private static TextView messageText;
	private static WeakReference<Activity> hostActivity;
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

	/** Registers the currently visible Activity as the dialog host. */
	public static void attach(Activity activity)
	{
		if (Looper.myLooper() != Looper.getMainLooper())
		{
			MAIN_HANDLER.post(() -> attach(activity));
			return;
		}
		if (isUsable(activity)) hostActivity = new WeakReference<>(activity);
	}

	/** Releases the dialog host when the Activity is no longer visible. */
	public static void detach(Activity activity)
	{
		if (Looper.myLooper() != Looper.getMainLooper())
		{
			MAIN_HANDLER.post(() -> detach(activity));
			return;
		}
		Activity current = hostActivity == null ? null : hostActivity.get();
		if (current == activity)
		{
			hide();
		}
	}

	/** Allows a service such as Processor to update the Activity-hosted dialog. */
	public static void show(String message)
	{
		MAIN_HANDLER.post(() ->
		{
			Activity activity = hostActivity == null ? null : hostActivity.get();
			show(activity, message);
		});
	}

	public static void show(Activity activity, String message)
	{
		if (Looper.myLooper() != Looper.getMainLooper())
		{
			MAIN_HANDLER.post(() -> show(activity, message));
			return;
		}
		if (!isUsable(activity)) return;
		hostActivity = new WeakReference<>(activity);

		if (dialog != null && dialog.isShowing())
		{
			setMessage(message);
			return;
		}

		dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setCancelable(false);
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(Color.GREEN);
		// if (BuildConfig.FLAVOR.equals("ams"))
		// {
		// drawable.setColor(Color.parseColor("#31A7FB"));
		// }
		drawable.setAlpha(50);
		float radius = 10 * activity.getResources().getDisplayMetrics().density;
		drawable.setCornerRadius(radius);

		// Root layout (full screen overlay)
		LinearLayout rootLayout = new LinearLayout(activity);
		rootLayout.setOrientation(LinearLayout.VERTICAL);
		rootLayout.setGravity(Gravity.CENTER);
		rootLayout.setBackgroundColor(Color.TRANSPARENT);
		rootLayout.setBackground(drawable);
		// rootLayout.setBackgroundColor(Color.parseColor("#80000000")); //
		// semi-transparent

		// Container layout (white box)
		LinearLayout container = new LinearLayout(activity);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setGravity(Gravity.CENTER);
		container.setPadding(160, 60, 160, 60);

		// Spinner
		ProgressBar progressBar = new ProgressBar(activity);
		progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
		container.addView(progressBar);

		// Message Text
		messageText = new TextView(activity);
		messageText.setText(message);
		messageText.setTextColor(Color.WHITE);
		messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		messageText.setTypeface(Typeface.DEFAULT_BOLD);
		messageText.setPadding(0, 30, 0, 0);
		messageText.setGravity(Gravity.CENTER);

		container.addView(messageText);

		rootLayout.addView(container);
		dialog.setContentView(rootLayout);

		if (!activity.isFinishing())
		{
			dialog.show();
		}
	}

	public static void setMessage(String message)
	{
		if (Looper.myLooper() != Looper.getMainLooper())
		{
			MAIN_HANDLER.post(() -> setMessage(message));
			return;
		}
		if (messageText != null)
		{
			messageText.setText(message);
		}
	}

	public static void hide()
	{
		if (Looper.myLooper() != Looper.getMainLooper())
		{
			MAIN_HANDLER.post(StarProgressDialog::hide);
			return;
		}
		if (dialog != null && dialog.isShowing())
		{
			dialog.dismiss();
		}
		dialog = null;
		messageText = null;
		hostActivity = null;
	}

	public static boolean isShowing()
	{
		return dialog != null && dialog.isShowing();
	}

	private static boolean isUsable(Activity activity)
	{
		return activity != null && !activity.isFinishing() && !activity.isDestroyed();
	}
}
