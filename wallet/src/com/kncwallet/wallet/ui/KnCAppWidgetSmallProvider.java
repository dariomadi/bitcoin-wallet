package com.kncwallet.wallet.ui;


import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.widget.RemoteViews;

import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;

public class KnCAppWidgetSmallProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (Constants.ACTION_APP_WIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context.getApplicationContext());
            ComponentName thisWidget = new ComponentName(context.getApplicationContext(), KnCAppWidgetSmallProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            if (appWidgetIds != null && appWidgetIds.length > 0) {
                onUpdate(context, appWidgetManager, appWidgetIds);
            }
        }

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        final Spannable balanceStr = KnCAppWidgetProvider.getBalanceStringWithSuffix(context);

        for (int i = 0; i < appWidgetIds.length; i++) {

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_small);

            rv.setTextViewText(R.id.text_view_balance, balanceStr);

            rv.setOnClickPendingIntent(R.id.button, KnCAppWidgetProvider.getWalletIntent(context, ""));

            appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }
}
