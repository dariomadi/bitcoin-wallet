/*
* This is KnC Software
* Please see EULA for more information
*/

package com.kncwallet.wallet.util;

import java.util.ArrayList;
import java.util.List;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.dto.AddressBookContact;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;

public class ContactsFetcher extends AsyncTask<Void, Void, List<AddressBookContact>>
{
	private static final String TAG = "ContactsFetcher";
	private ContentResolver _resolver;
	private ContactsRetrieved _callback;
	private Context _context;
	private String _phoneNumber;
	
	public ContactsFetcher(Context context, String userPhoneNumber)
	{
		_context = context;
		_resolver = context.getContentResolver();
		_phoneNumber = userPhoneNumber;
	}

    public String FixPhoneNumber(String rawNumber){
        String fixedNumber = "";

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumberProto;

        try {
            phoneNumberProto = phoneUtil.parse(rawNumber, WalletApplication.getLocaleFromSim(_context));
        } catch (NumberParseException e) {
            return null;
        }

        fixedNumber = phoneUtil.format(phoneNumberProto, PhoneNumberUtil.PhoneNumberFormat.E164);

        return fixedNumber.replace("+", "");
    }

	public void setOnCompletedCallback(ContactsRetrieved callback)
	{
		_callback = callback;
	}
	
	@Override
	protected List<AddressBookContact> doInBackground(Void... params) {
		try {
			ArrayList<AddressBookContact> ret = new ArrayList<AddressBookContact>();
			Cursor c = _resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);

			if(c.getCount() == 0)
				return ret;

			while (c.moveToNext())
			{
			     String Name=c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
			     String RawNumber = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                 String Number = FixPhoneNumber(RawNumber);
			     
			     int ContactId = c.getInt(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));
			     
			     if(Number == null)
			     {
			    	 Log.e(TAG, "Skipping contact " + Name + " no normalized number for "+RawNumber);
			     } else if(Number.equals(_phoneNumber)) {
			    	 Log.e(TAG, "Skipping contact " + Name + " with own phone number");
				 } else {
			    	 ret.add(new AddressBookContact(Name,Number, RawNumber, ContactId));
			     }
			}
			return ret;
		} catch (Exception ex) {
			Log.e(TAG, "Failed to load contacts due to: " + ex);
			return null;
		}
	}
	
	@Override
	protected void onPostExecute(List<AddressBookContact> result)
	{
		if(result == null)
			_callback.onErrorOccurred();
		
		_callback.onContactsRetrieved(result);
	}
}
