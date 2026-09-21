package com.SCVA.adapters.viewHolders;

import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.R;
import com.SCVA.Utils.Utils;
import com.SCVA.models.Contact;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class ContactListItemHolder extends RecyclerView.ViewHolder
{
	private final View view;

	private LinearLayout rootBg;

	private OnItemClickListener listener;

	private TextView header, date, description, receiptNumber, name, mobile;

	public ContactListItemHolder(View view, OnItemClickListener listener)
	{
		super(view);
		this.view = view;

		// rootBg = (LinearLayout) view.findViewById(R.id.root_bg);
		this.listener = listener;

		date = (TextView) view.findViewById(R.id.date);
		name = (TextView) view.findViewById(R.id.name);
		mobile = (TextView) view.findViewById(R.id.mobile);
		header = (TextView) view.findViewById(R.id.header);
		description = (TextView) view.findViewById(R.id.description);
		receiptNumber = (TextView) view.findViewById(R.id.receiptNumber);
	}

	public void bindData(final Contact entry)
	{
		// if (entry.isHeader())
		// {
		// header.setVisibility(View.VISIBLE);
		// header.setText("THIS IS A HEADER");
		// }
		// receiptNumber.setText(entry.getReceiptNumber());


		// date.setText(entry.getDate());
		name.setText(entry.getName());
		mobile.setText(entry.getMobileNumber());
		description.setText(entry.getDescription());

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
