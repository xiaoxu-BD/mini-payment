package com.minipay.channel;

import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.NotifyProcessStatus;
import com.minipay.common.enums.OrderStatus;
import com.minipay.common.enums.PaymentOrderStatus;
import com.minipay.common.enums.PaymentStatus;
import com.minipay.common.enums.Source;
import com.minipay.common.exception.BizException;
import com.minipay.infra.outbox.OutboxService;
import com.minipay.order.entity.Order;
import com.minipay.order.event.PaymentFailedEvent;
import com.minipay.order.event.PaymentSucceededEvent;
import com.minipay.order.mapper.OrderMapper;
import com.minipay.payment.entity.Payment;
import com.minipay.payment.entity.PaymentLog;
import com.minipay.payment.entity.PaymentOrder;
import com.minipay.payment.mapper.PaymentLogMapper;
import com.minipay.payment.mapper.PaymentMapper;
import com.minipay.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 渠道回调处理：幂等账本(dedup_key唯一约束) → 状态守卫条件更新 → 同事务更新支付/意图/订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelNotifyService {

    private final ChannelNotifyRecordMapper notifyRecordMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentLogMapper paymentLogMapper;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;
    private final com.minipay.refund.service.RefundService refundService;

    @Transactional
    public void handleNotify(ChannelNotifyRequest request) {
        // ===== 第一道防线：幂等账本 =====
        String dedupKey = StringUtils.isNotBlank(request.getNotifyId())
                ? "N:" + request.getNotifyId()
                : request.getChannel() + ":" + request.getChannelTransactionNo() + ":" + request.getEventType();
        ChannelNotifyRecord record = new ChannelNotifyRecord();
        record.setDedupKey(dedupKey);
        record.setChannel(request.getChannel().name());
        record.setEventType(request.getEventType().name());
        record.setBizType(request.getBizType().name());
        record.setBizNo(request.getBizNo());
        record.setRawPayload(request.toString());
        record.setProcessStatus(NotifyProcessStatus.PROCESSED.name());
        try {
            notifyRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.info("重复回调，直接幂等返回 dedupKey={}", dedupKey);
            return;
        }

        // ===== 业务分发 =====
        switch (request.getEventType()) {
            case PAY_SUCCESS -> handlePaySuccess(request);
            case PAY_FAIL -> handlePayFail(request);
            case REFUND_SUCCESS -> refundService.handleRefundSuccess(request);
            case REFUND_FAIL -> refundService.handleRefundFail(request);
            default -> throw new BizException(ResultCode.CHANNEL_NOTIFY_INVALID, "未知事件类型");
        }
    }

    private void handlePaySuccess(ChannelNotifyRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, request.getBizNo()));
        if (payment == null) {
            throw new BizException(ResultCode.CHANNEL_NOTIFY_INVALID, "支付流水不存在: " + request.getBizNo());
        }
        if (PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            log.info("支付已是成功态，幂等返回 paymentNo={}", request.getBizNo());
            return;
        }

        // 第二道防线：状态守卫条件更新（并发重复回调只有一个成功）
        int payRows = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, PaymentStatus.SUCCESS.name())
                .set(Payment::getSuccessTime, now)
                .set(StringUtils.isNotBlank(request.getChannelTransactionNo()),
                        Payment::getChannelTransactionNo, request.getChannelTransactionNo())
                .eq(Payment::getPaymentNo, request.getBizNo())
                .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
        if (payRows == 0) {
            log.info("支付流水已被并发处理 paymentNo={}", request.getBizNo());
            return;
        }

        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrderStatus.SUCCESS.name())
                .set(PaymentOrder::getSuccessTime, now)
                .eq(PaymentOrder::getPaymentOrderNo, payment.getPaymentOrderNo())
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAYING.name()));

        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, OrderStatus.PAID.name())
                .set(Order::getPaidTime, now)
                .eq(Order::getOrderNo, payment.getOrderNo())
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name()));
        if (orderRows == 0) {
            // 竞态：订单已被取消/关闭，渠道却成功 → 单边账，日终对账+人工兜底（阶段4场景3）
            log.error("[告警] 渠道支付成功但订单状态不允许更新 orderNo={}, paymentNo={}，需对账/人工处理",
                    payment.getOrderNo(), payment.getPaymentNo());
        }

        insertPaymentLog(payment.getPaymentNo(), PaymentStatus.PAYING.name(), PaymentStatus.SUCCESS.name(),
                "渠道支付成功回调");
        outboxService.record("payment", MqEventType.PAYMENT_SUCCEEDED,
                new PaymentSucceededEvent(payment.getPaymentNo(), payment.getPaymentOrderNo(),
                        payment.getOrderNo(), payment.getAmount()));
        log.info("支付成功落库 paymentNo={}, orderNo={}", payment.getPaymentNo(), payment.getOrderNo());
    }

    private void handlePayFail(ChannelNotifyRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, request.getBizNo()));
        if (payment == null) {
            throw new BizException(ResultCode.CHANNEL_NOTIFY_INVALID, "支付流水不存在: " + request.getBizNo());
        }
        if (PaymentStatus.FAILED.name().equals(payment.getStatus())) {
            return;
        }
        int payRows = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, PaymentStatus.FAILED.name())
                .set(Payment::getFailTime, now)
                .eq(Payment::getPaymentNo, request.getBizNo())
                .eq(Payment::getStatus, PaymentStatus.PAYING.name()));
        if (payRows == 0) {
            return;
        }
        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrderStatus.FAILED.name())
                .eq(PaymentOrder::getPaymentOrderNo, payment.getPaymentOrderNo())
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.PAYING.name()));

        insertPaymentLog(payment.getPaymentNo(), PaymentStatus.PAYING.name(), PaymentStatus.FAILED.name(),
                "渠道支付失败回调");
        outboxService.record("payment", MqEventType.PAYMENT_FAILED,
                new PaymentFailedEvent(payment.getPaymentNo(), payment.getPaymentOrderNo(),
                        payment.getOrderNo(), payment.getAmount()));
        log.info("支付失败落库 paymentNo={}", payment.getPaymentNo());
    }

    private void insertPaymentLog(String paymentNo, String from, String to, String remark) {
        PaymentLog logEntity = new PaymentLog();
        logEntity.setPaymentNo(paymentNo);
        logEntity.setFromStatus(from);
        logEntity.setToStatus(to);
        logEntity.setSource(Source.CHANNEL_CALLBACK.name());
        logEntity.setRemark(remark);
        paymentLogMapper.insert(logEntity);
    }
}
