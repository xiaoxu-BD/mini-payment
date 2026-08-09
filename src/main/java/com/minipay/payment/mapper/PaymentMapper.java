package com.minipay.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    int markPaySuccess(@Param("paymentNo") String paymentNo,
                       @Param("successTime") LocalDateTime successTime,
                       @Param("channelTransactionNo") String channelTransactionNo,
                       @Param("fromStatus") String fromStatus);

    int markPayFailed(@Param("paymentNo") String paymentNo,
                      @Param("failTime") LocalDateTime failTime,
                      @Param("fromStatus") String fromStatus);

    int markPaying(@Param("paymentNo") String paymentNo,
                   @Param("channelTransactionNo") String channelTransactionNo,
                   @Param("payUrl") String payUrl);

    int closePayments(@Param("paymentOrderNos") List<String> paymentOrderNos,
                      @Param("closablePayments") List<String> closablePayments,
                      @Param("closeTime") LocalDateTime closeTime);
}
