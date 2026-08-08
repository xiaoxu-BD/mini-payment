package com.minipay.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderItemRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotBlank(message = "商品名不能为空")
    private String productName;

    @NotNull(message = "单价不能为空")
    @Min(value = 1, message = "单价必须大于0")
    private Long unitPrice;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    private Integer quantity;
}
