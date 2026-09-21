package com.SCVA.db;

import java.util.ArrayList;

import com.SCVA.Utils.Utils;
import com.SCVA.models.Chat;
import com.SCVA.models.Contact;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

public class ChatsDataSource extends SqliteHelper
{
	public static String TABLE_NAME = "tblChats";

	public static String ID = "id";

	public static String KEY_ID = "keyId";

	public static String DATE = "date";

	public static String READ = "read";

	public static String TITLE = "title";

	public static String CONTACT_ID = "contactId";

	public static String MESSAGE = "message";

	public static String LAST_SEEN = "lastSeen";

	public ChatsDataSource(Context context)
	{
		super(context);
	}

	public int create(Chat item)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_ID, item.getKeyId());
		values.put(DATE, item.getDate());
		values.put(READ, item.isRead());
		values.put(TITLE, item.getTitle());
		values.put(CONTACT_ID, item.getContactId());
		values.put(MESSAGE, item.getMessage());
		values.put(LAST_SEEN, item.getLastSeen());
		return (int) create(TABLE_NAME, values);
	}

	public boolean update(Chat item)
	{
		ContentValues values = new ContentValues();
		values.put(KEY_ID, item.getKeyId());
		values.put(DATE, item.getDate());
		values.put(READ, item.isRead());
		values.put(TITLE, item.getTitle());
		values.put(CONTACT_ID, item.getContactId());
		values.put(MESSAGE, item.getMessage());
		values.put(LAST_SEEN, item.getLastSeen());

		String whereClause = "id = " + item.getId();
		return update(TABLE_NAME, values, whereClause);
	}

	public boolean delete(Chat item)
	{
		String whereClause = "rowid = " + item.getId();
		return delete(TABLE_NAME, whereClause);
	}

	public ArrayList<Chat> getAllChats()
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " ORDER BY date DESC";
		return getData(sql, false);
	}

	public Chat getChatAgainstId(int id)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE id = " + id;
		ArrayList<Chat> list = getData(sql, false);
		if (list.size() > 0) return list.get(0);
		return null;
	}

	public boolean deleteChatAgainstContactId(int contactId)
	{
		String whereClause = "contactId = " + contactId;
		return delete(TABLE_NAME, whereClause);
	}

	public int getChatIdAgainstContact(int contactId)
	{
        String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE contactId = " + contactId;
        ArrayList<Chat> list = getData(sql, false);
        if (list.size() > 0) return list.get(0).getId();
		return 0;
	}

	@SuppressLint("Range")
	public ArrayList<Chat> getData(String sql, boolean isSearch)
	{
		ArrayList<Chat> list = new ArrayList<Chat>();
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				Chat data = new Chat();

				data.setId(cursor.getInt(cursor.getColumnIndex(ID)));
				data.setKeyId(cursor.getInt(cursor.getColumnIndex(KEY_ID)));
				data.setDate(cursor.getString(cursor.getColumnIndex(DATE)));
				data.setRead(cursor.getInt(cursor.getColumnIndex(READ)) == 1);
				data.setTitle(cursor.getString(cursor.getColumnIndex(TITLE)));
				data.setContactId(cursor.getInt(cursor.getColumnIndex(CONTACT_ID)));
				data.setMessage(cursor.getString(cursor.getColumnIndex(MESSAGE)));
				data.setLastSeen(cursor.getString(cursor.getColumnIndex(LAST_SEEN)));

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

	public void burnChats()
	{
		execSQL("DELETE FROM " + TABLE_NAME);
	}
}