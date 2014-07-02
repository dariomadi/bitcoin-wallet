package com.kncwallet.wallet.ui;

import android.os.Handler;
import android.os.Looper;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;

import java.math.BigInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class SendRequestCommitTask {

    private final Wallet wallet;
    private final Handler backgroundHandler;
    private final Handler callbackHandler;

    public SendRequestCommitTask(@Nonnull final Wallet wallet, @Nonnull final Handler backgroundHandler)
    {
        this.wallet = wallet;
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
    }

    public final void commitOffline(@Nonnull final Wallet.SendRequest sendRequest)
    {
        backgroundHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    wallet.commitTx(sendRequest.tx);

                    final Transaction transaction = sendRequest.tx;

                    callbackHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            onSuccess(transaction);
                        }
                    });
                }
                catch (final IllegalArgumentException x)
                {
                    callbackHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            onFailure();
                        }
                    });
                }
            }
        });
    }

    protected abstract void onSuccess(@Nonnull Transaction transaction);

    protected abstract void onFailure();
}
