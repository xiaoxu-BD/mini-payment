package com.minipay.common.statemachine;

import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.PaymentStatus;
import com.minipay.common.exception.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 支付流水状态机（阶段3定稿）：流水层无回环。
 * CREATED → PAYING；PAYING → SUCCESS / FAILED / CLOSED；其余为终态。
 */
public final class PaymentStateMachine {

    private static final StateMachine<PaymentStatus> MACHINE = StateMachine.of(Map.of(
            PaymentStatus.CREATED, Set.of(PaymentStatus.PAYING),
            PaymentStatus.PAYING, Set.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.CLOSED)
    ));

    private PaymentStateMachine() {
    }

    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return MACHINE.canTransition(from, to);
    }

    public static void checkTransition(PaymentStatus from, PaymentStatus to) {
        if (!MACHINE.canTransition(from, to)) {
            throw new BizException(ResultCode.PAYMENT_STATUS_INVALID,
                    "非法支付流水状态转换: " + from + " -> " + to);
        }
    }
}
