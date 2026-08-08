CREATE DATABASE IF NOT EXISTS mini_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE mini_payment;

-- ============ 订单域 ============
CREATE TABLE IF NOT EXISTS t_order (
    id              BIGINT       NOT NULL COMMENT '主键',
    order_no        VARCHAR(32)  NOT NULL COMMENT '业务订单号',
    user_id         BIGINT       NOT NULL COMMENT '下单用户',
    total_amount    BIGINT       NOT NULL COMMENT '订单总额(分)',
    status          VARCHAR(32)  NOT NULL COMMENT '订单状态: PENDING_PAYMENT/PAID/CANCELLED/REFUNDING/PARTIALLY_REFUNDED/REFUNDED',
    expired_time    DATETIME(3)  NOT NULL COMMENT '支付过期时间',
    cancel_type     VARCHAR(16)  DEFAULT NULL COMMENT '取消类型: USER/TIMEOUT/OPERATOR',
    cancel_time     DATETIME(3)  DEFAULT NULL COMMENT '取消时间',
    paid_time       DATETIME(3)  DEFAULT NULL COMMENT '支付成功时间',
    idempotent_key  VARCHAR(64)  NOT NULL COMMENT '下单幂等键',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_idempotent_key (idempotent_key),
    KEY idx_user_created (user_id, created_at),
    KEY idx_status_expired (status, expired_time)
) ENGINE = InnoDB COMMENT ='业务订单';

CREATE TABLE IF NOT EXISTS t_order_item (
    id            BIGINT       NOT NULL COMMENT '主键',
    order_no      VARCHAR(32)  NOT NULL COMMENT '业务订单号',
    item_no       VARCHAR(32)  NOT NULL COMMENT '明细号',
    product_id    BIGINT       NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(128) NOT NULL COMMENT '商品名快照(D4)',
    unit_price    BIGINT       NOT NULL COMMENT '单价快照(分)',
    quantity      INT          NOT NULL COMMENT '数量',
    amount        BIGINT       NOT NULL COMMENT '小计(分)',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at    DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB COMMENT ='订单明细';

-- ============ 支付域 ============
CREATE TABLE IF NOT EXISTS t_payment_order (
    id                BIGINT      NOT NULL COMMENT '主键',
    payment_order_no  VARCHAR(32) NOT NULL COMMENT '支付意图号',
    order_no          VARCHAR(32) NOT NULL COMMENT '业务订单号',
    user_id           BIGINT      NOT NULL COMMENT '用户',
    channel           VARCHAR(16) NOT NULL COMMENT '渠道: WECHAT/ALIPAY',
    amount            BIGINT      NOT NULL COMMENT '支付金额(分)，创建时锁定',
    status            VARCHAR(32) NOT NULL COMMENT '状态: CREATED/PAYING/SUCCESS/FAILED/CLOSED',
    refunded_amount   BIGINT      NOT NULL DEFAULT 0 COMMENT '累计已退(分)，只在退款成功时累加',
    expired_time      DATETIME(3) NOT NULL COMMENT '意图过期时间',
    close_type        VARCHAR(16) DEFAULT NULL COMMENT '关闭类型: USER/TIMEOUT/OPERATOR',
    close_time        DATETIME(3) DEFAULT NULL COMMENT '关闭时间',
    success_time      DATETIME(3) DEFAULT NULL COMMENT '支付成功时间',
    version           INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_no (payment_order_no),
    KEY idx_order_status (order_no, status),
    KEY idx_status_expired (status, expired_time)
) ENGINE = InnoDB COMMENT ='支付意图';

CREATE TABLE IF NOT EXISTS t_payment (
    id                      BIGINT      NOT NULL COMMENT '主键',
    payment_no              VARCHAR(32) NOT NULL COMMENT '支付流水号',
    payment_order_no        VARCHAR(32) NOT NULL COMMENT '归属支付意图',
    order_no                VARCHAR(32) NOT NULL COMMENT '业务订单号(冗余，对账免Join)',
    channel                 VARCHAR(16) NOT NULL COMMENT '渠道',
    channel_transaction_no  VARCHAR(64) DEFAULT NULL COMMENT '渠道交易号(渠道内唯一，响应后回填)',
    amount                  BIGINT      NOT NULL COMMENT '本次支付金额(分)',
    status                  VARCHAR(32) NOT NULL COMMENT '状态: CREATED/PAYING/SUCCESS/FAILED/CLOSED',
    channel_pay_url         VARCHAR(512) DEFAULT NULL COMMENT '同步返回收银台地址/二维码内容',
    success_time            DATETIME(3) DEFAULT NULL,
    fail_time               DATETIME(3) DEFAULT NULL,
    close_time              DATETIME(3) DEFAULT NULL,
    created_at              DATETIME(3) NOT NULL,
    updated_at              DATETIME(3) NOT NULL,
    version                 INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_channel_txn (channel, channel_transaction_no),
    KEY idx_payment_order_no (payment_order_no),
    KEY idx_status_created (status, created_at)
) ENGINE = InnoDB COMMENT ='支付流水';

CREATE TABLE IF NOT EXISTS t_payment_log (
    id           BIGINT      NOT NULL COMMENT '主键',
    payment_no   VARCHAR(32) NOT NULL COMMENT '支付流水号',
    from_status  VARCHAR(32) DEFAULT NULL COMMENT '旧状态',
    to_status    VARCHAR(32) NOT NULL COMMENT '新状态',
    source       VARCHAR(16) NOT NULL COMMENT '来源: SYSTEM/CHANNEL_CALLBACK/TIMER/OPERATOR/RECON',
    operator     VARCHAR(64) DEFAULT NULL COMMENT '操作人(人工时必填)',
    remark       VARCHAR(255) DEFAULT NULL,
    version      INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at   DATETIME(3) NOT NULL,
    updated_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payment_created (payment_no, created_at)
) ENGINE = InnoDB COMMENT ='支付轨迹(append-only)';

CREATE TABLE IF NOT EXISTS t_refund (
    id                BIGINT      NOT NULL COMMENT '主键',
    refund_no         VARCHAR(32) NOT NULL COMMENT '退款单号(防重锚点)',
    payment_order_no  VARCHAR(32) NOT NULL COMMENT '归属支付意图',
    payment_no        VARCHAR(32) NOT NULL COMMENT '原成功流水',
    order_no          VARCHAR(32) NOT NULL COMMENT '业务订单号',
    channel           VARCHAR(16) NOT NULL COMMENT '渠道',
    channel_refund_no VARCHAR(64) DEFAULT NULL COMMENT '渠道退款号(渠道内唯一)',
    amount            BIGINT      NOT NULL COMMENT '本次退款金额(分)',
    status            VARCHAR(32) NOT NULL COMMENT '状态: CREATED/PROCESSING/SUCCESS/FAILED',
    reason            VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
    operator          VARCHAR(64) DEFAULT NULL COMMENT '发起人',
    retry_count       INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
    success_time      DATETIME(3) DEFAULT NULL,
    fail_time         DATETIME(3) DEFAULT NULL,
    version           INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at        DATETIME(3) NOT NULL,
    updated_at        DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_channel_refund (channel, channel_refund_no),
    KEY idx_payment_order_status (payment_order_no, status)
) ENGINE = InnoDB COMMENT ='退款单';

CREATE TABLE IF NOT EXISTS t_refund_log (
    id           BIGINT      NOT NULL COMMENT '主键',
    refund_no    VARCHAR(32) NOT NULL COMMENT '退款单号',
    from_status  VARCHAR(32) DEFAULT NULL,
    to_status    VARCHAR(32) NOT NULL,
    source       VARCHAR(16) NOT NULL,
    operator     VARCHAR(64) DEFAULT NULL,
    remark       VARCHAR(255) DEFAULT NULL,
    version      INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at   DATETIME(3) NOT NULL,
    updated_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_refund_created (refund_no, created_at)
) ENGINE = InnoDB COMMENT ='退款轨迹(append-only)';

-- ============ 渠道与对账域 ============
CREATE TABLE IF NOT EXISTS t_channel_notify_record (
    id              BIGINT      NOT NULL COMMENT '主键',
    dedup_key       VARCHAR(64) NOT NULL COMMENT '幂等锚点: 渠道通知号 或 channel+交易号+事件类型',
    channel         VARCHAR(16) NOT NULL COMMENT '渠道',
    event_type      VARCHAR(32) NOT NULL COMMENT 'PAY_SUCCESS/PAY_FAIL/REFUND_SUCCESS/REFUND_FAIL',
    biz_type        VARCHAR(16) NOT NULL COMMENT 'PAY/REFUND',
    biz_no          VARCHAR(32) NOT NULL COMMENT 'payment_no/refund_no',
    raw_payload     TEXT        COMMENT '原始报文',
    process_status  VARCHAR(16) NOT NULL COMMENT 'PROCESSED/DUPLICATE/FAILED',
    version         INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at      DATETIME(3) NOT NULL,
    updated_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dedup_key (dedup_key),
    KEY idx_biz (biz_type, biz_no)
) ENGINE = InnoDB COMMENT ='渠道回调记录(幂等账本)';

CREATE TABLE IF NOT EXISTS t_recon_task (
    id            BIGINT      NOT NULL COMMENT '主键',
    task_no       VARCHAR(32) NOT NULL COMMENT '对账任务号',
    channel       VARCHAR(16) NOT NULL COMMENT '渠道',
    bill_date     DATE        NOT NULL COMMENT '账期',
    bill_file     VARCHAR(255) DEFAULT NULL COMMENT '渠道账单文件路径',
    total_count   INT         NOT NULL DEFAULT 0,
    matched_count INT         NOT NULL DEFAULT 0,
    diff_count    INT         NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL COMMENT 'RUNNING/COMPLETED/FAILED',
    version       INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL,
    finished_at   DATETIME(3) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    UNIQUE KEY uk_channel_date (channel, bill_date)
) ENGINE = InnoDB COMMENT ='对账批次';

CREATE TABLE IF NOT EXISTS t_recon_difference (
    id                      BIGINT      NOT NULL COMMENT '主键',
    difference_no           VARCHAR(32) NOT NULL COMMENT '差异编号',
    task_id                 BIGINT      NOT NULL COMMENT '对账批次ID',
    channel                 VARCHAR(16) NOT NULL,
    bill_date               DATE        NOT NULL,
    diff_type               VARCHAR(16) NOT NULL COMMENT 'CHANNEL_ONLY/SYSTEM_ONLY/AMOUNT_MISMATCH',
    channel_transaction_no  VARCHAR(64) DEFAULT NULL COMMENT '渠道侧交易号',
    payment_no              VARCHAR(32) DEFAULT NULL COMMENT '系统侧流水号(可空)',
    channel_amount          BIGINT      DEFAULT NULL COMMENT '渠道金额(分)',
    system_amount           BIGINT      DEFAULT NULL COMMENT '系统金额(分)',
    status                  VARCHAR(16) NOT NULL COMMENT 'OPEN/HANG(挂起)/RESOLVED/CLOSED',
    operator                VARCHAR(64) DEFAULT NULL COMMENT '处理人',
    handle_time             DATETIME(3) DEFAULT NULL,
    remark                  VARCHAR(255) DEFAULT NULL,
    version                 INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at              DATETIME(3) NOT NULL,
    updated_at              DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_difference_no (difference_no),
    KEY idx_task_id (task_id),
    KEY idx_type_status (diff_type, status)
) ENGINE = InnoDB COMMENT ='对账差异';

-- ============ 基础设施 ============
CREATE TABLE IF NOT EXISTS t_message_outbox (
    id               BIGINT      NOT NULL COMMENT '主键',
    event_id         VARCHAR(64) NOT NULL COMMENT '消息幂等键',
    service          VARCHAR(32) NOT NULL COMMENT '来源服务: order/payment/refund/recon',
    event_type       VARCHAR(64) NOT NULL COMMENT '事件类型: OrderCreated/PaymentSucceeded/...',
    payload          TEXT        COMMENT '消息体(JSON)',
    status           VARCHAR(16) NOT NULL COMMENT 'PENDING/SENT/FAILED/DEAD',
    retry_count      INT         NOT NULL DEFAULT 0 COMMENT '重投次数',
    next_retry_time  DATETIME(3) NOT NULL COMMENT '下次重投时间',
    version          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE = InnoDB COMMENT ='本地消息表(outbox)';



-- 历史数据回滚；
INSERT INTO mini_payment.t_message_outbox (id, event_id, service, event_type, payload, status, retry_count, next_retry_time, created_at, updated_at) VALUES (2085334673407369219, '2085334673407369218', 'order', 'ORDER_CREATED', '{"orderNo":"O2085334673214431234","userId":1001,"totalAmount":19700}', 'PENDING', 0, '2026-08-06 19:58:24.449', '2026-08-06 19:58:24.450', '2026-08-06 19:58:24.450');
INSERT INTO mini_payment.t_message_outbox (id, event_id, service, event_type, payload, status, retry_count, next_retry_time, created_at, updated_at) VALUES (2085335702135922690, '2085335702135922689', 'order', 'ORDER_CANCELLED', '{"orderNo":"O2085334673214431234","cancelType":"USER"}', 'PENDING', 0, '2026-08-06 20:02:29.716', '2026-08-06 20:02:29.716', '2026-08-06 20:02:29.716');
