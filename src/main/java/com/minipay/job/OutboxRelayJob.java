package com.minipay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.common.enums.OutboxStatus;
import com.minipay.infra.event.OutboxEventHandler;
import com.minipay.infra.outbox.Outbox;
import com.minipay.infra.outbox.OutboxMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * outbox 本地转发器：扫描 PENDING → 交给事件处理器（本地模式）。
 * 阶段6接入 RocketMQ 后，本任务改为"投递MQ"，处理器由消费端调用。
 */
@Slf4j
//@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private static final int MAX_RETRY = 10;

    private final OutboxMapper outboxMapper;
    private final List<OutboxEventHandler> handlers;

    private Map<String, OutboxEventHandler> handlerMap;

    @PostConstruct
    public void init() {
        handlerMap = handlers.stream().collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity()));
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    public void relay() {
        List<Outbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<Outbox>()
                .eq(Outbox::getStatus, OutboxStatus.PENDING.name())
                .le(Outbox::getNextRetryTime, LocalDateTime.now())
                .last("LIMIT 100"));
        for (Outbox outbox : pending) {
            OutboxEventHandler handler = handlerMap.get(outbox.getEventType());
            if (handler == null) {
                // 无本地处理器的事件（如 PAYMENT_SUCCEEDED），留给MQ消费端处理，本轮跳过
                continue;
            }
            try {
                handler.handle(outbox.getPayload());
                outbox.setStatus(OutboxStatus.SENT.name());
                outboxMapper.updateById(outbox);
            } catch (Exception e) {
                int retry = outbox.getRetryCount() + 1;
                outbox.setRetryCount(retry);
                if (retry >= MAX_RETRY) {
                    outbox.setStatus(OutboxStatus.DEAD.name());
                    log.error("[告警] outbox 消息进入死信 eventId={}, eventType={}", outbox.getEventId(), outbox.getEventType(), e);
                } else {
                    outbox.setStatus(OutboxStatus.FAILED.name());
                    outbox.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.min(60, 2L * retry)));
                }
                outboxMapper.updateById(outbox);
            }
        }
    }
}
