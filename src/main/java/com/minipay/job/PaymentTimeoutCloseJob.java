package com.minipay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.channel.ChannelGateway;
import com.minipay.channel.ChannelNotifyRequest;
import com.minipay.channel.ChannelNotifyService;
import com.minipay.channel.dto.ChannelQueryRequest;
import com.minipay.channel.dto.ChannelQueryResult;
import com.minipay.common.enums.BizType;
import com.minipay.common.enums.Channel;
import com.minipay.common.enums.CloseType;
import com.minipay.common.enums.NotifyEventType;
import com.minipay.common.enums.PaymentOrderStatus;
import com.minipay.common.enums.PaymentStatus;
import com.minipay.payment.entity.Payment;
import com.minipay.payment.entity.PaymentOrder;
import com.minipay.payment.mapper.PaymentMapper;
import com.minipay.payment.mapper.PaymentOrderMapper;
import com.minipay.payment.service.PaymentService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时兜底：先向渠道查单（防"渠道已成功但回调丢失"），再关闭超时意图。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutCloseJob {

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentMapper paymentMapper;
    private final ChannelGateway channelGateway;
    private final ChannelNotifyService channelNotifyService;
    private final PaymentService paymentService;

    @XxlJob("paymentTimeoutCloseJob")
    public void closeExpiredPayingIntents() {
        List<PaymentOrder> expired = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAYING.name())
                .lt(PaymentOrder::getExpiredTime, LocalDateTime.now())
                .last("LIMIT 200"));
        if (expired.isEmpty()) {
            return;
        }
        XxlJobHelper.log("支付超时兜底: 候选 {} 单", expired.size());
        for (PaymentOrder paymentOrder : expired) {
            try {
                Payment inFlight = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getPaymentOrderNo, paymentOrder.getPaymentOrderNo())
                        .eq(Payment::getStatus, PaymentStatus.PAYING.name())
                        .last("LIMIT 1"));
                if (inFlight != null && inFlight.getChannelTransactionNo() != null) {
                    ChannelQueryResult result = channelGateway.queryPayment(
                            new ChannelQueryRequest(
                                    Channel.valueOf(inFlight.getChannel()), inFlight.getChannelTransactionNo()));
                    if ("SUCCESS".equals(result.getStatus())) {
                        // 渠道已成功但回调丢失：走标准回调流程补齐（查单兜底，阶段4场景3第3层）
                        ChannelNotifyRequest notify = new ChannelNotifyRequest();
                        notify.setChannel(Channel.valueOf(inFlight.getChannel()));
                        notify.setBizType(BizType.PAY);
                        notify.setBizNo(inFlight.getPaymentNo());
                        notify.setEventType(NotifyEventType.PAY_SUCCESS);
                        notify.setChannelTransactionNo(inFlight.getChannelTransactionNo());
                        notify.setAmount(inFlight.getAmount());
                        channelNotifyService.handleNotify(notify);
                        continue;
                    }
                }
                paymentService.closeActivePaymentOrder(paymentOrder.getOrderNo(), CloseType.TIMEOUT, null);
            } catch (Exception e) {
                log.error("支付超时处理失败 paymentOrderNo={}", paymentOrder.getPaymentOrderNo(), e);
            }
        }
    }
}
