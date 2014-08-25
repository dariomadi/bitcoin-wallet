package com.kncwallet.wallet.onename;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.util.WalletUtils;
import com.loopj.android.image.SmartImageTask;
import com.loopj.android.image.SmartImageView;

import java.util.HashMap;

public class OneNameAdapter extends BaseAdapter implements Filterable
{

    HashMap<String, Object> onenameData = new HashMap<String, Object>();

    private Context context;
    private String currentInput;
    private OneNameUserSelectedListener listener;

    public OneNameAdapter(Context context, OneNameUserSelectedListener listener) {
        super();
        this.context = context;
        this.listener = listener;
    }

    @Override
    public int getCount()
    {
        return 1;
    }

    @Override
    public Object getItem(int i){
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {

        if(convertView == null){
            convertView = View.inflate(context, R.layout.onename_row, null);
        }

        View view = convertView;

        Button button = (Button) view.findViewById(R.id.button);
        View workingView = view.findViewById(R.id.onename_searching);
        View resultView = view.findViewById(R.id.onename_user);

        workingView.setVisibility(View.GONE);
        resultView.setVisibility(View.GONE);

        Object object = onenameData.get(currentInput);

        button.setOnClickListener(null);


        if(object == null) {
            workingView.setVisibility(View.VISIBLE);

            String status = null;
            View progress = view.findViewById(R.id.progressBar);

            if (currentInput == null) {
                progress.setVisibility(View.INVISIBLE);
                status = context.getString(R.string.onename_search);
            } else if (currentInput != null) {
                status = context.getString(R.string.onename_searching, currentInput);
            } else {
                progress.setVisibility(View.VISIBLE);
            }

            TextView textViewStatus = (TextView) view.findViewById(R.id.onename_status);
            textViewStatus.setText(status);
        }else if(object instanceof OneNameError){
            workingView.setVisibility(View.VISIBLE);
            String status = context.getString(R.string.onename_no_user, ((OneNameError) object).key);
            view.findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
            TextView textViewStatus = (TextView) view.findViewById(R.id.onename_status);
            textViewStatus.setText(status);

        }else if(object instanceof OneNameUser){

            resultView.setVisibility(View.VISIBLE);

            TextView textViewName = (TextView) view.findViewById(R.id.onename_user_name);
            TextView textViewAddress = (TextView) view.findViewById(R.id.onename_user_address);
            final SmartImageView imageView = (SmartImageView) view.findViewById(R.id.onename_image);
            final ProgressBar imageViewProgress = (ProgressBar)view.findViewById(R.id.onename_image_progress);
            final OneNameUser user = (OneNameUser)object;

            imageViewProgress.setVisibility(View.GONE);

            textViewName.setText(user.getDisplayName());
            textViewAddress.setText(WalletUtils.formatHash(user.getAddress(), Constants.ADDRESS_FORMAT_GROUP_SIZE, 24));

            final String imageUrl = user.getImageUrl();

            if(imageUrl != null){
                imageViewProgress.setVisibility(View.VISIBLE);
                imageView.setImageUrl(imageUrl, R.drawable.contact_placeholder, new SmartImageTask.OnCompleteListener() {
                    @Override
                    public void onComplete() {
                        imageViewProgress.setVisibility(View.GONE);
                    }
                });

            }else{
                imageView.setImageResource(R.drawable.contact_placeholder);
            }

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(listener != null){
                        listener.onOneNameUserSelected(user);
                    }
                }
            });
        }

        return view;
    }

    private void notifyDataSetChangedOnUiThread()
    {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public Filter getFilter()
    {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                FilterResults filterResults = new FilterResults();

                if(charSequence != null && charSequence.length() > 1){

                    final String key = charSequence.toString().substring(1);
                    currentInput = key;

                    if(!onenameData.containsKey(key)) {

                        OneNameService.getUserByUsername(context, key, new OneNameService.OneNameServiceListener() {
                            @Override
                            public void onSuccess(OneNameUser user) {
                                onenameData.put(key, user);
                                notifyDataSetChangedOnUiThread();
                            }

                            @Override
                            public void onError(int errorCode, String message) {
                                onenameData.put(key, new OneNameError(key));
                                notifyDataSetChangedOnUiThread();
                            }
                        });
                    }
                }
                filterResults.count = 1;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                notifyDataSetChanged();
            }
        };
    }

    private class OneNameError
    {
        String key;

        private OneNameError(String key) {
            this.key = key;
        }
    }

    public interface OneNameUserSelectedListener
    {
        public void onOneNameUserSelected(OneNameUser oneNameUser);
    }

}