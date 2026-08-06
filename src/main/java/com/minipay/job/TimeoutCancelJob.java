package com.minipay.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.common.enums.CancelType;
import com.minipay.common.enums.OrderStatus;
import com.minipay.order.entity.Order;
import com.minipay.order.mapper.OrderMapper;
import com.minipay.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时取消扫描：先选候选，再逐单条件更新（0行=已被并发处理），重复执行天然安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutCancelJob {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void cancelExpiredOrders() {
        List<Order> candidates = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name())
                .lt(Order::getExpiredTime, LocalDateTime.now())
                .last("LIMIT 200"));
        if (candidates.isEmpty()) {
            return;
        }
        log.info("超时取消扫描: 候选 {} 单", candidates.size());
        for (Order order : candidates) {
            try {
                orderService.cancelOrder(order.getOrderNo(), CancelType.TIMEOUT, null);
            } catch (Exception e) {
                // 竞态中支付回调获胜、或已被其他实例取消，属正常，记录即可
                log.info("订单取消跳过 orderNo={}, reason={}", order.getOrderNo(), e.getMessage());
            }
        }
    }
}
