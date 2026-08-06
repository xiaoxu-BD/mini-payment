package com.minipay.recon.dto;

import com.minipay.common.enums.Channel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReconRunRequest {

    @NotNull(message = "渠道不能为空")
    private Channel channel;

    @NotNull(message = "账期不能为空")
    private LocalDate billDate;

    /** 仅测试用：往账单里注入一笔单边账和一笔金额不一致，用于演示差异识别 */
    private boolean injectAnomalies;
}
