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

import android.app.Activity;

import android.os.Handler;
import com.kncwallet.wallet.WalletApplication;
import com.kncwallet.wallet.util.Pin;

/**
 * @author Linus Unnebäck
 */
public abstract class AbstractActivity extends Activity
{
    @Override
    protected void onResume()
    {
        super.onResume();
        WalletApplication app = (WalletApplication) getApplication();
        app.stopActivityTransitionTimer();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        WalletApplication app = (WalletApplication) getApplication();
        app.startActivityTransitionTimer();
        app.checkPin();
    }
}
