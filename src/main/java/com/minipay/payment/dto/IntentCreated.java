package com.minipay.payment.dto;

import com.minipay.payment.entity.Payment;
import com.minipay.payment.entity.PaymentOrder;

/**
 * 创建支付意图的返回载体：意图 + 首笔流水。
 */
public record IntentCreated(PaymentOrder paymentOrder, Payment payment) {
}
