# API 调试索引

以下文件是 IntelliJ HTTP Client 等工具可直接执行的请求样例。默认地址为 `http://localhost:8081`，需要登录的请求必须先替换 token 变量。

## 当前链路

这些样例对应当前代码路径，优先按此顺序阅读或执行：

1. [`phase1-auth-api.http`](phase1-auth-api.http)：注册和登录。
2. [`show.http`](show.http)：演出列表和详情。
3. [`show-cache-api.http`](show-cache-api.http)：演出查询缓存。
4. [`async-order-submit-api.http`](async-order-submit-api.http)：异步下单入口和幂等 token。
5. [`phase5-redis-stock-api.http`](phase5-redis-stock-api.http)：Redis 预扣和库存治理。
6. [`phase2-stock-consistency-api.http`](phase2-stock-consistency-api.http)：Redis/MySQL 库存一致性。
7. [`phase5-reliable-message-api.http`](phase5-reliable-message-api.http)：本地消息表与可靠事件管理；异步创单的 Outbox 验证仅适用于切换到 Outbox 模式，默认 RocketMQ 模式走事务消息。
8. [`phase2-consumer-dlq-api.http`](phase2-consumer-dlq-api.http)：死信查询和人工处理。
9. [`phase4-rate-limit-api.http`](phase4-rate-limit-api.http)：限流和下单保护。
10. [`phase4-actuator-and-cost.http`](phase4-actuator-and-cost.http)：Actuator 和运营指标。
11. [`stock-preheat-api.http`](stock-preheat-api.http)：后台库存预热。

## 历史兼容与调试

这些文件仍保留，用于旧接口回归、权限验证或本地排障；它们不代表当前高并发购票主链路：

- [`order.http`](order.http)
- [`order-relation-validation-api.http`](order-relation-validation-api.http)
- [`order-status-api.http`](order-status-api.http)
- [`order-submit-guard-api.http`](order-submit-guard-api.http)
- [`order-timeout-api.http`](order-timeout-api.http)
- [`phase1-order-permission-api.http`](phase1-order-permission-api.http)
- [`phase1-payment-api.http`](phase1-payment-api.http)
- [`phase2-api.md`](phase2-api.md)
- [`phase2-full-flow.http`](phase2-full-flow.http)
- [`async-order-result-api.http`](async-order-result-api.http)
- [`async-order-consumer-api.http`](async-order-consumer-api.http)
- [`phase3-async-order-full-flow.http`](phase3-async-order-full-flow.http)
- [`phase4-idempotency-token-api.http`](phase4-idempotency-token-api.http)
- [`user.http`](user.http)

这些样例中出现的旧端口、请求体 `userId`、`POST /api/orders` 和直接支付入口属于兼容接口。新的压测和主链路验证必须使用带 Bearer token 的当前样例或 `/api/orders/async`。
