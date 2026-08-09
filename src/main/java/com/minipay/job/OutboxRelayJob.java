package com.minipay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.common.enums.OutboxStatus;
import com.minipay.infra.mq.MqMessage;
import com.minipay.infra.mq.MqProducer;
import com.minipay.infra.outbox.Outbox;
import com.minipay.infra.outbox.OutboxMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * outbox 转发器（XXL-JOB 调度）：扫描 PENDING → 投递 RocketMQ → 批量标记 SENT。
 * 投递失败进入 FAILED/DEAD 等待下轮重投；消费端幂等由业务条件更新保证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private static final int MAX_RETRY = 10;

    private final OutboxMapper outboxMapper;
    private final MqProducer mqProducer;

    @XxlJob("outboxRelayJob")
    public void relay() {
        List<Outbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<Outbox>()
                .eq(Outbox::getStatus, OutboxStatus.PENDING.name())
                .le(Outbox::getNextRetryTime, LocalDateTime.now())
                .last("LIMIT 100"));
        if (CollectionUtils.isEmpty(pending)) {
            return;
        }
        XxlJobHelper.log("outbox 转发: 候选 {} 条", pending.size());
        List<Long> sentIds = new ArrayList<>();
        for (Outbox outbox : pending) {
            try {
                mqProducer.publish(new MqMessage(outbox.getEventId(), outbox.getEventType(), outbox.getPayload()));
                sentIds.add(outbox.getId());
            } catch (Exception e) {
                int retry = outbox.getRetryCount() + 1;
                outbox.setRetryCount(retry);
                if (retry >= MAX_RETRY) {
                    outbox.setStatus(OutboxStatus.DEAD.name());
                    log.error("[告警] outbox 消息进入死信 eventId={}, eventType={}",
                            outbox.getEventId(), outbox.getEventType(), e);
                } else {
                    outbox.setStatus(OutboxStatus.FAILED.name());
                    outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(60, 2L * retry)));
                }
                outboxMapper.updateRetry(outbox.getId(), retry, outbox.getStatus(),
                        outbox.getNextRetryTime(), LocalDateTime.now());
            }
        }
        // 批量标记已投递，避免循环内逐条 update
        if (CollectionUtils.isNotEmpty(sentIds)) {
            outboxMapper.markSent(sentIds, LocalDateTime.now());
        }
    }
}
