/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.kncwallet.wallet.ui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.uri.BitcoinURI;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ContactImage;
import com.kncwallet.wallet.ui.InputParser.StringInputParser;
import com.kncwallet.wallet.ui.dialog.KnCDialog;
import com.kncwallet.wallet.util.BitmapFragment;
import com.kncwallet.wallet.util.ContactsDownloader;
import com.kncwallet.wallet.util.Qr;
import com.kncwallet.wallet.util.WalletUtils;

import com.kncwallet.wallet.R;
import com.loopj.android.image.SmartImage;
import com.loopj.android.image.SmartImageView;

/**
 * @author Andreas Schildbach
 */
public final class SendingAddressesFragment extends SherlockListFragment implements LoaderManager.LoaderCallbacks<Cursor>
{
	
	private AbstractWalletActivity activity;
	private ClipboardManager clipboardManager;
	private LoaderManager loaderManager;

	private SimpleCursorAdapter adapter;
	private String walletAddressesSelection;

	private final Handler handler = new Handler();

	private static final int REQUEST_CODE_SCAN = 0;

    private Hashtable<String, Object> contactImageCache = new Hashtable<String, Object>();
    private Hashtable<String, Object> contactImageUrlsCache = new Hashtable<String, Object>();
    private Hashtable<String, Integer> sourceCache = new Hashtable<String, Integer>();

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
		this.loaderManager = getLoaderManager();
	}

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		setEmptyText(getString(R.string.address_book_empty_text));

		adapter = new SimpleCursorAdapter(activity, R.layout.address_book_row, null, new String[] { AddressBookProvider.KEY_RAW_TELEPHONE, AddressBookProvider.KEY_ADDRESS, AddressBookProvider.KEY_LABEL,
				AddressBookProvider.KEY_ADDRESS, AddressBookProvider.KEY_ADDRESS }, new int[] { R.id.address_book_row_number, R.id.address_book_contact_image, R.id.address_book_row_label, R.id.address_book_row_address, R.id.address_book_row_source_image }, 0);
		adapter.setViewBinder(new ViewBinder()
		{
			@Override
			public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex)
			{
				if(view.getId() == R.id.address_book_contact_image){

			           SmartImageView img = (SmartImageView)view;

                       String address = cursor.getString(columnIndex);
			           Bitmap contactImage = cachedBitmap(address);
			           if(contactImage != null) {
			        	   img.setImageBitmap(contactImage);
			           } else {

                           String imageUrl = cachedImageUrl(address);
                           if(imageUrl != null){
                               img.setImageUrl(imageUrl, R.drawable.contact_placeholder);
                           }else {
                               img.setImageResource(R.drawable.contact_placeholder);
                           }
			           }
			           
			           return true; //true because the data was bound to the view
			    }
			
				if(view.getId() == R.id.address_book_row_number)
				{
					((TextView) view).setText(cursor.getString(columnIndex));
					return true;
				}
				
				if (!AddressBookProvider.KEY_ADDRESS.equals(cursor.getColumnName(columnIndex)))
					return false;

                if(view.getId() == R.id.address_book_row_source_image){
                    ((ImageView)view).setImageResource(cachedSourceImageResource(cursor.getString(columnIndex)));
                    view.setVisibility(View.VISIBLE);
                    return true;
                }

				((TextView) view).setText(WalletUtils.formatHash(cursor.getString(columnIndex), Constants.ADDRESS_FORMAT_GROUP_SIZE,		
						24));



				return true;
			}
		});
		setListAdapter(adapter);

		loaderManager.initLoader(0, null, this);

        getListView().setCacheColorHint(Color.TRANSPARENT);
        getListView().setBackgroundColor(getResources().getColor(R.color.knc_background_darker));
        getView().setBackgroundColor(getResources().getColor(R.color.knc_background_darker));
	}

    private String cachedImageUrl(String address){
        Object cached = contactImageUrlsCache.get(address);
        if(cached == null){

            String imageUrl = ContactImage.getImageUrl(activity, AddressBookProvider.resolveRowId(activity, address));

            if(imageUrl != null){
                contactImageUrlsCache.put(address, imageUrl);
                cached = imageUrl;
            }else{
                contactImageUrlsCache.put(address, new Object());
                cached = new Object();
            }
        }

        if(cached instanceof String){
            return (String)cached;
        }

        return null;
    }

    private Bitmap cachedBitmap(String address){
        Object cached = contactImageCache.get(address);
        if(cached == null) {
            Bitmap bitmap = AddressBookProvider.bitmapForAddress(SendingAddressesFragment.this.getActivity(), address);
            if(bitmap != null){
                contactImageCache.put(address, bitmap);
                cached = bitmap;
            }else{
                contactImageCache.put(address, new Object());
                cached = new Object();
            }
        }

        if(cached instanceof Bitmap){
            return (Bitmap)cached;
        }
        return null;
    }

    private int cachedSourceImageResource(String address){
        Integer resource = sourceCache.get(address);
        if(resource == null){
            resource = AddressBookProvider.imageResourceForSource(activity, address);
            sourceCache.put(address, resource);

        }
        return resource;
    }

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK)
		{
			final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

			new StringInputParser(input)
			{
				@Override
				protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
				{
					// workaround for "IllegalStateException: Can not perform this action after onSaveInstanceState"
					handler.postDelayed(new Runnable()
					{
						@Override
						public void run()
						{
							EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
						}
					}, 500);
				}

				@Override
				protected void directTransaction(final Transaction transaction)
				{
					cannotClassify(input);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(activity, null, R.string.address_book_options_scan_title, messageResId, messageArgs);
				}
			}.parse();
		}
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.sending_addresses_fragment_options, menu);

		final PackageManager pm = activity.getPackageManager();
		menu.findItem(R.id.sending_addresses_options_scan).setVisible(
				pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.sending_addresses_options_paste:
				handlePasteClipboard();
				return true;

			case R.id.sending_addresses_options_scan:
				handleScan();
				return true;

            case R.id.sending_addresses_options_reload:
                handleReloadContacts();
                return true;
		}

		return super.onOptionsItemSelected(item);
	}

    private void handleReloadContacts()
    {
        final Context context = activity;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String phoneNumber = activity.getWalletApplication().GetPhoneNumber();

        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(R.string.contacts_lookup_refresh_title);
        progressDialog.setMessage(getString(R.string.contacts_lookup_refresh_message));
        progressDialog.setCancelable(false);
        progressDialog.show();

        KnCDialog.fixDialogDivider(progressDialog);

        new ContactsDownloader(context, prefs, phoneNumber, new ContactsDownloader.ContactsDownloaderListener() {
            @Override
            public void onSuccess(int newContacts) {
                progressDialog.dismiss();
                if(newContacts > 0) {
                    Toast.makeText(context, getString(R.string.contacts_lookup_contacts_added, ""+newContacts), Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(context, R.string.contacts_lookup_success, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                progressDialog.dismiss();
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        }).doContactsLookup();
    }

	private void handlePasteClipboard()
	{
		if (clipboardManager.hasText())
		{
			final String input = clipboardManager.getText().toString().trim();

			new StringInputParser(input)
			{
				@Override
				protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
				{
					EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
				}

				@Override
				protected void directTransaction(final Transaction transaction)
				{
					cannotClassify(input);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(activity, null, R.string.address_book_options_paste_from_clipboard_title, messageResId, messageArgs);
				}
			}.parse();
		}
		else
		{
			activity.toast(R.string.address_book_options_copy_from_clipboard_msg_empty);
		}
	}

	private void handleScan()
	{
		startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id)
	{
		activity.startActionMode(new ActionMode.Callback()
		{
			@Override
			public boolean onCreateActionMode(final ActionMode mode, final Menu menu)
			{
				final MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.sending_addresses_context, menu);

                String address = getAddress(position);

                if (!AddressBookProvider.canEditAddress(activity, address))
				{
					menu.removeItem(R.id.sending_addresses_context_edit);
				}

                if(!AddressBookProvider.canDeleteAddress(activity, address)) {
                    menu.removeItem(R.id.sending_addresses_context_remove);
                }

				return true;
			}

			@Override
			public boolean onPrepareActionMode(final ActionMode mode, final Menu menu)
			{
				final String label = getLabel(position);
				mode.setTitle(label);

				return true;
			}

			@Override
			public boolean onActionItemClicked(final ActionMode mode, final MenuItem item)
			{
				switch (item.getItemId())
				{
					case R.id.sending_addresses_context_send:
						final Intent intent = new Intent(activity, WalletActivity.class);
						intent.putExtra(WalletActivity.INTENT_EXTRA_ADDRESS, getAddress(position));
						intent.putExtra(WalletActivity.INTENT_EXTRA_ADDRESS_LABEL, getLabel(position));
						mode.finish();
						activity.setResult(WalletActivity.RESULT_CODE_ADDRESSBOOK_SEND, intent);
						activity.finish();
						return true;

					case R.id.sending_addresses_context_edit:
						EditAddressBookEntryFragment.edit(getFragmentManager(), getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_remove:
						handleRemove(getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_show_qr:
						handleShowQr(getAddress(position));

						mode.finish();
						return true;

					case R.id.sending_addresses_context_copy_to_clipboard:
						handleCopyToClipboard(getAddress(position));

						mode.finish();
						return true;
				}

				return false;
			}

			@Override
			public void onDestroyActionMode(final ActionMode mode)
			{
			}

			private String getAddress(final int position)
			{
				final Cursor cursor = (Cursor) adapter.getItem(position);
				return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
			}

			private String getLabel(final int position)
			{
				final Cursor cursor = (Cursor) adapter.getItem(position);
				return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
			}
		});
	}

	private void handleRemove(final String address)
	{
		final Uri uri = AddressBookProvider.contentUri(activity.getPackageName()).buildUpon().appendPath(address).build();
		activity.getContentResolver().delete(uri, null, null);
	}

	private void handleShowQr(final String address)
	{
		final String uri = BitcoinURI.convertToBitcoinURI(address, null, null, null);
		final int size = (int) (256 * getResources().getDisplayMetrics().density);
		BitmapFragment.show(getFragmentManager(), Qr.bitmap(uri, size));
	}

	private void handleCopyToClipboard(final String address)
	{
		clipboardManager.setText(address);
		activity.toast(R.string.wallet_address_fragment_clipboard_msg);
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
        final Uri uri = AddressBookProvider.contentUri(activity.getPackageName());
        return new CursorLoader(activity, uri, null, AddressBookProvider.SELECTION_ACTIVE_STATE, new String[] { walletAddressesSelection != null ? walletAddressesSelection : "" }, AddressBookProvider.KEY_LABEL
                + " COLLATE LOCALIZED ASC");
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
	{
		adapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader)
	{
		adapter.swapCursor(null);
	}

	public void setWalletAddresses(@Nonnull final ArrayList<Address> addresses)
	{
		final StringBuilder builder = new StringBuilder();
		for (final Address address : addresses)
			builder.append(address.toString()).append(",");
		if (addresses.size() > 0)
			builder.setLength(builder.length() - 1);

		walletAddressesSelection = builder.toString();
	}
}
