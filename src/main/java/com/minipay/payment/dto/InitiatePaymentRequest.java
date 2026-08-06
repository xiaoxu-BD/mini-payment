package com.minipay.payment.dto;

import com.minipay.common.enums.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "渠道不能为空")
    private Channel channel;
}
