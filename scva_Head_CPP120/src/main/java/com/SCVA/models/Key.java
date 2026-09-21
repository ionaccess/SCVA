package com.SCVA.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.SCVA.Utils.Utils;
import com.SCVA.encryption.Aes256Encryptor;

public class Key implements Parcelable
{
	private int id;
	private String name;
	private String keyText;
	private int userGroupId;
	private boolean isDefault;
	private boolean isActive;
	private String createdOn;
	public int getId()
	{
		return id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getKeyText()
	{
		return keyText;
	}

	public void setKeyText(String keyText)
	{
		this.keyText = keyText;
	}

	public int getUserGroupId()
	{
		return userGroupId;
	}

	public void setUserGroupId(int userGroupId)
	{
		this.userGroupId = userGroupId;
	}

	public boolean isDefault()
	{
		return isDefault;
	}

	public void setDefault(boolean aDefault)
	{
		isDefault = aDefault;
	}

	public boolean isActive()
	{
		return isActive;
	}

	public void setActive(boolean active)
	{
		isActive = active;
	}

	public String getCreatedOn()
	{
		return createdOn;
	}

	public void setCreatedOn(String createdOn)
	{
		this.createdOn = createdOn;
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
		dest.writeString(this.name);
		dest.writeString(this.keyText);
		dest.writeInt(this.userGroupId);
		dest.writeByte(this.isDefault ? (byte) 1 : (byte) 0);
		dest.writeByte(this.isActive ? (byte) 1 : (byte) 0);
		dest.writeString(this.createdOn);
	}

	public Key()
	{
	}

	protected Key(Parcel in)
	{
		this.id = in.readInt();
		this.name = in.readString();
		this.keyText = in.readString();
		this.userGroupId = in.readInt();
		this.isDefault = in.readByte() != 0;
		this.isActive = in.readByte() != 0;
		this.createdOn = in.readString();
	}

	public static final Creator<Key> CREATOR = new Creator<Key>()
	{
		@Override
		public Key createFromParcel(Parcel source)
		{
			return new Key(source);
		}

		@Override
		public Key[] newArray(int size)
		{
			return new Key[size];
		}
	};

	public String getPlainKey(String password)
	{
		String pKey = "";
		Aes256Encryptor aes256Encryptor = new Aes256Encryptor(new Utils().generateMd5(password));
		pKey = aes256Encryptor.decrypt(keyText);
		if (pKey == null) pKey = "";
		return pKey;
	}
}
