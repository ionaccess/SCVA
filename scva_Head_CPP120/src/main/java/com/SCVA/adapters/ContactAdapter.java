package com.SCVA.adapters;

import java.util.ArrayList;
import java.util.List;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.R;
import com.SCVA.adapters.viewHolders.ContactListItemHolder;
import com.SCVA.models.Contact;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by §∞§ on 28/01/2026.
 */

public class ContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{
	public static final int HEADER = 1;

	public static final int ITEM = 2;

	public static final int FOOTER = 9;

	private final OnItemClickListener onItemClickListener;

	private List<Object> data;

	public ContactAdapter(List<Object> data, OnItemClickListener onItemClickListener)
	{
		this.data = data;
		this.onItemClickListener = onItemClickListener;
	}

	@Override
	public int getItemViewType(int position)
	{
		if (data.get(position) instanceof Contact) return ITEM;
		// else if (data.get(position) instanceof Record) return MONTH;
		else
			return HEADER;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		if (viewType == ITEM) return new ContactListItemHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_list_item, parent, false), onItemClickListener);
		// if (viewType == MONTH) return new
		// RecordListItemHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.month_list_item,
		// parent, false), onItemClickListener);
		return null;
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position)
	{
		if (data.get(position) instanceof Contact)
		{
			// if (position % 2 != 0)
			// {
			// ((Contact) data.get(position)).setEvan(true);
			// }
			((ContactListItemHolder) viewHolder).bindData((Contact) data.get(position));
		}
	}

	@Override
	public int getItemCount()
	{
		return data.size();
	}

	public void clearItems()
	{
		this.data = new ArrayList<>();
		notifyDataSetChanged();
	}

	public void addItems(@NonNull List<Object> items)
	{
		for (Object item : items)
		{
			this.data.add(item);
		}
		notifyDataSetChanged();
	}
}
