package com.minipay.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotNull(message = "用户不能为空")
    private Long userId;

    @NotBlank(message = "幂等键不能为空")
    private String idempotentKey;

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<CreateOrderItemRequest> items;
}
