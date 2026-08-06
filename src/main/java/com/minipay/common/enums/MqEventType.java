package com.minipay.common.enums;

/**
 * 跨模块领域事件。
 */
public enum MqEventType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    PAYMENT_INITIATED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    REFUND_SUCCEEDED,
    REFUND_FAILED
}
