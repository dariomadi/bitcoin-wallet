package com.kncwallet.wallet.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kncwallet.wallet.R;

public class KncPagerDialog {



    public final static class Content
    {
        private int stringsResource;
        private int drawableResource;

        public Content(int stringsResource, int drawableResource) {
            this.stringsResource = stringsResource;
            this.drawableResource = drawableResource;
        }

        public int getStringsResource() {
            return stringsResource;
        }

        public int getDrawableResource() {
            return drawableResource;
        }
    }

    public static void show(final Context context, final Content[] contents){

        View root = View.inflate(context, R.layout.dialog_pager,null);

        final LinearLayout pagerIndicatorContainer = (LinearLayout) root.findViewById(R.id.pager_indicator_container);

        ViewPager viewPager = (ViewPager) root.findViewById(R.id.pager);

        viewPager.setAdapter(new ViewPagerAdapter(context, contents));

        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override
            public void onPageSelected(int position){
                updatePagerIndicatorContainer(context, contents, pagerIndicatorContainer, position);
            }
        });

        updatePagerIndicatorContainer(context, contents, pagerIndicatorContainer, 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(root)
                .setCancelable(false)
                .setPositiveButton(R.string.button_ok, null)
                .show();

    }

    private static void updatePagerIndicatorContainer(final Context context, final Content[] contents, LinearLayout pagerIndicatorContainer, int position){
        pagerIndicatorContainer.removeAllViews();

        int spacingWidth = (int) context.getResources().getDimension(R.dimen.dialog_pager_indicator_spacing);
        for(int i=0; i<contents.length;i++){

            ImageView circle = new ImageView(context);
            if(i == position){
                circle.setImageResource(R.drawable.pager_indicator_selected);
            }else{
                circle.setImageResource(R.drawable.pager_indicator_empty);
            }

            circle.setPadding(spacingWidth,0,spacingWidth,0);

            pagerIndicatorContainer.addView(circle);

        }

    }



    private static final class ViewPagerAdapter extends PagerAdapter
    {
        private Context context;
        private int count = 0;
        private Content[] contents;

        private ViewPagerAdapter(Context context, Content[] contents) {
            this.context = context;
            this.count = contents.length;
            this.contents = contents;
        }

        public java.lang.Object instantiateItem(android.view.ViewGroup container, int position) {

            RelativeLayout view = (RelativeLayout)RelativeLayout.inflate(context, R.layout.dialog_pager_content, null);

            ImageView imageView = (ImageView) view.findViewById(R.id.imageView);
            TextView textView = (TextView) view.findViewById(R.id.textView);

            Content content = contents[position];

            if(content.drawableResource>0) {
                imageView.setImageResource(content.drawableResource);
            }
            if(content.stringsResource>0) {
                textView.setText(content.stringsResource);
            }

            container.addView(view);

            return view;

        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }
    }

}
