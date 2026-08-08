package com.minipay.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.infra.event.OutboxEventHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    private Map<String, OutboxEventHandler> handlerMap;

    @PostConstruct
    public void init() {
        handlerMap = handlers.stream().collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity()));
        try {
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
            consumer.setNamesrvAddr(nameServer);
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            consumer.subscribe(topic, "*");
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt message : messages) {
                    try {
                        MqMessage envelope = objectMapper.readValue(message.getBody(), MqMessage.class);
                        OutboxEventHandler handler = handlerMap.get(envelope.eventType());
                        if (handler == null) {
                            log.warn("无事件处理器 topic={}, eventType={}", topic, envelope.eventType());
                            continue;
                        }
                        // 处理器内部的条件更新保证重复消费幂等
                        handler.handle(envelope.payload());
                        log.info("MQ 消费成功 eventId={}, eventType={}", envelope.eventId(), envelope.eventType());
                    } catch (Exception e) {
                        log.error("MQ 消费失败 msgId={}, 交由MQ重试", message.getMsgId(), e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
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
