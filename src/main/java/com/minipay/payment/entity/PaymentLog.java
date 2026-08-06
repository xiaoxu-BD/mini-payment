package com.minipay.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.minipay.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_payment_log")
public class PaymentLog extends BaseEntity {

    private String paymentNo;
    private String fromStatus;
    private String toStatus;
    private String source;
    private String operator;
    private String remark;
}
