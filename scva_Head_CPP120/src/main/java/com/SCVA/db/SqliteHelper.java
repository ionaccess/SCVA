package com.SCVA.db;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import android.content.Context;
import java.io.FileOutputStream;
import android.content.ContentValues;
import com.SCVA.Utils.Utils;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

public class SqliteHelper extends SQLiteOpenHelper
{
	protected Context context;
	protected SQLiteDatabase database;
	private static final int DATABASE_VERSION = 7;
	private static final String DATABASE_NAME = "data.db";
	private static String DB_PATH = "/data/data/com.SCVA/databases/";

    public static final class UnsupportedMessageSchemaException extends IllegalStateException {
        public UnsupportedMessageSchemaException() { super("Unsupported message database schema. A049 requires a clean install/test database. Existing data was not replaced; no migration/backfill is provided."); }
    }
    /** Read-only preflight, before any message write or existing configuration upgrade. */
    public final void requireMessageMetadataSchema() {
        SQLiteDatabase check=null;
        try {
            check=SQLiteDatabase.openDatabase(DB_PATH+DATABASE_NAME,null,SQLiteDatabase.OPEN_READONLY);
            java.util.Set<String> columns=new java.util.HashSet<>();
            Cursor c=check.rawQuery("PRAGMA table_info(tblMessages)",null);
            try { while(c.moveToNext())columns.add(c.getString(1)); } finally { c.close(); }
            if(check.getVersion()<5 || check.getVersion()>DATABASE_VERSION || !columns.containsAll(java.util.Arrays.asList("encryptionAlgorithm","ciphertextFormat","ciphertextVersion","encryptionParameters")))throw new UnsupportedMessageSchemaException();
            java.util.Set<String> guards=new java.util.HashSet<>();
            c=check.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger'",null);
            try { while(c.moveToNext())guards.add(c.getString(0)); } finally { c.close(); }
            if(!guards.containsAll(java.util.Arrays.asList("message_key_insert_guard","message_key_update_guard","key_material_immutable","key_history_delete_guard")))throw new UnsupportedMessageSchemaException();
            c=check.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='tblKeys'",null);
            try { if(!c.moveToNext() || !c.getString(0).toUpperCase(java.util.Locale.ROOT).contains("AUTOINCREMENT"))throw new UnsupportedMessageSchemaException(); }
            finally { c.close(); }
        } catch(UnsupportedMessageSchemaException unsupported) { throw unsupported; }
        catch(RuntimeException unavailable) { throw new UnsupportedMessageSchemaException(); }
        finally { if(check!=null)check.close(); }
    }

	public SqliteHelper(Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.context = context;
		createDataBase();
	}

	@Override
	public void onCreate(SQLiteDatabase database)
	{
		this.database = database;
		createEventsTable();
	}

	protected void createEventsTable()
	{
		// String settings = "CREATE TABLE '" + SqliteHelper.TABLE_SETTINGS + "' (serial
		// INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT DEFAULT NULL, value TEXT DEFAULT
		// NULL)";
		// database.execSQL(settings);
		//
		// String media = "CREATE TABLE " + TABLE_MEDIA + " (id TEXT DEFAULT NULL,
		// product_id TEXT DEFAULT NULL, url TEXT, title TEXT, description TEXT,
		// isBanner TEXT)";
		// database.execSQL(media);
		//
		// database.execSQL("INSERT INTO " + TABLE_SETTINGS + " VALUES ( 2,
		// 'PRODUCT_VIEW_TYPE' , 'GRID')");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		if (oldVersion < 2 && newVersion >= 2) migrateConfigurationSpeed(db);
		if (oldVersion < 3 && newVersion >= 3) migrateGgWaveReceiveProtocols(db);
		if (oldVersion < 4 && newVersion >= 4) migrateConfigurationV2Fec(db);
		if (oldVersion < 5 && newVersion >= 5) migrateModem73Configuration(db);
		if (oldVersion < 6 && newVersion >= 6) migrateModem73VoxConfiguration(db);
		if (oldVersion < 7 && newVersion >= 7) migrateDefaultModem73Encryption(db);
	}

	private static void migrateModem73Configuration(SQLiteDatabase db)
	{
		ensureColumn(db, "modem73Callsign", "TEXT NOT NULL DEFAULT ''");
		ensureColumn(db, "modem73Profile", "TEXT NOT NULL DEFAULT 'OFDM_QPSK_1_2_NORMAL'");
	}

	private static void migrateModem73VoxConfiguration(SQLiteDatabase db)
	{
		ensureColumn(db, "modem73VoxEnabled", "INTEGER NOT NULL DEFAULT 0");
		ensureColumn(db, "modem73VoxLeadMs", "INTEGER NOT NULL DEFAULT 550");
	}

	private static void migrateDefaultModem73Encryption(SQLiteDatabase db)
	{
		db.execSQL("UPDATE tblConfigs SET encryption=1 WHERE modulationType='MODEM73' AND isDefault=1 AND name='MODEM73' AND encryption=0");
	}

	private static void ensureColumn(SQLiteDatabase db, String name, String declaration)
	{
		Cursor columns = db.rawQuery("PRAGMA table_info(tblConfigs)", null);
		boolean found = false;
		try { while (columns.moveToNext()) if (name.equals(columns.getString(1))) found = true; }
		finally { columns.close(); }
		if (!found) db.execSQL("ALTER TABLE tblConfigs ADD COLUMN " + name + " " + declaration);
	}

	private static void migrateConfigurationV2Fec(SQLiteDatabase db)
	{
		Cursor columns = db.rawQuery("PRAGMA table_info(tblConfigs)", null);
		boolean found = false;
		try { while (columns.moveToNext()) if ("v2FecEnabled".equals(columns.getString(1))) found = true; }
		finally { columns.close(); }
		if (!found) db.execSQL("ALTER TABLE tblConfigs ADD COLUMN v2FecEnabled INTEGER NOT NULL DEFAULT 1");
	}

	private static void migrateGgWaveReceiveProtocols(SQLiteDatabase db)
	{
		Cursor columns = db.rawQuery("PRAGMA table_info(tblConfigs)", null);
		boolean found = false;
		try { while (columns.moveToNext()) if ("ggWaveRxProtocols".equals(columns.getString(1))) found = true; }
		finally { columns.close(); }
		if (!found) db.execSQL("ALTER TABLE tblConfigs ADD COLUMN ggWaveRxProtocols TEXT");
	}

	private static void migrateConfigurationSpeed(SQLiteDatabase db)
	{
		Cursor columns = db.rawQuery("PRAGMA table_info(tblConfigs)", null);
		boolean found = false;
		try { while (columns.moveToNext()) if ("ggWaveSpeed".equals(columns.getString(1))) found = true; }
		finally { columns.close(); }
		if (!found) db.execSQL("ALTER TABLE tblConfigs ADD COLUMN ggWaveSpeed TEXT NOT NULL DEFAULT 'DT_FASTEST'");
	}

	public SQLiteDatabase getConnection()
	{
		return database;
	}

	public void createDataBase()
	{
		try
		{
			boolean dbExist = checkDataBase();

			if (dbExist)
			{
				// do nothing - database already exist
			}
			else
			{
				// 1. Force the system to create the empty file and the parent directories
				this.getReadableDatabase();

				// 2. CRITICAL FIX FOR OPPO: Immediately close the connection
				// to release the OS file-lock before overwriting it.
				this.close();

				// 3. Now copy your asset file over the safely closed file path
				copyDataBase();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Check if the database already exist to avoid re-copying the file each time you open the application.
	 * 
	 * @return true if it exists, false if it doesn't
	 */
	private boolean checkDataBase()
	{
        // An existing but unreadable/old file is never permission to replace user data.
        return new java.io.File(DB_PATH + DATABASE_NAME).exists();
	}

	/**
	 * Copies your database from your local assets-folder to the just created empty database in the system folder, from where it can be accessed and handled. This is done by transferring bytestream.
	 */
	private void copyDataBase()
	{
		try
		{
			// Open your local db as the input stream
			InputStream myInput = context.getAssets().open(DATABASE_NAME);

			// Path to the just created empty db
			String outFileName = DB_PATH + DATABASE_NAME;

			// Open the empty db as the output stream
			OutputStream myOutput = new FileOutputStream(outFileName);

			// transfer bytes from the inputfile to the outputfile
			byte[] buffer = new byte[1024];
			int length;
			while ((length = myInput.read(buffer)) > 0)
			{
				myOutput.write(buffer, 0, length);
			}

			// Close the streams
			myOutput.flush();
			myOutput.close();
			myInput.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public SQLiteDatabase openDataBase() throws SQLException
	{
		// Open the database
		String myPath = DB_PATH + DATABASE_NAME;
		database = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
		try
		{
			if (database.getVersion() < DATABASE_VERSION)
			{
				database.beginTransaction();
				try
				{
					if (database.getVersion() < 2) migrateConfigurationSpeed(database);
					if (database.getVersion() < 3) migrateGgWaveReceiveProtocols(database);
					if (database.getVersion() < 4) migrateConfigurationV2Fec(database);
					if (database.getVersion() < 5) migrateModem73Configuration(database);
					if (database.getVersion() < 6) migrateModem73VoxConfiguration(database);
					if (database.getVersion() < 7) migrateDefaultModem73Encryption(database);
					database.setVersion(DATABASE_VERSION);
					database.setTransactionSuccessful();
				}
				finally { database.endTransaction(); }
			}
		}
		catch (RuntimeException migrationFailure)
		{
			database.close(); database = null;
			throw new IllegalStateException("Configuration schema migration failed; stored data was not replaced", migrationFailure);
		}
		return database;

	}

	@Override
	public synchronized void close()
	{
		if (database != null)
		{
			database.close();
		}
		super.close();
	}

	public long create(String tableName, ContentValues values)
	{
		// SELECT last_insert_rowid();
		long lastId = -1;
		try
		{
			openDataBase();
			lastId = database.insert(tableName, null, values);
			close();
		}
		catch (SQLException e)
		{
			close();
			e.printStackTrace();
			return -1;
		}
		saveLastEditTime();
		return lastId;
	}
    /** Avoid insert()'s failure fallback; sensitive records fail explicitly without a values dump. */
    protected long insertSensitive(String tableName,ContentValues values) {
        try { openDataBase();long id=database.insertOrThrow(tableName,null,values);saveLastEditTime();return id; }
        catch(SQLException unavailable) { throw new IllegalStateException("Sensitive record storage unavailable"); }
        finally { close(); }
    }

	public boolean update(String tableName, ContentValues values, String whereClause)
	{
		try
		{
			openDataBase();
			int rowsUpdated = database.update(tableName, values, whereClause, null);
			Utils.println("Rows updated = " + rowsUpdated);
		}
		catch (SQLException e)
		{
			close();
			e.printStackTrace();
			return false;
		}
		close();
		saveLastEditTime();
		return true;
	}

	public boolean execSQL(String query)
	{
		try
		{
			openDataBase();
			database.execSQL(query);
		}
		catch (SQLException e)
		{
			close();
			e.printStackTrace();
			return false;
		}
		close();
		saveLastEditTime();
		return true;
	}

	public boolean delete(String tableName, String whereClause)
	{
		try
		{
			openDataBase();
			int rowsUpdated = database.delete(tableName, whereClause, null);
			Utils.println("Rows deleted = " + rowsUpdated);
		}
		catch (SQLException e)
		{
			close();
			e.printStackTrace();
			return false;
		}
		close();
		saveLastEditTime();
		return true;
	}

	public long getLastEditTime()
	{
		SharedPreferences prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE);
		return prefs.getLong("last_edit_time", 0);
	}

	private void saveLastEditTime()
	{
		SharedPreferences prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE);
		prefs.edit().putLong("last_edit_time", System.currentTimeMillis()).apply();
	}
}
