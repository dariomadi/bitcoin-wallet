package com.kncwallet.wallet.dto;

public class UpdateTransactionLabelSent extends UpdateTransactionLabelEntry {

    public String senderNote;
    public String sentFrom;

    public UpdateTransactionLabelSent(String senderNote, String sentFrom) {
        this.senderNote = senderNote;
        this.sentFrom = sentFrom;
    }
}
