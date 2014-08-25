/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.kncwallet.wallet.ui;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.google.bitcoin.core.VersionMessage;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.WalletApplication;

import com.kncwallet.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class AboutActivity extends AbstractPreferenceActivity
{
	private static final String KEY_ABOUT_VERSION = "about_version";
	private static final String KEY_ABOUT_CREDITS_WALLET = "about_credits_wallet";
	private static final String KEY_ABOUT_CREDITS_BITCOINJ = "about_credits_bitcoinj";
	private static final String KEY_ABOUT_CREDITS_ZXING = "about_credits_zxing";
    private static final String KEY_ABOUT_CREDITS_LOOPJ_IMAGEVIEW = "about_credits_loopjimage";
    private static final String KEY_ABOUT_CREDITS_ICON_DRAWER = "about_icon_drawer";
    private static final String KEY_ABOUT_CREDITS_ACTION_BAR_GENERATOR = "about_action_bar_generator";
    private static final String KEY_ABOUT_CREDITS_HOLO_COLORS_GENERATOR = "about_holo_colors_generator";

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.about);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_action_bar_background)));
		actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.knc_background_lighter)));
		actionBar.setIcon(R.drawable.ic_knclogo);

		findPreference(KEY_ABOUT_VERSION).setSummary(((WalletApplication) getApplication()).packageInfo().versionName);

		findPreference(KEY_ABOUT_CREDITS_WALLET).setTitle(getString(R.string.about_credits_wallet_title));
		findPreference(KEY_ABOUT_CREDITS_WALLET).setSummary(Constants.CREDITS_WALLET_URL);

		findPreference(KEY_ABOUT_CREDITS_BITCOINJ).setTitle(getString(R.string.about_credits_bitcoinj_title, VersionMessage.BITCOINJ_VERSION));
		findPreference(KEY_ABOUT_CREDITS_BITCOINJ).setSummary(Constants.CREDITS_BITCOINJ_URL);

		findPreference(KEY_ABOUT_CREDITS_ZXING).setSummary(Constants.CREDITS_ZXING_URL);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();
		if (KEY_ABOUT_CREDITS_BITCOINJ.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_BITCOINJ_URL)));
			finish();
		}
		else if (KEY_ABOUT_CREDITS_ZXING.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_ZXING_URL)));
			finish();
		}else if(KEY_ABOUT_CREDITS_ICON_DRAWER.equals(key)){
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_ICONDRAWER_URL)));
        }else if(KEY_ABOUT_CREDITS_HOLO_COLORS_GENERATOR.equals(key)){
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_HOLOCOLORS_URL)));
        }else if(KEY_ABOUT_CREDITS_ACTION_BAR_GENERATOR.equals(key)){
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_ACTIONBAR_URL)));
        }else if(KEY_ABOUT_CREDITS_LOOPJ_IMAGEVIEW.equals(key)){
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CREDITS_LOOPJ_IMAGEVIEW_URL)));
        }


		return false;
	}
}
