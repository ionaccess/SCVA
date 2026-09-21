package com.SCVA.adapters;

import java.util.ArrayList;
import java.util.List;

import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.adapters.viewHolders.ChatListItemHolder;
import com.SCVA.adapters.viewHolders.ContactListItemHolder;
import com.SCVA.models.Chat;
import com.SCVA.models.Contact;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by §∞§ on 28/01/2026.
 */

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
{
	public static final int HEADER = 1;

	public static final int ITEM = 2;

	public static final int FOOTER = 9;

	private final OnItemClickListener onItemClickListener;

	private List<Object> data;

	public ChatAdapter(List<Object> data, OnItemClickListener onItemClickListener)
	{
		this.data = data;
		this.onItemClickListener = onItemClickListener;
	}

	@Override
	public int getItemViewType(int position)
	{
		if (data.get(position) instanceof Chat) return ITEM;
		else
			return HEADER;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		if (viewType == ITEM) return new ChatListItemHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_list_item, parent, false), onItemClickListener);
		return null;
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position)
	{
		((ChatListItemHolder) viewHolder).bindData((Chat) data.get(position));
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
