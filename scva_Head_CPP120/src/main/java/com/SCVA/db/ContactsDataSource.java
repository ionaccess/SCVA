package com.SCVA.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.SCVA.Utils.Utils;
import com.SCVA.models.Contact;

import java.util.ArrayList;

public class ContactsDataSource extends SqliteHelper
{
	public static String TABLE_NAME = "tblContacts";

	public static String ID = "id";

	public static String NAME = "name";

	public static String CONFIG_ID = "configId";

	public static String IS_ACTIVE = "isActive";

	public static String IS_DEFAULT = "isDefault";

	public static String DESCRIPTION = "description";

	public static String KEY_GROUP_ID = "keyGroupId";

	public static String MSG_COUNT = "msgCount";

	public static String MOBILE_NUMBER = "mobileNumber";

	public ContactsDataSource(Context context)
	{
		super(context);
	}

	public long create(Contact item)
	{
		ContentValues values = new ContentValues();
		values.put(NAME, item.getName());
		values.put(IS_ACTIVE, item.isActive());
		values.put(IS_DEFAULT, item.isDefault());
		values.put(CONFIG_ID, item.getConfigId());
		values.put(MSG_COUNT, item.getMsgCount());
		values.put(KEY_GROUP_ID, item.getKeyGroupId());
		values.put(MOBILE_NUMBER, item.getMobileNumber());
		values.put(DESCRIPTION, item.getDescription());
		return create(TABLE_NAME, values);
	}

	public boolean update(Contact item)
	{
		ContentValues values = new ContentValues();
		values.put(NAME, item.getName());
		values.put(IS_ACTIVE, item.isActive());
		values.put(IS_DEFAULT, item.isDefault());
		values.put(MSG_COUNT, item.getMsgCount());
		values.put(CONFIG_ID, item.getConfigId());
		values.put(KEY_GROUP_ID, item.getKeyGroupId());
		values.put(DESCRIPTION, item.getDescription());
		values.put(MOBILE_NUMBER, item.getMobileNumber());

		String whereClause = "id = " + item.getId();
		return update(TABLE_NAME, values, whereClause);
	}

	public boolean delete(Contact item)
	{
		String whereClause = "rowid = " + item.getId();
		return delete(TABLE_NAME, whereClause);
	}

	public ArrayList<Contact> getAllContacts()
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + "";
		return getData(sql, false);
	}

	@SuppressLint("Range")
	public ArrayList<Contact> getData(String sql, boolean isSearch)
	{
		ArrayList<Contact> list = new ArrayList<Contact>();
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				Contact data = new Contact();

				data.setId(cursor.getInt(cursor.getColumnIndex(ID)));
				data.setName(cursor.getString(cursor.getColumnIndex(NAME)));
				data.setMsgCount(cursor.getInt(cursor.getColumnIndex(MSG_COUNT)));
				data.setConfigId(cursor.getInt(cursor.getColumnIndex(CONFIG_ID)));
				data.setActive(cursor.getInt(cursor.getColumnIndex(IS_ACTIVE)) == 1);
				data.setDefault(cursor.getInt(cursor.getColumnIndex(IS_DEFAULT)) == 1);
				data.setKeyGroupId(cursor.getString(cursor.getColumnIndex(KEY_GROUP_ID)));
				data.setDescription(cursor.getString(cursor.getColumnIndex(DESCRIPTION)));
				data.setMobileNumber(cursor.getString(cursor.getColumnIndex(MOBILE_NUMBER)));

				list.add(data);
			}
			cursor.close();
		}
		catch (Exception e)
		{
			close();
			e.printStackTrace();
			Utils.println("Exception: " + e);
		}
		close();
		return list;
	}

	public Contact getContactAgainstId(int userId)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE id = " + userId;
		ArrayList<Contact> list = getData(sql, false);
		if (list.size() > 0) return list.get(0);
		return null;
	}

	public void burnContacts()
	{
		execSQL("DELETE FROM " + TABLE_NAME);
	}

	public int getMessageCount(int userId)
	{
		String sql = "SELECT msgCount FROM " + TABLE_NAME + " WHERE id = " + userId;
		int count = 0;
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				count = cursor.getInt(cursor.getColumnIndex(MSG_COUNT));
			}
			cursor.close();
		}
		catch (Exception e)
		{
			close();
			e.printStackTrace();
			Utils.println("Exception: " + e);
		}
		close();
		return count;
	}
}