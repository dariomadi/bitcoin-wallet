package com.kncwallet.wallet.onename;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;

import org.apache.http.client.methods.HttpGet;

import java.util.HashMap;


public class OneNameService {

    public interface OneNameServiceListener {
        public void onSuccess(OneNameUser user);
        public void onError(int errorCode, String message);
    }
    public static void getUserByUsername(final Context context, final String username, final OneNameServiceListener listener) {
        getUserByUsername(context, username, true, listener);
    }

    public static void getUserByUsername(final Context context, final String username, boolean useCache, final OneNameServiceListener listener) {

        if(useCache) {
            OneNameCacheData data = OneNameCacheProvider.getCacheData(context, username);

            if (data != null) {

                if (data.code == 0) {
                    OneNameUser user = createOneNameUser(username, data.response);
                    listener.onSuccess(user);
                } else {
                    listener.onError(data.code, data.response);
                }
                return;
            }
        }

        HttpGet httpGet = new HttpGet("https://onename.io/" + username + ".json");
        AsyncRequest request = new AsyncRequest(httpGet, new AsyncRequest.AsyncRequestCallback() {
            @Override
            public void onSuccess(String response) {

                OneNameUser user = createOneNameUser(username, response);

                OneNameCacheProvider.cacheResponse(context, username, 0, response);

                listener.onSuccess(user);
            }

            @Override
            public void onError(AsyncRequest.RequestError error) {

                if(error != null && error.code != AsyncRequest.CONNECTION_ERROR) {
                    OneNameCacheProvider.cacheResponse(context, username, error.code, error.message);
                }

                listener.onError(error.code, error.message);
            }
        });

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            request.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }else{
            request.execute();
        }
    }

    private static OneNameUser createOneNameUser(String username, String response) {
        Gson gson = new Gson();
        OneNameUser user = gson.fromJson(response, OneNameUser.class);
        user.username = username;
        user._json = response;
        return user;
    }


}
