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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ContactImage;
import com.kncwallet.wallet.KnownAddressProvider;
import com.kncwallet.wallet.onename.OneNameUser;
import com.kncwallet.wallet.ui.dialog.KnCDialog;
import com.kncwallet.wallet.util.WalletUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.kncwallet.wallet.R;
import com.loopj.android.image.SmartImageTask;
import com.loopj.android.image.WebImage;

/**
 * @author Andreas Schildbach
 */
public final class EditAddressBookEntryFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = EditAddressBookEntryFragment.class.getName();

	private static final String KEY_ADDRESS = "address";
	private static final String KEY_SUGGESTED_ADDRESS_LABEL = "suggested_address_label";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_IMAGE_URL = "image_url";
    private static final String KEY_USERNAME = "username";

    public static void edit(final FragmentManager fm, OneNameUser oneNameUser)
    {
        final EditAddressBookEntryFragment fragment = new EditAddressBookEntryFragment();

        final Bundle args = new Bundle();
        args.putString(KEY_ADDRESS, oneNameUser.getAddress());
        args.putString(KEY_SUGGESTED_ADDRESS_LABEL, oneNameUser.getDisplayName());
        args.putString(KEY_IMAGE_URL, oneNameUser.getImageUrl());
        args.putString(KEY_SOURCE, AddressBookProvider.SOURCE_ONENAME);
        args.putString(KEY_USERNAME, oneNameUser.username);
        fragment.setArguments(args);
        fragment.show(fm, FRAGMENT_TAG);
    }

	public static void edit(final FragmentManager fm, @Nonnull final String address)
	{
		edit(fm, address, null);
	}

	public static void edit(final FragmentManager fm, @Nonnull final String address, @Nullable final String suggestedAddressLabel)
	{
		final DialogFragment newFragment = EditAddressBookEntryFragment.instance(address, suggestedAddressLabel);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static EditAddressBookEntryFragment instance(@Nonnull final String address, @Nullable final String suggestedAddressLabel)
	{
		final EditAddressBookEntryFragment fragment = new EditAddressBookEntryFragment();

		final Bundle args = new Bundle();
		args.putString(KEY_ADDRESS, address);
		args.putString(KEY_SUGGESTED_ADDRESS_LABEL, suggestedAddressLabel);
		fragment.setArguments(args);

		return fragment;
	}

	private Activity activity;
	private ContentResolver contentResolver;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = activity;
		this.contentResolver = activity.getContentResolver();

	}

    @Override
    public void onStart()
    {
        super.onStart();
        KnCDialog.fixDialogDivider(getDialog());
    }

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		final Bundle args = getArguments();
		final String address = args.getString(KEY_ADDRESS);
		final String suggestedAddressLabel = args.getString(KEY_SUGGESTED_ADDRESS_LABEL);
        final String imageUrl = args.getString(KEY_IMAGE_URL);
        final String argsSource = args.getString(KEY_SOURCE);
        final String username = args.getString(KEY_USERNAME);

		final LayoutInflater inflater = LayoutInflater.from(activity);

		final Uri uri = AddressBookProvider.contentUri(activity.getPackageName()).buildUpon().appendPath(address).build();

		final String label = AddressBookProvider.resolveLabel(activity, address);

		final boolean isAdd = label == null;

		final AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
		dialog.setInverseBackgroundForced(true);
		dialog.setTitle(isAdd ? R.string.edit_address_book_entry_dialog_title_add : R.string.edit_address_book_entry_dialog_title_edit);

		final View view = inflater.inflate(R.layout.edit_address_book_entry_dialog, null);

		final TextView viewAddress = (TextView) view.findViewById(R.id.edit_address_book_entry_address);
		viewAddress.setText(WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));

		final TextView viewLabel = (TextView) view.findViewById(R.id.edit_address_book_entry_label);
		viewLabel.setText(label != null ? label : suggestedAddressLabel);

		dialog.setView(view);

		final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				if (which == DialogInterface.BUTTON_POSITIVE)
				{
					final String newLabel = viewLabel.getText().toString().trim();

					if (!newLabel.isEmpty())
					{
						final ContentValues values = new ContentValues();
						values.put(AddressBookProvider.KEY_LABEL, newLabel);
                        values.put(AddressBookProvider.KEY_USERNAME, username);
						if (isAdd) {
                            contentResolver.insert(uri, values);
                            AddressBookProvider.ContactData contactData = AddressBookProvider.resolveContactData(activity, address);
                            KnownAddressProvider.saveKnownAddress(activity, contactData.id, contactData.rawTelephone, address, argsSource);
                        }else {
                            AddressBookProvider.ContactData contactData = AddressBookProvider.resolveContactData(activity, address);

                            contentResolver.update(uri, values, null, null);
                            if(contactData != null) {
                                KnownAddressProvider.saveKnownAddress(activity, contactData.id, contactData.rawTelephone, address, null);
                            }
                        }

                        saveImage(imageUrl, newLabel, address);
					}
					else if (!isAdd)
					{
						contentResolver.delete(uri, null, null);
					}
				}
				else if (which == DialogInterface.BUTTON_NEUTRAL)
				{
					contentResolver.delete(uri, null, null);
				}

				dismiss();
			}
		};

		dialog.setPositiveButton(isAdd ? R.string.button_add : R.string.edit_address_book_entry_dialog_button_edit, onClickListener);
		if (!isAdd)
			dialog.setNeutralButton(R.string.button_delete, onClickListener);
		dialog.setNegativeButton(R.string.button_cancel, onClickListener);

		return dialog.create();
	}

    private void saveImage(final String imageUrl, final String label, final String address)
    {
        if (imageUrl != null) {
            SmartImageTask task = new SmartImageTask(activity, new WebImage(imageUrl));
            task.setOnCompleteHandler(new SmartImageTask.OnCompleteHandler() {
                @Override
                public void onComplete(Bitmap bitmap) {
                    if (bitmap != null) {
                        int contactId = AddressBookProvider.resolveRowId(activity, address);
                        ContactImage.saveBitmap(activity, bitmap, contactId, label, address, imageUrl);
                    }
                }
            });
            task.run();
        }
    }

}
