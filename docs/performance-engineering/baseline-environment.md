# Phase 1 — Baseline Environment

> 状态：已确认（硬件明细待补齐）

## 1. 正式 Baseline 拓扑

正式 Baseline 使用两台物理机器，压测机与被测系统分离。

```text
┌────────────────────────┐
│ Load Generator         │
│                        │
│ MacBook Air M4         │
│ JMeter                 │
│                        │
│ 只负责制造请求与记录   │
│ HTTP 侧压测结果        │
└───────────┬────────────┘
            │
            │ LAN
            ▼
┌──────────────────────────────┐
│ System Under Test (SUT)      │
│                              │
│ Windows                      │
│                              │
│ Spring Boot Instance × 1     │
│ MySQL                        │
│ Redis                        │
│ RocketMQ                     │
└──────────────────────────────┘
```

## 2. 环境职责

### MacBook Air M4 — Load Generator

只运行：

- JMeter；
- 必要的结果采集脚本。

正式 Baseline 期间不在 Mac 上运行：

- Spring Boot；
- MySQL；
- Redis；
- RocketMQ。

这样可以避免 JMeter 与被测系统争抢 CPU、内存和 IO，降低压测机对结果的污染。

### Windows — System Under Test

运行：

- 单实例 Spring Boot；
- MySQL；
- Redis；
- RocketMQ。

Phase 1 明确保持 **单个 Spring Boot 实例**。

多实例部署属于 Phase 4，不提前混入 Phase 1。

## 3. Smoke Test 与正式 Baseline 的区别

开发阶段允许在同一台机器上做 Smoke Test，用于验证：

- JMeter 脚本能否运行；
- Token / admission 数据是否正确；
- RocketMQ 是否能够发送和消费；
- SQL 是否正常；
- 指标采集是否可用。

同机 Smoke Test 的结果：

> **不得进入正式 Benchmark。**

只有 Mac 作为 Load Generator、Windows 作为 SUT 的两机测试结果，才允许进入正式 Baseline Report。

## 4. 网络要求

优先使用稳定局域网连接。

优先级：

```text
有线 LAN
>
稳定 Wi-Fi LAN
>
其他网络环境
```

如果正式测试使用 Wi-Fi，必须在结果中明确记录，因为网络抖动会直接影响 HTTP P95 / P99 / Max Latency。

## 5. 为什么 Phase 1 不继续拆分 MySQL / Redis / RocketMQ

Phase 1 的目标是建立当前“单机部署形态”的性能基线，而不是模拟生产集群。

因此暂时把：

```text
Spring Boot
MySQL
Redis
RocketMQ
```

共同视作一个 SUT。

如果 Phase 2 的 Profiling 证明某个中间件是主要瓶颈，再设计隔离实验，例如：

```text
MySQL 成为瓶颈
→ 将 MySQL 独立部署后复测

RocketMQ 成为瓶颈
→ 将 RocketMQ 独立部署后复测
```

这样才能形成有意义的 Before / After，而不是一开始就堆复杂部署。

## 6. 正式测试前必须补齐的硬件信息

### Load Generator

已知：

- Device: MacBook Air M4
- CPU: Apple M4

待正式执行时记录：

- CPU core；
- Memory；
- macOS version；
- JDK version；
- JMeter version。

### SUT

待记录：

- Windows version；
- CPU 型号与核心数；
- Memory；
- 磁盘类型；
- JDK version；
- MySQL version；
- Redis version；
- RocketMQ version。

这些信息属于测试结果的一部分，不允许省略。
