package com.SCVA.adapters.viewHolders;

import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.models.Configuration;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class ConfigurationListItemHolder extends RecyclerView.ViewHolder
{
	private final View view;

	private OnItemClickListener listener;

	private TextView name, date, description;

	public ConfigurationListItemHolder(View view, OnItemClickListener listener)
	{
		super(view);
		this.view = view;

		this.listener = listener;

		name = (TextView) view.findViewById(R.id.name);
		date = (TextView) view.findViewById(R.id.date);
		description = (TextView) view.findViewById(R.id.description);
	}

	public void bindData(final Configuration entry)
	{
		name.setText(entry.getName());
		date.setText("" + entry.getFrequency());
		description.setText(entry.getModulationType());

		view.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (listener != null) listener.onItemClicked(entry, "CLICK");
			}
		});
	}
}
