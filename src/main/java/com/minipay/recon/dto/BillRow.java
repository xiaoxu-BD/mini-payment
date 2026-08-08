package com.minipay.recon.dto;

/**
 * 渠道账单解析行。
 */
public record BillRow(String txn, Long amount) {
}
