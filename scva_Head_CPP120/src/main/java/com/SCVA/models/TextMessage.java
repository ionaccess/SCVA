package com.SCVA.models;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by §∞§ on 03/05/2026.
 */
public class TextMessage implements Parcelable
{
	private int id;
    private int type;
    private int keyId;
    private String time = "";
    private boolean header = false;
    private boolean encrypted = false;
    public static final int MY_TEXT = 0;
    public static final int HIS_TEXT = 1;
    public static final int MY_KEY = 2;
    public static final int HIS_KEY = 3;
    public static final int MY_IMAGE = 4;
    public static final int HIS_IMAGE = 5;

	public int getId()
	{
		return id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public int getKeyId()
	{
		return keyId;
	}

	public void setKeyId(int keyId)
	{
		this.keyId = keyId;
	}

	public boolean isHeader()
	{
		return header;
	}

	public void setHeader(boolean header)
	{
		this.header = header;
	}

	public String getTime()
	{
		return time;
	}

	public void setTime(String time)
	{
		this.time = time;
	}

	public int getType()
	{
		return type;
	}

	public void setType(int type)
	{
		this.type = type;
	}

	public boolean isEncrypted()
	{
		return encrypted;
	}

	public void setEncrypted(boolean encrypted)
	{
		this.encrypted = encrypted;
	}

	public int getContactId()
	{
		return contactId;
	}

	public void setContactId(int contactId)
	{
		this.contactId = contactId;
	}

	public String getDate()
	{
		return date;
	}

	public void setDate(String date)
	{
		this.date = date;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(String message)
	{
		this.message = message;
	}

	private int contactId;
	private String date = "";
	private String message = "";

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeInt(this.id);
		dest.writeInt(this.keyId);
		dest.writeByte(this.header ? (byte) 1 : (byte) 0);
		dest.writeString(this.time);
		dest.writeInt(this.type);
		dest.writeByte(this.encrypted ? (byte) 1 : (byte) 0);
		dest.writeInt(this.contactId);
		dest.writeString(this.date);
		dest.writeString(this.message);
	}

	public TextMessage()
	{
	}

	protected TextMessage(Parcel in)
	{
		this.id = in.readInt();
		this.keyId = in.readInt();
		this.header = in.readByte() != 0;
		this.time = in.readString();
		this.type = in.readInt();
		this.encrypted = in.readByte() != 0;
		this.contactId = in.readInt();
		this.date = in.readString();
		this.message = in.readString();
	}

	public static final Creator<TextMessage> CREATOR = new Creator<TextMessage>()
	{
		@Override
		public TextMessage createFromParcel(Parcel source)
		{
			return new TextMessage(source);
		}

		@Override
		public TextMessage[] newArray(int size)
		{
			return new TextMessage[size];
		}
	};
}
