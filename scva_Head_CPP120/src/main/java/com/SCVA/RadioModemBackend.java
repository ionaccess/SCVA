package com.SCVA;

import com.SCVA.Interfaces.CallBack;
import com.SCVA.Interfaces.ModemBackend;
import com.SCVA.Interfaces.ProcessorCallBack;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;

/** Constructed only by Processor for an explicitly resolved Radio selection. */
final class RadioModemBackend implements ModemBackend
{
	private final ProcessorCallBack owner;
	private boolean initialized, started;
	private volatile boolean stopping, stopped;
	private static RadioModemBackend radioOwner;
	static synchronized boolean ownerAvailable() { return radioOwner == null; }
	RadioModemBackend(ProcessorCallBack owner) { this.owner = owner; }

	static int resolveMode(String canonical)
	{
		if (canonical == null || canonical.length() == 0) return -1;
		boolean haveNames = false;
		for (String name : Modem.modemCapListString) if (name != null && name.length() > 0) { haveNames = true; break; }
		if (!haveNames) Modem.updateModemCapabilityList(); // Metadata only, no engine/audio startup.
		for (int i = 0; i < Modem.modemCapListString.length && i < Modem.modemCapListInt.length; ++i)
			if (canonical.equals(Modem.modemCapListString[i])) return Modem.modemCapListInt[i];
		return -1;
	}
	public State state()
	{
		if (stopped) return State.STOPPED;
		if (stopping) return State.STOP_PENDING;
		if (!initialized) return State.IDLE;
		if (!Modem.hasReceiveWorker()) return started ? State.FAILED : State.SELECTED;
		if (Modem.modemState == Modem.RXMODEMRUNNING)
			return Modem.isRadioInitialized() ? State.RECEIVING : State.FAILED;
		if (Modem.modemState == Modem.RXMODEMPAUSED) return Modem.isRadioInitialized() ? State.PAUSED : State.FAILED;
		return State.STARTING;
	}
	public boolean busy() { return Modem.hasTransmitWorker() || GS.gI().isTXActive(); }
	public void apply(BackendSelection selection, boolean image)
	{
		if (stopping) throw new IllegalStateException("Radio stop is pending");
		if (busy() && !(Modem.isTransmitWorkerThread() && !GS.gI().isTXActive())) throw new IllegalStateException("Transmitter busy");
		if (!initialized)
		{
			if (!Modem.canStartReceiveWorker() || Modem.hasTransmitWorker()) throw new IllegalStateException("Previous Radio worker is still stopping");
			synchronized (RadioModemBackend.class)
			{
				if (radioOwner != null && radioOwner != this) throw new IllegalStateException("Previous Radio owner has not completed shutdown");
				radioOwner = this;
				Modem.beginRadioLifetime();
			}
			Message.saveEnv();
			Modem.ModemInit();
			Modem.setProcessorCallBack(owner);
			Constants.imageTxModemIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK64"));
			Modem.minImageModeIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK16"));
			Modem.maxImageModeIndex = Modem.getModeIndexFullList(Modem.getMode("MFSK128"));
			Modem.reset();
			GS.gI().setMonitor(""); GS.gI().setTXmonitor(""); GS.gI().setTermWindow("");
			initialized = true;
		}
		GS.gI().applyConfig(selection.configuration(), image);
		// Apply this configuration's threshold without resetting live frequency.
		Modem.reloadSquelch();
		Processor.TxModem = GS.gI().getRxModem();
	}
	public void receive()
	{
		if (stopping || started && !Modem.hasReceiveWorker()) throw new IllegalStateException("Radio must finish stopping before restart");
		if (!Modem.hasReceiveWorker()) { Modem.startModem(); started = true; }
	}
	public void pause() { Modem.pauseRxModem(); }
	public void resume() { if (stopping) throw new IllegalStateException("Radio is stopping"); if (!Modem.hasReceiveWorker()) receive(); else Modem.unPauseRxModem(); }
	public State stop()
	{
		if (stopping) return state();
		stopping = true;
		synchronized (RadioModemBackend.class)
		{
			if (radioOwner == this) Modem.requestRadioStop();
			else stopped = true; // Never initialized; cannot stop another Service's owner.
		}
		return state();
	}
	public synchronized State awaitStop(long timeoutMillis) throws InterruptedException
	{
		if (stopped) return State.STOPPED;
		if (!stopping) throw new IllegalStateException("Stop was not requested");
		if (!Modem.awaitRadioWorkers(timeoutMillis)) return State.STOP_PENDING;
		// No Processor/owner monitor is held across joins, CAT I/O or disposal.
		// This instance's cleanup-only monitor serializes duplicate await calls.
		try { com.SCVA.Utils.TruSDXCat.getInstance().pttOff(); }
		catch (RuntimeException ignored) { /* Callback/CAT failure cannot skip cleanup. */ }
		Modem.finishRadioStop();
		Processor.TxModem = -1;
		synchronized (RadioModemBackend.class)
		{
			radioOwner = null; // Publish availability only after disposal.
			stopped = true;
		}
		return state();
	}
	public void cancel() { if (busy()) Modem.stopTX = true; }
	public void text(String prepared) { Modem.txData("", "", prepared, 0, 0, false, ""); }
	public void image(String prepared) { Modem.txData("", "", prepared, 0, 0, false, ""); }
	public void key(String prepared) { Modem.txData("", "", prepared, 0, 0, false, ""); }
	public void binary(byte[] data) { Modem.sendV2Bytes(data); }
	public void diagnostic(int sequence) { Modem.sendV2Diagnostic(sequence); }
	public void readableDiagnostic(int sequence) { Modem.sendReadableRadioTest(sequence); }
	public void tune() { Modem.TxTune(); }
	public void frequency(double value, String savedValue)
	{
		GS.gI().getEditor().putString("AFREQUENCY", savedValue).apply();
		Modem.setFrequency(value); Modem.pauseRxModem(); Modem.unPauseRxModem();
	}
	public void rsid(boolean receive, boolean enabled)
	{
		GS.gI().getEditor().putBoolean(receive ? "RXRSID" : "TXRSID", enabled);
		if (receive) Modem.rxRsidOn = enabled; else Modem.txRsidOn = enabled;
	}
	public void listener(CallBack callback) { Modem.setListener(callback); }
}
