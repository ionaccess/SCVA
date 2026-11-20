package com.SCVA.Utils;

/**
 * Created by §∞§ on 18/02/2026.
 */
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;

/**
 *   ✅ Usage Examples <br/>
 *   🔹 Send Text
 *    WhatsAppHelper.sendTextMessage( this, "14155552671", "Reminder: Meeting at 5 PM"); <br/> <br/>
 *
 *  🔹 Send Image from Drawable Uri uri = WhatsAppHelper.getImageUriFromDrawable(this,R.drawable.sticker); <br/> <br/>
 *     WhatsAppHelper.sendImage(this, uri);
 *      <br/> <br/>
 *  ⚠️ Important Notes
 *  ✔ Phone number format:
 *    923217657432
 *    NOT +9 232 176 57432
*/

public class WhatsAppHelper
{
    public static String logNumber = "+923216921392";
	private static final String WHATSAPP_PACKAGE = "com.whatsapp";

	// ----------------------------------------
	// Check if WhatsApp is installed
	// ----------------------------------------
	public static boolean isWhatsAppInstalled(Context context)
	{
		try
		{
			context.getPackageManager().getPackageInfo(WHATSAPP_PACKAGE, 0);
			return true;
		}
		catch (PackageManager.NameNotFoundException e)
		{
			return false;
		}
	}

	// ----------------------------------------
	// Send Text Message to Specific Number
	// Phone must be international format WITHOUT "+"
	// ----------------------------------------
	public static void sendTextMessage(Context context, String message)
	{

		if (!isWhatsAppInstalled(context))
		{
			Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
			return;
		}

		try
		{
			String url = "https://wa.me/" + logNumber + "?text=" + URLEncoder.encode(message, "UTF-8");

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.setData(Uri.parse(url));
			intent.setPackage(WHATSAPP_PACKAGE);

			context.startActivity(intent);

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// ----------------------------------------
	// Send Image (Sticker-like image)
	// ----------------------------------------
	public static void sendImage(Context context, Uri imageUri)
	{
		if (!isWhatsAppInstalled(context))
		{
			Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
			return;
		}

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("image/*");
		intent.putExtra(Intent.EXTRA_STREAM, imageUri);
		intent.setPackage(WHATSAPP_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

		context.startActivity(intent);
	}

	// ----------------------------------------
	// Convert Drawable Resource to Shareable Uri
	// ----------------------------------------
	public static Uri getImageUriFromDrawable(Context context, int drawableId)
	{
		Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawableId);

		File cachePath = new File(context.getCacheDir(), "images");
		cachePath.mkdirs();

		File file = new File(cachePath, "shared_image.png");

		try
		{
			FileOutputStream stream = new FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
			stream.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
	}

	public static void sendTextAndImage(Context context, String message, Uri imageUri)
	{
		try
		{
			// Check if WhatsApp is installed
			context.getPackageManager().getPackageInfo(WHATSAPP_PACKAGE, 0);

			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType("image/*");

			intent.putExtra(Intent.EXTRA_TEXT, message);
			intent.putExtra(Intent.EXTRA_STREAM, imageUri);

			intent.setPackage(WHATSAPP_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

			context.startActivity(intent);

		}
		catch (PackageManager.NameNotFoundException e)
		{
			Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

}
