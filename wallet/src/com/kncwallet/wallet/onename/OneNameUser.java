package com.kncwallet.wallet.onename;

import com.kncwallet.wallet.onename.dto.Address;
import com.kncwallet.wallet.onename.dto.Formatted;
import com.kncwallet.wallet.onename.dto.Url;

public class OneNameUser {

    public String username;
    public String bio;
    public String v;
    public String website;
    public Formatted location;
    public Url avatar;
    public Address bitcoin;
    public Formatted name;
    public Url cover;
    public Object _json;

    @Override
    public String toString()
    {
        return username+" "+name+" "+bitcoin+" "+_json;
    }

    public String getDisplayName()
    {
        if(name != null && name.formatted != null){
            return name.formatted;
        }
        return username;
    }

    public String getAddress() {

        if(bitcoin!=null)
        {
            return bitcoin.address;
        }

        return null;
    }

    public String getImageUrl()
    {
        if(avatar != null){
            return avatar.url;
        }
        return null;
    }
}
