-- 存量库迁移：t_message_outbox 增加 trace_id 链路追踪字段（只执行一次）
USE mini_payment;

ALTER TABLE t_message_outbox ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID' AFTER event_id;
