package com.minipay.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_payment_order")
public class PaymentOrder extends BaseEntity {

    private String paymentOrderNo;
    private String orderNo;
    private Long userId;
    private String channel;
    private Long amount;
    private String status;
    private Long refundedAmount;
    private LocalDateTime expiredTime;
    private String closeType;
    private LocalDateTime closeTime;
    private LocalDateTime successTime;
}
