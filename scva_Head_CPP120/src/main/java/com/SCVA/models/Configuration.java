package com.SCVA.models;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by §∞§ on 29/04/2026.
 */
public class Configuration implements Parcelable
{
	private int id;
	private boolean afc;
	private String name;
	private int squelch;
	private int frequency;
	private int audioGain;
	private int contactId;
	private boolean active;
	private boolean tXRsID;
	private boolean rXRsID;
	private boolean aDefault;
	private boolean encryption;
	private String description;
	private String encryptionType;
	private String modulationType;
	private String rSErrorCorrection;

	public int getId()
	{
		return id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public int getContactId()
	{
		return contactId;
	}

	public void setContactId(int contactId)
	{
		this.contactId = contactId;
	}

	public boolean isAfc()
	{
		return afc;
	}

	public void setAfc(boolean afc)
	{
		this.afc = afc;
	}

	public boolean isActive()
	{
		return active;
	}

	public void setActive(boolean active)
	{
		this.active = active;
	}

	public boolean istXRsID()
	{
		return tXRsID;
	}

	public void settXRsID(boolean tXRsID)
	{
		this.tXRsID = tXRsID;
	}

	public boolean isrXRsID()
	{
		return rXRsID;
	}

	public void setrXRsID(boolean rXRsID)
	{
		this.rXRsID = rXRsID;
	}

	public boolean isDefault()
	{
		return aDefault;
	}

	public void setDefault(boolean aDefault)
	{
		this.aDefault = aDefault;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public int getSquelch()
	{
		return squelch;
	}

	public void setSquelch(int squelch)
	{
		this.squelch = squelch;
	}

	public int getFrequency()
	{
		return frequency;
	}

	public void setFrequency(int frequency)
	{
		this.frequency = frequency;
	}

	public int getAudioGain()
	{
		return audioGain;
	}

	public void setAudioGain(int audioGain)
	{
		this.audioGain = audioGain;
	}

	public boolean isEncryption()
	{
		return encryption;
	}

	public void setEncryption(boolean encryption)
	{
		this.encryption = encryption;
	}

	public String getDescription()
	{
		return description;
	}

	public void setDescription(String description)
	{
		this.description = description;
	}

	public String getEncryptionType()
	{
		return encryptionType;
	}

	public void setEncryptionType(String encryptionType)
	{
		this.encryptionType = encryptionType;
	}

	public String getModulationType()
	{
		return modulationType;
	}

	public void setModulationType(String modulationType)
	{
		this.modulationType = modulationType;
	}

	public String getrSErrorCorrection()
	{
		return rSErrorCorrection;
	}

	public void setrSErrorCorrection(String rSErrorCorrection)
	{
		this.rSErrorCorrection = rSErrorCorrection;
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
		dest.writeInt(this.contactId);
		dest.writeByte(this.afc ? (byte) 1 : (byte) 0);
		dest.writeByte(this.active ? (byte) 1 : (byte) 0);
		dest.writeByte(this.tXRsID ? (byte) 1 : (byte) 0);
		dest.writeByte(this.rXRsID ? (byte) 1 : (byte) 0);
		dest.writeByte(this.aDefault ? (byte) 1 : (byte) 0);
		dest.writeString(this.name);
		dest.writeInt(this.squelch);
		dest.writeInt(this.frequency);
		dest.writeInt(this.audioGain);
		dest.writeByte(this.encryption ? (byte) 1 : (byte) 0);
		dest.writeString(this.description);
		dest.writeString(this.encryptionType);
		dest.writeString(this.modulationType);
		dest.writeString(this.rSErrorCorrection);
	}

	public Configuration()
	{
	}

	protected Configuration(Parcel in)
	{
		this.id = in.readInt();
		this.contactId = in.readInt();
		this.afc = in.readByte() != 0;
		this.active = in.readByte() != 0;
		this.tXRsID = in.readByte() != 0;
		this.rXRsID = in.readByte() != 0;
		this.aDefault = in.readByte() != 0;
		this.name = in.readString();
		this.squelch = in.readInt();
		this.frequency = in.readInt();
		this.audioGain = in.readInt();
		this.encryption = in.readByte() != 0;
		this.description = in.readString();
		this.encryptionType = in.readString();
		this.modulationType = in.readString();
		this.rSErrorCorrection = in.readString();
	}

	public static final Creator<Configuration> CREATOR = new Creator<Configuration>()
	{
		@Override
		public Configuration createFromParcel(Parcel source)
		{
			return new Configuration(source);
		}

		@Override
		public Configuration[] newArray(int size)
		{
			return new Configuration[size];
		}
	};
}
