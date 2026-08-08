package com.minipay.channel.mock;

/**
 * 渠道账单行（对账模块生成账单的输入）。
 */
public record BillRecord(String channelTransactionNo, Long amount) {
}
