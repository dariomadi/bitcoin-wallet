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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Transaction;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.ui.dialog.KnCDialog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * @author Andreas Schildbach
 */
public final class SendCoinsActivity extends AbstractBindServiceActivity {
    public static final String INTENT_EXTRA_ADDRESS = "address";
    public static final String INTENT_EXTRA_ADDRESS_LABEL = "address_label";
    public static final String INTENT_EXTRA_AMOUNT = "amount";
    public static final String INTENT_EXTRA_BLUETOOTH_MAC = "bluetooth_mac";

    public static void start(final Context context, @Nonnull final String address, @Nullable final String addressLabel,
                             @Nullable final BigInteger amount, @Nullable final String bluetoothMac) {
        final Intent intent = new Intent(context, SendCoinsActivity.class);
        intent.putExtra(INTENT_EXTRA_ADDRESS, address);
        intent.putExtra(INTENT_EXTRA_ADDRESS_LABEL, addressLabel);
        intent.putExtra(INTENT_EXTRA_AMOUNT, amount);
        intent.putExtra(INTENT_EXTRA_BLUETOOTH_MAC, bluetoothMac);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.send_coins_content);

        getWalletApplication().startBlockchainService(false);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
        actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
        actionBar.setIcon(R.drawable.ic_knclogo);


    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (isInitiatedByNfcDiscovered()) {
            checkAddNfcAddress();
        }
    }


    private void checkAddNfcAddress() {

        try {

            final Activity activity = this;
            final String input = "" + getIntent().getData();

            new InputParser.StringInputParser(input) {
                @Override
                protected void bitcoinRequest(final Address address, final String addressLabel, final BigInteger amount, final String bluetoothMac) {
                    if (isNewAddress(address.toString())) {
                        askSaveNewAddress(address);
                    }
                }

                @Override
                protected void directTransaction(final Transaction transaction) {
                    cannotClassify(input);
                }

                @Override
                protected void error(final int messageResId, final Object... messageArgs) {
                    dialog(activity, null, R.string.address_book_options_paste_from_clipboard_title, messageResId, messageArgs);
                }
            }.parse();


        } catch (Exception e) {

        }

    }

    private void askSaveNewAddress(final Address address) {

        KnCDialog.Builder builder = new KnCDialog.Builder(this);
        builder.setTitle(R.string.nfc_new_address_dialog_title)
                .setMessage(address.toString())
                .setPositiveButton(R.string.nfc_new_address_dialog_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        saveNewAddress(address);
                    }
                })
                .setNegativeButton(R.string.nfc_new_address_cancel, null)
                .show();
    }

    private void saveNewAddress(Address address) {
        EditAddressBookEntryFragment.edit(getSupportFragmentManager(), address.toString());
    }

    private boolean isNewAddress(String address) {
        final String label = AddressBookProvider.resolveLabel(this, address);

        return label == null;
    }

    private boolean isInitiatedByNfcDiscovered() {
        return NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction());
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getSupportMenuInflater().inflate(R.menu.send_coins_activity_options, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            case R.id.send_coins_options_help:
                HelpDialogFragment.page(getSupportFragmentManager(), R.string.help_send_coins);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
