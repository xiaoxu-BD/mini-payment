package com.minipay.refund.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResponse {

    private String refundNo;
    private String paymentOrderNo;
    private String paymentNo;
    private String orderNo;
    private String channel;
    private Long amount;
    private String status;
    private String reason;
    private String operator;
    private Integer retryCount;
}
