package com.kncwallet.wallet.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.*;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.*;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ExchangeRatesProvider;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.ui.AbstractWalletActivity;
import com.kncwallet.wallet.ui.EditAddressBookEntryFragment;
import com.kncwallet.wallet.ui.TransactionsListAdapter;
import com.kncwallet.wallet.ui.WalletActivity;
import com.kncwallet.wallet.ui.util.AnimationUtil;
import com.kncwallet.wallet.ui.util.TxUtil;
import com.kncwallet.wallet.util.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.text.DateFormat;
import java.util.*;

public class TransactionsListView extends LinearLayout implements LoaderManager.LoaderCallbacks<List<Transaction>>, SharedPreferences.OnSharedPreferenceChangeListener {


    public enum Direction {
        RECEIVED, SENT
    }

    private AbstractWalletActivity activity;
    private WalletApplication application;
    private Wallet wallet;
    private SharedPreferences prefs;
    private NfcManager nfcManager;
    private ContentResolver resolver;
    private LoaderManager loaderManager;
    private FragmentManager fragmentManager;

    private TransactionsListAdapter adapter;

    @CheckForNull
    private Direction direction;

    private final Handler handler = new Handler();

    private static final String KEY_DIRECTION = "direction";
    private static final long THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;
    private static final Uri KEY_ROTATION_URI = Uri.parse("http://bitcoin.org/en/alert/2013-08-11-android");

    private final ListView listView;
    private final TextView emptyTextView;
    private final ProgressBar progressBar;

    public TransactionsListView(Activity activity, Direction direction, LoaderManager loaderManager, FragmentManager fragmentManager, int index) {
        super(activity);
        this.direction = direction;
        this.loaderManager = loaderManager;
        this.fragmentManager = fragmentManager;

        View.inflate(activity, R.layout.transactions_listview, this);

        listView = (ListView) this.findViewById(R.id.listView);
        emptyTextView = (TextView) this.findViewById(R.id.emptyTextView);
        emptyTextView.setVisibility(View.GONE);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setIndeterminate(true);

        ID_TRANSACTION_LOADER += index;

        setup(activity);

    }

    private final ContentObserver addressBookObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            adapter.clearLabelCache();
        }
    };


    public void setup(final Activity activity) {

        this.activity = (AbstractWalletActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.wallet = application.getWallet();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
        this.resolver = activity.getContentResolver();

        final boolean showBackupWarning = direction == null || direction == Direction.RECEIVED;

        adapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(), showBackupWarning);
        listView.setAdapter(adapter);


        this.listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                final Transaction tx = (Transaction) adapter.getItem(position);

                if (tx == null)
                    handleBackupWarningClick();
                else if (tx.getPurpose() == Transaction.Purpose.KEY_ROTATION)
                    handleKeyRotationClick();
                else
                    handleTransactionClick(tx);
            }
        });

        final SpannableStringBuilder emptyText = new SpannableStringBuilder(
                getString(direction == Direction.SENT ? R.string.wallet_transactions_fragment_empty_text_sent
                        : R.string.wallet_transactions_fragment_empty_text_received)
        );
        emptyText.setSpan(new StyleSpan(Typeface.BOLD), 0, emptyText.length(), SpannableStringBuilder.SPAN_POINT_MARK);
        if (direction != Direction.SENT)
            emptyText.append("\n\n").append(getString(R.string.wallet_transactions_fragment_empty_text_howto));

        Drawable divider = getResources().getDrawable(R.drawable.transaction_list_divider);
        listView.setDivider(divider);
        listView.setDividerHeight(1);

        resolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, addressBookObserver);

        prefs.registerOnSharedPreferenceChangeListener(this);

        loaderManager.restartLoader(ID_TRANSACTION_LOADER, null, this);


        wallet.addEventListener(transactionChangeListener);

        updateView();

        setEmptyText(emptyText);
    }

    public void updateUseBtc(final boolean useBtc, final ExchangeRatesProvider.ExchangeRate exchangeRate, boolean animate) {

        if(animate && AnimationUtil.shouldAnimate()){
            final long duration = getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);

            animate().alpha(0).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animate().alpha(1).setDuration(duration);
                }
            });

            postDelayed(new Runnable() {
                @Override
                public void run() {
                    adapter.updateUseBtc(useBtc, exchangeRate);
                }
            }, duration);

        }else{
            adapter.updateUseBtc(useBtc, exchangeRate);
        }
    }

    private void setEmptyText(SpannableStringBuilder emptyText) {
        emptyTextView.setText(emptyText);
    }

    public void destroy() {
        wallet.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        loaderManager.destroyLoader(ID_TRANSACTION_LOADER);

        prefs.unregisterOnSharedPreferenceChangeListener(this);

        resolver.unregisterContentObserver(addressBookObserver);
    }


    private int ID_TRANSACTION_LOADER = 3;

    private void handleTransactionClick(@Nonnull final Transaction tx) {
        activity.startActionMode(new ActionMode.Callback() {
            private Address address;
            private byte[] serializedTx;

            private static final int SHOW_QR_THRESHOLD_BYTES = 2500;

            @Override
            public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                final MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.wallet_transactions_context, menu);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                try {
                    final Date time = tx.getUpdateTime();
                    final DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(activity);
                    final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);

                    mode.setTitle(time != null ? (DateUtils.isToday(time.getTime()) ? getString(R.string.time_today) : dateFormat.format(time))
                            + ", " + timeFormat.format(time) : null);

                    final BigInteger value = tx.getValue(wallet);
                    final boolean sent = value.signum() < 0;

                    address = sent ? WalletUtils.getFirstToAddress(tx) : WalletUtils.getFirstFromAddress(tx);

                    final String label;
                    if (tx.isCoinBase())
                        label = getString(R.string.wallet_transactions_fragment_coinbase);
                    else if (address != null)
                        label = AddressBookProvider.resolveLabel(activity, address.toString());
                    else
                        label = "?";

                    final String prefix = getString(sent ? R.string.symbol_to : R.string.symbol_from) + " ";

                    if (tx.getPurpose() != Transaction.Purpose.KEY_ROTATION)
                        mode.setSubtitle(label != null ? prefix + label : WalletUtils.formatAddress(prefix, address,
                                Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
                    else
                        mode.setSubtitle(null);

                    MenuItem editIcon = menu.findItem(R.id.wallet_transactions_context_edit_address);
                    editIcon.setVisible(address != null);

                    if (address != null) {
                        if (label != null) {
                            //edit icon only if no number
                            String number = AddressBookProvider.resolveRawTelephone(activity, address.toString());
                            editIcon.setVisible(true);

                            if (number == null || number.equals("")) {
                                editIcon.setIcon(R.drawable.ic_action_edit);
                            } else {
                                editIcon.setVisible(false);
                            }
                        } else {
                            //this is an add
                            editIcon.setIcon(R.drawable.ic_action_new_label);
                            editIcon.setVisible(true);
                        }
                    }

                    serializedTx = tx.unsafeBitcoinSerialize();

                    menu.findItem(R.id.wallet_transactions_context_show_qr).setVisible(false);

                    Nfc.publishMimeObject(nfcManager, activity, Constants.MIMETYPE_TRANSACTION, serializedTx, false);

                    return true;
                } catch (final ScriptException x) {
                    return false;
                }
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.wallet_transactions_context_edit_address:
                        handleEditAddress(tx);

                        mode.finish();
                        return true;

                    case R.id.wallet_transactions_context_show_qr:
                        handleShowQr();

                        mode.finish();
                        return true;

                    case R.id.wallet_transactions_context_browse:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.EXPLORE_BASE_URL + "tx/" + tx.getHashAsString())));

                        mode.finish();
                        return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(final ActionMode mode) {
                Nfc.unpublish(nfcManager, activity);
            }

            private void handleEditAddress(@Nonnull final Transaction tx) {
                EditAddressBookEntryFragment.edit(getFragmentManager(), address.toString());
            }

            private void handleShowQr() {
                final int size = (int) (384 * getResources().getDisplayMetrics().density);
                final Bitmap qrCodeBitmap = Qr.bitmap(Qr.encodeBinary(serializedTx), size);
                BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
            }
        });
    }

    private void handleKeyRotationClick() {
        startActivity(new Intent(Intent.ACTION_VIEW, KEY_ROTATION_URI));
    }

    private void handleBackupWarningClick() {
        ((WalletActivity) activity).handleExportKeys();
    }

    @Override
    public Loader<List<Transaction>> onCreateLoader(final int id, final Bundle args) {
        return new TransactionsLoader(activity, wallet, direction);
    }

    @Override
    public void onLoadFinished(final Loader<List<Transaction>> loader, List<Transaction> transactions) {
        progressBar.setVisibility(View.GONE);

        boolean removeDust = prefs.getBoolean(Constants.PREFS_KEY_REMOVE_DUST_TX, true);

        if(removeDust){
            transactions = TxUtil.getTransactionsWithoutDust(wallet, transactions);
        }

        if (transactions != null && transactions.size() < 1) {
            emptyTextView.setVisibility(View.VISIBLE);
        }

        adapter.replace(transactions);
    }

    @Override
    public void onLoaderReset(final Loader<List<Transaction>> loader) {
        // don't clear the adapter, because it will confuse users
    }

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener(THROTTLE_MS) {
        @Override
        public void onThrottledWalletChanged() {
            adapter.notifyDataSetChanged();
        }
    };

    private static class TransactionsLoader extends AsyncTaskLoader<List<Transaction>> {
        private final Wallet wallet;
        @CheckForNull
        private final Direction direction;

        private TransactionsLoader(final Context context, @Nonnull final Wallet wallet, @Nullable final Direction direction) {
            super(context);

            this.wallet = wallet;
            this.direction = direction;
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();

            wallet.addEventListener(transactionAddRemoveListener);
            transactionAddRemoveListener.onReorganize(null); // trigger at least one reload

            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            wallet.removeEventListener(transactionAddRemoveListener);
            transactionAddRemoveListener.removeCallbacks();

            super.onStopLoading();
        }

        @Override
        public List<Transaction> loadInBackground() {
            final Set<Transaction> transactions = wallet.getTransactions(true);
            final List<Transaction> filteredTransactions = new ArrayList<Transaction>(transactions.size());

            try {
                for (final Transaction tx : transactions) {
                    final boolean sent = tx.getValue(wallet).signum() < 0;
                    if ((direction == Direction.RECEIVED && !sent) || direction == null || (direction == Direction.SENT && sent))
                        filteredTransactions.add(tx);
                }
            } catch (final ScriptException x) {
                throw new RuntimeException(x);
            }

            Collections.sort(filteredTransactions, TRANSACTION_COMPARATOR);

            return filteredTransactions;
        }

        private final ThrottlingWalletChangeListener transactionAddRemoveListener = new ThrottlingWalletChangeListener(THROTTLE_MS, true, true,
                false) {
            @Override
            public void onThrottledWalletChanged() {
                forceLoad();
            }
        };

        private static final Comparator<Transaction> TRANSACTION_COMPARATOR = new Comparator<Transaction>() {
            @Override
            public int compare(final Transaction tx1, final Transaction tx2) {
                final boolean pending1 = tx1.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                final boolean pending2 = tx2.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;

                if (pending1 != pending2)
                    return pending1 ? -1 : 1;

                final Date updateTime1 = tx1.getUpdateTime();
                final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
                final Date updateTime2 = tx2.getUpdateTime();
                final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

                if (time1 > time2)
                    return -1;
                else if (time1 < time2)
                    return 1;
                else
                    return 0;
            }
        };
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Constants.PREFS_KEY_BTC_PRECISION.equals(key))
            updateView();
    }

    private void updateView() {
        final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
        final int btcPrecision = precision.charAt(0) - '0';
        final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

        adapter.setPrecision(btcPrecision, btcShift);
        adapter.clearLabelCache();
    }

    private String getString(int resourceId) {
        return getContext().getString(resourceId);
    }

    private void startActivity(Intent intent) {
        this.activity.startActivity(intent);
    }

    private FragmentManager getFragmentManager() {
        return fragmentManager;
    }

}
