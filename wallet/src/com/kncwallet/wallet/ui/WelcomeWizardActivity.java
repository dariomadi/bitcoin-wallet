package com.kncwallet.wallet.ui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.google.bitcoin.core.Address;
import com.google.gson.reflect.TypeToken;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.dto.RegistrationEntry;
import com.kncwallet.wallet.dto.RegistrationRequest;
import com.kncwallet.wallet.dto.RegistrationResult;
import com.kncwallet.wallet.dto.ServerResponse;
import com.kncwallet.wallet.dto.ValidateRegistrationRequest;
import com.kncwallet.wallet.ui.dialog.KnCDialog;
import com.kncwallet.wallet.ui.wizard.WizardProfileView;
import com.kncwallet.wallet.ui.wizard.WizardView;
import com.kncwallet.wallet.ui.wizard.WizardWaitingSmsView;
import com.kncwallet.wallet.ui.wizard.WizardWelcomeView;
import com.kncwallet.wallet.util.AsyncWebRequest;
import com.kncwallet.wallet.util.ErrorResponse;
import com.kncwallet.wallet.util.WebRequestCompleted;

import java.util.ArrayList;

public class WelcomeWizardActivity extends AbstractSherlockActivity implements WizardView.WizardViewListener {

    private static final int NUMBER_OF_STEPS = 3;
    private static final long SMS_TIMEOUT = 60000 * 2; //2 mins
    LinearLayout content;
    TextView textViewSubtitle;

    private int currentStep;
    private SharedPreferences prefs;
    private SmsContentObserver contentObserver;

    private int numberOfResends = 0;

    private ArrayList<String> smsCodes;
    private boolean isValidatingSmsCode;

    private final static String WIZARD_STEP = "wizard_step";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.welcome_wizard);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        smsCodes = new ArrayList<String>();
        isValidatingSmsCode = false;

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
        actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
        actionBar.setIcon(R.drawable.ic_knclogo);

        content = (LinearLayout) findViewById(R.id.content);
        textViewSubtitle = (TextView) findViewById(R.id.text_view_subtitle);

        showStep(prefs.getInt(WIZARD_STEP, 0));

        registerSmsObserver();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        saveStepState();
    }

    private void showStep(int step) {

        content.removeAllViews();
        content.setVisibility(View.VISIBLE);

        WizardView wizardView = getView(step);

        String subtitle = wizardView.getTitle();
        textViewSubtitle.setText(subtitle);

        content.addView(wizardView);

        currentStep = step;
    }

    private WizardView getView(int step) {
        if (step == 0) {
            return new WizardWelcomeView(this, getPhoneNumber(), this);
        } else if (step == 1) {

            submitRegistration();

            return new WizardWaitingSmsView(this, getPhoneNumber(), SMS_TIMEOUT, this,
                    new WizardWaitingSmsView.EnterCodeCallback() {
                        @Override
                        public void enterCode() {
                            showEnterCodeDialog();
                        }
                    },
                    new WizardWaitingSmsView.CountdownFinishedCallback() {
                        @Override
                        public void onCountdownFinished() {
                            smsObserverDidTimeOut();
                        }
                    }
            );
        } else if (step == 2) {
            return new WizardProfileView(this, this, new WizardProfileView.ConfirmedNameCallback() {
                @Override
                public void didConfirmName(String name) {
                    storeProfileName(name);
                }
            });
        } else if (step == 11) {

            return new WizardWaitingSmsView(this, getPhoneNumber(), SMS_TIMEOUT, this,
                    new WizardWaitingSmsView.EnterCodeCallback() {
                        @Override
                        public void enterCode() {
                            showEnterCodeDialog();
                        }
                    },
                    new WizardWaitingSmsView.CountdownFinishedCallback() {
                        @Override
                        public void onCountdownFinished() {
                            smsObserverDidTimeOut();
                        }
                    }
            );
        }
        throw new IllegalArgumentException();
    }

    private void storeProfileName(String name) {
        Address bitcoinAddress = ((WalletApplication) getApplication()).determineSelectedAddress();
        String address = bitcoinAddress.toString();
        final Uri uri = AddressBookProvider.contentUri(getPackageName()).buildUpon().appendPath(address).build();

        final String label = AddressBookProvider.resolveLabel(this, address);

        final boolean isAdd = label == null;

        final ContentValues values = new ContentValues();
        values.put(AddressBookProvider.KEY_LABEL, name);

        ContentResolver contentResolver = getContentResolver();

        if (isAdd) {
            contentResolver.insert(uri, values);
        } else {
            contentResolver.update(uri, values, null, null);
        }

    }

    private void showEnterCodeDialog() {

        final EditText editText = new EditText(this);
        editText.setHint(R.string.sms_code_input_hint);

        KnCDialog.Builder builder = new KnCDialog.Builder(this);
        builder.setTitle(R.string.sms_code_manually)
                .setView(editText)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String input = editText.getText().toString();
                        if (input != null && input.length() > 0) {
                            submitValidation(input);
                        }
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }


    private String getPhoneNumber() {
        return ((WalletApplication) getApplication()).GetPhoneNumber();
    }

    private void submitRegistration() {
        WalletApplication walletApplication = (WalletApplication) getApplication();
        String phoneNumber = walletApplication.GetPhoneNumber();
        String phoneID = walletApplication.GetPhoneID();
        Address bitcoinAddress = walletApplication.determineSelectedAddress();

        RegistrationRequest payload = new RegistrationRequest(new RegistrationEntry(phoneNumber, bitcoinAddress.toString(), phoneID));
        String uri = Constants.API_BASE_URL;
        uri += "entries";

        TypeToken<ServerResponse<RegistrationResult>> typeHelper = new TypeToken<ServerResponse<RegistrationResult>>() {
        };

        AsyncWebRequest<RegistrationRequest, RegistrationResult> req =
                new AsyncWebRequest<RegistrationRequest, RegistrationResult>(
                        getBaseContext(),
                        uri,
                        "POST",
                        false,
                        payload,
                        typeHelper);

        req.setOnCompletedCallback(
                new WebRequestCompleted<RegistrationResult>() {
                    @Override
                    public void onComplete(RegistrationResult result) {
                        handleRegistrationComplete(result);
                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err) {
                        errorRegisterPhoneNumber();
                        displayError(String.format("%s (%d)", err.message, err.code));
                    }
                }
        );

        req.execute();

        numberOfResends = 0;
    }

    private void handleRegistrationComplete(RegistrationResult res) {
        //save to prefs

        SharedPreferences.Editor editor = prefs.edit();

        if (res.serverTelephoneNumber != null) {
            editor.putString(Constants.PREFS_SMS_VERIFY_ADDRESS, res.serverTelephoneNumber);
        }
        if(res.clientID != null ) {
            editor.putString("clientID", res.clientID);
        }
        if(res.secret != null ) {
            editor.putString("secret", res.secret);
        }
        editor.commit();

        //this will be null in real version
        Log.e("knc", "Server returned sms code: " + res.code);
        Log.e("knc", "Server returned telephone number: " + res.serverTelephoneNumber);
    }

    private void displayError(String errorText) {
        if (this.isFinishing())
            return;

        KnCDialog.Builder builder = new KnCDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.welcome_title);

        String message = getString(R.string.http_error_message) + errorText;
        builder.setMessage(message)
                .setPositiveButton(R.string.http_error_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        builder.show();
    }

    private void submitValidation(String authCode) {
        ValidateRegistrationRequest payload = new ValidateRegistrationRequest(
                authCode);

        String uri = Constants.API_BASE_URL;
        uri += "entries/";
        uri += getPhoneNumber();


        AsyncWebRequest<ValidateRegistrationRequest, Void> req =
                new AsyncWebRequest<ValidateRegistrationRequest, Void>(
                        getBaseContext(),
                        uri,
                        "PATCH",
                        true,
                        payload,
                        null);

        req.setOnCompletedCallback(
                new WebRequestCompleted<Void>() {
                    @Override
                    public void onErrorOccurred(ErrorResponse err) {
                        displayError(String.format("%s (%d)", err.message, err.code));
                    }

                    @Override
                    public void onComplete(Void result) {
                        handleEntryValidated();
                    }

                }
        );

        req.execute();
    }

    private void submitResend() {
        String uri = Constants.API_BASE_URL;
        uri += "entries/";
        uri += getPhoneNumber();
        uri += "/authToken";

        TypeToken<ServerResponse<RegistrationResult>> typeHelper = new TypeToken<ServerResponse<RegistrationResult>>() {
        };

        AsyncWebRequest<Void, RegistrationResult> req =
                new AsyncWebRequest<Void, RegistrationResult>(
                        getBaseContext(),
                        uri,
                        "GET",
                        true,
                        null,
                        typeHelper);

        req.setOnCompletedCallback(
                new WebRequestCompleted<RegistrationResult>() {
                    @Override
                    public void onComplete(RegistrationResult result) {
                        handleRegistrationComplete(result);
                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err) {
                        errorRegisterPhoneNumber();
                        displayError(String.format("%s (%d)", err.message, err.code));
                    }
                }
        );

        req.execute();

        numberOfResends++;
    }


    private void errorRegisterPhoneNumber() {
        showStep(0);
    }

    private void handleEntryValidated() {

        Toast.makeText(this, R.string.wizard_sms_verified, Toast.LENGTH_LONG).show();

        prefs.edit().putBoolean("registrationComplete", true).commit();
        wizardDone();
    }

    @Override
    public void stepDone() {

        if (currentStep + 1 < NUMBER_OF_STEPS) {
            showStep(currentStep + 1);
        } else {
            wizardDone();
        }
    }

    private void wizardDone() {
        finish();
    }

    @Override
    public void onBackPressed() {
        if (prefs.getBoolean("registrationComplete", false)) {
            super.onBackPressed();
        } else {
            invokeHome();
        }
    }

    public void invokeHome() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        startActivity(intent);
    }

    private void registerSmsObserver() {
        String url = "content://sms/";
        Uri uri = Uri.parse(url);

        contentObserver = new SmsContentObserver(new Handler());

        getContentResolver().registerContentObserver(uri, true, contentObserver);
    }

    private void smsObserverDidTimeOut() {

        if (currentStep == 1 || currentStep == 11) {
            displaySmsDialogTimeout();
        }
    }

    private boolean numbersMatch(String number, String number2) {

        try {
            PhoneNumberUtil.MatchType matchType = PhoneNumberUtil.getInstance().isNumberMatch(number, number2);

            return matchType == PhoneNumberUtil.MatchType.EXACT_MATCH ||
                    matchType == PhoneNumberUtil.MatchType.NSN_MATCH;
        } catch (Exception e) {
            Log.e("knc", "numbersMatch error " + e.getMessage(), e);
        }
        return false;
    }

    private void saveStepState()
    {
        int step = currentStep;
        if(step == 1){
            step = 11;
        }
        prefs.edit().putInt(WIZARD_STEP, step).commit();
    }

    private void displaySmsDialogTimeout() {

        if(isFinishing()){
            return;
        }

        if (numberOfResends < 3) {


            KnCDialog.Builder builder = new KnCDialog.Builder(this);
            builder.setTitle(R.string.wizard_sms_verify_fail_title)
                    .setMessage(R.string.wizard_sms_verify_fail_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.sms_resend, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            submitResend();
                            showStep(11);
                        }
                    })
                    .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            showStep(0);
                        }
                    })
                    .show();
        } else {
            showEnterCodeDialog();
        }
    }

    public void addSmsCodeCandidate(String code){
        if(!smsCodes.contains(code)){
            smsCodes.add(code);
        }
        checkNextSmsCode();
    }

    private void checkNextSmsCode()
    {
        if(smsCodes.size()>0){
            if(!isValidatingSmsCode) {
                String nextCandidate = smsCodes.remove(0);
                submitSmsValidation(nextCandidate);
            }
        }
    }

    private void submitSmsValidation(String authCode) {

        isValidatingSmsCode = true;

        ValidateRegistrationRequest payload = new ValidateRegistrationRequest(
                authCode);

        String uri = Constants.API_BASE_URL;
        uri += "entries/";
        uri += getPhoneNumber();

        AsyncWebRequest<ValidateRegistrationRequest, Void> req =
                new AsyncWebRequest<ValidateRegistrationRequest, Void>(
                        getBaseContext(),
                        uri,
                        "PATCH",
                        true,
                        payload,
                        null);

        req.setOnCompletedCallback(
                new WebRequestCompleted<Void>() {
                    @Override
                    public void onErrorOccurred(ErrorResponse err) {
                        isValidatingSmsCode = false;
                        checkNextSmsCode();
                    }

                    @Override
                    public void onComplete(Void result) {
                        handleEntryValidated();
                        smsCodes.clear();
                    }
                }
        );

        req.execute();
    }

    class SmsContentObserver extends ContentObserver {

        public SmsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }

        @Override
        public void onChange(boolean arg0) {
            super.onChange(arg0);

            String supposedSenderAddress = prefs.getString(Constants.PREFS_SMS_VERIFY_ADDRESS, "");

            Uri uriSMSURI = Uri.parse("content://sms/inbox");
            Cursor cur = getContentResolver().query(uriSMSURI, null, null,
                    null, null);
            cur.moveToNext();

            try {
                int addressIndex = cur.getColumnIndex("address");
                int bodyIndex = cur.getColumnIndex("body");

                String address = cur.getString(addressIndex);
                String body = cur.getString(bodyIndex);

                if (numbersMatch(supposedSenderAddress, address)) {
                    String code = body.substring(body.length() - 4);
                    addSmsCodeCandidate(code);
                    return;
                }

            } catch (Exception e) {
                Log.e("knc", e.getMessage(), e);
            }


        }
    }
}
