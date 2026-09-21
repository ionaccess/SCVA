package com.SCVA;

import com.SCVA.Utils.GS;
import com.SCVA.Utils.TruSDXCat;
import com.SCVA.Utils.Utils;
import android.app.Application;

public class SCVADebug extends Application
{
	@Override
	public void onCreate()
	{
		super.onCreate();
		GS.gI().setContext(this);
		if (GS.gI().getValue().getBoolean("SUGGESTED_CONFIG", true))
		{
			config.setSuggestedValues();
			GS.gI().getEditor().putBoolean("SUGGESTED_CONFIG", false).apply();
			Utils.println("SUGGESTED_CONFIG DONE");
		}

		// Initialization of ACRA error reporting
		// ACRA.init(this);
		// Utils.println("APP Started...");
	}
}