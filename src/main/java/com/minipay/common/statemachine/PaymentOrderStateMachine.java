package com.minipay.common.statemachine;

import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.PaymentOrderStatus;
import com.minipay.common.exception.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 支付意图状态机（阶段3定稿）：
 * CREATED → PAYING / CLOSED；PAYING → SUCCESS / FAILED / CLOSED；
 * FAILED → PAYING(重试) / CLOSED；SUCCESS / CLOSED 为终态。
 */
public final class PaymentOrderStateMachine {

    private static final StateMachine<PaymentOrderStatus> MACHINE = StateMachine.of(Map.of(
            PaymentOrderStatus.CREATED, Set.of(PaymentOrderStatus.PAYING, PaymentOrderStatus.CLOSED),
            PaymentOrderStatus.PAYING, Set.of(PaymentOrderStatus.SUCCESS, PaymentOrderStatus.FAILED, PaymentOrderStatus.CLOSED),
            PaymentOrderStatus.FAILED, Set.of(PaymentOrderStatus.PAYING, PaymentOrderStatus.CLOSED)
    ));

    private PaymentOrderStateMachine() {
    }

    public static boolean canTransition(PaymentOrderStatus from, PaymentOrderStatus to) {
        return MACHINE.canTransition(from, to);
    }

    public static void checkTransition(PaymentOrderStatus from, PaymentOrderStatus to) {
        if (!MACHINE.canTransition(from, to)) {
            throw new BizException(ResultCode.PAYMENT_ORDER_STATUS_INVALID,
                    "非法支付意图状态转换: " + from + " -> " + to);
        }
    }
}
