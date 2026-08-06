# Mini Payment System 接口文档

## 0. 通用约定

- Base URL：`http://localhost:8080`
- 金额单位：**分**（BIGINT）
- 时间格式：`yyyy-MM-dd HH:mm:ss`；账期日期格式：`yyyy-MM-dd`
- 统一响应结构：

```json
{
  "code": 0,
  "message": "成功",
  "data": {}
}
```

- `code = 0` 成功；非 0 失败，`message` 为原因（错误码见文末）。
- 业务号含义：`O` 订单号 / `PO` 支付意图号 / `P` 支付流水号 / `R` 退款单号 / `T` 对账任务号 / `D` 对账差异号。

---

## 1. 订单服务

### 1.1 创建订单

`POST /api/orders`

请求体：

```json
{
  "userId": 1001,
  "idempotentKey": "REQ-20260804-001",
  "items": [
    { "productId": 101, "productName": "Java 面试指南", "unitPrice": 9900, "quantity": 1 },
    { "productId": 102, "productName": "支付系统设计", "unitPrice": 4900, "quantity": 2 }
  ]
}
```

说明：
- `idempotentKey` 为下单幂等键，同一键重复请求返回同一订单；
- `unitPrice` 单价（分）、`quantity` 数量；订单总额 = 各明细 `unitPrice * quantity` 之和；
- 商品信息按快照落库（D4）。

响应 `data`：

```json
{
  "orderNo": "O1920...",
  "userId": 1001,
  "totalAmount": 19700,
  "status": "PENDING_PAYMENT",
  "expiredTime": "2026-08-04 12:00:00",
  "createdAt": "2026-08-04 11:45:00",
  "items": [
    { "itemNo": "IT...", "productId": 101, "productName": "Java 面试指南", "unitPrice": 9900, "quantity": 1, "amount": 9900 }
  ]
}
```

### 1.2 查询订单

`GET /api/orders/{orderNo}`

响应 `data` 同上（含明细）。

### 1.3 取消订单

`POST /api/orders/{orderNo}/cancel?cancelType=USER&operator=ops01`

- `cancelType`：`USER`（默认）/ `TIMEOUT` / `OPERATOR`；
- 仅 `PENDING_PAYMENT` 可取消；已取消幂等成功；已支付返回 `1002`（请走退款）；
- 取消成功会联动关闭支付意图并通知渠道（D7）。

---

## 2. 支付服务

### 2.1 发起支付

`POST /api/payments/initiate`

```json
{ "orderNo": "O1920...", "channel": "WECHAT" }
```

`channel`：`WECHAT` / `ALIPAY`

响应 `data`：

```json
{
  "paymentOrderNo": "PO...",
  "paymentNo": "P...",
  "orderNo": "O1920...",
  "channel": "WECHAT",
  "status": "PAYING",
  "payUrl": "mock://wechat/pay?txn=CHWECHAT..."
}
```

说明：
- 同一订单存在进行中意图时幂等返回原 `payUrl`；
- 订单已支付返回 `1005`，已过期返回 `1003`；
- 渠道创建失败返回 `4001`，意图进入 `FAILED`，订单未过期时可重新发起（D8）；
- 每次重试生成新的 `paymentNo`（A001 → A002 语义）。

### 2.2 查询支付流水

`GET /api/payments/query?paymentNo=P...`

响应 `data`：`paymentNo / paymentOrderNo / orderNo / channel / amount / status / channelTransactionNo / successTime / failTime / closeTime`。

### 2.3 关闭支付意图（运营操作）

`POST /api/payments/order/{orderNo}/close?closeType=OPERATOR&operator=ops01`

- `closeType`：`USER` / `TIMEOUT` / `OPERATOR`（默认 `OPERATOR`）；
- 关闭订单下所有非终态意图，并联动关闭渠道支付单。

---

## 3. 渠道回调

### 3.1 渠道回调入口（真实渠道对接形态）

`POST /api/channel/notify/{channel}`，`channel`：`WECHAT` / `ALIPAY`

```json
{
  "bizType": "PAY",
  "bizNo": "P...",
  "eventType": "PAY_SUCCESS",
  "notifyId": "NOTIFY-20260804-001",
  "channelTransactionNo": "CHWECHAT...",
  "amount": 19700
}
```

- `bizType`：`PAY` / `REFUND`；`eventType`：`PAY_SUCCESS` / `PAY_FAIL` / `REFUND_SUCCESS` / `REFUND_FAIL`；
- `bizNo` 传对应业务号（支付流水号或退款单号）；
- **幂等**：同一 `notifyId` 重复推送只处理一次，一律返回成功；
- 重复到达、状态已是终态时自动幂等返回（阶段4场景1）。

### 3.2 mock 渠道回调触发器（测试用）

`POST /mock-channel/{channel}/notify`

```json
{
  "bizType": "PAY",
  "bizNo": "P...",
  "eventType": "PAY_SUCCESS",
  "channelTransactionNo": "CHWECHAT...",
  "amount": 19700
}
```

每次调用自动生成新 `notifyId`；用同一 `bizNo` 重复调用可验证回调幂等。

---

## 4. 退款服务

### 4.1 创建退款

`POST /api/refunds`

```json
{ "orderNo": "O1920...", "amount": 5000, "reason": "用户退货", "operator": "ops01" }
```

前提：订单 `PAID` 或 `PARTIALLY_REFUNDED`；累计退款 ≤ 实付金额（超出返回 `3003`）。

响应 `data`：

```json
{
  "refundNo": "R...",
  "paymentOrderNo": "PO...",
  "paymentNo": "P...",
  "orderNo": "O1920...",
  "channel": "WECHAT",
  "amount": 5000,
  "status": "PROCESSING",
  "reason": "用户退货",
  "operator": "ops01",
  "retryCount": 0
}
```

### 4.2 重试退款

`POST /api/refunds/{refundNo}/retry`

请求体可选：`{ "operator": "ops01" }`

- 仅 `FAILED` 状态可重试；同一 `refund_no` 重发，渠道侧幂等（阶段4场景4）。

### 4.3 查询退款

`GET /api/refunds/{refundNo}`

---

## 5. 对账服务

### 5.1 执行对账

`POST /api/recon/run`

```json
{ "channel": "WECHAT", "billDate": "2026-08-04", "injectAnomalies": true }
```

- `injectAnomalies = true` 时账单会注入一笔"金额不一致"和一笔"渠道单边"，用于演示差异识别；
- 同一渠道同一账期只能跑一次，重复执行返回 `5001`；
- 账单解析失败 → 任务 `FAILED` + 告警，不产出错误差异。

响应 `data`：

```json
{
  "taskNo": "T...",
  "channel": "WECHAT",
  "billDate": "2026-08-04",
  "billFile": "./recon-bills/bill_WECHAT_2026-08-04.csv",
  "totalCount": 10,
  "matchedCount": 8,
  "diffCount": 2,
  "status": "COMPLETED",
  "finishedAt": "2026-08-04 23:05:00"
}
```

### 5.2 查询差异

`GET /api/recon/differences?status=OPEN`

`status` 可选：`OPEN` / `HANG` / `RESOLVED` / `CLOSED`

响应 `data` 为数组，字段含：`differenceNo / diffType（CHANNEL_ONLY|SYSTEM_ONLY|AMOUNT_MISMATCH）/ channelTransactionNo / paymentNo / channelAmount / systemAmount / status / operator / remark`。

### 5.3 差异处理（人工闭环）

状态机：`OPEN → HANG → RESOLVED → CLOSED`

```text
POST /api/recon/differences/{differenceNo}/hang
POST /api/recon/differences/{differenceNo}/resolve
POST /api/recon/differences/{differenceNo}/close
```

请求体（均可选）：

```json
{ "operator": "ops01", "remark": "已人工核对，用户已退款" }
```

- 每一步校验当前状态，乐观锁防并发，冲突返回 `5003`；
- 全程操作人留痕（D6：不做自动修复）。

---

## 6. 端到端演示流程

```text
1. POST /api/orders                                  创建订单 → orderNo（PENDING_PAYMENT）
2. POST /api/payments/initiate                      发起支付 → paymentNo + channelTransactionNo（PAYING）
3. POST /mock-channel/WECHAT/notify                 模拟支付成功 → PAY_SUCCESS
4. GET  /api/orders/{orderNo}                       订单 PAID
5. POST /api/refunds                                发起部分退款 → refundNo
6. POST /mock-channel/WECHAT/notify                 模拟退款成功 → REFUND_SUCCESS（bizNo=refundNo）
7. GET  /api/orders/{orderNo}                       订单 PARTIALLY_REFUNDED / REFUNDED
8. POST /api/recon/run                              执行日终对账
9. GET  /api/recon/differences                      查看差异 → hang / resolve / close
```

验证幂等/并发的小实验：
- 重复调用 `POST /api/orders`（同 `idempotentKey`）→ 返回同一订单；
- 重复推送同 `notifyId` 回调 → 只处理一次；
- 支付成功后调用取消 → 返回 `1002`；
- 退款金额超过剩余可退 → 返回 `3003`。

---

## 7. 主要错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 400 | 参数错误 |
| 500 | 系统繁忙 |
| 1001 | 订单不存在 |
| 1002 | 订单状态不允许该操作（如已支付取消） |
| 1003 | 订单已过期 |
| 1005 | 订单已支付 |
| 2001 | 支付单不存在 |
| 2004 | 支付流水状态不允许该操作 |
| 3002 | 退款单状态不允许该操作 |
| 3003 | 累计退款金额超过实付金额 |
| 3004 | 退款请求重复 |
| 4001 | 渠道调用失败 |
| 5001 | 对账任务已存在 |
| 5002 | 对账差异不存在 |
| 5003 | 差异状态不允许该操作 |
| 5004 | 对账账单异常 |

## 8. 启动步骤

1. MySQL 执行建表脚本：`src/main/resources/db/schema.sql`；
2. 修改 `application.yml`：数据库账号密码、Redis、RocketMQ NameServer；
3. 启动：`mvn spring-boot:run`（或 IDE 运行 `MiniPaymentApplication`）；
4. 打开 `http://localhost:8080` 调用上述接口。
