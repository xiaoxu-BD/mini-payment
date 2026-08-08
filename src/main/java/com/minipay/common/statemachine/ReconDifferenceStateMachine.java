package com.minipay.common.statemachine;

import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.ReconDiffStatus;
import com.minipay.common.exception.BizException;

import java.util.Map;
import java.util.Set;

/**
 * 对账差异状态机：OPEN → HANG(人工挂起) → RESOLVED → CLOSED，逐级推进，不可跳级。
 */
public final class ReconDifferenceStateMachine {

    private static final StateMachine<ReconDiffStatus> MACHINE = StateMachine.of(Map.of(
            ReconDiffStatus.OPEN, Set.of(ReconDiffStatus.HANG),
            ReconDiffStatus.HANG, Set.of(ReconDiffStatus.RESOLVED),
            ReconDiffStatus.RESOLVED, Set.of(ReconDiffStatus.CLOSED)
    ));

    private ReconDifferenceStateMachine() {
    }

    public static boolean canTransition(ReconDiffStatus from, ReconDiffStatus to) {
        return MACHINE.canTransition(from, to);
    }

    public static void checkTransition(ReconDiffStatus from, ReconDiffStatus to) {
        if (!MACHINE.canTransition(from, to)) {
            throw new BizException(ResultCode.RECON_DIFF_STATUS_INVALID,
                    "非法差异状态转换: " + from + " -> " + to);
        }
    }
}
