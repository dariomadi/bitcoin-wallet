package com.kncwallet.wallet.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

public class ListPreference extends android.preference.ListPreference {
    public ListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ListPreference(Context context) {
        super(context);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        KnCDialog.fixDialogDivider(getDialog());
    }

}