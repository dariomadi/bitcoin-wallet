package com.kncwallet.wallet.ui.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.View;

public class AnimationUtil {

    public static boolean shouldAnimate()
    {
        return Build.VERSION.SDK_INT>10;
    }

    public static void toggleViews(final View view, final View view2){
        if(shouldAnimate()) {
            animateToggleViews(view, view2);
        } else {
            toggleViewsWithoutAnimation(view,view2);
        }
    }

    @SuppressLint("NewApi")
    private static void animateToggleViews(final View view, final View view2){

        long duration = view.getContext().getResources().getInteger(android.R.integer.config_shortAnimTime);
        if (view.getVisibility() == View.VISIBLE) {

            view.animate().alpha(0).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                }
            });

            view2.animate().alpha(1).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view2.setVisibility(View.VISIBLE);
                }
            });

        } else {

            view.animate().alpha(1).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.VISIBLE);
                }
            });

            view2.animate().alpha(0).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view2.setVisibility(View.GONE);
                }
            });

        }
    }

    private static void toggleViewsWithoutAnimation(View view, View view2){
        if (view.getVisibility() == View.VISIBLE) {
            view.setVisibility(View.GONE);
            view2.setVisibility(View.VISIBLE);
        }else{
            view.setVisibility(View.VISIBLE);
            view2.setVisibility(View.GONE);
        }
    }
}
