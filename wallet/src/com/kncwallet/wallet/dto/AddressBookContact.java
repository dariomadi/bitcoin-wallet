/*
 * This is KnC Software
 * Please see EULA for more information
 */

package com.kncwallet.wallet.dto;

public class AddressBookContact {
	public AddressBookContact(String label, String number, String rawNumber, Integer contactId)
	{
		this.Label = label;
		this.TelephoneNumber = number;
		this.RawNumber = rawNumber;
		this.ContactId = contactId;
	}
	
	public String Label;
	
	public String TelephoneNumber;
	
	public String RawNumber;
	
	public Integer ContactId;
}