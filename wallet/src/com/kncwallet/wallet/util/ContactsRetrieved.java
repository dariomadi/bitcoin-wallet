/*
* This is KnC Software
* Please see EULA for more information
*/

package com.kncwallet.wallet.util;

import java.util.List;

import com.kncwallet.wallet.dto.AddressBookContact;


public interface ContactsRetrieved
{
	public void onContactsRetrieved(List<AddressBookContact> contacts);
	public void onErrorOccurred();
}
