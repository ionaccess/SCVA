package com.SCVA.db;

import java.util.ArrayList;

import com.SCVA.SCVA;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.Utils;
import com.SCVA.models.Configuration;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

public class ConfigurationsDataSource extends SqliteHelper
{
	public String TABLE_NAME = "tblConfigs";

	public String ID = "id";

	public String NAME = "name";

	public String IS_ACTIVE = "isActive";

	public String IS_DEFAULT = "isDefault";

	public String DESCRIPTION = "description";

	public String CONTACT_ID = "contactId";

	public String AFC = "afc";

	public String SQUELCH = "squelch";

	public String FREQUENCY = "frequency";

	public String AUDIO_GAIN = "audioGain";

	public String ENCRYPTION = "encryption";

	public String ENCRYPTION_TYPE = "encryptionType";

	private String MODULATION_TYPE = "modulationType";

	public String RS_ERROR_CORRECTION = "rSErrorCorrection";
	public String V2_FEC_ENABLED = "v2FecEnabled";
	public String GGWAVE_SPEED = "ggWaveSpeed";
	public String GGWAVE_RX_PROTOCOLS = "ggWaveRxProtocols";
	public String MODEM73_CALLSIGN = "modem73Callsign";
	public String MODEM73_PROFILE = "modem73Profile";
	public String MODEM73_VOX_ENABLED = "modem73VoxEnabled";
	public String MODEM73_VOX_LEAD_MS = "modem73VoxLeadMs";

	public String RXRSID = "RXRSID";

	public String TXRSID = "TXRSID";

	private Context context;

	public ConfigurationsDataSource(Context context)
	{
		super(context);
		this.context = context;
	}

	public long create(Configuration item)
	{
		ContentValues values = new ContentValues();
		values.put(AFC, item.isAfc());
		values.put(NAME, item.getName());
		values.put(RXRSID, item.isrXRsID());
		values.put(TXRSID, item.istXRsID());
		values.put(SQUELCH, item.getSquelch());
		values.put(IS_ACTIVE, item.isActive());
		values.put(IS_DEFAULT, item.isDefault());
		values.put(FREQUENCY, item.getFrequency());
		values.put(AUDIO_GAIN, item.getAudioGain());
		values.put(ENCRYPTION, item.isEncryption());
		values.put(CONTACT_ID, item.getContactId());
		values.put(DESCRIPTION, item.getDescription());
		values.put(MODULATION_TYPE, item.getModulationType());
		values.put(ENCRYPTION_TYPE, item.getEncryptionType());
		values.put(RS_ERROR_CORRECTION, item.getrSErrorCorrection());
		values.put(V2_FEC_ENABLED, item.isV2FecEnabled());
		values.put(GGWAVE_SPEED, item.getGgWaveSpeed());
		values.put(GGWAVE_RX_PROTOCOLS, item.getGgWaveRxProtocols());
		values.put(MODEM73_CALLSIGN, item.getModem73Callsign());
		values.put(MODEM73_PROFILE, item.getModem73Profile());
		values.put(MODEM73_VOX_ENABLED, item.isModem73VoxEnabled());
		values.put(MODEM73_VOX_LEAD_MS, item.getModem73VoxLeadMs());
		return create(TABLE_NAME, values);
	}

	public boolean update(Configuration item)
	{
		ContentValues values = new ContentValues();
		values.put(AFC, item.isAfc());
		values.put(NAME, item.getName());
		values.put(RXRSID, item.isrXRsID());
		values.put(TXRSID, item.istXRsID());
		values.put(SQUELCH, item.getSquelch());
		values.put(IS_ACTIVE, item.isActive());
		values.put(IS_DEFAULT, item.isDefault());
		values.put(FREQUENCY, item.getFrequency());
		values.put(AUDIO_GAIN, item.getAudioGain());
		values.put(ENCRYPTION, item.isEncryption());
		values.put(CONTACT_ID, item.getContactId());
		values.put(DESCRIPTION, item.getDescription());
		values.put(MODULATION_TYPE, item.getModulationType());
		values.put(ENCRYPTION_TYPE, item.getEncryptionType());
		values.put(RS_ERROR_CORRECTION, item.getrSErrorCorrection());
		values.put(V2_FEC_ENABLED, item.isV2FecEnabled());
		values.put(GGWAVE_SPEED, item.getGgWaveSpeed());
		values.put(GGWAVE_RX_PROTOCOLS, item.getGgWaveRxProtocols());
		values.put(MODEM73_CALLSIGN, item.getModem73Callsign());
		values.put(MODEM73_PROFILE, item.getModem73Profile());
		values.put(MODEM73_VOX_ENABLED, item.isModem73VoxEnabled());
		values.put(MODEM73_VOX_LEAD_MS, item.getModem73VoxLeadMs());

		String whereClause = "id = " + item.getId();
		return update(TABLE_NAME, values, whereClause);
	}

	public boolean delete(Configuration item)
	{
		String whereClause = "rowid = " + item.getId();
		return delete(TABLE_NAME, whereClause);
	}

	public ArrayList<Configuration> getAllConfigurations()
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + "";
		return getData(sql, false);
	}

	public Configuration getConfiguration(int id)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE id = " + id;
		ArrayList<Configuration> list = getData(sql, false);
		if (list.size() > 0) return list.get(0);
		return null;
	}

	public ArrayList<Configuration> getContactConfigurations(int contactId)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE contactId = " + contactId;
		return getData(sql, false);
	}

	@SuppressLint("Range")
	public ArrayList<Configuration> getData(String sql, boolean isSearch)
	{
		ArrayList<Configuration> list = new ArrayList<Configuration>();
		try
		{
			Utils.println(sql);
			openDataBase();
			Cursor cursor = getConnection().rawQuery(sql, null);
			while (cursor.moveToNext())
			{
				Configuration data = new Configuration();

				data.setId(cursor.getInt(cursor.getColumnIndex(ID)));
				data.setName(cursor.getString(cursor.getColumnIndex(NAME)));
				data.setActive(cursor.getInt(cursor.getColumnIndex(IS_ACTIVE)) == 1);
				data.setDefault(cursor.getInt(cursor.getColumnIndex(IS_DEFAULT)) == 1);
				data.setContactId(cursor.getInt(cursor.getColumnIndex(CONTACT_ID)));
				data.setDescription(cursor.getString(cursor.getColumnIndex(DESCRIPTION)));

				data.setrXRsID(cursor.getInt(cursor.getColumnIndex(RXRSID)) == 1);
				data.settXRsID(cursor.getInt(cursor.getColumnIndex(TXRSID)) == 1);

				data.setAfc(cursor.getInt(cursor.getColumnIndex(AFC)) == 1);
				data.setSquelch(cursor.getInt(cursor.getColumnIndex(SQUELCH)));
				data.setFrequency(cursor.getInt(cursor.getColumnIndex(FREQUENCY)));
				data.setAudioGain(cursor.getInt(cursor.getColumnIndex(AUDIO_GAIN)));
				data.setEncryption(cursor.getInt(cursor.getColumnIndex(ENCRYPTION)) == 1);
				data.setEncryptionType(cursor.getString(cursor.getColumnIndex(ENCRYPTION_TYPE)));
				data.setModulationType(cursor.getString(cursor.getColumnIndex(MODULATION_TYPE)));
				data.setrSErrorCorrection(cursor.getString(cursor.getColumnIndex(RS_ERROR_CORRECTION)));
				int v2FecColumn = cursor.getColumnIndex(V2_FEC_ENABLED);
				data.setV2FecEnabled(v2FecColumn >= 0 ? cursor.getInt(v2FecColumn) == 1 : true);
				int speedColumn = cursor.getColumnIndex(GGWAVE_SPEED);
				data.setGgWaveSpeed(speedColumn >= 0 ? cursor.getString(speedColumn) : "DT_FASTEST");
				int rxProtocolsColumn = cursor.getColumnIndex(GGWAVE_RX_PROTOCOLS);
				data.setGgWaveRxProtocols(rxProtocolsColumn >= 0 ? cursor.getString(rxProtocolsColumn) : null);
				int callsignColumn = cursor.getColumnIndex(MODEM73_CALLSIGN);
				data.setModem73Callsign(callsignColumn >= 0 ? cursor.getString(callsignColumn) : "");
				int profileColumn = cursor.getColumnIndex(MODEM73_PROFILE);
				data.setModem73Profile(profileColumn >= 0 ? cursor.getString(profileColumn) : "OFDM_QPSK_1_2_NORMAL");
				int voxEnabledColumn = cursor.getColumnIndex(MODEM73_VOX_ENABLED);
				data.setModem73VoxEnabled(voxEnabledColumn >= 0 && cursor.getInt(voxEnabledColumn) == 1);
				int voxLeadColumn = cursor.getColumnIndex(MODEM73_VOX_LEAD_MS);
				data.setModem73VoxLeadMs(voxLeadColumn >= 0 ? cursor.getInt(voxLeadColumn) : 550);

				list.add(data);
			}
			cursor.close();
		}
		catch (Exception e)
		{
			close();
			if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().startsWith("Configuration schema migration failed")) throw (IllegalStateException) e;
			e.printStackTrace();
			Utils.println("Exception: " + e);
		}
		close();
		return list;
	}

	public Configuration getConfigurationAgainstId(int configId)
	{
		String sql = "SELECT  rowid, * FROM " + TABLE_NAME + " WHERE id = " + configId;
		ArrayList<Configuration> list = getData(sql, false);
		if (list.size() > 0) return list.get(0);
		return null;
	}

	public void restoreSettingsToDefault()
	{
		SharedPreferences editor = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE);
		// prefs.edit().putLong("last_edit_time", System.currentTimeMillis()).apply();

		// Restore RX and TX RSID in case they were disabled by error
		editor.edit().putBoolean("RXRSID", true).apply();
		editor.edit().putBoolean("TXRSID", true).apply();

		// General and GUI
		editor.edit().putBoolean("USEMODELIST", false).apply();
		editor.edit().putString("BUTTONTEXTSIZE", "12").apply();
		// Modem - General
		editor.edit().putString("VOLUME", "10").apply();
		editor.edit().putString("AFREQUENCY", "1500").apply();
		editor.edit().putBoolean("SLOWCPU", false).apply();
		// RSID
		editor.edit().putBoolean("TXPOSTRSID", false).apply();
		editor.edit().putBoolean("RSIDWIDESEARCH", true).apply();
		editor.edit().putString("RSID_ERRORS", "2").apply();
		// 8PSK
		editor.edit().putBoolean("8PSKPILOT", true).apply();
		editor.edit().putString("8PSKPILOTPOWER", "-30").apply();
		// DominoEx
		editor.edit().putBoolean("DOMINOEXFILTER", true).apply();
		editor.edit().putString("DOMINOEXBW", "2.0").apply();
		editor.edit().putBoolean("DOMINOEXFEC", false).apply();
		editor.edit().putString("DOMCWI", "0.0").apply();
		// Thor
		editor.edit().putString("THORCWI", "0.0").apply();
		editor.edit().putBoolean("THORFILTER", true).apply();
		editor.edit().putString("THORBW", "2.0").apply();
		editor.edit().putBoolean("THORPREAMBLE", true).apply();
		editor.edit().putBoolean("THORSOFTSYMBOLS", true).apply();
		editor.edit().putBoolean("THORSOFTBITS", true).apply();
		// Olivia
		editor.edit().putString("OLIVIATONES", "2").apply();
		editor.edit().putString("OLIVIABW", "2").apply();
		editor.edit().putString("OLIVIASMARGIN", "8").apply();
		editor.edit().putString("OLIVIASINTEG", "4").apply();
		editor.edit().putBoolean("OLIVIARESETFEC", false).apply();
		editor.edit().putBoolean("OLIVIA8BIT", true).apply();
		// MT63
		editor.edit().putBoolean("MT638BIT", true).apply();
		editor.edit().putBoolean("MT63INTEGRATION", true).apply();
		editor.edit().putBoolean("MT63USETONES", true).apply();
		editor.edit().putBoolean("MT63TWOTONES", true).apply();
		editor.edit().putString("MT63TONEDURATION", "4").apply();
		editor.edit().putBoolean("MT63AT500", false).apply();
		// Image Attachments
		editor.edit().putString("TARGETMAXMEGAPIXELS", "0.5").apply();
		editor.edit().putString("JPEGQUALITY", "70").apply();
		// Data exchange
		editor.edit().putBoolean("USECOMPRESSION", true).apply();
		editor.edit().putString("COMPRESSIONENCODER", "1").apply();
		editor.edit().putBoolean("FORCECOMPRESSION", false).apply();
		editor.edit().putString("EXTRACTTIMEOUT", "4").apply();
		// Personnal data - No Change
		// Date-Time format - No change
		// File name format - No change
		// Radiogram
		// editor.edit().putString("RGWORDSPERLINE", "5").apply();
		// editor.edit().putBoolean("SHOWARLDESC",true).apply();
		// GPS Time
		editor.edit().putBoolean("USEGPSTIME", false).apply();
		editor.edit().putString("LEAPSECONDS", "0").apply();
	}

	public void burnConfigurations()
	{
		execSQL("DELETE FROM " + TABLE_NAME);
	}
}
