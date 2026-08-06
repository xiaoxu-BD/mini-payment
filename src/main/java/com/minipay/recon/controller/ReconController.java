package com.minipay.recon.controller;

import com.minipay.common.api.Result;
import com.minipay.recon.dto.ReconDifferenceResponse;
import com.minipay.recon.dto.ReconHandleRequest;
import com.minipay.recon.dto.ReconRunRequest;
import com.minipay.recon.dto.ReconTaskResponse;
import com.minipay.recon.service.ReconService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recon")
@RequiredArgsConstructor
public class ReconController {

    private final ReconService reconService;

    @PostMapping("/run")
    public Result<ReconTaskResponse> run(@Valid @RequestBody ReconRunRequest request) {
        return Result.success(reconService.runRecon(request));
    }

    @GetMapping("/differences")
    public Result<List<ReconDifferenceResponse>> differences(@RequestParam(required = false) String status) {
        return Result.success(reconService.queryDifferences(status));
    }

    @PostMapping("/differences/{differenceNo}/hang")
    public Result<Void> hang(@PathVariable String differenceNo, @RequestBody(required = false) ReconHandleRequest request) {
        reconService.hang(differenceNo, request);
        return Result.success();
    }

    @PostMapping("/differences/{differenceNo}/resolve")
    public Result<Void> resolve(@PathVariable String differenceNo, @RequestBody(required = false) ReconHandleRequest request) {
        reconService.resolve(differenceNo, request);
        return Result.success();
    }

    @PostMapping("/differences/{differenceNo}/close")
    public Result<Void> close(@PathVariable String differenceNo, @RequestBody(required = false) ReconHandleRequest request) {
        reconService.close(differenceNo, request);
        return Result.success();
    }
}
