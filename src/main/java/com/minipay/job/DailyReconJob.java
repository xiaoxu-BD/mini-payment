package com.minipay.job;

import com.minipay.common.enums.Channel;
import com.minipay.common.exception.BizException;
import com.minipay.recon.dto.ReconRunRequest;
import com.minipay.recon.dto.ReconTaskResponse;
import com.minipay.recon.service.ReconService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 日终对账（XXL-JOB 调度）：默认对前一天账期逐渠道执行；重复执行由 (channel, bill_date) 唯一约束拦截。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReconJob {

    private final ReconService reconService;

    @XxlJob("reconDailyJob")
    public void runDailyRecon() {
        LocalDate billDate = LocalDate.now().minusDays(1);
        for (Channel channel : Channel.values()) {
            try {
                ReconRunRequest request = new ReconRunRequest();
                request.setChannel(channel);
                request.setBillDate(billDate);
                request.setInjectAnomalies(false);
                ReconTaskResponse response = reconService.runRecon(request);
                XxlJobHelper.log("对账完成 channel={}, billDate={}, 总数={}, 差异={}",
                        channel, billDate, response.getTotalCount(), response.getDiffCount());
            } catch (BizException e) {
                // 已有任务（唯一约束）或账单异常，记录日志继续下一渠道
                XxlJobHelper.log("对账跳过 channel={}, reason={}", channel, e.getMessage());
            }
        }
    }
}
