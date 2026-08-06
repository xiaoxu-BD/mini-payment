package com.minipay.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {

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
}
