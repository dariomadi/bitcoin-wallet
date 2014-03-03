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

import java.io.IOException;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.util.AsyncWebRequest;
import com.kncwallet.wallet.util.CrashReporter;
import com.kncwallet.wallet.util.ErrorResponse;
import com.kncwallet.wallet.util.WebRequestCompleted;

import com.kncwallet.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class PreferencesActivity extends SherlockPreferenceActivity implements OnPreferenceChangeListener
{
	private WalletApplication application;
	private Preference trustedPeerPreference;
	private Preference trustedPeerOnlyPreference;
	private Preference deleteEntryPreference;
	private SharedPreferences prefs;

	private static final String PREFS_KEY_REPORT_ISSUE = "report_issue";
	private static final String PREFS_KEY_INITIATE_RESET = "initiate_reset";
	private static final String PREFS_KEY_DATA_USAGE = "data_usage";
	private static final String PREFS_KEY_DELETE_ENTRY = "remove_from_directory";
	private static final String PREFS_KEY_APP_PIN_ENABLED = "app_pin_enabled";
	private static final String PREFS_KEY_ENTRY_DELETED = "entry_deleted";
	
	private static final Intent dataUsageIntent = new Intent();
	static
	{
		dataUsageIntent.setComponent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH ? new ComponentName("com.android.settings",
				"com.android.settings.Settings$DataUsageSummaryActivity") : new ComponentName("com.android.phone", "com.android.phone.Settings"));
	}

	private static final Logger log = LoggerFactory.getLogger(PreferencesActivity.class);

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		
		super.onCreate(savedInstanceState);

		application = (WalletApplication) getApplication();
		addPreferencesFromResource(R.xml.preferences);

		final Preference dataUsagePreference = findPreference(PREFS_KEY_DATA_USAGE);
		dataUsagePreference.setEnabled(getPackageManager().resolveActivity(dataUsageIntent, 0) != null);
		
		deleteEntryPreference = findPreference(PREFS_KEY_DELETE_ENTRY);
	
		
		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
		actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
		actionBar.setIcon(R.drawable.ic_knclogo);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		if(prefs.getBoolean(PREFS_KEY_ENTRY_DELETED, false))
			deleteEntryPreference.setEnabled(false);
	}

	@Override
	protected void onDestroy()
	{
		//trustedPeerPreference.setOnPreferenceChangeListener(null);
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();

		if (PREFS_KEY_DATA_USAGE.equals(key))
		{
			startActivity(dataUsageIntent);
			finish();
		}
		
		else if (PREFS_KEY_DELETE_ENTRY.equals(key))
		{
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					this);
	 
			// set title
			alertDialogBuilder.setTitle("KnC Wallet");
 
			// set dialog message
			alertDialogBuilder
				.setMessage("Are you sure?")
				.setCancelable(false)
				.setPositiveButton("Yes",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						// if this button is clicked, close
						// current activity
						runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                            	PreferencesActivity.this.doRemoveEntry();
                            }
						});
						
					}
				  })
				.setNegativeButton("No",new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog,int id) {
						// if this button is clicked, just close
						// the dialog box and do nothing
						dialog.cancel();
					}
				});
 
				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();
 
				// show it
				alertDialog.show();
		}
		
		else if (PREFS_KEY_REPORT_ISSUE.equals(key))
		{
			final ReportIssueDialogBuilder dialog = new ReportIssueDialogBuilder(this, R.string.report_issue_dialog_title_issue,
					R.string.report_issue_dialog_message_issue)
			{
				@Override
				protected CharSequence subject()
				{
					return Constants.REPORT_SUBJECT_ISSUE + " " + application.packageInfo().versionName;
				}

				@Override
				protected CharSequence collectApplicationInfo() throws IOException
				{
					final StringBuilder applicationInfo = new StringBuilder();
					CrashReporter.appendApplicationInfo(applicationInfo, application);
					return applicationInfo;
				}

				@Override
				protected CharSequence collectStackTrace()
				{
					return null;
				}

				@Override
				protected CharSequence collectDeviceInfo() throws IOException
				{
					final StringBuilder deviceInfo = new StringBuilder();
					CrashReporter.appendDeviceInfo(deviceInfo, PreferencesActivity.this);
					return deviceInfo;
				}

				@Override
				protected CharSequence collectWalletDump()
				{
					return application.getWallet().toString(false, true, true, null);
				}
			};

			dialog.show();

			return true;
		}
		else if (PREFS_KEY_INITIATE_RESET.equals(key))
		{
			final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setTitle(R.string.preferences_initiate_reset_title);
			dialog.setMessage(R.string.preferences_initiate_reset_dialog_message);
			dialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, new OnClickListener()
			{
				@Override
				public void onClick(final DialogInterface dialog, final int which)
				{
					log.info("manually initiated blockchain reset");

					application.resetBlockchain();
					finish();
				}
			});
			dialog.setNegativeButton(R.string.button_dismiss, null);
			dialog.show();

			return true;
		}

		return false;
	}
	
	public void doRemoveEntry()
	{

		String uri = Constants.API_BASE_URL;
		uri += "entries/";
		uri += application.GetPhoneNumber();
		
		AsyncWebRequest<Void, Void> req = 
			new AsyncWebRequest<Void, Void>(
				getBaseContext(),
				uri,
				"DELETE",
				true,
				null,
				null);
		
		req.setOnCompletedCallback(
			new WebRequestCompleted<Void>() {	
				@Override
				public void onErrorOccurred(ErrorResponse err)
				{
					Toast.makeText(PreferencesActivity.this, "Error deleting entry (" + err.code + ")", Toast.LENGTH_SHORT).show();
				}
				
				@Override
				public void onComplete(Void result) {
					Toast.makeText(PreferencesActivity.this, "Entry Deleted", Toast.LENGTH_SHORT).show();
					final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(PreferencesActivity.this);
					prefs.edit().putBoolean(PREFS_KEY_ENTRY_DELETED, true).commit();
					deleteEntryPreference.setEnabled(false);
				}
			});
		
		req.execute();
	}

	@Override
	public boolean onPreferenceChange(final Preference preference, final Object newValue)
	{
		if (preference.equals(trustedPeerPreference))
		{
			application.stopBlockchainService();
			updateTrustedPeer((String) newValue);
		}
		else if (preference.equals(trustedPeerOnlyPreference))
		{
			application.stopBlockchainService();
		}

		return true;
	}

	private void updateTrustedPeer(@Nonnull final String trustedPeer)
	{
		if (trustedPeer.isEmpty())
		{
			trustedPeerPreference.setSummary(R.string.preferences_trusted_peer_summary);
			trustedPeerOnlyPreference.setEnabled(false);
		}
		else
		{
			trustedPeerPreference.setSummary(trustedPeer);
			trustedPeerOnlyPreference.setEnabled(true);
		}
	}
}
