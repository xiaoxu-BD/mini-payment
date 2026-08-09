package com.minipay.recon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.recon.entity.ReconDifference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReconDifferenceMapper extends BaseMapper<ReconDifference> {

    int updateHandleInfo(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("operator") String operator,
                         @Param("remark") String remark,
                         @Param("handleTime") LocalDateTime handleTime,
                         @Param("version") Integer version,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
