# Phase 1 — Baseline

> 状态：讨论中  
> 目标：在不修改核心业务逻辑的前提下，建立可复现、可解释的单实例性能基线。

## 1. Phase 1 要回答的问题

Phase 1 不追求“最高 TPS”这个单一数字，而是先回答：

1. 当前默认 RocketMQ 异步创单链路，在单实例下稳定能接收多少抢票请求？
2. 异步消费者每秒真正能创建多少正式订单？
3. Submit TPS 与 Order Creation TPS 之间什么时候开始出现差距？
4. MQ Lag 从哪个压力档位开始持续增长？
5. P95 / P99 在什么压力区间开始恶化？
6. 第一个资源瓶颈来自应用、Redis、RocketMQ、MySQL，还是压测机本身？
7. 在压力升高时，系统是“正确拒绝”还是出现 5xx、连接失败、卡死、库存不一致等系统性失败？
8. 压测结束后，异步链路是否能够最终收敛到一致状态？

## 2. 当前默认测试对象

Phase 1 默认只测试当前主链路：

```text
POST /api/orders/async
        ↓
Redis Lua 预扣
        ↓
RocketMQ Transaction Message
        ↓
Async Order Consumer
        ↓
MySQL 条件扣库存 + 创建正式订单
```

默认消息模式必须保持：

```text
publisher-mode = rocketmq
rocket-mq-transaction-message-enabled = true
```

Kafka、Redis Stream、Outbox 不进入第一轮 Baseline，避免同时比较多个变量。

## 3. 两类 Baseline

### Baseline A — Capacity Baseline

目的：测量系统本身的处理能力，而不是策略配置能挡掉多少请求。

要求：

- 保留真实 JWT、幂等、Redis Lua、RocketMQ、MySQL 创单链路；
- Waiting Room、Rate Limit、Risk Control、In-Flight 等保护策略不能成为主要瓶颈；
- 可以通过配置提高阈值或关闭非核心门控，但不能修改核心业务代码；
- 库存必须充足，避免售罄提前结束实验；
- 请求必须使用足够多的独立用户 / Token / requestId；
- 不在请求后同步轮询结果，避免把查询流量混入提交吞吐。

主要回答：

```text
入口最大稳定 Submit TPS 是多少？
异步 Order Creation TPS 是多少？
什么时候开始产生持续 MQ Lag？
第一个真实资源瓶颈在哪里？
```

### Baseline B — Flash-Sale Baseline

目的：保留实际抢票保护策略，观察高峰流量下系统如何削峰和拒绝。

要求：

- Flash-sale profile；
- Waiting Room；
- Rate Limit；
- Risk Control；
- Activity Isolation；
- In-Flight Control；
- 少量库存 + 大量用户请求。

这一组不以“所有请求成功”为目标。

要区分：

```text
业务拒绝
- 限流
- 无资格
- 重复提交
- 售罄
- 等待室拒绝

系统失败
- HTTP 5xx
- Socket / Connection Error
- Timeout
- MQ 发布失败
- 请求长期卡在 QUEUED / PROCESSING
- 超卖 / 库存不一致
```

## 4. 为什么必须拆成 A / B

如果只打开全部保护策略压测，入口可能在很低的 QPS 就大量拒绝请求。

这时看到的：

```text
高 Throughput
低 DB CPU
MQ Lag = 0
```

不代表系统创单能力很强，也可能只是大量请求根本没有进入核心链路。

反过来，如果为了跑分把所有保护全部删除，又无法说明真实抢票场景下系统行为。

因此 Phase 1 用两条基线分别回答：

| Baseline | 回答的问题 |
| --- | --- |
| Capacity Baseline | 核心交易链路能处理多少 |
| Flash-Sale Baseline | 保护策略面对洪峰时是否正确工作 |

## 5. 固定变量

正式 Baseline 开始后，以下变量必须记录且单轮测试中不能随意变化：

### 代码

- Git branch
- Git commit SHA
- Java version
- Spring Boot version

### 应用配置

- Spring Profiles
- RocketMQ producer / consumer 参数
- Consumer 线程数
- Batch Consumer 开关
- HikariCP 连接池
- Stock Bucket 数量
- Waiting Room
- Rate Limit
- Risk Control
- Backpressure
- In-Flight Control

### 数据

- 用户数量
- Ticket Category
- 初始库存
- 每单数量
- Bucket 数量

### 环境

- OS
- CPU
- Memory
- JDK
- MySQL version
- Redis version
- RocketMQ version
- JMeter version
- JMeter 是否与服务端同机

## 6. 需要采集的指标

### 6.1 HTTP / JMeter

必须记录：

- Requests / sec
- Submit TPS
- Error %
- P50
- P95
- P99
- Max Latency

### 6.2 Async Order

必须单独计算：

- Order Creation TPS
- SUCCESS / FAILED / COMPENSATED
- QUEUED 数量
- PROCESSING 数量
- 从提交到正式订单创建的异步完成延迟

> HTTP 200 / code=0 只代表请求被系统接受，不代表订单已经创建。

### 6.3 RocketMQ

必须观察：

- Producer Send TPS
- Consumer TPS
- Consumer Lag / Accumulation
- Retry
- DLQ
- 压测停止后 Lag 回落到 0 所需时间

### 6.4 Redis

至少记录：

- CPU
- ops/sec
- connected_clients
- keyspace hits / misses
- Lua 相关耗时（若当前可采集）
- 库存最终一致性

### 6.5 MySQL

至少记录：

- CPU
- Connections
- Threads_running
- QPS / TPS
- Slow Query
- Row Lock / Lock Wait
- HikariCP active / pending

### 6.6 JVM / Application

至少记录：

- Process CPU
- Heap Used
- GC Count / Pause
- Live Threads
- HTTP Thread
- 关键业务 Counter
- In-Flight Request

## 7. 压力模型

第一版暂定使用阶梯式压力，而不是一上来冲极限。

正式数值在测试环境确认后锁定。

示意：

| Stage | Target QPS | Duration | Purpose |
| --- | ---: | ---: | --- |
| Warm-up | 低 | 60–120s | JVM / 连接池 / 缓存预热 |
| S1 | 待定 | 180s | 低压力稳定性 |
| S2 | 待定 | 180s | 中低压力 |
| S3 | 待定 | 180s | 中压力 |
| S4 | 待定 | 180s | 接近容量边界 |
| S5 | 待定 | 180s | 超过稳定容量，观察退化 |

每个 Stage 之间必须重置会影响下一轮结果的业务数据，避免库存、请求状态、Token 和缓存状态污染后续实验。

## 8. 稳定吞吐的判定

不能简单把“JMeter 打出的最大 TPS”定义为系统吞吐。

暂定只有同时满足以下条件，才叫 Stable Throughput：

- 无不可解释 HTTP 5xx / 连接错误；
- P99 没有持续发散；
- MQ Lag 不持续单调增长；
- 压测停止后积压能够在合理时间内清空；
- ticket_order_request 最终能够收敛；
- 无超卖；
- Redis / MySQL 库存最终一致；
- JVM 不发生持续 Full GC / OOM；
- 数据库连接池没有长期 pending；
- 重复执行同一档位，结果波动在可接受范围内。

## 9. 每个档位至少重复 3 次

单次跑出来的结果不能直接进入最终 Benchmark。

每个候选稳定档位至少重复 3 次，记录：

- Median；
- Min / Max；
- 波动幅度；
- 是否存在异常 Run。

只有结果可重复，才进入 Baseline Report。

## 10. Phase 1 明确不做

本阶段先不做：

- 为提升数字而修改核心业务代码；
- Consumer Batch 优化；
- MySQL Batch Insert；
- Bucket 数量调优；
- JVM 参数专项调优；
- 多实例部署；
- Redis Cluster；
- MySQL 主从；
- 故障注入。

这些内容必须建立在 Baseline 之后，否则没有可信的 Before / After。

## 11. 当前待确认事项

在正式执行前，需要把下面几项讨论并锁定：

1. Baseline 的实际运行机器；
2. JMeter 是否与服务端同机；
3. Capacity Baseline 是否允许临时提高/关闭 Waiting Room、Rate Limit 等策略；
4. 每单固定购买 1 张还是 2 张；
5. 初始库存和用户数据规模；
6. 第一版压力阶梯；
7. Baseline 是否先只测单个热点票档；
8. Phase 1 是否采集 Prometheus 级指标，还是先使用 Actuator + 系统命令 + MySQL/Redis/RocketMQ 原生指标。

## 12. 输出物

Phase 1 完成后，本目录至少应新增：

```text
phase-1-baseline.md
baseline-environment.md
baseline-scenarios.md
baseline-results.md
raw-results/
```

其中 `raw-results/` 是否直接提交仓库，需要根据文件大小决定；大型 JMeter HTML / JTL 仍应避免直接提交。
