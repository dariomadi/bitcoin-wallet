package com.kncwallet.wallet.ui.wizard;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import com.kncwallet.wallet.R;

public class WizardProfileView extends WizardView {

    public interface ConfirmedNameCallback
    {
        public void didConfirmName(String name);
    }

    public WizardProfileView(Context context, WizardViewListener listener, final ConfirmedNameCallback confirmedNameCallback) {
        super(context, R.layout.wizard_profile, listener);

        final EditText editTextName = (EditText)this.findViewById(R.id.edit_text_name);

        findViewById(R.id.button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = editTextName.getText().toString();
                confirmedNameCallback.didConfirmName(name);
                stepDone();
            }
        });

    }

    @Override
    public String getTitle() {
        return getString(R.string.wizard_profile_title);
    }
}
