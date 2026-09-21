package com.SCVA.activities;

/**
 * Created by §∞§ on 18/06/2026.
 */
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.SCVA.R;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.TruSDXAudio;
import com.SCVA.Utils.TruSDXCat;
import com.SCVA.Utils.Utils;
import com.SCVA.Utils.WhatsAppHelper;
import com.SCVA.db.SettingsDataSource;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.text.DecimalFormat;
import java.util.List;

public class RigControlActivity extends BaseActivity implements View.OnClickListener, TextWatcher, CompoundButton.OnCheckedChangeListener
{
	private String log = "";
	private TextView txtStatus;
	private EditText frequency, delay;
	private UsbManager usbManager;
	private boolean isPttOn = false;
	private CheckBox enableRig, enableAudioOverUSB;
	private UsbSerialPort serialPort;
	private SettingsDataSource settingsDataSource;
	private Button testConnect, testPtt, setFrequency, testAudio, sendLog, crashApp;
	public static final long MIN_FREQ = 30000L; // 30 kHz
	public static final long MAX_FREQ = 60000000L; // 60 MHz
	private static final String ACTION_USB_PERMISSION = "com.SCVA.trusdx.USB_PERMISSION";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.activity_rig_control);

		settingsDataSource = new SettingsDataSource(this);

		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		registerReceiver(usbReceiver, filter);

		testConnect = findViewById(R.id.testConnect);
		testConnect.setOnClickListener(this);

		testAudio = findViewById(R.id.testAudio);
		testAudio.setOnClickListener(this);

		sendLog = findViewById(R.id.sendLog);
		sendLog.setOnClickListener(this);

		testPtt = findViewById(R.id.testPtt);
		testPtt.setOnClickListener(this);

		setFrequency = findViewById(R.id.setFrequency);
		setFrequency.setOnClickListener(this);

		crashApp = findViewById(R.id.crashApp);
		crashApp.setOnClickListener(this);

		txtStatus = findViewById(R.id.txtStatus);
		frequency = findViewById(R.id.frequency);

		delay = findViewById(R.id.delay);
		delay.setText(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL_DELAY, "1"));
		delay.addTextChangedListener(this);

		frequency = findViewById(R.id.frequency);
		frequency.setText(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_FREQUENCY, "0"));
		frequency.addTextChangedListener(this);

		enableRig = findViewById(R.id.enableRig);
		enableRig.setChecked(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL, "0").equalsIgnoreCase("1"));
		enableRig.setOnCheckedChangeListener(this);

		enableAudioOverUSB = findViewById(R.id.enableAudioOverUSB);
		enableAudioOverUSB.setChecked(settingsDataSource.getValueAgainstKey(Constants.SETTINGS_RIG_AUDIO_OVER_USB, "0").equalsIgnoreCase("1"));
		enableAudioOverUSB.setOnCheckedChangeListener(this);

		usbManager = (UsbManager) getSystemService(USB_SERVICE);
	}

	private void connectRadio()
	{
		try
		{
			List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

			if (drivers.isEmpty())
			{
				txtStatus.setText(R.string.no_usb_serial_device_found);
				return;
			}

			UsbSerialDriver driver = drivers.get(0);

			UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());

			if (connection == null)
			{
				PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
				usbManager.requestPermission(driver.getDevice(), permissionIntent);
				txtStatus.setText(R.string.requesting_usb_permission);
				return;
			}

			serialPort = driver.getPorts().get(0);

			serialPort.open(connection);

			serialPort.setParameters(Constants.BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
			serialPort.setDTR(true);
			serialPort.setRTS(true);

			// pass connection to helper
			TruSDXCat.getInstance().setSerialPort(serialPort);
			// after that do
			// TruSDXCat.getInstance().pttOn();
			// TruSDXCat.getInstance().pttOff();
			//
			// TruSDXCat.getInstance().setFrequency(13560000);
			// String id = TruSDXCat.getInstance().getRigId();
			// make sure connection before call
			// TruSDXCat.ensureConnected(getApplicationContext());
			// TruSDXCat.getInstance().setFrequency(13560000);

			// Enable audio streaming:
			TruSDXAudio.getInstance().enableAudioMode();

			txtStatus.setText(R.string.connected_to_tr_usdx);
			testConnect.setBackgroundResource(R.drawable.round_border_shadow_pin_btn_bg);
			testPtt.setEnabled(true);
			frequency.setText("" + catToMHz(getFrequency()));
		}
		catch (Exception e)
		{
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void startTerminalRead()
	{
		new Thread(() -> {

			byte[] buffer = new byte[1024];

			while (true)
			{
				try
				{
					int len = serialPort.read(buffer, 5000);
					if (len > 0)
					{
						String hex = "";
						for (int i = 0; i < len; i++)
						{
							hex += String.format("%02X ", buffer[i]);
						}
						// terminalTextValue = terminalTextValue + "RX[" + len +
						// "] " + new
						// String(buffer, 0, len) + " HEX=" + hex;
					}
				}
				catch (Exception e)
				{
					break;
				}
			}
			// terminalText.setText(terminalTextValue);

		}).start();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		unregisterReceiver(usbReceiver);

		try
		{
			if (serialPort != null)
			{
				serialPort.close();
			}
		}
		catch (Exception ignored)
		{
		}
	}

	private final BroadcastReceiver usbReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action))
			{
				synchronized (this)
				{
					UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
					{
						if (device != null)
						{
							connectRadio();
						}
					}
					else
					{
						txtStatus.setText(R.string.usb_permission_denied);
					}
				}
			}
		}
	};

	private String sendCATCommand(String command)
	{
		if (serialPort == null)
		{
			return null;
		}

		try
		{
			log = log + "TRUSDX | TX: " + command;

			// send command
			serialPort.write(command.getBytes(), 1000);
			Thread.sleep(500);
			byte[] buffer = new byte[256];
			int total = 0;
			StringBuilder response = new StringBuilder();
			while (true)
			{
				int len = serialPort.read(buffer, 200);
				if (len > 0)
				{
					total += len;
					response.append(new String(buffer, 0, len));
				}
				else
				{
					break;
				}
			}

			log = log + "TRUSDX | RX bytes=" + total + " data=" + response;
			if (total > 0)
			{
				return response.toString();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	private String getFrequency()
	{
		try
		{
			serialPort.write("FA;".getBytes(), 1000);
			Thread.sleep(300);

			byte[] buffer = new byte[128];
			int len = serialPort.read(buffer, 1000);

			if (len > 0)
			{
				String response = new String(buffer, 0, len).trim();
				Utils.println("TRUSDX | FA response: " + response);
				return response;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public double catToMHz(String catResponse)
	{
		long hz = Long.parseLong(catResponse.substring(2, catResponse.length() - 1));
		return hz / 1_000_000.0;
	}

	private long parseFrequency(String response)
	{
		if (response == null) return -1;
		if (!response.startsWith("FA")) return -1;
		try
		{
			String digits = response.replace("FA", "").replace(";", "");
			return Long.parseLong(digits);
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == crashApp)
		{
			((TextView) findViewById(R.id.actions)).setText("Crash");
		}
		if (view == testConnect)
		{
			connectRadio();
		}
		if (view == testAudio)
		{
			TruSDXCat.getInstance().pttOn();
			TruSDXAudio.getInstance().startRx();
		}
		if (view == sendLog)
		{
			WhatsAppHelper.sendTextMessage(this, Constants.logNumber, Utils.readFileFromInternalStorage());
			Utils.clearFileUsingFileObject();
		}
		if (view == testPtt)
		{
			if (isPttOn)
			{
				isPttOn = false;
				sendCATCommand("RX;");
				testPtt.setText(R.string.ptt_on);
				testPtt.setTextColor(Color.WHITE);
				testPtt.setBackgroundResource(R.drawable.round_border_shadow_pin_btn_bg);
			}
			else
			{
				isPttOn = true;
				sendCATCommand("TX0;");
				testPtt.setText(R.string.ptt_off);
				testPtt.setTextColor(Color.WHITE);
				testPtt.setBackgroundResource(R.drawable.round_border_shadow_red_btn_bg);
			}
			enableRig.setEnabled(true);
		}
		if (view == setFrequency)
		{
			double mhz = Double.parseDouble(frequency.getText().toString());
			long hz = Math.round(mhz * 1_000_000);
			if (isValidFrequency(hz))
			{
				if (isValidFrequency(hz))
				{
					String cat = String.format("FA%011d;", hz);
					System.out.println(cat);
					setFrequency(cat);
				}
			}
		}
	}

	public String frequencyToCat(double mhz)
	{
		long hz = Math.round(mhz * 1_000_000);
		return String.format("FA%011d;", hz);
	}

	// For 13.56 MHz, the CAT frequency in Hz is:
	// 13,560,000 Hz
	// The CAT command to send is:
	// FA013560000;
	// setFrequency(13560000);
	private void setFrequency(String frequencyHz)
	{
		try
		{
			serialPort.write(frequencyHz.getBytes(), 1000);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public boolean isValidFrequency(long hz)
	{
		return hz >= MIN_FREQ && hz <= MAX_FREQ;
	}

	public String catToDisplayMHz(String catResponse)
	{
		long hz = Long.parseLong(catResponse.substring(2, catResponse.length() - 1));

		double mhz = hz / 1_000_000.0;

		return new DecimalFormat("0.######").format(mhz);
	}

	@Override
	public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2)
	{

	}

	@Override
	public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
	{

	}

	@Override
	public void afterTextChanged(Editable editable)
	{
		settingsDataSource.updateSettingValue(Constants.SETTINGS_RIG_CONTROL_DELAY, delay.getText().toString());
		settingsDataSource.updateSettingValue(Constants.SETTINGS_RIG_FREQUENCY, frequency.getText().toString());

		if (delay.hasFocus())
		{
			if (delay.getText().toString().length() > 0 && Integer.parseInt(delay.getText().toString()) > 10)
			{
				Utils.hideKeyboard(delay.getContext(), delay);
				runOnUiThread(
						new Utils().showQuestionDialog("Warning:", "It is not suggested to add more than 10 seconds \nAre you sure you want to proceed ?", this, new Runnable()
						{
							@Override
							public void run()
							{

							}
						}, new Runnable()
						{
							@Override
							public void run()
							{
								delay.setText("10");
							}
						}));
			}
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton compoundButton, boolean b)
	{
		if (compoundButton == enableRig)
		{
			settingsDataSource.updateSettingValue(Constants.SETTINGS_RIG_CONTROL, enableRig.isChecked() ? "1" : "0");
		}
		if (compoundButton == enableAudioOverUSB)
		{
			settingsDataSource.updateSettingValue(Constants.SETTINGS_RIG_AUDIO_OVER_USB, enableAudioOverUSB.isChecked() ? "1" : "0");
		}
	}
}

// UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
// String logTxt = "START ";
// // for (UsbDevice device : manager.getDeviceList().values())
// // {
// // logTxt = logTxt + "USB | VID=" + device.getVendorId() + " PID=" +
// // device.getProductId() + " Interfaces=" + device.getInterfaceCount() +
// "\n";
// // }
//
// UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
//
// for (UsbDevice device : manager.getDeviceList().values())
// {
// logTxt = logTxt + "USB | VID=" + device.getVendorId() + " PID=" +
// device.getProductId() + "\n";
//
// for (int i = 0; i < device.getInterfaceCount(); i++)
// {
//
// UsbInterface intf = device.getInterface(i);
//
// logTxt = logTxt + "USB | Interface " + i + " Class=" +
// intf.getInterfaceClass() + " SubClass=" + intf.getInterfaceSubclass() + "
// Endpoints="
// + intf.getEndpointCount() + "\n";
//
// for (int e = 0; e < intf.getEndpointCount(); e++)
// {
//
// UsbEndpoint ep = intf.getEndpoint(e);
//
// logTxt = logTxt + "USB | Endpoint " + e + " Type=" + ep.getType() + "
// Direction=" + ep.getDirection() + " Address=" + ep.getAddress()+"\n END";
// }
// }
// }
//
// WhatsAppHelper.sendTextMessage(this, Constants.logNumber, logTxt);