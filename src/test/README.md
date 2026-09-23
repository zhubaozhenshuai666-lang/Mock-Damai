# 测试目录说明

测试源码按被测边界组织，文件名统一使用 `目标类型 + Test.java`。本目录当前没有确认可安全删除的重复或空测试；小型测试通常保护配置、消息适配器或安全边界，不按行数删减。

## 分类

| 目录 | 覆盖范围 | 运行依赖 |
| --- | --- | --- |
| `auth/` | JWT、登录失败、密码策略、用户上下文和 Token 黑名单 | 无外部服务 |
| `config/` | Actuator、MQ 属性和异步下单护栏 | 无外部服务 |
| `controller/` | 用户、支付和后台管理接口 | Mockito/MockMvc |
| `idempotency/` | 一次性幂等 token | Mockito |
| `mapper/` | SQL、Mapper XML、脚本和文档契约 | 从仓库根目录运行 |
| `mq/` | Kafka、RocketMQ 消费者、生产者和批量调度 | 大多为 Mockito |
| `ratelimit/` | 客户端 IP 和限流令牌桶 | 无外部服务 |
| `service/`、`service/impl/` | 缓存、库存、订单、支付和治理服务 | 大多为 Mockito |
| `integration/` | Spring Boot、MySQL、Redis 和消息链路集成 | Docker；无 Docker 时按配置跳过 |
| `task/` | 本地消息发布定时任务 | Mockito |

## 测试资源

- `resources/application-test.yml`：`@ActiveProfiles("test")` 使用的测试配置。
- `resources/mockito-extensions/org.mockito.plugins.MockMaker`：Mockito mock maker 配置，不能删除。
- `integration/BaseIntegrationTest.java`：集成测试基类，直接加载 `docs/sql/schema.sql` 和 `docs/sql/data.sql`。

## 运行

从仓库根目录执行：

```bash
mvn test
```

集成测试需要 Docker，并会启动 MySQL 和 Redis 容器；只验证纯单元测试时，可以按类或包执行 Maven Surefire 过滤。不要从其他工作目录运行依赖 `Path.of(...)` 的契约测试。
