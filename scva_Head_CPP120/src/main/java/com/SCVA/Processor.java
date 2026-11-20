/*
 * Processor.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA)
 * Based on Pskmail from Per Crusefalk and Rein Couperus
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.SCVA;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.SCVA.Interfaces.CallBack;
import com.SCVA.Interfaces.ModemBackend;
import com.SCVA.Interfaces.ProcessorCallBack;
import com.SCVA.Interfaces.UICallBack;
import com.SCVA.Utils.Constants;
import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * @author John Douyere (VK2ETA)
 */
public class Processor extends Service implements ProcessorCallBack, GgWaveBackend.Listener, Modem73Backend.Events
{
	private volatile UICallBack uiCallBack;
	private final IBinder binder = new LocalBinder();
	static String application = "Secure Comms via Audio 3.0.0";
	static String version = "Version 3.0";
	static boolean onWindows = true;
	static String ModemPreamble = ""; // String to send before any Tx Buffer
	static String ModemPostamble = ""; // String to send after any Tx buffer
	static final String DirLogs = "Logs";
	static final String messageLogFile = "messagelog.txt";
	static boolean compressedMsg = false;
	static int DCD = 0;

	// No Radio identity is selected by service creation.
	public static int TxModem = -1;

	// globals for communication
	static String mycall; // my call sign from options
	public static String TX_Text; // output queue
	// Error handling and logging object
	static loggingClass log;

	public static void processor()
	{
		// Nothing as this is a service
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		log = new loggingClass("SCVA");
		handleInitialization();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		// A started/bound service is not a modem selection.
		showForegroundNotification();
		return START_NOT_STICKY;
	}

	private void showForegroundNotification()
	{
		if (android.os.Build.VERSION.SDK_INT >= 26)
		{
			android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			manager.createNotificationChannel(new android.app.NotificationChannel("com.SCVA", "Background Modem", android.app.NotificationManager.IMPORTANCE_NONE));
		}
		startForeground(1, new androidx.core.app.NotificationCompat.Builder(this, "com.SCVA").setSmallIcon(R.drawable.notificationicon).setContentTitle("SCVA")
				.setContentText(getString(R.string.modem_service_selection)).setOngoing(true).build());
	}

	@Override
	public void onDestroy()
	{
		synchronized (this) { destroyed = true; }
		stopRadio();
		uiCallBack = null;
		GS.gI().setProcessorON(false);
		super.onDestroy();
	}

	// Post to main terminal window
	// public void PostToTerminal(String text)
	// {
	// GS.gI().TermWindow += text;
	// scvaCallBack.post(null, "addToTerminal");
	// }

	// Post to main terminal window
	// public static void PostToModem(String text)
	// {
	// GS.gI().monitor += text;
	// SCVA.mHandler.post(SCVA.addToModem);
	// }

	// Process one block of received data. Called from Modem.RxBlock when a complete
	// block is received
	public void processWrapBlock(String Blockline)
	{
		FileWriter out = null;
		try
		{
			boolean unwrapResult = Message.ProcessWrapBuffer(Blockline);
			if (unwrapResult)
			{
				// Save file and log it
				String resultString = Message.getUnwrapText();
				String resultFilename = Message.getUnwrapFilename();
				GS.gI().setLastReceivedMessageFname(resultFilename);
				String inboxFolderPath = Constants.HomePath + Constants.Dirprefix + Constants.DirInbox + Constants.Separator;
				File msgReceivedFile = new File(inboxFolderPath + GS.gI().getLastReceivedMessageFname());
				if (msgReceivedFile.exists())
				{
					msgReceivedFile.delete();
				}
				out = new FileWriter(msgReceivedFile, true);
				out.write(resultString);
				out.close();
				// We now check how many, if any, attached image are expected
				Pattern psc = Pattern.compile("(^_img.*),<analog>(\\d+)", Pattern.MULTILINE);
				Matcher msc = psc.matcher(resultString);
				GS.gI().setLastMessageNoExpectedImages(0);
				GS.gI().setCurrentImageSequenceNo(0);
				boolean keepLooking = true;
				for (int start = 0; keepLooking;)
				{
					keepLooking = msc.find(start);
					if (keepLooking)
					{
						// Store the field name for this image (sequence in form = sequence of Tx)
						GS.gI().getLastMessageImgFieldName()[GS.gI().lastMessageNoExpectedImages++] = msc.group(1);
						start = msc.end();
					}
				}
				// Save the time of end of Rx (any attached picture must be send within a set
				// time)
				GS.gI().setLastMessageEndTxTime(System.currentTimeMillis());
				// Advise reception of message
				post("PostToTerminal", GS.gI().getContext().getString(R.string.txt_ChecksumOK));
				post("PostToTerminal", "\n" + GS.gI().getContext().getString(R.string.txt_SavedFile) + ": " + resultFilename);
				Message.addEntryToLog(GS.gI().getContext().getString(R.string.txt_ReceivedMessage) + ": " + resultFilename);
			}
			else
			{
				post("PostToTerminal", "\n " + GS.gI().getContext().getString(R.string.txt_BadCrcFileContent) + "\n" + Message.geterrtext());
				// Save file anyway into a Blank form file for further usage
				String resultString = Message.geterrtext();
				// Remove the WRAP section and try to store as a normal form
				int formStart = resultString.indexOf("]<flmsg>");
				if (formStart != -1)
				{
					resultString = resultString.substring(formStart + 1);
				}
				File msgReceivedFile = new File(Constants.HomePath + Constants.Dirprefix + Constants.DirInbox + Constants.Separator + "Last_Bad_CrC_Rx");
				if (msgReceivedFile.exists())
				{
					msgReceivedFile.delete();
				}
				out = new FileWriter(msgReceivedFile, true);
				out.write(resultString);
				out.close();
				post("PostToTerminal", "\n\n" + GS.gI().getContext().getString(R.string.txt_SavedRecoveredData));
				Message.addEntryToLog(GS.gI().getContext().getString(R.string.txt_SavedRecoveredData));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void handleInitialization()
	{
		try
		{
			// Initialize send queue
			TX_Text = "";
			ModemPreamble = config.getPreferenceS("MODEMPREAMBLE", "");
			ModemPostamble = config.getPreferenceS("MODEMPOSTAMBLE", "");
			// Compression settings
			compressedMsg = config.getPreferenceB("COMPRESSED");
			Processor.mycall = config.getPreferenceS("CALL");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			loggingClass.writelog("Problems with config parameter.", e, true);
		}
	}

	@Override
	public void post(String type, String data)
	{
		if (shuttingDown) return;
		if (type.equalsIgnoreCase("PostV2Diagnostic"))
		{
			postToAttachedUI("PostV2Diagnostic", data);
		}
		if (type.equalsIgnoreCase("PostToTerminal"))
		{
			GS.gI().TermWindow += data;
			postToAttachedUI("PostToTerminal", data);
		}
		if (type.equalsIgnoreCase("PostTo"))
		{
			GS.gI().monitor += data;
			postToAttachedUI("PostToModem", data);
		}
		if (type.equalsIgnoreCase("processWrapBlock"))
		{
			processWrapBlock(data);
		}
		else
		{
			postToAttachedUI(type, data);
		}
	}

	private void postToAttachedUI(String type, String data)
	{
		UICallBack target = uiCallBack;
		if (target != null && hasSelectionFor(uiContactId)) target.post(type, data);
	}

	private ModemBackend backend;
	private static final Object BACKEND_OWNER_LOCK = new Object();
	private static Processor backendServiceOwner;
	private volatile BackendSelection selection;
	private volatile BackendSelection requestedSelection;
	private volatile PhoneSpectrumSnapshot latestPhoneSpectrum;
	private volatile int phoneCpuLoad;
	private long phoneSpectrumSequence;
	private long lastModem73SpectrumDiagnosticMs;

	public static final class PhoneSpectrumSnapshot
	{
		public final int sampleRate, fftSize;
		public final long sequence;
		private final float[] levelsDbfs;
		private final long revision;
		private PhoneSpectrumSnapshot(float[] levelsDbfs, int sampleRate, int fftSize, long revision, long sequence)
		{
			this.levelsDbfs = java.util.Arrays.copyOf(levelsDbfs, levelsDbfs.length);
			this.sampleRate = sampleRate; this.fftSize = fftSize; this.revision = revision; this.sequence = sequence;
		}
		public float[] copyLevelsDbfs() { return java.util.Arrays.copyOf(levelsDbfs, levelsDbfs.length); }
	}
	private java.lang.ref.WeakReference<CallBack> completionListener = new java.lang.ref.WeakReference<>(null);
	private java.util.Map<String, Object> appliedSettings;
	private volatile boolean shuttingDown;
	private boolean destroyed;
	private Thread radioCleanup;
	private volatile boolean stopDeadlineExpired;
	private static final long RADIO_STOP_DEADLINE_MS = 5000;
	private boolean preparationFailed, serviceStarted;
	private boolean transitionPending;
	private Thread transitionWorker;
	private long phoneBackendRevision = -1;
	private final Map<Long, PendingPhoneReceive> pendingPhoneReceives = new LinkedHashMap<>();
	private final Map<Long, PendingModem73> pendingModem73 = new LinkedHashMap<>();
	private final Map<Long, String> modem73TxStates = new LinkedHashMap<>();
	private volatile long selectionRevision;
	private volatile int uiContactId = -1;
	private String selectionError = "Select a contact configuration first";

	private boolean reserveBackendSlot()
	{
		synchronized (BACKEND_OWNER_LOCK)
		{
			if (backendServiceOwner != null && backendServiceOwner != this) return false;
			backendServiceOwner = this;
			return true;
		}
	}

	private void releaseBackendSlot()
	{
		synchronized (BACKEND_OWNER_LOCK)
		{
			if (backendServiceOwner == this) backendServiceOwner = null;
		}
	}

	public synchronized ModemBackend.State modemState()
	{
		if (preparationFailed && !shuttingDown) return ModemBackend.State.FAILED;
		return backend == null ? ModemBackend.State.IDLE : backend.state();
	}

	public synchronized String selectionError()
	{
		return selectionError;
	}

	private boolean rejectSelection(String mode, String reason, String userMessage)
	{
		selectionError = userMessage;
		logSelection("rejected", mode, reason);
		if (Modem73Backend.MODE.equals(mode)) logModem73Selection("selection_rejected", requestedSelection, reason);
		return false;
	}

	private void logModem73Selection(String stage, BackendSelection requested, String detail)
	{
		long generation = backend instanceof Modem73Backend ? ((Modem73Backend) backend).instanceId() : 0;
		int configurationId = requested == null ? -1 : requested.configuration().getId();
		int contactId = requested == null ? -1 : requested.contactId;
		String state = backend instanceof Modem73Backend ? ((Modem73Backend) backend).state().name() : "NONE";
		try { Utils.println("MODEM73", "stage=" + stage + " monoMs=" + SystemClock.elapsedRealtime() + " generation=" + generation + " selectionRevision=" + selectionRevision
				+ " configId=" + configurationId + " contactId=" + contactId + " state=" + state + " detail=" + (detail == null ? "none" : detail.replace('|', '/').replace('\n', ' ').replace('\r', ' '))); }
		catch (RuntimeException ignored) { }
	}

	void modem73Diagnostic(Modem73Backend source, String stage, String detail)
	{
		modem73Diagnostic(source, stage, source.operationId(), detail);
	}

	void modem73Diagnostic(Modem73Backend source, String stage, long operationId, String detail)
	{
		long revision = selectionRevision;
		BackendSelection current = selection;
		boolean active = backend == source;
		int configurationId = current != null && active ? current.configuration().getId() : -1;
		int contactId = current != null && active ? current.contactId : -1;
		try { Utils.println("MODEM73", "stage=" + stage + " monoMs=" + SystemClock.elapsedRealtime() + " generation=" + source.instanceId() + " selectionRevision=" + revision
				+ " configId=" + configurationId + " contactId=" + contactId + " operationId=" + operationId + " active=" + active + " state=" + source.state()
				+ " detail=" + (detail == null ? "none" : detail.replace('|', '/').replace('\n', ' ').replace('\r', ' '))); }
		catch (RuntimeException ignored) { }
	}

	public synchronized void modem73UiDiagnostic(String stage, long operationId, String detail)
	{
		long generation = backend instanceof Modem73Backend ? ((Modem73Backend) backend).instanceId() : 0;
		int configurationId = selection == null ? -1 : selection.configuration().getId();
		int contactId = selection == null ? -1 : selection.contactId;
		try { Utils.println("MODEM73", "stage=" + stage + " monoMs=" + SystemClock.elapsedRealtime() + " generation=" + generation + " selectionRevision=" + selectionRevision
				+ " configId=" + configurationId + " contactId=" + contactId + " operationId=" + operationId + " state=" + modemState() + " detail="
				+ (detail == null ? "none" : detail.replace('|', '/').replace('\n', ' ').replace('\r', ' '))); }
		catch (RuntimeException ignored) { }
	}

	public synchronized void modem73RecordStorageOutcome(boolean refresh)
	{
		if (backend instanceof Modem73Backend)
		{
			if (refresh) ((Modem73Backend) backend).recordRefreshRequested();
			else ((Modem73Backend) backend).recordStorageReturned();
		}
	}

	private static void logSelection(String stage, String mode, String detail)
	{
		String safeMode = mode == null ? "none" : mode.replace('|', '_').replace('\n', ' ').replace('\r', ' ');
		String safeDetail = detail == null ? "none" : detail.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
		if (safeDetail.length() > 240) safeDetail = safeDetail.substring(0, 240);
		try { Utils.println("MODEM_SELECTION", "stage=" + stage + " mode=" + safeMode + " detail=" + safeDetail); }
		catch (RuntimeException ignored) { }
	}

	private Map<String, Object> effectiveSettings()
	{
		java.util.Map<String, Object> result = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, ?> entry : GS.gI().getValue().getAll().entrySet())
		{
			String key = entry.getKey();
			if (key.startsWith("USE") || key.startsWith("RSFEC") || key.equals("AFREQUENCY") || key.equals("SLOWCPU") || key.startsWith("RSID") || key.equals("RXRSID")
					|| key.equals("TXRSID") || key.equals("VOLUME") || key.equals("WFMAXVALUE") || key.equals("SQUELCHVALUE") || key.equals("AFCONOFF") || key.equals("use_enc")
					|| key.equals("enc_algorithm"))
				result.put(key, entry.getValue());
		}
		return result;
	}

	public synchronized boolean applySelection(BackendSelection requested)
	{
		if (destroyed || shuttingDown)
		{
			return rejectSelection(requested == null ? null : requested.modeName(), "service_stopping destroyed=" + destroyed + " shuttingDown=" + shuttingDown, "The modem service is stopping");
		}
		if (transitionPending)
		{
			return rejectSelection(requested == null ? null : requested.modeName(), "transition_pending", "The previous modem is stopping; selection will continue after it is quiescent");
		}
		if (preparationFailed)
		{
			return rejectSelection(requested == null ? null : requested.modeName(), "preparation_cleanup_pending", "Previous modem preparation cleanup is pending");
		}
		requestedSelection = requested;
		if (requested != null && Modem73Backend.MODE.equals(requested.modeName())) logModem73Selection("selection_submitted", requested, "applySelection");
		if (requested == null)
		{
			return rejectSelection(null, "missing_configuration", "Select a contact configuration first");
		}
		final String targetMode = requested.modeName();
		final boolean targetPhone = "GGWave".equals(targetMode) || Modem73Backend.MODE.equals(targetMode);
		final boolean targetModem73 = Modem73Backend.MODE.equals(targetMode);
		if (targetPhone && requested.configuration().getId() == 9)
		{
			return rejectSelection(targetMode, "special_configuration_9_unavailable", "Phone modems are unavailable for the special configuration 9");
		}
		if (targetModem73 && requested.configuration().isEncryption() && !Modem73Crypto.supports(requested.configuration().getEncryptionType())) return rejectSelection(targetMode, "encrypted_algorithm_unsupported", "Select a supported MODEM73 encryption algorithm");
		if (targetModem73 && (!Modem73Packet.validCallsign(requested.modem73Callsign()) || !Modem73Backend.isSupportedProfile(requested.modem73Profile())))
			return rejectSelection(targetMode, "invalid_callsign_or_unsupported_profile", "MODEM73 requires a valid callsign and a supported profile");
		int radioMode = -1;
		try
		{
			if (!targetPhone) radioMode = RadioModemBackend.resolveMode(requested.modeName());
			if (!targetPhone && radioMode < 0)
			{
				return rejectSelection(targetMode, "radio_mode_unavailable", "The selected modem is unavailable; no other modem was started");
			}
		}
		catch (RuntimeException | LinkageError failure)
		{
			return rejectSelection(targetMode, "radio_capability_resolution_failed " + failureSummary(failure), "Radio capabilities are unavailable: " + failureSummary(failure));
		}
		boolean unchanged = requested.sameAs(selection) && effectiveSettings().equals(appliedSettings) && !GS.gI().isRXParamsChanged();
		if (unchanged)
		{
			if (targetModem73)
			{
				if (backend instanceof Modem73Backend && ((Modem73Backend) backend).isReceivingHealthy())
				{
					selectionError = "";
					logSelection("unchanged_ready_capture_verified", targetMode, "selection_match_backend_and_recent_capture_healthy");
					logModem73Selection("selection_reused", requested, "same_selection_backend_and_capture_healthy");
					return true;
				}
				if (backend instanceof Modem73Backend && ((Modem73Backend) backend).isStartingWithWorker())
				{
					selectionError = "";
					logModem73Selection("selection_reused_starting", requested, "same_selection_live_rx_worker_starting");
					return true;
				}
				if (backend instanceof Modem73Backend && ((Modem73Backend) backend).state() == ModemBackend.State.PAUSED && !backend.busy())
				{
					try
					{
						((Modem73Backend) backend).resume();
						selectionError = "";
						logModem73Selection("selection_resume_requested", requested, "same_selection_was_paused");
						return true;
					}
					catch (RuntimeException failure) { logModem73Selection("selection_resume_failed", requested, failureSummary(failure)); }
				}
				if (backend == null)
				{
					logModem73Selection("selection_recreate", requested, "selection_recorded_without_active_backend");
				}
				else
				{
					if (backend.busy() || GS.gI().isReceivingForm()) return rejectSelection(targetMode, "backend_busy_during_recovery", "MODEM73 is busy; receive recovery is pending");
					logModem73Selection("selection_recovery_requested", requested, "backend_not_receiving_healthy");
					selectionError = "MODEM73 receive stopped; safely restarting after backend cleanup";
					beginBackendTransition(requested);
					return false;
				}
			}
			else
			{
				selectionError = "";
				logSelection("unchanged_ready", targetMode, "selection_and_effective_settings_match");
				return true;
			}
		}
		if (backend == null && !reserveBackendSlot())
		{
			return rejectSelection(targetMode, "another_service_owns_or_is_stopping_backend", "Another modem service is still stopping or owns the audio backend");
		}
		// The service must be foreground before a selected backend opens audio.
		if (!serviceStarted)
		{
			try
			{
				startService(new Intent(this, Processor.class));
				serviceStarted = true;
				showForegroundNotification();
			}
			catch (RuntimeException failure)
			{
				if (backend == null) releaseBackendSlot();
				return rejectSelection(targetMode, "foreground_service_start_failed " + failureSummary(failure), "Modem service could not enter foreground: " + failureSummary(failure));
			}
		}
		if (backend != null && (!sameBackendFamily(backend, targetMode) || targetPhone && !requested.sameAs(selection)))
		{
			if (backend.busy() || GS.gI().isReceivingForm())
			{
				return rejectSelection(targetMode, "current_backend_busy_or_form_receive", "The current modem is busy; selection was not changed");
			}
			logSelection("transition_requested", targetMode, "from=" + backend.getClass().getSimpleName());
			beginBackendTransition(requested);
			selectionError = "The previous modem is stopping; the new selection will start after quiescence";
			logSelection("transition_pending", targetMode, "previous backend quiescence required");
			return false;
		}
		if (backend != null && (backend.busy() || GS.gI().isReceivingForm()))
		{
			return rejectSelection(targetMode, "backend_busy_or_form_receive", "Radio is busy; configuration was not applied");
		}
		if (backend == null)
		{
			if (!targetPhone && !RadioModemBackend.ownerAvailable())
			{
				return rejectSelection(targetMode, "previous_radio_shutdown_pending", "Previous Radio shutdown is pending");
			}
			logSelection("backend_create", targetMode, "selected_backend_only");
			if (targetModem73)
			{
				Modem.setPhoneBackendActive(true);
				backend = new Modem73Backend(this, this);
			}
			else if (targetPhone)
			{
				Modem.setPhoneBackendActive(true);
				backend = new GgWaveBackend(this, this);
			}
			else
			{
				Modem.setPhoneBackendActive(false);
				backend = new RadioModemBackend(this);
			}
		}
		try
		{
			logSelection("prepare_start", targetMode, "backend=" + backend.getClass().getSimpleName());
			backend.apply(requested, false);
			boolean selectionCommittedBeforeReceive = false;
			if (targetModem73)
			{
				// Publish the callback identity before the RX worker can return its first frame.
				selection = requested;
				++selectionRevision;
				latestPhoneSpectrum = null;
				phoneCpuLoad = 0;
				pendingPhoneReceives.clear();
				pendingModem73.clear();
				phoneBackendRevision = selectionRevision;
				((Modem73Backend) backend).setSpectrumEnabled(uiCallBack != null && uiContactId == requested.contactId);
				appliedSettings = effectiveSettings();
				GS.gI().setRXParamsChanged(false);
				selectionCommittedBeforeReceive = true;
				logModem73Selection("selection_accepted", requested, "backend_applied; starting persistent RX");
			}
			if (targetPhone) backend.receive();
			if (!selectionCommittedBeforeReceive)
			{
				selection = requested;
				++selectionRevision;
				latestPhoneSpectrum = null;
				phoneCpuLoad = 0;
				pendingPhoneReceives.clear();
				phoneBackendRevision = targetPhone ? selectionRevision : -1;
				appliedSettings = effectiveSettings();
				GS.gI().setRXParamsChanged(false);
			}
			if (!targetPhone && !GS.gI().isModemPaused()) backend.receive();
			GS.gI().setProcessorON(true);
			selectionError = "";
			if (targetModem73)
			{
				logSelection("selection_accepted", targetMode, "state=" + backend.state() + " revision=" + selectionRevision);
				logModem73Selection("selection_start_requested", requested, "state=" + backend.state());
			}
			else logSelection("ready", targetMode, "state=" + backend.state() + " revision=" + selectionRevision);
			return true;
		}
		catch (RuntimeException | LinkageError failure)
		{
			selectionError = (targetModem73 ? "MODEM73 preparation failed: " : targetPhone ? "GGWave preparation failed: " : "Radio preparation failed: ") + failureSummary(failure);
			logSelection("prepare_failed", targetMode, failureSummary(failure));
			if (targetModem73) logModem73Selection("prepare_failed", requested, failureSummary(failure));
			preparationFailed = true;
			cleanupFailedPreparation(backend);
			return false;
		}
	}

	private static String failureSummary(Throwable failure)
	{
		String message = failure == null ? "unknown" : failure.getMessage();
		if (message == null || message.trim().isEmpty()) message = failure == null ? "unknown" : failure.getClass().getSimpleName();
		message = message.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
		return message.length() > 240 ? message.substring(0, 240) : message;
	}

	/** Quiesce a partially prepared engine without permanently closing the live Processor service. */
	private void cleanupFailedPreparation(final ModemBackend failedBackend)
	{
		logSelection("failed_cleanup_start", requestedSelection == null ? null : requestedSelection.modeName(), "backend=" + (failedBackend == null ? "none" : failedBackend.getClass().getSimpleName()));
		transitionPending = true;
		++selectionRevision;
		phoneBackendRevision = -1;
		pendingPhoneReceives.clear();
		pendingModem73.clear();
		modem73TxStates.clear();
		if (failedBackend == null)
		{
			preparationFailed = false;
			transitionPending = false;
			selection = null;
			appliedSettings = null;
			releaseBackendSlot();
			logSelection("failed_cleanup_complete", null, "no_backend_resources");
			return;
		}
		try { failedBackend.stop(); }
		catch (RuntimeException | LinkageError failure)
		{
			selectionError = "Failed modem preparation stop request failed: " + failureSummary(failure);
			preparationFailed = true;
			logSelection("failed_cleanup_stop_request_error", requestedSelection == null ? null : requestedSelection.modeName(), failureSummary(failure));
			return;
		}
		transitionWorker = new Thread(() ->
		{
			try
			{
				boolean interrupted = false;
				ModemBackend.State stopped;
				for (;;) { try { stopped = failedBackend.awaitStop(RADIO_STOP_DEADLINE_MS); break; } catch (InterruptedException ignored) { interrupted = true; } }
				if (stopped == ModemBackend.State.STOP_PENDING)
				{
					logSelection("failed_cleanup_pending", null, "backend=" + failedBackend.getClass().getSimpleName());
					post("BackendStopPending", "The failed modem preparation is still stopping");
					for (;;) { try { failedBackend.awaitStop(0); break; } catch (InterruptedException ignored) { interrupted = true; } }
				}
				if (interrupted) Thread.currentThread().interrupt();
				if (!(failedBackend instanceof RadioModemBackend)) Modem.setPhoneBackendActive(false);
				boolean releaseOwner;
				synchronized (Processor.this)
				{
					if (backend == failedBackend) backend = null;
					selection = null;
					appliedSettings = null;
					preparationFailed = false;
					transitionPending = false;
					releaseOwner = destroyed || shuttingDown;
				}
				if (releaseOwner) releaseBackendSlot();
				logSelection("failed_cleanup_complete", null, "backend=" + failedBackend.getClass().getSimpleName() + " releaseOwner=" + releaseOwner);
			}
			catch (RuntimeException | LinkageError failure)
			{
				synchronized (Processor.this)
				{
					preparationFailed = true;
					transitionPending = true;
					selectionError = "Failed modem preparation cleanup is pending: " + failure.getMessage();
				}
				logSelection("failed_cleanup_error", null, failureSummary(failure));
				post("BackendStopPending", selectionError());
			}
		}, "SCVA-Backend-Preparation-Cleanup");
		transitionWorker.start();
	}

	private static boolean sameBackendFamily(ModemBackend active, String targetMode)
	{
		if (Modem73Backend.MODE.equals(targetMode)) return active instanceof Modem73Backend;
		if ("GGWave".equals(targetMode)) return active instanceof GgWaveBackend;
		return active instanceof RadioModemBackend;
	}

	private void beginBackendTransition(BackendSelection requested)
	{
		if (backend == null || transitionPending) return;
		final ModemBackend oldBackend = backend;
		transitionPending = true;
		++selectionRevision; // Invalidate callbacks and delayed results before stop is requested.
		latestPhoneSpectrum = null;
		if (oldBackend instanceof Modem73Backend) post("PhoneSpectrum", "");
		phoneCpuLoad = 0;
		pendingPhoneReceives.clear();
		phoneBackendRevision = -1;
		logSelection("transition_stop_requested", requested == null ? null : requested.modeName(), "previous=" + oldBackend.getClass().getSimpleName());
		if (oldBackend instanceof Modem73Backend) logModem73Selection("backend_stop_requested", selection, "transition_to=" + (requested == null ? "none" : requested.modeName()));
		try { oldBackend.stop(); }
		catch (RuntimeException | LinkageError failure)
		{
			selectionError = "Previous modem stop request failed: " + failureSummary(failure);
			logSelection("transition_stop_request_error", requested == null ? null : requested.modeName(), failureSummary(failure));
			return;
		}
		transitionWorker = new Thread(() -> {
			try
			{
				ModemBackend.State stopped;
				try { stopped = oldBackend.awaitStop(RADIO_STOP_DEADLINE_MS); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Backend stop wait was interrupted", interrupted); }
				if (stopped == ModemBackend.State.STOP_PENDING)
				{
					logSelection("transition_stop_pending", requestedSelection == null ? null : requestedSelection.modeName(), "previous=" + oldBackend.getClass().getSimpleName());
					post("BackendStopPending", "The previous modem is still stopping");
					for (;;) { try { oldBackend.awaitStop(0); break; } catch (InterruptedException ignored) { } }
				}
				if (!(oldBackend instanceof RadioModemBackend)) Modem.setPhoneBackendActive(false);
				BackendSelection next;
				boolean releaseOwner;
				synchronized (Processor.this)
				{
					if (backend == oldBackend) backend = null;
					selection = null;
					appliedSettings = null;
					transitionPending = false;
					next = requestedSelection;
					releaseOwner = destroyed || shuttingDown;
				}
				if (releaseOwner) { releaseBackendSlot(); return; }
				logSelection("transition_quiesced", next == null ? null : next.modeName(), "previous=" + oldBackend.getClass().getSimpleName());
				if (next != null && applySelection(next))
				{
					logSelection("transition_selection_ready", next.modeName(), "selected_backend_started");
					post("BackendSelectionReady", "");
				}
				else
				{
					logSelection("transition_selection_failed", next == null ? null : next.modeName(), selectionError());
					post("BackendSelectionFailed", selectionError());
				}
			}
			catch (RuntimeException | LinkageError failure)
			{
				synchronized (Processor.this) { transitionPending = true; selectionError = "Previous modem shutdown incomplete: " + failure.getMessage(); }
				logSelection("transition_cleanup_failed", requestedSelection == null ? null : requestedSelection.modeName(), failureSummary(failure));
				post("BackendStopPending", selectionError());
			}
		}, "SCVA-Backend-Transition");
		transitionWorker.start();
	}

	@Override
	public synchronized void onText(GgWaveBackend source, GgWaveBackend.ReceivedText message, long operationId)
	{
		if (message == null || !message.accepted()) { ggTrace("GGWAVE_RX", "stage=processor_callback op=" + operationId + " result=rejected_packet"); return; }
		if (backend != source || phoneBackendRevision != selectionRevision || selection == null || !"GGWave".equals(selection.modeName())
				|| !hasSelectionFor(selection.contactId) || uiCallBack == null || uiContactId != selection.contactId)
		{
			ggTrace("GGWAVE_RX", "stage=processor_callback op=" + operationId + " result=stale_context");
			GgWaveDiagnostics.event(this, "phone.rx.delivery", operationId, "rx", selection == null ? "" : selection.ggWaveSpeed(), "\"outcome\":\"reject_stale_context\"");
			return;
		}
		String text = message.text();
		if (text == null || message.format == GgWaveBackend.ReceivedText.Format.SCVA_PREPARED && !text.endsWith("§")) { ggTrace("GGWAVE_RX", "stage=processor_callback op=" + operationId + " result=invalid_complete_message"); return; }
        MessageEncryptionMetadata.Snapshot metadata;
        try { metadata=snapshotIncoming(message.format==GgWaveBackend.ReceivedText.Format.SCVA_PREPARED && selection.configuration().isEncryption() && MessageEncryptionMetadata.looksLikeChunks(text),false,null); }
        catch(RuntimeException unavailable) { post("PostMessageMetadataError","Message metadata/key context unavailable");return; }
		pendingPhoneReceives.put(operationId, new PendingPhoneReceive(source, message, selection, selectionRevision,metadata));
		while (pendingPhoneReceives.size() > 64) pendingPhoneReceives.remove(pendingPhoneReceives.keySet().iterator().next());
		GgWaveDiagnostics.event(this, "phone.rx.delivery", operationId, "rx", selection.ggWaveSpeed(), "\"outcome\":\"callback_accepted\",\"format\":\"" + message.format + "\",\"textBytes\":" + message.bytes().length);
		ggTrace("GGWAVE_RX", "stage=processor_callback op=" + operationId + " result=accepted format=" + message.format + " messageBytes=" + message.bytes().length);
		post("PostPhoneText", Long.toString(operationId));
	}

	/** Consume one complete decoded event only while its original backend/contact selection is still active. */
	public synchronized GgWaveBackend.ReceivedText consumePhoneReceive(long operationId)
	{
		PendingPhoneReceive pending = pendingPhoneReceives.remove(operationId);
		if (pending == null || backend != pending.source || selectionRevision != pending.revision || selection == null
				|| !pending.selection.sameAs(selection) || !hasSelectionFor(pending.selection.contactId)
				|| uiCallBack == null || uiContactId != pending.selection.contactId)
		{
			GgWaveDiagnostics.event(this, "phone.rx.delivery", operationId, "rx", selection == null ? "" : selection.ggWaveSpeed(), "\"outcome\":\"reject_stale_context\"");
			ggTrace("GGWAVE_RX", "stage=processor_consume op=" + operationId + " result=stale_or_missing");
			return null;
		}
		ggTrace("GGWAVE_RX", "stage=processor_consume op=" + operationId + " result=accepted format=" + pending.message.format + " messageBytes=" + pending.message.bytes().length);
		return pending.message;
	}
    public synchronized MessageEncryptionMetadata.Delivery<GgWaveBackend.ReceivedText> consumePhoneMessage(long operationId) {
        PendingPhoneReceive pending=pendingPhoneReceives.get(operationId);
        GgWaveBackend.ReceivedText content=consumePhoneReceive(operationId);
        return content==null?null:new MessageEncryptionMetadata.Delivery<>(content,pending.metadata);
    }

	private static void ggTrace(String tag, String message)
	{
		try { Utils.println(tag, message); } catch (RuntimeException ignored) { }
	}

	private static final class PendingPhoneReceive
	{
		final GgWaveBackend source;
		final GgWaveBackend.ReceivedText message;
		final BackendSelection selection;
		final long revision;
		final MessageEncryptionMetadata.Snapshot metadata;
		PendingPhoneReceive(GgWaveBackend source, GgWaveBackend.ReceivedText message, BackendSelection selection, long revision,MessageEncryptionMetadata.Snapshot metadata)
		{
			this.source = source; this.message = message; this.selection = selection; this.revision = revision;this.metadata=metadata;
		}
	}

    private MessageEncryptionMetadata.Snapshot snapshotIncoming(boolean encrypted,boolean modern,String algorithm) {
        if(!encrypted)return MessageEncryptionMetadata.plaintext(selection.contactId,0);
        com.SCVA.models.Key key=new com.SCVA.db.KeysDataSource(this).getDefaultKey(selection.contactId);
        if(key==null||key.getUserGroupId()!=selection.contactId)throw new IllegalStateException("Receiving contact key unavailable");
        if(modern&&!selection.matchesModem73KeyContext(Modem73Crypto.keyContext(key.getId(),key.getKeyText())))throw new IllegalStateException("Receiving key selection changed");
        return modern?MessageEncryptionMetadata.modem73(selection.contactId,key.getId(),algorithm)
                :MessageEncryptionMetadata.chunks(selection.contactId,key.getId(),selection.configuration().getEncryptionType(),"");
    }
    public static final class MessageContext {
        public final BackendSelection selection;public final long revision;public final MessageEncryptionMetadata.Snapshot metadata;
        private MessageContext(BackendSelection selection,long revision,MessageEncryptionMetadata.Snapshot metadata) { this.selection=selection;this.revision=revision;this.metadata=metadata; }
    }
    public synchronized MessageContext captureMessageContext(int contactId,BackendSelection expected) {
        if(!matchesSelection(expected)||expected.contactId!=contactId)throw new IllegalStateException("Message context changed");
        MessageEncryptionMetadata.Snapshot metadata=null;
        try { metadata=snapshotIncoming(selection.configuration().isEncryption(),false,null); } catch(RuntimeException unavailable) { /* Plaintext may still be received. */ }
        return new MessageContext(selection,selectionRevision,metadata);
    }
    public synchronized boolean matchesMessageContext(MessageContext context) {
        return context!=null && context.revision==selectionRevision && matchesSelection(context.selection);
    }

	@Override
	public synchronized void onProgress(GgWaveBackend source, long operationId, int percent)
	{
		if (backend == source && phoneBackendRevision == selectionRevision && selection != null && hasSelectionFor(selection.contactId))
			post("PostPhoneTxProgress", operationId + ":" + Math.max(0, Math.min(100, percent)));
	}

	@Override
	public synchronized void onSpectrum(GgWaveBackend source, float[] levelsDbfs, int sampleRate, int fftSize)
	{
		if (levelsDbfs == null || backend != source || phoneBackendRevision != selectionRevision || selection == null
				|| !"GGWave".equals(selection.modeName()) || uiCallBack == null || uiContactId != selection.contactId) return;
		latestPhoneSpectrum = new PhoneSpectrumSnapshot(levelsDbfs, sampleRate, fftSize, selectionRevision, ++phoneSpectrumSequence);
		post("PhoneSpectrum", "");
	}

	@Override
	public synchronized void onSpectrum(Modem73Backend source, float[] levelsDbfs, int sampleRate, int fftSize, long generation)
	{
		if (levelsDbfs == null || backend != source || selection == null || !Modem73Backend.MODE.equals(selection.modeName())
				|| source.state() != ModemBackend.State.RECEIVING || uiCallBack == null || uiContactId != selection.contactId
				|| generation != source.spectrumGeneration()) return;
		latestPhoneSpectrum = new PhoneSpectrumSnapshot(levelsDbfs, sampleRate, fftSize, selectionRevision, ++phoneSpectrumSequence);
		post("PhoneSpectrum", "");
		long now = SystemClock.elapsedRealtime();
		if (now - lastModem73SpectrumDiagnosticMs >= 5000) {
			lastModem73SpectrumDiagnosticMs = now;
			modem73Diagnostic(source, "spectrum_routed", "sequence=" + phoneSpectrumSequence + " sampleRate=" + sampleRate + " fftSize=" + fftSize);
		}
	}

	@Override
	public synchronized void onCpuLoad(GgWaveBackend source, int percent)
	{
		if (backend != source || phoneBackendRevision != selectionRevision || selection == null
				|| !"GGWave".equals(selection.modeName()) || uiCallBack == null || uiContactId != selection.contactId) return;
		phoneCpuLoad = Math.max(0, Math.min(100, percent));
		post("updateCPULoad", "");
	}

	public int phoneCpuLoad()
	{
		return phoneCpuLoad;
	}

	/** Returns the latest spectrum for the attached phone-modem chat. */
	public synchronized PhoneSpectrumSnapshot phoneSpectrumSnapshot(int contactId)
	{
		PhoneSpectrumSnapshot snapshot = latestPhoneSpectrum;
		if (snapshot == null || selection == null || !(backend instanceof GgWaveBackend || backend instanceof Modem73Backend) || selection.contactId != contactId
				|| snapshot.revision != selectionRevision || uiCallBack == null || uiContactId != contactId) return null;
		if (backend instanceof Modem73Backend && backend.state() != ModemBackend.State.RECEIVING) return null;
		return snapshot;
	}

	@Override
	public synchronized void onPlaybackComplete(GgWaveBackend source, String prepared)
	{
		if (backend == source && phoneBackendRevision == selectionRevision && selection != null && hasSelectionFor(selection.contactId))
			post("PostPhoneTxComplete", prepared);
	}

	@Override
	public synchronized void onCancelled(GgWaveBackend source)
	{
		if (backend == source && phoneBackendRevision == selectionRevision && selection != null && hasSelectionFor(selection.contactId))
			post("PostPhoneCancelled", "GGWave transmission cancelled");
	}

	@Override
	public synchronized void onFailure(GgWaveBackend source, String message)
	{
		if (backend == source && phoneBackendRevision == selectionRevision && selection != null && hasSelectionFor(selection.contactId))
			post("PostPhoneError", message == null ? "GGWave operation failed" : message);
	}

	public synchronized void attachUI(UICallBack listener, int contactId)
	{
		uiCallBack = listener;
		uiContactId = contactId;
		if (backend instanceof Modem73Backend) {
			latestPhoneSpectrum = null;
			((Modem73Backend) backend).setSpectrumEnabled(selection != null && selection.contactId == contactId);
		}
		if (selection != null && Modem73Backend.MODE.equals(selection.modeName())) logModem73Selection("ui_attached", selection, "contactContext=" + (contactId == selection.contactId));
		if (listener instanceof CallBack && completionListener.get() != listener)
		{
			completionListener.clear();
			completionListener = new java.lang.ref.WeakReference<>((CallBack) listener);
		}
	}

	public synchronized void detachUI(UICallBack listener)
	{
		if (uiCallBack == listener)
		{
			if (selection != null && Modem73Backend.MODE.equals(selection.modeName())) logModem73Selection("ui_detached", selection, "backend_remains_service_owned");
			uiCallBack = null;
			uiContactId = -1;
			if (backend instanceof Modem73Backend) {
				latestPhoneSpectrum = null;
				((Modem73Backend) backend).setSpectrumEnabled(false);
			}
			pendingPhoneReceives.clear();
			pendingModem73.clear();
		}
	}

	public synchronized void detachCompletion(CallBack listener)
	{
		if (completionListener.get() == listener) completionListener.clear();
	}

	public synchronized boolean matchesSelection(BackendSelection expected)
	{
		return expected != null && expected.sameAs(selection) && hasSelectionFor(expected.contactId);
	}

	public synchronized boolean hasSelectionFor(int contactId)
	{
		return !shuttingDown && !preparationFailed && selection != null && selection.sameAs(requestedSelection) && selection.contactId == contactId;
	}

	public synchronized void requireRadio(int contactId)
	{
		if (!hasSelectionFor(contactId)) throw new IllegalStateException("No active Radio selection for this contact");
		if (!(backend instanceof RadioModemBackend)) throw new IllegalStateException("This request is unsupported by the selected Phone backend");
		ModemBackend.State state = backend.state();
		if (state != ModemBackend.State.RECEIVING && state != ModemBackend.State.PAUSED) throw new IllegalStateException("Radio is not ready: " + state);
	}

	private void admitTransmit(int contactId)
	{
		requireRadio(contactId);
		if (backend.busy()) throw new IllegalStateException("Transmitter busy");
		final long revision = selectionRevision;
		final java.lang.ref.WeakReference<CallBack> completion = completionListener;
		backend.listener((object, type) -> {
			CallBack target = completion.get();
			if (!shuttingDown && revision == selectionRevision && hasSelectionFor(contactId) && target != null) target.notify(object, type);
			else if (!"PROGRESS_HIDE".equalsIgnoreCase(type)) com.SCVA.Utils.TruSDXCat.getInstance().pttOff();
		});
	}

	public synchronized void sendMessage(int contactId, String prepared)
	{
		admitTransmit(contactId);
		TX_Text += prepared;
		backend.text(prepared);
	}

	/** Explicit ordinary-text Phone route; Radio/framed legacy entry points never reach it. */
	public synchronized void sendPhoneText(int contactId, String prepared, long operationId, boolean encrypted, String algorithm)
	{
		if (!hasSelectionFor(contactId) || !(backend instanceof GgWaveBackend) || phoneBackendRevision != selectionRevision)
		{ ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=rejected reason=selection_not_ready"); throw new IllegalStateException("No ready GGWave selection for this contact"); }
		if (selection.configuration().getId() == 9 || GS.gI().isReceivingForm()) { ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=rejected reason=special_or_busy_mode"); throw new IllegalStateException("This GGWave request is unavailable"); }
		if (prepared == null) { ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=rejected reason=null_payload"); throw new IllegalArgumentException("GGWave message is empty"); }
		byte[] bytes = GgWaveBackend.preparedBytes(prepared);
		int maximum = GgWavePacket.MAX_PREPARED_BYTES;
		if (bytes.length < 1 || bytes.length > maximum) { ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=rejected reason=prepared_length preparedBytes=" + bytes.length + " maximum=" + maximum + " encrypted=" + encrypted); throw new IllegalArgumentException("GGWave supports at most " + maximum + " final prepared UTF-8 bytes for this message format"); }
		if (!prepared.endsWith("§")) { ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=rejected reason=missing_terminator preparedBytes=" + bytes.length); throw new IllegalArgumentException("GGWave requires the prepared chat terminator"); }
		if (backend.busy()) { ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=rejected reason=transmitter_busy preparedBytes=" + bytes.length); throw new IllegalStateException("GGWave transmitter is busy"); }
		ggTrace("GGWAVE_TX", "stage=processor_admission op=" + operationId + " result=accepted preparedBytes=" + bytes.length + " encrypted=" + encrypted + " algorithm=" + safeDiagnosticAlgorithm(algorithm) + " speed=" + selection.ggWaveSpeed());
		GgWaveDiagnostics.event(this, "phone.tx.admission", operationId, "tx", selection.ggWaveSpeed(),
				"\"outcome\":\"accepted\",\"preparedBytes\":" + bytes.length + ",\"encryption\":" + encrypted + ",\"algorithm\":\"" + safeDiagnosticAlgorithm(algorithm) + "\"");
		((GgWaveBackend) backend).text(prepared, operationId, encrypted); // Admission precedes queue/UI effects.
	}

	public synchronized long sendModem73Text(int contactId, String text)
	{
		if (!hasSelectionFor(contactId) || !(backend instanceof Modem73Backend) || selection.configuration().isEncryption())
		{
			if (backend instanceof Modem73Backend) logModem73Selection("tx_admission_rejected", selection, "reason=no_ready_plaintext_selection_or_encryption_enabled");
			throw new IllegalStateException("No ready plaintext MODEM73 selection for this contact");
		}
		if (GS.gI().isReceivingForm()) { logModem73Selection("tx_admission_rejected", selection, "reason=form_receive_active");throw new IllegalStateException("MODEM73 cannot transmit while a form is being received"); }
		try { Modem73Packet.prepare(selection.modem73Callsign(), text); }
		catch (IllegalArgumentException invalid) { logModem73Selection("tx_admission_rejected", selection, "reason=invalid_text_or_capacity");throw invalid; }
		if (backend.busy()) { logModem73Selection("tx_admission_rejected", selection, "reason=transmitter_busy");throw new IllegalStateException("MODEM73 transmitter is busy"); }
		backend.text(text);
		long operation=((Modem73Backend) backend).lastOperationId();
		modem73Diagnostic((Modem73Backend)backend,"tx_admitted",operation,"direction=tx outcome=queued");
		return operation;
	}

    public synchronized long sendModem73Record(int contactId,BackendSelection expected,byte[] record) {
        if(!hasSelectionFor(contactId)||!(backend instanceof Modem73Backend)||expected==null||!expected.sameAs(selection)||!selection.configuration().isEncryption()||!selection.hasModem73KeyContext()||GS.gI().isReceivingForm())throw new IllegalStateException("No matching MODEM73 encryption/key selection for this contact");
        ((Modem73Backend)backend).record(record);long operation=((Modem73Backend)backend).lastOperationId();modem73Diagnostic((Modem73Backend)backend,"tx_admitted",operation,"encrypted=true recordBytes="+record.length);return operation;
    }
	boolean isCatEnabled()
	{
		return "1".equalsIgnoreCase(new com.SCVA.db.SettingsDataSource(this).getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL, "0"));
	}
	long catLeadMs()
	{
		try { return Long.parseLong(new com.SCVA.db.SettingsDataSource(this).getValueAgainstKey(Constants.SETTINGS_RIG_CONTROL_DELAY, "0")) * 1000L; }
		catch (RuntimeException invalid) { return 0; }
	}
	long catTailMs() { return 50; }

	private static final class PendingModem73
	{
		final Modem73Backend source; final BackendSelection selection; final long revision; final byte[] payload; final boolean receive;
        final MessageEncryptionMetadata.Snapshot metadata;
		PendingModem73(Modem73Backend source, BackendSelection selection, long revision, byte[] payload, boolean receive) { this(source,selection,revision,payload,receive,null); }
        PendingModem73(Modem73Backend source,BackendSelection selection,long revision,byte[] payload,boolean receive,MessageEncryptionMetadata.Snapshot metadata) {this.source=source;this.selection=selection;this.revision=revision;this.payload=payload.clone();this.receive=receive;this.metadata=metadata;}
	}
	@Override public synchronized void onState(Modem73Backend source, String value)
	{
		if (backend == source) {
			logSelection("modem73_state", Modem73Backend.MODE, value); logModem73Selection("backend_state", selection, value);
			if (value == null || !value.startsWith("RECEIVING")) { latestPhoneSpectrum = null; post("PhoneSpectrum", ""); }
		}
	}
	@Override public synchronized void onTxStage(Modem73Backend source, long operationId, String stage)
	{
		if (backend != source || selection == null || !Modem73Backend.MODE.equals(selection.modeName()) || !hasSelectionFor(selection.contactId)) { modem73Diagnostic(source,"stale_tx_stage",operationId,"stage="+stage);return; }
		logSelection("modem73_tx_stage", Modem73Backend.MODE, "operation=" + operationId + " stage=" + stage);
		modem73Diagnostic(source,"tx_stage",operationId,"direction=tx stage="+stage);
		modem73TxStates.put(operationId, stage);
		while (modem73TxStates.size() > 64) modem73TxStates.remove(modem73TxStates.keySet().iterator().next());
	}
	@Override public synchronized void onProgress(Modem73Backend source, long operationId, int percent)
	{
		if(backend==source && selection!=null && hasSelectionFor(selection.contactId)) post("PostModem73Progress",operationId+":"+Math.max(0,Math.min(99,percent)));
	}
	@Override public synchronized void onTx(Modem73Backend source, long operationId, String outcome, byte[] payload, String detail)
	{
		if (backend != source || selection == null || !Modem73Backend.MODE.equals(selection.modeName()) || !hasSelectionFor(selection.contactId)) { modem73Diagnostic(source,"stale_tx_terminal",operationId,"direction=tx outcome="+outcome);return; }
		logSelection("modem73_tx_terminal", Modem73Backend.MODE, "operation=" + operationId + " outcome=" + outcome + " detail=" + (detail == null ? "none" : detail));
		modem73Diagnostic(source,"tx_result_received",operationId,"direction=tx outcome="+outcome+" detail="+(detail==null?"none":detail)+" next=ui_result_event");
		pendingModem73.put(operationId,new PendingModem73(source,selection,selectionRevision,payload,false));
		modem73TxStates.put(operationId, outcome);
		while(pendingModem73.size()>64) pendingModem73.remove(pendingModem73.keySet().iterator().next());
		post("PostModem73Tx", operationId+":"+outcome+":"+detail);
	}
	void modem73Progress(Modem73Backend source,long operationId,int percent) { onProgress(source,operationId,percent); }
	@Override public synchronized void onRx(Modem73Backend source, byte[] payload)
	{
		long id=Modem73Backend.nextOperationId();
		modem73Diagnostic(source, "codec_packet_received", id, "bytes=" + (payload == null ? -1 : payload.length));
		if (payload==null || backend!=source || selection==null || !Modem73Backend.MODE.equals(selection.modeName()) || !hasSelectionFor(selection.contactId)
				|| uiCallBack==null || uiContactId!=selection.contactId)
		{
			source.recordDelivery(false);
			logSelection("modem73_rx_rejected", Modem73Backend.MODE, "stale_backend_selection_or_chat_context");
			modem73Diagnostic(source, "delivery_rejected", id, "reason=stale_backend_selection_or_active_chat_unavailable");
			return;
		}
        Modem73Packet.Received parsed;
        try { parsed=Modem73Packet.receive(payload); }
        catch(RuntimeException rejected) { source.recordParser(false);modem73Diagnostic(source,"parser_rejected",id,"reason=invalid_application_record bytes="+payload.length);return; }
        if(parsed.encrypted&&!selection.configuration().isEncryption()) {source.recordParser(false);modem73Diagnostic(source,"parser_rejected",id,"reason=encryption_configuration_mismatch");post("PostModem73ReceiveError","Encrypted reception requires an encrypted selection and scoped contact key");return;}
        source.recordParser(true);modem73Diagnostic(source,"application_record_validated",id,"encrypted="+parsed.encrypted+" bytes="+payload.length+" decryption=application_stage");
        MessageEncryptionMetadata.Snapshot metadata;
        try { metadata=snapshotIncoming(parsed.encrypted,true,parsed.algorithm); }
        catch(RuntimeException unavailable) { source.recordDelivery(false);modem73Diagnostic(source,"delivery_rejected",id,"reason=receiving_key_or_metadata_context_unavailable");post("PostMessageMetadataError","Receiving key/metadata context unavailable");return; }
		pendingModem73.put(id,new PendingModem73(source,selection,selectionRevision,payload,true,metadata));
		post("PostModem73Rx",Long.toString(id));
	}
	@Override public synchronized void onFailure(Modem73Backend source, String detail)
	{
		if (backend != source) { modem73Diagnostic(source, "stale_runtime_failure", detail == null ? "MODEM73 operation failed" : detail); return; }
		latestPhoneSpectrum = null;
		post("PhoneSpectrum", "");
		if(backend==source && selection!=null && hasSelectionFor(selection.contactId))
		{
			String reason=detail==null?"MODEM73 operation failed":detail;
			selectionError=reason;
			logSelection("modem73_runtime_failure", Modem73Backend.MODE, reason);
			modem73Diagnostic(source,"runtime_failure",reason);
			post("PostModem73Error",reason);
		}
	}
	public synchronized byte[] consumeModem73Event(long id)
	{
		PendingModem73 event=pendingModem73.remove(id);
		if(event==null || backend!=event.source || selectionRevision!=event.revision || selection==null || !event.selection.sameAs(selection)
				|| !hasSelectionFor(event.selection.contactId) || uiCallBack==null || uiContactId!=event.selection.contactId){if(event!=null&&event.receive)event.source.recordDelivery(false);modem73UiDiagnostic("event_context_rejected",id,"reason=stale_revision_backend_or_chat");return null;}
		if(event.receive)event.source.recordDelivery(true);
		modem73UiDiagnostic(event.receive?"rx_delivery_context_accepted":"tx_result_consumed",id,"direction="+(event.receive?"rx":"tx")+" payloadBytes="+event.payload.length+" meaning=accepted_for_chat_callback");
		return event.payload.clone();
	}
    public synchronized Modem73Packet.Received consumeModem73Record(long id){byte[] record=consumeModem73Event(id);return record==null?null:Modem73Packet.receive(record);}
    public synchronized MessageEncryptionMetadata.Delivery<Modem73Packet.Received> consumeModem73Message(long id) {
        PendingModem73 pending=pendingModem73.get(id);byte[] record=consumeModem73Event(id);
        return record==null?null:new MessageEncryptionMetadata.Delivery<>(Modem73Packet.receive(record),pending.metadata);
    }
    public synchronized String consumeModem73Text(long id){Modem73Packet.Received record=consumeModem73Record(id);return record==null||record.encrypted?null:record.stored;}

	private static String safeDiagnosticAlgorithm(String algorithm)
	{
		if (algorithm == null) return "unknown";
		return algorithm.matches("[A-Za-z0-9_-]{1,24}") ? algorithm : "other";
	}

	public synchronized void sendImage(int contactId, String prepared)
	{
		admitTransmit(contactId);
		TX_Text += prepared;
		backend.image(prepared);
	}

	public synchronized void sendKey(int contactId, String prepared)
	{
		admitTransmit(contactId);
		TX_Text += prepared;
		backend.key(prepared);
	}

	public synchronized void sendBinary(int contactId, byte[] data)
	{
		admitTransmit(contactId);
		backend.binary(data);
	}

	public synchronized void sendDiagnostic(int sequence, boolean readable)
	{
		if (selection == null) throw new IllegalStateException("Open a configured chat first");
		admitTransmit(selection.contactId);
		if (readable) backend.readableDiagnostic(sequence);
		else
			backend.diagnostic(sequence);
	}

	public synchronized void tune(int contactId)
	{
		admitTransmit(contactId);
		backend.tune();
	}

	public synchronized void cancelRadio(int contactId)
	{
		if (!shuttingDown && selection != null && selection.contactId == contactId) backend.cancel();
	}

	public synchronized boolean backendBusy(int contactId)
	{
		return hasSelectionFor(contactId) && backend != null && backend.busy();
	}

	public synchronized void applyRadioConfiguration(int contactId, boolean image)
	{
		requireRadio(contactId);
		backend.apply(selection, image);
		appliedSettings = effectiveSettings();
	}

	public synchronized void changeFrequency(double value, String savedValue)
	{
		if (selection == null) throw new IllegalStateException("Open a configured chat first");
		requireRadio(selection.contactId);
		if (backend.busy()) throw new IllegalStateException("Transmitter busy");
		backend.frequency(value, savedValue);
	}

	public synchronized void setRsid(int contactId, boolean receive, boolean enabled)
	{
		if (!hasSelectionFor(contactId)) throw new IllegalStateException("No active Radio selection for this contact");
		// RSID is a Radio-only control. Ignore stale/queued UI updates for Phone backends.
		if (!(backend instanceof RadioModemBackend)) return;
		backend.rsid(receive, enabled);
	}

	public synchronized void pauseRadio(int contactId)
	{
		requireRadio(contactId);
		backend.pause();
	}

	public synchronized void resumeRadio(int contactId)
	{
		if (!hasSelectionFor(contactId)) throw new IllegalStateException("No active Radio selection for this contact");
		if (backend.busy()) throw new IllegalStateException("Transmitter busy");
		backend.resume();
	}

	public synchronized void stopFromChat()
	{
		// Preserve the chat's existing receive-only service-stop condition.
		if (backend != null && backend.state() == ModemBackend.State.RECEIVING && GS.gI().isProcessorON())
		{
			stopRadio();
			stopSelf();
		}
	}

	public synchronized ModemBackend.State stopRadio()
	{
		logSelection("service_stop_requested", selection == null ? null : selection.modeName(), "destroyed=" + destroyed + " backend=" + (backend == null ? "none" : backend.getClass().getSimpleName()));
		shuttingDown = true;
		uiCallBack = null;
		uiContactId = -1;
		completionListener.clear();
		if (transitionPending) return ModemBackend.State.STOP_PENDING; // Transition worker already owns the old backend.
		if (backend == null) { releaseBackendSlot(); return ModemBackend.State.IDLE; }
		if (radioCleanup == null)
		{
			final ModemBackend stoppingBackend = backend;
			stoppingBackend.stop();
			radioCleanup = new Thread(() ->
			{
				try
				{
					boolean pending;
					try { pending = stoppingBackend.awaitStop(RADIO_STOP_DEADLINE_MS) == ModemBackend.State.STOP_PENDING; }
					catch (InterruptedException interrupted) { pending = true; }
					if (pending)
					{
						stopDeadlineExpired = true;
						// Same cleanup owner waits for actual exit; timeout grants no release/start permission.
						for (;;)
						{
							try { stoppingBackend.awaitStop(0); break; }
							catch (InterruptedException interrupted) { /* Still the sole cleanup owner. */ }
						}
					}
					if (!(stoppingBackend instanceof RadioModemBackend)) Modem.setPhoneBackendActive(false);
					releaseBackendSlot();
				}
				catch (RuntimeException | LinkageError failure)
				{
					// Keep exclusive ownership on cleanup failure; never publish a false STOPPED.
					synchronized (Processor.this) { selectionError = "Radio shutdown incomplete: " + failure.getMessage(); }
				}
			}, "SCVA-Radio-Shutdown");
			radioCleanup.start();
		}
		return backend.state();
	}

	public boolean radioStopDeadlineExpired() { return stopDeadlineExpired; }

	// Binder class
	public class LocalBinder extends Binder
	{
		public Processor getService()
		{
			return Processor.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	// Example method
	public String getMessage()
	{
		Utils.println("Service Connected");
		return "Service Connected";
	}
}
