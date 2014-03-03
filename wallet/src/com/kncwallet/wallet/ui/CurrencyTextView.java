/*
 * Copyright 2013-2014 the original author or authors.
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

import java.math.BigInteger;

import javax.annotation.Nonnull;

import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.util.GenericUtils;
import com.kncwallet.wallet.util.WalletUtils;

import android.R.style;
import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.TextView;
import com.kncwallet.wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class CurrencyTextView extends TextView
{
	private String suffix = null;
	private ForegroundColorSpan suffixColorSpan = null;
	private StyleSpan suffixStyleSpan = null;
	private BigInteger amount = null;
	private int precision = 0;
	private int shift = 0;
	private boolean alwaysSigned = false;

	public CurrencyTextView(final Context context)
	{
		super(context);
	}

	public CurrencyTextView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
	}

	public void setSuffix(@Nonnull final String suffix)
	{
		this.suffix = Constants.CHAR_HAIR_SPACE + suffix ;
		updateView();
	}

	public void setSuffixColor(final int suffixColor)
	{
		this.suffixColorSpan = new ForegroundColorSpan(suffixColor);
		updateView();
	}
	
	public void setSuffixStyle(final int suffixStyle)
	{
		this.suffixStyleSpan = new StyleSpan(suffixStyle);
	}

	public void setAmount(@Nonnull final BigInteger amount)
	{
		this.amount = amount;
		updateView();
	}

	public void setPrecision(final int precision, final int shift)
	{
		this.precision = precision;
		this.shift = shift;
		updateView();
	}

	public void setAlwaysSigned(final boolean alwaysSigned)
	{
		this.alwaysSigned = alwaysSigned;
		updateView();
	}

	public void setStrikeThru(final boolean strikeThru)
	{
		if (strikeThru)
			setPaintFlags(getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
		else
			setPaintFlags(getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		setSuffixColor(getResources().getColor(R.color.knc_dark_text));
		//italic??
		setSuffixStyle(2);
		setSingleLine();
	}

	private void updateView()
	{
		final Editable text;

		if (amount != null)
		{
			final String s;
			if (alwaysSigned)
				s = GenericUtils.formatValue(amount, Constants.CURRENCY_PLUS_SIGN, Constants.CURRENCY_MINUS_SIGN, precision, shift);
			else
				s = GenericUtils.formatValue(amount, precision, shift);

			text = new SpannableStringBuilder(s);

			if (suffix != null)
			{
				text.append(suffix);
			
				if (suffixColorSpan != null)
					text.setSpan(suffixColorSpan, text.length() - suffix.length(), text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				
				if (suffixStyleSpan != null)
					text.setSpan(suffixStyleSpan, text.length() - suffix.length(), text.length() , Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		else
		{
			text = null;
		}

		setText(text);
	}
}
