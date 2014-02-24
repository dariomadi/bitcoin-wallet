/*
* This is KnC Software
* Please see EULA for more information
*/

package com.kncwallet.wallet.util;

public interface WebRequestCompleted<TResponse>
{
	public void onComplete(TResponse result);
	public void onErrorOccurred(ErrorResponse error);
}

