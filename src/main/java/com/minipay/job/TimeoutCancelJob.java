package com.minipay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.collections4.CollectionUtils;
import com.minipay.common.enums.CancelType;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.OrderStatus;
import com.minipay.common.statemachine.OrderStateMachine;
import com.minipay.infra.outbox.OutboxService;
import com.minipay.order.entity.Order;
import com.minipay.order.event.OrderCancelledEvent;
import com.minipay.order.mapper.OrderMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 超时取消扫描：批量条件更新（状态守卫 + 过期校验），重复执行天然安全；
 * 仅对真正被取消的订单批量写 outbox 事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutCancelJob {

    private final OrderMapper orderMapper;
    private final OutboxService outboxService;

    @XxlJob("timeoutCancelOrderJob")
    public void cancelExpiredOrders() {
        // 可取消状态集由状态机推导：当前仅 PENDING_PAYMENT → CANCELLED
        List<String> cancellable = Arrays.stream(OrderStatus.values())
                .filter(s -> OrderStateMachine.canTransition(s, OrderStatus.CANCELLED))
                .map(Enum::name)
                .toList();
        List<Order> candidates = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, cancellable)
                .lt(Order::getExpiredTime, LocalDateTime.now())
                .last("LIMIT 200"));
        if (CollectionUtils.isEmpty(candidates)) {
            return;
        }
        XxlJobHelper.log("超时取消扫描: 候选 {} 单", candidates.size());
        List<String> orderNos = candidates.stream().map(Order::getOrderNo).toList();
        LocalDateTime now = LocalDateTime.now();
        // 批量条件取消：状态守卫 + 过期校验，竞态中支付成功回调获胜的订单不会被取消
        int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, OrderStatus.CANCELLED.name())
                .set(Order::getCancelType, CancelType.TIMEOUT.name())
                .set(Order::getCancelTime, now)
                .in(Order::getOrderNo, orderNos)
                .in(Order::getStatus, cancellable)
                .lt(Order::getExpiredTime, now));
        if (affected == 0) {
            return;
        }
        // 仅对真正被取消的订单发事件，避免误发竞态中已支付的订单
        List<Order> cancelled = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getOrderNo, orderNos)
                .eq(Order::getStatus, OrderStatus.CANCELLED.name()));
        if (CollectionUtils.isEmpty(cancelled)) {
            return;
        }
        List<Object> payloads = cancelled.stream()
                .map(o -> (Object) new OrderCancelledEvent(o.getOrderNo(), CancelType.TIMEOUT.name()))
                .toList();
        outboxService.recordBatch("order", MqEventType.ORDER_CANCELLED, payloads);
    }
}
