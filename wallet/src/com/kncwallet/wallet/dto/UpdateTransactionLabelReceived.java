package com.kncwallet.wallet.dto;

public class UpdateTransactionLabelReceived extends UpdateTransactionLabelEntry {

    public String receiverNote;
    public String sentTo;

    public UpdateTransactionLabelReceived(String receiverNote, String sentTo) {
        this.receiverNote = receiverNote;
        this.sentTo = sentTo;
    }
}
