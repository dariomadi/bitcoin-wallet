package com.kncwallet.wallet.util;

import com.kncwallet.wallet.Constants;

public class DenominationUtil {

    public static String getCurrencyCode(int shift){

        switch (shift){
            case 0: return Constants.CURRENCY_CODE_BTC;
            case 6: return Constants.CURRENCY_CODE_BITS;
            default: return Constants.CURRENCY_CODE_MBTC;
        }
    }

    public static int getMaxPrecision(int shift) {
        switch (shift) {
            case 0:
                return Constants.BTC_MAX_PRECISION;
            case 3:
                return Constants.MBTC_MAX_PRECISION;
            default:
                return Constants.BITS_MAX_PRECISION;
        }
    }
}
