package com.minipay.order.controller;

import com.minipay.common.api.Result;
import com.minipay.common.enums.CancelType;
import com.minipay.order.dto.CreateOrderRequest;
import com.minipay.order.dto.OrderResponse;
import com.minipay.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return Result.success(orderService.createOrder(request));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderResponse> query(@PathVariable String orderNo) {
        return Result.success(orderService.queryOrder(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestParam(defaultValue = "USER") CancelType cancelType,
                               @RequestParam(required = false) String operator) {
        orderService.cancelOrder(orderNo, cancelType, operator);
        return Result.success();
    }
}
