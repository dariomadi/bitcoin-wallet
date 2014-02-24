/*
 * This is KnC Software
 * Please see EULA for more information
 */

package com.kncwallet.wallet.dto;

import java.util.ArrayList;
import java.util.List;

public class ContactsRequest {
	public ContactsRequest(List<AddressBookContact> contacts)
	{
		this.contacts = new ArrayList<String>();
		for(AddressBookContact contact : contacts)
		{
			this.contacts.add(contact.TelephoneNumber);
		}
	}

	public List<String> contacts;
}