package com.SCVA.Utils;

/**
 * Created by §∞§ on 30/08/2026.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public abstract class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback
{
	private final Paint paint;
	private final Drawable deleteIcon;
	private final int backgroundColor;
	private final int marginPixels;

	public SwipeToDeleteCallback(Context context)
	{
		// Configure to only allow swiping from Right to Left (LEFT)
		super(0, ItemTouchHelper.LEFT);

		this.paint = new Paint();
		this.backgroundColor = Color.parseColor("#EF5350");
		this.deleteIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_delete);

		// Convert a clean 16dp margin into physical device pixels
		float density = context.getResources().getDisplayMetrics().density;
		this.marginPixels = (int) (16 * density);
	}

	@Override
	public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target)
	{
		return false; // defaults to swipe-only (no drag-and-drop sorting)
	}

	@Override
	public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
			boolean isCurrentlyActive)
	{

		View itemView = viewHolder.itemView;

		// Only draw if the item is being swiped to the left
		if (dX < 0)
		{
			// 1. Draw the background color block
			paint.setColor(backgroundColor);
			c.drawRect((float) itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom(), paint);

			// 2. Draw your custom delete icon asset
			if (deleteIcon != null)
			{
				// Force icon to render white for optimal contrast
				deleteIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

				int itemHeight = itemView.getBottom() - itemView.getTop();
				int iconIntrinsicWidth = deleteIcon.getIntrinsicWidth();
				int iconIntrinsicHeight = deleteIcon.getIntrinsicHeight();

				// Vertically center the icon shape
				int iconTop = itemView.getTop() + (itemHeight - iconIntrinsicHeight) / 2;
				int iconBottom = iconTop + iconIntrinsicHeight;

				// Position the icon on the right edge with margins
				int iconRight = itemView.getRight() - marginPixels;
				int iconLeft = iconRight - iconIntrinsicWidth;

				// Only render icon visible once user has dragged past the margin zone
				if (dX < -(marginPixels * 2 + iconIntrinsicWidth))
				{
					deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
					deleteIcon.draw(c);
				}
			}
		}
		super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
	}
}
