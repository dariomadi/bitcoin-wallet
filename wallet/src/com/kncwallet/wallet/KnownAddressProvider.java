package com.kncwallet.wallet;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class KnownAddressProvider extends ContentProvider {

    private static final String DATABASE_TABLE = "known_address_data";

    private static final String KEY_ROWID = "_id";
    public static final String KEY_CONTACT  = "contact";
    public static final String KEY_PHONE  = "phone";
    public static final String KEY_ADDRESS  = "address";
    public static final String KEY_SOURCE  = "source";


    public static class KnownAddress
    {
        public int contactId;
        public String phone;
        public String address;
        public String source;

        public KnownAddress(int contactId, String phone, String address, String source) {
            this.contactId = contactId;
            this.phone = phone;
            this.address = address;
            this.source = source;
        }
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

    public static Uri contentUri(@Nonnull final String packageName)
    {
        return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE);
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values)
    {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String contactId = uri.getLastPathSegment();
        values.put(KEY_CONTACT, contactId);

        long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

        final Uri rowUri = contentUri(getContext().getPackageName()).buildUpon().appendPath(contactId).appendPath(Long.toString(rowId)).build();

        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    public static void contactHasNewId(final Context context, final int oldId, final int newId){

        Log.e("knc","contactHasNewId "+oldId+" => "+newId);

        List<KnownAddress> list = getKnownAddresses(context, oldId);
        if(list != null){
            Log.e("knc","list size "+list.size());
            for(KnownAddress knownAddress : list){
                Log.e("knc","save known id "+newId+" = "+knownAddress.address);
                saveKnownAddress(context, newId, knownAddress.phone, knownAddress.address, knownAddress.source);

            }

        }else{
            Log.e("knc","list null");
        }
    }

    public static void saveKnownAddress(final Context context, int contactId, String phone, String address, String source){

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(contactId+"").build();

        ContentResolver contentResolver = context.getContentResolver();

        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_ADDRESS, address);
        contentValues.put(KEY_PHONE, phone);
        if(source != null) {
            contentValues.put(KEY_SOURCE, source);
        }
        contentValues.put(KEY_CONTACT, contactId);

        contentResolver.insert(uri, contentValues);

    }

    public static Boolean knownAddressDataExists(final Context context, final int contactId) {
        return getKnownAddresses(context, contactId) != null;
    }

    public static KnownAddress getKnown(final Context context, final String address){

        KnownAddress data = null;

        final String selection = KEY_ADDRESS + " LIKE ?";
        final String[] selectionArgs = new String[]{"%"+address+"%"};

        final Uri uri = contentUri(context.getPackageName()).buildUpon().build();
        final Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, null);

        if(cursor != null){

            if(cursor.moveToLast()){
                int contactId = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CONTACT));
                String phone = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PHONE));
                String source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE));
                data = new KnownAddress(contactId, phone, address, source);
            }

        }

        return data;
    }

    public static List<KnownAddress> getKnownAddresses(final Context context, final int contactId)
    {
        List<KnownAddress> list = new ArrayList<KnownAddress>();

        final String selection = KEY_CONTACT + " LIKE ?";
        final String[] selectionArgs = new String[]{"%"+contactId+"%"};

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(""+contactId).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, null);

        if (cursor != null)
        {
            while (cursor.moveToNext()){
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CONTACT));
                String address = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ADDRESS));
                String phone = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PHONE));
                String source = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SOURCE));

                KnownAddress data = new KnownAddress(id, phone, address, source);
                list.add(data);
            }

            cursor.close();
        }

        return list.size() > 0 ? list : null;
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs)
    {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String contactId = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_CONTACT + "=?", new String[] { contactId });

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

        final String contactId = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, KEY_CONTACT + "=?", new String[] { contactId });

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

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, originalSelection, originalSelectionArgs, null, null, sortOrder);

        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }


    private static class Helper extends SQLiteOpenHelper
    {

        private static final int DATABASE_VERSION = 1;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
                + KEY_ADDRESS + " TEXT NOT NULL, " //
                + KEY_PHONE + " TEXT NULL, " //
                + KEY_CONTACT + " INTEGER, " //
                + KEY_SOURCE + " TEXT NULL);";

        public Helper(final Context context)
        {
            super(context, DATABASE_TABLE, null, DATABASE_VERSION);
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
            }
            else
            {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }
    }
}