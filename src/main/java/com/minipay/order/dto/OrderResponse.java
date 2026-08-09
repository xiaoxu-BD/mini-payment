package com.minipay.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {

    private String orderNo;
    private Long userId;
    private Long totalAmount;
    private String status;
    private LocalDateTime expiredTime;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;
}
