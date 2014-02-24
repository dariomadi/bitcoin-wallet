/*
 * This is KnC Software
 * Please see EULA for more information
 */

package com.kncwallet.wallet.dto;

public class RegistrationEntry
{
	public String telephoneNumber;
	public String bitcoinWalletAddress;
	public String phoneID;
	
	public RegistrationEntry(String num, String addr, String id)
	{
		telephoneNumber = num;
		bitcoinWalletAddress = addr;
		phoneID = id;
	}
}
