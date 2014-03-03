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

import com.kncwallet.wallet.R;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PinDialogPreference extends DialogPreference {

	private final Context ctx;
	private EditText pin;
	private TextView pinLabel;
	private EditText newPin;
	private CheckBox pinEnabled;
	private TextView pinEnabledLabel;
	
    public PinDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        ctx = context;
        setDialogLayoutResource(R.layout.pin_dialog);
    }

	@Override
	protected View onCreateDialogView() 
	{
		final String existingPin = this.getPersistedString("");
		//layout is set elsewhere
    	final View view = super.onCreateDialogView();
    	pin = (EditText) view.findViewById(R.id.pin_dialog_pin);
    	pinLabel = (TextView) view.findViewById(R.id.pin_dialog_pin_label);
    	newPin = (EditText) view.findViewById(R.id.pin_dialog_new_pin);
    	pinEnabled = (CheckBox) view.findViewById(R.id.pin_dialog_enable);
    	pinEnabledLabel = (TextView) view.findViewById(R.id.pin_dialog_pin_enabled_label);
    	pinEnabled.setChecked(true);
    	pinEnabled.setVisibility(View.GONE);
    	pinEnabledLabel.setVisibility(View.GONE);
	    if(existingPin.equals(""))
	    {
	    	pin.setVisibility(View.GONE);
	    	pinLabel.setVisibility(View.GONE);
	    } else {
	    	pinEnabled.setVisibility(View.VISIBLE);
	    	pinEnabledLabel.setVisibility(View.VISIBLE);
	    }
	    
		return view;
	}

	@Override
    protected void onDialogClosed(boolean positiveResult) {
		final String existingPin = this.getPersistedString("");
		
		String toSave = "";
		if(pinEnabled.isChecked())
		{
			toSave = newPin.getText().toString();
		}
		
        if(positiveResult)
        {
        	super.onDialogClosed(positiveResult);
        	if(pin.getText().toString().equals(existingPin) || existingPin.equals(""))
        	{
        		this.persistString(toSave);
        	} else {
        		Toast toast = Toast.makeText(ctx, "Incorrect pin supplied.", Toast.LENGTH_SHORT);
        		toast.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL, 0, 0);
        		toast.show();
        	}
        } else {
        	super.onDialogClosed(positiveResult);
        }
    }
}
