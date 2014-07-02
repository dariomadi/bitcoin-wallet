package com.kncwallet.wallet.ui.wizard;


import android.content.Context;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;

import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.WalletApplication;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WizardWaitingSmsView extends WizardView {

    public interface EnterCodeCallback
    {
        public void enterCode();
    }

    public interface CountdownFinishedCallback
    {
        public void onCountdownFinished();
    }

    public WizardWaitingSmsView(Context context, String phoneNumber, long timeout,
                                WizardViewListener listener,
                                final EnterCodeCallback enterCodeCallback,
                                final CountdownFinishedCallback countdownFinishedCallback) {
        super(context, R.layout.wizard_waiting_sms, listener);

        final SimpleDateFormat dateFormat = new SimpleDateFormat("mm:ss");

        TextView textViewPhoneNumber = (TextView) findViewById(R.id.text_view_phone);

        String selectedLocale = PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.PREFS_PHONE_NUMBER_LOCALE, null);

        textViewPhoneNumber.setText(WalletApplication.getFormattedPhoneNumber(context, phoneNumber, selectedLocale));

        final TextView textViewTimer = (TextView) findViewById(R.id.text_view_timer);

        new CountDownTimer(timeout, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {

                Date date = new Date(millisUntilFinished);

                textViewTimer.setText(dateFormat.format(date));
            }

            @Override
            public void onFinish() {
                textViewTimer.setText("0:00");
                countdownFinishedCallback.onCountdownFinished();
            }
        }.start();

        findViewById(R.id.button_enter_code).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                enterCodeCallback.enterCode();
            }
        });

    }

    @Override
    public String getTitle() {
        return getString(R.string.wizard_waiting_sms_title);
    }
}
