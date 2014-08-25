package com.kncwallet.wallet.onename;

import android.os.AsyncTask;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AsyncRequest extends AsyncTask<Void, Void, Object>
{

    public static final int CONNECTION_ERROR = -1;

    public interface AsyncRequestCallback
    {
        public void onSuccess(String response);
        public void onError(RequestError error);
    }

    public class RequestError
    {
        public int code;
        public String message;

        public RequestError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private HttpUriRequest request;
    private AsyncRequestCallback callback;

    public AsyncRequest(HttpUriRequest request, AsyncRequestCallback callback) {
        this.request = request;
        this.callback = callback;
    }

    private String getResponseBody(HttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        InputStream content = entity.getContent();
        String responseString = CharStreams.toString(new InputStreamReader(content, Charsets.UTF_8));
        Closeables.closeQuietly(content);
        return responseString;
    }

    @Override
    protected Object doInBackground(Void... voids) {
        try {

            HttpClient client = new DefaultHttpClient();
            HttpResponse response = client.execute(request);
            StatusLine statusLine = response.getStatusLine();
            final int statusCode = statusLine.getStatusCode();

            if(statusCode < 200 || statusCode > 300)
            {
                return new RequestError(statusCode, getResponseBody(response));
            }else{
                return getResponseBody(response);
            }
        } catch (Exception e){

        }

        return null;
    }

    @Override
    public void onPostExecute(Object result)
    {
        if(result == null){
            callback.onError(new RequestError(CONNECTION_ERROR, ""));
        }else if(result instanceof RequestError){
            RequestError error = (RequestError) result;
            callback.onError(new RequestError(error.code, error.message));
        }else if(result instanceof String){
            callback.onSuccess((String) result);
        }
    }
}