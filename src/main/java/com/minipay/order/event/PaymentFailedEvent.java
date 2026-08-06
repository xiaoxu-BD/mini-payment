package com.minipay.order.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {

    private String paymentNo;
    private String paymentOrderNo;
    private String orderNo;
    private Long amount;
}
