package com.SCVA.models;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by §∞§ on 04/05/2026.
 */
public class Chat implements Parcelable
{
	private int id;
	private Key key;
	private int keyId;
	private String date = "";
	private boolean read;
	private String title = "";
	private int contactId;
	private String passKey = "";

	public int getId()
	{
		return id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public Key getKey()
	{
		return key;
	}

	public void setKey(Key key)
	{
		this.key = key;
	}

	public int getKeyId()
	{
		return keyId;
	}

	public void setKeyId(int keyId)
	{
		this.keyId = keyId;
	}

	public String getDate()
	{
		return date;
	}

	public void setDate(String date)
	{
		this.date = date;
	}

	public boolean isRead()
	{
		return read;
	}

	public void setRead(boolean read)
	{
		this.read = read;
	}

	public String getTitle()
	{
		if (contact != null) return contact.getName();
		return title;
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	public int getContactId()
	{
		if (contact != null) return contact.getId();
		return contactId;
	}

	public void setContactId(int contactId)
	{
		this.contactId = contactId;
	}

	public String getPassKey()
	{
		return passKey;
	}

	public void setPassKey(String passKey)
	{
		this.passKey = passKey;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(String message)
	{
		this.message = message;
	}

	public Contact getContact()
	{
		return contact;
	}

	public void setContact(Contact contact)
	{
		this.contact = contact;
	}

	public String getLastSeen()
	{
		return lastSeen;
	}

	public void setLastSeen(String lastSeen)
	{
		this.lastSeen = lastSeen;
	}

	private String message = "";
	private Contact contact;
	private String lastSeen = "";

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeInt(this.id);
		dest.writeParcelable(this.key, flags);
		dest.writeInt(this.keyId);
		dest.writeString(this.date);
		dest.writeByte(this.read ? (byte) 1 : (byte) 0);
		dest.writeString(this.title);
		dest.writeInt(this.contactId);
		dest.writeString(this.passKey);
		dest.writeString(this.message);
		dest.writeParcelable(this.contact, flags);
		dest.writeString(this.lastSeen);
	}

	public Chat()
	{
	}

	protected Chat(Parcel in)
	{
		this.id = in.readInt();
		this.key = in.readParcelable(Key.class.getClassLoader());
		this.keyId = in.readInt();
		this.date = in.readString();
		this.read = in.readByte() != 0;
		this.title = in.readString();
		this.contactId = in.readInt();
		this.passKey = in.readString();
		this.message = in.readString();
		this.contact = in.readParcelable(Contact.class.getClassLoader());
		this.lastSeen = in.readString();
	}

	public static final Creator<Chat> CREATOR = new Creator<Chat>()
	{
		@Override
		public Chat createFromParcel(Parcel source)
		{
			return new Chat(source);
		}

		@Override
		public Chat[] newArray(int size)
		{
			return new Chat[size];
		}
	};
}
