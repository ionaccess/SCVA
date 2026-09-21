package com.SCVA.UIViews;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;

public class KnobModel
{
	public interface Listener
	{
		void onDialPositionChanged(KnobModel sender, int nicksChanged, double currentValue);

		void onHideDialView(double value);
	}

	private List<Listener> listeners = new ArrayList<Listener>();

	private int totalNicks = 100;

	private int currentNick = 0;

	double currentValue = 0;

	public KnobModel()
	{

	}

	public final float getRotationInDegrees()
	{
		return (360.0f / totalNicks) * currentNick;
	}

	public void setCurrentValue(double currentValue)
	{
		this.currentValue = currentValue;
	}

	public double getCurrentValue()
	{
		return currentValue;
	}

	public void hideView(double value)
	{
		// Utils.println("|" + listeners.size() + "|");
		for (Listener listener : listeners)
		{
			listener.onHideDialView(value);
		}
	}

	public void rotationChanged(double currentValue, String rotation)
	{
		// Utils.println(currentValue + rotation);
	}

	public final void rotate(int nicks)
	{
		currentNick = (currentNick + nicks);
		if (currentNick >= totalNicks)
		{
			currentNick %= totalNicks;
		}
		else if (currentNick < 0)
		{
			currentNick = (totalNicks + currentNick);
		}

		for (Listener listener : listeners)
		{
			listener.onDialPositionChanged(this, nicks, currentValue);
		}
	}

	public final List<Listener> getListeners()
	{
		return listeners;
	}

	public final int getTotalNicks()
	{
		return totalNicks;
	}

	public final int getCurrentNick()
	{
		return currentNick;
	}

	public final void addListener(Listener listener)
	{
		listeners.add(listener);
	}

	public final void removeListener(Listener listener)
	{
		listeners.remove(listener);
	}

	private static String getBundlePrefix()
	{
		return KnobModel.class.getSimpleName() + ".";
	}

	public final void save(Bundle bundle)
	{
		String prefix = getBundlePrefix();

		bundle.putInt(prefix + "totalNicks", totalNicks);
		bundle.putInt(prefix + "currentNick", currentNick);
	}

	public static KnobModel restore(Bundle bundle)
	{
		KnobModel model = new KnobModel();

		String prefix = getBundlePrefix();
		model.totalNicks = bundle.getInt(prefix + "totalNicks");
		model.currentNick = bundle.getInt(prefix + "currentNick");

		return model;
	}
}
