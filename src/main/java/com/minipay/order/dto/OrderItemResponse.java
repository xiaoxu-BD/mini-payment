package com.minipay.order.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponse {

    private String itemNo;
    private Long productId;
    private String productName;
    private Long unitPrice;
    private Integer quantity;
    private Long amount;
}
