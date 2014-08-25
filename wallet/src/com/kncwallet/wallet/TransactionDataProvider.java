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
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.bitcoin.core.Transaction;
import com.google.gson.reflect.TypeToken;
import com.kncwallet.wallet.dto.RegistrationResult;
import com.kncwallet.wallet.dto.ServerResponse;
import com.kncwallet.wallet.dto.SubmitTransactionEntry;
import com.kncwallet.wallet.dto.SubmitTransactionRequest;
import com.kncwallet.wallet.dto.TransactionResult;
import com.kncwallet.wallet.dto.TransactionResultData;
import com.kncwallet.wallet.dto.UpdateTransactionLabelEntry;
import com.kncwallet.wallet.dto.UpdateTransactionLabelReceived;
import com.kncwallet.wallet.dto.UpdateTransactionLabelRequest;
import com.kncwallet.wallet.dto.UpdateTransactionLabelSent;
import com.kncwallet.wallet.util.AsyncWebRequest;
import com.kncwallet.wallet.util.ErrorResponse;
import com.kncwallet.wallet.util.WebRequestCompleted;

import java.util.List;

import javax.annotation.Nonnull;

public class TransactionDataProvider extends ContentProvider {

    private static final String DATABASE_TABLE = "transaction_data";

    private static final String KEY_ROWID = "_id";
    public static final String KEY_MESSAGE  = "message";
    public static final String KEY_LABEL  = "label";
    public static final String KEY_LOOKUP  = "lookup";
    public static final String KEY_TXID = "txid";

    public static class TxData
    {
        public String txId;
        public String message;
        public String label;

        public TxData(String message, String label)
        {
            this.message = message;
            this.label = label;
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

        final String txid = uri.getLastPathSegment();
        values.put(KEY_TXID, txid);

        long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

        final Uri rowUri = contentUri(getContext().getPackageName()).buildUpon().appendPath(txid).appendPath(Long.toString(rowId)).build();

        getContext().getContentResolver().notifyChange(rowUri, null);

        return rowUri;
    }

    public static int hasBeenLookedUp(final Context context, @Nonnull final String txid)
    {
        int n = 0;

        final String selection = KEY_TXID + " LIKE ?";
        final String[] selectionArgs = new String[]{"%"+txid+"%"};

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(txid).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, null);

        if (cursor != null)
        {
            if (cursor.moveToFirst()) {
                n = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_LOOKUP));
            }

            cursor.close();
        }

        return n;

    }

    public static void markLookedUp(final Context context, @Nonnull final String txid){
        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(txid).build();

        ContentResolver contentResolver = context.getContentResolver();

        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_LOOKUP, 1);

        if(txDataExists(context, txid)){
            contentResolver.update(uri, contentValues, null, null);
        }else{
            contentResolver.insert(uri, contentValues);
        }
    }

    public static void saveTxData(final Context context, @Nonnull final String txid, final String message, final String label){

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(txid).build();

        ContentResolver contentResolver = context.getContentResolver();

        ContentValues contentValues = new ContentValues();
        contentValues.put(KEY_LABEL, label);
        contentValues.put(KEY_MESSAGE, message);
        contentValues.put(KEY_LOOKUP, 1);

        if(txDataExists(context, txid)){
            contentResolver.update(uri, contentValues, null, null);
        }else{
            contentResolver.insert(uri, contentValues);
        }

    }

    public static Boolean txDataExists(final Context context, @Nonnull final String txid) {
        return getTxData(context, txid) != null;
    }


    public static TxData getTxData(final Context context, @Nonnull final String txid)
    {
        TxData txData = null;

        final String selection = KEY_TXID + " LIKE ?";
        final String[] selectionArgs = new String[]{"%"+txid+"%"};

        final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(txid).build();
        final Cursor cursor = context.getContentResolver().query(uri, null, selection, selectionArgs, null);

        if (cursor != null)
        {
            if (cursor.moveToFirst()) {
                String message = cursor.getString(cursor.getColumnIndexOrThrow(KEY_MESSAGE));
                String label = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LABEL));

                txData = new TxData(message, label);
            }

            cursor.close();
        }

        return txData;
    }

    public interface TransactionDataProviderDirectoryListener
    {
        public void foundData(TxData txData);
        public void noData(String txId);
    }

    public static void getTxDataFromDirectory(final Context context, final String txId, final String counterPartAddress, final boolean sent, final TransactionDataProviderDirectoryListener listener){

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if(prefs.getBoolean("entry_deleted", false)) {
            listener.noData(txId);
            return;
        }

        String uri = Constants.API_BASE_URL;
        uri += "transactions/";
        uri += txId;

        TypeToken<ServerResponse<TransactionResultData>> typeHelper = new TypeToken<ServerResponse<TransactionResultData>>(){};

        AsyncWebRequest<Void, TransactionResultData> req =
                new AsyncWebRequest<Void, TransactionResultData>(
                        context,
                        uri,
                        "GET",
                        true,
                        null,
                        typeHelper);

        req.setOnCompletedCallback(
                new WebRequestCompleted<TransactionResultData>() {
                    @Override
                    public void onComplete(TransactionResultData result) {

                        if(result != null){

                            String label;
                            if(result.senderNote != null){
                                label = result.senderNote;
                            }else{
                                label = result.receiverNote;
                            }

                            String counterPartPhone = sent ? result.sentTo : result.sentFrom;

                            String currentKnownAddress = AddressBookProvider.resolveAddress(context, counterPartPhone);

                            AddressBookProvider.ContactData contactData = AddressBookProvider.resolveContactData(context, currentKnownAddress);

                            if(contactData != null) {
                                KnownAddressProvider.saveKnownAddress(context, contactData.id, counterPartPhone, counterPartAddress, AddressBookProvider.SOURCE_DIRECTORY);
                            }

                            TransactionDataProvider.saveTxData(context, txId, result.message, label);

                            listener.foundData(TransactionDataProvider.getTxData(context, txId));

                        }else{
                            listener.noData(txId);
                        }
                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err)
                    {
                        listener.noData(txId);
                    }
                }
        );

        req.execute();


    }



    public static void updateTxLabelInDirectory(Context context, final String txId, final String note, boolean sent, final String telephoneNumber)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if(prefs.getBoolean("entry_deleted", false))
            return;

        String uri = Constants.API_BASE_URL;
        uri += "transactions/";
        uri += txId;

        UpdateTransactionLabelRequest payload = new UpdateTransactionLabelRequest();

        UpdateTransactionLabelEntry transaction;

        if(sent){
            transaction = new UpdateTransactionLabelSent(note, telephoneNumber);
        }else{
            transaction = new UpdateTransactionLabelReceived(note, telephoneNumber);
        }
        payload.transaction = transaction;

        AsyncWebRequest<UpdateTransactionLabelRequest, Void> req =
                new AsyncWebRequest<UpdateTransactionLabelRequest, Void>(
                        context,
                        uri,
                        "POST",
                        true,
                        payload,
                        null);

        req.setOnCompletedCallback(
                new WebRequestCompleted<Void>() {
                    @Override
                    public void onComplete(Void result) {

                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err)
                    {

                    }
                }
        );

        req.execute();

    }

    public static void uploadTxToDirectory(Context context, String txId, String toAddress, String phoneNumber, String message, String label)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if(prefs.getBoolean("entry_deleted", false))
            return;

        String uri = Constants.API_BASE_URL;
        uri += "transactions/";
        uri += txId;

        SubmitTransactionRequest payload = new SubmitTransactionRequest();

        SubmitTransactionEntry transactionEntry = new SubmitTransactionEntry();

        transactionEntry.sentFrom = phoneNumber;
        transactionEntry.senderNote = label;
        transactionEntry.message = message;
        transactionEntry.sentTo = AddressBookProvider.resolveTelephone(context, toAddress);

        payload.transaction = transactionEntry;

        AsyncWebRequest<SubmitTransactionRequest, Void> req =
                new AsyncWebRequest<SubmitTransactionRequest, Void>(
                        context,
                        uri,
                        "POST",
                        true,
                        payload,
                        null);

        req.setOnCompletedCallback(
                new WebRequestCompleted<Void>() {
                    @Override
                    public void onComplete(Void result) {
                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err)
                    {

                    }
                }
        );

        req.execute();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs)
    {
        if (uri.getPathSegments().size() != 1)
            throw new IllegalArgumentException(uri.toString());

        final String txid = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_TXID + "=?", new String[] { txid });

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

        final String txid = uri.getLastPathSegment();

        final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, KEY_TXID + "=?", new String[] { txid });

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
                + KEY_TXID + " TEXT NOT NULL, " //
                + KEY_MESSAGE + " TEXT NULL, " //
                + KEY_LOOKUP + " INTEGER, " //
                + KEY_LABEL + " TEXT NULL);";

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
