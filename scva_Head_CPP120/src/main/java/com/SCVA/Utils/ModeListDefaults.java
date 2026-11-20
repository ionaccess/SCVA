package com.SCVA.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.SCVA.db.ConfigurationsDataSource;

/** Applies the modem-list defaults only while establishing a fresh install. */
public final class ModeListDefaults
{
	private static final String INITIALIZED_KEY = "MODE_LIST_DEFAULTS_INITIALIZED";

	private ModeListDefaults() {}

	public static synchronized void initializeForFreshInstall(Context context)
	{
		SharedPreferences preferences = context.getSharedPreferences(Constants.DEFAULT_PREFERENCES, Context.MODE_PRIVATE);
		if (preferences.getBoolean(INITIALIZED_KEY, false)) return;

		ConfigurationsDataSource configurations = new ConfigurationsDataSource(context);
		boolean hasConfigurations;
		try
		{
			hasConfigurations = !configurations.getAllConfigurations().isEmpty();
		}
		finally
		{
			configurations.close();
		}

		SharedPreferences.Editor editor = preferences.edit();
		if (!hasConfigurations && !hasSavedModeListPreferences(preferences))
		{
			editor.putBoolean("USEMODELIST", true);
			for (String modemName : Constants.V2_PASSED_MODEMS)
			{
				editor.putBoolean("USE" + modemName, true);
			}
		}
		editor.putBoolean(INITIALIZED_KEY, true).apply();
	}

	private static boolean hasSavedModeListPreferences(SharedPreferences preferences)
	{
		if (preferences.contains("USEMODELIST")) return true;
		for (String modemName : Constants.V2_PASSED_MODEMS)
		{
			if (preferences.contains("USE" + modemName)) return true;
		}
		return false;
	}
}
