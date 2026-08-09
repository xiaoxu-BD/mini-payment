package com.minipay.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    int markSuccess(@Param("paymentOrderNo") String paymentOrderNo,
                    @Param("successTime") LocalDateTime successTime,
                    @Param("fromStatus") String fromStatus);

    int markFailed(@Param("paymentOrderNo") String paymentOrderNo,
                   @Param("fromStatuses") List<String> fromStatuses);

    int accumulateRefunded(@Param("paymentOrderNo") String paymentOrderNo,
                           @Param("amount") Long amount);

    int closeIntents(@Param("paymentOrderNos") List<String> paymentOrderNos,
                     @Param("closableIntents") List<String> closableIntents,
                     @Param("closeType") String closeType,
                     @Param("closeTime") LocalDateTime closeTime);
}
