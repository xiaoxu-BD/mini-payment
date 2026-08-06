package com.minipay.refund.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_refund")
public class Refund extends BaseEntity {

    private String refundNo;
    private String paymentOrderNo;
    private String paymentNo;
    private String orderNo;
    private String channel;
    private String channelRefundNo;
    private Long amount;
    private String status;
    private String reason;
    private String operator;
    private Integer retryCount;
    private LocalDateTime successTime;
    private LocalDateTime failTime;
}
