package com.kncwallet.wallet.onename;

public class OneNameCacheData {
    public String response;
    public int code;
    public long timestamp;

    public OneNameCacheData(String response, int code, long timestamp) {
        this.response = response;
        this.code = code;
        this.timestamp = timestamp;
    }
}
