package com.SCVA.models;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by §∞§ on 29/04/2026.
 */
public class Contact implements Parcelable
{
	private int id = -1;
	private int msgCount = 0;
	private int configId;
	private String name;
	private String keyGroupId;
	private boolean isActive;
	private boolean isDefault;
	private String note;
	private String mobileNumber;
	private String description;
	public int getId()
	{
		return id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public int getMsgCount()
	{
		return msgCount;
	}

	public void setMsgCount(int msgCount)
	{
		this.msgCount = msgCount;
	}

	public int getConfigId()
	{
		return configId;
	}

	public void setConfigId(int configId)
	{
		this.configId = configId;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getKeyGroupId()
	{
		return keyGroupId;
	}

	public void setKeyGroupId(String keyGroupId)
	{
		this.keyGroupId = keyGroupId;
	}

	public boolean isActive()
	{
		return isActive;
	}

	public void setActive(boolean active)
	{
		isActive = active;
	}

	public boolean isDefault()
	{
		return isDefault;
	}

	public void setDefault(boolean aDefault)
	{
		isDefault = aDefault;
	}

	public String getNote()
	{
		return note;
	}

	public void setNote(String note)
	{
		this.note = note;
	}

	public String getMobileNumber()
	{
		return mobileNumber;
	}

	public void setMobileNumber(String mobileNumber)
	{
		this.mobileNumber = mobileNumber;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeInt(this.id);
		dest.writeInt(this.msgCount);
		dest.writeInt(this.configId);
		dest.writeString(this.name);
		dest.writeString(this.keyGroupId);
		dest.writeByte(this.isActive ? (byte) 1 : (byte) 0);
		dest.writeByte(this.isDefault ? (byte) 1 : (byte) 0);
		dest.writeString(this.note);
		dest.writeString(this.mobileNumber);
		dest.writeString(this.description);
	}

	public Contact()
	{
	}

	protected Contact(Parcel in)
	{
		this.id = in.readInt();
		this.msgCount = in.readInt();
		this.configId = in.readInt();
		this.name = in.readString();
		this.keyGroupId = in.readString();
		this.isActive = in.readByte() != 0;
		this.isDefault = in.readByte() != 0;
		this.note = in.readString();
		this.mobileNumber = in.readString();
		this.description = in.readString();
	}

	public static final Creator<Contact> CREATOR = new Creator<Contact>()
	{
		@Override
		public Contact createFromParcel(Parcel source)
		{
			return new Contact(source);
		}

		@Override
		public Contact[] newArray(int size)
		{
			return new Contact[size];
		}
	};
}
