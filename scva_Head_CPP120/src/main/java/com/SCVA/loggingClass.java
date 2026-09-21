package com.SCVA;

import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;

public class loggingClass
{
	private static String Application = "";

	public loggingClass(String app)
	{
		Application = app;
	}

	// Android allow for different log levels
	public static void writelog(String msg, Exception e, boolean b)
	{
		// Android For the moment send this to the terminal
		if (e == null)
		{
            e.printStackTrace();
            Utils.println(Application, msg);
		}
		else
		{
            e.printStackTrace();
			Utils.println(Application, msg, e);
		}
		GS.gI().TermWindow += msg;
//		SCVA.mHandler.post(SCVA.addToTerminal);
	}
}