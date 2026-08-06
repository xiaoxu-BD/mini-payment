package com.minipay.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentQueryResponse {

    private String paymentNo;
    private String paymentOrderNo;
    private String orderNo;
    private String channel;
    private Long amount;
    private String status;
    private String channelTransactionNo;
    private LocalDateTime successTime;
    private LocalDateTime failTime;
    private LocalDateTime closeTime;
}
