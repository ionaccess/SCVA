package com.SCVA.Utils;

import java.util.ArrayList;

import com.SCVA.Interfaces.CallBack;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by §∞§ on 11/2/2017.
 */

public class SketchSheetView extends View
{
	private CallBack callBack;

	public boolean isEditable()
	{
		return editable;
	}

	public void setEditable(boolean editable)
	{
		this.editable = editable;
	}

	private boolean editable = false;

	Paint paint;

	Path path2;

	Bitmap bitmap;

	public void setBackground(int background)
	{
		// this.background = background;
		setBackgroundResource(background);
	}

	Bitmap background;

	Canvas canvas;

	private ArrayList<DrawingClass> pathList = new ArrayList<DrawingClass>();
	private ArrayList<Integer> coordinateList = new ArrayList<Integer>();

	public String getRandom()
	{
		String randomString = "";
		for (int i = 0; i < coordinateList.size(); i++)
		{
			randomString = randomString + coordinateList.get(i);
		}
		// Utils.println(randomString);
		return randomString;
	}

	public SketchSheetView(Context context)
	{
		super(context);
		init();
	}

	private void init()
	{
		paint = new Paint();
		path2 = new Path();

		bitmap = Bitmap.createBitmap(820, 480, Bitmap.Config.ARGB_4444);
		canvas = new Canvas(bitmap);

		paint.setDither(true);

		paint.setColor(Color.parseColor("#000000"));

		paint.setStyle(Paint.Style.STROKE);

		paint.setStrokeJoin(Paint.Join.ROUND);

		paint.setStrokeCap(Paint.Cap.ROUND);

		paint.setStrokeWidth(10);
	}

	public SketchSheetView(Context context, @Nullable AttributeSet attrs)
	{
		super(context, attrs);
		init();
	}

	public SketchSheetView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		init();
	}

	// public SketchSheetView(Context context, @Nullable AttributeSet attrs, int
	// defStyleAttr, int defStyleRes)
	// {
	// super(context, attrs, defStyleAttr, defStyleRes);
	// init();
	// }

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (editable)
		{
			DrawingClass pathWithPaint = new DrawingClass();
			canvas.drawPath(path2, paint);
			if (event.getAction() == MotionEvent.ACTION_DOWN)
			{
				path2.moveTo(event.getX(), event.getY());
				path2.lineTo(event.getX(), event.getY());
				coordinateList.add((int) event.getX());
			}
			else if (event.getAction() == MotionEvent.ACTION_MOVE)
			{
				path2.lineTo(event.getX(), event.getY());
				coordinateList.add((int) event.getY());
				pathWithPaint.setPath(path2);
				pathWithPaint.setPaint(paint);
				pathList.add(pathWithPaint);
			}
		}
		invalidate();
		return true;
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);
		// if (background != null) canvas.drawBitmap(background, 0, 0, paint);

		if (pathList.size() > 0)
		{
			canvas.drawPath(pathList.get(pathList.size() - 1).getPath(), pathList.get(pathList.size() - 1).getPaint());
			if (callBack != null) callBack.notify(pathList, "");
		}
	}

	public void setCallBack(CallBack callBack)
	{
		this.callBack = callBack;
	}
}

class DrawingClass
{

	Path DrawingClassPath;

	Paint DrawingClassPaint;

	public Path getPath()
	{
		return DrawingClassPath;
	}

	public void setPath(Path path)
	{
		this.DrawingClassPath = path;
	}

	public Paint getPaint()
	{
		return DrawingClassPaint;
	}

	public void setPaint(Paint paint)
	{
		this.DrawingClassPaint = paint;
	}
}