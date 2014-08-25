package com.kncwallet.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.kncwallet.wallet.R;
import com.kncwallet.wallet.TransactionDataProvider;
import com.kncwallet.wallet.ui.dialog.KnCDialog;

import javax.annotation.Nonnull;

public class EditNoteFragment extends DialogFragment {

    private static final String FRAGMENT_TAG = EditNoteFragment.class.getName();

    private static final String KEY_TX_ID = "tx_id";
    private static final String KEY_SENT = "tx_sent";
    private static final String KEY_PHONE = "tx_phone";

    public static void edit(final FragmentManager fm, @Nonnull final String txId, final boolean sent, final String telephoneNumber) {
        final DialogFragment newFragment = EditNoteFragment.instance(txId, sent, telephoneNumber);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private static EditNoteFragment instance(@Nonnull final String txId, final boolean sent, final String telephoneNumber) {
        final EditNoteFragment fragment = new EditNoteFragment();

        final Bundle args = new Bundle();
        args.putString(KEY_TX_ID, txId);
        args.putBoolean(KEY_SENT, sent);
        args.putString(KEY_PHONE, telephoneNumber);
        fragment.setArguments(args);

        return fragment;
    }

    private Activity activity;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public void onStart() {
        super.onStart();
        KnCDialog.fixDialogDivider(getDialog());
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final String txId = args.getString(KEY_TX_ID);
        final String telephoneNumber = args.getString(KEY_PHONE);
        final boolean sent = args.getBoolean(KEY_SENT, true);

        final TransactionDataProvider.TxData txData = TransactionDataProvider.getTxData(activity, txId);

        final View view = activity.getLayoutInflater().inflate(R.layout.edit_note_dialog, null);

        final EditText editText = (EditText) view.findViewById(R.id.edit_note_edit_text);

        final TextView textViewMessage = (TextView) view.findViewById(R.id.edit_note_message);

        if (txData != null) {
            editText.setText(txData.label);
            textViewMessage.setText(txData.message);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle((txData != null && txData.label != null) ? R.string.tx_label_edit : R.string.tx_label_add)
                .setView(view)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        saveNote(txId, editText.getText().toString(), sent, telephoneNumber);
                    }
                })
                .setNegativeButton(R.string.button_cancel, null);


        return builder.create();

    }

    private void saveNote(String txId, String note, boolean sent, String telephoneNumber) {

        TransactionDataProvider.TxData txData = TransactionDataProvider.getTxData(activity, txId);

        String message = null;
        if (txData != null) {
            message = txData.message;
        }

        TransactionDataProvider.saveTxData(activity, txId, message, note);
        TransactionDataProvider.updateTxLabelInDirectory(activity, txId, note, sent, telephoneNumber);

    }


}
