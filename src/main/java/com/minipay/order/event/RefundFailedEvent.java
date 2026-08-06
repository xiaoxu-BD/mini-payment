package com.minipay.order.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundFailedEvent {

    private String refundNo;
    private String orderNo;
    private Long amount;
}
