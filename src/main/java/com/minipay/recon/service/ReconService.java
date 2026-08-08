package com.minipay.recon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.minipay.channel.mock.MockChannelGateway;
import com.minipay.channel.mock.BillRecord;
import com.minipay.common.api.ResultCode;
import com.minipay.common.enums.Channel;
import com.minipay.common.enums.PaymentStatus;
import com.minipay.common.enums.ReconDiffStatus;
import com.minipay.common.enums.ReconDiffType;
import com.minipay.common.enums.ReconTaskStatus;
import com.minipay.common.exception.BizException;
import com.minipay.common.statemachine.ReconDifferenceStateMachine;
import com.minipay.common.util.BizNoGenerator;
import com.minipay.payment.entity.Payment;
import com.minipay.payment.mapper.PaymentMapper;
import com.minipay.recon.dto.ReconDifferenceResponse;
import com.minipay.recon.dto.ReconHandleRequest;
import com.minipay.recon.dto.ReconRunRequest;
import com.minipay.recon.dto.ReconTaskResponse;
import com.minipay.recon.dto.BillRow;
import com.minipay.recon.entity.ReconDifference;
import com.minipay.recon.entity.ReconTask;
import com.minipay.recon.mapper.ReconDifferenceMapper;
import com.minipay.recon.mapper.ReconTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日终对账：生成/导入渠道账单(commons-csv) → 逐笔比对 → 差异落库(OPEN) → 人工挂起/处理/关闭(D6)。
 * 只识别差异、不自动修复；账单异常任务置 FAILED，不产出错误差异。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconService {

    private final ReconTaskMapper reconTaskMapper;
    private final ReconDifferenceMapper reconDifferenceMapper;
    private final PaymentMapper paymentMapper;
    private final MockChannelGateway mockChannelGateway;

    @Value("${app.recon.bill-dir:./recon-bills}")
    private String billDir;

    public ReconTaskResponse runRecon(ReconRunRequest request) {
        ReconTask exist = reconTaskMapper.selectOne(new LambdaQueryWrapper<ReconTask>()
                .eq(ReconTask::getChannel, request.getChannel().name())
                .eq(ReconTask::getBillDate, request.getBillDate()));
        if (exist != null) {
            throw new BizException(ResultCode.RECON_TASK_EXISTS);
        }

        ReconTask task = new ReconTask();
        task.setTaskNo(BizNoGenerator.taskNo());
        task.setChannel(request.getChannel().name());
        task.setBillDate(request.getBillDate());
        task.setStatus(ReconTaskStatus.RUNNING.name());
        task.setTotalCount(0);
        task.setMatchedCount(0);
        task.setDiffCount(0);
        reconTaskMapper.insert(task);

        String filePath = Paths.get(billDir,
                "bill_" + request.getChannel() + "_" + request.getBillDate() + ".csv").toString();
        task.setBillFile(filePath);
        try {
            generateBillFile(request.getChannel(), filePath, request.isInjectAnomalies());
            List<BillRow> billRows = parseBillFile(filePath);
            reconcile(task, request.getChannel(), billRows);
            task.setStatus(ReconTaskStatus.COMPLETED.name());
            task.setFinishedAt(LocalDateTime.now());
            reconTaskMapper.updateById(task);
        } catch (Exception e) {
            task.setStatus(ReconTaskStatus.FAILED.name());
            task.setFinishedAt(LocalDateTime.now());
            reconTaskMapper.updateById(task);
            log.error("[告警] 对账失败 channel={}, billDate={}", request.getChannel(), request.getBillDate(), e);
            throw new BizException(ResultCode.RECON_BILL_INVALID, "对账失败: " + e.getMessage());
        }
        return toResponse(reconTaskMapper.selectById(task.getId()));
    }

    public List<ReconDifferenceResponse> queryDifferences(String status) {
        LambdaQueryWrapper<ReconDifference> wrapper = new LambdaQueryWrapper<ReconDifference>()
                .orderByDesc(ReconDifference::getId)
                .last("LIMIT 100");
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(ReconDifference::getStatus, status);
        }
        return reconDifferenceMapper.selectList(wrapper).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void hang(String differenceNo, ReconHandleRequest request) {
        ReconDifference diff = requireDiff(differenceNo);
        ReconDifferenceStateMachine.checkTransition(
                EnumUtils.getEnum(ReconDiffStatus.class, diff.getStatus()), ReconDiffStatus.HANG);
        diff.setStatus(ReconDiffStatus.HANG.name());
        applyHandle(diff, request);
        if (reconDifferenceMapper.updateById(diff) == 0) {
            throw new BizException(ResultCode.RECON_DIFF_STATUS_INVALID, "并发冲突，请刷新后重试");
        }
    }

    @Transactional
    public void resolve(String differenceNo, ReconHandleRequest request) {
        ReconDifference diff = requireDiff(differenceNo);
        ReconDifferenceStateMachine.checkTransition(
                EnumUtils.getEnum(ReconDiffStatus.class, diff.getStatus()), ReconDiffStatus.RESOLVED);
        diff.setStatus(ReconDiffStatus.RESOLVED.name());
        applyHandle(diff, request);
        if (reconDifferenceMapper.updateById(diff) == 0) {
            throw new BizException(ResultCode.RECON_DIFF_STATUS_INVALID, "并发冲突，请刷新后重试");
        }
    }

    @Transactional
    public void close(String differenceNo, ReconHandleRequest request) {
        ReconDifference diff = requireDiff(differenceNo);
        ReconDifferenceStateMachine.checkTransition(
                EnumUtils.getEnum(ReconDiffStatus.class, diff.getStatus()), ReconDiffStatus.CLOSED);
        diff.setStatus(ReconDiffStatus.CLOSED.name());
        applyHandle(diff, request);
        if (reconDifferenceMapper.updateById(diff) == 0) {
            throw new BizException(ResultCode.RECON_DIFF_STATUS_INVALID, "并发冲突，请刷新后重试");
        }
    }

    // ================= 内部实现 =================

    private void generateBillFile(Channel channel, String filePath, boolean injectAnomalies) throws IOException {
        FileUtils.forceMkdirParent(new File(filePath));
        List<BillRecord> records = mockChannelGateway.snapshotSuccessfulPayments(channel);
        boolean amountModified = false;
        try (CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(Paths.get(filePath), StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            printer.printRecord("channel_transaction_no", "amount", "status");
            for (BillRecord record : records) {
                long amount = record.amount();
                if (injectAnomalies && !amountModified) {
                    amount += 100; // 模拟"金额不一致"差异
                    amountModified = true;
                }
                printer.printRecord(record.channelTransactionNo(), amount, "SUCCESS");
            }
            if (injectAnomalies) {
                // 模拟"渠道有、系统无"的单边账
                printer.printRecord("FAKE_CHANNEL_ONLY_" + channel + "_" + System.currentTimeMillis(), 5000, "SUCCESS");
            }
        }
        log.info("渠道账单已生成 file={}, 行数={}", filePath, records.size());
    }

    private List<BillRow> parseBillFile(String filePath) throws IOException {
        List<BillRow> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("channel_transaction_no", "amount", "status")
                .setSkipHeaderRecord(true)
                .build();
        try (CSVParser parser = format.parse(
                Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8))) {
            for (CSVRecord record : parser) {
                String txn = record.get("channel_transaction_no");
                Long amount = NumberUtils.toLong(record.get("amount"), 0L);
                if (StringUtils.equalsIgnoreCase(record.get("status"), "SUCCESS")) {
                    rows.add(new BillRow(txn, amount));
                }
            }
        }
        return rows;
    }

    private void reconcile(ReconTask task, Channel channel, List<BillRow> billRows) {
        Set<String> billTxns = billRows.stream().map(BillRow::txn).collect(Collectors.toSet());
        int matched = 0;

        // 一次性加载系统成功流水，避免逐笔查库
        List<Payment> systemSuccess = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getChannel, channel.name())
                .eq(Payment::getStatus, PaymentStatus.SUCCESS.name()));
        Map<String, Payment> paymentByTxn = systemSuccess.stream()
                .filter(p -> StringUtils.isNotBlank(p.getChannelTransactionNo()))
                .collect(Collectors.toMap(Payment::getChannelTransactionNo, Function.identity(), (a, b) -> a));

        // 渠道流水逐笔比对：匹配不上→单边账(渠道多)；金额不一致→AMOUNT_MISMATCH
        for (BillRow row : billRows) {
            Payment payment = paymentByTxn.get(row.txn());
            if (payment == null) {
                insertDifference(task, ReconDiffType.CHANNEL_ONLY, row.txn(), null, row.amount(), null);
            } else if (!ObjectUtils.equals(payment.getAmount(), row.amount())) {
                insertDifference(task, ReconDiffType.AMOUNT_MISMATCH, row.txn(),
                        payment.getPaymentNo(), row.amount(), payment.getAmount());
            } else {
                matched++;
            }
        }

        // 系统成功流水但账单缺失 → 单边账(系统多)
        for (Payment payment : systemSuccess) {
            if (StringUtils.isBlank(payment.getChannelTransactionNo())
                    || !billTxns.contains(payment.getChannelTransactionNo())) {
                insertDifference(task, ReconDiffType.SYSTEM_ONLY, payment.getChannelTransactionNo(),
                        payment.getPaymentNo(), null, payment.getAmount());
            }
        }

        task.setTotalCount(billRows.size());
        task.setMatchedCount(matched);
        task.setDiffCount(reconDifferenceMapper.selectCount(new LambdaQueryWrapper<ReconDifference>()
                .eq(ReconDifference::getTaskId, task.getId())).intValue());
    }

    private void insertDifference(ReconTask task, ReconDiffType type, String channelTxn,
                                  String paymentNo, Long channelAmount, Long systemAmount) {
        ReconDifference diff = new ReconDifference();
        diff.setDifferenceNo(BizNoGenerator.differenceNo());
        diff.setTaskId(task.getId());
        diff.setChannel(task.getChannel());
        diff.setBillDate(task.getBillDate());
        diff.setDiffType(type.name());
        diff.setChannelTransactionNo(channelTxn);
        diff.setPaymentNo(paymentNo);
        diff.setChannelAmount(channelAmount);
        diff.setSystemAmount(systemAmount);
        diff.setStatus(ReconDiffStatus.OPEN.name());
        reconDifferenceMapper.insert(diff);
    }

    private ReconDifference requireDiff(String differenceNo) {
        ReconDifference diff = reconDifferenceMapper.selectOne(new LambdaQueryWrapper<ReconDifference>()
                .eq(ReconDifference::getDifferenceNo, differenceNo));
        if (diff == null) {
            throw new BizException(ResultCode.RECON_DIFF_NOT_FOUND);
        }
        return diff;
    }

    private void applyHandle(ReconDifference diff, ReconHandleRequest request) {
        diff.setOperator(request == null ? null : request.getOperator());
        diff.setRemark(request == null ? null : request.getRemark());
        diff.setHandleTime(LocalDateTime.now());
    }

    private ReconTaskResponse toResponse(ReconTask task) {
        return ReconTaskResponse.builder()
                .taskNo(task.getTaskNo())
                .channel(task.getChannel())
                .billDate(task.getBillDate())
                .billFile(task.getBillFile())
                .totalCount(task.getTotalCount())
                .matchedCount(task.getMatchedCount())
                .diffCount(task.getDiffCount())
                .status(task.getStatus())
                .finishedAt(task.getFinishedAt())
                .build();
    }

    private ReconDifferenceResponse toResponse(ReconDifference diff) {
        return ReconDifferenceResponse.builder()
                .differenceNo(diff.getDifferenceNo())
                .taskId(diff.getTaskId())
                .channel(diff.getChannel())
                .billDate(diff.getBillDate())
                .diffType(diff.getDiffType())
                .channelTransactionNo(diff.getChannelTransactionNo())
                .paymentNo(diff.getPaymentNo())
                .channelAmount(diff.getChannelAmount())
                .systemAmount(diff.getSystemAmount())
                .status(diff.getStatus())
                .operator(diff.getOperator())
                .handleTime(diff.getHandleTime())
                .remark(diff.getRemark())
                .build();
    }

}
