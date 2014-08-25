package com.kncwallet.wallet.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.reflect.TypeToken;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.KnownAddressProvider;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.dto.AddressBookContact;
import com.kncwallet.wallet.dto.ContactResponse;
import com.kncwallet.wallet.dto.ContactsRequest;
import com.kncwallet.wallet.dto.ServerResponse;

import java.util.ArrayList;
import java.util.List;

public class ContactsDownloader {


    private static final String PREFS_CONTACT_LOOKUP_TIMESTAMP = "contact_lookup_timestamp";
    private static final long PREFS_CONTACT_LOOKUP_TIME_LIMIT = 1000 * 60 * 60;

    private Context context;
    private SharedPreferences prefs;
    private String phoneNumber;
    private ContactsDownloaderListener listener;

    public interface ContactsDownloaderListener
    {
        public void onSuccess(int numberOfNewContacts);
        public void onError(String message);
    }

    public ContactsDownloader(Context context, SharedPreferences prefs, String phoneNumber, ContactsDownloaderListener listener) {
        this.context = context;
        this.prefs = prefs;
        this.phoneNumber = phoneNumber;
        this.listener = listener;
    }

    public void checkContactsLookup()
    {
        long timestamp = prefs.getLong(PREFS_CONTACT_LOOKUP_TIMESTAMP, 0);

        long diff = System.currentTimeMillis() - timestamp;

        if(diff > PREFS_CONTACT_LOOKUP_TIME_LIMIT){
            doContactsLookup();
        }
    }

    //get the list of local contacts from the address book
    //on complete, look them up on the server
    public void doContactsLookup()
    {
        prefs.edit().putLong(PREFS_CONTACT_LOOKUP_TIMESTAMP, System.currentTimeMillis()).commit();

        if(prefs.getBoolean("entry_deleted", false))
            return;

        ContactsFetcher fetcher = new ContactsFetcher(context, phoneNumber);
        fetcher.setOnCompletedCallback(new ContactsRetrieved() {
            @Override
            public void onContactsRetrieved(List<AddressBookContact> contacts)
            {
                lookupRemoteContacts(contacts);
            }

            @Override
            public void onErrorOccurred() {
                if(listener != null){
                    listener.onError(context.getString(R.string.contacts_lookup_load_contacts_error));
                }
            }
        });

        fetcher.execute();
    }

    //send web request to the server with contacts
    //on complete, save them locally
    private List<AddressBookContact> requestedContacts;
    public void lookupRemoteContacts(final List<AddressBookContact> contacts)
    {
        requestedContacts = contacts;

        if(requestedContacts == null)
            return;

        ContactsRequest payload = new ContactsRequest(contacts);

        String uri = Constants.API_BASE_URL;
        uri += "entries/";
        uri += phoneNumber;
        uri += "/contacts";

        TypeToken<ServerResponse<ArrayList<ContactResponse>>> typeHelper = new TypeToken<ServerResponse<ArrayList<ContactResponse>>>(){};

        AsyncWebRequest<ContactsRequest, ArrayList<ContactResponse>> req =
                new AsyncWebRequest<ContactsRequest, ArrayList<ContactResponse>>(
                        context,
                        uri,
                        "POST",
                        true,
                        payload,
                        typeHelper);

        req.setOnCompletedCallback(
                new WebRequestCompleted<ArrayList<ContactResponse>>() {
                    @Override
                    public void onComplete(ArrayList<ContactResponse> result) {
                        //WelcomeActivity.this.handleSMSResent();
                        saveMatchingContacts(result);
                    }

                    @Override
                    public void onErrorOccurred(ErrorResponse err)
                    {
                        if(listener != null){
                            listener.onError(context.getString(R.string.contacts_lookup_download_error));
                        }
                    }
                }
        );

        req.execute();
    }

    //Save the contacts we just downloaded
    //TODO: make this async!
    public void saveMatchingContacts(ArrayList<ContactResponse> matchedContacts)
    {
        int newContacts = 0;
        for(ContactResponse item : matchedContacts)
        {
            for(AddressBookContact contact : requestedContacts)
            {
                if(contact.TelephoneNumber.equals(item.telephoneNumber))
                {

                    AddressBookProvider.ContactData oldContactData = null;
                    //something that we requested returned a match!
                    final Uri uri = AddressBookProvider.contentUri(context.getPackageName()).buildUpon().appendPath(item.bitcoinWalletAddress).build();
                    Boolean isAdd = true;

                    ContentResolver contentResolver = context.getContentResolver();

                    //if the address is already in there, just update its stuff
                    if(AddressBookProvider.addressExists(context, item.bitcoinWalletAddress))
                        isAdd = false;

                    String existingAddressForNumber = AddressBookProvider.resolveAddress(context, item.telephoneNumber);
                    //if the phone number is already in there
                    if(existingAddressForNumber != null)
                    {
                        oldContactData = AddressBookProvider.resolveContactData(context, existingAddressForNumber);
                        //and it is different
                        if(!existingAddressForNumber.equals(item.bitcoinWalletAddress))
                        {
                            //delete the old address
                            final Uri existingAddressUri = AddressBookProvider.contentUri(context.getPackageName()).buildUpon().appendPath(existingAddressForNumber).build();
                            contentResolver.delete(existingAddressUri, null, null);

                            //don't count this as new when displaying to user
                            newContacts--;
                            //we will then just treat the new one as normal
                        }
                    }

                    final ContentValues values = new ContentValues();
                    values.put(AddressBookProvider.KEY_LABEL, contact.Label);
                    values.put(AddressBookProvider.KEY_TELEPHONE, contact.TelephoneNumber);
                    values.put(AddressBookProvider.KEY_RAW_TELEPHONE, contact.RawNumber);

                    if (isAdd)
                    {
                        contentResolver.insert(uri, values);
                        newContacts++;
                    } else
                    {
                        contentResolver.update(uri, values, null, null);
                        break;
                    }

                    AddressBookProvider.ContactData newContactData = AddressBookProvider.resolveContactData(context, item.bitcoinWalletAddress);

                    KnownAddressProvider.saveKnownAddress(context, newContactData.id, newContactData.rawTelephone, newContactData.address, AddressBookProvider.SOURCE_DIRECTORY);
                    if(oldContactData != null && newContactData != null && existingAddressForNumber != null && !existingAddressForNumber.equals(item.bitcoinWalletAddress) ){
                        KnownAddressProvider.contactHasNewId(context, oldContactData.id, newContactData.id);
                    }

                }
            }
        }

        if(listener != null){
            listener.onSuccess(newContacts);
        }

    }


}
