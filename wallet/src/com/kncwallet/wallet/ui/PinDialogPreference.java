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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.*;
import com.kncwallet.wallet.R;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

public class PinDialogPreference extends DialogPreference {

    private final Context context;
    private EditText pin;
    private TextView pinLabel;
    private EditText newPin;
    private CheckBox pinEnabled;
    private EditText confirmPin;

    private String previouslyEnteredNewPin = null;

    public PinDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setDialogLayoutResource(R.layout.pin_dialog);
    }

    @Override
    protected View onCreateDialogView() {
        final String existingPin = this.getPersistedString("");
        //layout is set in constructor
        final View view = super.onCreateDialogView();
        pin = (EditText) view.findViewById(R.id.pin_dialog_pin);
        pinLabel = (TextView) view.findViewById(R.id.pin_dialog_pin_label);
        newPin = (EditText) view.findViewById(R.id.pin_dialog_new_pin);
        pinEnabled = (CheckBox) view.findViewById(R.id.pin_dialog_enable);
        confirmPin = (EditText) view.findViewById(R.id.pin_dialog_confirm_pin);
        pinEnabled.setChecked(true);
        pinEnabled.setVisibility(View.GONE);
        if (existingPin.equals("")) {
            pin.setVisibility(View.GONE);
            pinLabel.setVisibility(View.GONE);
        } else {
            pinEnabled.setVisibility(View.VISIBLE);
        }

        pinEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {

                if (checked) {
                    newPin.setEnabled(true);
                    newPin.setHint(null);
                    newPin.setText(previouslyEnteredNewPin);
                    confirmPin.setEnabled(true);
                } else {
                    previouslyEnteredNewPin = newPin.getText().toString();
                    newPin.setEnabled(false);
                    newPin.setHint(R.string.pin_settings_will_disable);
                    newPin.setText(null);
                    confirmPin.setText(null);
                    confirmPin.setEnabled(false);
                }

            }
        });

        return view;
    }

    private void toastFeedback(int stringResourceId) {
        Toast toast = Toast.makeText(context, stringResourceId, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        if(getDialog()!=null) {

            Button positiveButton = ((AlertDialog) getDialog()).getButton(Dialog.BUTTON_POSITIVE);
            if(positiveButton != null) {
                positiveButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        positiveButtonPressed();
                    }
                });
            }
        }
    }

    private void positiveButtonPressed() {
        final String existingPin = this.getPersistedString("");

        String toSave = "";
        if (pinEnabled.isChecked()) {
            toSave = newPin.getText().toString();
        }

        if (pin.getText().toString().equals(existingPin) || existingPin.equals("")) {

            if(toSave.equals(confirmPin.getText().toString())) {

                this.persistString(toSave);

                if (toSave.equals("")) {
                    toastFeedback(R.string.pin_settings_pin_disabled);
                } else if (existingPin.equals("")) {
                    toastFeedback(R.string.pin_settings_pin_enabled);
                } else {
                    toastFeedback(R.string.pin_settings_pin_changed);
                }
                getDialog().dismiss();
            }else{
                confirmPin.requestFocus();
            }

        } else {
            toastFeedback(R.string.pin_settings_incorrect_supplied);
            getDialog().dismiss();
        }

    }
}
