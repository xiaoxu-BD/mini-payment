package com.minipay.refund.controller;

import com.minipay.common.api.Result;
import com.minipay.refund.dto.RefundRequest;
import com.minipay.refund.dto.RefundResponse;
import com.minipay.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    public Result<RefundResponse> create(@Valid @RequestBody RefundRequest request) {
        return Result.success(refundService.createRefund(request));
    }

    @PostMapping("/{refundNo}/retry")
    public Result<RefundResponse> retry(@PathVariable String refundNo,
                                        @RequestBody(required = false) RefundRequest request) {
        String operator = request == null ? null : request.getOperator();
        return Result.success(refundService.retryRefund(refundNo, operator));
    }

    @GetMapping("/{refundNo}")
    public Result<RefundResponse> query(@PathVariable String refundNo) {
        return Result.success(refundService.queryRefund(refundNo));
    }
}
