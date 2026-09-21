package com.SCVA.activities;

import com.SCVA.R;
import com.SCVA.UIViews.KnobModel;
import com.SCVA.UIViews.KnobView;
import com.SCVA.Utils.Utils;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Created by §∞§ on 03/06/2026.
 */
public class TestActivity extends Activity implements View.OnClickListener, KnobModel.Listener
{
	int cruntValue = 0;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_test);

		KnobView dial = (KnobView) findViewById(R.id.dial);
		dial.getModel().addListener(this);
	}

	@Override
	public void onDialPositionChanged(KnobModel sender, int nicksChanged, double currentValue)
	{
		TextView text = (TextView) findViewById(R.id.text);
		int ct = (int) (sender.getCurrentNick() * 0.05);
		cruntValue = cruntValue + ct;
		text.setText(((int) sender.getCurrentValue() / 10) + "Hz");
	}

	@Override
	public void onHideDialView(double value)
	{
		Utils.println("HIDE");
	}

	@Override
	public void onClick(View view)
	{

	}

	@Override
	public void onPointerCaptureChanged(boolean hasCapture)
	{
		super.onPointerCaptureChanged(hasCapture);
	}
}
