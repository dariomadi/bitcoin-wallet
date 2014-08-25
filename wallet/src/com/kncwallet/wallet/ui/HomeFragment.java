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

import android.app.Activity;
import android.content.*;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.uri.BitcoinURI;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ExchangeRatesProvider;
import com.kncwallet.wallet.ExchangeRatesProvider.ExchangeRate;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.service.BlockchainService;
import com.kncwallet.wallet.ui.util.AnimationUtil;
import com.kncwallet.wallet.ui.view.KnCFragment;
import com.kncwallet.wallet.ui.view.TransactionsListView;
import com.kncwallet.wallet.ui.view.TransactionsListView.Direction;
import com.kncwallet.wallet.util.DenominationUtil;
import com.kncwallet.wallet.util.Nfc;
import com.kncwallet.wallet.util.ViewPagerTabs;
import com.kncwallet.wallet.util.WalletUtils;

import javax.annotation.CheckForNull;
import java.math.BigInteger;
import java.util.Date;

/**
 * @author Andreas Schildbach
 */
public final class HomeFragment extends KnCFragment {

    private WalletApplication application;
    private AbstractWalletActivity activity;
    private Wallet wallet;
    private SharedPreferences prefs;
    private LoaderManager loaderManager;

    //used to republish when address changes
    private NfcManager nfcManager;
    private TextView bitcoinAddressLabel;
    private Address lastSelectedAddress;

    private View viewBalance;
    private CurrencyTextView viewBalanceBtc;
    private CurrencyTextView viewBalanceLocal;
    private TextView viewProgress;

    private static final int REQUEST_ADDRESS = 10;

    @CheckForNull
    private BigInteger balance = null;
    @CheckForNull
    private ExchangeRate exchangeRate = null;

    private int download;
    @CheckForNull
    private Date bestChainDate = null;
    private boolean replaying = false;

    private static final int ID_BALANCE_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;
    private LinearLayout bottomLayout;
    private TransactionsListViewPagerAdapter transactionsListViewPagerAdapter;
    private boolean showInBtc = true;


    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractWalletActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.wallet = application.getWallet();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.loaderManager = getLoaderManager();
        this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);

    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.home_fragment_options, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.home_fragment_options_address_book:
                startActivityForResult(new Intent(activity, AddressBookActivity.class), REQUEST_ADDRESS);
                return true;
        }
        return true;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        //TODO: make sure this doesnt break anything!
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.home_fragment, container, false);
    }

    public void switchBalance() {
        if (exchangeRate == null) {
            viewBalanceBtc.setVisibility(View.VISIBLE);
            viewBalanceLocal.setVisibility(View.GONE);
            return;
        }

        AnimationUtil.toggleViews(viewBalanceBtc, viewBalanceLocal);

        showInBtc = !showInBtc;

        toggleCurrencyInTransactionList(true);
    }

    private void toggleCurrencyInTransactionList(boolean animate) {
        transactionsListViewPagerAdapter.notifyCurrencyChange(animate);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewBalance = view.findViewById(R.id.wallet_balance);
        viewBalance.setEnabled(false);

        viewBalanceBtc = (CurrencyTextView) view.findViewById(R.id.wallet_balance_btc);
        viewBalanceLocal = (CurrencyTextView) view.findViewById(R.id.wallet_balance_local);

        viewBalanceLocal.setPrecision(Constants.LOCAL_PRECISION, 0);
        viewBalanceLocal.setStrikeThru(Constants.TEST);

        viewBalanceBtc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                HomeFragment.this.switchBalance();
            }
        });

        viewBalanceLocal.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                HomeFragment.this.switchBalance();
            }
        });


        viewProgress = (TextView) view.findViewById(R.id.wallet_balance_progress);

        TextView header = ((TextView) view.findViewById(R.id.header_text));

        bitcoinAddressLabel = (TextView) view.findViewById(R.id.bitcoin_address_label);
        header.setText(R.string.home_heading);

        //transaction list stuff

        final ViewPagerTabs pagerTabs = (ViewPagerTabs) view.findViewById(R.id.transactions_pager_tabs);
        pagerTabs.addTabLabels(R.string.wallet_transactions_fragment_tab_sent, R.string.wallet_transactions_fragment_tab_all,
                R.string.wallet_transactions_fragment_tab_received);


        final ViewPager subViewPager = (ViewPager) view.findViewById(R.id.transactions_pager);

        transactionsListViewPagerAdapter = new TransactionsListViewPagerAdapter(activity);
        subViewPager.setAdapter(transactionsListViewPagerAdapter);
        subViewPager.setOnPageChangeListener(pagerTabs);
        subViewPager.setPageMargin(0);
        subViewPager.setCurrentItem(1);
        subViewPager.setPageMarginDrawable(R.color.knc_background_darker);

        subViewPager.setOffscreenPageLimit(3);

        pagerTabs.forceRedrawAtPosition(1);

        bottomLayout = (LinearLayout) view.findViewById(R.id.home_bottom);
        bottomLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ((WalletActivity) activity).handleExportKeys();
            }
        });

        final SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_backup)));
        text.append("\n");
        text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_safety)));

        TextView bottomBackupinfo = (TextView)bottomLayout.findViewById(R.id.home_bottom_backup_info);
        bottomBackupinfo.setText(text);
    }


    @Override
    public void onResume() {
        super.onResume();
        LoaderManager lm = getLoaderManager();
        lm.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
        lm.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

        if (broadcastReceiverIntent == null) {
            broadcastReceiverIntent = activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
        }

        activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        updateBalanceView();
        updateAddressView();
        updateBackupView();

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_RATE_LOADER);
        loaderManager.destroyLoader(ID_BALANCE_LOADER);
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);

        Nfc.unpublish(nfcManager, getActivity());

        if (broadcastReceiverIntent != null) {
            activity.unregisterReceiver(broadcastReceiver);
            broadcastReceiverIntent = null;
        }

        super.onPause();
    }

    private void updateBalanceView() {
        if (!isAdded())
            return;

        final boolean showProgress;

        if (bestChainDate != null) {
            final long blockchainLag = System.currentTimeMillis() - bestChainDate.getTime();
            final boolean blockchainUptodate = blockchainLag < Constants.BLOCKCHAIN_UPTODATE_THRESHOLD_MS;
            final boolean downloadOk = download == BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK;

            showProgress = !(blockchainUptodate || !replaying);

            final String downloading = getString(downloadOk ? R.string.blockchain_state_progress_downloading
                    : R.string.blockchain_state_progress_stalled);

            if (blockchainLag < 2 * DateUtils.DAY_IN_MILLIS) {
                final long hours = blockchainLag / DateUtils.HOUR_IN_MILLIS;
                viewProgress.setText(getString(R.string.blockchain_state_progress_hours, downloading, hours));
            } else if (blockchainLag < 2 * DateUtils.WEEK_IN_MILLIS) {
                final long days = blockchainLag / DateUtils.DAY_IN_MILLIS;
                viewProgress.setText(getString(R.string.blockchain_state_progress_days, downloading, days));
            } else if (blockchainLag < 90 * DateUtils.DAY_IN_MILLIS) {
                final long weeks = blockchainLag / DateUtils.WEEK_IN_MILLIS;
                viewProgress.setText(getString(R.string.blockchain_state_progress_weeks, downloading, weeks));
            } else {
                final long months = blockchainLag / (30 * DateUtils.DAY_IN_MILLIS);
                viewProgress.setText(getString(R.string.blockchain_state_progress_months, downloading, months));
            }
        } else {
            showProgress = false;
        }

        if (!showProgress) {

            viewBalance.setVisibility(View.VISIBLE);

            if (balance != null) {
                final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
                final int btcPrecision = precision.charAt(0) - '0';
                final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;
                final String prefix = DenominationUtil.getCurrencyCode(btcShift);

                if (viewBalanceLocal.getVisibility() != View.VISIBLE) {
                    viewBalanceBtc.setVisibility(View.VISIBLE);
                }

                viewBalanceBtc.setPrecision(btcPrecision, btcShift);
                viewBalanceBtc.setSuffix(prefix);
                viewBalanceBtc.setAmount(balance);
            } else {
                viewBalanceBtc.setVisibility(View.INVISIBLE);
            }

            viewProgress.setVisibility(View.GONE);
        } else {
            viewProgress.setVisibility(View.VISIBLE);
            viewBalance.setVisibility(View.INVISIBLE);
        }

        if (exchangeRate != null && exchangeRate.rate != null && balance != null) {
            final BigInteger localValue = WalletUtils.localValue(balance, exchangeRate.rate);
            viewBalanceLocal.setSuffix(exchangeRate.currencyCode);
            viewBalanceLocal.setAmount(localValue);
            viewBalanceLocal.setTextColor(getResources().getColor(R.color.knc_highlight));
        }
    }

    private void updateAddressView() {
        final Address selectedAddress = application.determineSelectedAddress();

        if (!selectedAddress.equals(lastSelectedAddress)) {
            lastSelectedAddress = selectedAddress;

            bitcoinAddressLabel.setText(WalletUtils.formatAddress(selectedAddress, 34,
                    34));

            final String addressStr = BitcoinURI.convertToBitcoinURI(selectedAddress, null, null, null);

            Nfc.publishUri(nfcManager, getActivity(), addressStr);
        }
    }

    private void updateBackupView()
    {
        boolean show = prefs.getBoolean(Constants.PREFS_KEY_REMIND_BACKUP, true);
        if(show){
            bottomLayout.setVisibility(View.VISIBLE);
        }else{
            bottomLayout.setVisibility(View.GONE);
        }
    }

    private Intent broadcastReceiverIntent = null;
    private final BlockchainBroadcastReceiver broadcastReceiver = new BlockchainBroadcastReceiver();

    private final class BlockchainBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            download = intent.getIntExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
            bestChainDate = (Date) intent.getSerializableExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE);
            replaying = intent.getBooleanExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_REPLAYING, false);

            updateBalanceView();
        }
    }

    private final LoaderCallbacks<BigInteger> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<BigInteger>() {
        @Override
        public Loader<BigInteger> onCreateLoader(final int id, final Bundle args) {
            return new WalletBalanceLoader(activity, wallet);
        }

        @Override
        public void onLoadFinished(final Loader<BigInteger> loader, final BigInteger balance) {
            HomeFragment.this.balance = balance;

            updateBalanceView();
        }

        @Override
        public void onLoaderReset(final Loader<BigInteger> loader) {
        }
    };

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            return new ExchangeRateLoader(activity);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null) {
                data.moveToFirst();
                exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                toggleCurrencyInTransactionList(false);
                updateBalanceView();
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    private final OnSharedPreferenceChangeListener prefsListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
            if (Constants.PREFS_KEY_SELECTED_ADDRESS.equals(key))
                updateAddressView();
        }
    };

    private class TransactionsListViewPagerAdapter extends PagerAdapter {

        private final Activity activity;
        private boolean animatedUpdates = true;

        public TransactionsListViewPagerAdapter(Activity activity) {
            this.activity = activity;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Object instantiateItem(android.view.ViewGroup container, int position) {

            TransactionsListView.Direction direction = getDirection(position);

            TransactionsListView transactionsListView = new TransactionsListView(activity, direction, getLoaderManager(), getFragmentManager(), position);
            container.addView(transactionsListView);
            return transactionsListView;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            ((TransactionsListView) view).destroy();
            collection.removeView((View) view);
        }

        @Override
        public int getItemPosition(Object object) {
            if(object instanceof TransactionsListView){
                ((TransactionsListView)object).updateUseBtc(showInBtc, exchangeRate, animatedUpdates);
            }
            return super.getItemPosition(object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        public void notifyCurrencyChange(boolean animate) {
            this.animatedUpdates = animate;
            super.notifyDataSetChanged();
        }
    }

    private static Direction getDirection(int position) {
        final Direction direction;
        if (position == 0)
            direction = Direction.SENT;
        else if (position == 1)
            direction = null;
        else if (position == 2)
            direction = Direction.RECEIVED;
        else
            throw new IllegalStateException();

        return direction;
    }

    @Override
    public void isShowing() {

    }
}
