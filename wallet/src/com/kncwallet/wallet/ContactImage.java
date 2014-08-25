package com.kncwallet.wallet;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.annotation.Nonnull;

public class ContactImage {

    public static final String PREFS = "CONTACT_IMAGES";


    public static boolean saveBitmap(Context context, Bitmap sourceBitmap, final int contactId, final String label, final String address, final String imageUrl) {
        String path = saveBitmapUsingOutputStream(context, sourceBitmap, label, address);

        if(path == null){
            path = saveBitmapUsingMediaStore(context, sourceBitmap, label, address);
        }

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, 0).edit();

        if(path != null) {
            editor.putString("" + contactId, path);
        }

        editor.putString(contactId+":url",  imageUrl)
              .commit();

        Bitmap bitmap = getBitmap(context, contactId);

        return bitmap != null;
    }

    public static String getImageUrl(Context context, int contactId){
        return context.getSharedPreferences(PREFS, 0).getString(contactId+":url", null);
    }

    private static String saveBitmapUsingMediaStore(Context context, Bitmap sourceBitmap, String label, String address)
    {
        try {
            String tryInsert = MediaStore.Images.Media.insertImage(context.getContentResolver(), sourceBitmap, label, address);

            return tryInsert;
        }catch (Exception e){
            Log.e("knc",e.toString(),e);
        }
        return null;
    }

    private static String saveBitmapUsingOutputStream(Context context, Bitmap sourceBitmap, final String label, final String address) {

        ContentValues values = new ContentValues(3);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, label);
        values.put(MediaStore.Images.Media.DESCRIPTION, address);

        Uri mediaUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            OutputStream outStream = context.getContentResolver().openOutputStream(mediaUri);
            sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outStream);
            outStream.close();

            return mediaUri.toString();
        } catch (Exception e) {
            Log.e("knc",e.toString(),e);
        }

        return null;

    }

    public static Boolean contactImageExists(final Context context, final int contactId) {
        return getBitmap(context, contactId) != null;
    }

    public static Bitmap getBitmap(final Context context, final int contactId) {
        String uriString = context.getSharedPreferences(PREFS, 0).getString(contactId + "", null);
        if (uriString == null) return null;
        return getBitmap(context, Uri.parse(uriString));
    }

    private static Bitmap getBitmap(final Context context, final Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
            return bitmap;
        } catch (IOException e) {}
        return null;
    }


}
