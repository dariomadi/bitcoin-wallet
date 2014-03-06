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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.annotation.SuppressLint;
import android.app.ActionBar.TabListener;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.dto.AddressBookContact;
import com.kncwallet.wallet.dto.AddressPatchRequest;
import com.kncwallet.wallet.dto.ContactResponse;
import com.kncwallet.wallet.dto.ContactsRequest;
import com.kncwallet.wallet.dto.RegistrationEntry;
import com.kncwallet.wallet.dto.RegistrationRequest;
import com.kncwallet.wallet.dto.RegistrationResult;
import com.kncwallet.wallet.dto.ServerResponse;
import com.kncwallet.wallet.ui.InputParser.BinaryInputParser;
import com.kncwallet.wallet.ui.InputParser.StringInputParser;
import com.kncwallet.wallet.util.AsyncWebRequest;
import com.kncwallet.wallet.util.ContactsFetcher;
import com.kncwallet.wallet.util.ContactsRetrieved;
import com.kncwallet.wallet.util.CrashReporter;
import com.kncwallet.wallet.util.Crypto;
import com.kncwallet.wallet.util.ErrorResponse;
import com.kncwallet.wallet.util.HttpGetThread;
import com.kncwallet.wallet.util.Iso8601Format;
import com.kncwallet.wallet.util.Nfc;
import com.kncwallet.wallet.util.WalletUtils;
import com.kncwallet.wallet.util.WebRequestCompleted;

import com.kncwallet.wallet_test.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletActivity extends AbstractBindServiceActivity implements TabListener, ComponentCallbacks2
{
	private static final int DIALOG_IMPORT_KEYS = 0;
	private static final int DIALOG_EXPORT_KEYS = 1;
	private static final int DIALOG_ALERT_OLD_SDK = 2;

	private WalletApplication application;
	private Wallet wallet;
	private SharedPreferences prefs;
	
	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;
	
	public static final int RESULT_CODE_ADDRESSBOOK_SEND = 123;
	
	public static final String INTENT_EXTRA_ADDRESS = "address";
	public static final String INTENT_EXTRA_ADDRESS_LABEL = "address_label";
	public static final String INTENT_EXTRA_AMOUNT = "amount";
	public static final String INTENT_EXTRA_BLUETOOTH_MAC = "bluetooth_mac";
	

	private static final int REQUEST_CODE_SCAN = 0;
	
	//this is called when the app gets backgrounded, 
	//if that has happened, we need to show the pin
	//entry screen on launch
	public void onTrimMemory(int level)
	{
		if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
		{
			//app moved to background
			IsRestartingFromBackground = true;
		}
	}
	
	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = getWalletApplication();
		wallet = application.getWallet();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.wallet_activity);

		if (savedInstanceState == null)
			checkAlerts();
		
		touchLastUsed();

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
		
		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
		
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		
		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager
				.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						actionBar.setSelectedNavigationItem(position);
					}
				});
		
		//keep all 3 tabs in memory - this might be a bad idea, but only 1 more than default
		mViewPager.setOffscreenPageLimit(3);
		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}
				
		actionBar.setSelectedNavigationItem(1);
		actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
		actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
		actionBar.setIcon(R.drawable.ic_knclogo);
		
		prefs.registerOnSharedPreferenceChangeListener(prefsListener);
		
		handleIntent(getIntent());
		
	}
	
	private final OnSharedPreferenceChangeListener prefsListener = new OnSharedPreferenceChangeListener()
	{
		@Override
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (Constants.PREFS_KEY_SELECTED_ADDRESS.equals(key))
			{
				final Address selectedAddress = application.determineSelectedAddress();
				doAddressPatch(selectedAddress);
			}
		}
	};
	
	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}
	
	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}
	
	//submit a change in default address to the server
	public void doAddressPatch(Address addr)
	{	
		if(prefs.getBoolean("entry_deleted", false))
			return;
		
		String uri = Constants.API_BASE_URL;
		uri += "entries/";
		uri += application.GetPhoneNumber();
		
		AddressPatchRequest payload;
		payload = new AddressPatchRequest(addr.toString());
		
		AsyncWebRequest<AddressPatchRequest, Void> req = 
				new AsyncWebRequest<AddressPatchRequest, Void>(
									getBaseContext(),
									uri,
									"PATCH",
									true,
									payload,
									null);
		
		req.setOnCompletedCallback(
			new WebRequestCompleted<Void>() {
				@Override
				public void onComplete(Void result) {
					//WelcomeActivity.this.handleSMSResent();
					//WalletActivity.this.saveMatchingContacts(result);
					Toast.makeText(WalletActivity.this, "Directory entry updated", Toast.LENGTH_LONG).show();
				}
					
				@Override
				public void onErrorOccurred(ErrorResponse err)
				{
					Toast.makeText(WalletActivity.this, "Error downloading updating directory", Toast.LENGTH_SHORT).show();
					//WelcomeActivity.this.displayError(String.format("%s (%d)", err.message, err.code));
				}
			}
		);

		req.execute();
	}
	
	private boolean isNetworkAvailable() {
	    android.net.ConnectivityManager connectivityManager 
	          = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
	
	//get the list of local contacts from the address book
	//on complete, look them up on the server
	public void doContactsLookup()
	{
		final boolean hasNetwork = isNetworkAvailable();
		if(!hasNetwork)
			return;
		
		if(prefs.getBoolean("entry_deleted", false))
			return;
		
		ContactsFetcher fetcher = new ContactsFetcher(getBaseContext(), application.GetPhoneNumber());
		fetcher.setOnCompletedCallback(new ContactsRetrieved() {
			@Override
			public void onContactsRetrieved(List<AddressBookContact> contacts)
			{
				WalletActivity.this.lookupRemoteContacts(contacts);
			}

			@Override
			public void onErrorOccurred() {
				// TODO Auto-generated method stub
				Toast.makeText(WalletActivity.this, "Failed to load Contacts.", Toast.LENGTH_SHORT).show();
			}
		});
		
		fetcher.execute();
	}
	
	//send web request to the server with contacts
	//on complete, save them locally
	private List<AddressBookContact> requestedContacts;
	public void lookupRemoteContacts(List<AddressBookContact> contacts)
	{	
		requestedContacts = contacts;
		
		if(requestedContacts == null)
			return;
		
		ContactsRequest payload = new ContactsRequest(contacts);
		
		String uri = Constants.API_BASE_URL;
		uri += "entries/";
		uri += application.GetPhoneNumber();
		uri += "/contacts";
		
		TypeToken<ServerResponse<ArrayList<ContactResponse>>> typeHelper = new TypeToken<ServerResponse<ArrayList<ContactResponse>>>(){};
		
		AsyncWebRequest<ContactsRequest, ArrayList<ContactResponse>> req = 
				new AsyncWebRequest<ContactsRequest, ArrayList<ContactResponse>>(
									getBaseContext(),
									uri,
									"POST",
									true,
									payload,
									typeHelper);
		
		req.setOnCompletedCallback(
			new WebRequestCompleted<ArrayList<ContactResponse>>() {
				@Override
				public void onComplete(ArrayList<ContactResponse> result) {
					//WelcomeActivity.this.handleSMSResent();
					WalletActivity.this.saveMatchingContacts(result);
				}
					
				@Override
				public void onErrorOccurred(ErrorResponse err)
				{
					Toast.makeText(WalletActivity.this, "Error downloading contacts", Toast.LENGTH_SHORT).show();
					//WelcomeActivity.this.displayError(String.format("%s (%d)", err.message, err.code));
				}
			}
		);

		req.execute();
	}
	
	//Save the contacts we just downloaded
	//TODO: make this async!
	public void saveMatchingContacts(ArrayList<ContactResponse> matchedContacts)
	{
		int newContacts = 0;
		for(ContactResponse item : matchedContacts)
		{
			for(AddressBookContact contact : requestedContacts)
			{
				if(contact.TelephoneNumber.equals(item.telephoneNumber))
				{
					//something that we requested returned a match!
					final Uri uri = AddressBookProvider.contentUri(WalletActivity.this.getPackageName()).buildUpon().appendPath(item.bitcoinWalletAddress).build();
					Boolean isAdd = true;
					
					ContentResolver contentResolver = getContentResolver();
					
					//if the address is already in there, just update its stuff
					if(AddressBookProvider.addressExists(WalletActivity.this, item.bitcoinWalletAddress))
						isAdd = false;
					
					String existingAddressForNumber = AddressBookProvider.resolveAddress(WalletActivity.this, item.telephoneNumber);
					//if the phone number is already in there
					if(existingAddressForNumber != null)
					{
						//and it is different
						if(!existingAddressForNumber.equals(item.bitcoinWalletAddress))
						{
							//delete the old address
							final Uri existingAddressUri = AddressBookProvider.contentUri(WalletActivity.this.getPackageName()).buildUpon().appendPath(existingAddressForNumber).build();
							contentResolver.delete(existingAddressUri, null, null);
							
							//don't count this as new when displaying to user
							newContacts--;
							//we will then just treat the new one as normal
						}
					}

					final ContentValues values = new ContentValues();
					values.put(AddressBookProvider.KEY_LABEL, contact.Label);
					values.put(AddressBookProvider.KEY_TELEPHONE, contact.TelephoneNumber);
					values.put(AddressBookProvider.KEY_RAW_TELEPHONE, contact.RawNumber);
					
					if (isAdd)
					{
						contentResolver.insert(uri, values);
						newContacts++;
					} else
					{	
						contentResolver.update(uri, values, null, null);
						break;
					}
				}
			}
		}
		
		if(newContacts > 0)
			Toast.makeText(WalletActivity.this, newContacts + " new contacts added", Toast.LENGTH_SHORT).show();
			
	}

	public static Boolean IsRestartingFromBackground = false;
	public static Boolean IsFirstRun = true;
	
	@Override
	public void onBackPressed()
	{
		//make sure we show app pin when they back
		//out to home screen then return
		IsRestartingFromBackground = true;
		super.onBackPressed();
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		
		if(IsRestartingFromBackground || IsFirstRun)
		{
			if(prefs.getBoolean("app_pin_enabled", false))
				startActivity(new Intent(this, PinEntryActivity.class));
		}
		IsRestartingFromBackground = false;
		
		if(!prefs.getBoolean("registrationComplete", false))
		{
			startActivity(new Intent(this, WelcomeActivity.class));
		} else {
			if(IsFirstRun)
			{
				IsFirstRun = false;
				//kick off an async task to fetch the contacts from the server
				doContactsLookup();
			}
		}
		
	}
	
	@Override
	protected void onRestart()
	{
		super.onRestart();
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		
		//load up the default prefs in case this is first run
		
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		
		getWalletApplication().startBlockchainService(true);
		
		checkLowStorageAlert();
	}
	
	


	@Override
	protected void onNewIntent(final Intent intent)
	{
		handleIntent(intent);
	}

	private void handleIntent(@Nonnull final Intent intent)
	{
		final String action = intent.getAction();
		
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))
		{
			final String inputType = intent.getType();
			final NdefMessage ndefMessage = (NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
			final byte[] input = Nfc.extractMimePayload(Constants.MIMETYPE_TRANSACTION, ndefMessage);

			new BinaryInputParser(inputType, input)
			{
				@Override
				protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
				{
					cannotClassify(inputType);
				}

				@Override
				protected void directTransaction(final Transaction transaction)
				{
					processDirectTransaction(transaction);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(WalletActivity.this, null, 0, messageResId, messageArgs);
				}
			}.parse();
		}
	}


	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		getSupportMenuInflater().inflate(R.menu.wallet_options, menu);
		//menu.findItem(R.id.wallet_options_donate).setVisible(!Constants.TEST);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		final Resources res = getResources();
		final String externalStorageState = Environment.getExternalStorageState();

		menu.findItem(R.id.wallet_options_exchange_rates).setVisible(res.getBoolean(R.bool.show_exchange_rates_option));
		menu.findItem(R.id.wallet_options_import_keys).setEnabled(
				Environment.MEDIA_MOUNTED.equals(externalStorageState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(externalStorageState));
		menu.findItem(R.id.wallet_options_export_keys).setEnabled(Environment.MEDIA_MOUNTED.equals(externalStorageState));

		return true;
	}
	
	static final int ADDRESS_BOOK_REQUEST = 857;

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			/*case R.id.wallet_options_request:
				handleRequestCoins();
				return true;

			case R.id.wallet_options_send:
				handleSendCoins();
				return true;

			case R.id.wallet_options_scan:
				handleScan();
				return true;
*/
			case R.id.wallet_options_address_book:
				startActivityForResult(new Intent(this, AddressBookActivity.class), ADDRESS_BOOK_REQUEST);
				return true;

			case R.id.wallet_options_exchange_rates:
				startActivity(new Intent(this, ExchangeRatesActivity.class));
				return true;

			/*case R.id.wallet_options_network_monitor:
				startActivity(new Intent(this, NetworkMonitorActivity.class));
				return true;
			 */
			case R.id.wallet_options_import_keys:
				showDialog(DIALOG_IMPORT_KEYS);
				return true;

			case R.id.wallet_options_export_keys:
				handleExportKeys();
				return true;

			case R.id.wallet_options_preferences:
				startActivity(new Intent(this, PreferencesActivity.class));
				return true;

			case R.id.wallet_options_about:
				startActivity(new Intent(this, AboutActivity.class));
				return true;

			case R.id.wallet_options_safety:
				HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_safety);
				return true;

			/*case R.id.wallet_options_donate:
				SendCoinsActivity.start(this, Constants.DONATION_ADDRESS, getString(R.string.wallet_donate_address_label), null, null);
				return true;
			 */
			/*case R.id.wallet_options_help:
				HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_wallet);
				return true;
			*/
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    
	    if (requestCode == REQUEST_CODE_SCAN)
		{
			if (resultCode == Activity.RESULT_OK)
			{
				final String input = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

				new StringInputParser(input)
				{
					@Override
					protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac)
					{
						SendCoinsFragment frag = (SendCoinsFragment)WalletActivity.this.mSectionsPagerAdapter.getItem(0);
						if(frag != null)
							frag.update(address != null ? address.toString() : null, addressLabel, amount, null);
						
						final ActionBar actionBar = getActionBar();
						actionBar.setSelectedNavigationItem(0);
					}

					@Override
					protected void directTransaction(final Transaction transaction)
					{
						processDirectTransaction(transaction);
						//cannotClassify(input);
					}

					@Override
					protected void error(final int messageResId, final Object... messageArgs)
					{
						dialog(WalletActivity.this, null, R.string.button_scan, messageResId, messageArgs);
					}
				}.parse();
			}
		} else if (requestCode == ADDRESS_BOOK_REQUEST) {
            if (resultCode == RESULT_CODE_ADDRESSBOOK_SEND) {
            	final Bundle extras = data.getExtras();
            	final String address = extras.getString(SendCoinsFragment.INTENT_EXTRA_ADDRESS);
        		final String addressLabel = extras.getString(SendCoinsFragment.INTENT_EXTRA_ADDRESS_LABEL);
        		SendCoinsFragment frag = (SendCoinsFragment) this.mSectionsPagerAdapter.getItem(0);
        		if(frag != null)
        			frag.update(address, addressLabel, null, null);
        		
        		final ActionBar actionBar = getActionBar();
        		actionBar.setSelectedNavigationItem(0);
            }
        }
 
    }

	public void handleScan()
	{
		startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
	}

	public void handleExportKeys()
	{
		showDialog(DIALOG_EXPORT_KEYS);

		prefs.edit().putBoolean(Constants.PREFS_KEY_REMIND_BACKUP, false).commit();
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		if (id == DIALOG_IMPORT_KEYS)
			return createImportKeysDialog();
		else if (id == DIALOG_EXPORT_KEYS)
			return createExportKeysDialog();
		else if (id == DIALOG_ALERT_OLD_SDK)
			return createAlertOldSdkDialog();
		else
			throw new IllegalArgumentException();
	}

	@Override
	protected void onPrepareDialog(final int id, final Dialog dialog)
	{
		if (id == DIALOG_IMPORT_KEYS)
			prepareImportKeysDialog(dialog);
		else if (id == DIALOG_EXPORT_KEYS)
			prepareExportKeysDialog(dialog);
	}

	private Dialog createImportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.import_keys_from_storage_dialog, null);
		final Spinner fileView = (Spinner) view.findViewById(R.id.import_keys_from_storage_file);
		final EditText passwordView = (EditText) view.findViewById(R.id.import_keys_from_storage_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.import_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.import_keys_dialog_button_import, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final File file = (File) fileView.getSelectedItem();
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				importPrivateKeys(file, password);
			}
		});
		builder.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});

		return builder.create();
	}

	private void prepareImportKeysDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final List<File> files = new LinkedList<File>();

		// external storage
		if (Constants.EXTERNAL_WALLET_BACKUP_DIR.exists() && Constants.EXTERNAL_WALLET_BACKUP_DIR.isDirectory())
			for (final File file : Constants.EXTERNAL_WALLET_BACKUP_DIR.listFiles())
				if (WalletUtils.KEYS_FILE_FILTER.accept(file) || Crypto.OPENSSL_FILE_FILTER.accept(file))
					files.add(file);

		// internal storage
		for (final String filename : fileList())
			if (filename.startsWith(Constants.WALLET_KEY_BACKUP_BASE58 + '.'))
				files.add(new File(getFilesDir(), filename));

		// sort
		Collections.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(final File lhs, final File rhs)
			{
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});

		final FileAdapter adapter = new FileAdapter(this, files)
		{
			@Override
			public View getDropDownView(final int position, View row, final ViewGroup parent)
			{
				final File file = getItem(position);
				final boolean isExternal = Constants.EXTERNAL_WALLET_BACKUP_DIR.equals(file.getParentFile());
				final boolean isEncrypted = Crypto.OPENSSL_FILE_FILTER.accept(file);

				if (row == null)
					row = inflater.inflate(R.layout.wallet_import_keys_file_row, null);

				final TextView filenameView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_filename);
				filenameView.setText(file.getName());

				final TextView securityView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_security);
				final String encryptedStr = context.getString(isEncrypted ? R.string.import_keys_dialog_file_security_encrypted
						: R.string.import_keys_dialog_file_security_unencrypted);
				final String storageStr = context.getString(isExternal ? R.string.import_keys_dialog_file_security_external
						: R.string.import_keys_dialog_file_security_internal);
				securityView.setText(encryptedStr + ", " + storageStr);

				final TextView createdView = (TextView) row.findViewById(R.id.wallet_import_keys_file_row_created);
				createdView
						.setText(context.getString(isExternal ? R.string.import_keys_dialog_file_created_manual
								: R.string.import_keys_dialog_file_created_automatic, DateUtils.getRelativeTimeSpanString(context,
								file.lastModified(), true)));

				return row;
			}
		};

		final Spinner fileView = (Spinner) alertDialog.findViewById(R.id.import_keys_from_storage_file);
		fileView.setAdapter(adapter);
		fileView.setEnabled(!adapter.isEmpty());

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.import_keys_from_storage_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog)
		{
			@Override
			protected boolean hasFile()
			{
				return fileView.getSelectedItem() != null;
			}

			@Override
			protected boolean needsPassword()
			{
				final File selectedFile = (File) fileView.getSelectedItem();
				return selectedFile != null ? Crypto.OPENSSL_FILE_FILTER.accept(selectedFile) : false;
			}
		};
		passwordView.addTextChangedListener(dialogButtonEnabler);
		fileView.setOnItemSelectedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.import_keys_from_storage_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private Dialog createExportKeysDialog()
	{
		final View view = getLayoutInflater().inflate(R.layout.export_keys_dialog, null);
		final EditText passwordView = (EditText) view.findViewById(R.id.export_keys_dialog_password);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setInverseBackgroundForced(true);
		builder.setTitle(R.string.export_keys_dialog_title);
		builder.setView(view);
		builder.setPositiveButton(R.string.export_keys_dialog_button_export, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				final String password = passwordView.getText().toString().trim();
				passwordView.setText(null); // get rid of it asap

				exportPrivateKeys(password);
			}
		});
		builder.setNegativeButton(R.string.button_cancel, new OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int which)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});
		builder.setOnCancelListener(new OnCancelListener()
		{
			@Override
			public void onCancel(final DialogInterface dialog)
			{
				passwordView.setText(null); // get rid of it asap
			}
		});

		final AlertDialog dialog = builder.create();

		return dialog;
	}

	private void prepareExportKeysDialog(final Dialog dialog)
	{
		final AlertDialog alertDialog = (AlertDialog) dialog;

		final EditText passwordView = (EditText) alertDialog.findViewById(R.id.export_keys_dialog_password);
		passwordView.setText(null);

		final ImportDialogButtonEnablerListener dialogButtonEnabler = new ImportDialogButtonEnablerListener(passwordView, alertDialog);
		passwordView.addTextChangedListener(dialogButtonEnabler);

		final CheckBox showView = (CheckBox) alertDialog.findViewById(R.id.export_keys_dialog_show);
		showView.setOnCheckedChangeListener(new ShowPasswordCheckListener(passwordView));
	}

	private Dialog createAlertOldSdkDialog()
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_old_sdk_dialog_title);
		builder.setMessage(R.string.wallet_old_sdk_dialog_message);
		builder.setPositiveButton(R.string.button_ok, null);
		builder.setNegativeButton(R.string.button_dismiss, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(final DialogInterface dialog, final int id)
			{
				prefs.edit().putBoolean(Constants.PREFS_KEY_ALERT_OLD_SDK_DISMISSED, true).commit();
				finish();
			}
		});
		return builder.create();
	}

	private void checkLowStorageAlert()
	{
		final Intent stickyIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW));
		if (stickyIntent != null)
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setIcon(android.R.drawable.ic_dialog_alert);
			builder.setTitle(R.string.wallet_low_storage_dialog_title);
			builder.setMessage(R.string.wallet_low_storage_dialog_msg);
			builder.setPositiveButton(R.string.wallet_low_storage_dialog_button_apps, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
					finish();
				}
			});
			builder.setNegativeButton(R.string.button_dismiss, null);
			builder.show();
		}
	}

	private void checkAlerts()
	{
		final PackageInfo packageInfo = getWalletApplication().packageInfo();
		/*final int versionNameSplit = packageInfo.versionName.indexOf('-');
		final String base = Constants.VERSION_URL + (versionNameSplit >= 0 ? packageInfo.versionName.substring(versionNameSplit) : "");
		final String url = base + "?current=" + packageInfo.versionCode;

		new HttpGetThread(getAssets(), url)
		{
			@Override
			protected void handleLine(final String line, final long serverTime)
			{
				final int serverVersionCode = Integer.parseInt(line.split("\\s+")[0]);

				log.info("according to \"" + url + "\", strongly recommended minimum app version is " + serverVersionCode);

				if (serverTime > 0)
				{
					final long diffMinutes = Math.abs((System.currentTimeMillis() - serverTime) / DateUtils.MINUTE_IN_MILLIS);

					if (diffMinutes >= 60)
					{
						log.info("according to \"" + url + "\", system clock is off by " + diffMinutes + " minutes");

						runOnUiThread(new Runnable()
						{
							@Override
							public void run()
							{
								if (!isFinishing())
									timeskewAlert(diffMinutes);
							}
						});

						return;
					}
				}

				if (serverVersionCode > packageInfo.versionCode)
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							if (!isFinishing())
								versionAlert(serverVersionCode);
						}
					});

					return;
				}
			}

			@Override
			protected void handleException(final Exception x)
			{
				if (x instanceof UnknownHostException || x instanceof SocketException || x instanceof SocketTimeoutException)
				{
					// swallow
					log.debug("problem reading", x);
				}
				else
				{
					CrashReporter.saveBackgroundTrace(new RuntimeException(url, x), packageInfo);
				}
			}
		}.start();
*/
		if (CrashReporter.hasSavedCrashTrace())
		{
			final StringBuilder stackTrace = new StringBuilder();
			final StringBuilder applicationLog = new StringBuilder();

			try
			{
				CrashReporter.appendSavedCrashTrace(stackTrace);
			}
			catch (final IOException x)
			{
				log.info("problem appending crash info", x);
			}

			final ReportIssueDialogBuilder dialog = new ReportIssueDialogBuilder(this, R.string.report_issue_dialog_title_crash,
					R.string.report_issue_dialog_message_crash)
			{
				@Override
				protected CharSequence subject()
				{
					return Constants.REPORT_SUBJECT_CRASH + " " + packageInfo.versionName;
				}

				@Override
				protected CharSequence collectApplicationInfo() throws IOException
				{
					final StringBuilder applicationInfo = new StringBuilder();
					CrashReporter.appendApplicationInfo(applicationInfo, application);
					return applicationInfo;
				}

				@Override
				protected CharSequence collectStackTrace() throws IOException
				{
					if (stackTrace.length() > 0)
						return stackTrace;
					else
						return null;
				}

				@Override
				protected CharSequence collectDeviceInfo() throws IOException
				{
					final StringBuilder deviceInfo = new StringBuilder();
					CrashReporter.appendDeviceInfo(deviceInfo, WalletActivity.this);
					return deviceInfo;
				}

				@Override
				protected CharSequence collectWalletDump()
				{
					return wallet.toString(false, true, true, null);
				}
			};

			dialog.show();
		}
	}

	private void timeskewAlert(final long diffMinutes)
	{
		final PackageManager pm = getPackageManager();
		final Intent settingsIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_timeskew_dialog_title);
		builder.setMessage(getString(R.string.wallet_timeskew_dialog_msg, diffMinutes));

		if (pm.resolveActivity(settingsIntent, 0) != null)
		{
			builder.setPositiveButton(R.string.wallet_timeskew_dialog_button_settings, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(settingsIntent);
					finish();
				}
			});
		}

		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private void versionAlert(final int serverVersionCode)
	{
		final PackageManager pm = getPackageManager();
		final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName())));
		final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setTitle(R.string.wallet_version_dialog_title);
		builder.setMessage(getString(R.string.wallet_version_dialog_msg));

		if (pm.resolveActivity(marketIntent, 0) != null)
		{
			builder.setPositiveButton(R.string.wallet_version_dialog_button_market, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(marketIntent);
					finish();
				}
			});
		}

		if (pm.resolveActivity(binaryIntent, 0) != null)
		{
			builder.setNeutralButton(R.string.wallet_version_dialog_button_binary, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int id)
				{
					startActivity(binaryIntent);
					finish();
				}
			});
		}

		builder.setNegativeButton(R.string.button_dismiss, null);
		builder.show();
	}

	private void importPrivateKeys(@Nonnull final File file, @Nonnull final String password)
	{
		try
		{
			final Reader plainReader;
			if (Crypto.OPENSSL_FILE_FILTER.accept(file))
			{
				final BufferedReader cipherIn = new BufferedReader(new InputStreamReader(new FileInputStream(file), Constants.UTF_8));
				final StringBuilder cipherText = new StringBuilder();
				while (true)
				{
					final String line = cipherIn.readLine();
					if (line == null)
						break;

					cipherText.append(line);
				}
				cipherIn.close();

				final String plainText = Crypto.decrypt(cipherText.toString(), password.toCharArray());
				plainReader = new StringReader(plainText);
			}
			else if (WalletUtils.KEYS_FILE_FILTER.accept(file))
			{
				plainReader = new InputStreamReader(new FileInputStream(file), Constants.UTF_8);
			}
			else
			{
				throw new IllegalStateException(file.getAbsolutePath());
			}

			final BufferedReader keyReader = new BufferedReader(plainReader);
			final List<ECKey> importedKeys = WalletUtils.readKeys(keyReader);
			keyReader.close();

			final int numKeysToImport = importedKeys.size();
			final int numKeysImported = wallet.addKeys(importedKeys);

			final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setInverseBackgroundForced(true);
			final StringBuilder message = new StringBuilder();
			if (numKeysImported > 0)
				message.append(getString(R.string.import_keys_dialog_success_imported, numKeysImported));
			if (numKeysImported < numKeysToImport)
			{
				if (message.length() > 0)
					message.append('\n');
				message.append(getString(R.string.import_keys_dialog_success_existing, numKeysToImport - numKeysImported));
			}
			if (numKeysImported > 0)
			{
				if (message.length() > 0)
					message.append("\n\n");
				message.append(getString(R.string.import_keys_dialog_success_reset));
			}
			dialog.setMessage(message);
			if (numKeysImported > 0)
			{
				dialog.setPositiveButton(R.string.import_keys_dialog_button_reset_blockchain, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(final DialogInterface dialog, final int id)
					{
						getWalletApplication().resetBlockchain();
						finish();
					}
				});
				dialog.setNegativeButton(R.string.button_dismiss, null);
			}
			else
			{
				dialog.setNeutralButton(R.string.button_dismiss, null);
			}
			dialog.show();

			log.info("imported " + numKeysImported + " of " + numKeysToImport + " private keys");
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.import_keys_dialog_failure, x.getMessage())).setNeutralButton(R.string.button_dismiss, null)
					.show();

			log.info("problem reading private keys", x);
		}
	}

	private void exportPrivateKeys(@Nonnull final String password)
	{
		try
		{
			Constants.EXTERNAL_WALLET_BACKUP_DIR.mkdirs();
			final DateFormat dateFormat = Iso8601Format.newDateFormat();
			dateFormat.setTimeZone(TimeZone.getDefault());
			final File file = new File(Constants.EXTERNAL_WALLET_BACKUP_DIR, Constants.EXTERNAL_WALLET_KEY_BACKUP + "-"
					+ dateFormat.format(new Date()));

			final List<ECKey> keys = new LinkedList<ECKey>();
			for (final ECKey key : wallet.getKeys())
				if (!wallet.isKeyRotating(key))
					keys.add(key);

			final StringWriter plainOut = new StringWriter();
			WalletUtils.writeKeys(plainOut, keys);
			plainOut.close();
			final String plainText = plainOut.toString();

			final String cipherText = Crypto.encrypt(plainText, password.toCharArray());

			final Writer cipherOut = new OutputStreamWriter(new FileOutputStream(file), Constants.UTF_8);
			cipherOut.write(cipherText);
			cipherOut.close();

			final AlertDialog.Builder dialog = new AlertDialog.Builder(this).setInverseBackgroundForced(true).setMessage(
					getString(R.string.export_keys_dialog_success, file));
			dialog.setPositiveButton(R.string.export_keys_dialog_button_archive, new OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					mailPrivateKeys(file);
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();

			log.info("exported " + keys.size() + " private keys to " + file);
		}
		catch (final IOException x)
		{
			new AlertDialog.Builder(this).setInverseBackgroundForced(true).setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.import_export_keys_dialog_failure_title)
					.setMessage(getString(R.string.export_keys_dialog_failure, x.getMessage())).setNeutralButton(R.string.button_dismiss, null)
					.show();

			log.error("problem writing private keys", x);
		}
	}

	private void mailPrivateKeys(@Nonnull final File file)
	{
		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_keys_dialog_mail_subject));
		intent.putExtra(Intent.EXTRA_TEXT,
				getString(R.string.export_keys_dialog_mail_text) + "\n\n" + String.format(Constants.WEBMARKET_APP_URL, getPackageName()) + "\n\n"
						+ Constants.SOURCE_URL + '\n');
		intent.setType("x-bitcoin/private-keys");
		intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
		startActivity(Intent.createChooser(intent, getString(R.string.export_keys_dialog_mail_intent_chooser)));

		log.info("invoked archive private keys chooser");
	}
	
	public void navigateHome()
	{
		final ActionBar actionBar = getActionBar();
		actionBar.setSelectedNavigationItem(1);
	}

	private class SectionsPagerAdapter extends FragmentPagerAdapter {
		
		private SendCoinsFragment _lastSendFragment = null;
		private HomeFragment _lastHomeFragment = null;
		private ReceiveFragment _lastReceiveFragment = null;
		
		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			// Return a DummySectionFragment (defined as a static inner class
			// below) with the page number as its lone argument.
			
			Fragment toDisplay = null;
			Bundle args;
			
			switch(position)
			{
				case 0:
					if(_lastSendFragment == null)
						_lastSendFragment = new SendCoinsFragment();
					toDisplay = _lastSendFragment;
					break;
				case 1:
					if(_lastHomeFragment == null)
						_lastHomeFragment = new HomeFragment();
					toDisplay = _lastHomeFragment;
					break;
				case 2:
					if(_lastReceiveFragment == null)
						_lastReceiveFragment = new ReceiveFragment();
					toDisplay = _lastReceiveFragment;
					break;
			}
			
			return toDisplay;

		}

		@Override
		public int getCount() {
			// Show 3 total pages.
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
			case 0:
				return getString(R.string.send_tab_text).toUpperCase(l);
			case 1:
				return getString(R.string.home_tab_text).toUpperCase(l);
			case 2:
				return getString(R.string.receive_tab_text).toUpperCase(l);
			}
			return null;
		}
		
		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
		    FragmentManager manager = ((Fragment)object).getFragmentManager();
		    android.support.v4.app.FragmentTransaction trans = manager.beginTransaction();
		    if(object == _lastSendFragment)
		    	_lastSendFragment = null;
		    
		    if(object == _lastReceiveFragment)
		    	_lastReceiveFragment = null;
		    
		    if(object == _lastHomeFragment)
		    	_lastHomeFragment = null;
		    trans.remove((Fragment)object);
		    trans.commit();
		}
	}
}
