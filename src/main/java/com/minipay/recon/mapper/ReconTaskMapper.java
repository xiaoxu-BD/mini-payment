package com.minipay.recon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.minipay.recon.entity.ReconTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReconTaskMapper extends BaseMapper<ReconTask> {

    int finishTask(@Param("id") Long id,
                   @Param("billFile") String billFile,
                   @Param("totalCount") Integer totalCount,
                   @Param("matchedCount") Integer matchedCount,
                   @Param("diffCount") Integer diffCount,
                   @Param("status") String status,
                   @Param("finishedAt") LocalDateTime finishedAt,
                   @Param("updatedAt") LocalDateTime updatedAt);
}
