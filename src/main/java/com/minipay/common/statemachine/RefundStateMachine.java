package com.minipay.common.statemachine;

import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.RefundStatus;
import com.minipay.common.exception.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 退款单状态机（阶段3定稿）：
 * CREATED → PROCESSING / FAILED；PROCESSING → SUCCESS / FAILED；
 * FAILED → CREATED(同一refund_no重试)；SUCCESS 为终态。
 */
public final class RefundStateMachine {

    private static final StateMachine<RefundStatus> MACHINE = StateMachine.of(Map.of(
            RefundStatus.CREATED, Set.of(RefundStatus.PROCESSING, RefundStatus.FAILED),
            RefundStatus.PROCESSING, Set.of(RefundStatus.SUCCESS, RefundStatus.FAILED),
            RefundStatus.FAILED, Set.of(RefundStatus.CREATED)
    ));

    private RefundStateMachine() {
    }

    public static boolean canTransition(RefundStatus from, RefundStatus to) {
        return MACHINE.canTransition(from, to);
    }

    public static void checkTransition(RefundStatus from, RefundStatus to) {
        if (!MACHINE.canTransition(from, to)) {
            throw new BizException(ResultCode.REFUND_STATUS_INVALID,
                    "非法退款单状态转换: " + from + " -> " + to);
        }
    }
}
