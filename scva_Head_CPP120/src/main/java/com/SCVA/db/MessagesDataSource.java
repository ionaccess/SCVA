package com.SCVA.db;

import java.util.ArrayList;

import com.SCVA.Utils.Utils;
import com.SCVA.models.Contact;
import com.SCVA.models.TextMessage;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

public class MessagesDataSource extends SqliteHelper
{
	public static String TABLE_NAME = "tblMessages";

	public static String ID = "id";

	public static String CONTACT_ID = "contactId";

	public static String KEY_ID = "keyId";

	public static String MESSAGE = "message";

	public static String DATE = "date";

	public static String ENCRYPTED = "encrypted";

	public static String TYPE = "type";

	public MessagesDataSource(Context context)
	{
		super(context);
	}

	public void create(TextMessage item)
	{
		ContentValues values = new ContentValues();
		values.put(DATE, item.getDate());
		values.put(TYPE, item.getType());
		values.put(KEY_ID, item.getKeyId());
		values.put(MESSAGE, item.getMessage());
		values.put(ENCRYPTED, item.isEncrypted());
		values.put(CONTACT_ID, item.getContactId());
		create(TABLE_NAME, values);
	}

	public boolean update(TextMessage item)
	{
		ContentValues values = new ContentValues();
		values.put(DATE, item.getDate());
		values.put(TYPE, item.getType());
		values.put(TYPE, item.getType());
		values.put(KEY_ID, item.getKeyId());
		values.put(MESSAGE, item.getMessage());
		values.put(ENCRYPTED, item.isEncrypted());
		values.put(CONTACT_ID, item.getContactId());

		String whereClause = "id = " + item.getId();
		return update(TABLE_NAME, values, whereClause);
	}

	public boolean delete(TextMessage item)
	{
		String whereClause = "rowid = " + item.getId();
		return delete(TABLE_NAME, whereClause);
	}

	public ArrayList<TextMessage> getAllMessages()
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + "";
		return getData(sql, false);
	}

	public ArrayList<TextMessage> getUserMessages(int userId)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE " + CONTACT_ID + " = " + userId;
		return getData(sql, false);
	}

	public boolean deleteUserMessages(int userId)
	{
		String whereClause = "contactId = " + userId;
		return delete(TABLE_NAME, whereClause);
	}

	@SuppressLint("Range")
	public ArrayList<TextMessage> getData(String sql, boolean isSearch)
	{
		ArrayList<TextMessage> list = new ArrayList<TextMessage>();
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				TextMessage data = new TextMessage();

				data.setId(cursor.getInt(cursor.getColumnIndex(ID)));
				data.setType(cursor.getInt(cursor.getColumnIndex(TYPE)));
				data.setDate(cursor.getString(cursor.getColumnIndex(DATE)));
				data.setKeyId(cursor.getInt(cursor.getColumnIndex(KEY_ID)));
				data.setMessage(cursor.getString(cursor.getColumnIndex(MESSAGE)));
				data.setContactId(cursor.getInt(cursor.getColumnIndex(CONTACT_ID)));
				data.setEncrypted(cursor.getInt(cursor.getColumnIndex(ENCRYPTED)) == 1);

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

	public void burnMessages()
	{
		execSQL("DELETE FROM " + TABLE_NAME);
	}
}