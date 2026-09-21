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
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "data.db";
	private static String DB_PATH = "/data/data/com.SCVA/databases/";

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
		// db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEDIA);
		// db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
		onCreate(db);
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
		SQLiteDatabase checkDB = null;
		try
		{
			String myPath = DB_PATH + DATABASE_NAME;
			checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
		}
		catch (SQLiteException e)
		{
			e.printStackTrace();
		}

		if (checkDB != null)
		{
			checkDB.close();
		}
		return checkDB != null ? true : false;
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