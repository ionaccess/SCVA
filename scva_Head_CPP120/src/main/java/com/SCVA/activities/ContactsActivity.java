package com.SCVA.activities;

import java.util.ArrayList;

import com.SCVA.R;
import com.SCVA.Interfaces.OnItemClickListener;
import com.SCVA.adapters.ContactAdapter;
import com.SCVA.db.ContactsDataSource;
import com.SCVA.models.Contact;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by §∞§ on 28/01/2026.
 */
public class ContactsActivity extends BaseActivity implements View.OnClickListener, OnItemClickListener
{
	private enum CallType
	{
		ALL, SELECTION
	};

	private CallType callType = CallType.ALL;

	private ImageView search, addContact;

	private ArrayList<Object> allItems;

	private TextView titleText;

	private RecyclerView listView;

	private EditText keywordView;

	private ContactAdapter entryAdapter;

	private ContactsDataSource entryDataSource;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		setContentView(R.layout.contacts_activity);

		entryDataSource = new ContactsDataSource(this);

		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("ALL")) callType = CallType.ALL;
		if (getIntent().getStringExtra("TYPE").equalsIgnoreCase("SELECTION")) callType = CallType.SELECTION;

		titleText = findViewById(R.id.titleText);

		listView = findViewById(R.id.listView);

		addContact = findViewById(R.id.addEntry);
		addContact.setOnClickListener(this);

		search = findViewById(R.id.settings);
		search.setOnClickListener(this);

		if (callType == CallType.ALL)
		{
			titleText.setText(R.string.all_contacts);
			keywordView = findViewById(R.id.keywordView);
		}
		if (callType == CallType.SELECTION)
		{
			titleText.setText(R.string.select_contact);
		}

		allItems = new ArrayList<Object>();
		entryAdapter = new ContactAdapter(allItems, this);
		listView.setAdapter(entryAdapter);
	}

	Runnable populate = new Runnable()
	{
		@Override
		public void run()
		{
			entryAdapter.notifyDataSetChanged();
		}
	};

	@Override
	public void onClick(View view)
	{
		if (view == search)
		{
			finish();
		}
		if (view == addContact)
		{
			if (callType == CallType.ALL)
			{
				Intent intent = new Intent(this, AddContactActivity.class);
				intent.putExtra("TYPE", "ALL");
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}
			if (callType == CallType.SELECTION)
			{
				Intent intent = new Intent(this, AddContactActivity.class);
				intent.putExtra("TYPE", "SELECTION");
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		allItems.clear();

		if (callType == CallType.ALL)
		{
			allItems.addAll(entryDataSource.getAllContacts());
		}
		if (callType == CallType.SELECTION)
		{
			allItems.addAll(entryDataSource.getAllContacts());
		}
		entryAdapter.notifyDataSetChanged();
	}

	@Override
	public void onLoadMore(int position)
	{

	}

	@Override
	public void onItemClicked(Object model, String type)
	{
		if (callType == CallType.ALL)
		{
			Intent intent = new Intent(this, AddContactActivity.class);
			intent.putExtra("OBJECT", ((Parcelable) model));
			intent.putExtra("TYPE", "ALL");
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}
		if (callType == CallType.SELECTION)
		{
			Contact tmpContact = ((Contact) model);
			Intent intent = new Intent();
			intent.putExtra("TYPE", "SELECTION");
			intent.putExtra("OBJECT", ((Parcelable) model));
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	}

	@Override
	public void onItemLongClicked(Object model)
	{

	}
}