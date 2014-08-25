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

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.WalletApplication;

import com.kncwallet.wallet.R;
import com.kncwallet.wallet.ui.dialog.KnCDialog;
import com.kncwallet.wallet.util.WalletUtils;

import java.math.BigInteger;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends SherlockFragmentActivity
{
	private WalletApplication application;

	protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    private static final int REQUEST_SCAN_PAPER_WALLET = 12;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		application = (WalletApplication) getApplication();

		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		application.stopActivityTransitionTimer();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		application.startActivityTransitionTimer();

        application.checkPin();
	}

	protected WalletApplication getWalletApplication()
	{
		return application;
	}

	protected final void toast(@Nonnull final String text, final Object... formatArgs)
	{
		toast(text, Toast.LENGTH_SHORT, formatArgs);
	}

	protected final void longToast(@Nonnull final String text, final Object... formatArgs)
	{
		toast(text, Toast.LENGTH_LONG, formatArgs);
	}

	protected final void toast(@Nonnull final String text, final int duration, final Object... formatArgs)
	{
		final Toast toast = Toast.makeText(this, text, duration);		//toast.setView(view);
		toast.show();
	}

	protected final void toast(final int textResId, final Object... formatArgs)
	{
		toast(textResId, 0, Toast.LENGTH_SHORT, formatArgs);
	}

	protected final void longToast(final int textResId, final Object... formatArgs)
	{
		toast(textResId, 0, Toast.LENGTH_LONG, formatArgs);
	}

	protected final void toast(final int textResId, final int imageResId, final int duration, final Object... formatArgs)
	{
		toast(getString(textResId, formatArgs),duration, formatArgs);
	}

	protected void touchLastUsed()
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final long prefsLastUsed = prefs.getLong(Constants.PREFS_KEY_LAST_USED, 0);
		final long now = System.currentTimeMillis();
		prefs.edit().putLong(Constants.PREFS_KEY_LAST_USED, now).commit();

		log.info("just being used - last used {} minutes ago", (now - prefsLastUsed) / DateUtils.MINUTE_IN_MILLIS);
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SCAN_PAPER_WALLET && resultCode == RESULT_OK){
            final String input = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            handleImportKey(input);
        }

    }

    protected void handlePaperWallet()
    {
        KnCDialog.Builder builder = new KnCDialog.Builder(this);
        builder.setTitle(R.string.menu_paper_wallet_title)
                .setMessage(R.string.menu_paper_wallet_message)
                .setPositiveButton(R.string.button_scan, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        scanPaperWallet();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void scanPaperWallet()
    {
        startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_SCAN_PAPER_WALLET);
    }

    private void handleImportKey(String input){
        ECKey ecKey = WalletUtils.readKey(input);
        if(ecKey != null){
            if(application.getWallet().hasKey(ecKey)) {
                Toast.makeText(this, R.string.menu_paper_wallet_import_key_exist, Toast.LENGTH_LONG).show();
            }else if(application.getWallet().addKey(ecKey)){
                String address = ecKey.toAddress(Constants.NETWORK_PARAMETERS).toString();
                EditAddressBookEntryFragment.edit(getSupportFragmentManager(), address);
            }else{
                errorImportingPrivateKey();
            }

        }else{
            errorImportingPrivateKey();
        }
    }

    private void errorImportingPrivateKey()
    {
        KnCDialog.Builder builder = new KnCDialog.Builder(this);
        builder.setTitle(R.string.menu_paper_wallet_title)
                .setMessage(R.string.menu_paper_wallet_import_error)
                .setPositiveButton(R.string.button_ok, null)
                .show();
    }
}
