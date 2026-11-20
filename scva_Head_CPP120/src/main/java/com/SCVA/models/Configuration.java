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
	private boolean v2FecEnabled = true;
	private String ggWaveSpeed = "DT_FASTEST";
	private String ggWaveRxProtocols;
	public static final String DEFAULT_MODEM73_CALLSIGN = "SCVA";
	public static final boolean DEFAULT_MODEM73_ENCRYPTION = true;
	public static final String MODEM73_PROFILE_DEFAULT = "OFDM_QPSK_1_2_NORMAL";
	public static final String MODEM73_PROFILE_ROBUST = "OFDM_QPSK_1_4_LONG";
	public static final String MODEM73_PROFILE_REPEATED = "OFDM_QPSK_1_4X2_LONG";
	public static final String MODEM73_PROFILE_QPSK_14_NORMAL = "OFDM_QPSK_1_4_NORMAL";
	public static final String MODEM73_PROFILE_BPSK_14_NORMAL = "OFDM_BPSK_1_4_NORMAL";
	public static final String MODEM73_PROFILE_RDM_600 = "ROBUST_RDM_600";
	public static final String MODEM73_PROFILE_RDMN_300 = "ROBUST_RDMN_300";
	private String modem73Callsign = DEFAULT_MODEM73_CALLSIGN;
	private String modem73Profile = MODEM73_PROFILE_DEFAULT;
	private boolean modem73VoxEnabled;
	private int modem73VoxLeadMs = 550;

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

	public boolean isV2FecEnabled()
	{
		return v2FecEnabled;
	}

	public void setV2FecEnabled(boolean v2FecEnabled)
	{
		this.v2FecEnabled = v2FecEnabled;
	}

	public String getGgWaveSpeed()
	{
		return ggWaveSpeed == null ? "DT_FASTEST" : ggWaveSpeed;
	}

	public void setGgWaveSpeed(String ggWaveSpeed)
	{
		this.ggWaveSpeed = ggWaveSpeed == null || ggWaveSpeed.length() == 0 ? "DT_FASTEST" : ggWaveSpeed;
	}

	public String getGgWaveRxProtocols()
	{
		return ggWaveRxProtocols;
	}

	public void setGgWaveRxProtocols(String ggWaveRxProtocols)
	{
		this.ggWaveRxProtocols = ggWaveRxProtocols == null || ggWaveRxProtocols.trim().length() == 0 ? null : ggWaveRxProtocols;
	}

	public String getModem73Callsign() { return modem73Callsign == null || modem73Callsign.trim().isEmpty() ? DEFAULT_MODEM73_CALLSIGN : modem73Callsign; }
	public void setModem73Callsign(String value) { modem73Callsign = value == null || value.trim().isEmpty() ? DEFAULT_MODEM73_CALLSIGN : value; }
	public String getModem73Profile() { return modem73Profile == null ? MODEM73_PROFILE_DEFAULT : modem73Profile; }
	public void setModem73Profile(String value) { modem73Profile = value == null || value.length() == 0 ? MODEM73_PROFILE_DEFAULT : value; }
	public boolean isModem73VoxEnabled() { return modem73VoxEnabled; }
	public void setModem73VoxEnabled(boolean value) { modem73VoxEnabled = value; }
	public int getModem73VoxLeadMs() { return modem73VoxLeadMs; }
	public void setModem73VoxLeadMs(int value) { modem73VoxLeadMs = value; }

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
		dest.writeByte(this.v2FecEnabled ? (byte) 1 : (byte) 0);
		dest.writeString(getGgWaveSpeed());
		dest.writeString(this.ggWaveRxProtocols);
		dest.writeString(getModem73Callsign());
		dest.writeString(getModem73Profile());
		dest.writeByte(this.modem73VoxEnabled ? (byte) 1 : (byte) 0);
		dest.writeInt(getModem73VoxLeadMs());
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
		this.v2FecEnabled = in.readByte() != 0;
		this.ggWaveSpeed = in.readString();
		if (this.ggWaveSpeed == null) this.ggWaveSpeed = "DT_FASTEST";
		this.ggWaveRxProtocols = in.readString();
		this.modem73Callsign = in.readString();
		this.modem73Profile = in.readString();
		this.modem73VoxEnabled = in.readByte() != 0;
		this.modem73VoxLeadMs = in.readInt();
		if (this.modem73Callsign == null || this.modem73Callsign.trim().isEmpty()) this.modem73Callsign = DEFAULT_MODEM73_CALLSIGN;
		if (this.modem73Profile == null) this.modem73Profile = MODEM73_PROFILE_DEFAULT;
		if (this.modem73VoxLeadMs <= 0) this.modem73VoxLeadMs = 550;
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
