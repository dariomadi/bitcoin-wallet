/*
 * This is KnC Software
 * Please see EULA for more information
 */

package com.kncwallet.wallet.dto;

public class AddressPatchRequest {

	public AddressPatchEntry entry;
	
	public AddressPatchRequest(String newAddress)
	{
		entry = new AddressPatchEntry(newAddress);
	}
}
