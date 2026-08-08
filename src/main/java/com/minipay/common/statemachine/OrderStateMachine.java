package com.minipay.common.statemachine;

import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.OrderStatus;
import com.minipay.common.exception.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 业务订单状态机（阶段3定稿）：
 * PENDING_PAYMENT → PAID / CANCELLED；PAID → REFUNDING；
 * REFUNDING → PAID / PARTIALLY_REFUNDED / REFUNDED；PARTIALLY_REFUNDED → REFUNDING；
 * CANCELLED / REFUNDED 为终态。
 */
public final class OrderStateMachine {

    private static final StateMachine<OrderStatus> MACHINE = StateMachine.of(Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.REFUNDING),
            OrderStatus.REFUNDING, Set.of(OrderStatus.PAID, OrderStatus.PARTIALLY_REFUNDED, OrderStatus.REFUNDED),
            OrderStatus.PARTIALLY_REFUNDED, Set.of(OrderStatus.REFUNDING)
    ));

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return MACHINE.canTransition(from, to);
    }

    public static void checkTransition(OrderStatus from, OrderStatus to) {
        if (!MACHINE.canTransition(from, to)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "非法订单状态转换: " + from + " -> " + to);
        }
    }
}
