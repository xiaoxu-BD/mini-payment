package com.minipay.common.aop;

import com.minipay.common.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB 任务级链路：每次任务执行生成/沿用 traceId，结束清理。
 */
@Aspect
@Component
public class XxlJobTraceAspect {

    @Around("@annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        TraceContext.set(null);
        try {
            return pjp.proceed();
        } finally {
            TraceContext.clear();
        }
    }
}
