package com.SCVA.Interfaces;

import com.SCVA.BackendSelection;

/** Application control; codecs, PCM resources and DSP remain in the implementation. */
public interface ModemBackend
{
	enum State
	{
		IDLE, SELECTED, STARTING, RECEIVING, PAUSED, STOP_PENDING, STOPPED, FAILED
	}
	State state();
	boolean busy();
	void apply(BackendSelection selection, boolean image);
	void receive();
	void pause();
	void resume();
	State stop();
	State awaitStop(long timeoutMillis) throws InterruptedException;
	void cancel();
	void text(String prepared);
	void image(String prepared);
	void key(String prepared);
	void binary(byte[] data);
	void diagnostic(int sequence);
	void readableDiagnostic(int sequence);
	void tune();
	void frequency(double value, String savedValue);
	void rsid(boolean receive, boolean enabled);
	void listener(CallBack callback);
}
