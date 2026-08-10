package com.minipay.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.trace.TraceContext;
import com.minipay.infra.event.OutboxEventHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RocketMQ 消费者：按 eventType 路由到 outbox 事件处理器。
 * 消费幂等不依赖 MQ 去重，而是业务侧条件更新（阶段4场景5）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqEventConsumer {

    private final ObjectMapper objectMapper;
    private final List<OutboxEventHandler> handlers;

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.consumer-group}")
    private String consumerGroup;

    @Value("${rocketmq.topic}")
    private String topic;

    /** 事件扇出：一个事件类型可路由给多个处理器（业务处理 + 通知等） */
    private Map<String, List<OutboxEventHandler>> handlerMap;

    @PostConstruct
    public void init() {
        handlerMap = handlers.stream()
                .flatMap(h -> h.eventTypes().stream().map(t -> Map.entry(t, h)))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        try {
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
            consumer.setNamesrvAddr(nameServer);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            consumer.subscribe(topic, "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt message : messages) {
                    try {
                        MqMessage envelope = objectMapper.readValue(message.getBody(), MqMessage.class);
                        // 链路追踪：沿用生产端 traceId，消费日志与业务日志同链
                        TraceContext.set(envelope.traceId());
                        List<OutboxEventHandler> eventHandlers = handlerMap.get(envelope.eventType());
                        if (CollectionUtils.isEmpty(eventHandlers)) {
                            log.warn("无事件处理器 topic={}, eventType={}", topic, envelope.eventType());
                            continue;
                        }
                        // 事件扇出：逐个执行订阅者；各处理器内部条件更新保证重复消费幂等
                        for (OutboxEventHandler handler : eventHandlers) {
                            handler.handle(envelope.eventType(), envelope.payload());
                        }
                        log.info("MQ 消费成功 eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
                    } catch (Exception e) {
                        log.error("MQ 消费失败 msgId={}, 交由MQ重试", message.getMsgId(), e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    } finally {
                        TraceContext.clear();
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
            log.info("RocketMQ Consumer 启动成功 nameServer={}, topic={}", nameServer, topic);
        } catch (Exception e) {
            log.error("RocketMQ Consumer 启动失败 nameServer={}", nameServer, e);
        }
    }
}
