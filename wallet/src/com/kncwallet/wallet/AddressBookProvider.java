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

package com.kncwallet.wallet;

import java.io.InputStream;
import java.util.List;

import javax.annotation.Nonnull;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.util.Log;

/**
 * @author Andreas Schildbach
 */
public class AddressBookProvider extends ContentProvider
{
	private static final String DATABASE_TABLE = "address_book";

	public static final String KEY_ROWID = "_id";
	public static final String KEY_ADDRESS = "address";
	public static final String KEY_LABEL = "label";
	public static final String KEY_TELEPHONE = "phone";
	public static final String KEY_RAW_TELEPHONE = "rawphone";
    public static final String KEY_USERNAME = "username";

	public static final String SELECTION_QUERY = "q";
	public static final String SELECTION_IN = "in";
	public static final String SELECTION_NOTIN = "notin";
    public static final String SELECTION_COMPLEX = "c";

    public static final String SOURCE_DIRECTORY = "directory";
    public static final String SOURCE_ONENAME = "onename";

	public static Uri contentUri(@Nonnull final String packageName)
	{
		return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE);
	}

	public static String resolveLabel(final Context context, @Nonnull final String address)
	{
		String label = null;

        ContactData contactData = resolveContactData(context, address);
        if(contactData != null){
            label = contactData.label;
        }

        if(label == null){

            KnownAddressProvider.KnownAddress knownAddress = KnownAddressProvider.getKnown(context, address);
            if(knownAddress != null){

                String currentAddress = getAddress(context, knownAddress.contactId);

                return resolveLabel(context, currentAddress);
            }

        }

		return label;

	}

    public static int resolveContactId(final Context context, @Nonnull final String address)
    {
        int contactId = -1;

        ContactData contactData = resolveContactData(context, address);
        if(contactData != null){
            contactId = contactData.id;
        }

        if(contactId == -1){

            KnownAddressProvider.KnownAddress knownAddress = KnownAddressProvider.getKnown(context, address);
            if(knownAddress != null){
                String currentAddress = getAddress(context, knownAddress.contactId);
                return resolveContactId(context, currentAddress);
            }
        }
        return contactId;
    }



    public static String getAddress(final Context context, final int contactId){

        String address = null;

        final String selection = KEY_ROWID + " LIKE ?";
        final String[] selectionArgs = new String[]{"%"+contactId+"%"};

        final Uri uri = contentUri(context.getPackageName()).buildUpon().build();
        final Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, null);

        if(cursor != null){

            if(cursor.moveToFirst()){
                address = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ADDRESS));
            }

            cursor.close();

        }

        return address;
    }

    public static int resolveRowId(final Context context, @Nonnull final String address)
    {
        int rowId = -1;

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

        if (cursor != null)
        {
            if (cursor.moveToFirst())
                rowId = cursor.getInt(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ROWID));

            cursor.close();
        }

        return rowId;
    }

    public static boolean canDeleteAddress(final Context context, final String address){
        int contactId = AddressBookProvider.resolveContactId(context, address);
        String currentMainAddress = AddressBookProvider.getAddress(context, contactId);
        String number = AddressBookProvider.resolveRawTelephone(context, currentMainAddress);
        return (number == null || number.equals(""));
    }

    public static boolean canEditAddress(final Context context, final String address){
        int contactId = AddressBookProvider.resolveContactId(context, address);
        String currentMainAddress = AddressBookProvider.getAddress(context, contactId);
        String number = AddressBookProvider.resolveRawTelephone(context, currentMainAddress);
        String source = getSourceForAddress(context, address);
        return (source == null && (number == null || number.equals("")));
    }

    public static String getSourceForAddress(final Context context, final String address){
        ContactData contactData = resolveContactData(context, address);

        if(contactData != null) {
            if (contactData.rawTelephone != null) {
                return AddressBookProvider.SOURCE_DIRECTORY;
            }

            List<KnownAddressProvider.KnownAddress> knownAddresses = KnownAddressProvider.getKnownAddresses(context, contactData.id);
            if (knownAddresses != null) {
                for (KnownAddressProvider.KnownAddress knownAddress : knownAddresses) {
                    if (knownAddress.source != null && knownAddress.source.equals(AddressBookProvider.SOURCE_ONENAME)) {
                        return AddressBookProvider.SOURCE_ONENAME;
                    }else if (knownAddress.source != null && knownAddress.source.equals(AddressBookProvider.SOURCE_DIRECTORY)) {
                        return AddressBookProvider.SOURCE_DIRECTORY;
                    }
                }
            }
        }

        return null;
    }

    public static int imageResourceForSource(final Context context, final String address){

        String source = getSourceForAddress(context, address);
        if(source != null){
            if(source.equals(SOURCE_ONENAME)){
                return R.drawable.source_on;
            }else if(source.equals(SOURCE_DIRECTORY)){
                return R.drawable.source_knc;
            }
        }

        return 0;
    }

	public static String resolveTelephone(final Context context, @Nonnull final String address)
	{
		String phone = null;
		
		final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
		final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

		if (cursor != null)
		{
			if (cursor.moveToFirst())
				phone = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_TELEPHONE));

			cursor.close();
		}

		return phone;
	}

    public static class ContactData
    {
        public String address;
        public String label;
        public String phone;
        public String rawTelephone;
        public String username;
        public int id;
    }

    public static ContactData resolveContactData(final Context context, @Nonnull final String address)
    {
        ContactData data = null;

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

        if (cursor != null)
        {
            if (cursor.moveToFirst()) {
                data = new ContactData();
                data.rawTelephone = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_RAW_TELEPHONE));
                data.id = cursor.getInt(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ROWID));
                data.phone = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_TELEPHONE));
                data.address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
                data.label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
                data.username = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_USERNAME));
            }

            cursor.close();
        }

        return data;
    }

	
	public static String resolveRawTelephone(final Context context, @Nonnull final String address)
	{
		String phone = null;
		
		final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
		final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

		if (cursor != null)
		{
			if (cursor.moveToFirst())
				phone = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_RAW_TELEPHONE));

			cursor.close();
		}

		return phone;
	}
	
	public static String resolveAddress(final Context context, @Nonnull final String telephone)
	{
		//contentUri(context.getPackageName()), null,
		//AddressBookProvider.SELECTION_QUERY, new String[] { constraint.toString() }, null
		
		String address = null;
		final Uri uri = contentUri(context.getPackageName());
		final Cursor cursor = context.getContentResolver().query(uri, null, SELECTION_QUERY, new String[]{telephone}, null);
		
		if (cursor != null)
		{
			if (cursor.moveToFirst())
				address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
			
			cursor.close();
		}
		
		return address;
	}
	
	public static Boolean addressExists(final Context context, @Nonnull final String address) {
		final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
		final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
		Boolean exists = false;
		if(cursor != null) {
			exists = cursor.getCount() > 0;
			cursor.close();
		}
		return exists;
	}
	
	public static Bitmap bitmapForAddress(final Context context, @Nonnull final String address) {

        ContactData data = resolveContactData(context, address);

		if(data != null && data.rawTelephone != null && !data.rawTelephone.equals("")){
            return fetchContactPhoto(context, data.rawTelephone);
        }else if (data != null) {
            return ContactImage.getBitmap(context, data.id);
        }

        return null;
	}
	
	private static Bitmap fetchContactPhoto(Context context, String phoneNumber) 
	{
		Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
		        Uri.encode(phoneNumber));
		    Cursor cursor = context.getContentResolver().query(uri,
		        new String[] { PhoneLookup.DISPLAY_NAME, PhoneLookup._ID, PhoneLookup.PHOTO_ID },
		        null, null, null);

		    Long contactId = (long) -1;
		    Long photoId = (long) -1;
		    if(cursor != null ){
			    if (cursor.moveToFirst()) {
			        do {
			        contactId = cursor.getLong(cursor
			            .getColumnIndex(PhoneLookup._ID));
			        photoId = cursor.getLong(cursor.getColumnIndex(PhoneLookup.PHOTO_ID));
			        } while (cursor.moveToNext());
			    }
			    cursor.close();
		    }
		    return loadContactPhoto(context.getContentResolver(), contactId, photoId );
	}

	public static Bitmap loadContactPhoto(ContentResolver cr, long  id,long photo_id) 
	{
		if(id == -1)
			return null;
	    Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
	    InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri);
	    if (input != null) 
	    {
	        return BitmapFactory.decodeStream(input);
	    }
	    else
	    {
	        Log.d("PHOTO","first try failed to load photo");
	    }

	    byte[] photoBytes = null;

	    Uri photoUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, photo_id);

	    Cursor c = cr.query(photoUri, new String[] {ContactsContract.CommonDataKinds.Photo.PHOTO}, null, null, null);

	    try 
	    {
	        if (c.moveToFirst()) 
	            photoBytes = c.getBlob(0);

	    } catch (Exception e) {
	        // TODO: handle exception
	        e.printStackTrace();

	    } finally {

	        c.close();
	    }           

	    if (photoBytes != null)
	        return BitmapFactory.decodeByteArray(photoBytes,0,photoBytes.length);
	    else
	        Log.d("PHOTO","second try also failed");
	    return null;
	}

	private Helper helper;

	@Override
	public boolean onCreate()
	{
		helper = new Helper(getContext());
		return true;
	}

	@Override
	public String getType(final Uri uri)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values)
	{
		if (uri.getPathSegments().size() != 1)
			throw new IllegalArgumentException(uri.toString());

		final String address = uri.getLastPathSegment();
		values.put(KEY_ADDRESS, address);

		long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

		final Uri rowUri = contentUri(getContext().getPackageName()).buildUpon().appendPath(address).appendPath(Long.toString(rowId)).build();

		getContext().getContentResolver().notifyChange(rowUri, null);

		return rowUri;
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs)
	{
		if (uri.getPathSegments().size() != 1)
			throw new IllegalArgumentException(uri.toString());

		final String address = uri.getLastPathSegment();

		final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_ADDRESS + "=?", new String[] { address });

		if (count > 0)
			getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs)
	{
		final List<String> pathSegments = uri.getPathSegments();
		if (pathSegments.size() != 1)
			throw new IllegalArgumentException(uri.toString());

		final String address = uri.getLastPathSegment();

		final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, KEY_ADDRESS + "=?", new String[] { address });

		if (count > 0)
			getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String originalSelection, final String[] originalSelectionArgs,
			final String sortOrder)
	{
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(DATABASE_TABLE);

		final List<String> pathSegments = uri.getPathSegments();
		if (pathSegments.size() > 1)
			throw new IllegalArgumentException(uri.toString());

		String selection = null;
		String[] selectionArgs = null;

		if (pathSegments.size() == 1)
		{
			final String address = uri.getLastPathSegment();

			qb.appendWhere(KEY_ADDRESS + "=");
			qb.appendWhereEscapeString(address);
		}
		else if (SELECTION_IN.equals(originalSelection))
		{
			final String[] addresses = originalSelectionArgs[0].trim().split(",");

			qb.appendWhere(KEY_ADDRESS + " IN (");
			appendAddresses(qb, addresses);
			qb.appendWhere(")");
		}
		else if (SELECTION_NOTIN.equals(originalSelection))
		{
			final String[] addresses = originalSelectionArgs[0].trim().split(",");

			qb.appendWhere(KEY_ADDRESS + " NOT IN (");
			appendAddresses(qb, addresses);
			qb.appendWhere(")");
		}
		else if (SELECTION_QUERY.equals(originalSelection))
		{
			final String query = '%' + originalSelectionArgs[0].trim() + '%';
			//selection = KEY_ADDRESS + " LIKE ? OR " + KEY_LABEL + " LIKE ? OR " + KEY_TELEPHONE + " LIKE ? OR " + KEY_RAW_TELEPHONE + " LIKE ?";
			selection = KEY_LABEL + " LIKE ? OR " + KEY_TELEPHONE + " LIKE ? OR " + KEY_RAW_TELEPHONE + " LIKE ?";
			selectionArgs = new String[] { query, query, query };
		}else{
            selection = originalSelection;
            selectionArgs = originalSelectionArgs;
        }

		final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, selection, selectionArgs, null, null, sortOrder);

		cursor.setNotificationUri(getContext().getContentResolver(), uri);

		return cursor;
	}

	private static void appendAddresses(@Nonnull final SQLiteQueryBuilder qb, @Nonnull final String[] addresses)
	{
		for (final String address : addresses)
		{
			qb.appendWhereEscapeString(address.trim());
			if (!address.equals(addresses[addresses.length - 1]))
				qb.appendWhere(",");
		}
	}

	private static class Helper extends SQLiteOpenHelper
	{
		private static final String DATABASE_NAME = "address_book";
		private static final int DATABASE_VERSION = 2;

		private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
				+ KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
				+ KEY_ADDRESS + " TEXT NOT NULL, " //
				+ KEY_LABEL + " TEXT NULL, "
				+ KEY_TELEPHONE + " TEXT NULL, "
                + KEY_USERNAME + " TEXT NULL, "
				+ KEY_RAW_TELEPHONE + " TEXT NULL);";

		public Helper(final Context context)
		{
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(final SQLiteDatabase db)
		{
			db.execSQL(DATABASE_CREATE);
		}

		@Override
		public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion)
		{
			db.beginTransaction();
			try
			{
				for (int v = oldVersion; v < newVersion; v++)
					upgrade(db, v);

				db.setTransactionSuccessful();
			}
			finally
			{
				db.endTransaction();
			}
		}

		private void upgrade(final SQLiteDatabase db, final int oldVersion)
		{
			if (oldVersion == 1)
			{
				db.execSQL("ALTER TABLE " + DATABASE_TABLE + " ADD COLUMN " + KEY_USERNAME + " TEXT");
			}
			else
			{
				throw new UnsupportedOperationException("old=" + oldVersion);
			}
		}
	}
}
