package com.minipay.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_payment")
public class Payment extends BaseEntity {

    private String paymentNo;
    private String paymentOrderNo;
    private String orderNo;
    private String channel;
    private String channelTransactionNo;
    private Long amount;
    private String status;
    private String channelPayUrl;
    private LocalDateTime successTime;
    private LocalDateTime failTime;
    private LocalDateTime closeTime;
}
