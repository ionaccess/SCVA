package com.SCVA.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.SCVA.Utils.Utils;

public class SettingsDataSource extends SqliteHelper
{
	public static String TABLE_NAME = "tblSettings";

	public static final String COLUMN_ID = "serial";

	public static final String COLUMN_KEY = "key";

	public static final String COLUMN_VALUE = "value";

	public SettingsDataSource(Context context)
	{
		super(context);
	}

	private void create(String key, String value)
	{
		ContentValues values = new ContentValues();
		values.put(COLUMN_KEY, key);
		values.put(COLUMN_VALUE, value);
		create(TABLE_NAME, values);
	}

	public boolean updateSettingValue(String key, String value)
	{
		ContentValues values = new ContentValues();
		values.put("value", value);
		String whereClause = "key = '" + key + "'";

        return update(TABLE_NAME, values, whereClause);
	}

	@SuppressLint("Range")
	public String getValueAgainstKey(String key, String defaultValue)
	{
		try
		{
			openDataBase();
			String sql = "SELECT value FROM " + TABLE_NAME + " WHERE key = '" + key + "'";
			Cursor cursor = getConnection().rawQuery(sql, null);
			if (cursor.moveToNext())
			{
				defaultValue = cursor.getString(cursor.getColumnIndex(COLUMN_VALUE));
			}
			else
			{
				create(key, defaultValue);
				Utils.println("Key not found");
			}
			cursor.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
		close();
		return defaultValue;
	}

	public void burnSettings()
    {
        execSQL("DELETE FROM " + TABLE_NAME);
    }
}