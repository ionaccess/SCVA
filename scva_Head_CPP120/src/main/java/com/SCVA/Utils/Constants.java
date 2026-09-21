package com.SCVA.Utils;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class Constants
{
	/**
	 * ⚠️ All testing data is being fed using ⚠️
     * ⚠️ this field mark false before build ⚠️
	 */
	public static boolean devMode = true;
	// ⚠️ Remove before code push ⚠️
	public static String logNumber = "";
	public final static int BAUD_RATE = 115200; // 38400
	public final static int MODEMVIEWwithWF = 4;
	public final static int TERMVIEW = 1;
	public final static int MODEMVIEWnoWF = 3;
	public final static int INBOX_VIEW = 5;
	public final static int DRAFTS_VIEW = 6;
	public final static int OUTBOX_VIEW = 7;
	public final static int FORMSVIEW = 8;
	public final static int TEMPLATESVIEW = 9;
	public final static int SENTITEMSVIEW = 10;
	public final static int ABOUTVIEW = 21;
	public static final String DirImages = "NBEMS-Images";
	public static final String DirSent = "Sent";
	public static final String Dirprefix = "";
	public static final String HomePath = "";
	public static final String tempCSVfn = "csvdata.csv";
	public static final String DirDisplayForms = "DisplayForms";
	public static final String Separator = "";
	public static int imageTxModemIndex = 0;
	public static final String DirTemp = "Temp";
	public static final String DirInbox = "Inbox";
	public static final String DirOutbox = "Outbox";
	public static final String DATE_FORMAT = "dd-MMM-yy";
	public static final String DirTemplates = "Templates";
	public static final String tempPicfn = "tempicture.jpg";
	public static final String DirEntryForms = "EntryForms";
	public static final String DATE_FORMAT_DB = "dd-mm-yy";
	public static final String SETTINGS_USER_PIN = "SETTINGS_USER_PIN";
	public static final String SETTINGS_FIRST_RUN = "SETTINGS_FIRST_RUN";
	public static final String DEFAULT_PREFERENCES = "com.SCVA_preferences";
	public static final String SETTINGS_RIG_CONTROL = "SETTINGS_RIG_CONTROL";
	public static final String SETTINGS_RIG_AUDIO_OVER_USB = "SETTINGS_RIG_AUDIO_OVER_USB";
	public static final String SETTINGS_RIG_CONTROL_DELAY = "SETTINGS_RIG_CONTROL_DELAY";
	public static String[] monthNames =
	{ "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };
	public static final String SETTINGS_SCHEDULE_SELECTION = "SETTINGS_SCHEDULE_SELECTION";
	public static final String SETTINGS_SCHEDULE_MESSAGE = "SETTINGS_SCHEDULE_MESSAGE";
	public static final String SETTINGS_SCHEDULE_TIME = "SETTINGS_SCHEDULE_TIME";
	public static final String SETTINGS_REPEAT_TIMES = "SETTINGS_REPEAT_TIMES";
	public static final String SETTINGS_SCHEDULE_DELAY = "SETTINGS_SCHEDULE_DELAY";
	public static final String SETTINGS_RIG_FREQUENCY = "SETTINGS_SCHEDULE_FREQUENCY";

}
