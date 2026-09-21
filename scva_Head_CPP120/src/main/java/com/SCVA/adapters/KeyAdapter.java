package com.SCVA.adapters;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.SCVA.R;
import com.SCVA.Utils.Utils;
import com.SCVA.models.Key;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class KeyAdapter extends ArrayAdapter<Key>
{
	private int viewResourceId;

	private ArrayList<Key> data;

	SimpleDateFormat sdf = new SimpleDateFormat(Utils.DATE_FORMAT);

	public KeyAdapter(Context context, int viewResourceId, ArrayList<Key> data, ListView listView)
	{
		super(context, viewResourceId, data);
		this.viewResourceId = viewResourceId;
		this.data = data;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent)
	{
		View rowView = convertView;
		if (convertView == null)
		{
			Activity activity = (Activity) getContext();
			LayoutInflater inflater = activity.getLayoutInflater();
			rowView = inflater.inflate(viewResourceId, null);
		}
		final Key raw = data.get(position);

		TextView tvAvatar = (TextView) rowView.findViewById(R.id.tvAvatar);
		try
		{
			tvAvatar.setText(raw.getName().substring(0, 1).toUpperCase());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		TextView name = (TextView) rowView.findViewById(R.id.name);
		name.setText(raw.getName());

		TextView date = (TextView) rowView.findViewById(R.id.date);
		date.setText("" + sdf.format(new Date(Long.parseLong(raw.getCreatedOn()))));

		TextView status = (TextView) rowView.findViewById(R.id.status);
		String statusText = "";
		status.setText(statusText);

		if (raw.isDefault())
		{
			statusText = "A";
			status.setText(statusText);
		}

		if (raw.isActive())
		{
			statusText = statusText + " A";
			status.setText(statusText);
		}
		return rowView;
	}
}