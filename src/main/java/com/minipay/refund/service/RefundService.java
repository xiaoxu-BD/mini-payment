package com.minipay.refund.service;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.EnumUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.channel.ChannelGateway;
import com.minipay.channel.dto.ChannelRefundRequest;
import com.minipay.channel.dto.ChannelRefundResult;
import com.minipay.channel.ChannelNotifyRequest;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.Channel;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.OrderStatus;
import com.minipay.common.enums.PaymentOrderStatus;
import com.minipay.common.enums.PaymentStatus;
import com.minipay.common.enums.RefundStatus;
import com.minipay.common.enums.Source;
import com.minipay.common.exception.BizException;
import com.minipay.common.statemachine.OrderStateMachine;
import com.minipay.common.statemachine.RefundStateMachine;
import com.minipay.common.util.BizNoGenerator;
import com.minipay.infra.outbox.OutboxService;
import com.minipay.order.entity.Order;
import com.minipay.order.event.RefundFailedEvent;
import com.minipay.order.event.RefundSucceededEvent;
import com.minipay.order.mapper.OrderMapper;
import com.minipay.payment.entity.Payment;
import com.minipay.payment.entity.PaymentOrder;
import com.minipay.payment.mapper.PaymentMapper;
import com.minipay.payment.mapper.PaymentOrderMapper;
import com.minipay.refund.dto.RefundRequest;
import com.minipay.refund.dto.RefundResponse;
import com.minipay.refund.entity.Refund;
import com.minipay.refund.entity.RefundLog;
import com.minipay.refund.mapper.RefundMapper;
import com.minipay.refund.mapper.RefundLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 退款服务：退款单独立聚合根；refund_no 唯一约束防重；累计退款原子防超退；
 * 失败后同一 refund_no 重试（渠道幂等，阶段4场景4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundMapper refundMapper;
    private final RefundLogMapper refundLogMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final ChannelGateway channelGateway;
    private final OutboxService outboxService;

    public RefundResponse createRefund(RefundRequest request) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, request.getOrderNo()));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        OrderStatus orderStatus = EnumUtils.getEnum(OrderStatus.class, order.getStatus());
        if (!OrderStateMachine.canTransition(orderStatus, OrderStatus.REFUNDING)) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "订单当前状态不可退款");
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, request.getOrderNo())
                .eq(PaymentOrder::getStatus, PaymentOrderStatus.SUCCESS.name()));
        if (paymentOrder == null) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "订单无成功支付，不可退款");
        }
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentOrderNo, paymentOrder.getPaymentOrderNo())
                .eq(Payment::getStatus, PaymentStatus.SUCCESS.name()));
        if (payment == null) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "找不到成功支付流水");
        }
        if (paymentOrder.getRefundedAmount() + request.getAmount() > paymentOrder.getAmount()) {
            throw new BizException(ResultCode.REFUND_AMOUNT_EXCEED);
        }

        // 订单进入退款中（条件更新，防止并发重复退款操作）；可退款状态集由状态机推导
        List<String> refundable = Arrays.stream(OrderStatus.values())
                .filter(s -> OrderStateMachine.canTransition(s, OrderStatus.REFUNDING))
                .map(Enum::name)
                .toList();
        int orderRows = orderMapper.markRefunding(request.getOrderNo(), refundable);
        if (orderRows == 0) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "订单状态变更失败，可能已被并发处理");
        }

        String refundNo = BizNoGenerator.refundNo();
        Refund refund = new Refund();
        refund.setRefundNo(refundNo);
        refund.setPaymentOrderNo(paymentOrder.getPaymentOrderNo());
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setOrderNo(request.getOrderNo());
        refund.setChannel(paymentOrder.getChannel());
        refund.setAmount(request.getAmount());
        refund.setStatus(RefundStatus.CREATED.name());
        refund.setReason(request.getReason());
        refund.setOperator(request.getOperator());
        refund.setRetryCount(0);
        refundMapper.insert(refund);

        try {
            // 事务外调渠道：同一 refund_no 重试渠道侧幂等
            ChannelRefundResult result = channelGateway.createRefund(
                    new ChannelRefundRequest(
                            Channel.valueOf(refund.getChannel()), refundNo,
                            payment.getChannelTransactionNo(), request.getAmount()));
            markProcessingTx(refundNo, result.getChannelRefundNo());
        } catch (Exception e) {
            log.error("渠道创建退款失败 refundNo={}", refundNo, e);
            markFailedTx(refundNo, "渠道创建退款失败");
            throw new BizException(ResultCode.CHANNEL_ERROR, "渠道创建退款失败: " + e.getMessage());
        }
        return toResponse(findByRefundNo(refundNo));
    }

    /**
     * 退款重试：同一退款单、同一 refund_no（渠道幂等），防止重复退款。
     */
    public RefundResponse retryRefund(String refundNo, String operator) {
        Refund refund = findByRefundNo(refundNo);
        if (refund == null) {
            throw new BizException(ResultCode.REFUND_NOT_FOUND);
        }
        if (!RefundStatus.FAILED.name().equals(refund.getStatus())) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "仅失败状态的退款单可重试");
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, refund.getOrderNo()));
        if (order == null) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "订单当前状态不可重试退款");
        }
        OrderStatus orderStatus = EnumUtils.getEnum(OrderStatus.class, order.getStatus());
        if (!OrderStateMachine.canTransition(orderStatus, OrderStatus.REFUNDING)) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "订单当前状态不可重试退款");
        }
        List<String> refundable = Arrays.stream(OrderStatus.values())
                .filter(s -> OrderStateMachine.canTransition(s, OrderStatus.REFUNDING))
                .map(Enum::name)
                .toList();
        int orderRows = orderMapper.markRefunding(refund.getOrderNo(), refundable);
        if (orderRows == 0) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID);
        }
        RefundStatus refundStatus = EnumUtils.getEnum(RefundStatus.class, refund.getStatus());
        if (!RefundStateMachine.canTransition(refundStatus, RefundStatus.CREATED)) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID, "仅失败状态的退款单可重试");
        }
        int rows = refundMapper.markCreatedForRetry(refundNo, refund.getRetryCount() + 1, operator);
        if (rows == 0) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID);
        }
        try {
            Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getPaymentNo, refund.getPaymentNo()));
            ChannelRefundResult result = channelGateway.createRefund(
                    new ChannelRefundRequest(
                            Channel.valueOf(refund.getChannel()), refundNo,
                            payment == null ? null : payment.getChannelTransactionNo(), refund.getAmount()));
            markProcessingTx(refundNo, result.getChannelRefundNo());
        } catch (Exception e) {
            log.error("渠道重试退款失败 refundNo={}", refundNo, e);
            markFailedTx(refundNo, "渠道重试退款失败");
            throw new BizException(ResultCode.CHANNEL_ERROR, "渠道重试退款失败: " + e.getMessage());
        }
        return toResponse(findByRefundNo(refundNo));
    }

    /**
     * 退款成功回调：累计已退金额（原子防超退）→ 联动订单状态。
     */
    @Transactional
    public void handleRefundSuccess(ChannelNotifyRequest request) {
        Refund refund = findByRefundNo(request.getBizNo());
        if (refund == null) {
            throw new BizException(ResultCode.REFUND_NOT_FOUND);
        }
        RefundStatus refundStatus = EnumUtils.getEnum(RefundStatus.class, refund.getStatus());
        if (refundStatus == null || !RefundStateMachine.canTransition(refundStatus, RefundStatus.SUCCESS)) {
            return; // 幂等：已是成功态或非法状态
        }
        String channelRefundNo = request.getChannelTransactionNo() == null
                ? refund.getChannelRefundNo() : request.getChannelTransactionNo();
        int rows = refundMapper.markRefundSuccess(request.getBizNo(), LocalDateTime.now(),
                channelRefundNo, refundStatus.name());
        if (rows == 0) {
            return; // 并发已处理
        }

        // 原子累加已退金额：refunded_amount + amount <= amount 防超退（阶段6锁策略）
        int accRows = paymentOrderMapper.accumulateRefunded(refund.getPaymentOrderNo(), refund.getAmount());
        if (accRows == 0) {
            log.error("[告警] 累计退款金额超过实付金额 refundNo={}, paymentOrderNo={}",
                    refund.getRefundNo(), refund.getPaymentOrderNo());
        }

        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentOrderNo, refund.getPaymentOrderNo()));
        String targetStatus = ObjectUtils.equals(paymentOrder.getRefundedAmount(), paymentOrder.getAmount())
                ? OrderStatus.REFUNDED.name()
                : OrderStatus.PARTIALLY_REFUNDED.name();
        OrderStateMachine.checkTransition(OrderStatus.REFUNDING, OrderStatus.valueOf(targetStatus));
        orderMapper.updateStatusFromRefunding(refund.getOrderNo(), targetStatus);

        insertRefundLog(refund.getRefundNo(), RefundStatus.PROCESSING.name(), RefundStatus.SUCCESS.name(),
                "渠道退款成功回调");
        outboxService.record("refund", MqEventType.REFUND_SUCCEEDED,
                new RefundSucceededEvent(refund.getRefundNo(), refund.getOrderNo(), refund.getAmount()));
        log.info("退款成功 refundNo={}, orderNo={}, 订单状态->{}", refund.getRefundNo(), refund.getOrderNo(), targetStatus);
    }

    /**
     * 退款失败回调：订单回到 PAID 或 PARTIALLY_REFUNDED，退款单可重试。
     */
    @Transactional
    public void handleRefundFail(ChannelNotifyRequest request) {
        Refund refund = findByRefundNo(request.getBizNo());
        if (refund == null) {
            throw new BizException(ResultCode.REFUND_NOT_FOUND);
        }
        RefundStatus refundStatus = EnumUtils.getEnum(RefundStatus.class, refund.getStatus());
        if (refundStatus == null || !RefundStateMachine.canTransition(refundStatus, RefundStatus.FAILED)) {
            return; // 幂等：已是失败态或非法状态
        }
        int rows = refundMapper.markRefundFailed(request.getBizNo(), LocalDateTime.now(), refundStatus.name());
        if (rows == 0) {
            return;
        }
        PaymentOrder paymentOrder = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentOrderNo, refund.getPaymentOrderNo()));
        String targetStatus = ObjectUtils.defaultIfNull(paymentOrder.getRefundedAmount(), 0L) > 0
                ? OrderStatus.PARTIALLY_REFUNDED.name()
                : OrderStatus.PAID.name();
        OrderStateMachine.checkTransition(OrderStatus.REFUNDING, OrderStatus.valueOf(targetStatus));
        orderMapper.updateStatusFromRefunding(refund.getOrderNo(), targetStatus);

        insertRefundLog(refund.getRefundNo(), RefundStatus.PROCESSING.name(), RefundStatus.FAILED.name(),
                "渠道退款失败回调");
        outboxService.record("refund", MqEventType.REFUND_FAILED,
                new RefundFailedEvent(refund.getRefundNo(), refund.getOrderNo(), refund.getAmount()));
        log.info("退款失败 refundNo={}, orderNo={}, 订单状态->{}", refund.getRefundNo(), refund.getOrderNo(), targetStatus);
    }

    public RefundResponse queryRefund(String refundNo) {
        Refund refund = findByRefundNo(refundNo);
        if (refund == null) {
            throw new BizException(ResultCode.REFUND_NOT_FOUND);
        }
        return toResponse(refund);
    }

    @Transactional
    public void markProcessingTx(String refundNo, String channelRefundNo) {
        refundMapper.markProcessing(refundNo, channelRefundNo);
    }

    @Transactional
    public void markFailedTx(String refundNo, String remark) {
        refundMapper.markFailed(refundNo, LocalDateTime.now(),
                List.of(RefundStatus.CREATED.name(), RefundStatus.PROCESSING.name()));
    }

    private Refund findByRefundNo(String refundNo) {
        return refundMapper.selectOne(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getRefundNo, refundNo));
    }

    private void insertRefundLog(String refundNo, String from, String to, String remark) {
        RefundLog refundLog = new RefundLog();
        refundLog.setRefundNo(refundNo);
        refundLog.setFromStatus(from);
        refundLog.setToStatus(to);
        refundLog.setSource(Source.CHANNEL_CALLBACK.name());
        refundLog.setRemark(remark);
        refundLogMapper.insert(refundLog);
    }

    private RefundResponse toResponse(Refund refund) {
        return RefundResponse.builder()
                .refundNo(refund.getRefundNo())
                .paymentOrderNo(refund.getPaymentOrderNo())
                .paymentNo(refund.getPaymentNo())
                .orderNo(refund.getOrderNo())
                .channel(refund.getChannel())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .reason(refund.getReason())
                .operator(refund.getOperator())
                .retryCount(refund.getRetryCount())
                .build();
    }
}
