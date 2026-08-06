package com.minipay.payment.controller;

import com.minipay.common.api.Result;
import com.minipay.common.enums.CloseType;
import com.minipay.payment.dto.InitiatePaymentRequest;
import com.minipay.payment.dto.PayResponse;
import com.minipay.payment.dto.PaymentQueryResponse;
import com.minipay.payment.service.PaymentService;
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
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public Result<PayResponse> initiate(@Valid @RequestBody InitiatePaymentRequest request) {
        return Result.success(paymentService.initiatePayment(request));
    }

    @GetMapping("/query")
    public Result<PaymentQueryResponse> query(@RequestParam String paymentNo) {
        return Result.success(paymentService.queryPayment(paymentNo));
    }

    @PostMapping("/order/{orderNo}/close")
    public Result<Void> closeByOrder(@PathVariable String orderNo,
                                     @RequestParam(defaultValue = "OPERATOR") CloseType closeType,
                                     @RequestParam(required = false) String operator) {
        paymentService.closeActivePaymentOrder(orderNo, closeType, operator);
        return Result.success();
    }
}
