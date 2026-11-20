package com.SCVA;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.SystemClock;

import com.SCVA.Interfaces.ModemBackend;

/** Processor-owned, bounded GGWave backend. This is not a fldigi modem. */
public final class GgWaveBackend implements ModemBackend {
    /** Utils.println writes to the legacy log file; logging must never fail a modem operation. */
    private static void trace(String tag, String message) {
        try { com.SCVA.Utils.Utils.println(tag, message); } catch (RuntimeException ignored) { }
    }

    private void publishProgress(long operationId, int percent) {
        try { events.onProgress(this, operationId, percent); }
        catch (RuntimeException callbackFailure) { trace("GGWAVE_TX", "stage=progress_callback result=ignored exception=" + callbackFailure.getClass().getSimpleName()); }
    }

    public interface Listener {
        void onText(GgWaveBackend source, ReceivedText message, long operationId);
        void onProgress(GgWaveBackend source, long operationId, int percent);
        void onSpectrum(GgWaveBackend source, float[] levelsDbfs, int sampleRate, int fftSize);
        void onCpuLoad(GgWaveBackend source, int percent);
        void onPlaybackComplete(GgWaveBackend source, String prepared);
        void onCancelled(GgWaveBackend source);
        void onFailure(GgWaveBackend source, String message);
    }

    /** Complete, strictly validated receive value. Format controls downstream storage semantics. */
    public static final class ReceivedText {
        public enum Format { SCVA_PREPARED, RAW_PLAINTEXT, REJECTED }
        public final Format format;
        public final String reason;
        private final byte[] bytes;

        ReceivedText(Format format, byte[] bytes, String reason) {
            this.format = format; this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length); this.reason = reason;
        }
        public byte[] bytes() { return bytes == null ? null : Arrays.copyOf(bytes, bytes.length); }
        public String text() { return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8); }
        public boolean accepted() { return format != Format.REJECTED; }
    }

    private static final String MODE = "GGWave";
    private static final int RATE = 48000;
    private static final int FRAME = 1024;
    private static final int MAX_TEXT = 55;
    private static final int MAX_PREPARED = GgWavePacket.MAX_PREPARED_BYTES;
    private static final int RX = 1, TX = 2;
    private static final int WRITE_BLOCK = 1024;
    // Give the continuous RX decoder a clean reacquisition interval between
    // independently marked GW fragments. Single-packet sends are unchanged.
    private static final int INTER_FRAGMENT_SILENCE_SAMPLES = RATE / 2;

    static { System.loadLibrary("c++_shared"); System.loadLibrary("SCVA_Modem_Interface"); }
    private static native long nativeCreate(int direction, String protocolToken, String[] receiveProtocolTokens);
    private static native void nativeClose(long handle);
    private static native short[] nativeEncode(long handle, byte[] packet);
    private static native byte[][] nativeFeed(long handle, short[] samples, int count);

    private final Context appContext;
    private final Listener events;
    private final GgWaveSpectrumAnalyzer spectrumAnalyzer = new GgWaveSpectrumAnalyzer();
    private volatile State state = State.IDLE;
    private volatile boolean stopping, cancelRequested, pauseRequested;
    private long rxCodec, txCodec;
    private AudioRecord record;
    private AudioTrack track;
    private Thread rxWorker, txWorker;
    private BackendSelection selection;
    private long rxPendingSamples;
    private int rxPendingFeeds, rxCodecRejects;
    private long lastRxSummaryMs;
    private long rxCodecElapsedMs;
    // RX is serialized by receiveLoop; assembly lives only as long as this selected backend.
    private final GgWavePacket.Reassembler rxReassembler = new GgWavePacket.Reassembler();

    public GgWaveBackend(Context context, Listener listener) {
        if (context == null || listener == null) throw new IllegalArgumentException("GGWave owner is unavailable");
        appContext = context.getApplicationContext();
        events = listener;
    }

    @Override public synchronized State state() { return stopping ? State.STOP_PENDING : state; }
    @Override public synchronized boolean busy() { return txWorker != null && txWorker.isAlive(); }

    @Override public synchronized void apply(BackendSelection requested, boolean image) {
        if (stopping || state == State.STOPPED) throw new IllegalStateException("GGWave is stopping");
        if (requested == null || !MODE.equals(requested.modeName()) || requested.configuration().getId() == 9 || image)
            throw new IllegalArgumentException("GGWave requires a non-special GGWave text configuration");
        if (busy()) throw new IllegalStateException("GGWave is busy");
        if (rxCodec == 0 || txCodec == 0) {
            String protocol = requested.ggWaveSpeed();
            if (!BackendSelection.isKnownGgWaveSpeed(protocol)) throw new IllegalArgumentException("Unknown GGWave protocol");
            String[] receiveProtocols = requested.ggWaveRxProtocols().split(",", -1);
            if (!BackendSelection.areKnownGgWaveRxProtocols(requested.ggWaveRxProtocols())) throw new IllegalArgumentException("Invalid GGWave receive protocol list");
            long rx = nativeCreate(RX, protocol, receiveProtocols);
            if (rx == 0) throw new IllegalStateException("GGWave receive profile preparation failed");
            long tx = nativeCreate(TX, protocol, new String[0]);
            if (tx == 0) { nativeClose(rx); throw new IllegalStateException("GGWave transmit profile preparation failed"); }
            rxCodec = rx; txCodec = tx;
        }
        selection = requested;
        state = State.SELECTED;
        GgWaveDiagnostics.event(appContext, "phone.selection", 0, "local", requested.ggWaveSpeed(), "\"state\":\"selected\"");
    }

    @Override public synchronized void receive() {
        if (stopping || rxCodec == 0 || txCodec == 0) throw new IllegalStateException("GGWave is not prepared");
        if (rxWorker != null && rxWorker.isAlive()) return;
        prepareAudio();
        try { record.startRecording(); }
        catch (RuntimeException failure) { closeAudioAfterStartFailure(); throw failure; }
        if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            closeAudioAfterStartFailure(); throw new IllegalStateException("48 kHz microphone capture did not start");
        }
        pauseRequested = false;
        state = State.RECEIVING;
        rxWorker = new Thread(this::receiveLoop, "SCVA-GGWave-RX-48k");
        rxWorker.start();
    }

    private void prepareAudio() {
        int inMin = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int outMin = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (inMin <= 0 || outMin <= 0) throw new IllegalStateException("48 kHz mono PCM16 audio is unavailable");
        if (record == null || record.getState() != AudioRecord.STATE_INITIALIZED) {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(inMin, FRAME * 8));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) { record.release(); record = null; throw new IllegalStateException("48 kHz microphone initialization failed"); }
        }
        if (record.getSampleRate() != RATE || record.getChannelCount() != 1 || record.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT)
            throw new IllegalStateException("Microphone did not provide 48 kHz mono PCM16");
        if (track == null || track.getState() != AudioTrack.STATE_INITIALIZED) {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(outMin, FRAME * 8 * 2), AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) { track.release(); track = null; throw new IllegalStateException("48 kHz playback initialization failed"); }
        }
        if (track.getSampleRate() != RATE || track.getChannelCount() != 1 || track.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT)
            throw new IllegalStateException("Playback did not provide 48 kHz mono PCM16");
    }

    private void closeAudioAfterStartFailure() {
        if (record != null) { try { record.release(); } catch (RuntimeException ignored) {} record = null; }
        if (track != null) { try { track.release(); } catch (RuntimeException ignored) {} track = null; }
    }

    private static int maxPacketLength(List<byte[]> packets) {
        int maximum = 0;
        for (byte[] packet : packets) if (packet != null && packet.length > maximum) maximum = packet.length;
        return maximum;
    }

    private void receiveLoop() {
        short[] capture = new short[4096];
        try {
            while (!stopping && !pauseRequested) {
                int n = record.read(capture, 0, capture.length, AudioRecord.READ_BLOCKING);
                if (n > 0 && !stopping && !pauseRequested) {
                    long processingStartedNs = System.nanoTime();
                    long feedStarted = SystemClock.elapsedRealtime();
                    byte[][] results = nativeFeed(rxCodec, capture, n);
                    long feedElapsed = SystemClock.elapsedRealtime() - feedStarted;
                    rxCodecElapsedMs += feedElapsed;
                    try {
                        spectrumAnalyzer.accept(capture, n, levels -> {
                            if (!stopping && !pauseRequested) {
                                try { events.onSpectrum(this, levels, RATE, GgWaveSpectrumAnalyzer.FFT_SIZE); }
                                catch (RuntimeException ignored) { /* Visualization must not interrupt audio decoding. */ }
                            }
                        });
                    } catch (RuntimeException ignored) { /* A failed spectrum frame does not reject captured audio. */ }
                    long processingNs = Math.max(0L, System.nanoTime() - processingStartedNs);
                    long audioBlockNs = (long) n * 1000000000L / RATE;
                    int cpuPercent = audioBlockNs <= 0 ? 0 : (int) Math.max(0, Math.min(100,
                            Math.round((processingNs * 100.0) / audioBlockNs)));
                    try { events.onCpuLoad(this, cpuPercent); }
                    catch (RuntimeException ignored) { /* UI metrics must not interrupt audio decoding. */ }
                    int status = results != null && results.length > 0 && results[0] != null && results[0].length == 1 ? results[0][0] & 0xff : 2;
                    if (status == 0) { rxPendingFeeds++; rxPendingSamples += n; }
                    if (status == 2) { rxCodecRejects++; rxPendingFeeds++; rxPendingSamples += n; }
                    long now = SystemClock.elapsedRealtime();
                    if ((rxPendingFeeds >= 256 || now - lastRxSummaryMs >= 30000) && rxPendingFeeds > 0) {
                        trace("GGWAVE_RX", "stage=codec_summary speed=" + speed() + " pendingFeeds=" + rxPendingFeeds + " pendingSamples=" + rxPendingSamples + " codecRejects=" + rxCodecRejects + " codecMs=" + rxCodecElapsedMs);
                        GgWaveDiagnostics.event(appContext, "phone.rx.codec", 0, "rx", speed(),
                                "\"outcome\":\"aggregate\",\"pendingFeeds\":" + rxPendingFeeds + ",\"pendingSamples\":" + rxPendingSamples + ",\"codecRejects\":" + rxCodecRejects + ",\"codecMs\":" + rxCodecElapsedMs);
                        rxPendingFeeds = 0; rxPendingSamples = 0; rxCodecRejects = 0; rxCodecElapsedMs = 0; lastRxSummaryMs = now;
                    }
                    if (status == 1 && results != null) {
                        for (int i = 1; i < results.length; i++) {
                            byte[] packet = results[i];
                            long operationId = GgWaveDiagnostics.nextOperationId();
                            trace("GGWAVE_RX", "stage=codec_packet op=" + operationId + " speed=" + speed() + " packetBytes=" + (packet == null ? 0 : packet.length));
                            GgWaveDiagnostics.event(appContext, "phone.rx.codec", operationId, "rx", speed(),
                                    "\"outcome\":\"codec_packet_received\",\"packetBytes\":" + (packet == null ? 0 : packet.length)
                                    + ",\"pendingFeeds\":" + rxPendingFeeds + ",\"pendingSamples\":" + rxPendingSamples + ",\"codecRejects\":" + rxCodecRejects + ",\"codecMs\":" + rxCodecElapsedMs);
                            rxPendingFeeds = 0; rxPendingSamples = 0; rxCodecRejects = 0; rxCodecElapsedMs = 0;
                            GgWavePacket.ReceiveResult receiveResult = GgWavePacket.acceptReceived(packet, rxReassembler, SystemClock.elapsedRealtime());
                            ReceivedText parsed = receiveResult.message;
                            trace("GGWAVE_RX", "stage=packet_parse op=" + operationId + " result=" + receiveResult.reason + " pending=" + receiveResult.pending + " " + GgWavePacket.fragmentSummary(packet) + " messageBytes=" + (parsed == null || !parsed.accepted() ? 0 : parsed.bytes().length));
                            GgWaveDiagnostics.event(appContext, "phone.rx.packet", operationId, "rx", speed(),
                                    "\"outcome\":\"" + receiveResult.reason + "\",\"packetBytes\":" + (packet == null ? 0 : packet.length));
                            if (parsed != null && parsed.accepted() && !stopping && !pauseRequested) {
                                String received = parsed.text();
                                String stage = parsed.format == ReceivedText.Format.RAW_PLAINTEXT ? "phone.rx.raw" : "phone.rx.prepared";
                                trace("GGWAVE_RX", "stage=application_delivery op=" + operationId + " format=" + parsed.format + " preparedBytes=" + parsed.bytes().length);
                                GgWaveDiagnostics.event(appContext, stage, operationId, "rx", speed(), "\"outcome\":\"valid\",\"format\":\"" + parsed.format + "\",\"textBytes\":" + parsed.bytes().length);
                                GgWaveDiagnostics.content(appContext, stage, operationId, "receivedText", received);
                                events.onText(this, parsed, operationId);
                            }
                        }
                    }
                    synchronized (this) { if (!stopping && !pauseRequested) state = State.RECEIVING; }
                } else if (n < 0 && !stopping && !pauseRequested) {
                    trace("GGWAVE_RX", "stage=audio_capture result=failed readResult=" + n);
                    events.onFailure(this, "GGWave microphone read failed: " + n);
                    break;
                }
            }
        } catch (RuntimeException failure) {
            trace("GGWAVE_RX", "stage=receive_worker result=failed exception=" + failure.getClass().getSimpleName());
            if (!stopping && !pauseRequested) events.onFailure(this, "GGWave receive failed: " + failure.getMessage());
        } finally {
            synchronized (this) {
                if (!stopping && pauseRequested) state = State.PAUSED;
                else if (!stopping) state = State.SELECTED;
                notifyAll();
            }
        }
    }

    @Override public synchronized void pause() {
        if (stopping) return;
        pauseRequested = true;
        stopRecordToWakeReader();
        state = State.PAUSED;
    }
    @Override public synchronized void resume() { if (stopping) throw new IllegalStateException("GGWave is stopping"); receive(); }

    private void pauseCaptureForTransmit() throws InterruptedException {
        Thread worker;
        synchronized (this) {
            if (stopping) throw new IllegalStateException("GGWave is stopping");
            pauseRequested = true;
            stopRecordToWakeReader();
            worker = rxWorker;
        }
        if (worker != null && worker != Thread.currentThread()) worker.join(3000);
        if (worker != null && worker.isAlive()) throw new IllegalStateException("GGWave receive worker did not pause");
        synchronized (this) { state = State.PAUSED; }
    }

    private void stopRecordToWakeReader() {
        AudioRecord current = record;
        if (current != null && current.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            try { current.stop(); } catch (IllegalStateException ignored) { }
        }
    }

    @Override public void text(String prepared) { text(prepared, GgWaveDiagnostics.nextOperationId()); }

    public void text(String prepared, long operationId) { text(prepared, operationId, false); }

    public void text(String prepared, long operationId, boolean encrypted) {
        final byte[] bytes = preparedBytes(prepared);
        int maximum = MAX_PREPARED;
        if (bytes == null || bytes.length < 1 || bytes.length > maximum || !GgWavePacket.strictUtf8(bytes) || !prepared.endsWith("§")) {
            trace("GGWAVE_TX", "stage=backend_admission op=" + operationId + " result=rejected bytes=" + (bytes == null ? -1 : bytes.length) + " max=" + maximum + " encrypted=" + encrypted + " terminator=" + (prepared != null && prepared.endsWith("§")));
            throw new IllegalArgumentException("GGWave supports 1–" + maximum + " prepared UTF-8 bytes including the § terminator for this message format");
        }
        final byte[] immutable = Arrays.copyOf(bytes, bytes.length);
        synchronized (this) {
            if (stopping || state != State.RECEIVING && state != State.PAUSED) {
                trace("GGWAVE_TX", "stage=backend_admission op=" + operationId + " result=not_ready state=" + state);
                throw new IllegalStateException("GGWave is not ready");
            }
            if (busy()) {
                trace("GGWAVE_TX", "stage=backend_admission op=" + operationId + " result=busy");
                throw new IllegalStateException("GGWave transmitter is busy");
            }
            cancelRequested = false;
            trace("GGWAVE_TX", "stage=queued op=" + operationId + " speed=" + speed() + " preparedBytes=" + bytes.length + " encrypted=" + encrypted);
            txWorker = new Thread(() -> transmitWorker(immutable, prepared, operationId, encrypted), "SCVA-GGWave-TX-48k");
            txWorker.start();
        }
    }

    private void transmitWorker(byte[] payload, String prepared, long operationId, boolean encrypted) {
        long started = SystemClock.elapsedRealtime();
        trace("GGWAVE_TX", "stage=worker_start op=" + operationId + " speed=" + speed() + " preparedBytes=" + payload.length + " encrypted=" + encrypted);
        try {
            pauseCaptureForTransmit();
            trace("GGWAVE_TX", "stage=rx_paused op=" + operationId);
            if (stopping || cancelRequested) throw new Cancelled();
            // Short plaintext keeps its interoperable raw representation. Any long
            // prepared message uses the bounded GW2 fragment envelope; encryption
            // affects the payload bytes, not whether fragmentation is required.
            List<byte[]> packets = GgWavePacket.packetizeText(payload, encrypted);
            String packetFormat = payload.length > MAX_TEXT ? "GW2" : encrypted ? "GW1" : "raw";
            String packetOutcome = payload.length > MAX_TEXT ? "scva_gw_fragments" : encrypted ? "scva_gw_envelope" : "raw_utf8";
            trace("GGWAVE_TX", "stage=packetized op=" + operationId + " format=" + packetFormat + " preparedBytes=" + payload.length + " packetCount=" + packets.size() + " maxPacketBytes=" + maxPacketLength(packets));
            GgWaveDiagnostics.event(appContext, "phone.tx.packet", operationId, "tx", speed(), "\"outcome\":\"" + packetOutcome + "\",\"payloadBytes\":" + payload.length + ",\"packetCount\":" + packets.size());
            AudioTrack output = track;
            if (output == null || output.getState() != AudioTrack.STATE_INITIALIZED) { trace("GGWAVE_TX", "stage=audio_sink result=unavailable"); throw new IllegalStateException("48 kHz playback is unavailable"); }
            output.flush();
            final long playbackStart = ((long) output.getPlaybackHeadPosition()) & 0xffffffffL;
            output.play();
            int written = 0;
            int interPacketSilenceWritten = 0;
            final short[] silenceBlock = new short[WRITE_BLOCK];
            int lastProgressPercent = 0;
            long writeStarted = SystemClock.elapsedRealtime();
            long encodeElapsedTotal = 0;
            for (int packetIndex = 0; packetIndex < packets.size(); packetIndex++) {
                if (stopping || cancelRequested) throw new Cancelled();
                byte[] packet = packets.get(packetIndex);
                long encodeStarted = SystemClock.elapsedRealtime();
                short[] pcm = nativeEncode(txCodec, packet);
                long encodeElapsed = SystemClock.elapsedRealtime() - encodeStarted;
                encodeElapsedTotal += encodeElapsed;
                if (pcm == null || pcm.length == 0) {
                    trace("GGWAVE_TX", "stage=encode op=" + operationId + " fragment=" + (packetIndex + 1) + "/" + packets.size() + " result=failed packetBytes=" + packet.length + " elapsedMs=" + encodeElapsed);
                    throw new IllegalStateException("GGWave encoding failed");
                }
                trace("GGWAVE_TX", "stage=encode op=" + operationId + " fragment=" + (packetIndex + 1) + "/" + packets.size() + " result=ok packetBytes=" + packet.length + " pcmSamples=" + pcm.length + " elapsedMs=" + encodeElapsed);
                GgWaveDiagnostics.event(appContext, "phone.tx.audio", operationId, "tx", speed(), "\"outcome\":\"fragment_encoded\",\"fragment\":" + (packetIndex + 1) + ",\"packetCount\":" + packets.size() + ",\"packetBytes\":" + packet.length + ",\"pcmSamples\":" + pcm.length + ",\"encodeMs\":" + encodeElapsed);
                if (stopping || cancelRequested) throw new Cancelled();
                int offset = 0;
                int fragmentWritten = 0;
                long fragmentWriteStarted = SystemClock.elapsedRealtime();
                while (offset < pcm.length) {
                    if (stopping || cancelRequested) throw new Cancelled();
                    int n = Math.min(WRITE_BLOCK, pcm.length - offset);
                    int accepted = output.write(pcm, offset, n, AudioTrack.WRITE_BLOCKING);
                    if (accepted <= 0) { trace("GGWAVE_TX", "stage=audio_write op=" + operationId + " fragment=" + (packetIndex + 1) + "/" + packets.size() + " result=rejected requestedSamples=" + n + " accepted=" + accepted); throw new IllegalStateException("GGWave audio sink rejected samples: " + accepted); }
                    offset += accepted;
                    written += accepted;
                    fragmentWritten += accepted;
                    int progressPercent = Math.min(99, (packetIndex * 100 + (int) ((long) offset * 100 / pcm.length)) / packets.size());
                    if (progressPercent > lastProgressPercent) {
                        lastProgressPercent = progressPercent;
                        publishProgress(operationId, progressPercent);
                    }
                }
                trace("GGWAVE_TX", "stage=audio_write op=" + operationId + " fragment=" + (packetIndex + 1) + "/" + packets.size() + " acceptedSamples=" + fragmentWritten + " elapsedMs=" + (SystemClock.elapsedRealtime() - fragmentWriteStarted));
                if (packetIndex + 1 < packets.size()) {
                    int gapWritten = 0;
                    long gapStarted = SystemClock.elapsedRealtime();
                    while (gapWritten < INTER_FRAGMENT_SILENCE_SAMPLES) {
                        if (stopping || cancelRequested) throw new Cancelled();
                        int requested = Math.min(WRITE_BLOCK, INTER_FRAGMENT_SILENCE_SAMPLES - gapWritten);
                        int accepted = output.write(silenceBlock, 0, requested, AudioTrack.WRITE_BLOCKING);
                        if (accepted <= 0) {
                            trace("GGWAVE_TX", "stage=inter_fragment_silence op=" + operationId + " after=" + (packetIndex + 1) + "/" + packets.size() + " result=rejected accepted=" + accepted);
                            throw new IllegalStateException("GGWave inter-fragment silence write failed: " + accepted);
                        }
                        gapWritten += accepted;
                        written += accepted;
                        interPacketSilenceWritten += accepted;
                    }
                    trace("GGWAVE_TX", "stage=inter_fragment_silence op=" + operationId + " after=" + (packetIndex + 1) + "/" + packets.size() + " result=written samples=" + gapWritten + " elapsedMs=" + (SystemClock.elapsedRealtime() - gapStarted));
                }
            }
            long deadline = SystemClock.elapsedRealtime() + written * 1000L / RATE + 10000L;
            while ((((((long) output.getPlaybackHeadPosition()) & 0xffffffffL) - playbackStart) & 0xffffffffL) < written) {
                if (stopping || cancelRequested) throw new Cancelled();
                if (SystemClock.elapsedRealtime() >= deadline) { trace("GGWAVE_TX", "stage=playback_drain op=" + operationId + " result=timeout expectedSamples=" + written); throw new IllegalStateException("GGWave playback drain timed out"); }
                Thread.sleep(10);
            }
            trace("GGWAVE_TX", "stage=playback_drain op=" + operationId + " result=complete packets=" + packets.size() + " acceptedSamples=" + written + " interPacketSilenceSamples=" + interPacketSilenceWritten + " elapsedMs=" + (SystemClock.elapsedRealtime() - writeStarted));
            output.pause(); output.flush();
            if (!stopping) { publishProgress(operationId, 100); events.onPlaybackComplete(this, prepared); }
            GgWaveDiagnostics.event(appContext, "phone.tx.audio", operationId, "tx", speed(), "\"outcome\":\"playback_complete\",\"packetCount\":" + packets.size() + ",\"pcmSamples\":" + written + ",\"encodeMs\":" + encodeElapsedTotal + ",\"writeAndDrainMs\":" + (SystemClock.elapsedRealtime() - writeStarted) + ",\"totalMs\":" + (SystemClock.elapsedRealtime() - started));
        } catch (Cancelled cancelled) {
            trace("GGWAVE_TX", "stage=worker_end op=" + operationId + " result=cancelled");
            GgWaveDiagnostics.event(appContext, "phone.tx.audio", operationId, "tx", speed(), "\"outcome\":\"cancelled\"");
            if (!stopping) events.onCancelled(this); // Cancellation is not playback completion.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            trace("GGWAVE_TX", "stage=worker_end op=" + operationId + " result=interrupted");
            if (!stopping) events.onFailure(this, "GGWave transmission was interrupted");
        } catch (RuntimeException failure) {
            trace("GGWAVE_TX", "stage=worker_end op=" + operationId + " result=failed exception=" + failure.getClass().getSimpleName());
            GgWaveDiagnostics.event(appContext, "phone.tx.audio", operationId, "tx", speed(), "\"outcome\":\"failed\",\"error\":\"audio_or_codec\"");
            if (!stopping) events.onFailure(this, "GGWave transmission failed: " + failure.getMessage());
        } finally {
            synchronized (this) {
                txWorker = null;
                if (!stopping) {
                    cancelRequested = false;
                    try { receive(); } catch (RuntimeException failure) { state = State.FAILED; events.onFailure(this, failure.getMessage()); }
                }
                trace("GGWAVE_TX", "stage=worker_quiesced op=" + operationId + " stopping=" + stopping + " state=" + state);
                notifyAll();
            }
        }
    }

    @Override public synchronized State stop() {
        if (state == State.STOPPED) return state;
        stopping = true; cancelRequested = true; pauseRequested = true;
        stopRecordToWakeReader();
        AudioTrack current = track;
        if (current != null) { try { current.stop(); } catch (IllegalStateException ignored) { } }
        state = State.STOP_PENDING;
        return state;
    }

    @Override public State awaitStop(long timeoutMillis) throws InterruptedException {
        synchronized (this) {
            if (state == State.STOPPED) return state;
            if (!stopping) throw new IllegalStateException("GGWave stop was not requested");
        }
        final long deadline = timeoutMillis <= 0 ? Long.MAX_VALUE : SystemClock.elapsedRealtime() + timeoutMillis;
        for (;;) {
            Thread rx, tx;
            synchronized (this) { rx = rxWorker; tx = txWorker; }
            long remaining = deadline == Long.MAX_VALUE ? 0 : deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0 && deadline != Long.MAX_VALUE) return State.STOP_PENDING;
            if (rx != null && rx != Thread.currentThread()) rx.join(deadline == Long.MAX_VALUE ? 0 : Math.max(1, remaining));
            if (rx != null && rx.isAlive()) return State.STOP_PENDING;
            remaining = deadline == Long.MAX_VALUE ? 0 : deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0 && deadline != Long.MAX_VALUE) return State.STOP_PENDING;
            if (tx != null && tx != Thread.currentThread()) tx.join(deadline == Long.MAX_VALUE ? 0 : Math.max(1, remaining));
            if (tx != null && tx.isAlive()) return State.STOP_PENDING;
            break;
        }
        synchronized (this) {
            if (state == State.STOPPED) return state;
            closeAudioAfterStartFailure();
            if (rxCodec != 0) { nativeClose(rxCodec); rxCodec = 0; }
            if (txCodec != 0) { nativeClose(txCodec); txCodec = 0; }
            state = State.STOPPED;
            notifyAll();
            return state;
        }
    }

    @Override public synchronized void cancel() { cancelRequested = true; }
    @Override public void image(String prepared) { throw new IllegalArgumentException("GGWave trial supports ordinary text only"); }
    @Override public void key(String prepared) { throw new IllegalArgumentException("GGWave trial does not support key transfer"); }
    @Override public void binary(byte[] data) { throw new IllegalArgumentException("GGWave trial does not support binary messages"); }
    @Override public void diagnostic(int sequence) { throw new IllegalArgumentException("GGWave trial does not support Radio diagnostics"); }
    @Override public void readableDiagnostic(int sequence) { throw new IllegalArgumentException("GGWave trial does not support Radio diagnostics"); }
    @Override public void tune() { throw new IllegalArgumentException("Tune is a Radio-only operation"); }
    @Override public void frequency(double value, String savedValue) { throw new IllegalArgumentException("Frequency is a Radio-only setting"); }
    @Override public void rsid(boolean receive, boolean enabled) { throw new IllegalArgumentException("RSID is not used by GGWave"); }
    @Override public void listener(com.SCVA.Interfaces.CallBack callback) { }

    static byte[] preparedBytes(String text) {
        if (text == null) return null;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Prepared message is not valid Unicode", invalid);
        }
    }

    private static final class Cancelled extends RuntimeException { private static final long serialVersionUID = 1L; }

    private synchronized String speed() { return selection == null ? "DT_FASTEST" : selection.ggWaveSpeed(); }
}

/** GW packet and bounded raw-text parser, isolated from SCVA V1/V2 transport code. */
final class GgWavePacket {
    static final int MAX_TEXT = 55;
    static final int MAX_PREPARED_BYTES = 4096;
    private static final int FRAGMENT_HEADER = 21;
    private static final int CRC_BYTES = 4;
    private static final int MAX_PACKET = 64;
    static final int FRAGMENT_DATA = MAX_PACKET - FRAGMENT_HEADER - CRC_BYTES; // 39 bytes
    private static final int VERSION_SINGLE = 1;
    private static final int VERSION_FRAGMENT = 2;
    private static final int TYPE_PREPARED_TEXT = 1;
    private static final long ASSEMBLY_IDLE_MS = 10L * 60L * 1000L;
    private static final long ASSEMBLY_LIFETIME_MS = 60L * 60L * 1000L;
    private static final int MAX_ASSEMBLIES = 4;
    private static final int MAX_COMPLETED_IDS = 64;
    private static final SecureRandom MESSAGE_IDS = new SecureRandom();

    static byte[] packText(byte[] text) {
        if (text == null || text.length < 1 || text.length > MAX_TEXT || !strictUtf8(text)
                || !new String(text, StandardCharsets.UTF_8).endsWith("§"))
            throw new IllegalArgumentException("GGWave packet requires 1–55 prepared UTF-8 bytes including §");
        byte[] out = new byte[text.length + 9];
        out[0] = 0x47; out[1] = 0x57; out[2] = 1; out[3] = 1; out[4] = (byte) text.length;
        System.arraycopy(text, 0, out, 5, text.length);
        long crc = crc32c(out, 0, text.length + 5);
        out[out.length-4] = (byte) (crc >>> 24); out[out.length-3] = (byte) (crc >>> 16);
        out[out.length-2] = (byte) (crc >>> 8); out[out.length-1] = (byte) crc;
        return out;
    }

    /** Selects raw short plaintext, the legacy short encrypted envelope, or GW2 for any long prepared text. */
    static List<byte[]> packetizeText(byte[] text, boolean encrypted) {
        if (text == null || text.length < 1 || text.length > MAX_PREPARED_BYTES)
            throw new IllegalArgumentException("GGWave prepared text is outside the supported byte range");
        if (text.length > MAX_TEXT) return packTextFragments(text);
        if (!encrypted) return java.util.Collections.singletonList(Arrays.copyOf(text, text.length));
        return java.util.Collections.singletonList(packText(text));
    }

    static byte[] unpackText(byte[] packet) {
        ParseResult result = parse(packet);
        return result.text;
    }

    /** Legacy test/caller view retained for the original GW-only packet contract. */
    static ParseResult parse(byte[] packet) {
        if (packet == null) return new ParseResult(null, "null_packet");
        if (packet.length < 10 || packet.length > 64) return new ParseResult(null, "packet_length");
        if (packet[0] != 0x47 || packet[1] != 0x57) return new ParseResult(null, "magic");
        if (packet[2] != 1) return new ParseResult(null, "version");
        if (packet[3] != 1) return new ParseResult(null, "type");
        int length = packet[4] & 0xff;
        if (length < 1 || length > MAX_TEXT || packet.length != length + 9) return new ParseResult(null, "length");
        long expected = ((packet[packet.length-4] & 0xffL) << 24) | ((packet[packet.length-3] & 0xffL) << 16) | ((packet[packet.length-2] & 0xffL) << 8) | (packet[packet.length-1] & 0xffL);
        if (crc32c(packet, 0, packet.length - 4) != expected) return new ParseResult(null, "crc");
        byte[] text = Arrays.copyOfRange(packet, 5, 5 + length);
        if (!strictUtf8(text)) return new ParseResult(null, "utf8");
        if (!new String(text, StandardCharsets.UTF_8).endsWith("§")) return new ParseResult(null, "terminator");
        return new ParseResult(text, "valid");
    }

    static GgWaveBackend.ReceivedText parseReceived(byte[] packet) {
        ReceiveResult result = acceptReceived(packet, new Reassembler(), 0);
        return result.message == null ? new GgWaveBackend.ReceivedText(GgWaveBackend.ReceivedText.Format.REJECTED, null, result.reason) : result.message;
    }

    /** Stateful receive entry used by the serialized backend RX worker. */
    static ReceiveResult acceptReceived(byte[] packet, Reassembler reassembler, long nowMs) {
        if (packet == null) return receiveRejected("reject_envelope");
        // The two-byte discriminator is reserved. Once recognized, malformed GW is never raw text.
        if (packet.length >= 2 && packet[0] == 0x47 && packet[1] == 0x57) {
            if (packet.length < 3) return receiveRejected("reject_envelope");
            int version = packet[2] & 0xff;
            if (version == VERSION_FRAGMENT) {
                if (reassembler == null) return receiveRejected("reject_fragment_state");
                if (packet.length < FRAGMENT_HEADER + 1 + CRC_BYTES || packet.length > MAX_PACKET)
                    return receiveRejected("reject_envelope");
                return reassembler.accept(packet, nowMs);
            }
            GgWaveBackend.ReceivedText envelope = parseEnvelope(packet);
            return new ReceiveResult(envelope, envelope.reason, false);
        }
        if (packet.length == 0 || packet.length > MAX_TEXT) return receiveRejected("reject_raw_length");
        if (!strictUtf8(packet)) return receiveRejected("reject_utf8");
        if (hasUnsupportedControl(packet)) return receiveRejected("reject_binary_or_control");
        byte[] applicationText = packet;
        if (packet.length >= 2 && packet[packet.length - 2] == (byte) 0xc2 && packet[packet.length - 1] == (byte) 0xa7) {
            applicationText = Arrays.copyOf(packet, packet.length - 2);
            if (applicationText.length == 0) return receiveRejected("reject_raw_length");
        }
        GgWaveBackend.ReceivedText raw = accepted(GgWaveBackend.ReceivedText.Format.RAW_PLAINTEXT, applicationText, "format_raw_utf8");
        return new ReceiveResult(raw, raw.reason, false);
    }

    static List<byte[]> packTextFragments(byte[] text) {
        if (text == null || text.length <= MAX_TEXT || text.length > MAX_PREPARED_BYTES || !strictUtf8(text)
                || !new String(text, StandardCharsets.UTF_8).endsWith("§"))
            throw new IllegalArgumentException("GGWave fragmented message requires 56–4096 prepared UTF-8 bytes including §");
        int count = (text.length + FRAGMENT_DATA - 1) / FRAGMENT_DATA;
        long id;
        do { id = MESSAGE_IDS.nextLong(); } while (id == 0);
        List<byte[]> packets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * FRAGMENT_DATA;
            int length = Math.min(FRAGMENT_DATA, text.length - offset);
            byte[] packet = new byte[FRAGMENT_HEADER + length + CRC_BYTES];
            packet[0] = 0x47; packet[1] = 0x57; packet[2] = VERSION_FRAGMENT; packet[3] = TYPE_PREPARED_TEXT;
            putLong(packet, 4, id);
            putInt(packet, 12, text.length);
            putShort(packet, 16, index);
            putShort(packet, 18, count);
            packet[20] = (byte) length;
            System.arraycopy(text, offset, packet, FRAGMENT_HEADER, length);
            putCrc(packet);
            packets.add(packet);
        }
        return packets;
    }

    static final class ReceiveResult {
        final GgWaveBackend.ReceivedText message;
        final String reason;
        final boolean pending;
        ReceiveResult(GgWaveBackend.ReceivedText message, String reason, boolean pending) {
            this.message = message; this.reason = reason; this.pending = pending;
        }
    }

    /** Bounded, per-backend fragment state. Caller serializes receive calls. */
    static final class Reassembler {
        private final LinkedHashMap<Long, Assembly> active = new LinkedHashMap<>();
        private final LinkedHashMap<Long, Long> completed = new LinkedHashMap<>();

        synchronized ReceiveResult accept(byte[] packet, long nowMs) {
            expire(nowMs);
            if (packet.length < FRAGMENT_HEADER + 1 + CRC_BYTES || packet.length > MAX_PACKET
                    || packet[0] != 0x47 || packet[1] != 0x57 || (packet[2] & 0xff) != VERSION_FRAGMENT
                    || (packet[3] & 0xff) != TYPE_PREPARED_TEXT) return receiveRejected("reject_fragment_header");
            int fragmentLength = packet[20] & 0xff;
            if (fragmentLength < 1 || fragmentLength > FRAGMENT_DATA || packet.length != FRAGMENT_HEADER + fragmentLength + CRC_BYTES
                    || !validCrc(packet)) return receiveRejected("reject_fragment_crc_or_length");
            long id = getLong(packet, 4);
            long totalLong = getInt(packet, 12) & 0xffffffffL;
            int index = getShort(packet, 16), count = getShort(packet, 18);
            if (id == 0 || totalLong <= MAX_TEXT || totalLong > MAX_PREPARED_BYTES) return receiveRejected("reject_fragment_total");
            int total = (int) totalLong;
            int expectedCount = (total + FRAGMENT_DATA - 1) / FRAGMENT_DATA;
            if (count != expectedCount || index < 0 || index >= count) return receiveRejected("reject_fragment_index");
            int expectedLength = Math.min(FRAGMENT_DATA, total - index * FRAGMENT_DATA);
            if (fragmentLength != expectedLength) return receiveRejected("reject_fragment_size");
            if (completed.containsKey(id)) return new ReceiveResult(null, "fragment_duplicate_completed", true);

            Assembly assembly = active.get(id);
            if (assembly == null) {
                if (active.size() >= MAX_ASSEMBLIES) return receiveRejected("reject_fragment_capacity");
                assembly = new Assembly(total, count, nowMs);
                active.put(id, assembly);
            } else if (assembly.total != total || assembly.count != count) {
                active.remove(id);
                return receiveRejected("reject_fragment_conflict");
            }
            byte[] data = Arrays.copyOfRange(packet, FRAGMENT_HEADER, FRAGMENT_HEADER + fragmentLength);
            if (assembly.parts[index] != null) {
                if (Arrays.equals(assembly.parts[index], data)) return new ReceiveResult(null, "fragment_duplicate", true);
                active.remove(id);
                return receiveRejected("reject_fragment_conflict");
            }
            assembly.parts[index] = data;
            assembly.received++;
            assembly.lastSeenMs = nowMs;
            if (assembly.received != assembly.count) return new ReceiveResult(null, "fragment_pending", true);

            byte[] message = new byte[assembly.total];
            int offset = 0;
            for (byte[] part : assembly.parts) {
                if (part == null || offset + part.length > message.length) { active.remove(id); return receiveRejected("reject_fragment_assembly"); }
                System.arraycopy(part, 0, message, offset, part.length);
                offset += part.length;
            }
            active.remove(id);
            if (offset != message.length || !strictUtf8(message) || !new String(message, StandardCharsets.UTF_8).endsWith("§"))
                return receiveRejected("reject_fragment_message");
            completed.put(id, nowMs);
            while (completed.size() > MAX_COMPLETED_IDS) completed.remove(completed.keySet().iterator().next());
            GgWaveBackend.ReceivedText result = accepted(GgWaveBackend.ReceivedText.Format.SCVA_PREPARED, message, "format_scva_gw_fragments");
            return new ReceiveResult(result, result.reason, false);
        }

        synchronized int activeCount() { return active.size(); }

        private void expire(long nowMs) {
            Iterator<Map.Entry<Long, Assembly>> it = active.entrySet().iterator();
            while (it.hasNext()) {
                Assembly a = it.next().getValue();
                if (nowMs - a.lastSeenMs > ASSEMBLY_IDLE_MS || nowMs - a.createdMs > ASSEMBLY_LIFETIME_MS) it.remove();
            }
            Iterator<Map.Entry<Long, Long>> done = completed.entrySet().iterator();
            while (done.hasNext()) if (nowMs - done.next().getValue() > ASSEMBLY_LIFETIME_MS) done.remove();
        }
    }

    private static final class Assembly {
        final int total, count;
        final byte[][] parts;
        final long createdMs;
        long lastSeenMs;
        int received;
        Assembly(int total, int count, long now) { this.total = total; this.count = count; this.parts = new byte[count][]; this.createdMs = this.lastSeenMs = now; }
    }

    private static ReceiveResult receiveRejected(String reason) {
        return new ReceiveResult(new GgWaveBackend.ReceivedText(GgWaveBackend.ReceivedText.Format.REJECTED, null, reason), reason, false);
    }

    static String fragmentSummary(byte[] packet) {
        if (packet == null || packet.length < FRAGMENT_HEADER || packet[0] != 0x47 || packet[1] != 0x57
                || (packet[2] & 0xff) != VERSION_FRAGMENT) return "fragment=none";
        int index = ((packet[16] & 0xff) << 8) | (packet[17] & 0xff);
        int count = ((packet[18] & 0xff) << 8) | (packet[19] & 0xff);
        return "fragment=" + (index + 1) + "/" + count + " fragmentBytes=" + (packet[20] & 0xff);
    }

    private static void putCrc(byte[] packet) {
        long crc = crc32c(packet, 0, packet.length - CRC_BYTES);
        int at = packet.length - CRC_BYTES;
        packet[at] = (byte)(crc >>> 24); packet[at + 1] = (byte)(crc >>> 16);
        packet[at + 2] = (byte)(crc >>> 8); packet[at + 3] = (byte)crc;
    }

    private static boolean validCrc(byte[] packet) {
        int at = packet.length - CRC_BYTES;
        long expected = ((packet[at] & 0xffL) << 24) | ((packet[at + 1] & 0xffL) << 16) | ((packet[at + 2] & 0xffL) << 8) | (packet[at + 3] & 0xffL);
        return crc32c(packet, 0, at) == expected;
    }

    private static void putLong(byte[] b, int at, long v) { for (int n = 7; n >= 0; --n) b[at + 7 - n] = (byte)(v >>> (n * 8)); }
    private static long getLong(byte[] b, int at) { long v = 0; for (int i = 0; i < 8; ++i) v = (v << 8) | (b[at + i] & 0xffL); return v; }
    private static void putInt(byte[] b, int at, int v) { b[at]=(byte)(v>>>24); b[at+1]=(byte)(v>>>16); b[at+2]=(byte)(v>>>8); b[at+3]=(byte)v; }
    private static int getInt(byte[] b, int at) { return (b[at]&255)<<24 | (b[at+1]&255)<<16 | (b[at+2]&255)<<8 | (b[at+3]&255); }
    private static void putShort(byte[] b, int at, int v) { b[at]=(byte)(v>>>8); b[at+1]=(byte)v; }
    private static int getShort(byte[] b, int at) { return (b[at]&255)<<8 | (b[at+1]&255); }

    private static GgWaveBackend.ReceivedText parseEnvelope(byte[] packet) {
        if (packet.length < 10 || packet.length > 64) return rejected("reject_envelope");
        if (packet[2] != 1) return rejected("reject_envelope");
        if (packet[3] != 1) return rejected("reject_envelope");
        int length = packet[4] & 0xff;
        if (length < 1 || length > MAX_TEXT || packet.length != length + 9) return rejected("reject_envelope");
        long expected = ((packet[packet.length-4] & 0xffL) << 24) | ((packet[packet.length-3] & 0xffL) << 16) | ((packet[packet.length-2] & 0xffL) << 8) | (packet[packet.length-1] & 0xffL);
        if (crc32c(packet, 0, packet.length - 4) != expected) return rejected("reject_envelope");
        byte[] text = Arrays.copyOfRange(packet, 5, 5 + length);
        if (!strictUtf8(text)) return rejected("reject_utf8");
        if (!new String(text, StandardCharsets.UTF_8).endsWith("§")) return rejected("reject_envelope");
        return accepted(GgWaveBackend.ReceivedText.Format.SCVA_PREPARED, text, "format_scva_gw");
    }

    private static GgWaveBackend.ReceivedText accepted(GgWaveBackend.ReceivedText.Format format, byte[] text, String reason) {
        return new GgWaveBackend.ReceivedText(format, text, reason);
    }

    private static GgWaveBackend.ReceivedText rejected(String reason) {
        return new GgWaveBackend.ReceivedText(GgWaveBackend.ReceivedText.Format.REJECTED, null, reason);
    }

    static final class ParseResult { final byte[] text; final String reason; ParseResult(byte[] text, String reason) { this.text = text; this.reason = reason; } }

    private static boolean hasUnsupportedControl(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (Character.getType(cp) == Character.CONTROL && cp != '\t' && cp != '\n' && cp != '\r') return true;
        }
        return false;
    }

    static boolean strictUtf8(byte[] bytes) {
        try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)); return true; }
        catch (CharacterCodingException invalid) { return false; }
    }

    private static long crc32c(byte[] bytes, int offset, int length) {
        long crc = 0xffffffffL;
        for (int i = offset; i < offset + length; ++i) {
            crc ^= bytes[i] & 0xff;
            for (int bit = 0; bit < 8; ++bit) crc = (crc >>> 1) ^ ((crc & 1) != 0 ? 0x82f63b78L : 0L);
        }
        return crc ^ 0xffffffffL;
    }
}
