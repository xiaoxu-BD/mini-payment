-- ============================================================
-- 存量库迁移：补齐 BaseEntity 通用字段（updated_at / version）
-- 适用：已按旧版 schema.sql 建过库的环境；新库直接跑 schema.sql 即可
-- 注意：MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报错，请只执行一次
-- ============================================================
USE mini_payment;

ALTER TABLE t_payment ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER updated_at;

ALTER TABLE t_payment_log
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER remark,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER created_at;

ALTER TABLE t_refund_log
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER remark,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER created_at;

ALTER TABLE t_channel_notify_record
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER process_status,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER created_at;

ALTER TABLE t_recon_task
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER status,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间' AFTER created_at;

ALTER TABLE t_message_outbox ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER next_retry_time;
