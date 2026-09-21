package com.SCVA.adapters.viewHolders;

import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.models.Chat;

import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;

import androidx.recyclerview.widget.RecyclerView;

public class ChatListItemHolder extends RecyclerView.ViewHolder
{
	private final View view;

	private OnItemClickListener listener;

	private TextView date, description, name;

	String timePattern = "HH:mm";

	SimpleDateFormat formatter = new SimpleDateFormat(timePattern, Locale.US);

	public ChatListItemHolder(View view, OnItemClickListener listener)
	{
		super(view);
		this.view = view;

		this.listener = listener;

		name = (TextView) view.findViewById(R.id.name);
		date = (TextView) view.findViewById(R.id.date);
		description = (TextView) view.findViewById(R.id.description);
	}

	public void bindData(final Chat entry)
	{
		name.setText(entry.getTitle());
		date.setText(formatter.format(Long.parseLong(entry.getDate())));
		description.setText(entry.getMessage());

		view.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (listener != null) listener.onItemClicked(entry, null);
			}
		});
	}
}
