package com.kncwallet.wallet.onename;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import java.util.List;


public class OneNameCacheProvider extends ContentProvider {

    public static final long CACHE_TIME = 1000 * 60 * 30;

    private static final String DATABASE_TABLE = "onename_cache_data_a";

    private static final String KEY_ROWID = "_id";
    public static final String KEY_RESPONSE = "response";
    public static final String KEY_CODE = "code";
    public static final String KEY_REQUEST = "request";
    public static final String KEY_TIMESTAMP = "timestamp";

    private Helper helper;

    @Override
    public boolean onCreate() {
        helper = new Helper(getContext());
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    public static Uri contentUri(final String packageName) {
        return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE);
    }


    public static void cacheResponse(final Context context, final String request, final int code, final String response){

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(request).build();

        ContentResolver contentResolver = context.getContentResolver();

        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_RESPONSE, response);
        contentValues.put(KEY_CODE, code);
        contentValues.put(KEY_TIMESTAMP, System.currentTimeMillis());

        if(cacheDataExists(context, request)){
            contentResolver.update(uri, contentValues, null, null);
        }else{
            contentResolver.insert(uri, contentValues);
        }

    }

    public static Boolean cacheDataExists(final Context context, final String request) {
        return getCacheData(context, request) != null;
    }


    public static OneNameCacheData getCacheData(final Context context, final String request)
    {

        OneNameCacheData data = null;

        final String selection = KEY_REQUEST + " LIKE ?";
        final String[] selectionArgs = new String[]{"%"+request+"%"};

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(request).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, null);

        if (cursor != null)
        {
            if (cursor.moveToFirst()) {
                String response = cursor.getString(cursor.getColumnIndexOrThrow(KEY_RESPONSE));
                int code = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CODE));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));

                data = new OneNameCacheData(response, code, timestamp);
            }

            cursor.close();
        }

        if(data != null){
            long diff = System.currentTimeMillis() - data.timestamp;
            if(diff > CACHE_TIME){
                context.getContentResolver().delete(uri, null, null);
                return null;
            }
        }

        return data;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String request = uri.getLastPathSegment();
        values.put(KEY_REQUEST, request);

        long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

        final Uri rowUri = contentUri(getContext().getPackageName()).buildUpon().appendPath(request).appendPath(Long.toString(rowId)).build();

        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }


    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String request = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_REQUEST + "=?", new String[]{request});

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String request = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, KEY_REQUEST + "=?", new String[]{request});

        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String originalSelection, final String[] originalSelectionArgs,
                        final String sortOrder) {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() > 1)
            throw new IllegalArgumentException(uri.toString());

        final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, originalSelection, originalSelectionArgs, null, null, sortOrder);

        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }


    private static class Helper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 1;

        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
                + KEY_REQUEST + " TEXT NULL, " //
                + KEY_CODE + " INTEGER, " //
                + KEY_TIMESTAMP + " LONG, " //
                + KEY_RESPONSE + " TEXT NULL);";

        public Helper(final Context context) {
            super(context, DATABASE_TABLE, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            db.beginTransaction();
            try {
                for (int v = oldVersion; v < newVersion; v++)
                    upgrade(db, v);

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private void upgrade(final SQLiteDatabase db, final int oldVersion) {
            if (oldVersion == 1) {
            } else {
                throw new UnsupportedOperationException("old=" + oldVersion);
            }
        }
    }
}
