package com.kncwallet.wallet.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.View;

import com.kncwallet.wallet.R;

public class KnCDialog extends AlertDialog {

    public KnCDialog(Context context) {
        super(context);
    }

    public static class Builder extends AlertDialog.Builder
    {
        public Builder(Context context) {
            super(context);
        }

        @Override
        public AlertDialog show()
        {
            final AlertDialog alert = super.create();
            alert.show();
            fixDialogDivider(alert);
            return alert;
        }
    }

    public static void fixDialogDivider(final Dialog dialog)
    {
        if(dialog == null) return;

        final int titleDividerId = dialog.getContext().getResources().getIdentifier("titleDivider", "id", "android");
        View divider = dialog.findViewById(titleDividerId);
        if (divider != null) {
            divider.setBackgroundColor(dialog.getContext().getResources().getColor(R.color.knc_highlight));
        }
    }

}
