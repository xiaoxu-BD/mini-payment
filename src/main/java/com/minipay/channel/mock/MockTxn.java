package com.minipay.channel.mock;

import lombok.Data;

/**
 * mock 渠道的内存交易记录。
 */
@Data
class MockTxn {

    private final String txnNo;
    private final Long amount;
    private volatile String status;

    MockTxn(String txnNo, Long amount, String status) {
        this.txnNo = txnNo;
        this.amount = amount;
        this.status = status;
    }
}
