package com.SCVA;

/** Builds a lightweight, capture-side spectrum for the GGWave waterfall. */
final class GgWaveSpectrumAnalyzer
{
	static final int SAMPLE_RATE = 48000;
	static final int FFT_SIZE = 4096;
	private static final int BIN_COUNT = FFT_SIZE / 2 + 1;

	interface Listener { void onSpectrum(float[] levelsDbfs); }

	private final short[] frame = new short[FFT_SIZE];
	private final double[] real = new double[FFT_SIZE];
	private final double[] imaginary = new double[FFT_SIZE];
	private final double[] window = new double[FFT_SIZE];
	private final double[] twiddleReal = new double[FFT_SIZE / 2];
	private final double[] twiddleImaginary = new double[FFT_SIZE / 2];
	private final double windowSum;
	private int filled;

	GgWaveSpectrumAnalyzer()
	{
		double sum = 0;
		for (int i = 0; i < FFT_SIZE; i++)
		{
			window[i] = 0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / (FFT_SIZE - 1));
			sum += window[i];
		}
		windowSum = sum;
		for (int i = 0; i < FFT_SIZE / 2; i++)
		{
			double angle = -2.0 * Math.PI * i / FFT_SIZE;
			twiddleReal[i] = Math.cos(angle);
			twiddleImaginary[i] = Math.sin(angle);
		}
	}

	void accept(short[] samples, int count, Listener listener)
	{
		if (samples == null || count <= 0 || count > samples.length || listener == null) return;
		int offset = 0;
		while (offset < count)
		{
			int copied = Math.min(FFT_SIZE - filled, count - offset);
			System.arraycopy(samples, offset, frame, filled, copied);
			offset += copied;
			filled += copied;
			if (filled == FFT_SIZE)
			{
				listener.onSpectrum(transform());
				filled = 0;
			}
		}
	}

	private float[] transform()
	{
		for (int i = 0; i < FFT_SIZE; i++)
		{
			real[i] = (frame[i] / 32768.0) * window[i];
			imaginary[i] = 0;
		}

		for (int i = 1, j = 0; i < FFT_SIZE; i++)
		{
			int bit = FFT_SIZE >> 1;
			for (; (j & bit) != 0; bit >>= 1) j ^= bit;
			j ^= bit;
			if (i < j)
			{
				double swap = real[i]; real[i] = real[j]; real[j] = swap;
				swap = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = swap;
			}
		}

		for (int length = 2; length <= FFT_SIZE; length <<= 1)
		{
			int half = length >> 1;
			int twiddleStep = FFT_SIZE / length;
			for (int base = 0; base < FFT_SIZE; base += length)
			{
				for (int j = 0; j < half; j++)
				{
					int wi = j * twiddleStep;
					double tr = twiddleReal[wi] * real[base + j + half] - twiddleImaginary[wi] * imaginary[base + j + half];
					double ti = twiddleReal[wi] * imaginary[base + j + half] + twiddleImaginary[wi] * real[base + j + half];
					real[base + j + half] = real[base + j] - tr;
					imaginary[base + j + half] = imaginary[base + j] - ti;
					real[base + j] += tr;
					imaginary[base + j] += ti;
				}
			}
		}

		float[] levels = new float[BIN_COUNT];
		for (int bin = 0; bin < BIN_COUNT; bin++)
		{
			double magnitude = Math.hypot(real[bin], imaginary[bin]) / windowSum;
			if (bin != 0 && bin != FFT_SIZE / 2) magnitude *= 2.0;
			levels[bin] = (float) (20.0 * Math.log10(Math.max(magnitude, 1.0e-6)));
		}
		return levels;
	}
}
