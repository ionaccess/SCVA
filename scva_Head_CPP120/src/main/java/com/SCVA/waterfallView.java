/*
 * waterfallView.java  
 *
 * Copyright (C) 2011 John Douyere (VK2ETA)  
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.Arrays;
import java.util.Locale;

import com.SCVA.Utils.GS;
import com.SCVA.Utils.Utils;

public class waterfallView extends AppCompatImageView
{
	private int xDim = 0;

	private int yDim = 190;

	private int[] Buffer;

	private double Amplitude;

	private final double audiofactor = -2.0;

	private int red, blue, green;

	public double maxvalue = 11.0;

	private int start;

	private int end;

	private int wfCwidth;

	Bitmap wfBitmap = null;

	Paint myBmPaint = null;

	Paint myTextPaint = null;

	private boolean onTouchFrequencyChange = true;

	public double selectedFrequency;

	private double touchPosition = 0;

	private boolean touched = false;
	private boolean phoneMode = false;
	private boolean phoneSpectrumReady = false;
	private long phoneRowsDrawn;
	private float[] phoneSpectrumDbfs;
	private int phoneSampleRate;
	private int phoneFftSize;
	private int phoneDisplayMinHz = 0;
	private int phoneDisplayMaxHz = 9600;
	private boolean phoneAdaptiveContrast;
	private float phoneNoiseFloorDbfs = -90.0f;
	private int phoneSignalQualityPercent;
	private static final int PHONE_IDLE_BLUE = Color.rgb(28, 28, 255);

	public waterfallView(Context context)
	{
		super(context);
	}

	public waterfallView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
	}

	public waterfallView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		// Fixed 500Hz - 3000Hz range
		start = (int) (500.0 * 2048.0 / Modem.RSID_SAMPLE_RATE);
		end = 1 + (int) (3000.0 * 2048.0 / Modem.RSID_SAMPLE_RATE);
		xDim = end - start;
		Buffer = new int[xDim * yDim];
		maxvalue = GS.gI().getValue().getFloat("WFMAXVALUE", (float) 11.0);
		myBmPaint = new Paint();
		myBmPaint.setColor(Color.WHITE);
		myBmPaint.setStyle(Paint.Style.STROKE);
		myBmPaint.setStrokeWidth(2);
		myTextPaint = new Paint();
		myTextPaint.setTextSize(20); // for drawtext below
		myTextPaint.setColor(Color.WHITE);
		myTextPaint.setStyle(Paint.Style.FILL);
		myTextPaint.setStrokeWidth(2);
		selectedFrequency = Modem.frequency;
	}

	/** Switches this view to an independent GGWave spectrum without touching Radio FFT state. */
	public void setPhoneMode(boolean enabled)
	{
		if (phoneMode == enabled) return;
		phoneMode = enabled;
		phoneSpectrumDbfs = null;
		phoneSpectrumReady = false;
		phoneRowsDrawn = 0;
		phoneSignalQualityPercent = 0;
		if (enabled) { phoneSampleRate = GgWaveSpectrumAnalyzer.SAMPLE_RATE; phoneFftSize = GgWaveSpectrumAnalyzer.FFT_SIZE; }
		Arrays.fill(Buffer, enabled ? PHONE_IDLE_BLUE : Color.BLACK);
		invalidate();
	}

	public long phoneRowsDrawn() { return phoneRowsDrawn; }

	/** Selects the phone backend's frequency band and receive display contrast. */
	public void setPhoneDisplayProfile(String protocol)
	{
		phoneAdaptiveContrast = "MODEM73".equals(protocol);
		int minHz = 0;
		int maxHz = 9600;
		if (phoneAdaptiveContrast)
		{
			// The fixed MODEM73 profile is centered at 1500 Hz and its native
			// receive frontend filters approximately 150..2850 Hz.
			maxHz = 3000;
		}
		else if (protocol != null && protocol.startsWith("ULTRASOUND_"))
		{
			minHz = 12000;
			maxHz = phoneSampleRate / 2;
		}
		if (phoneDisplayMinHz != minHz || phoneDisplayMaxHz != maxHz)
		{
			phoneDisplayMinHz = minHz;
			phoneDisplayMaxHz = maxHz;
			Arrays.fill(Buffer, PHONE_IDLE_BLUE);
			invalidate();
		}
	}

	/** Receives one phone-backend capture spectrum, in dBFS. */
	public int setPhoneSpectrum(float[] levelsDbfs, int sampleRate, int fftSize)
	{
		if (!phoneMode || levelsDbfs == null || fftSize < 2 || sampleRate <= 0 || levelsDbfs.length < fftSize / 2 + 1) return phoneSignalQualityPercent;
		phoneSpectrumDbfs = Arrays.copyOf(levelsDbfs, fftSize / 2 + 1);
		phoneSampleRate = sampleRate;
		phoneFftSize = fftSize;
		int firstAudioBin = Math.max(1, phoneFftSize * phoneDisplayMinHz / phoneSampleRate);
		int lastAudioBin = Math.min(phoneSpectrumDbfs.length - 1,
				phoneFftSize * phoneDisplayMaxHz / phoneSampleRate);
		if (lastAudioBin < firstAudioBin) return phoneSignalQualityPercent;
		int[] histogram = new int[121];
		int bandBins = 0;
		for (int bin = firstAudioBin; bin <= lastAudioBin; bin++)
		{
			int bucket = Math.max(0, Math.min(120, Math.round(phoneSpectrumDbfs[bin] + 120.0f)));
			histogram[bucket]++;
			bandBins++;
		}
		int noiseTarget = Math.max(1, (bandBins * 20 + 99) / 100);
		int signalTarget = Math.max(1, (bandBins * 99 + 99) / 100);
		int cumulative = 0;
		boolean noiseCaptured = false;
		float signalPercentileDbfs = -120.0f;
		for (int i = 0; i < histogram.length; i++)
		{
			cumulative += histogram[i];
			if (!noiseCaptured && cumulative >= noiseTarget) { phoneNoiseFloorDbfs = i - 120.0f; noiseCaptured = true; }
			if (cumulative >= signalTarget) { signalPercentileDbfs = i - 120.0f; break; }
		}
		// A single FFT-bin spike is not a signal. Use the upper-band percentile,
		// discount broadband-noise crest, then map a wider 0-60 dB range to the bar.
		float spectralContrastDb = Math.max(0.0f, signalPercentileDbfs - phoneNoiseFloorDbfs - 12.0f);
		int measuredPercent = Math.max(0, Math.min(100, Math.round(spectralContrastDb * (100.0f / 60.0f))));
		phoneSignalQualityPercent = Math.round(phoneSignalQualityPercent * 0.70f + measuredPercent * 0.30f);
		phoneSpectrumReady = true;
		return phoneSignalQualityPercent;
	}

	/** Clears received audio after the selected phone backend stops. */
	public void clearPhoneSpectrum()
	{
		if (!phoneMode || phoneSpectrumDbfs == null) return;
		phoneSpectrumDbfs = null;
		phoneSpectrumReady = false;
		phoneSignalQualityPercent = 0;
		Arrays.fill(Buffer, PHONE_IDLE_BLUE);
		invalidate();
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		int leftPos = 0;
		int topPos = 0;
		int xDimFinal = xDim;
		int yPosText;
		int widthScale = 1;
		super.onDraw(canvas);

		// Future speed optimization and memory improvements (less GC)
		// Replace pixels in the bitmap with the colors in the array.
		// wfBitmap.setPixels(int[] pixels, int offset, int stride, int x, int y, int
		// width, int height)

		// Calculate scaling factor for wider screen (orientation)
		wfCwidth = canvas.getWidth(); // returns the width of the screen NOT the waterfall object on the screen
		// wfCheight = 130; //fixed height
		// topPos = wfCheight / 2 - 200;
		// topPos = (topPos < 0 ? 0: topPos);
		// fixed

		if (widthScale > 1)
		{
			// Utils.println("SCALLED");
			topPos = 50;
			yDim = 200;
		}
		widthScale = (int) (wfCwidth / xDim);
		xDimFinal = xDim * widthScale;
		xDimFinal = getWidth();
		myTextPaint.setTextSize(widthScale == 1 ? 20 : 30);
		yPosText = widthScale == 1 ? 30 : 35;
		leftPos = (wfCwidth - xDimFinal) / 2 - 0; // Centered and we need at least 55 dp for the buttons on the right
		leftPos = (leftPos < 1 ? 1 : leftPos);
		// Insert the newest phone spectrum before rebuilding the bitmap so each capture
		// frame appears immediately, with the same rolling-history direction as Radio.
		if (phoneMode && phoneSpectrumReady && phoneSpectrumDbfs != null)
		{
			System.arraycopy(Buffer, 0, Buffer, 2 * xDim, xDim * (yDim - 2));
			drawPhoneSpectrumRow();
			System.arraycopy(Buffer, 0, Buffer, xDim, xDim);
			phoneSpectrumReady = false;
			phoneRowsDrawn++;
		}
		// Flicker issue. Always draw, even if same data
		wfBitmap = Bitmap.createBitmap(Buffer, xDim, yDim, Bitmap.Config.ARGB_8888);
		// if (widthScale > 1)
		{
			// wfBitmap = Bitmap.createScaledBitmap(wfBitmap, xDim * widthScale, yDim,
			// true);
			wfBitmap = Bitmap.createScaledBitmap(wfBitmap, xDimFinal - 10, yDim, true);
		}
		// Moved to object creation section myBmPaint = new Paint();
		// Draw rectangle around waterfall
		canvas.drawRect((float) leftPos, (float) topPos, (float) leftPos + xDimFinal, (float) topPos + yDim, myBmPaint);
		if (phoneMode)
		{
			drawPhoneFrequencyScale(canvas, leftPos, topPos, yDim, xDimFinal, yPosText, phoneDisplayMinHz, phoneDisplayMaxHz);
		}
		else
		{
			// Draw the "500Hz" tick mark and text
			canvas.drawLine((float) leftPos, (float) topPos + yDim, (float) leftPos, (float) topPos + yDim + 10, myBmPaint);
			canvas.drawText("500", leftPos - 30, topPos + yDim + yPosText, myTextPaint);
			// Draw the "1000Hz" tick mark and text
			canvas.drawLine((float) leftPos + xDimFinal / 5, (float) topPos + yDim, (float) leftPos + xDimFinal / 5, (float) topPos + yDim + 10, myBmPaint);
			canvas.drawText("1000", leftPos + xDimFinal / 5 - 40, topPos + yDim + yPosText, myTextPaint);
			// Draw the "1500Hz" tick mark and text
			canvas.drawLine((float) leftPos + xDimFinal * 2 / 5, (float) topPos + yDim, (float) leftPos + xDimFinal * 2 / 5, (float) topPos + yDim + 10, myBmPaint);
			canvas.drawText("1500", leftPos + xDimFinal * 2 / 5 - 40, topPos + yDim + yPosText, myTextPaint);
			// Draw the "2000Hz" tick mark and text
			canvas.drawLine((float) leftPos + xDimFinal * 3 / 5, (float) topPos + yDim, (float) leftPos + xDimFinal * 3 / 5, (float) topPos + yDim + 10, myBmPaint);
			canvas.drawText("2000", leftPos + xDimFinal * 3 / 5 - 40, topPos + yDim + yPosText, myTextPaint);
			// Draw the "2500Hz" tick mark and text
			canvas.drawLine((float) leftPos + xDimFinal * 4 / 5, (float) topPos + yDim, (float) leftPos + xDimFinal * 4 / 5, (float) topPos + yDim + 10, myBmPaint);
			canvas.drawText("2500", leftPos + xDimFinal * 4 / 5 - 40, topPos + yDim + yPosText, myTextPaint);
			// Draw the "3000Hz" tick mark and text
			canvas.drawLine((float) leftPos + xDimFinal, (float) topPos + yDim, (float) leftPos + xDimFinal, (float) topPos + yDim + 10, myBmPaint);
			canvas.drawText("3000", leftPos + xDimFinal - 40, topPos + yDim + yPosText, myTextPaint);
		}
		// Draw the updated waterfall bitmap
		canvas.drawBitmap(wfBitmap, (float) leftPos, (float) topPos, null);
		// The Radio center-frequency marker has no meaning for the independent GGWave audio view.
		if (!phoneMode && touched)
		{
			// selectedFrequency = (touchPosition - leftPos) / xDimFinal * 2500.0 + 500.0;
			if (selectedFrequency > 2500) selectedFrequency = 2500;
			if (selectedFrequency < 500) selectedFrequency = 500;
		}
		else if (!phoneMode)
		{
			selectedFrequency = Modem.frequency;
		}
		if (!phoneMode)
		{
		myTextPaint.setColor(Color.RED);
		canvas.drawLine((float) (leftPos + xDimFinal * (selectedFrequency - (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos - 10,
				(float) (leftPos + xDimFinal * (selectedFrequency - (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos + yDim + 10, myTextPaint);
		canvas.drawLine((float) (leftPos + xDimFinal * (selectedFrequency + (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos - 10,
				(float) (leftPos + xDimFinal * (selectedFrequency + (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos + yDim + 10, myTextPaint);
		canvas.drawLine((float) (leftPos + xDimFinal * (selectedFrequency - (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos - 10,
				(float) (leftPos + xDimFinal * (selectedFrequency + (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos - 10, myTextPaint);
		canvas.drawLine((float) (leftPos + xDimFinal * (selectedFrequency - (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos + yDim + 10,
				(float) (leftPos + xDimFinal * (selectedFrequency + (Modem.bandwidth / 2) - 500.0) / 2500.0), (float) topPos + yDim + 10, myTextPaint);
		// Draw the selected frequency text
		myTextPaint.setTextSize(40);
		myTextPaint.setColor(Color.GREEN);
		// if (touched) canvas.drawText(String.format("%.1f", selectedFrequency),
		// leftPos + (xDimFinal / 2) - 40, 30, myTextPaint);
		myTextPaint.setColor(Color.BLACK);
		}
		// }
		if (!phoneMode && Modem.newAmplReady)
		{
			// prepare new bitmap
			// First shift one row up to make room for new values
			System.arraycopy(Buffer, 0, Buffer, 2 * xDim, xDim * (yDim - 2)); // fall down
			// Wait for new array
			// Android add dynamic centre frequency (on RSID reception)
			// if (Modem.rsidFrequency != 0) {
			// start = (int) ((Modem.rsidFrequency - 500.0) * 2048.0 /
			// Modem.RSID_SAMPLE_RATE);
			// end = 1 + (int) ((Modem.rsidFrequency + 500.0) * 2048.0 /
			// Modem.RSID_SAMPLE_RATE);
			// }
			// Then prepare new rows (we double the rows for visibility purposes)
			int j = -1;
			for (int i = start; i < end; i++)
			{
				Amplitude = Math.log(Modem.WaterfallAmpl[i]) + audiofactor;
				red = (int) (Amplitude > maxvalue * 0.5 ? 255.0 * (Amplitude / maxvalue) : 128.0 * (Amplitude / maxvalue));
				if (red > 255) red = 255;
				if (red < 0) red = 0;
				green = (int) (Amplitude > maxvalue * 0.5 ? 255.0 * (Amplitude / maxvalue) : 128.0 * (Amplitude / maxvalue));
				if (green > 255) green = 255;
				if (green < 0) green = 0;
				blue = (int) (Amplitude > maxvalue * 0.1 ? 512.0 * ((maxvalue - Amplitude) / maxvalue) : 512.0 * (Amplitude / maxvalue));
				if (blue > 255) blue = 255;
				if (blue < 0) blue = 0;
				int thispixel = 0xff000000 | (red << 16) | (green << 8) | blue;
				// Changed to single pixel
				// Buffer[++j] = thispixel; //Double the width to allow for better viewing
				Buffer[++j] = thispixel;
			}
			// Double the thickness for visibility purposes
			System.arraycopy(Buffer, 0, Buffer, xDim, xDim);
			// Reset flag for Rx RSID method to fill another dataset
			Modem.newAmplReady = false;
		}
	}

	private void drawPhoneFrequencyScale(Canvas canvas, int left, int top, int height, int width, int textY, int minHz, int maxHz)
	{
		int originalTextColor = myTextPaint.getColor();
		myTextPaint.setColor(Color.BLACK);
		for (int tick = 0; tick <= 5; tick++)
		{
			int x = left + width * tick / 5;
			int hz = minHz + (maxHz - minHz) * tick / 5;
			canvas.drawLine(x, top + height, x, top + height + 10, myBmPaint);
			String label = String.format(Locale.US, "%d", hz);
			float labelWidth = myTextPaint.measureText(label);
			float labelX = tick == 0 ? x : tick == 5 ? x - labelWidth : x - labelWidth / 2.0f;
			canvas.drawText(label, labelX, top + height + textY, myTextPaint);
		}
		myTextPaint.setColor(originalTextColor);
	}

	private void drawPhoneSpectrumRow()
	{
		int bins = Math.min(phoneSpectrumDbfs.length, phoneFftSize / 2 + 1);
		int firstBin = Math.max(0, Math.min(bins - 1, Math.round((float) phoneDisplayMinHz * phoneFftSize / phoneSampleRate)));
		int lastBin = Math.max(firstBin + 1, Math.min(bins, Math.round((float) phoneDisplayMaxHz * phoneFftSize / phoneSampleRate) + 1));
		// MODEM73 uses its in-band floor. A margin keeps ordinary microphone
		// noise blue while a received spectral component rises above it.
		float threshold = phoneAdaptiveContrast ? phoneNoiseFloorDbfs + 14.0f
				: Math.max(-55.0f, phoneNoiseFloorDbfs + 14.0f);
		float displayRangeDb = phoneAdaptiveContrast ? 18.0f : 22.0f;
		for (int column = 0; column < xDim; column++)
		{
			int from = firstBin + column * (lastBin - firstBin) / xDim;
			int to = Math.max(from + 1, firstBin + (column + 1) * (lastBin - firstBin) / xDim);
			to = Math.min(to, lastBin);
			float level = -120.0f;
			for (int bin = from; bin < to; bin++) level = Math.max(level, phoneSpectrumDbfs[bin]);
			double strength = Math.max(0.0, Math.min(1.0, (level - threshold) / displayRangeDb));
			if (phoneAdaptiveContrast && strength <= 0.0)
			{
				Buffer[column] = PHONE_IDLE_BLUE;
				continue;
			}
			double radioStrength;
			if (strength <= 0.0)
			{
				// Keep idle texture subtle and blue; stronger bins use the Radio waterfall palette below.
				double noise = Math.max(0.0, Math.min(1.0, (level - phoneNoiseFloorDbfs + 3.0) / 14.0));
				radioStrength = 0.22 + 0.18 * noise;
			}
			else
			{
				radioStrength = 0.22 + 0.78 * strength;
			}
			Buffer[column] = phoneRadioPaletteColor(radioStrength);
		}
	}

	/** Uses the same blue-to-yellow RGB mapping as the Radio waterfall on a normalized level. */
	private static int phoneRadioPaletteColor(double level)
	{
		level = Math.max(0.0, Math.min(1.0, level));
		int redGreen = (int) (level > 0.5 ? 255.0 * level : 128.0 * level);
		int blue = (int) (level > 0.1 ? 512.0 * (1.0 - level) : 512.0 * level);
		return Color.rgb(Math.min(255, redGreen), Math.min(255, redGreen), Math.min(255, blue));
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (phoneMode) return true; // Phone spectra are receive-only; Radio tuning is unavailable.
		super.onTouchEvent(event);
		if (onTouchFrequencyChange)
		{
			touchPosition = event.getX();
			if (event.getAction() == MotionEvent.ACTION_DOWN)
			{
				touched = true;
			}
			if (event.getAction() == MotionEvent.ACTION_UP)
			{
				if (!GS.gI().isReceivingForm() && GS.gI().isProcessorON() && !GS.gI().isTXActive() && Modem.modemState == Modem.RXMODEMRUNNING)
				{
					Context host = getContext();
					while (host instanceof android.content.ContextWrapper && !(host instanceof com.SCVA.activities.UserChatActivity))
					{
						Context base = ((android.content.ContextWrapper) host).getBaseContext();
						if (base == host) break;
						host = base;
					}
					if (!(host instanceof com.SCVA.activities.UserChatActivity))
					{ android.widget.Toast.makeText(getContext(), "Open a configured chat first", android.widget.Toast.LENGTH_LONG).show(); return true; }
					if (!((com.SCVA.activities.UserChatActivity) host).changeRadioFrequency((int) selectedFrequency, Integer.toString((int) selectedFrequency))) return true;
					SCVA.mHandler.post(SCVA.updateTitle);
				}
				touched = false;
			}
		}
		return true;
	}

	public void setOnTouchFrequencyChange(boolean onTouchFrequencyChange)
	{
		this.onTouchFrequencyChange = onTouchFrequencyChange;
	}

	public double getSelectedFrequency()
	{
		return selectedFrequency;
	}

	public void setSelectedFrequency(double selectedFrequency)
	{
		this.selectedFrequency = selectedFrequency;
	}

	public boolean isTouched()
	{
		return touched;
	}

	public void setTouched(boolean touched)
	{
		this.touched = touched;
	}

}
