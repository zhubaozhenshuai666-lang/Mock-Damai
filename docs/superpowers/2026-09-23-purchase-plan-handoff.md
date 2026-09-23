# 预约计划模块交接记录

更新时间：2026-09-24
当前分支：`codex/remaining-workspace-features`
远端：`origin`（GitHub: `zhubaozhenshuai666-lang/smart-ticket-lite`）

## 交接目标

本记录起初用于预约计划模块的中途交接。后续工作区已扩展到观演人、异步抢票集成、演出搜索和艺人热榜；这些相关代码、文档、SQL 与测试现归入 `codex/remaining-workspace-features`。JDK 21 下已运行全量 Maven 测试；本机没有 Docker，Testcontainers 集成测试被跳过。没有运行压测。

本分支不应被推送或合并到 `main` / `master`；本记录里“提交到当前分支”的旧操作要求已被上述范围取代。

详细设计和原模块任务拆分见 [`2026-09-23-purchase-plan-module.md`](plans/2026-09-23-purchase-plan-module.md)。预约与抢票提交分离的业务约定见 [`0001-预约与抢票提交分离.md`](../adr/0001-预约与抢票提交分离.md)。

## 已确定的业务语义

- 预约是开售前填写的购票模板，不占库存、不锁价格、不保证抢到票。
- 开售前可以编辑场次、票档、数量和观演人；已完成方案被任何编辑后都回到 `DRAFT`，需要再次完成预约。
- 预约中的观演人数必须严格等于购票数量；提交抢票时以服务端已完成方案和观演人快照为准，不信任客户端重新传入的人数或身份列表。
- 完成预约和抢票提交是两个动作。未完成预约的用户仍可走普通抢票入口。
- 正式订单创建后，支付、取消和超时关闭只改变订单状态；预约计划不镜像订单状态。
- 异步请求结果不确定时进入 `RECONCILIATION_REQUIRED`；在安全对账前不能盲目重复提交。
- 消息架构决定不变：RocketMQ 交易命令、Kafka 领域事件；不做压测。

## 已提交的预约计划内容

- 场次增加独立开售开始/结束时间及后台校验、查询映射和 SQL 修复脚本。
- 预约状态及持久化改为 `DRAFT / READY / SUBMITTING / RECONCILIATION_REQUIRED / FAILED / ORDER_CREATED / CANCELLED / EXPIRED`，支持开售前编辑与版本化完成。
- 抢票请求从预约快照读取方案，预先绑定 `orderRequestId`；订单观演人快照和预约消费在异步消费者内做影响行数校验。
- 增加按场次开售窗口过期的扫描，以及预约/抢票请求对账服务和扫描任务。
- 收敛 Mapper 更新条件、订单请求 ID 匹配条件和 XML 时间比较语法。
- README 和 API 示例已调整为“先完成预约，开售后提交”，不再在提交请求中传观演人列表。
- 本次纳入了对应的预约服务、Mapper、过期扫描、异步消费者和预约对账测试。

## 已处理锚点：同步拒绝后的安全状态收敛

当前实现已区分确定同步拒绝与结果不确定两类异常：

`TicketPurchasePlanServiceImpl.submit()` 对 `AsyncOrderSubmissionRejectedException` 收敛到 `FAILED`；其他无法确认请求或库存副作用的异常仍进入 `RECONCILIATION_REQUIRED`。相应分支测试已加入 `TicketPurchasePlanServiceImplTest` 并通过聚焦测试。

已执行验证：JDK 21 下 `OrderServiceImplTest`、`TicketPurchasePlanServiceImplTest`、`PurchasePlanReconciliationServiceImplTest`、两类预约扫描任务、`AsyncCreateOrderConsumerTest`、`MapperSqlContractTest`、`AdminBusinessServiceImplTest`、`ShowServiceImplTest` 聚焦测试通过；异常消费者日志属于预期失败路径。`ArtistRankingServiceImplTest` 和 `ShowSearchServiceImplTest` 的聚焦测试也通过。代码提交后再次运行了 `git diff --check`。

2026-09-24 已补齐后续发现的问题：`publishSession()` 重新校验持久化的场次与开售窗口边界；移除只返回旧状态的 `/retry` 路由，失败预约仍通过新的幂等 Token 调用 `/submit`；预约请求预绑定和异常状态各自使用独立事务，异步下单事务回滚不再撤销对账状态；Redis `DUPLICATE` 按结果不确定处理；同一 Token 在请求行尚未落库时返回预绑定 ID 和 `SUBMITTING` 状态。相应服务、事务边界和控制器测试已补充。

### 后续验证建议

- 后续如修改此处状态转换，再检查服务测试覆盖：下单服务同步拒绝时写入 `FAILED`，调用已开始但库存/请求结果不确定时仍写入 `RECONCILIATION_REQUIRED`，预约状态更新行数错误不得静默忽略。
- 失败预约保留原有方案完成版本，能用新的幂等 token 通过 `POST /submit` 重试；对账状态不得盲目提交。
- 全量 `mvn test` 已执行；本模块不要求 JMeter。

## 工作区及提交边界

原始交接时的提交边界已过期。当前业务提交纳入了预约/观演人/抢票和搜索/排行榜相关实现及资料；未纳入 `AGENTS.md` 本地指令文件、仅有执行位差异的压测脚本和本机生成物。

`UserController.java` 与 `AuthControllerTest.java` 中重新开放的用户创建和按 ID 查询接口未纳入业务提交：它们没有认证拦截，查询响应包含手机号，且与预约功能无关，应单独审查后再决定如何处理。

本次提交不要推送到 `main` / `master`，也不要把剩余安全审查文件混入本功能提交。

## 验证边界

- JDK 21 全量 `mvn test`：405 个测试，0 失败，5 个 Testcontainers 集成测试因没有 Docker 被跳过。预约提交事务边界通过可记录提交/回滚顺序的聚焦测试验证；预约端点通过 MockMvc 验证 `/submit` 与已移除的 `/retry`。
- 缺失 `orderRequestId` 的异常旧数据继续保守告警，禁止按计划 ID 推断异步请求并自动重试；需要运行环境中的数据核查。
- 本次提交不包含上述两个待安全审查文件；交接状态以最新 Git 提交和实际代码为准。
