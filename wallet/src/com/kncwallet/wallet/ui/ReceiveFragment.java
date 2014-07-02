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
import java.util.Date;

import javax.annotation.CheckForNull;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.nfc.NfcManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.ShareCompat.IntentBuilder;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.text.ClipboardManager;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.uri.BitcoinURI;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ExchangeRatesProvider;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.ExchangeRatesProvider.ExchangeRate;
import com.kncwallet.wallet.service.BlockchainService;
import com.kncwallet.wallet.ui.util.AnimationUtil;
import com.kncwallet.wallet.ui.view.KnCFragment;
import com.kncwallet.wallet.util.Bluetooth;
import com.kncwallet.wallet.util.DenominationUtil;
import com.kncwallet.wallet.util.Nfc;
import com.kncwallet.wallet.util.Qr;
import com.kncwallet.wallet.util.ViewPagerTabs;
import com.kncwallet.wallet.util.WalletUtils;

import com.kncwallet.wallet.R;


public class ReceiveFragment extends KnCFragment {

	private WalletApplication application;
	private AbstractWalletActivity activity;
	private Wallet wallet;
	private SharedPreferences prefs;
	private ClipboardManager clipboardManager;
	private ShareActionProvider shareActionProvider;
	
	private NfcManager nfcManager;
	private TextView bitcoinAddressLabel;
	private Address lastSelectedAddress;
	private LoaderManager loaderManager;
	
	private View viewBalance;
	private CurrencyTextView viewBalanceBtc;
	private CurrencyTextView viewBalanceLocal;
	private TextView viewProgress;
	private ImageView qrView;
	private Bitmap qrCodeBitmap;
	
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
	
	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.wallet = application.getWallet();
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		this.loaderManager = getLoaderManager();
		this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
	}
	
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		//TODO: make sure this doesnt break anything!
		setRetainInstance(true);
	}
	
	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.receive_fragment, container, false);
	}
	
	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater)
	{
		inflater.inflate(R.menu.request_coins_fragment_options, menu);
		
		final MenuItem shareItem = menu.findItem(R.id.request_coins_options_share);
		shareActionProvider = (ShareActionProvider) shareItem.getActionProvider();

		updateShareIntent();
		
		super.onCreateOptionsMenu(menu, inflater);
	}
	
	private void updateShareIntent()
	{
		if(lastSelectedAddress == null)
			return;
		
		if(shareActionProvider == null)
			return;
		
		// update share intent
		shareActionProvider.setShareHistoryFileName(null);
		final IntentBuilder builder = IntentBuilder.from(activity);
		builder.setText(lastSelectedAddress.toString());
		builder.setType("text/plain");
		builder.setChooserTitle(R.string.request_coins_share_dialog_title);
		shareActionProvider.setShareIntent(builder.getIntent());
	}
	
	public void switchBalance()
	{
		if(exchangeRate == null)
		{
			viewBalanceBtc.setVisibility(View.VISIBLE);
			viewBalanceLocal.setVisibility(View.GONE);
			return;
		}

        AnimationUtil.toggleViews(viewBalanceBtc, viewBalanceLocal);
	}
	
	@Override
	public void onViewCreated(final View view, final Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		viewBalance = view.findViewById(R.id.wallet_balance);
		viewBalance.setEnabled(false);
		
		viewBalanceBtc = (CurrencyTextView) view.findViewById(R.id.wallet_balance_btc);
		
		viewBalanceLocal = (CurrencyTextView) view.findViewById(R.id.wallet_balance_local);
		
		viewBalanceLocal.setPrecision(Constants.LOCAL_PRECISION, 0);
		viewBalanceLocal.setStrikeThru(Constants.TEST);

		viewBalanceBtc.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				ReceiveFragment.this.switchBalance();
			}
		});
		
		viewBalanceLocal.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				ReceiveFragment.this.switchBalance();
			}
		});
		
		viewProgress = (TextView) view.findViewById(R.id.wallet_balance_progress);
		
		qrView = (ImageView) view.findViewById(R.id.request_coins_qr);
        qrView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                shareThisAddress();
            }
        });
		
		TextView header = ((TextView) view.findViewById(R.id.header_text));
		
		bitcoinAddressLabel = (TextView) view.findViewById(R.id.bitcoin_address_label);

        if(Build.VERSION.SDK_INT < 14){
            bitcoinAddressLabel.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    shareThisAddress();
                }
            });
        }

		header.setText(R.string.receive_heading);

        final Button shareButton = (Button) view.findViewById(R.id.button_share_this_address);
        shareButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                shareThisAddress();
            }
        });
		 
		 setHasOptionsMenu(true);
	}

    private void shareThisAddress() {
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, lastSelectedAddress.toString());
        shareIntent.setType("text/plain");
        getActivity().startActivity(Intent.createChooser(shareIntent, getString(R.string.share_this_address)));
    }

    @Override
	public void onResume()
	{
		super.onResume();

		if(broadcastReceiverIntent == null)
		{
			broadcastReceiverIntent = activity.registerReceiver(broadcastReceiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
		}
		
		prefs.registerOnSharedPreferenceChangeListener(prefsListener);
		
		loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
		loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

		updateBalanceView();
		updateAddressView(true);
		//updateShareIntent();
	}
	
	@Override
	public void onPause()
	{
		loaderManager.destroyLoader(ID_BALANCE_LOADER);
		loaderManager.destroyLoader(ID_RATE_LOADER);

		if(broadcastReceiverIntent != null)
		{
			activity.unregisterReceiver(broadcastReceiver);
			broadcastReceiverIntent = null;
		}

		prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);

		Nfc.unpublish(nfcManager, getActivity());
		
		super.onPause();
	}
	
	private void updateBalanceView()
	{
		if (!isAdded())
			return;

		final boolean showProgress;

		if (bestChainDate != null)
		{
			final long blockchainLag = System.currentTimeMillis() - bestChainDate.getTime();
			final boolean blockchainUptodate = blockchainLag < Constants.BLOCKCHAIN_UPTODATE_THRESHOLD_MS;
			final boolean downloadOk = download == BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK;

			showProgress = !(blockchainUptodate || !replaying);

			final String downloading = getString(downloadOk ? R.string.blockchain_state_progress_downloading
					: R.string.blockchain_state_progress_stalled);

			if (blockchainLag < 2 * DateUtils.DAY_IN_MILLIS)
			{
				final long hours = blockchainLag / DateUtils.HOUR_IN_MILLIS;
				viewProgress.setText(getString(R.string.blockchain_state_progress_hours, downloading, hours));
			}
			else if (blockchainLag < 2 * DateUtils.WEEK_IN_MILLIS)
			{
				final long days = blockchainLag / DateUtils.DAY_IN_MILLIS;
				viewProgress.setText(getString(R.string.blockchain_state_progress_days, downloading, days));
			}
			else if (blockchainLag < 90 * DateUtils.DAY_IN_MILLIS)
			{
				final long weeks = blockchainLag / DateUtils.WEEK_IN_MILLIS;
				viewProgress.setText(getString(R.string.blockchain_state_progress_weeks, downloading, weeks));
			}
			else
			{
				final long months = blockchainLag / (30 * DateUtils.DAY_IN_MILLIS);
				viewProgress.setText(getString(R.string.blockchain_state_progress_months, downloading, months));
			}
		}
		else
		{
			showProgress = false;
		}

		if (!showProgress)
		{
			viewBalance.setVisibility(View.VISIBLE);

			if (balance != null)
			{
				final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
				final int btcPrecision = precision.charAt(0) - '0';
				final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;
				final String prefix = DenominationUtil.getCurrencyCode(btcShift);

				viewBalanceBtc.setVisibility(View.VISIBLE);
				viewBalanceBtc.setPrecision(btcPrecision, btcShift);
				viewBalanceBtc.setSuffix(prefix);
				viewBalanceBtc.setAmount(balance);
			}
			else
			{
				viewBalanceBtc.setVisibility(View.INVISIBLE);
			}

			viewProgress.setVisibility(View.GONE);
		}
		else
		{
			viewProgress.setVisibility(View.VISIBLE);
			viewBalance.setVisibility(View.INVISIBLE);
		}
		
		if (exchangeRate != null && exchangeRate.rate != null && balance != null)
		{
			final BigInteger localValue = WalletUtils.localValue(balance, exchangeRate.rate);
			viewBalanceLocal.setSuffix(exchangeRate.currencyCode);
			viewBalanceLocal.setAmount(localValue);
			viewBalanceLocal.setTextColor(getResources().getColor(R.color.knc_highlight));
		}
	}
	
	private void handleCopy()
	{
		final String selectedAddress = lastSelectedAddress.toString();
		clipboardManager.setText(selectedAddress);
		activity.toast(R.string.receive_clipboard_message);
	}
	
	private void handleEmail()
	{
		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("message/rfc822");
		//i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"recipient@example.com"});
		i.putExtra(Intent.EXTRA_SUBJECT, "My bitcoin address");
		i.putExtra(Intent.EXTRA_TEXT   , "My bitcoin address is: " + lastSelectedAddress.toString() + "\n\n\nDoes someone want to give me some copy for this???");
		try {
		    startActivity(Intent.createChooser(i, "Send mail..."));
		} catch (android.content.ActivityNotFoundException ex) {
		    activity.toast("There are no email clients installed.");
		}
	}
	
	private void updateAddressView(Boolean force)
	{
		final Address selectedAddress = application.determineSelectedAddress();

		if (!selectedAddress.equals(lastSelectedAddress) || force)
		{
			lastSelectedAddress = selectedAddress;

			bitcoinAddressLabel.setText(WalletUtils.formatAddress(selectedAddress, 34,
					34));

			final String addressStr = BitcoinURI.convertToBitcoinURI(selectedAddress, null, null, null);

			// update qr code
			final int size = (int) (256 * getResources().getDisplayMetrics().density);
			qrCodeBitmap = Qr.bitmap(addressStr, size);
			qrView.setImageBitmap(qrCodeBitmap);
			
			Nfc.publishUri(nfcManager, getActivity(), addressStr);
		}
	}
	
	Intent broadcastReceiverIntent = null;
	private final BlockchainBroadcastReceiver broadcastReceiver = new BlockchainBroadcastReceiver();

	private final class BlockchainBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			download = intent.getIntExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD, BlockchainService.ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			bestChainDate = (Date) intent.getSerializableExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE);
			replaying = intent.getBooleanExtra(BlockchainService.ACTION_BLOCKCHAIN_STATE_REPLAYING, false);

			updateBalanceView();
		}
	}

	private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>()
	{
		@Override
		public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
		{
			return new ExchangeRateLoader(activity);
		}

		@Override
		public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
		{
			if (data != null)
			{
				data.moveToFirst();
				exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
				updateBalanceView();
			}
		}

		@Override
		public void onLoaderReset(final Loader<Cursor> loader)
		{
		}
	};
	
	private final LoaderCallbacks<BigInteger> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<BigInteger>()
	{
		@Override
		public Loader<BigInteger> onCreateLoader(final int id, final Bundle args)
		{
			return new WalletBalanceLoader(activity, wallet);
		}

		@Override
		public void onLoadFinished(final Loader<BigInteger> loader, final BigInteger balance)
		{
			ReceiveFragment.this.balance = balance;

			updateBalanceView();
		}

		@Override
		public void onLoaderReset(final Loader<BigInteger> loader)
		{
		}
	};

	private final OnSharedPreferenceChangeListener prefsListener = new OnSharedPreferenceChangeListener()
	{
		@Override
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (Constants.PREFS_KEY_SELECTED_ADDRESS.equals(key))
			{
				updateAddressView(false);
				updateShareIntent();
			}
		}
	};

    @Override
    public void isShowing() {

    }
	
}
