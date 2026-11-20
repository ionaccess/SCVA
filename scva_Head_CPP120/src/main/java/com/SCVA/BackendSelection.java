package com.SCVA;

import java.util.Arrays;

import com.SCVA.models.Configuration;

/** Immutable application selection; contains no Activity, engine or encryption object. */
public final class BackendSelection
{
	public final int contactId;
	private final int id, frequency, gain, squelch;
	private final boolean afc, txRsid, rxRsid, encryption, v2FecEnabled, modem73VoxEnabled;
	private final int modem73VoxLeadMs;
	private final String mode, preset, algorithm, ggWaveSpeed, ggWaveRxProtocols, modem73Callsign, modem73Profile, modem73KeyContext;

    public BackendSelection(int contactId, Configuration config){this(contactId,config,"");}
	public BackendSelection(int contactId, Configuration config,String localKeyContext)
	{
		if (contactId <= 0 || config == null || config.getId() <= 0) throw new IllegalArgumentException("Select a contact configuration first");
		this.contactId = contactId;
		id = config.getId();
		frequency = config.getFrequency();
		gain = config.getAudioGain();
		squelch = config.getSquelch();
		afc = config.isAfc();
		txRsid = config.istXRsID();
		rxRsid = config.isrXRsID();
		encryption = config.isEncryption();
		v2FecEnabled = config.isV2FecEnabled();
		mode = config.getModulationType();
		preset = config.getrSErrorCorrection();
		algorithm = config.getEncryptionType();
		ggWaveSpeed = config.getGgWaveSpeed();
		String configuredRxProtocols = config.getGgWaveRxProtocols();
		ggWaveRxProtocols = configuredRxProtocols == null ? ggWaveSpeed : configuredRxProtocols;
		modem73Callsign = config.getModem73Callsign();
		modem73Profile = config.getModem73Profile();
		modem73VoxEnabled = config.isModem73VoxEnabled();
		modem73VoxLeadMs = config.getModem73VoxLeadMs();
        modem73KeyContext = "MODEM73".equals(mode)&&encryption?(localKeyContext==null?"missing":localKeyContext):"";
		if ("GGWave".equals(mode) && !isKnownGgWaveSpeed(ggWaveSpeed)) throw new IllegalArgumentException("Select a valid GGWave protocol");
		if ("GGWave".equals(mode) && !areKnownGgWaveRxProtocols(ggWaveRxProtocols)) throw new IllegalArgumentException("Select at least one valid GGWave receive protocol");
		if ("MODEM73".equals(mode) && !Modem73Backend.isSupportedProfile(modem73Profile)) throw new IllegalArgumentException("Unsupported MODEM73 profile");
		if ("MODEM73".equals(mode) && (modem73VoxLeadMs < 100 || modem73VoxLeadMs > 3000)) throw new IllegalArgumentException("MODEM73 VOX lead must be 100–3000 ms");
	}

	public String modeName()
	{
		return mode;
	}

	public boolean sameAs(BackendSelection other)
	{
		return other != null && Arrays.equals(values(), other.values());
	}

	private Object[] values()
	{
		return new Object[]
		{ contactId, id, frequency, gain, squelch, afc, txRsid, rxRsid, encryption, v2FecEnabled, mode, preset, algorithm, ggWaveSpeed, ggWaveRxProtocols, modem73Callsign, modem73Profile, modem73VoxEnabled, modem73VoxLeadMs, modem73KeyContext };
	}

	public Configuration configuration()
	{
		Configuration c = new Configuration();
		c.setId(id);
		c.setContactId(contactId);
		c.setFrequency(frequency);
		c.setAudioGain(gain);
		c.setSquelch(squelch);
		c.setAfc(afc);
		c.settXRsID(txRsid);
		c.setrXRsID(rxRsid);
		c.setEncryption(encryption);
		c.setV2FecEnabled(v2FecEnabled);
		c.setModulationType(mode);
		c.setrSErrorCorrection(preset);
		c.setEncryptionType(algorithm);
		c.setGgWaveSpeed(ggWaveSpeed);
		c.setGgWaveRxProtocols(ggWaveRxProtocols);
		c.setModem73Callsign(modem73Callsign);
		c.setModem73Profile(modem73Profile);
		c.setModem73VoxEnabled(modem73VoxEnabled);
		c.setModem73VoxLeadMs(modem73VoxLeadMs);
		return c;
	}

	public String ggWaveSpeed()
	{
		return ggWaveSpeed;
	}

	public String ggWaveRxProtocols()
	{
		return ggWaveRxProtocols;
	}
	public String modem73Callsign() { return modem73Callsign; }
	public String modem73Profile() { return modem73Profile; }
	public boolean modem73VoxEnabled() { return modem73VoxEnabled; }
	public int modem73VoxLeadMs() { return modem73VoxLeadMs; }
    public boolean hasModem73KeyContext(){return !modem73KeyContext.isEmpty()&&!modem73KeyContext.equals("missing");}
    public boolean matchesModem73KeyContext(String context){return modem73KeyContext.equals(context);}

	public static boolean areKnownGgWaveRxProtocols(String protocols)
	{
		if (protocols == null || protocols.length() == 0) return false;
		String[] tokens = protocols.split(",", -1);
		java.util.HashSet<String> seen = new java.util.HashSet<>();
		for (String token : tokens) if (!isKnownGgWaveSpeed(token) || !seen.add(token)) return false;
		return true;
	}

	public static boolean isKnownGgWaveSpeed(String speed)
	{
		return "AUDIBLE_NORMAL".equals(speed) || "AUDIBLE_FAST".equals(speed) || "AUDIBLE_FASTEST".equals(speed)
				|| "ULTRASOUND_NORMAL".equals(speed) || "ULTRASOUND_FAST".equals(speed) || "ULTRASOUND_FASTEST".equals(speed)
				|| "DT_NORMAL".equals(speed) || "DT_FAST".equals(speed) || "DT_FASTEST".equals(speed);
	}
}
