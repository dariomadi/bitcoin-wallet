package com.kncwallet.wallet.ui.wizard;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.ui.dialog.KnCDialog;

public abstract class WizardView extends LinearLayout {

    WizardViewListener listener;

    public WizardView(Context context, int layoutResource, WizardViewListener listener) {
        super(context);
        this.listener = listener;
        View.inflate(context, layoutResource, this);
    }

    protected void stepDone() {
        if (listener != null) listener.stepDone();
    }

    public abstract String getTitle();

    public interface WizardViewListener {
        public void stepDone();
    }

    public void displayError(String message){
        KnCDialog.Builder builder = new KnCDialog.Builder(getContext());
        builder.setMessage(message)
                .setPositiveButton(R.string.button_ok, null)
                .show();
    }

    public String getString(int resource) {
        return getResources().getString(resource);
    }

    public void toast(int resource) {
        Toast.makeText(getContext(), resource, Toast.LENGTH_LONG).show();
    }
}
