/*
* This is KnC Software
* Please see EULA for more information
*/

package com.kncwallet.wallet.ui;

import com.actionbarsherlock.app.ActionBar;
import com.google.bitcoin.core.Address;
import com.google.gson.reflect.TypeToken;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.dto.RegistrationEntry;
import com.kncwallet.wallet.dto.RegistrationRequest;
import com.kncwallet.wallet.dto.RegistrationResult;
import com.kncwallet.wallet.dto.ServerResponse;
import com.kncwallet.wallet.dto.ValidateRegistrationRequest;
import com.kncwallet.wallet.util.AsyncWebRequest;
import com.kncwallet.wallet.util.ErrorResponse;
import com.kncwallet.wallet.util.WebRequestCompleted;

import com.kncwallet.wallet.R;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class WelcomeActivity extends Activity {
	private static final String TAG = "WelcomeActivity";
	
	private static final int DIALOG_WELCOME = 0;
	private static final int DIALOG_CODE = 1;
	
	private WalletApplication application;
	private Address bitcoinAddress;
	private String phoneNumber;
	private String phoneID;
	private SharedPreferences prefs;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_welcome);
		this.application = (WalletApplication) getApplication();
		bitcoinAddress = application.determineSelectedAddress();
		phoneNumber = application.GetPhoneNumber();
		phoneID = application.GetPhoneID();
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		final android.app.ActionBar actionBar = getActionBar();
		actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
		actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
		actionBar.setIcon(R.drawable.ic_knclogo);
		
		showCurrentDialog();
	}
	
	private void showCurrentDialog()
	{
		if(!prefs.getBoolean("registrationComplete", false))
		{
			if(prefs.getString("clientID", null) != null)
			{
				showDialog(DIALOG_CODE);
				return;
			} else {
				showDialog(DIALOG_WELCOME);
				return;
			}
		}
		finish();
	}

	@Override
	protected Dialog onCreateDialog(final int id, Bundle bundle)
	{
		if(id == DIALOG_WELCOME)
			return createWelcomeDialog();
		
		if(id == DIALOG_CODE)
			return createSmsCodeDialog();
		
		throw new IllegalArgumentException();
	}
	
	//initial dialog on first run
	private Dialog createWelcomeDialog()
	{
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        View inflated = getLayoutInflater().inflate(R.layout.welcome_dialog, null);
        final EditText editor = (EditText)inflated.findViewById(R.id.welcome_phone_number_input);
    
        editor.setText(phoneNumber);    
        builder.setView(inflated);
        builder.setCancelable(false);
        builder.setTitle(R.string.welcome_title);
        builder
               .setPositiveButton(R.string.welcome_confirm, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   phoneNumber = WalletApplication.FixPhoneNumber(getApplicationContext(), editor.getText().toString());
                	   prefs.edit().putString("phoneNumber", phoneNumber).commit();
                	   if(phoneNumber == null)
                	   {
                		   runOnUiThread(new Runnable() {
                               @Override
                               public void run() {
                            	   Toast.makeText(WelcomeActivity.this, "Invalid phone number", Toast.LENGTH_SHORT).show();
                               }
                		   });
                	   } else {
                    	   dialog.cancel();
                    	   submitRegistration();
                	   }
                   }
               });
        
        editor.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        
        // Create the AlertDialog object and return it
		return builder.create();
	}
	
	//initial post of registration details
	private void submitRegistration()
	{
		RegistrationRequest payload = new RegistrationRequest(new RegistrationEntry(phoneNumber,bitcoinAddress.toString(), phoneID));
		String uri = Constants.API_BASE_URL;
		uri += "entries";
		
		TypeToken<ServerResponse<RegistrationResult>> typeHelper = new TypeToken<ServerResponse<RegistrationResult>>(){};
				 	
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
					WelcomeActivity.this.handleRegistrationComplete(result);
				}
					
				@Override
				public void onErrorOccurred(ErrorResponse err)
				{
					WelcomeActivity.this.displayError(String.format("%s (%d)", err.message, err.code));
				}
			}
		);
		
		req.execute();
	}
	
	//code returned for debugging currently - will be sent to user via SMS
	private String code;
	//on completion of registration request
	private void handleRegistrationComplete(RegistrationResult res)
	{
		//save to prefs
		prefs.edit().putString("clientID", res.clientID).commit();
		prefs.edit().putString("secret", res.secret).commit();

		//this will be null in real version
		code = res.code;
		Log.d(TAG, "Server returned sms code: " + res.code);
		showCurrentDialog();
	}
	
	//dialog once registration has been submitted and awaiting sms
	private Dialog createSmsCodeDialog()
	{
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.welcome_title);
       
        View inflated = getLayoutInflater().inflate(R.layout.sms_code_dialog, null);
        final EditText editor = (EditText)inflated.findViewById(R.id.sms_code_dialog_input);
        
        if(code != null)
        	editor.setText(code);
        
               
        builder.setView(inflated)
        
               .setPositiveButton(R.string.sms_confirm, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       dialog.cancel();
                       submitValidation(editor.getText().toString());
                   }
               })
               .setNeutralButton(R.string.sms_resend, new DialogInterface.OnClickListener() {
            	   public void onClick(DialogInterface dialog, int id) {
            		   dialog.cancel();
            		   WelcomeActivity.this.submitResend();
            	   }
               });
        // Create the AlertDialog object and return it
        final AlertDialog dialog = builder.create();        	
        
        
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
            	if(code == null)
            		((AlertDialog)dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            }
        });
        
        editor.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub
				int len = s.length();
				if(len != 4) {
					dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
			     } else {
			        dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(true);
			     }
			}
        });
        
		return dialog;
	}
	
	//request to resend sms message if it never came through
	private void submitResend()
	{
		String uri = Constants.API_BASE_URL;
		uri += "entries/";
		uri += phoneNumber;
		uri += "/authToken";
		
		TypeToken<ServerResponse<RegistrationResult>> typeHelper = new TypeToken<ServerResponse<RegistrationResult>>(){};
		
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
					WelcomeActivity.this.handleSMSResent();
				}
					
				@Override
				public void onErrorOccurred(ErrorResponse err)
				{
					WelcomeActivity.this.displayError(String.format("%s (%d)", err.message, err.code));
				}
			}
		);

		req.execute();
	}
	
	//on successful sms re-send
	private void handleSMSResent()
	{
		//toast?
		Toast.makeText(WelcomeActivity.this, "SMS Re-sent", Toast.LENGTH_SHORT).show();
		showCurrentDialog();
	}
	
	//user has entered code, validate their registration
	private void submitValidation(String authCode)
	{
		ValidateRegistrationRequest payload = new ValidateRegistrationRequest(
													authCode);
		
		String uri = Constants.API_BASE_URL;
		uri += "entries/";
		uri += phoneNumber;
		
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
				public void onErrorOccurred(ErrorResponse err)
				{
					WelcomeActivity.this.displayError(String.format("%s (%d)", err.message, err.code));
				}

				@Override
				public void onComplete(Void result) {
					WelcomeActivity.this.handleEntryValidated();
				}
			}
		);

		req.execute();
	}
	
	//registration is complete
	private void handleEntryValidated()
	{
		prefs.edit().putBoolean("registrationComplete", true).commit();
		showCurrentDialog();
	}
	
	//display error alert if things go wrong
	private void displayError(String errorText)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.welcome_title);
        
        String message = getString(R.string.http_error_message) + errorText;
        builder.setMessage(message)
               .setPositiveButton(R.string.http_error_ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   dialog.cancel();
                	   showCurrentDialog();
                   }
               });
        builder.create().show();
	}
}
