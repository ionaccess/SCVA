/*
 * config.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA) 
 *
 * This program is distributed in the hope that it will be useful,  
 * but WITHOUT ANY WARRANTY; without even the implied warranty of  
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the  
 * GNU General Public License for more details.  
 *
 * You should have received a copy of the GNU General Public License  
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.  
 */

package com.SCVA;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import android.app.AlertDialog;
import android.content.DialogInterface;

import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;

public class config
{

	/**
	 * @param Key
	 * @return
	 */
	public static String getPreferenceS(String Key)
	{
		String myReturn = "";

		try
		{
			myReturn = GS.gI().getValue().getString(Key, "");
		}
		catch (Exception e)
		{
			myReturn = "";
		}
		return myReturn;
	}

	/**
	 * Get the saved value, if its not there then use the default value
	 *
	 * @param Key
	 * @param Default
	 * @return
	 */
	public static String getPreferenceS(String Key, String Default)
	{
		String myReturn = "";

		try
		{
			myReturn = GS.gI().getValue().getString(Key, Default);
			// if (myReturn.equals("")) myReturn = Default;
		}
		catch (Exception e)
		{
			myReturn = Default;
		}
		return myReturn;
	}

	// Reads an integer from preferences, with default value
	public static int getPreferenceI(String Key, int Default)
	{
		int myReturn = 0;
		String myPref = "";

		try
		{
			myPref = GS.gI().getValue().getString(Key, "");
			if (myPref.equals(""))
			{
				myReturn = Default;
			}
			else
			{
				// Try integer conversion
				try
				{
					myReturn = Integer.parseInt(myPref);
				}
				catch (NumberFormatException ex)
				{
					// Return zero is probably the best logic here since we cannot interract with
					// the user anyway
					loggingClass.writelog("Cannot convert preference [" + Key + "] to a number" + ex.getMessage(), null, true);
					myReturn = 0;
				}
			}
		}
		catch (Exception e)
		{
			myReturn = Default;
		}
		return myReturn;
	}

	// Reads a double from preferences, with default value
	public static double getPreferenceD(String Key, double Default)
	{
		double myReturn = 0;
		String myPref = "";

		try
		{
			myPref = GS.gI().getValue().getString(Key, "");
			if (myPref.equals(""))
			{
				myReturn = Default;
			}
			else
			{
				// Try double conversion
				try
				{
					myReturn = Double.parseDouble(myPref);
				}
				catch (NumberFormatException ex)
				{
					// Return zero is probably the best logic here since we cannot interract with
					// the user anyway
					loggingClass.writelog("Cannot convert preference [" + Key + "] to a number" + ex.getMessage(), null, true);
					myReturn = 0.0f;
				}
			}
		}
		catch (Exception e)
		{
			// No value entered or no preference not found
			myReturn = Default;
		}
		return myReturn;
	}

	/**
	 * @param Key
	 * @return
	 */
	public static boolean getPreferenceB(String Key)
	{
		boolean myReturn = false;

		try
		{
			myReturn = GS.gI().getValue().getBoolean(Key, false);
		}
		catch (Exception e)
		{
			myReturn = false;
		}
		return myReturn;
	}

	/**
	 * Get the saved value, if its not there then use the default value
	 *
	 * @param Key
	 * @param Default
	 * @return
	 */
	public static boolean getPreferenceB(String Key, boolean Default)
	{
		boolean myReturn = false;

		try
		{
			myReturn = GS.gI().getValue().getBoolean(Key, Default);
		}
		catch (Exception e)
		{
			myReturn = Default;
		}

		return myReturn;
	}

	/*
	 * Sets the passes value into the assed preference Key, if its not there do
	 * nothing
	 *
	 * @param key
	 *
	 * @param Default
	 *
	 * @return true=ok, false=failed
	 */
	public static boolean setPreferenceS(String key, String newValue)
	{
		boolean myReturn = true;

		try
		{
			// store value into preferences
			GS.gI().getEditor().putString(key, newValue).apply();
		}
		catch (Exception e)
		{
			myReturn = false;
		}
		return myReturn;
	}

	// For storing Boolean preferences
	public static boolean setPreferenceB(String pref, boolean flag)
	{
		Boolean myReturn = true;
		try
		{
			// store value into preferences
			GS.gI().getEditor().putBoolean(pref, flag).apply();
		}
		catch (Exception e)
		{
			myReturn = false;
		}
		return myReturn;
	}

	// Backup all preferences to file
	public static boolean burnBackupFile(String fileName)
	{
		String fullFileName = Constants.HomePath + Constants.Dirprefix + fileName;
		File dst = new File(fullFileName);
		File dict = new File(Constants.HomePath + Constants.Dirprefix);
		if (dst.exists()) dst.delete();
		if (dict.exists()) deleteAllFromDirectory(dict);

		return false;
	}

	public static void deleteAllFromDirectory(File fileOrDirectory)
	{
		if (fileOrDirectory.isDirectory()) for (File child : fileOrDirectory.listFiles())
			deleteAllFromDirectory(child);
		fileOrDirectory.delete();
	}

	// Backup all preferences to file
	public static boolean saveSharedPreferencesToFile(String fileName)
	{
		String fullFileName = Constants.HomePath + Constants.Dirprefix + fileName;
		File dst = new File(fullFileName);
		boolean res = false;
		ObjectOutputStream output = null;
		try
		{
			output = new ObjectOutputStream(new FileOutputStream(dst));
			output.writeObject(GS.gI().getValue().getAll());
			res = true;
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (output != null)
				{
					output.flush();
					output.close();
				}
			}
			catch (IOException ex)
			{
				ex.printStackTrace();
			}
		}
		return res;
	}

	// Read backup preference file and restore values
	public static void loadSharedPreferencesFromFile(String fileName)
	{
		String fullFileName = Constants.HomePath + Constants.Dirprefix + fileName;
		final File src = new File(fullFileName);

		AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(GS.gI().getContext());
		myAlertDialog.setMessage(GS.gI().getContext().getString(R.string.txt_YouWantToOverwriteSettings));
		myAlertDialog.setCancelable(false);
		myAlertDialog.setPositiveButton(GS.gI().getContext().getString(R.string.txt_Yes), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				ObjectInputStream input = null;
				try
				{
					GS.gI().getEditor().clear();
					input = new ObjectInputStream(new FileInputStream(src));
					Map<String, ?> entries = (Map<String, ?>) input.readObject();
					for (Map.Entry<String, ?> entry : entries.entrySet())
					{
						Object v = entry.getValue();
						String key = entry.getKey();
						Utils.println("key = " + key + " value = " + v);
						if (v instanceof Boolean) GS.gI().gI().getEditor().putBoolean(key, ((Boolean) v).booleanValue()).apply();
						else if (v instanceof Float) GS.gI().getEditor().putFloat(key, ((Float) v).floatValue()).apply();
						else if (v instanceof Integer) GS.gI().getEditor().putInt(key, ((Integer) v).intValue()).apply();
						else if (v instanceof Long) GS.gI().getEditor().putLong(key, ((Long) v).longValue()).apply();
						else if (v instanceof String) GS.gI().getEditor().putString(key, ((String) v)).apply();
					}
				}
				catch (FileNotFoundException e)
				{
					// e.printStackTrace();
					SCVA.myInstance.topToastText(GS.gI().getContext().getString(R.string.txt_NoBackupFileFound));
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				catch (ClassNotFoundException e)
				{
					e.printStackTrace();
				}
				finally
				{
					try
					{
						if (input != null)
						{
							input.close();
						}
					}
					catch (IOException ex)
					{
						ex.printStackTrace();
					}
				}
			}
		});
		myAlertDialog.setNegativeButton(GS.gI().getContext().getString(R.string.txt_Cancel), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
			}
		});
		myAlertDialog.show();
	}

	public static void restoreSettingsToDefault()
	{
		AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(GS.gI().getContext());
		myAlertDialog.setMessage(GS.gI().getContext().getString(R.string.txt_YouWantToRestoreSettingsToDefault));
		myAlertDialog.setCancelable(false);
		myAlertDialog.setPositiveButton(GS.gI().getContext().getString(R.string.txt_Yes), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				// Restore RX and TX RSID in case they were disabled by error
				GS.gI().getEditor().putBoolean("RXRSID", true).apply();
				GS.gI().getEditor().putBoolean("TXRSID", true).apply();

				// General and GUI
				GS.gI().getEditor().putBoolean("USEMODELIST", false).apply();
				GS.gI().getEditor().putString("BUTTONTEXTSIZE", "12").apply();

				// Modem - General
				GS.gI().getEditor().putString("VOLUME", "10").apply();
				GS.gI().getEditor().putString("AFREQUENCY", "1500").apply();
				GS.gI().getEditor().putBoolean("SLOWCPU", false).apply();

				// RSID
				GS.gI().getEditor().putBoolean("TXPOSTRSID", false).apply();
				GS.gI().getEditor().putBoolean("RSIDWIDESEARCH", true).apply();
				GS.gI().getEditor().putString("RSID_ERRORS", "2").apply();

				// 8PSK
				GS.gI().getEditor().putBoolean("8PSKPILOT", true).apply();
				GS.gI().getEditor().putString("8PSKPILOTPOWER", "-30").apply();

				// DominoEx
				GS.gI().getEditor().putBoolean("DOMINOEXFILTER", true).apply();
				GS.gI().getEditor().putString("DOMINOEXBW", "2.0").apply();
				GS.gI().getEditor().putBoolean("DOMINOEXFEC", false).apply();
				GS.gI().getEditor().putString("DOMCWI", "0.0").apply();

				// Thor
				GS.gI().getEditor().putString("THORCWI", "0.0").apply();
				GS.gI().getEditor().putBoolean("THORFILTER", true).apply();
				GS.gI().getEditor().putString("THORBW", "2.0").apply();
				GS.gI().getEditor().putBoolean("THORPREAMBLE", true).apply();
				GS.gI().getEditor().putBoolean("THORSOFTSYMBOLS", true).apply();
				GS.gI().getEditor().putBoolean("THORSOFTBITS", true).apply();

				// Olivia
				GS.gI().getEditor().putString("OLIVIATONES", "2").apply();
				GS.gI().getEditor().putString("OLIVIABW", "2").apply();
				GS.gI().getEditor().putString("OLIVIASMARGIN", "8").apply();
				GS.gI().getEditor().putString("OLIVIASINTEG", "4").apply();
				GS.gI().getEditor().putBoolean("OLIVIARESETFEC", false).apply();
				GS.gI().getEditor().putBoolean("OLIVIA8BIT", true).apply();

				// MT63
				GS.gI().getEditor().putBoolean("MT638BIT", true).apply();
				GS.gI().getEditor().putBoolean("MT63INTEGRATION", true).apply();
				GS.gI().getEditor().putBoolean("MT63USETONES", true).apply();
				GS.gI().getEditor().putBoolean("MT63TWOTONES", true).apply();
				GS.gI().getEditor().putString("MT63TONEDURATION", "4").apply();
				GS.gI().getEditor().putBoolean("MT63AT500", false).apply();

				// Image Attachments
				GS.gI().getEditor().putString("TARGETMAXMEGAPIXELS", "0.5").apply();
				GS.gI().getEditor().putString("JPEGQUALITY", "70").apply();

				// Data exchange
				GS.gI().getEditor().putBoolean("USECOMPRESSION", true).apply();
				GS.gI().getEditor().putString("COMPRESSIONENCODER", "1").apply();
				GS.gI().getEditor().putBoolean("FORCECOMPRESSION", false).apply();
				GS.gI().getEditor().putString("EXTRACTTIMEOUT", "4").apply();

				// Personnal data - No Change
				// Date-Time format - No change
				// File name format - No change
				// Radiogram
				// GS.gI().getEditor().putString("RGWORDSPERLINE", "5").apply();
				// GS.gI().getEditor().putBoolean("SHOWARLDESC",true).apply();
				// GPS Time
				GS.gI().getEditor().putBoolean("USEGPSTIME", false).apply();
				GS.gI().getEditor().putString("LEAPSECONDS", "0").apply();
			}
		});
		myAlertDialog.setNegativeButton(GS.gI().getContext().getString(R.string.txt_Cancel), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
			}
		});
		myAlertDialog.show();
	}

	public static void setSuggestedValues()
	{
		// Restore RX and TX RSID in case they were disabled by error
		// GS.gI().getEditor().putBoolean("RXRSID", true).apply();
		// GS.gI().getEditor().putBoolean("TXRSID", true).apply();
		//
		// GS.gI().getEditor().putString("AFREQUENCY", "1500").apply();
		// GS.gI().getEditor().putString("VOLUME", "10").apply();
		// GS.gI().getEditor().putBoolean("RSFEC", true).apply();
		// GS.gI().getEditor().putBoolean("USEBPSK31", true).apply();
		// GS.gI().getEditor().putBoolean("USEMODELIST", true).apply();
		// // GS.gI().getEditor().putBoolean("use_enc", true).apply();
		// GS.gI().getEditor().putString("enc_algorithm", "aes").apply();
	}
}
