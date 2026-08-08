package com.minipay.common.statemachine;

import java.util.Map;
import java.util.Set;

/**
 * 通用状态机：类加载时把 "from -> 允许的 to 集合" 规则表构建好（静态初始化）。
 * 业务侧只调用 canTransition / checkTransition，不再手写状态规则。
 *
 * @param <S> 状态枚举类型
 */
public final class StateMachine<S extends Enum<S>> {

    /** 转换规则表：from -> tos。不在表中的状态视为终态。 */
    private final Map<S, Set<S>> transitions;

    private StateMachine(Map<S, Set<S>> transitions) {
        this.transitions = Map.copyOf(transitions);
    }

    public static <S extends Enum<S>> StateMachine<S> of(Map<S, Set<S>> transitions) {
        return new StateMachine<>(transitions);
    }

    public boolean canTransition(S from, S to) {
        Set<S> targets = transitions.get(from);
        return targets != null && targets.contains(to);
    }

    /** 校验通过返回目标状态；非法转换抛 IllegalStateException。 */
    public S transition(S from, S to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("非法状态转换: " + from + " -> " + to);
        }
        return to;
    }

    public boolean isTerminal(S state) {
        return !transitions.containsKey(state);
    }
}
