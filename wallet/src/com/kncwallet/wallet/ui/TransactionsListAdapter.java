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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Transaction.Purpose;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.core.TransactionConfidence.ConfidenceType;
import com.google.bitcoin.core.Wallet;
import com.kncwallet.wallet.AddressBookProvider;
import com.kncwallet.wallet.Constants;
import com.kncwallet.wallet.ContactImage;
import com.kncwallet.wallet.ExchangeRatesProvider;
import com.kncwallet.wallet.KnownAddressProvider;
import com.kncwallet.wallet.TransactionDataProvider;
import com.kncwallet.wallet.util.CircularProgressView;
import com.kncwallet.wallet.util.DenominationUtil;
import com.kncwallet.wallet.util.WalletUtils;

import com.kncwallet.wallet.R;
import com.loopj.android.image.SmartImageView;

/**
 * @author Andreas Schildbach
 */
public class TransactionsListAdapter extends BaseAdapter
{
	private final Context context;
	private final LayoutInflater inflater;
	private final Wallet wallet;
	private final int maxConnectedPeers;

	private final List<Transaction> transactions = new ArrayList<Transaction>();
	private int precision = 0;
	private int shift = 0;
	private boolean showEmptyText = false;
	private boolean showBackupWarning = false;

	private final int colorSignificant;
	private final int colorInsignificant;
	private final int colorError;
	private final int colorCircularBuilding;
	private final String textCoinBase;
	private final String textInternal;

    private final HashMap<String, Object> contactImageUrlsCache = new HashMap<String, Object>();
    private final HashMap<String, Object> contactImageCache = new HashMap<String, Object>();
	private final Map<String, String> labelCache = new HashMap<String, String>();
    private final Map<String, Object> txDataCache = new HashMap<String, Object>();
	private final static String CACHE_NULL_MARKER = "";

	private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
	private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

	private static final int VIEW_TYPE_TRANSACTION = 0;
	private static final int VIEW_TYPE_WARNING = 1;

    private boolean useBtc = true;
    private ExchangeRatesProvider.ExchangeRate exchangeRate = null;

	public TransactionsListAdapter(final Context context, @Nonnull final Wallet wallet, final int maxConnectedPeers, final boolean showBackupWarning)
	{
		this.context = context;
		inflater = LayoutInflater.from(context);

		this.wallet = wallet;
		this.maxConnectedPeers = maxConnectedPeers;
		//this.showBackupWarning = showBackupWarning;
		this.showBackupWarning = false;

		final Resources resources = context.getResources();
		colorCircularBuilding = resources.getColor(R.color.knc_highlight);
		colorSignificant = resources.getColor(R.color.knc_highlight);
		colorInsignificant = resources.getColor(R.color.fg_insignificant);
		colorError = resources.getColor(R.color.fg_error);
		textCoinBase = context.getString(R.string.wallet_transactions_fragment_coinbase);
		textInternal = context.getString(R.string.wallet_transactions_fragment_internal);
	}

	public void setPrecision(final int precision, final int shift)
	{
		this.precision = precision;
		this.shift = shift;

		notifyDataSetChanged();
	}

	public void clear()
	{
		transactions.clear();

		notifyDataSetChanged();
	}

	public void replace(@Nonnull final Transaction tx)
	{
		transactions.clear();
		transactions.add(tx);

		notifyDataSetChanged();
	}

	public void replace(@Nonnull final Collection<Transaction> transactions)
	{
		this.transactions.clear();
		this.transactions.addAll(transactions);

		showEmptyText = true;

		notifyDataSetChanged();
	}

    public void updateUseBtc(boolean useBtc, ExchangeRatesProvider.ExchangeRate exchangeRate){
        this.useBtc = useBtc;
        this.exchangeRate = exchangeRate;
        notifyDataSetChanged();
    }

	@Override
	public boolean isEmpty()
	{
		return showEmptyText && super.isEmpty();
	}

	@Override
	public int getCount()
	{
		int count = transactions.size();

		if (count == 1 && showBackupWarning)
			count++;

		return count;
	}

	@Override
	public Transaction getItem(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return null;

		return transactions.get(position);
	}

	@Override
	public long getItemId(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return 0;

		return WalletUtils.longHash(transactions.get(position).getHash());
	}

	@Override
	public int getViewTypeCount()
	{
		return 2;
	}

	@Override
	public int getItemViewType(final int position)
	{
		if (position == transactions.size() && showBackupWarning)
			return VIEW_TYPE_WARNING;
		else
			return VIEW_TYPE_TRANSACTION;
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	@Override
	public View getView(final int position, View row, final ViewGroup parent)
	{
		final int type = getItemViewType(position);

		if (type == VIEW_TYPE_TRANSACTION)
		{
			if (row == null)
				row = inflater.inflate(R.layout.transaction_row_extended, null);

			final Transaction tx = getItem(position);
			bindView(row, tx);
		}
		else if (type == VIEW_TYPE_WARNING)
		{
			if (row == null)
				row = inflater.inflate(R.layout.transaction_row_warning, null);

			final TextView messageView = (TextView) row.findViewById(R.id.transaction_row_warning_message);
			messageView.setText(Html.fromHtml(context.getString(R.string.wallet_transactions_row_warning_backup)));
		}
		else
		{
			throw new IllegalStateException("unknown type: " + type);
		}

		return row;
	}

	public void bindView(@Nonnull final View row, @Nonnull final Transaction tx)
	{
		final TransactionConfidence confidence = tx.getConfidence();
		final ConfidenceType confidenceType = confidence.getConfidenceType();
		final boolean isOwn = confidence.getSource().equals(TransactionConfidence.Source.SELF);
		final boolean isCoinBase = tx.isCoinBase();
		final boolean isInternal = WalletUtils.isInternal(tx);

		try
		{
			final BigInteger value = tx.getValue(wallet);
			final boolean sent = value.signum() < 0;

			final CircularProgressView rowConfidenceCircular = (CircularProgressView) row.findViewById(R.id.transaction_row_confidence_circular);
			final TextView rowConfidenceTextual = (TextView) row.findViewById(R.id.transaction_row_confidence_textual);

			// confidence
			if (confidenceType == ConfidenceType.PENDING)
			{
				rowConfidenceCircular.setVisibility(View.VISIBLE);
				rowConfidenceTextual.setVisibility(View.GONE);

				rowConfidenceCircular.setProgress(1);
				rowConfidenceCircular.setMaxProgress(1);
				rowConfidenceCircular.setSize(confidence.numBroadcastPeers());
				rowConfidenceCircular.setMaxSize(maxConnectedPeers / 2); // magic value
				rowConfidenceCircular.setColors(colorInsignificant, colorInsignificant);
			}
			else if (confidenceType == ConfidenceType.BUILDING)
			{
				rowConfidenceCircular.setVisibility(View.VISIBLE);
				rowConfidenceTextual.setVisibility(View.GONE);

				rowConfidenceCircular.setProgress(confidence.getDepthInBlocks());
				rowConfidenceCircular.setMaxProgress(isCoinBase ? Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth()
						: Constants.MAX_NUM_CONFIRMATIONS);
				rowConfidenceCircular.setSize(1);
				rowConfidenceCircular.setMaxSize(1);
				rowConfidenceCircular.setColors(colorCircularBuilding, Color.DKGRAY);
			}
			else if (confidenceType == ConfidenceType.DEAD)
			{
				rowConfidenceCircular.setVisibility(View.GONE);
				rowConfidenceTextual.setVisibility(View.VISIBLE);

				rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_DEAD);
				rowConfidenceTextual.setTextColor(Color.RED);
			}
			else
			{
				rowConfidenceCircular.setVisibility(View.GONE);
				rowConfidenceTextual.setVisibility(View.VISIBLE);

				rowConfidenceTextual.setText(CONFIDENCE_SYMBOL_UNKNOWN);
				rowConfidenceTextual.setTextColor(colorInsignificant);
			}


			// time
			final TextView rowTime = (TextView) row.findViewById(R.id.transaction_row_time);
			if (rowTime != null)
			{
				final Date time = tx.getUpdateTime();
				rowTime.setText(time != null ? time.toLocaleString() : null);

				//if(textColor != colorSignificant)
				//	rowTime.setTextColor(textColor);
			}

			// receiving or sending
			final TextView rowFromTo = (TextView) row.findViewById(R.id.transaction_row_fromto);
			if (isInternal) {
				rowFromTo.setText(R.string.transaction_direction_internal);
			} else if (sent) {
				rowFromTo.setText(R.string.transaction_direction_sent);
			} else {
				rowFromTo.setText(R.string.transaction_direction_received);
			}

			//if(textColor != colorSignificant)
			//	rowFromTo.setTextColor(textColor);
			// coinbase
			//final View rowCoinbase = row.findViewById(R.id.transaction_row_coinbase);
			//rowCoinbase.setVisibility(isCoinBase ? View.VISIBLE : View.GONE);

			// address
			final TextView rowAddress = (TextView) row.findViewById(R.id.transaction_row_address);
			final Address address = sent ? WalletUtils.getFirstToAddress(tx) : WalletUtils.getFirstFromAddress(tx);
			final String label;
			if (isCoinBase)
				label = textCoinBase;
			else if (isInternal)
				label = textInternal;
			else if (address != null)
				label = resolveLabel(address.toString());
			else
				label = "?";

			//if(textColor != colorSignificant)
			//	rowAddress.setTextColor(textColor);

			rowAddress.setText(label != null ? label : address.toString());
			rowAddress.setTypeface(label != null ? Typeface.DEFAULT : Typeface.MONOSPACE);

			SmartImageView img = (SmartImageView) row.findViewById(R.id.transaction_row_contact_image);

            if(address == null) {

                img.setImageResource(R.drawable.mined_btc);

            }else if(label != null)
			{
			    Bitmap contactImage = cachedContactImage(address.toString());
			    if(contactImage != null) {
			    	img.setImageBitmap(contactImage);
			    } else {

                    String imageUrl = cachedImageUrl(address.toString());
                    if(imageUrl != null){
                        img.setImageUrl(imageUrl, R.drawable.contact_placeholder);
                    }else {
                        img.setImageResource(R.drawable.contact_placeholder);
                    }
			    }
			} else {
				img.setImageResource(R.drawable.contact_placeholder);
			}

			// value
			final CurrencyTextView rowValue = (CurrencyTextView) row.findViewById(R.id.transaction_row_value);

			//if(textColor != colorSignificant)
			//	rowValue.setTextColor(textColor);


            rowValue.setVisibility(View.VISIBLE);

            if(useBtc){
                rowValue.setAlwaysSigned(false);
                rowValue.setPrecision(precision, shift);
                rowValue.setAmount(value.abs());
                rowValue.setStrikeThru(false);
                final String suffix = DenominationUtil.getCurrencyCode(shift);

                rowValue.setSuffix(suffix);
            }else{
                rowValue.setPrecision(Constants.LOCAL_PRECISION, 0);
                rowValue.setStrikeThru(Constants.TEST);

                if(exchangeRate != null && exchangeRate.rate != null) {
                    final BigInteger localValue = WalletUtils.localValue(value.abs(), exchangeRate.rate);
                    rowValue.setSuffix(exchangeRate.currencyCode);
                    rowValue.setAmount(localValue);
                }else{
                    rowValue.setVisibility(View.INVISIBLE);
                }
            }




			// extended message
			final View rowExtend = row.findViewById(R.id.transaction_row_extend);
			if (rowExtend != null)
			{
				final TextView rowMessage = (TextView) row.findViewById(R.id.transaction_row_message);
				final boolean isTimeLocked = tx.isTimeLocked();
				rowExtend.setVisibility(View.GONE);

				if (tx.getPurpose() == Purpose.KEY_ROTATION)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(Html.fromHtml(context.getString(R.string.transaction_row_message_purpose_key_rotation)));
					rowMessage.setTextColor(colorSignificant);
				}
				else if (isOwn && confidenceType == ConfidenceType.PENDING && confidence.numBroadcastPeers() <= 1)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_own_unbroadcasted);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (!sent && value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_dust);
					rowMessage.setTextColor(colorInsignificant);
				}
				else if (!sent && confidenceType == ConfidenceType.PENDING && isTimeLocked)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_unconfirmed_locked);
					rowMessage.setTextColor(colorError);
				}
				else if (!sent && confidenceType == ConfidenceType.PENDING && !isTimeLocked)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_unconfirmed_unlocked);
					rowMessage.setTextColor(colorInsignificant);
				}
                else if (isCoinBase && confidence.getDepthInBlocks() < Constants.NETWORK_PARAMETERS.getSpendableCoinbaseDepth())
                {
                    rowExtend.setVisibility(View.VISIBLE);
                    rowMessage.setText(R.string.transaction_row_message_received_building);
                    rowMessage.setTextColor(colorInsignificant);
                }
				else if (!sent && confidenceType == ConfidenceType.DEAD)
				{
					rowExtend.setVisibility(View.VISIBLE);
					rowMessage.setText(R.string.transaction_row_message_received_dead);
					rowMessage.setTextColor(colorError);
				}

            }


            final View rowExtra = row.findViewById(R.id.transaction_row_extra);
            if(rowExtra != null){
                rowExtra.setVisibility(View.GONE);
                if(address != null && tx != null) {
                    final TextView rowText = (TextView) row.findViewById(R.id.transaction_row_extra_message);
                    SpannableStringBuilder txDataString = resolveTxData(tx.getHashAsString(), address.toString(), sent);
                    if (txDataString != null) {
                        rowExtra.setVisibility(View.VISIBLE);
                        rowText.setText(txDataString);
                    }
                }
            }
		}
		catch (final ScriptException x)
		{
			throw new RuntimeException(x);
		}
	}
    private String cachedImageUrl(String address){
        Object cached = contactImageUrlsCache.get(address);
        if(cached == null){
            int contactId = AddressBookProvider.resolveContactId(context, address);
            String imageUrl = ContactImage.getImageUrl(context, contactId);

            if(imageUrl != null){
                contactImageUrlsCache.put(address, imageUrl);
                cached = imageUrl;
            }else{
                contactImageUrlsCache.put(address, new Object());
                cached = new Object();
            }
        }

        if(cached instanceof String){
            return (String)cached;
        }

        return null;
    }

    private Bitmap cachedContactImage(String address) {

        Object cached = contactImageCache.get(address);
        if(cached == null) {
            Bitmap bitmap = AddressBookProvider.bitmapForAddress(context, address);

            if(bitmap == null){ //check if its a known old address

                KnownAddressProvider.KnownAddress knownAddress = KnownAddressProvider.getKnown(context, address);
                if(knownAddress != null){
                    String currentAddress = AddressBookProvider.getAddress(context, knownAddress.contactId);
                    if(currentAddress != null){
                        bitmap = AddressBookProvider.bitmapForAddress(context, currentAddress);
                    }
                }
            }

            if(bitmap != null){
                contactImageCache.put(address, bitmap);
                cached = bitmap;
            }else{
                contactImageCache.put(address, new Object());
                cached = new Object();
            }
        }

        if(cached instanceof Bitmap){
            return (Bitmap)cached;
        }
        return null;
    }

	private String resolveLabel(@Nonnull final String address)
	{
		final String cachedLabel = labelCache.get(address);
		if (cachedLabel == null)
		{
			final String label = AddressBookProvider.resolveLabel(context, address);
			if (label != null)
				labelCache.put(address, label);
			else
				labelCache.put(address, CACHE_NULL_MARKER);
			return label;
		}
		else
		{
			return cachedLabel != CACHE_NULL_MARKER ? cachedLabel : null;
		}
	}

    public void clearTxDataCache()
    {
        txDataCache.clear();
        contactImageCache.clear();
        contactImageUrlsCache.clear();
        notifyDataSetChanged();
    }

	public void clearLabelCache()
	{
		labelCache.clear();
        contactImageCache.clear();
        contactImageUrlsCache.clear();
		notifyDataSetChanged();
	}

    public void loadTxDataFromDirectory(final String txId, final String address, final boolean sent)
    {
        TransactionDataProvider.getTxDataFromDirectory(context, txId, address, sent, new TransactionDataProvider.TransactionDataProviderDirectoryListener() {
            @Override
            public void foundData(TransactionDataProvider.TxData txData) {
                TransactionDataProvider.saveTxData(context, txId, txData.message, txData.label);
                txDataCache.remove(txId);
                labelCache.remove(address);
                contactImageCache.remove(address);
                notifyDataSetChanged();
            }

            @Override
            public void noData(String txId) {
                TransactionDataProvider.markLookedUp(context, txId);
            }
        });
    }

    public SpannableStringBuilder resolveTxData(final String txId, final String address, final boolean sent){

        final Object cached = txDataCache.get(txId);
        if(cached == null){


            TransactionDataProvider.TxData txData = TransactionDataProvider.getTxData(context, txId);

            if(txData == null){
                loadTxDataFromDirectory(txId, address, sent);
            }

            if(txData != null && (txData.message!=null || txData.label != null)){

                String note = txData.label != null ? txData.label : "";

                String message = txData.message != null ? txData.message : "";

                if(note.length()>0 && message.length()>0){
                    note+="\n";
                }

                if((note+message).length() == 0){
                    txDataCache.put(txId, new Object());
                    return null;
                }

                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(note+message);
                if(note.length() > 0) {
                    spannableStringBuilder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.knc_highlight)), 0, note.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if(message.length() > 0) {
                    spannableStringBuilder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.knc_mid_text)), note.length(), note.length() + message.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                txDataCache.put(txId, spannableStringBuilder);
                return spannableStringBuilder;
            }else{
                txDataCache.put(txId, new Object());
                return null;
            }

        }else{
            return cached.getClass() == SpannableStringBuilder.class ? (SpannableStringBuilder)cached : null;
        }
    }
}
