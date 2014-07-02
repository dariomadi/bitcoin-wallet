package com.kncwallet.wallet.ui.util;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TxUtil {

    public static boolean isDust(Wallet wallet, Transaction tx) {

        final BigInteger value = tx.getValue(wallet);
        final boolean sent = value.signum() < 0;

        return (!sent && isDust(value));
    }

    public static boolean isDust(BigInteger value){
        return value.compareTo(Transaction.MIN_NONDUST_OUTPUT) < 0;
    }

    public static List<Transaction> getTransactionsWithoutDust(Wallet wallet, Collection<Transaction> transactions) {
        List<Transaction> filteredTransactions = new ArrayList<Transaction>();

        for (Transaction tx : transactions) {
            if (!isDust(wallet, tx)) {
                filteredTransactions.add(tx);
            }
        }

        return filteredTransactions;
    }
}
