# SmartTicket Lite 第二阶段接口文档

> 历史阶段说明，保留早期接口样例供对照。本文不保证所有请求可直接执行；当前可执行请求以 [API 调试索引](README.md) 的“当前链路”为准。

基础地址：`http://localhost:8081`  
统一响应：`{"code":200,"message":"success","data":...}`；业务异常通常返回 `code=400`。

## 用户接口

### 查询当前用户

- URL：`/api/users/me`
- Method：`GET`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：无
- 请求 JSON：无
- 正常场景：只返回当前登录用户的资料
- 异常场景：未登录或 token 无效

```json
{"code":200,"message":"success","data":{"id":1,"username":"zewbby","phone":"13800000001","status":"NORMAL","roleCode":"USER"}}
```

### 注册用户

- URL：`/api/auth/register`
- Method：`POST`
- 请求参数：`username`、`phone`、`password`
- 请求 JSON：见下方
- 正常场景：创建购票用户
- 异常场景：用户名、手机号或密码校验失败，或手机号已存在

```json
{"username":"phase2tester","phone":"13900000001","password":"Test123456"}
```

```json
{"code":200,"message":"success","data":{"id":2,"username":"phase2tester","phone":"13900000001","status":"NORMAL","roleCode":"USER"}}
```

## 演出查询接口

### 查询演出列表

- URL：`/api/shows`
- Method：`GET`
- 请求参数：无
- 请求 JSON：无
- 正常场景：浏览可选择的演出
- 异常场景：当前实现通常返回空数组

```json
{"code":200,"message":"success","data":[{"id":1,"title":"SmartTicket 测试演唱会","artist":"测试乐队","city":"上海","venueName":"梅赛德斯奔驰文化中心"}]}
```

### 查询演出详情

- URL：`/api/shows/{id}`
- Method：`GET`
- 请求参数：路径参数 `id`，演出 ID
- 请求 JSON：无
- 正常场景：返回场馆及场次信息；相同查询可命中 Redis 缓存
- 异常场景：演出不存在，返回 `演出不存在`

```json
{"code":200,"message":"success","data":{"id":1,"title":"SmartTicket 测试演唱会","artist":"测试乐队","city":"上海","venueName":"梅赛德斯奔驰文化中心","sessions":[]}}
```

### 查询场次

- URL：`/api/shows/{id}/sessions`
- Method：`GET`
- 请求参数：路径参数 `id`，演出 ID
- 请求 JSON：无
- 正常场景：查看演出的可购票场次；可命中 Redis 缓存
- 异常场景：演出不存在

```json
{"code":200,"message":"success","data":[{"id":1,"showId":1,"venueName":"梅赛德斯奔驰文化中心","city":"上海","startTime":"2026-06-20T19:30:00","ticketCategories":[]}]}
```

### 查询票档

- URL：`/api/sessions/{sessionId}/ticket-categories`
- Method：`GET`
- 请求参数：路径参数 `sessionId`，场次 ID
- 请求 JSON：无
- 正常场景：查看票价与展示用 `availableStock`；结果缓存于 Redis
- 异常场景：不存在的场次当前可能返回空数组

```json
{"code":200,"message":"success","data":[{"id":2,"sessionId":1,"name":"内场票","price":880,"totalStock":10,"availableStock":10}]}
```

> 订单操作后当前不会主动清理演出查询缓存。核验库存变化前，请删除 `session:ticket-categories:{sessionId}` 缓存，或直接查询 MySQL。

## 预约计划与开售抢票

预约计划是用户在开售前准备的填单方案，不是订单、库存预留或价格承诺。开售前可以修改场次、票档、购票数量和观演人；任何修改都会使已完成版本失效，必须重新完成预约。观演人数必须严格等于购票数量。完成预约只保存方案和观演人身份快照，不创建抢票请求、不扣减或占用库存。

完成预约和提交抢票是两个独立操作：`POST /api/purchase-plans/{planId}/complete` 只将当前方案标记为 `READY`；到达场次开售时间后，用户还需要显式调用 `POST /api/purchase-plans/{planId}/submit`。提交时服务端从预约计划中读取已完成的场次、票档、数量和观演人快照，客户端不得提交 `audienceIds`、场次、票档或数量。抢票仍会校验开售窗口、票档和实时库存，预约不保证抢到票或价格不变。

开售前先用 `/api/purchase-plans` 创建计划，再通过 `/spec` 设置场次、票档和数量；可通过 `/audiences` 设置观演人。示例：

```http
POST /api/purchase-plans/{planId}/complete
Authorization: Bearer <token>
Content-Type: application/json

{"version":2}
```

开售后提交示例：

```http
POST /api/purchase-plans/{planId}/submit
Authorization: Bearer <token>
Content-Type: application/json

{"version":3,"idempotencyToken":"<one-time-token>"}
```

`submit` 返回异步请求结果及 `requestId`，通过 `GET /api/order-requests/{requestId}` 查询创单状态。未在开售前完成预约、或预约已经过期时，用户按普通抢票流程调用 `POST /api/orders/async`，不会获得预约自动填单。

同一幂等 Token 重复提交时，若异步请求尚未落库，`submit` 会返回预绑定的 `requestId` 和 `SUBMITTING` 状态；此时先查询预约状态，稍后再查询订单请求，不要换 Token 创建第二次抢票。

如果提交期间发生不确定错误，预约计划可能进入 `RECONCILIATION_REQUIRED`。这表示系统正在核对预约计划与异步请求的关联，不代表可以安全地重新创建请求；不要换幂等 Token 盲目重试。先查询 `GET /api/purchase-plans/{planId}` 和对应的订单请求，等待服务端对账后再操作。

只有预约状态已明确为 `FAILED` 时，才可以在开售窗口内获取新的幂等 Token，再调用同一个 `/submit` 接口重试；不存在单独的 `/retry` 接口。

预约计划可见状态为 `DRAFT`、`READY`、`SUBMITTING`、`RECONCILIATION_REQUIRED`、`FAILED`、`ORDER_CREATED`、`CANCELLED` 或 `EXPIRED`。正式订单创建后预约计划保持 `ORDER_CREATED`；之后的支付、取消和超时关闭由正式订单自身管理。

## 订单接口

### 创建订单

> 阶段 4B 后，`POST /api/orders` 已废弃，仅保留为本地调试 / 历史兼容入口。高并发购票主链路只使用 `POST /api/orders/async`。

- URL：`/api/orders`
- Method：`POST`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：`showId`、`sessionId`、`ticketCategoryId`、`quantity`、`idempotencyToken`
- 请求 JSON：见下方
- 正常场景：本地调试创建待支付订单并锁定库存；默认超时关闭消息由 RocketMQ 发布
- 异常场景：未登录、演出场次票档关系不匹配、票档/库存不存在、库存不足、并发重复提交或消息发布失败

```json
{"showId":1,"sessionId":1,"ticketCategoryId":2,"quantity":1,"idempotencyToken":"token-from-/api/orders/idempotency-token"}
```

```json
{"code":200,"message":"success","data":{"id":30,"orderNo":"ST...","status":"PENDING_PAYMENT","expireTime":"2026-05-27T18:01:00","payTime":null}}
```

创建成功时库存变化：`available_stock - quantity`，`locked_stock + quantity`。
当前 `OrderConstant.ORDER_TIMEOUT_MINUTES` 为 `10` 分钟；支付或主动取消测试应在订单自动关闭前完成。

### 高并发异步下单

- URL：`/api/orders/async`
- Method：`POST`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：`showId`、`sessionId`、`ticketCategoryId`、`quantity`、`idempotencyToken`
- 正常场景：返回 `requestId`，后续通过 `/api/order-requests/{requestId}` 查询订单创建结果
- 主链路：限流、soldout 快速失败、Redis 预扣、RocketMQ 事务消息、消费者创建订单

### 查询订单

- URL：`/api/orders/{id}`
- Method：`GET`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：路径参数 `id`，订单 ID
- 请求 JSON：无
- 正常场景：查看当前登录用户自己的订单状态及时间信息
- 异常场景：订单不存在或不属于当前登录用户

```json
{"code":200,"message":"success","data":{"id":30,"status":"PENDING_PAYMENT","expireTime":"2026-05-27T18:01:00","payTime":null,"cancelTime":null,"closeTime":null}}
```

### 查询用户订单列表

- URL：`/api/users/me/orders`
- Method：`GET`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：无
- 请求 JSON：无
- 正常场景：查看当前登录用户的订单历史
- 异常场景：没有订单时返回空数组

```json
{"code":200,"message":"success","data":[{"id":30,"status":"PENDING_PAYMENT"}]}
```

旧路径 `/api/users/{userId}/orders` 暂时保留兼容，但会忽略路径中的 `userId`，只返回当前 token 用户的订单。

### 创建支付单

- URL：`/api/payments/create`
- Method：`POST`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：`orderId`、`channel`
- 请求 JSON：见下方
- 正常场景：为当前登录用户自己的 `PENDING_PAYMENT` 订单创建 `payment_order`
- 异常场景：订单不存在、不属于当前登录用户、已支付、已取消、已关闭或已过期

```json
{"orderId":30,"channel":"MOCK"}
```

```json
{"code":200,"message":"success","data":{"paymentNo":"PAY...","orderId":30,"amount":880.00,"channel":"MOCK","status":"INIT"}}
```

### mock-pay 支付回调

- URL：`/api/payments/mock-pay`
- Method：`POST`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：`paymentNo`、`success`、`timestamp`、`signature`，建议同时提供 `nonce` 防重放
- 请求 JSON：见下方
- 正常场景：当前登录用户自己的支付单支付成功，订单 `PENDING_PAYMENT -> PAID`
- 异常场景：支付单不存在、不属于当前用户、支付单已关闭/失败、订单已取消/关闭

```json
{"paymentNo":"PAY...","success":true,"timestamp":1770000000000,"nonce":"唯一随机值","signature":"按支付回调密钥计算的HMAC-SHA256十六进制签名"}
```

请求时须把示例时间戳换成当前毫秒时间戳并重新计算签名。签名原文按 `paymentNo`、`success`、`timestamp`、`nonce` 顺序以换行符连接，密钥来自 `smart-ticket.payment.mock-callback-secret`；可执行示例见 [`phase1-payment-api.http`](phase1-payment-api.http)。

支付成功时库存变化：`locked_stock - quantity`，`sold_stock + quantity`。再次回调须使用新的 `nonce` 和对应签名；订单已支付时不会重复流转库存，复用旧 `nonce` 会被防重放校验拒绝。

旧接口 `/api/orders/{id}/pay` 已废弃，会提示“请先创建支付单后再支付”，不能绕过 `payment_order` 直接修改订单。

### 主动取消订单

- URL：`/api/orders/{id}/cancel`
- Method：`POST`
- 请求头：`Authorization: Bearer <token>`
- 请求参数：路径参数 `id`，订单 ID
- 请求 JSON：无
- 正常场景：仅当前登录用户自己的 `PENDING_PAYMENT` 订单可以取消
- 异常场景：订单不存在、不属于当前登录用户、重复取消，或订单已经支付/关闭

```json
{"code":200,"message":"success","data":{"id":31,"status":"CANCELLED","cancelTime":"2026-05-27T18:02:00","cancelReason":"用户主动取消"}}
```

取消成功时库存变化：`available_stock + quantity`，`locked_stock - quantity`。

## 自动超时关闭

超时关闭没有对外 Controller 接口。创建订单后发送 RocketMQ 延迟消息；延迟级别由 `smart-ticket.order-timeout.rocket-mq-delay-level` 控制，到期后由消费者调用关闭逻辑，定时任务继续作为兜底扫描。

```json
{"code":200,"message":"success","data":{"id":32,"status":"CLOSED","closeTime":"2026-05-27T18:05:00","cancelReason":"订单超时未支付关闭"}}
```

超时关闭库存变化：`available_stock + quantity`，`locked_stock - quantity`。
