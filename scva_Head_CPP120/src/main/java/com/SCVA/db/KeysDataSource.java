package com.SCVA.db;

import java.util.ArrayList;
import java.util.Date;

import com.SCVA.Utils.Utils;
import com.SCVA.models.Key;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

public class KeysDataSource extends SqliteHelper
{
    public static final class KeyHistoryPolicyException extends IllegalStateException {
        public KeyHistoryPolicyException() { super("Key material/contact cannot be replaced, and a key referenced by encrypted history cannot be deleted. Create/import a new key row for replacement; keep the old key for history."); }
    }
	public static String TABLE_NAME = "tblKeys";

	public static String ID = "id";

	public static String NAME = "name";

	public static String KEY_TEXT = "keyText";

	public static String USER_GROUP_ID = "userGroupId";

	public static String DATE = "date";

	public static String IS_DEFAULT = "isDefault";

	public static String IS_ACTIVE = "isActive";

	public KeysDataSource(Context context)
	{
		super(context);
	}

	public void create(Key item)
	{
        requireMessageMetadataSchema();
		ContentValues values = new ContentValues();
		values.put(NAME, item.getName());
		values.put(DATE, item.getCreatedOn());
		values.put(IS_ACTIVE, item.isActive());
		values.put(KEY_TEXT, item.getKeyText());
		values.put(IS_DEFAULT, item.isDefault());
		values.put(USER_GROUP_ID, item.getUserGroupId());
		long id=insertSensitive(TABLE_NAME, values);
        if(id<=0)throw new IllegalStateException("Key storage failed");item.setId((int)id);
	}

	public void markKeyAsDefault(Key key)
	{
        requireMessageMetadataSchema();
		String query = "UPDATE " + TABLE_NAME + " SET " + IS_DEFAULT + " = CASE WHEN id = " + key.getId() + " THEN 1 ELSE 0 END WHERE " + USER_GROUP_ID + " = "
				+ key.getUserGroupId();
		execSQL(query);
	}

	public void markKeyActive(Key key)
	{
        requireMessageMetadataSchema();
		String query = "UPDATE " + TABLE_NAME + " SET " + IS_ACTIVE + " = CASE WHEN id = " + key.getId() + " THEN 1 ELSE 0 END";
		execSQL(query);
	}

	public boolean update(Key item)
	{
        requireMessageMetadataSchema();
		ContentValues values = new ContentValues();
		values.put(NAME, item.getName());
		values.put(KEY_TEXT, item.getKeyText());
		values.put(USER_GROUP_ID, item.getUserGroupId());
		values.put(DATE, item.getCreatedOn());
		values.put(IS_DEFAULT, item.isDefault());
		values.put(IS_ACTIVE, item.isActive());

		String whereClause = "id = " + item.getId();
		try { return update(TABLE_NAME, values, whereClause); }
        catch(android.database.sqlite.SQLiteConstraintException blocked) { close();throw new KeyHistoryPolicyException(); }
	}

	public boolean delete(Key item)
	{
        requireMessageMetadataSchema();
		String whereClause = "rowid = " + item.getId();
		try { return delete(TABLE_NAME, whereClause); }
        catch(android.database.sqlite.SQLiteConstraintException blocked) { close();throw new KeyHistoryPolicyException(); }
	}

	public ArrayList<Key> getAllKeys()
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + "";
		return getData(sql, false);
	}

	public ArrayList<Key> getUserKeys(int userId)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE userGroupId = " + userId;
		return getData(sql, false);
	}

	public Key getDefaultKey(int userGroupId)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE isDefault = 1 AND userGroupId = " + userGroupId;
		if (userGroupId == 0)
		{
			sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE isDefault = 1";
		}
		ArrayList<Key> list = getData(sql, false);
		if (list.size() > 0) return list.get(0);
		return null;
	}

	@SuppressLint("Range")
	public ArrayList<Key> getData(String sql, boolean isSearch)
	{
		ArrayList<Key> list = new ArrayList<Key>();
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				Key data = new Key();

				data.setId(cursor.getInt(cursor.getColumnIndex(ID)));
				data.setName(cursor.getString(cursor.getColumnIndex(NAME)));
				data.setKeyText(cursor.getString(cursor.getColumnIndex(KEY_TEXT)));
				data.setUserGroupId(cursor.getInt(cursor.getColumnIndex(USER_GROUP_ID)));
				data.setDefault(cursor.getInt(cursor.getColumnIndex(IS_DEFAULT)) == 1);
				data.setActive(cursor.getInt(cursor.getColumnIndex(IS_ACTIVE)) == 1);
				data.setCreatedOn(cursor.getString(cursor.getColumnIndex(DATE)));

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

	public boolean deleteUserKeys(int userId)
	{
        requireMessageMetadataSchema();
		String whereClause = "userGroupId = " + userId;
		try { return delete(TABLE_NAME, whereClause); }
        catch(android.database.sqlite.SQLiteConstraintException blocked) { close();throw new KeyHistoryPolicyException(); }
	}

	public int getUserKeyCount(int userId)
	{
		String sql = "SELECT count() FROM tblKeys WHERE userGroupId = " + userId;
		int count = 0;
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				count = cursor.getInt(cursor.getColumnIndex("count()"));
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

	public Key getActiveKey()
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE isActive = 1";
		ArrayList<Key> list = getData(sql, false);
		if (list.size() > 0) return list.get(0);
		return null;
	}

	public void burnKeyStore()
	{
        requireMessageMetadataSchema();
		try { execSQL("DELETE FROM " + TABLE_NAME); }
        catch(android.database.sqlite.SQLiteConstraintException blocked) { close();throw new KeyHistoryPolicyException(); }
	}
}
