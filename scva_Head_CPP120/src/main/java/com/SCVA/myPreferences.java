/*
 * Preferences.java  
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

/**
 * @author John Douyere <vk2eta@gmail.com>
 */

import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.activities.AllChatsActivity;
import com.SCVA.activities.ChangeVerificationPINActivity;
import com.SCVA.activities.KeysActivity;
import com.SCVA.Utils.Utils;
import com.SCVA.db.KeysDataSource;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class myPreferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener
{
	int key_lenght = 0;
	private int v2TestSequence = 1;
	public SharedPreferences.OnSharedPreferenceChangeListener splistener;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Start from the fixed section of the preferences
		addPreferencesFromResource(R.xml.preferences);
		PreferenceScreen screen = getPreferenceScreen();
		CheckBoxPreference v2Switch = (CheckBoxPreference) findPreference("RSFEC_V2_ENABLED");
		ListPreference v2Preset = (ListPreference) findPreference("RSFEC_V2_PRESET");
		v2Preset.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference p, Object value)
			{
				if (GS.gI().isTXActive())
				{
					Toast.makeText(myPreferences.this, "Change V2 FEC preset between transmissions", Toast.LENGTH_LONG).show();
					return false;
				}
				String selected = String.valueOf(value);
				if (!(selected.equals("1") || selected.equals("4") || selected.equals("5") || selected.equals("6") || selected.equals("7") || selected.equals("8") || selected.equals("9")))
					return false;
				return true;
			}
		});
		v2Switch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference p, Object value)
			{
				boolean enabled = Boolean.TRUE.equals(value);
				if (GS.gI().isTXActive())
				{
					android.widget.Toast.makeText(myPreferences.this, "Change V2 FEC between transmissions", android.widget.Toast.LENGTH_LONG).show();
					return false;
				}
				try
				{
					if (enabled && !Modem.isV2ModemSupported())
					{
						Toast.makeText(myPreferences.this, "V2 currently supports THOR22 or THOR50x1 only", Toast.LENGTH_LONG).show();
						Utils.println("[SCVA-V2-FEC][RX][STEP=00 STATE] V2 enable rejected: unsupported modem");
						return false;
					}
					Utils.println("[SCVA-V2-FEC][RX][STEP=00 STATE] " + (enabled ? "V2 enabled" : "V2 disabled"));
					p.setSummary(enabled ? "V2 FEC: ON — Experimental" : "V2 FEC: OFF");
				}
				catch (Exception e)
				{
					Toast.makeText(myPreferences.this, "V2 currently supports THOR22 or THOR50x1 only", android.widget.Toast.LENGTH_LONG).show();
					return false;
				}
				return true;
			}
		});
		v2Switch.setSummary(v2Switch.isChecked() ? "V2 FEC: ON — Experimental" : "V2 FEC: OFF");
		Preference v2Test = findPreference("RSFEC_V2_TEST");
		v2Test.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference p)
			{
				try
				{
					Modem.sendV2Diagnostic(v2TestSequence++);
				}
				catch (RuntimeException error)
				{
					android.widget.Toast.makeText(myPreferences.this, error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
				}
				return true;
			}
		});
		Preference readableTest = findPreference("RSFEC_READABLE_TEST");
		readableTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference p)
			{
				try
				{
					Modem.sendReadableRadioTest(v2TestSequence++);
				}
				catch (RuntimeException error)
				{
					android.widget.Toast.makeText(myPreferences.this, error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
				}
				return true;
			}
		});

		Preference legacyApp = (Preference) findPreference("legacy_app");
		legacyApp.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				Utils.println("=======================================");

				Intent intent = new Intent(myPreferences.this, SCVA.class);
				startActivity(intent);
				return true;
			}
		});

		// Now add the dynamic part of the preferences (mode list etc...).
		PreferenceCategory targetCategory = (PreferenceCategory) findPreference("listofmodestouse");
		for (int i = 0; i < Modem.numModes; i++)
		{
			// create one check box for each setting you need
			CheckBoxPreference checkBoxPreference = new CheckBoxPreference(this);
			// make sure each key is unique
			checkBoxPreference.setKey("USE" + Modem.modemCapListString[i]);
			checkBoxPreference.setTitle("Use " + Modem.modemCapListString[i]);
			// checkBoxPreference.setChecked(true);
			targetCategory.addPreference(checkBoxPreference);
		}

		Preference enc_key = (Preference) findPreference("enc_key");
		enc_key.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				Utils.println("=======================================");

				Intent intent = new Intent(myPreferences.this, KeysActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				intent.putExtra("TYPE", "ALL");
				startActivity(intent);
				return true;
			}
		});

		Preference appPin = (Preference) findPreference("app_pin");
		appPin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				Intent intent = new Intent(myPreferences.this, ChangeVerificationPINActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				intent.putExtra("TYPE", "ALL");
				startActivity(intent);
				return true;
			}
		});

		Preference advanceMode = (Preference) findPreference("beginner_mode");
		if (!GS.gI().getValue().getBoolean("beginner_mode", false))
		{
			advanceMode.setTitle("Advance Mode");
		}
		else
		{
			advanceMode.setTitle("Beginner Mode");
		}
		advanceMode.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
		{
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				new AlertDialog.Builder(myPreferences.this).setMessage("Are you sure you want to change the mode ?").setCancelable(true)
						.setPositiveButton("Yes", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								if (!GS.gI().getValue().getBoolean("beginner_mode", false))
								{
									GS.gI().getEditor().putBoolean("beginner_mode", true).apply();

									Intent intent = new Intent(myPreferences.this, AllChatsActivity.class);
									intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
									startActivity(intent);
								}
								else
								{
									GS.gI().getEditor().putBoolean("beginner_mode", false).apply();

									Intent intent = new Intent(myPreferences.this, AllChatsActivity.class);
									intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
									startActivity(intent);
								}
							}
						}).setNegativeButton("No", null).show();
				return true;
			}
		});

		// to remove in each mode

		PreferenceScreen encryptionCategory = (PreferenceScreen) findPreference("encryption");
		screen.removePreference(encryptionCategory);

		Preference userPS = (Preference) findPreference("userPS");
		screen.removePreference(userPS);

		Preference RadiogramPS = (Preference) findPreference("RadiogramPS");
		screen.removePreference(RadiogramPS);

		Preference filePS = (Preference) findPreference("filePS");
		screen.removePreference(filePS);

		// to remove in user mode
		if (!Constants.devMode)
		{
            PreferenceScreen modemCategory = (PreferenceScreen) findPreference("rsFECPS");
			screen.removePreference(legacyApp);
            modemCategory.removePreference(v2Test);
            modemCategory.removePreference(readableTest);
		}

		// to remove in advance mode
		if (!GS.gI().isAdvanceMode())
		{
			Preference modeList = (Preference) findPreference("modelistPS");
			screen.removePreference(modeList);
			Preference useModeList = (Preference) findPreference("USEMODELIST");
			screen.removePreference(useModeList);

			Preference modemPS = (Preference) findPreference("modemPS");
			screen.removePreference(modemPS);

			Preference dataexchangePS = (Preference) findPreference("dataexchangePS");
			screen.removePreference(dataexchangePS);

			// Preference encryption = (Preference) findPreference("encryption");
			// screen.removePreference(encryption);

			Preference imageattachmentPS = (Preference) findPreference("imageattachmentPS");
			screen.removePreference(imageattachmentPS);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		// if (GS.gI().getValue().getString("enc_algorithm", "").equals("blowfish"))
		// key_lenght = 56;
		// else
		// key_lenght = 32;
		// // Validate Encryption Key length
		// if (GS.gI().getValue().getBoolean("use_enc", false))
		// {
		// if (new KeysDataSource(myPreferences.this).getActiveKey() == null)
		// {
		// Toast.makeText(myPreferences.this, "There should be at-least one active key
		// to activate encryption.", Toast.LENGTH_LONG).show();
		// // Toast.makeText(myPreferences.this, "Please add and Unlock a key to enable
		// // encryption", Toast.LENGTH_LONG).show();
		// GS.gI().getEditor().putBoolean("use_enc", false).commit();
		// try {
		// CheckBoxPreference enc_use = (CheckBoxPreference) findPreference("use_enc");
		// enc_use.setChecked(false);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		// }
		// Implementation
		if (key.equals("AFREQUENCY") || key.equals("SLOWCPU") || key.startsWith("USE") || key.equals("RSID_ERRORS") || key.equals("RSIDWIDESEARCH"))
		{
			GS.gI().setRXParamsChanged(true);
		}

	}
}
