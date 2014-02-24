/*
 * This is KnC Software
 * Please see EULA for more information
 */

package com.kncwallet.wallet.dto;

public class ValidateRegistrationRequest
{
	public AuthPatchEntry entry;
	
	public ValidateRegistrationRequest(String token)
	{
		entry = new AuthPatchEntry(token);
	}
}
