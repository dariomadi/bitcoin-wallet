/*
* This is KnC Software
* Please see EULA for more information
*/

package com.kncwallet.wallet.util;

public class ErrorResponse
{
	public int code;
	public String message;
	
	public ErrorResponse()
	{
		this.code = 0;
		this.message = "";
	}
	
	public ErrorResponse(int code, String message)
	{
		this.code = code;
		this.message = message;
	}
	
}
