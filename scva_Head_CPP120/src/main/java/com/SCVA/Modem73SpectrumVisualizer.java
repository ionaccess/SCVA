package com.SCVA;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** A disposable, bounded view of MODEM73 receive PCM. It never owns an audio device. */
final class Modem73SpectrumVisualizer {
    static final int SAMPLE_RATE = GgWaveSpectrumAnalyzer.SAMPLE_RATE;
    static final int FFT_SIZE = GgWaveSpectrumAnalyzer.FFT_SIZE;
    static final int QUEUE_CAPACITY = 4;
    private static final long MIN_UPDATE_NS = TimeUnit.MILLISECONDS.toNanos(100);

    interface Listener { void onSpectrum(float[] levelsDbfs, int sampleRate, int fftSize, long generation); }
    private static final class Chunk {
        final long generation;
        final float[] samples;
        Chunk(long generation, float[] samples) { this.generation = generation; this.samples = samples; }
    }

    private final ArrayBlockingQueue<Chunk> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong offeredChunks = new AtomicLong(), droppedChunks = new AtomicLong(), emittedFrames = new AtomicLong();
    private final Listener listener;
    private volatile boolean enabled, stopping;
    private Thread worker;

    Modem73SpectrumVisualizer(Listener listener) { this.listener = listener; }

    synchronized void start() {
        if (worker != null) return;
        stopping = false;
        worker = new Thread(this::run, "SCVA-MODEM73-SPECTRUM");
        worker.start();
    }

    void setEnabled(boolean value) {
        generation.incrementAndGet();
        enabled = value && !stopping;
        queue.clear();
    }

    long generation() { return generation.get(); }
    boolean enabled() { return enabled && !stopping; }
    long offeredChunks() { return offeredChunks.get(); }
    long droppedChunks() { return droppedChunks.get(); }
    long emittedFrames() { return emittedFrames.get(); }

    /** Called after decoder admission. A full queue drops view data without delaying decoding. */
    void offer(float[] samples, int count) {
        if (!enabled || stopping || samples == null || count <= 0 || count > samples.length) return;
        if (queue.remainingCapacity() == 0) { droppedChunks.incrementAndGet(); return; }
        long current = generation.get();
        float[] copy = Arrays.copyOf(samples, count);
        if (enabled && !stopping && current == generation.get()) {
            if (queue.offer(new Chunk(current, copy))) offeredChunks.incrementAndGet();
            else droppedChunks.incrementAndGet();
        }
    }

    void stop() {
        stopping = true;
        setEnabled(false);
        Thread active = worker;
        if (active != null) active.interrupt();
    }

    boolean awaitStop(long timeoutMillis) throws InterruptedException {
        Thread active = worker;
        if (active == null || active == Thread.currentThread()) return true;
        active.join(Math.max(1, timeoutMillis));
        return !active.isAlive();
    }

    int queuedChunks() { return queue.size(); }

    private void run() {
        long current = -1;
        final long[] lastUpdate = {0};
        GgWaveSpectrumAnalyzer analyzer = new GgWaveSpectrumAnalyzer();
        while (!stopping) {
            try {
                Chunk chunk = queue.poll(100, TimeUnit.MILLISECONDS);
                if (chunk == null || !enabled || chunk.generation != generation.get()) continue;
                if (chunk.generation != current) {
                    current = chunk.generation;
                    lastUpdate[0] = 0;
                    analyzer = new GgWaveSpectrumAnalyzer();
                }
                short[] converted = new short[chunk.samples.length];
                for (int i = 0; i < converted.length; i++) {
                    float sample = Float.isFinite(chunk.samples[i]) ? chunk.samples[i] : 0.0f;
                    converted[i] = (short) Math.round(Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
                }
                final long acceptedGeneration = current;
                analyzer.accept(converted, converted.length, levels -> {
                    long now = System.nanoTime();
                    if (!stopping && enabled && generation.get() == acceptedGeneration && now - lastUpdate[0] >= MIN_UPDATE_NS) {
                        lastUpdate[0] = now;
                        emittedFrames.incrementAndGet();
                        listener.onSpectrum(levels, SAMPLE_RATE, FFT_SIZE, acceptedGeneration);
                    }
                });
            } catch (InterruptedException interrupted) {
                if (stopping) break;
            } catch (RuntimeException ignored) {
                // Visualization failure must not affect the capture or decoder workers.
            }
        }
        queue.clear();
    }
}
