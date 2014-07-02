package com.kncwallet.wallet.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

import com.google.bitcoin.core.Wallet;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.R;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.util.DenominationUtil;
import com.kncwallet.wallet.util.GenericUtils;
import com.kncwallet.wallet.util.WalletUtils;

import java.math.BigInteger;

public class KnCAppWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (Constants.ACTION_APP_WIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context.getApplicationContext());
            ComponentName thisWidget = new ComponentName(context.getApplicationContext(), KnCAppWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            if (appWidgetIds != null && appWidgetIds.length > 0) {
                onUpdate(context, appWidgetManager, appWidgetIds);
            }
        }

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        final Spannable balanceStr = getBalanceString(context);

        final String btcPrefix = getBtcPrefix(context);

        for (int i = 0; i < appWidgetIds.length; i++) {

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget);

            rv.setTextViewText(R.id.text_view_balance, balanceStr);

            rv.setTextViewText(R.id.text_view_prefix, btcPrefix);

            rv.setOnClickPendingIntent(R.id.button, getWalletIntent(context, ""));

            rv.setOnClickPendingIntent(R.id.widget_button_send, getWalletIntent(context, Constants.WIDGET_START_SEND));

            rv.setOnClickPendingIntent(R.id.widget_button_request, getWalletIntent(context, Constants.WIDGET_START_RECEIVE));

            rv.setOnClickPendingIntent(R.id.widget_button_send_qr, getWalletIntent(context, Constants.WIDGET_START_SEND_QR));

            appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    protected static Spannable getBalanceString(Context context) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        final Wallet wallet = application.getWallet();
        final BigInteger balance = wallet.getBalance(Wallet.BalanceType.ESTIMATED);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
        final int btcPrecision = precision.charAt(0) - '0';
        final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

        final Spannable balanceStr = new SpannableString(GenericUtils.formatValue(balance, btcPrecision, btcShift));
        WalletUtils.formatSignificant(balanceStr, WalletUtils.SMALLER_SPAN);

        return balanceStr;
    }


    protected static Spannable getBalanceStringWithSuffix(Context context) {
        final WalletApplication application = (WalletApplication) context.getApplicationContext();
        final Wallet wallet = application.getWallet();
        BigInteger balance = wallet.getBalance(Wallet.BalanceType.ESTIMATED);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
        final int btcPrecision = precision.charAt(0) - '0';
        final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;

        final Spannable balanceStr = new SpannableString(GenericUtils.formatValue(balance, btcPrecision, btcShift));
        WalletUtils.formatSignificant(balanceStr, WalletUtils.SMALLER_SPAN);

        String btcPrefix = DenominationUtil.getCurrencyCode(btcShift);

        btcPrefix = Constants.CHAR_HAIR_SPACE + btcPrefix + " ";

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(balanceStr);

        spannableStringBuilder.append(btcPrefix);

        spannableStringBuilder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.knc_dark_text)), balanceStr.length(), balanceStr.length() + btcPrefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        spannableStringBuilder.setSpan(new StyleSpan(2), balanceStr.length(), balanceStr.length() + btcPrefix.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannableStringBuilder;
    }

    protected static String getBtcPrefix(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String precision = prefs.getString(Constants.PREFS_KEY_BTC_PRECISION, Constants.PREFS_DEFAULT_BTC_PRECISION);
        final int btcShift = precision.length() == 3 ? precision.charAt(2) - '0' : 0;
        final String btcPrefix = DenominationUtil.getCurrencyCode(btcShift);
        return btcPrefix;
    }

    protected static PendingIntent getWalletIntent(Context context, String action) {
        Intent intent = new Intent(context, WalletActivity.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

}
