package com.SCVA.Utils;

/**
 * Created by §∞§ on 21/06/2026.
 */

import java.util.List;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

public class TruSDXCat
{
	private static final String TAG = "TruSDXCat";
	private static TruSDXCat instance;
	private UsbSerialPort serialPort;

	private TruSDXCat()
	{
	}

	public static synchronized TruSDXCat getInstance()
	{
		if (instance == null)
		{
			instance = new TruSDXCat();
		}

		return instance;
	}

	public synchronized void setSerialPort(UsbSerialPort port)
	{
		this.serialPort = port;
	}

	public synchronized void disconnect()
	{
		try
		{
			if (serialPort != null)
			{
				serialPort.close();
			}

		}
		catch (Exception e)
		{
			Utils.println(TAG, "Disconnect failed", e);

		}
		finally
		{
			serialPort = null;
		}
	}

	public synchronized boolean isConnected()
	{
		return serialPort != null;
	}

	public synchronized UsbSerialPort getSerialPort()
	{
		return serialPort;
	}

	public synchronized boolean send(String command)
	{
		if (serialPort == null)
		{
			Utils.println(TAG, "Not connected");
			return false;
		}

		try
		{
			Utils.println(TAG, "TX: " + command);
			serialPort.write(command.getBytes(), 1000);
			return true;
		}
		catch (Exception e)
		{
			Utils.println(TAG, "Send failed", e);

			return false;
		}
	}

	public synchronized String query(String command)
	{
		if (serialPort == null)
		{
			return null;
		}
		try
		{
			Utils.println(TAG, "TX: " + command);
			serialPort.write(command.getBytes(), 1000);

			Thread.sleep(300);

			byte[] buffer = new byte[512];
			int len = serialPort.read(buffer, 1000);
			if (len > 0)
			{
				String response = new String(buffer, 0, len).trim();
				Utils.println(TAG, "RX: " + response);
				return response;
			}

		}
		catch (Exception e)
		{

			Utils.println(TAG, "Query failed", e);
		}

		return null;
	}

	// ----------------------
	// PTT
	// ----------------------

	public void pttOn()
	{
		send("TX0;");
	}

	public void pttOff()
	{
		send("RX;");
	}

	// ----------------------
	// Frequency
	// ----------------------

	public void setFrequency(long frequencyHz)
	{
		String cmd = String.format("FA%09d;", frequencyHz);
		send(cmd);
	}

	public String getFrequencyRaw()
	{
		return query("FA;");
	}

	public long getFrequencyHz()
	{
		try
		{
			String response = getFrequencyRaw();
			if (response == null)
			{
				return -1;
			}
			response = response.replace("FA", "").replace(";", "").trim();
			return Long.parseLong(response);
		}
		catch (Exception e)
		{

			Utils.println(TAG, "Frequency parse failed", e);

			return -1;
		}
	}

	// ----------------------
	// Radio Info
	// ----------------------

	public String getRadioId()
	{
		return query("ID;");
	}

	// ----------------------
	// Generic CAT
	// ----------------------

	public synchronized String execute(String command)
	{
		return query(command);
	}

	public synchronized boolean executeNoReply(String command)
	{
		return send(command);
	}

	public static boolean connect(Context context)
	{
		try
		{
			UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
			List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

			if (drivers.isEmpty())
			{
				Utils.println(TAG, "No USB serial device found");
				return false;
			}

			UsbSerialDriver driver = drivers.get(0);

			if (!usbManager.hasPermission(driver.getDevice()))
			{

				Utils.println(TAG, "USB permission missing");
				return false;
			}
			UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());

			if (connection == null)
			{
				return false;
			}

			UsbSerialPort port = driver.getPorts().get(0);

			port.open(connection);
			port.setParameters(Constants.BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

			port.setDTR(true);
			port.setRTS(true);

			getInstance().setSerialPort(port);
			Utils.println(TAG, "Connected");
			return true;
		}
		catch (Exception e)
		{
			Utils.println(TAG, "Connect failed", e);
			return false;
		}
	}

	public static boolean ensureConnected(Context context)
	{
		if (getInstance().isConnected())
		{
			return true;
		}
		return connect(context);
	}
}