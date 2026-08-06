package com.minipay.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayResponse {

    private String paymentOrderNo;
    private String paymentNo;
    private String orderNo;
    private String channel;
    private String status;
    private String payUrl;
}
