package com.SCVA.adapters.viewHolders;

import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.Utils.Constants;
import com.SCVA.models.TextMessage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class TextMessageListItemHolder extends RecyclerView.ViewHolder
{
	private final View view;

	private LinearLayout rootBg, mine, his;

	private OnItemClickListener listener;

	private ImageView myKeyView, hisKeyView, myImage, hisImage;

	private TextView myName, myDate, hisName, hisDate, header;

	public TextMessageListItemHolder(View view, OnItemClickListener listener)
	{
		super(view);
		this.view = view;

		this.listener = listener;

		his = view.findViewById(R.id.his);
		mine = view.findViewById(R.id.mine);

		header = (TextView) view.findViewById(R.id.header);

		myImage = (ImageView) view.findViewById(R.id.my_Image);
		hisImage = (ImageView) view.findViewById(R.id.his_Image);

		myKeyView = (ImageView) view.findViewById(R.id.my_keyView);
		hisKeyView = (ImageView) view.findViewById(R.id.his_keyView);

		myDate = (TextView) view.findViewById(R.id.my_date);
		myName = (TextView) view.findViewById(R.id.my_name);
		hisName = (TextView) view.findViewById(R.id.his_name);
		hisDate = (TextView) view.findViewById(R.id.his_date);
	}

	public void bindData(final TextMessage entry)
	{
		if (entry.getType() == TextMessage.MY_TEXT)
		{
			if (entry.isHeader())
			{
				header.setVisibility(View.VISIBLE);
				header.setText(entry.getDate());
			}
			else
			{
				header.setVisibility(View.GONE);
			}
			mine.setVisibility(View.VISIBLE);
			his.setVisibility(View.GONE);

			myImage.setVisibility(View.GONE);
			myKeyView.setVisibility(View.GONE);

			myName.setVisibility(View.VISIBLE);
			myName.setText(entry.getMessage());
			myDate.setText(entry.getTime());
		}
		if (entry.getType() == TextMessage.HIS_TEXT)
		{
			if (entry.isHeader())
			{
				header.setVisibility(View.VISIBLE);
				header.setText(entry.getDate());
			}
			else
			{
				header.setVisibility(View.GONE);
			}
			his.setVisibility(View.VISIBLE);
			mine.setVisibility(View.GONE);

			hisImage.setVisibility(View.GONE);
			hisKeyView.setVisibility(View.GONE);

			hisName.setVisibility(View.VISIBLE);
			hisName.setText(entry.getMessage());
			hisDate.setText(entry.getTime());
		}
		if (entry.getType() == TextMessage.MY_KEY)
		{
			header.setVisibility(View.GONE);
			his.setVisibility(View.GONE);
			mine.setVisibility(View.VISIBLE);
			myName.setVisibility(View.GONE);
			myImage.setVisibility(View.GONE);
			myDate.setText(entry.getTime());
			myKeyView.setVisibility(View.VISIBLE);
		}
		if (entry.getType() == TextMessage.HIS_KEY)
		{
			mine.setVisibility(View.GONE);
			header.setVisibility(View.GONE);
			his.setVisibility(View.VISIBLE);
			hisName.setVisibility(View.GONE);
			hisDate.setText(entry.getTime());
			hisImage.setVisibility(View.GONE);
			hisKeyView.setVisibility(View.VISIBLE);
		}
		if (entry.getType() == TextMessage.MY_IMAGE)
		{
			his.setVisibility(View.GONE);
			header.setVisibility(View.GONE);
			mine.setVisibility(View.VISIBLE);
			myName.setVisibility(View.GONE);
			myKeyView.setVisibility(View.GONE);

			myDate.setText(entry.getTime());
			myImage.setVisibility(View.VISIBLE);

			try
			{
				byte[] imageBytes = Base64.decode(entry.getMessage(), Base64.DEFAULT);
				Bitmap decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
				myImage.setImageDrawable(null);
				myImage.setImageBitmap(decodedImage);
			}
			catch (Exception e)
			{
				if (Constants.devMode) e.printStackTrace();
			}
		}

		if (entry.getType() == TextMessage.HIS_IMAGE)
		{
			mine.setVisibility(View.GONE);
			header.setVisibility(View.GONE);
			his.setVisibility(View.VISIBLE);
			hisName.setVisibility(View.GONE);
			hisKeyView.setVisibility(View.GONE);

			hisDate.setText(entry.getTime());

			try
			{
				hisImage.setVisibility(View.VISIBLE);
				byte[] imageBytes = Base64.decode(entry.getMessage(), Base64.DEFAULT);
				Bitmap decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
				hisImage.setImageDrawable(null);
				hisImage.setImageBitmap(decodedImage);
			}
			catch (Exception e)
			{
				if (Constants.devMode) e.printStackTrace();
			}
		}

		view.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (listener != null) listener.onItemClicked(entry, null);
			}
		});
		view.setOnLongClickListener(new View.OnLongClickListener()
		{
			@Override
			public boolean onLongClick(View view)
			{
				if (listener != null) listener.onItemLongClicked(entry);
				return false;
			}
		});
	}
}