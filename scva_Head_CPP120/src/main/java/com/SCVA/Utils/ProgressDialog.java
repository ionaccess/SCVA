package com.SCVA.Utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.SCVA.Interfaces.CallBack;

public class ProgressDialog
{
	private static Dialog dialog;

	private static CallBack callBack;

	private static TextView messageText;

	public static void show(Activity activity, String message)
	{
		if (activity == null || activity.isFinishing()) return;

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
		drawable.setAlpha(50);
		float radius = 10 * activity.getResources().getDisplayMetrics().density;
		drawable.setCornerRadius(radius);

		// Root layout (full screen overlay)
		LinearLayout rootLayout = new LinearLayout(activity);
		rootLayout.setOrientation(LinearLayout.VERTICAL);
		rootLayout.setGravity(Gravity.CENTER);
		rootLayout.setBackgroundColor(Color.TRANSPARENT);
		rootLayout.setBackground(drawable);

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
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener()
		{
			@Override
			public void onDismiss(DialogInterface dialogInterface)
			{
				if (callBack != null)
				{
					callBack.notify(null, "PROGRESS_HIDE");
				}
			}
		});
	}

	public static void setMessage(String message)
	{
		if (messageText != null)
		{
			messageText.setText(message);
		}
	}

	public static void hide()
	{
		if (dialog != null && dialog.isShowing())
		{
			dialog.dismiss();
			dialog = null;
			messageText = null;
		}
	}

	public static boolean isShowing()
	{
		if (dialog == null) return false;
		return dialog.isShowing();
	}

	public static void setCallBackListener(CallBack callBack)
	{
		ProgressDialog.callBack = callBack;
	}

	public static void setCancelable(boolean cancelable)
	{
		dialog.setCancelable(cancelable);
	}
}