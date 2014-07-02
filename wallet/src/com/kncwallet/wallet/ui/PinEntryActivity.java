/*
 * Copyright 2013-2014 the original author or authors.
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

import com.actionbarsherlock.app.ActionBar;
import com.kncwallet.wallet.Constants;

import com.kncwallet.wallet.R;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class PinEntryActivity extends AbstractSherlockActivity {

	EditText pinEditor;
	String appPinValue = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_pin_entry);
		pinEditor = (EditText)findViewById(R.id.pin_entry_editor);

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		String appPinEnabled = sharedPref.getString(Constants.PREFS_KEY_APP_PIN_VALUE, "");

		appPinValue = sharedPref.getString(Constants.PREFS_KEY_APP_PIN_VALUE, "");
		if(appPinValue.equals(""))
		{
			//app pin is not enabled
			//bail out
            finishAuthorized();
		}

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
		actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
		actionBar.setIcon(R.drawable.ic_knclogo);
	}

	public void submitPressed(View view)
	{
		String enteredPin = pinEditor.getText().toString();
		if(enteredPin.equals(appPinValue))
		{
            finishAuthorized();
			//kill the view
		} else {
			Toast toast = Toast.makeText(this, R.string.pin_invalid, Toast.LENGTH_SHORT);
			toast.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL, 0, 0);
			pinEditor.setText(null);
			toast.show();
		}
	}

    private void finishAuthorized()
    {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(Constants.PREFS_KEY_APP_PIN_AUTHORIZED, true)
                .commit();

        finish();
    }

	//If they are on the app pin screen and press back, we want to
	//close the app
	private int backButtonCount = 0;
	public void onBackPressed()
	{
		if(backButtonCount >= 1)
		{
			backButtonCount = 0;
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_HOME);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} else {
			Toast toast = Toast.makeText(this, R.string.activity_back_double_tap, Toast.LENGTH_SHORT);
			toast.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL, 0, 0);
			toast.show();
			backButtonCount++;
		}
	}

}
