package com.kncwallet.wallet.util;

import android.content.Context;
import android.preference.PreferenceManager;
import com.kncwallet.wallet.Constants;

public class Pin {

    public static void setPinAuthorized(Context context, boolean authorized)
    {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(Constants.PREFS_KEY_APP_PIN_AUTHORIZED, authorized)
                .commit();
    }

    public static boolean pinImmediatelyLocks(Context context){
        return true;
    }

}
