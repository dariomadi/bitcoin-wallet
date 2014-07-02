/*
* This is KnC Software
* Please see EULA for more information
*/

package com.kncwallet.wallet.util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.dto.ServerResponse;


public class AsyncWebRequest<TPayload, TResult> extends AsyncTask<Void, Void, Object>
{	
	
	private class ErrorResponseWrapper
	{
		public ErrorResponse error;
	}
	
	//No implementation provided for patch
	//so lets bodge it based on POST
	private class HttpPatch extends HttpPost {
	    public static final String METHOD_PATCH = "PATCH";

	    public HttpPatch(final String url) {
	        super(url);
	    }

	    @Override
	    public String getMethod() {
	        return METHOD_PATCH;
	    }
	}
	
	private String _method;
	private Boolean _signRequest;
	private WebRequestCompleted<TResult> _callback;
	private String _uri;
	private TPayload _payload;
	private Context _context;
	private SharedPreferences _prefs;
	private String _userAgent;
	private Type _resultType;
	private long _timeStamp;
	
	public AsyncWebRequest(Context appContext, String uri, String method, Boolean signed, TPayload payload, TypeToken<ServerResponse<TResult>> resultTypeToken)
	{
		_uri = uri;
		_method = method;
		_signRequest = signed;
		_context = appContext;
		_payload = payload;
		
		if(resultTypeToken != null)
		{
			_resultType = resultTypeToken.getType();
		}
		
		_prefs = PreferenceManager.getDefaultSharedPreferences(_context);
		_timeStamp = (System.currentTimeMillis() / 1000L);
		
		try {
			PackageInfo p =_context.getPackageManager()
			    .getPackageInfo(_context.getPackageName(), 0);	
			_userAgent = "KnCWallet " + p.versionName + p.versionCode + Constants.UA_KEY;
		} catch (Exception ex)
		{
			//never mind
			_userAgent = "KnCWallet";
		}
	}
	
	public void setOnCompletedCallback(WebRequestCompleted<TResult> callback)
	{
		_callback = callback;
	}
	
	@Override
	protected Object doInBackground(Void... params) {
		try {
			HttpUriRequest request = createRequest();
			HttpClient client = new DefaultHttpClient();
			HttpResponse response = client.execute(request);
			
			StatusLine statusLine = response.getStatusLine();
			final int statusCode = statusLine.getStatusCode();
			if(statusCode < 200 || statusCode > 300)
			{
				
				try {

					ErrorResponseWrapper err = deserialize(response, new TypeToken<ErrorResponseWrapper>(){}.getType());
					if(err.error.code != 0 && err.error.message != null)
                        return new ErrorResponse(statusCode, err.error.message);
				
				} catch (Exception ex) {}
				
				return new ErrorResponse(statusCode, "An http error occurred");
				
			} else {
				if(_resultType == null)
					return null;
					
				try {
					ServerResponse<TResult> parsed = deserialize(response, _resultType);
					if(parsed.data != null)
					{
						return parsed.data;
					}
			
				} catch (Exception ex) {}
				
				return new ErrorResponse(statusCode, "The server returned an invalid response");
								
			}
		}
        catch (UnknownHostException ex) {
            return new ErrorResponse(-1,_context.getString(R.string.http_connection_error));
        }
        catch (Exception ex)
		{
            Log.e("knc",ex.getMessage()+" "+ex.getClass(),ex);
            return new ErrorResponse(-1,ex.getMessage().replace("api.kncwallet.com", "directory service"));
		}
	}
	
	@Override
	protected void onPostExecute(Object result)
	{
		if(result instanceof ErrorResponse)
		{
			_callback.onErrorOccurred((ErrorResponse)result);
		} else {
			//SHOULD be safe but this is nasty :(
			_callback.onComplete((TResult)result);
		}
	}
	
	private HttpUriRequest createRequest() throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException
	{
		HttpUriRequest request = null;
		
		String serializedBody = "";
		if(_payload != null)
			serializedBody = new Gson().toJson(_payload);
		
		StringEntity body = new StringEntity(serializedBody);
		
		if(_method == "POST")
		{
			HttpPost post = new HttpPost(_uri);
			post.setEntity(body);
			request = post;
		} else if (_method == "PATCH") {
			HttpPatch patch = new HttpPatch(_uri);
			patch.setEntity(body);
			request = patch;
		} else if (_method == "GET") {
			request = new HttpGet(_uri);
		} else if (_method == "DELETE") {
			request = new HttpDelete(_uri);
		} else if (_method == "PUT") {
			HttpPut put = new HttpPut(_uri);
			put.setEntity(body);
			request = put;
		} else {
			throw new UnsupportedOperationException("Method " + _method + " is not supported");
		}
		
		request.setHeader("Accept", "application/json");
		request.setHeader("Content-type", "application/json");
		request.setHeader("User-Agent", _userAgent);
		request.setHeader("X-Timestamp", "" + _timeStamp);
		
		if(_signRequest) {
			String clientID = _prefs.getString("clientID", null);
			String secret = _prefs.getString("secret", null);
				
			if(clientID == null)
				throw new IllegalArgumentException("Tried to send signed request with no stored client id");
			
			if(secret == null)
				throw new IllegalArgumentException("Tried to send signed request with no stored secret");
			
			Mac mac = Mac.getInstance("HmacMD5");
		    SecretKeySpec sk = new SecretKeySpec(secret.getBytes(),mac.getAlgorithm());  
		    mac.init(sk);
		    byte[] result = mac.doFinal((_uri + serializedBody + _timeStamp).getBytes());
		    
		    String hmac = byteArrayToHex(result);
					
			//try not to leave this lying around
			secret = null;
			
			request.setHeader("X-Signature", hmac);
			request.setHeader("X-ClientId", clientID);	
		}
		
		return request;
	}
	
	private static String byteArrayToHex(byte[] a) 
	{
		StringBuilder sb = new StringBuilder();
		for(byte b: a)
		   sb.append(String.format("%02x", b&0xff));
		return sb.toString();
	}

	private <T> T deserialize(HttpResponse response, Type typeToDeserialize) throws IllegalStateException, IOException
	{
		HttpEntity entity = response.getEntity();
		InputStream content = entity.getContent();
		
		String responseString = CharStreams.toString(new InputStreamReader(content, Charsets.UTF_8));
		
		Closeables.closeQuietly(content);
		
		Gson gson = new Gson();
		T parsed = gson.fromJson(responseString, typeToDeserialize);
		
		return parsed;
	}
}