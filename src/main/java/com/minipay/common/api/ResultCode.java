package com.minipay.common.api;

import lombok.Getter;

/**
 * 统一返回码：0 成功；1xxx 订单；2xxx 支付；3xxx 退款；4xxx 渠道；5xxx 对账；6xxx MQ。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统繁忙"),

    ORDER_NOT_FOUND(1001, "订单不存在"),
    ORDER_STATUS_INVALID(1002, "订单状态不允许该操作"),
    ORDER_EXPIRED(1003, "订单已过期"),
    ORDER_CANCELLED(1004, "订单已取消"),
    ORDER_ALREADY_PAID(1005, "订单已支付"),
    ORDER_ITEM_AMOUNT_MISMATCH(1006, "订单明细金额与总额不一致"),

    PAYMENT_ORDER_NOT_FOUND(2001, "支付单不存在"),
    PAYMENT_ORDER_STATUS_INVALID(2002, "支付单状态不允许该操作"),
    PAYMENT_NOT_FOUND(2003, "支付流水不存在"),
    PAYMENT_STATUS_INVALID(2004, "支付流水状态不允许该操作"),
    PAYMENT_ALREADY_SUCCESS(2005, "支付已成功"),
    PAYMENT_INTENT_EXISTS(2006, "该订单已有进行中的支付意图"),

    REFUND_NOT_FOUND(3001, "退款单不存在"),
    REFUND_STATUS_INVALID(3002, "退款单状态不允许该操作"),
    REFUND_AMOUNT_EXCEED(3003, "累计退款金额超过实付金额"),
    REFUND_DUPLICATE(3004, "退款请求重复"),

    CHANNEL_ERROR(4001, "渠道调用失败"),
    CHANNEL_RESPONSE_INVALID(4002, "渠道响应异常"),
    CHANNEL_NOTIFY_INVALID(4003, "渠道回调验签失败"),

    RECON_TASK_EXISTS(5001, "对账任务已存在"),
    RECON_DIFF_NOT_FOUND(5002, "对账差异不存在"),
    RECON_DIFF_STATUS_INVALID(5003, "差异状态不允许该操作"),
    RECON_BILL_INVALID(5004, "对账账单异常"),

    MQ_SEND_FAILED(6001, "消息发送失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
