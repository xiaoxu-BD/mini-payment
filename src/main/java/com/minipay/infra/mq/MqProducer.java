package com.minipay.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.api.ResultCode;
import com.minipay.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者：tag=事件类型，key=eventId（便于消费端/控制台按幂等键查询）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqProducer {

    private final ObjectMapper objectMapper;

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer-group}")
    private String producerGroup;

    @Value("${rocketmq.topic}")
    private String topic;

    private volatile DefaultMQProducer producer;

    @PostConstruct
    public void init() {
        try {
            DefaultMQProducer mqProducer = new DefaultMQProducer(producerGroup);
            mqProducer.setNamesrvAddr(nameServer);
            mqProducer.setSendMsgTimeout(3_000);
            mqProducer.start();
            this.producer = mqProducer;
            log.info("RocketMQ Producer 启动成功 nameServer={}, topic={}", nameServer, topic);
        } catch (Exception e) {
            // MQ 不可用时应用照常启动，消息留在 outbox 由转发任务重投
            log.error("RocketMQ Producer 启动失败，消息将停留在 outbox 等待重投 nameServer={}", nameServer, e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }

    public void publish(MqMessage message) {
        if (producer == null) {
            throw new BizException(ResultCode.MQ_SEND_FAILED, "RocketMQ 不可用");
        }
        try {
            Message msg = new Message(topic, message.eventType(), message.eventId(),
                    objectMapper.writeValueAsBytes(message));
            SendResult result = producer.send(msg);
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                throw new BizException(ResultCode.MQ_SEND_FAILED, "MQ 发送失败: " + result.getSendStatus());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.MQ_SEND_FAILED, "MQ 发送异常: " + e.getMessage());
        }
    }
}
