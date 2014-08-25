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
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.support.v4.content.CursorLoader;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.gson.reflect.TypeToken;
import com.kncwallet.wallet.ContactImage;
import com.kncwallet.wallet.KnownAddressProvider;
import com.kncwallet.wallet.TransactionDataProvider;
import com.kncwallet.wallet.dto.AddressBookContact;
import com.kncwallet.wallet.dto.ContactResponse;
import com.kncwallet.wallet.dto.ContactsRequest;
import com.kncwallet.wallet.dto.ServerResponse;
import com.kncwallet.wallet.onename.OneNameAdapter;
import com.kncwallet.wallet.onename.OneNameService;
import com.kncwallet.wallet.onename.OneNameUser;
import com.kncwallet.wallet.ui.dialog.KnCDialog;
import com.kncwallet.wallet.ui.util.AnimationUtil;
import com.kncwallet.wallet.ui.view.KnCFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter.ViewBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.BalanceType;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ExchangeRatesProvider;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.ExchangeRatesProvider.ExchangeRate;
import com.kncwallet.wallet.offline.SendBluetoothTask;
import com.kncwallet.wallet.ui.InputParser.StringInputParser;
import com.kncwallet.wallet.util.AsyncWebRequest;
import com.kncwallet.wallet.util.DenominationUtil;
import com.kncwallet.wallet.util.ErrorResponse;
import com.kncwallet.wallet.util.WalletUtils;

import de.schildbach.wallet.integration.android.BitcoinIntegration;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.util.WebRequestCompleted;
import com.loopj.android.image.SmartImageView;

import android.content.ContentValues;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsFragment extends KnCFragment {
    private AbstractBindServiceActivity activity;
    private WalletApplication application;
    private Wallet wallet;
    private ContentResolver contentResolver;
    private LoaderManager loaderManager;
    private SharedPreferences prefs;
    @CheckForNull
    private BluetoothAdapter bluetoothAdapter;
    private SimpleCursorAdapter contactListAdapter;

    private int btcPrecision;
    private int btcShift;

    @CheckForNull
    private ExchangeRate exchangeRate = null;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private AutoCompleteTextView receivingAddressView;
    private View receivingStaticView;
    private TextView receivingStaticAddressView;
    private TextView receivingStaticLabelView;
    private CheckBox bluetoothEnableView;
    private ListView contactsListView;

    private TextView bluetoothMessageView;
    private ListView sentTransactionView;
    private TransactionsListAdapter sentTransactionListAdapter;
    private Button viewGo;

    private CurrencyCalculatorLink amountCalculatorLink;
    private CurrencyTextView viewBalanceBtc;
    private CurrencyTextView viewBalanceLocal;

    private MenuItem scanAction;

    private AddressAndLabel validatedAddress = null;
    private boolean isValidAmounts = false;

    @CheckForNull
    private String bluetoothMac;
    private Boolean bluetoothAck = null;

    private State state = State.INPUT;
    private Transaction sentTransaction = null;

    private static final int ID_RATE_LOADER = 0;
    private static final int ID_LIST_LOADER = 1;

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int REQUEST_CODE_ENABLE_BLUETOOTH = 1;

    public static final String INTENT_EXTRA_ADDRESS = "address";
    public static final String INTENT_EXTRA_ADDRESS_LABEL = "address_label";
    public static final String INTENT_EXTRA_AMOUNT = "amount";
    public static final String INTENT_EXTRA_BLUETOOTH_MAC = "bluetooth_mac";

    private static final Logger log = LoggerFactory.getLogger(SendCoinsFragment.class);

    private CurrencyAmountView btcAmountView;

    private EditText txText;

    private String walletAddressesSelection;

    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED, LOOKUP
    }

    private final class ReceivingAddressListener implements OnFocusChangeListener, TextWatcher {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus)
                validateReceivingAddress(true);
        }

        @Override
        public void afterTextChanged(final Editable s) {
            //dismissPopup();

            validateReceivingAddress(false);
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {

            if (s != null && s.toString().startsWith("+")) {
                receivingAddressView.setAdapter(new OneNameAdapter(activity, new OneNameAdapter.OneNameUserSelectedListener() {
                    @Override
                    public void onOneNameUserSelected(OneNameUser oneNameUser) {
                        didSelectOneNameUser(oneNameUser);
                    }
                }));
            } else {
                receivingAddressView.setAdapter(new AutoCompleteAddressAdapter(activity, null));
            }

        }
    }

    private void didSelectOneNameUser(OneNameUser oneNameUser) {

        String address = oneNameUser.getAddress();

        String label = AddressBookProvider.resolveLabel(activity, address);

        if (label == null) {
            label = oneNameUser.getDisplayName();
            EditAddressBookEntryFragment.edit(getFragmentManager(), oneNameUser);
        }

        if (setSendAddress(address, label)) {
            startReceivingAddressActionMode();
        } else {
            informInvalidAddress(address, label);
        }
    }

    private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

    private final class DummyResetActionMode implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }
    }

    private final class ReceivingAddressActionMode implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
            final MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.send_coins_address_context, menu);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            if (validatedAddress != null) {
                String title = validatedAddress.label;
                String subtitle = validatedAddress.address.toString();
                if (title == null) {
                    title = getString(R.string.address_unlabeled);
                }

                mode.setTitle(title);
                mode.setSubtitle(subtitle);

                return true;
            }

            return false;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            switch (item.getItemId()) {
                //case R.id.send_coins_address_context_edit_address:
                //	handleEditAddress();

                //	mode.finish();
                //	return true;

                case R.id.send_coins_address_context_clear:
                    handleClear();

                    mode.finish();
                    return true;
            }

            return false;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode) {
            if (receivingStaticView.hasFocus())
                amountCalculatorLink.requestFocus();
        }

        private void handleEditAddress() {
            EditAddressBookEntryFragment.edit(getFragmentManager(), validatedAddress.address.toString());
        }

        private void handleClear() {
            // switch from static to input
            validatedAddress = null;
            receivingAddressView.setText(null);
            receivingStaticAddressView.setText(null);

            updateView();

            receivingAddressView.requestFocus();
        }
    }

    private final CurrencyAmountView.Listener amountsListener = new CurrencyAmountView.Listener() {
        @Override
        public void changed() {
            //dismissPopup();

            validateAmounts(false);
        }

        @Override
        public void done() {
            validateAmounts(true);

            viewGo.requestFocusFromTouch();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (!hasFocus) {
                validateAmounts(true);
            }
        }
    };

    private final ContentObserver contentObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            updateView();
        }
    };

    private final TransactionConfidence.Listener sentTransactionConfidenceListener = new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(final Transaction tx, final TransactionConfidence.Listener.ChangeReason reason) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (sentTransaction != null) {
                        sentTransactionListAdapter.notifyDataSetChanged();

                        final TransactionConfidence confidence = sentTransaction.getConfidence();
                        final ConfidenceType confidenceType = confidence.getConfidenceType();
                        final int numBroadcastPeers = confidence.numBroadcastPeers();

                        if (state == State.SENDING) {
                            if (confidenceType == ConfidenceType.DEAD)
                                state = State.FAILED;
                            else if (numBroadcastPeers > 1 || confidenceType == ConfidenceType.BUILDING)
                                state = State.SENT;

                            updateView();
                        }

                        if (reason == ChangeReason.SEEN_PEERS && confidenceType == ConfidenceType.PENDING) {
                            // play sound effect
                            final int soundResId = getResources().getIdentifier("send_coins_broadcast_" + numBroadcastPeers, "raw",
                                    activity.getPackageName());
                            if (soundResId > 0)
                                RingtoneManager.getRingtone(activity, Uri.parse("android.resource://" + activity.getPackageName() + "/" + soundResId))
                                        .play();
                        }
                    }
                }
            });
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
                updateView();

                if (state == State.INPUT)
                    amountCalculatorLink.setExchangeRate(exchangeRate);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractBindServiceActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.wallet = application.getWallet();
        this.contentResolver = activity.getContentResolver();
        this.loaderManager = getLoaderManager();

    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        backgroundThread = new HandlerThread("backgroundThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
        btcPrecision = precision.charAt(0) - '0';
        btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;
    }

    public void switchBalance() {
        if (exchangeRate == null) {
            viewBalanceBtc.setVisibility(View.VISIBLE);
            viewBalanceLocal.setVisibility(View.GONE);
            return;
        }

        AnimationUtil.toggleViews(viewBalanceBtc, viewBalanceLocal);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.send_coins_fragment, container, false);
        contactsListView = (ListView) view.findViewById(R.id.send_coins_contacts_list);

        viewBalanceBtc = (CurrencyTextView) view.findViewById(R.id.wallet_balance_btc);

        txText = (EditText)view.findViewById(R.id.send_coins_text);

        viewBalanceLocal = (CurrencyTextView) view.findViewById(R.id.wallet_balance_local);

        viewBalanceLocal.setPrecision(Constants.LOCAL_PRECISION, 0);
        viewBalanceLocal.setStrikeThru(Constants.TEST);

        viewBalanceBtc.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                SendCoinsFragment.this.switchBalance();
            }
        });

        viewBalanceLocal.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                SendCoinsFragment.this.switchBalance();
            }
        });

        receivingAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_coins_receiving_address);
        receivingAddressView.setAdapter(new AutoCompleteAddressAdapter(activity, null));
        receivingAddressView.setOnFocusChangeListener(receivingAddressListener);
        receivingAddressView.addTextChangedListener(receivingAddressListener);

        receivingStaticView = view.findViewById(R.id.send_coins_receiving_static);
        receivingStaticAddressView = (TextView) view.findViewById(R.id.send_coins_receiving_static_address);
        receivingStaticLabelView = (TextView) view.findViewById(R.id.send_coins_receiving_static_label);

        receivingStaticView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {

                if (hasFocus) {
                    startReceivingAddressActionMode();
                } else {
                    clearActionMode();
                }
            }
        });

        btcAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_btc);
        btcAmountView.setCurrencySymbol(DenominationUtil.getCurrencyCode(btcShift));
        btcAmountView.setInputPrecision(DenominationUtil.getMaxPrecision(btcShift));
        btcAmountView.setHintPrecision(btcPrecision);
        btcAmountView.setShift(btcShift);

        final CurrencyAmountView localAmountView = (CurrencyAmountView) view.findViewById(R.id.send_coins_amount_local);
        localAmountView.setInputPrecision(Constants.LOCAL_PRECISION);
        localAmountView.setHintPrecision(Constants.LOCAL_PRECISION);
        amountCalculatorLink = new CurrencyCalculatorLink(btcAmountView, localAmountView);

        bluetoothEnableView = (CheckBox) view.findViewById(R.id.send_coins_bluetooth_enable);
        bluetoothEnableView.setChecked(bluetoothAdapter != null && bluetoothAdapter.isEnabled());
        bluetoothEnableView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                if (isChecked && !bluetoothAdapter.isEnabled()) {
                    // try to enable bluetooth
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_ENABLE_BLUETOOTH);
                }
            }
        });

        bluetoothMessageView = (TextView) view.findViewById(R.id.send_coins_bluetooth_message);

        sentTransactionView = (ListView) view.findViewById(R.id.send_coins_sent_transaction);
        sentTransactionListAdapter = new TransactionsListAdapter(activity, wallet, application.maxConnectedPeers(), false);
        sentTransactionView.setAdapter(sentTransactionListAdapter);

        viewGo = (Button) view.findViewById(R.id.send_coins_go);
        viewGo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                validateReceivingAddress(true);
                validateAmounts(true);

                if (everythingValid())
                    handleGo();
            }
        });

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            final Intent intent = activity.getIntent();
            final String action = intent.getAction();
            final Uri intentUri = intent.getData();
            final String scheme = intentUri != null ? intentUri.getScheme() : null;

            if ((Intent.ACTION_VIEW.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) && intentUri != null
                    && "bitcoin".equals(scheme))
                initStateFromBitcoinUri(intentUri);
            else if (intent.hasExtra(SendCoinsFragment.INTENT_EXTRA_ADDRESS))
                initStateFromIntentExtras(intent.getExtras());
        }

        TextView header = ((TextView) view.findViewById(R.id.header_text));

        header.setText(R.string.send_heading);

        contactListAdapter = new SimpleCursorAdapter(activity, R.layout.address_book_row_small, null, new String[]{AddressBookProvider.KEY_ADDRESS, AddressBookProvider.KEY_LABEL,
                AddressBookProvider.KEY_ADDRESS, AddressBookProvider.KEY_ADDRESS}, new int[]{R.id.address_book_contact_image, R.id.address_book_row_label, R.id.address_book_row_address, R.id.address_book_row_source_image});

        contactListAdapter.setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(final View view, final Cursor cursor, final int columnIndex) {
                if (view.getId() == R.id.address_book_contact_image) {
                    //...
                    SmartImageView img = (SmartImageView) view;
                    //img.setImageBitmap(bm);
                    String address = cursor.getString(columnIndex);
                    Bitmap contactImage = AddressBookProvider.bitmapForAddress(SendCoinsFragment.this.getActivity(), address);
                    if (contactImage != null) {
                        img.setImageBitmap(contactImage);
                    } else {

                        String imageUrl = ContactImage.getImageUrl(activity, AddressBookProvider.resolveRowId(activity, address));
                        if(imageUrl != null){
                            img.setImageUrl(imageUrl, R.drawable.contact_placeholder);
                        }else {
                            img.setImageResource(R.drawable.contact_placeholder);
                        }

                    }

                    return true; //true because the data was bound to the view
                } else if(view.getId() == R.id.address_book_row_source_image) {
                    ((ImageView)view).setImageResource(cachedSourceImageResource(cursor.getString(columnIndex)));
                    return true;
                }

                //Constants.ADDRESS_FORMAT_LINE_SIZE));

                return false;
            }
        });

        boolean smallScreen = getResources().getBoolean(R.bool.values_small);

        if(!smallScreen) {
            ListView list = (ListView) contactsListView;
            Drawable divider = getResources().getDrawable(R.drawable.transaction_list_divider);
            list.setDivider(divider);
            list.setDividerHeight(1);
            list.setAdapter(contactListAdapter);

            list.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    //to save a lookup
                    TextView addr = (TextView) view.findViewById(R.id.address_book_row_address);
                    TextView label = (TextView) view.findViewById(R.id.address_book_row_label);

                    if(setSendAddress(addr.getText().toString(), label.getText().toString())) {
                        startReceivingAddressActionMode();
                    }else{
                        informInvalidAddress(addr.getText().toString(), label.getText().toString());
                    }

                }
            });
        }else{
            contactsListView.setVisibility(View.GONE);
        }

        return view;
    }

    private Hashtable<String, Integer> sourceCache = new Hashtable<String, Integer>();
    private int cachedSourceImageResource(String address){
        Integer resource = sourceCache.get(address);
        if(resource == null){
            resource = AddressBookProvider.imageResourceForSource(activity, address);
            sourceCache.put(address, resource);

        }
        return resource;
    }

    private void startReceivingAddressActionMode() {
        activity.startActionMode(new ReceivingAddressActionMode());
    }

    private void clearActionMode() {

        if(!activity.isFinishing()) {
            ActionMode actionMode = activity.startActionMode(new DummyResetActionMode());
            actionMode.finish();
        }
    }

    private void forceHideKeyboard()
    {
        InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        View view = activity.getCurrentFocus();
        if(view!=null) {
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    protected boolean setSendAddress(String address, String label) {
        try {
            validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, address, label);
            receivingAddressView.setText(null);
            updateView();
            return true;
        } catch (AddressFormatException ex) {
            //who cares, just return
        }
        updateView();
        return false;
    }

    private void informInvalidAddress(String address, String label)
    {
        KnCDialog.Builder builder = new KnCDialog.Builder(activity);
        builder.setTitle(getString(R.string.send_invalid_address_pattern, label))
                .setMessage(""+address)
                .setPositiveButton(R.string.button_ok, null)
                .setCancelable(false)
                .show();
    }

    private void initStateFromIntentExtras(@Nonnull final Bundle extras) {
        final String address = extras.getString(SendCoinsFragment.INTENT_EXTRA_ADDRESS);
        final String addressLabel = extras.getString(SendCoinsFragment.INTENT_EXTRA_ADDRESS_LABEL);
        final BigInteger amount = (BigInteger) extras.getSerializable(SendCoinsFragment.INTENT_EXTRA_AMOUNT);
        final String bluetoothMac = extras.getString(SendCoinsFragment.INTENT_EXTRA_BLUETOOTH_MAC);

        update(address, addressLabel, amount, bluetoothMac);
    }

    private void initStateFromBitcoinUri(@Nonnull final Uri bitcoinUri) {
        final String input = bitcoinUri.toString();

        new StringInputParser(input) {
            @Override
            protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac) {
                update(address.toString(), addressLabel, amount, bluetoothMac);
            }

            @Override
            protected void directTransaction(final Transaction transaction) {
                cannotClassify(input);
            }

            @Override
            protected void error(final int messageResId, final Object... messageArgs) {
                dialog(activity, dismissListener, 0, messageResId, messageArgs);
            }

            private final DialogInterface.OnClickListener dismissListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    //activity.finish();
                }
            };
        }.parse();
    }

    @Override
    public void onResume() {
        super.onResume();

        contentResolver.registerContentObserver(AddressBookProvider.contentUri(activity.getPackageName()), true, contentObserver);

        amountCalculatorLink.setListener(amountsListener);

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

        updateView();

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                clearActionMode();
            }
        });


        createWalletAddressSelection();

        loaderManager.restartLoader(ID_LIST_LOADER, null, new LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
            {
                final Uri uri = AddressBookProvider.contentUri(activity.getPackageName());
                return new CursorLoader(activity, uri, null, AddressBookProvider.SELECTION_NOTIN,
                        new String[] { walletAddressesSelection != null ? walletAddressesSelection : "" }, AddressBookProvider.KEY_LABEL
                        + " COLLATE LOCALIZED ASC");
            }

            @Override
            public void onLoadFinished(final Loader<Cursor> loader, final Cursor data)
            {
                contactListAdapter.swapCursor(data);
            }

            @Override
            public void onLoaderReset(final Loader<Cursor> loader)
            {
                contactListAdapter.swapCursor(null);
            }
        });

    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_RATE_LOADER);
        amountCalculatorLink.setListener(null);

        contentResolver.unregisterContentObserver(contentObserver);

        super.onPause();
    }

    @Override
    public void onDetach() {
        handler.removeCallbacksAndMessages(null);

        super.onDetach();
    }

    @Override
    public void onDestroy() {
        backgroundThread.getLooper().quit();

        if (sentTransaction != null)
            sentTransaction.getConfidence().removeEventListener(sentTransactionConfidenceListener);

        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        saveInstanceState(outState);
    }

    private void saveInstanceState(final Bundle outState) {
        outState.putSerializable("state", state);

        if (validatedAddress != null)
            outState.putParcelable("validated_address", validatedAddress);

        outState.putBoolean("is_valid_amounts", isValidAmounts);

        if (sentTransaction != null)
            outState.putSerializable("sent_transaction_hash", sentTransaction.getHash());

        outState.putString("bluetooth_mac", bluetoothMac);

        if (bluetoothAck != null)
            outState.putBoolean("bluetooth_ack", bluetoothAck);
    }

    private void restoreInstanceState(final Bundle savedInstanceState) {
        state = (State) savedInstanceState.getSerializable("state");

        validatedAddress = savedInstanceState.getParcelable("validated_address");

        isValidAmounts = savedInstanceState.getBoolean("is_valid_amounts");

        if (savedInstanceState.containsKey("sent_transaction_hash")) {
            sentTransaction = wallet.getTransaction((Sha256Hash) savedInstanceState.getSerializable("sent_transaction_hash"));
            sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);
        }

        bluetoothMac = savedInstanceState.getString("bluetooth_mac");

        if (savedInstanceState.containsKey("bluetooth_ack"))
            bluetoothAck = savedInstanceState.getBoolean("bluetooth_ack");
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                new StringInputParser(input) {
                    @Override
                    protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac) {
                        update(address != null ? address.toString() : null, addressLabel, amount, null);
                    }

                    @Override
                    protected void directTransaction(final Transaction transaction) {
                        activity.processDirectTransaction(transaction);
                        //cannotClassify(input);
                    }

                    @Override
                    protected void error(final int messageResId, final Object... messageArgs) {
                        dialog(activity, null, R.string.button_scan, messageResId, messageArgs);
                    }
                }.parse();
            }
        } else if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            bluetoothEnableView.setChecked(resultCode == Activity.RESULT_OK);
        }
    }


    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.send_coins_fragment_options, menu);

        scanAction = menu.findItem(R.id.send_coins_options_scan);

        final PackageManager pm = activity.getPackageManager();
        scanAction.setVisible(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) || pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send_coins_options_scan:
                handleScan();
                return true;

            //case R.id.send_coins_options_empty:
            //	handleEmpty();
            //	return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void validateReceivingAddress(final boolean popups) {
        try {
            final String addressStr = receivingAddressView.getText().toString().trim();
            if (!addressStr.isEmpty()) {
                final NetworkParameters addressParams = Address.getParametersFromAddress(addressStr);
                if (addressParams != null && !addressParams.equals(Constants.NETWORK_PARAMETERS)) {
                    // address is valid, but from different known network
                    if (popups)
                        popupMessage(receivingAddressView,
                                getString(R.string.send_coins_fragment_receiving_address_error_cross_network, addressParams.getId()));
                } else if (addressParams == null) {
                    // address is valid, but from different unknown network
                    if (popups)
                        popupMessage(receivingAddressView, getString(R.string.send_coins_fragment_receiving_address_error_cross_network_unknown));
                } else {
                    // valid address
                    final String label = AddressBookProvider.resolveLabel(activity, addressStr);
                    validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, addressStr, label);
                    receivingAddressView.setText(null);
                }
            } else {
                // empty field should not raise error message
            }
        } catch (final AddressFormatException x) {
            // could not decode address at all
            if (popups)
                popupMessage(receivingAddressView, getString(R.string.send_coins_fragment_receiving_address_error));
        }

        updateView();
    }

    private void validateAmounts(final boolean popups) {
        isValidAmounts = false;

        final BigInteger amount = amountCalculatorLink.getAmount();

        if (amount == null) {
            // empty amount
            if (popups)
                popupMessage(amountCalculatorLink.activeView(), getString(R.string.send_coins_fragment_amount_empty));
        } else if (amount.signum() > 0) {
            final BigInteger estimated = wallet.getBalance(BalanceType.ESTIMATED);
            final BigInteger available = wallet.getBalance(BalanceType.AVAILABLE);
            final BigInteger pending = estimated.subtract(available);
            // TODO subscribe to wallet changes

            final BigInteger availableAfterAmount = available.subtract(amount);
            final boolean enoughFundsForAmount = availableAfterAmount.signum() >= 0;

            if (enoughFundsForAmount) {
                // everything fine
                isValidAmounts = true;
            } else {
                // not enough funds for amount
                //if (popups)
                //	popupAvailable(amountCalculatorLink.activeView(), available, pending);
            }
        } else {
            // invalid amount
            //if (popups)
            //	popupMessage(amountCalculatorLink.activeView(), getString(R.string.send_coins_fragment_amount_error));
        }

        updateView();
    }

    private void popupMessage(@Nonnull final View anchor, @Nonnull final String message) {
        //dismissPopup();
        /*
        popupMessageView.setText(message);
		popupMessageView.setMaxWidth(getView().getWidth());
		 */
        //popup(anchor, popupMessageView);
    }

	/*
	private void popupAvailable(@Nonnull final View anchor, @Nonnull final BigInteger available, @Nonnull final BigInteger pending)
	{
		dismissPopup();

		final CurrencyTextView viewAvailable = (CurrencyTextView) popupAvailableView.findViewById(R.id.send_coins_popup_available_amount);
		//TODO: fix this
		viewAvailable.setSuffix(btcShift == 0 ? Constants.CURRENCY_CODE_BTC : Constants.CURRENCY_CODE_MBTC);
		viewAvailable.setPrecision(btcPrecision, btcShift);
		viewAvailable.setAmount(available);

		final TextView viewPending = (TextView) popupAvailableView.findViewById(R.id.send_coins_popup_available_pending);
		viewPending.setVisibility(pending.signum() > 0 ? View.VISIBLE : View.GONE);
		final int precision = btcShift == 0 ? Constants.BTC_MAX_PRECISION : Constants.MBTC_MAX_PRECISION;
		viewPending.setText(getString(R.string.send_coins_fragment_pending, GenericUtils.formatValue(pending, precision, btcShift)));

		popup(anchor, popupAvailableView);
	}*/

	/*
	private void popup(@Nonnull final View anchor, @Nonnull final View contentView)
	{
		contentView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0), MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0));

		popupWindow = new PopupWindow(contentView, contentView.getMeasuredWidth(), contentView.getMeasuredHeight(), false);
		popupWindow.showAsDropDown(anchor);

		// hack
		contentView.setBackgroundResource(popupWindow.isAboveAnchor() ? R.drawable.popup_frame_above : R.drawable.popup_frame_below);
	}*/

	/*
	private void dismissPopup()
	{
		if (popupWindow != null)
		{
			popupWindow.dismiss();
			popupWindow = null;
		}
	}*/

    private void handleGo() {
        // create spend
        final BigInteger amount = amountCalculatorLink.getAmount();
        final SendRequest sendRequest = SendRequest.to(validatedAddress.address, amount);
        sendRequest.changeAddress = WalletUtils.pickOldestKey(wallet).toAddress(Constants.NETWORK_PARAMETERS);
        sendRequest.emptyWallet = amount.equals(wallet.getBalance(BalanceType.AVAILABLE));

        clearActionMode();
        forceHideKeyboard();


        lookupContactBeforeSend(sendRequest, validatedAddress.address.toString());

    }

    private void completeSendRequest(final SendRequest sendRequest){
        state = State.PREPARATION;
        updateView();

        new Handler().post(new Runnable() {
            @Override
            public void run() {

                try {
                    wallet.completeTx(sendRequest);

                    boolean showConfirm = true;//prefs.getBoolean(Constants.PREFS_KEY_FEE_INFO, true);

                    if (showConfirm) {
                        showConfirmDialog(sendRequest);
                    } else {
                        commitSendRequest(sendRequest);
                    }

                } catch (InsufficientMoneyException.CouldNotAdjustDownwards e) {
                    couldNotAdjustDownwardsError();
                } catch (InsufficientMoneyException e) {
                    errorSendingCoins();
                }

            }
        });
    }

    private void showConfirmDialog(final SendRequest sendRequest)
    {

        final BigInteger amount = amountCalculatorLink.getAmount();

        BigInteger fee = sendRequest.fee;

        BigInteger amountIncludingFee = amount.add(fee);

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_transaction_fee, null);

        CurrencyTextView dialogCurrencyBtc = (CurrencyTextView) view.findViewById(R.id.currency_text_view_btc);
        dialogCurrencyBtc.setPrecision(btcPrecision, btcShift);
        dialogCurrencyBtc.setAmount(amountIncludingFee);
        final String suffix = DenominationUtil.getCurrencyCode(btcShift);
        dialogCurrencyBtc.setSuffix(suffix);

        CurrencyTextView dialogCurrencyLocal = (CurrencyTextView) view.findViewById(R.id.currency_text_view_local);

        if (exchangeRate != null && exchangeRate.rate != null) {
            final BigInteger localValue = WalletUtils.localValue(amountIncludingFee, exchangeRate.rate);
            dialogCurrencyLocal.setSuffix(exchangeRate.currencyCode);
            dialogCurrencyLocal.setPrecision(Constants.LOCAL_PRECISION, 0);
            dialogCurrencyLocal.setStrikeThru(Constants.TEST);
            dialogCurrencyLocal.setAmount(localValue);

        }else{
            dialogCurrencyLocal.setVisibility(View.GONE);
        }

        String toName = validatedAddress.label;
        if(toName == null){
            toName = validatedAddress.address.toString();

            LinearLayout currencyParent = (LinearLayout)view.findViewById(R.id.currency_parent);
            currencyParent.setOrientation(LinearLayout.VERTICAL);

        }

        String textViewToText = getString(R.string.dialog_transaction_to_user, toName);

        TextView textViewTo = (TextView)view.findViewById(R.id.text_view_trail);
        textViewTo.setText(textViewToText);

        final CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkBox);

        KnCDialog.Builder builder = new KnCDialog.Builder(activity);
        builder.setTitle(getString(R.string.dialog_transaction_title, amountIncludingFee))
                .setView(view)
                .setPositiveButton(R.string.send_tab_text, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        if (checkBox.isChecked()) {
                            prefs.edit().putBoolean(Constants.PREFS_KEY_FEE_INFO, false).commit();
                        }
                        commitSendRequest(sendRequest);
                    }
                })
                .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        resetSendRequest();
                    }
                })
                .show();

    }

    private void resetSendRequest()
    {
        state = State.INPUT;
        updateView();
    }

    private void showTransactionFeeInfo()
    {
        KnCDialog.Builder builder = new KnCDialog.Builder(activity);
        builder.setTitle(R.string.dialog_fee_info_title)
                .setMessage(R.string.dialog_fee_info_message)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        handleGo();
                    }
                })
                .show();
    }

    private void saveTransactionData(Transaction transaction)
    {
        String message = null;
        String label = null;

        if(!canSendMessage()){
            label = txText.getText().toString();
        }else{
            message = txText.getText().toString();
        }

        TransactionDataProvider.saveTxData(activity, transaction.getHashAsString(), message, label);

        TransactionDataProvider.uploadTxToDirectory(activity, transaction.getHashAsString(), validatedAddress.address.toString() ,application.GetPhoneNumber(), message, label);

    }



    private void commitSendRequest(final SendRequest sendRequest){

        new SendRequestCommitTask(wallet, backgroundHandler){

            @Override
            protected void onSuccess(@Nonnull Transaction transaction) {
                sentTransaction = transaction;

                saveTransactionData(transaction);

                state = State.SENDING;
                updateView();

                sentTransaction.getConfidence().addEventListener(sentTransactionConfidenceListener);

                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && bluetoothMac != null && bluetoothEnableView.isChecked()) {
                    new SendBluetoothTask(bluetoothAdapter, backgroundHandler) {
                        @Override
                        protected void onResult(final boolean ack) {
                            bluetoothAck = ack;

                            if (state == State.SENDING)
                                state = State.SENT;

                            updateView();
                        }
                    }.send(bluetoothMac, transaction); // send asynchronously
                }

                application.broadcastTransaction(sentTransaction);

                final Intent result = new Intent();
                BitcoinIntegration.transactionHashToResult(result, sentTransaction.getHashAsString());
                activity.setResult(Activity.RESULT_OK, result);



            }

            @Override
            protected void onFailure() {
                errorSendingCoins();
            }
        }.commitOffline(sendRequest);
    }

    private void couldNotAdjustDownwardsError()
    {
        errorSendingCoins(getString(R.string.send_coins_error_adjust_downwards));
    }

    private void errorSendingCoins(){
        errorSendingCoins(getString(R.string.send_coins_error_insufficient_money));
    }

    private void errorSendingCoins(String message){
        state = State.FAILED;
        updateView();

        if(!activity.isFinishing()) {
            KnCDialog.Builder builder = new KnCDialog.Builder(activity);
            builder.setTitle(R.string.send_coins_error_msg)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            state = State.INPUT;
                            updateView();
                        }
                    })
                    .show();
        }
    }

    private void handleScan() {
        startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE_SCAN);
    }

    protected void handleEmpty() {
        final BigInteger available = wallet.getBalance(BalanceType.AVAILABLE);

        amountCalculatorLink.setBtcAmount(available);
    }


    public class AutoCompleteAddressAdapter extends CursorAdapter {
        public AutoCompleteAddressAdapter(final Context context, final Cursor c) {
            super(context, c);
        }

        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.address_book_row, parent, false);
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
            final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
            final ViewGroup viewGroup = (ViewGroup) view;
            final TextView labelView = (TextView) viewGroup.findViewById(R.id.address_book_row_label);
            labelView.setText(label);
            final TextView addressView = (TextView) viewGroup.findViewById(R.id.address_book_row_address);
            //addressView.setText(WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, Constants.ADDRESS_FORMAT_LINE_SIZE));
            addressView.setText(WalletUtils.formatHash(address, Constants.ADDRESS_FORMAT_GROUP_SIZE, 24));

            final SmartImageView img = (SmartImageView) view.findViewById(R.id.address_book_contact_image);
            String formattedAddress = address.toString();
            Bitmap contactImage = AddressBookProvider.bitmapForAddress(context, formattedAddress);
            if (contactImage != null) {
                img.setImageBitmap(contactImage);
            } else {

                String imageUrl = ContactImage.getImageUrl(activity, AddressBookProvider.resolveRowId(activity, formattedAddress));
                if(imageUrl != null){
                    img.setImageUrl(imageUrl, R.drawable.contact_placeholder);
                }else {
                    img.setImageResource(R.drawable.contact_placeholder);
                }
            }
        }

        @Override
        public CharSequence convertToString(final Cursor cursor) {
            return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
        }

        @Override
        public Cursor runQueryOnBackgroundThread(final CharSequence constraint) {
            String cons = "";
            if (constraint != null)
                cons = '%'+constraint.toString()+'%';

            String sqlNotLike = "";
            String[] args = new String[3];

            if(walletAddressesSelection != null) {
                String[] myAddresses = walletAddressesSelection.split(",");
                args = new String[myAddresses.length + 3];
                for (int i = 0; i < myAddresses.length; i++) {
                    sqlNotLike += "address NOT LIKE ? AND ";
                    args[i] = '%' + myAddresses[i] + '%';
                }
            }

            for(int i=args.length-3; i < args.length; i++){
                args[i] = cons;
            }

            String sql = sqlNotLike+"(label LIKE ? OR phone LIKE ? OR rawphone LIKE ?)";

            final Cursor cursor = contentResolver.query(AddressBookProvider.contentUri(activity.getPackageName()), null,
                    sql, args, null);
            return cursor;
        }
    }

    private void updateView() {
        //set balance
        //should probably do live updates really

        if (validatedAddress != null) {

            receivingAddressView.setVisibility(View.GONE);

            receivingStaticView.setVisibility(View.VISIBLE);
            receivingStaticAddressView.setText(WalletUtils.formatAddress(validatedAddress.address, 32,
                    32));
            final String addressBookLabel = AddressBookProvider.resolveLabel(activity, validatedAddress.address.toString());
            final String staticLabel;
            if (addressBookLabel != null) {
                staticLabel = addressBookLabel;
                receivingStaticLabelView.setText(staticLabel);
                receivingStaticLabelView.setVisibility(View.VISIBLE);
                receivingStaticAddressView.setVisibility(View.GONE);
            } else if (validatedAddress.label != null) {
                staticLabel = validatedAddress.label;
                receivingStaticLabelView.setText(staticLabel);
                receivingStaticLabelView.setVisibility(View.VISIBLE);
                receivingStaticAddressView.setVisibility(View.GONE);
            } else {
                receivingStaticLabelView.setVisibility(View.GONE);
                receivingStaticAddressView.setVisibility(View.VISIBLE);
                staticLabel = getString(R.string.address_unlabeled);
            }

        } else {
            receivingStaticView.setVisibility(View.GONE);

            receivingAddressView.setVisibility(View.VISIBLE);
        }

        receivingAddressView.setEnabled(state == State.INPUT);

        receivingStaticView.setEnabled(state == State.INPUT);

        amountCalculatorLink.setEnabled(state == State.INPUT);

        bluetoothEnableView.setVisibility(bluetoothAdapter != null && bluetoothMac != null ? View.VISIBLE : View.GONE);
        bluetoothEnableView.setEnabled(state == State.INPUT);

        if (sentTransaction != null) {
            contactsListView.setVisibility(View.GONE);
            final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
            final int btcPrecision = precision.charAt(0) - '0';
            final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

            sentTransactionView.setVisibility(View.VISIBLE);
            sentTransactionListAdapter.setPrecision(btcPrecision, btcShift);
            sentTransactionListAdapter.replace(sentTransaction);
        } else {
            contactsListView.setVisibility(View.VISIBLE);
            sentTransactionView.setVisibility(View.GONE);
            sentTransactionListAdapter.clear();
        }

        if (bluetoothAck != null) {
            bluetoothMessageView.setVisibility(View.VISIBLE);
            bluetoothMessageView.setText(bluetoothAck ? R.string.send_coins_fragment_bluetooth_ack : R.string.send_coins_fragment_bluetooth_nack);
        } else {
            bluetoothMessageView.setVisibility(View.GONE);
        }

        viewGo.setEnabled(everythingValid());

        if (state == State.INPUT) {
            viewGo.setText(R.string.send_coins_fragment_button_send);
        } else if (state == State.PREPARATION) {
            viewGo.setText(R.string.send_coins_preparation_msg);
        } else if (state == State.SENDING) {
            viewGo.setText(R.string.send_coins_sending_msg);
        } else if (state == State.SENT) {
            viewGo.setText(R.string.send_coins_sent_msg);
            reset();
            //activity.navigateHome();
            activity.toast(getString(R.string.bitcoin_sent));
        } else if (state == State.FAILED) {
            viewGo.setText(R.string.send_coins_failed_msg);
        } else if( state == State.LOOKUP){
            viewGo.setText(R.string.send_lookup_in_progress);
        }

        if (scanAction != null)
            scanAction.setEnabled(state == State.INPUT);

        updateBalanceView();

        if(!canSendMessage()){
            txText.setHint(R.string.tx_label);
        }else{
            txText.setHint(R.string.tx_message);
        }

        txText.setEnabled(state == State.INPUT);
    }

    private boolean canSendMessage()
    {
        if(prefs.getBoolean("entry_deleted", false)){
            return false;
        }
        if(validatedAddress != null) {
            String phone = AddressBookProvider.resolveTelephone(activity, validatedAddress.address.toString());
            if(phone != null){
                return true;
            }
        }

        return false;
    }


    private void updateBalanceView() {
        final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
        final int btcPrecision = precision.charAt(0) - '0';
        final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

        viewBalanceBtc.setSuffix(DenominationUtil.getCurrencyCode(btcShift));
        viewBalanceBtc.setPrecision(btcPrecision, btcShift);
        viewBalanceBtc.setAmount(wallet.getBalance(BalanceType.AVAILABLE));

        btcAmountView.setCurrencySymbol(DenominationUtil.getCurrencyCode(btcShift));
        btcAmountView.setInputPrecision(DenominationUtil.getMaxPrecision(btcShift));
        btcAmountView.setHintPrecision(btcPrecision);
        btcAmountView.setShift(btcShift);

        if (exchangeRate != null && exchangeRate.rate != null) {
            final BigInteger localValue = WalletUtils.localValue(wallet.getBalance(BalanceType.AVAILABLE), exchangeRate.rate);
            viewBalanceLocal.setSuffix(exchangeRate.currencyCode);
            viewBalanceLocal.setAmount(localValue);
            viewBalanceLocal.setTextColor(getResources().getColor(R.color.knc_highlight));
        }

        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) btcAmountView.getLayoutParams();
        if(btcShift == 6){
            layoutParams.weight = 3;
        }else{
            layoutParams.weight = 2;
        }
    }


    private void reset() {
        state = State.INPUT;
        //update(null,null,null,null);
        receivingAddressView.setText(null);
        validatedAddress = null;
        sentTransaction = null;
        amountCalculatorLink.setBtcAmount(BigInteger.valueOf(0));
        receivingAddressView.requestFocus();
        txText.setText(null);

        updateView();
    }

    private boolean everythingValid() {
        return state == State.INPUT && validatedAddress != null && isValidAmounts;
    }

    public void update(final String receivingAddress, final String receivingLabel, @Nullable final BigInteger amount,
                       @Nullable final String bluetoothMac) {
        try {
            validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, receivingAddress, receivingLabel);
            receivingAddressView.setText(null);
        } catch (final Exception x) {
            receivingAddressView.setText(receivingAddress);
            validatedAddress = null;
            log.info("problem parsing address: '" + receivingAddress + "'", x);
        }

        if (amount != null)
            amountCalculatorLink.setBtcAmount(amount);

        // focus
        if (receivingAddress != null && amount == null)
            amountCalculatorLink.requestFocus();
        else if (receivingAddress != null && amount != null)
            viewGo.requestFocus();

        this.bluetoothMac = bluetoothMac;

        bluetoothAck = null;

        updateView();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                validateReceivingAddress(true);
                validateAmounts(true);
            }
        }, 500);
    }

    @Override
    public void isShowing() {

    }

    private void createWalletAddressSelection()
    {

        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();

        final List<ECKey> keys = walletApplication.getWallet().getKeys();
        final ArrayList<Address> addresses = new ArrayList<Address>(keys.size());

        for (final ECKey key : keys)
        {
            final Address address = key.toAddress(Constants.NETWORK_PARAMETERS);
            addresses.add(address);
        }

        final StringBuilder builder = new StringBuilder();
        for (final Address address : addresses)
            builder.append(address.toString()).append(",");
        if (addresses.size() > 0)
            builder.setLength(builder.length() - 1);

        walletAddressesSelection = builder.toString();

    }

    private void lookupContactBeforeSend(final SendRequest sendRequest, final String address){

        String telephoneNumber = AddressBookProvider.resolveTelephone(activity, address);

        String source = AddressBookProvider.getSourceForAddress(activity, address);

        if(source != null && source.equals(AddressBookProvider.SOURCE_ONENAME)){
            lookupOneNameUser(sendRequest, address);
        }else if(prefs.getBoolean("entry_deleted", false)) {
            completeSendRequest(sendRequest);
        }else if(telephoneNumber != null){
            lookupContact(sendRequest, address, telephoneNumber);
        }else{
            completeSendRequest(sendRequest);
        }

    }

    private void lookupOneNameUser(final SendRequest sendRequest, final String address){
        state = State.LOOKUP;
        updateView();

        AddressBookProvider.ContactData contactData = AddressBookProvider.resolveContactData(activity, address);

        if(contactData!=null && contactData.username != null){
            OneNameService.getUserByUsername(activity, contactData.username, false, new OneNameService.OneNameServiceListener() {
                @Override
                public void onSuccess(OneNameUser user) {
                    lookupContactCheckNewAddress(sendRequest, address, user.getAddress());
                }

                @Override
                public void onError(int errorCode, String message) {
                    displayLookupError(getString(R.string.send_lookup_generic_error));
                }
            });
        }else{
            displayLookupError(getString(R.string.send_lookup_generic_error));
        }
    }


    private void lookupContact(final SendRequest sendRequest, final String address, final String telephoneNumber)
    {
        state = State.LOOKUP;
        updateView();

        List<AddressBookContact> contactList = new ArrayList<AddressBookContact>();
        contactList.add(new AddressBookContact(null, telephoneNumber, null, null));
        ContactsRequest payload = new ContactsRequest(contactList);

        String uri = Constants.API_BASE_URL;
        uri += "entries/";
        uri += application.GetPhoneNumber();
        uri += "/contacts";

        TypeToken<ServerResponse<ArrayList<ContactResponse>>> typeHelper = new TypeToken<ServerResponse<ArrayList<ContactResponse>>>(){};

        AsyncWebRequest<ContactsRequest, ArrayList<ContactResponse>> req =
                new AsyncWebRequest<ContactsRequest, ArrayList<ContactResponse>>(
                        activity,
                        uri,
                        "POST",
                        true,
                        payload,
                        typeHelper);

        req.setOnCompletedCallback(
                new WebRequestCompleted<ArrayList<ContactResponse>>() {
                    @Override
                    public void onComplete(ArrayList<ContactResponse> result) {
                        lookupContactSuccess(sendRequest, address, result);
                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err)
                    {
                        lookupContactError(err);
                    }
                }
        );

        req.execute();
    }

    private void lookupContactSuccess(final SendRequest sendRequest, final String address, ArrayList<ContactResponse> result){

        if(result != null && result.size() > 0){

            ContactResponse contactResponse = result.get(0);

            if(address.equals(contactResponse.bitcoinWalletAddress)){
                completeSendRequest(sendRequest);
            }else{
                contactHasNewAddress(sendRequest, address, contactResponse.bitcoinWalletAddress);
            }

        }else{
            displayLookupError(getString(R.string.send_lookup_generic_error));
        }
    }

    private void lookupContactCheckNewAddress(final SendRequest sendRequest, final String address, final String newAdress){
        if(newAdress != null && newAdress.equals(address)){
            completeSendRequest(sendRequest);
        }else if(newAdress != null){
            contactHasNewAddress(sendRequest, address, newAdress);
        }else{
            displayLookupError(getString(R.string.send_lookup_generic_error));
        }
    }

    private void contactHasNewAddress(final SendRequest sendRequest, final String oldAddress, final String newAddress){

        View content = activity.getLayoutInflater().inflate(R.layout.send_lookup_new_address_dialog, null);

        SmartImageView contactImage = (SmartImageView) content.findViewById(R.id.send_lookup_contact_image);
        TextView contactLabel = (TextView) content.findViewById(R.id.send_lookup_contact_label);
        TextView oldAddressTextView = (TextView) content.findViewById(R.id.send_lookup_old_address);
        TextView newAddressTextView = (TextView) content.findViewById(R.id.send_lookup_new_address);

        Bitmap bitmap = AddressBookProvider.bitmapForAddress(activity, oldAddress);
        if (bitmap != null) {
            contactImage.setImageBitmap(bitmap);
        } else {

            String imageUrl = ContactImage.getImageUrl(activity, AddressBookProvider.resolveRowId(activity, oldAddress));
            if(imageUrl != null){
                contactImage.setImageUrl(imageUrl, R.drawable.contact_placeholder);
            }else {
                contactImage.setImageResource(R.drawable.contact_placeholder);
            }
        }



        String label = AddressBookProvider.resolveLabel(activity, oldAddress);
        contactLabel.setText(label);

        oldAddressTextView.setText(WalletUtils.formatHash(oldAddress,
                Constants.ADDRESS_FORMAT_GROUP_SIZE, 24));
        newAddressTextView.setText(WalletUtils.formatHash(newAddress,
                Constants.ADDRESS_FORMAT_GROUP_SIZE, 24));

        KnCDialog.Builder builder = new KnCDialog.Builder(activity);
        builder.setTitle(R.string.send_lookup_new_address_dialog_title)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(R.string.send_lookup_save_new_address_and_send, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        saveContactsNewAddressAndSend(sendRequest, oldAddress, newAddress);
                    }
                })
                .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        state = State.INPUT;
                        updateView();
                    }
                })
                .show();
    }

    private void saveContactsNewAddressAndSend(final SendRequest sendRequest, final String oldAddress, final String newAddress){

        AddressBookProvider.ContactData oldContactData = AddressBookProvider.resolveContactData(activity, oldAddress);

        final Uri existingAddressUri = AddressBookProvider.contentUri(activity.getPackageName()).buildUpon().appendPath(oldAddress).build();
        contentResolver.delete(existingAddressUri, null, null);

        final Uri uri = AddressBookProvider.contentUri(activity.getPackageName()).buildUpon().appendPath(newAddress).build();

        final ContentValues values = new ContentValues();
        values.put(AddressBookProvider.KEY_LABEL, oldContactData.label);
        values.put(AddressBookProvider.KEY_TELEPHONE, oldContactData.phone);
        values.put(AddressBookProvider.KEY_RAW_TELEPHONE, oldContactData.rawTelephone);

        contentResolver.insert(uri, values);

        AddressBookProvider.ContactData newContactData = AddressBookProvider.resolveContactData(activity, newAddress);

        KnownAddressProvider.saveKnownAddress(activity, newContactData.id, newContactData.rawTelephone, oldAddress, AddressBookProvider.SOURCE_DIRECTORY);

        KnownAddressProvider.contactHasNewId(activity, oldContactData.id, newContactData.id);

        try {

            validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, newAddress, newContactData.label);

            final BigInteger amount = amountCalculatorLink.getAmount();
            final SendRequest newSendRequest = SendRequest.to(validatedAddress.address, amount);
            newSendRequest.changeAddress = sendRequest.changeAddress;
            newSendRequest.emptyWallet = sendRequest.emptyWallet;

            completeSendRequest(newSendRequest);

        }catch (AddressFormatException e){
            errorSendingCoins();
        }

    }

    private void lookupContactError(ErrorResponse err){
        if(err != null && err.message != null) {
            displayLookupError(err.message);
        }else{
            displayLookupError(getString(R.string.send_lookup_generic_error));
        }
    }

    private void displayLookupError(String message){
        state = State.INPUT;
        updateView();

        if(!activity.isFinishing()) {
            KnCDialog.Builder builder = new KnCDialog.Builder(activity);
            builder.setTitle(R.string.send_coins_failed_msg)
                    .setMessage(message)
                    .setPositiveButton(R.string.button_ok, null)
                    .show();
        }

    }

}
