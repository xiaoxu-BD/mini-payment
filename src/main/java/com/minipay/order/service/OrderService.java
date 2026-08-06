package com.minipay.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.CancelType;
import com.minipay.common.enums.MqEventType;
import com.minipay.common.enums.OrderStatus;
import com.minipay.common.exception.BizException;
import com.minipay.common.util.BizNoGenerator;
import com.minipay.infra.outbox.OutboxService;
import com.minipay.order.dto.CreateOrderRequest;
import com.minipay.order.dto.OrderResponse;
import com.minipay.order.entity.Order;
import com.minipay.order.entity.OrderItem;
import com.minipay.order.event.OrderCancelledEvent;
import com.minipay.order.event.OrderCreatedEvent;
import com.minipay.order.mapper.OrderItemMapper;
import com.minipay.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String SERVICE = "order";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OutboxService outboxService;

    @Value("${app.order.expire-minutes:15}")
    private long expireMinutes;

    /**
     * 创建订单：幂等校验 → 模拟算价 → 本地事务(订单+明细+outbox)。
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order exist = findByOrderIdempotentKey(request.getIdempotentKey());
        if (exist != null) {
            return toResponse(exist);
        }

        String orderNo = BizNoGenerator.orderNo();
        LocalDateTime now = LocalDateTime.now();

        // 模拟商品服务算价 + 商品快照(D4)
        long totalAmount = 0L;
        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderRequest.ItemRequest item : request.getItems()) {
            long amount = item.getUnitPrice() * item.getQuantity();
            totalAmount += amount;
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setItemNo("IT" + BizNoGenerator.eventId());
            orderItem.setProductId(item.getProductId());
            orderItem.setProductName(item.getProductName());
            orderItem.setUnitPrice(item.getUnitPrice());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setAmount(amount);
            items.add(orderItem);
        }
        if (totalAmount <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "订单金额必须大于0");
        }

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setExpiredTime(now.plusMinutes(expireMinutes));
        order.setIdempotentKey(request.getIdempotentKey());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发重复请求：幂等键唯一约束兜底，直接返回已存在订单
            Order existOrder = findByOrderIdempotentKey(request.getIdempotentKey());
            if (existOrder == null) {
                throw new BizException(ResultCode.SYSTEM_ERROR, "订单创建冲突，请重试");
            }
            return toResponse(existOrder);
        }
        for (OrderItem item : items) {
            //TODO 循环操作数据库
            orderItemMapper.insert(item);
        }

        outboxService.record(SERVICE, MqEventType.ORDER_CREATED,
                new OrderCreatedEvent(orderNo, order.getUserId(), order.getTotalAmount()));
        return toResponse(order);
    }

    /**
     * 取消订单（用户/定时/运营共用）：条件更新保证与支付成功回调互斥。
     */
    @Transactional
    public void cancelOrder(String orderNo, CancelType cancelType, String operator) {
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, OrderStatus.CANCELLED.name())
                .set(Order::getCancelType, cancelType.name())
                .set(Order::getCancelTime, now)
                .eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT.name()));
        if (rows == 0) {
            Order order = findByOrderNo(orderNo);
            if (order == null) {
                throw new BizException(ResultCode.ORDER_NOT_FOUND);
            }
            if (OrderStatus.PAID.name().equals(order.getStatus())) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID, "订单已支付，不能取消，请走退款");
            }
            if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
                return; // 已取消，幂等返回
            }
            throw new BizException(ResultCode.ORDER_STATUS_INVALID);
        }
        log.info("订单取消成功 orderNo={}, cancelType={}, operator={}", orderNo, cancelType, operator);
        outboxService.record(SERVICE, MqEventType.ORDER_CANCELLED,
                new OrderCancelledEvent(orderNo, cancelType.name()));
    }

    public OrderResponse queryOrder(String orderNo) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return toResponse(order);
    }

    public Order findByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
    }

    private Order findByOrderIdempotentKey(String idempotentKey) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getIdempotentKey, idempotentKey));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, order.getOrderNo()));
        List<OrderResponse.ItemResponse> itemResponses = items.stream()
                .map(i -> OrderResponse.ItemResponse.builder()
                        .itemNo(i.getItemNo())
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .amount(i.getAmount())
                        .build())
                .toList();
        return OrderResponse.builder()
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .expiredTime(order.getExpiredTime())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
