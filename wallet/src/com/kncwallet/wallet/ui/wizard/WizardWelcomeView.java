package com.kncwallet.wallet.ui.wizard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.Image;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.WalletApplication;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class WizardWelcomeView extends WizardView {

    private final EditText editTextPhone;
    private final EditText editTextPhoneCode;
    private final Spinner spinner;

    private ArrayList<Country> countries;

    PhoneNumberUtil phoneNumberUtil;

    private int selectedIndex = 0;

    public WizardWelcomeView(Context context, String phoneNumber, WizardViewListener listener) {
        super(context, R.layout.wizard_phonenumber, listener);

        phoneNumberUtil = PhoneNumberUtil.getInstance();

        editTextPhone = (EditText) findViewById(R.id.edit_text_phone);
        editTextPhoneCode = (EditText) findViewById(R.id.edit_text_country_prefix);

        editTextPhone.setText(phoneNumber);

        Button confirm = (Button) findViewById(R.id.button);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitPhoneNumber();
            }
        });

        countries = getCountries();

        CountrySpinnerAdapter countrySpinnerAdapter = new CountrySpinnerAdapter(getContext());
        countrySpinnerAdapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);


        spinner = (Spinner) findViewById(R.id.spinner);
        spinner.setAdapter(countrySpinnerAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                didSelectCountry(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        setUserLocale();
    }

    private void setUserLocale() {
        String locale = WalletApplication.getLocaleFromSim(getContext());
        if (locale == null) {
            locale = "gb";
        }

        if (locale != null) {
            int index = -1;
            for (int i = 0; i < countries.size(); i++) {
                Country country = countries.get(i);
                if (locale.equalsIgnoreCase(country.code)) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                spinner.setSelection(index);
            }
        }
    }

    private void didSelectCountry(int index) {
        Country country = countries.get(index);

        int code = phoneNumberUtil.getCountryCodeForRegion(country.code);

        editTextPhoneCode.setText("+" + code);

        selectedIndex = index;
    }

    private class Country implements Comparable {
        String name;
        String code;
        String flag;

        private Country(String code, String name) {
            this.name = name;
            this.code = code;
            this.flag = getFlagResourceName(code);
        }

        @Override
        public String toString() {
            return code + " : " + name;
        }

        @Override
        public int compareTo(Object o) {
            if (o instanceof Country) {
                Country country = (Country) o;
                return name.compareTo(country.name);
            }

            return 0;
        }

        private String getFlagResourceName(String code){
            //resource exceptions
            if(code.equalsIgnoreCase("do")){
                return "do_";
            }
            return code.toLowerCase();
        }



    }

    private ArrayList<Country> getCountries() {

        Set<String> supportedRegions = phoneNumberUtil.getSupportedRegions();
        ArrayList<Country> countries = new ArrayList<Country>();

        for (String code : supportedRegions) {
            Locale locale = new Locale("", code);
            countries.add(new Country(code, locale.getDisplayCountry()));
        }
        Collections.sort(countries);
        return countries;

    }

    private class CountrySpinnerAdapter extends ArrayAdapter<String> {

        public CountrySpinnerAdapter(Context context) {
            super(context, R.layout.wizard_spinner_item, new String[countries.size()]);
        }

        @Override
        public String getItem(int i) {
            return countries.get(i).name;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if(convertView == null){
                convertView = View.inflate(getContext(), R.layout.wizard_spinner_item, null);
            }

            View view = convertView;

            Country country = countries.get(position);

            ((TextView)view.findViewById(android.R.id.text1)).setText(country.name);
            ((ImageView)view.findViewById(R.id.image_view_icon)).setImageResource(getImage(country.flag));

            return view;
        }

        @Override
        public android.view.View getDropDownView(int position, View convertView, ViewGroup parent) {

            if(convertView == null){
                convertView = View.inflate(getContext(), R.layout.wizard_spinner_dropdown_item, null);
            }

            View view = convertView;

            Country country = countries.get(position);

            ((TextView)view.findViewById(android.R.id.text1)).setText(country.name);
            ((ImageView)view.findViewById(R.id.image_view_icon)).setImageResource(getImage(country.flag));

            return view;
        }

    }

    private int getImage(String isoCountryCode){
        return getResources().getIdentifier(isoCountryCode, "drawable", getContext().getPackageName());
    }

    private void submitPhoneNumber() {

        if (validatePhoneNumber()) {
            displayConfirmPhoneNumberDialog();
        } else {
            displayInvalidPhoneNumberForCountryDialog();
        }
    }

    private String getSelectedLocale(){
        Country country = countries.get(selectedIndex);
        return country.code;
    }

    private String getNormalizedPhoneNumber()
    {

        String rawNumber = editTextPhone.getText().toString();

        return WalletApplication.FixPhoneNumber(getContext(), rawNumber, getSelectedLocale());
    }

    private boolean validatePhoneNumber() {

        String phoneNumber = getNormalizedPhoneNumber();

        if (phoneNumber == null) {
            return false;
        }

        return true;
    }

    private void displayConfirmPhoneNumberDialog()
    {
        Country country = countries.get(selectedIndex);

        String phoneNumber = WalletApplication.getFormattedPhoneNumber(getContext(), getNormalizedPhoneNumber(), getSelectedLocale());

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(getResources().getString(R.string.wizard_phone_number_will_verify_pattern, phoneNumber))
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        phoneNumberConfirmed();
                    }
                })
                .setNegativeButton(R.string.button_edit, null)
                .show();
    }

    private void phoneNumberConfirmed() {
        storePhoneNumber();
        stepDone();
    }

    private void storePhoneNumber() {

        String phoneNumber = getNormalizedPhoneNumber();

        PreferenceManager.getDefaultSharedPreferences(getContext())
                .edit()
                .putString(Constants.PREFS_PHONE_NUMBER, phoneNumber)
                .putString(Constants.PREFS_PHONE_NUMBER_LOCALE, getSelectedLocale())
                .commit();
    }

    private void displayInvalidPhoneNumberForCountryDialog() {
        Country country = countries.get(selectedIndex);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.wizard_phone_number_invalid_title)
                .setMessage(getResources().getString(R.string.wizard_phone_number_invalid_country_pattern, country.name))
                .setPositiveButton(R.string.button_ok, null)
                .show();
    }


    @Override
    public String getTitle() {
        return getString(R.string.welcome_heading);
    }
}
