# 预约计划模块实施计划

> **For agentic workers:** 本计划在当前工作区内按任务顺序执行；每个任务都先写失败测试、确认失败原因，再写最小实现。用户要求整个预约模块完成后统一提交一个中文 commit，因此任务内不单独提交，最后统一精确暂存预约模块文件。

**Goal:** 将预约计划实现为开售前可编辑、版本化确认的填单模板，并在开售后以服务端快照安全触发异步抢票，补齐场次开售窗口、订单观演人快照、异常对账和文档契约。

**Architecture:** `performance_session` 持有实时 `sale_start_time/sale_end_time`，预约计划只保存配置和完成版本，不占库存。完成预约只写入预约快照；抢票提交预先生成 `orderRequestId`，使用预约快照创建异步请求；RocketMQ/Kafka 消费者在同一数据库事务内创建订单、复制观演人快照、消耗预约计划并标记请求成功。请求或计划关联异常进入 `RECONCILIATION_REQUIRED`，由对账任务按请求状态恢复，禁止盲目重试。

**Tech Stack:** Java 21、Spring Boot 3.5、MyBatis XML、MySQL 8、Redis Lua、RocketMQ 交易命令、Kafka Outbox、JUnit 5、Mockito、AssertJ。

**Spec:** `CONTEXT.md`、`docs/adr/0001-预约与抢票提交分离.md`。

## Global Constraints

- 预约计划不是库存预留、价格承诺或正式订单；完成预约不调用 MQ、不扣 Redis/MySQL 库存。
- 开售前允许修改场次、票档、数量和观演人；任一修改都会使已完成版本回到未完成状态。
- 当前方案的观演人数必须始终等于购票数量；完成预约时冻结观演人身份快照。
- 同一用户同一场次只允许一份有效预约；终态计划释放唯一占位后才能创建新计划。
- 抢票提交只接受预约版本、幂等 token 和准入信息；场次、票档、数量、观演人全部由服务端读取。
- 预约不保证库存或价格；抢票提交和消费者处理必须重新校验实时关系、票档状态和开售窗口。
- 正式订单生成后，支付、取消、超时关闭只由正式订单负责；预约计划不镜像 `PAID/CANCELLED`。
- 所有状态更新使用版本/旧状态/时间条件；影响行数不符合预期必须抛错或进入对账，不得静默忽略。
- 不执行 JMeter 或压力测试；完成模块前不修改搜索、排行榜和压测脚本等无关功能。
- 手工编辑使用 `apply_patch`；最终提交信息必须使用中文；不得覆盖工作区中已有的无关用户改动。

## 文件边界

预约模块允许修改的文件范围：场次开售窗口实体、DTO、VO、Mapper、后台场次服务；预约计划实体、状态、DTO、VO、服务、控制器、Mapper、任务和配置；异步订单请求 ID 传递、预约消费者事务、订单/支付中错误的预约状态镜像；预约相关 SQL、API 文档、测试和本计划文件。搜索、排行榜、JMeter、用户资料和 RabbitMQ 清理改动不属于本次提交。

### Task 1: 建立场次开售窗口

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/domain/entity/PerformanceSession.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/AdminCreateSessionRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/AdminUpdateSessionRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/vo/SessionVO.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/ShowMapper.java`
- Modify: `src/main/resources/mapper/ShowMapper.xml`
- Modify: `src/main/java/com/zewbby/smartticket/service/impl/AdminBusinessServiceImpl.java`
- Modify: `docs/sql/schema.sql`
- Modify: `docs/sql/local-schema-repair.sql`
- Modify: `docs/sql/data.sql`
- Test: `src/test/java/com/zewbby/smartticket/service/impl/AdminBusinessServiceImplTest.java`
- Test: `src/test/java/com/zewbby/smartticket/service/impl/ShowServiceImplTest.java`
- Test: `src/test/java/com/zewbby/smartticket/mapper/MapperSqlContractTest.java`

**Interfaces:**
- `PerformanceSession.saleStartTime` and `saleEndTime` are `LocalDateTime`.
- Admin create/update requests expose both fields. New/updated sessions require both values, `saleStart < saleEnd <= session.startTime`.
- Legacy rows may be migrated as nullable; a null window is never eligible for appointment completion or order submission.
- Public/admin session selects, insert and update map both fields. Public session responses expose them; public queries do not hide future sessions before sale start.

- [ ] Write tests for invalid window order, sale end after performance start, missing window on publishing, and public response field mapping.
- [ ] Run `mvn -Dtest=AdminBusinessServiceImplTest,ShowServiceImplTest,MapperSqlContractTest test` and verify the new assertions fail because fields and validation are absent.
- [ ] Add fields and SQL mappings; validate window in `createSession`, `updateSession`, and `publishSession`; reject metadata changes once `saleStartTime` has passed.
- [ ] Add canonical schema columns/index and idempotent repair `ADD COLUMN` statements; update fixture times to future windows.
- [ ] Rerun the focused tests and inspect `git diff --check`.

### Task 2: Replace the reservation state model and persistence contract

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/enums/PurchasePlanStatusEnum.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/entity/TicketPurchasePlan.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/vo/TicketPurchasePlanVO.java`
- Create: `src/main/java/com/zewbby/smartticket/domain/dto/CompletePurchasePlanRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/ConfirmPurchasePlanRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/SubmitPurchasePlanRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/TicketPurchasePlanMapper.java`
- Modify: `src/main/resources/mapper/TicketPurchasePlanMapper.xml`
- Modify: `docs/sql/schema.sql`
- Modify: `docs/sql/local-schema-repair.sql`
- Test: `src/test/java/com/zewbby/smartticket/service/impl/TicketPurchasePlanServiceImplTest.java`
- Test: `src/test/java/com/zewbby/smartticket/mapper/MapperSqlContractTest.java`

**Interfaces:**
- State codes become `DRAFT`, `READY`, `SUBMITTING`, `FAILED`, `RECONCILIATION_REQUIRED`, `ORDER_CREATED`, `CANCELLED`, `EXPIRED`; remove `PAID` as a reservation state.
- Add `activePlanKey`, `completedVersion`, `completedAt`, and `orderRequestId` persistence fields. `activePlanKey` is nullable and unique; it is populated for non-terminal plans as `userId + ':' + sessionId` and cleared on cancel, expire, or order creation.
- Mapper methods must include exact status/version/time predicates: `updateSpec`, `updateAudiences`, `complete`, `beginSubmitting`, `markFailed`, `markReconciliationRequired`, `markOrderCreated`, `cancel`, and expiry scans.
- `beginSubmitting` receives a server-generated `orderRequestId` and writes it in the same conditional update; no post-send binding is required for new requests.

- [ ] Add failing enum/state and mapper contract tests for READY completion, exact audience count, active key, and SQL time predicates.
- [ ] Run the focused tests and confirm failures identify the old `SPEC_SELECTED`/`PAID` behavior.
- [ ] Implement the entity/VO/DDL/mapper changes with backward-compatible aliases only where existing callers need compilation.
- [ ] Rerun focused tests; verify no SQL silently accepts a stale version or a sale-window boundary.

### Task 3: Implement editable reservation and completion API

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/service/TicketPurchasePlanService.java`
- Modify: `src/main/java/com/zewbby/smartticket/service/impl/TicketPurchasePlanServiceImpl.java`
- Modify: `src/main/java/com/zewbby/smartticket/controller/TicketPurchasePlanController.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/CreatePurchasePlanRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/UpdatePurchasePlanSpecRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/UpdatePurchasePlanAudienceRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/vo/TicketPurchasePlanVO.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/TicketPurchasePlanAudienceMapper.java`
- Modify: `src/main/resources/mapper/TicketPurchasePlanAudienceMapper.xml`
- Test: `src/test/java/com/zewbby/smartticket/service/impl/TicketPurchasePlanServiceImplTest.java`
- Test: `src/test/java/com/zewbby/smartticket/controller/TicketPurchasePlanControllerTest.java` (create if absent)

**Interfaces:**
- Add `POST /api/purchase-plans/{planId}/complete` with `CompletePurchasePlanRequest(version)`; retain `confirm-spec` only as a deprecated compatibility alias that calls the same completion method, never as a separate state transition.
- `updateSpec` is allowed only before `saleStartTime` for `DRAFT` or `READY`; it updates session/票档/quantity, sets status `DRAFT`, clears completion metadata, and preserves the invariant check for selected audience count at completion.
- `updateAudiences` is allowed only before `saleStartTime` for `DRAFT` or `READY`; it requires distinct active user-owned audience IDs exactly equal to quantity, replaces selected snapshots, sets status `DRAFT`, and increments version.
- `complete` requires a published show/session/category with a non-null current sale window, `now < saleStartTime`, an exact selected snapshot count equal to quantity, and a matching version. It atomically sets `READY`, increments version, and records `completedVersion/completedAt`; it does not call `OrderService`.
- Creation stores default snapshots and initial selected snapshots when supplied; it does not reserve inventory. Duplicate active plan keys are translated to a business error returning the existing-plan guidance.
- `getOwnedPlan` must not use fixed `now + 30min` expiry. It should expose `READY` after sale start as read-only and let submit/expiry logic decide eligibility.

- [ ] Write tests first for READY→DRAFT on every edit, exact count mismatch, invalid/foreign audiences, sale-start boundary, completion without MQ, duplicate active plan, and stale version.
- [ ] Run the focused service/controller tests and confirm red failures.
- [ ] Implement minimal service/mapper/controller behavior and map selected snapshot IDs without accepting client audience IDs during submit.
- [ ] Rerun tests and check transaction rollback assumptions for replacing snapshots.

### Task 4: Add expiry and sale-window eligibility reconciliation

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/config/PurchasePlanProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.example.yml`
- Modify: `src/main/java/com/zewbby/smartticket/task/PurchasePlanExpiryScanTask.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/TicketPurchasePlanMapper.java`
- Modify: `src/main/resources/mapper/TicketPurchasePlanMapper.xml`
- Create: `src/test/java/com/zewbby/smartticket/task/PurchasePlanExpiryScanTaskTest.java`

**Interfaces:**
- Remove fixed creation-time expiration as the eligibility rule. Keep scan batch/delay settings only.
- Scan `DRAFT` plans whose selected session sale start has passed and mark them `EXPIRED`; scan `READY` plans after sale end as `EXPIRED`. Never expire `SUBMITTING`, `FAILED`, `RECONCILIATION_REQUIRED`, `ORDER_CREATED`, or `CANCELLED` automatically.
- Submit/complete/edit perform the same time checks synchronously; the task is cleanup and user-visible status convergence, not the boundary authority.

- [ ] Write task tests for before/after sale start, READY after sale end, disabled feature, batch size, and terminal-state exclusion.
- [ ] Run them to observe failure against the old `expire_time`-only SQL.
- [ ] Implement join-based scan SQL and task logging; clear `activePlanKey` when a plan becomes terminal.
- [ ] Rerun focused tests and mapper SQL assertions.

### Task 5: Split completion from real抢票提交 and pre-bind request IDs

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/SubmitPurchasePlanRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/domain/dto/CreateOrderRequest.java`
- Modify: `src/main/java/com/zewbby/smartticket/service/impl/TicketPurchasePlanServiceImpl.java`
- Modify: `src/main/java/com/zewbby/smartticket/service/impl/OrderServiceImpl.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/OrderRequestMapper.java`
- Modify: `src/main/resources/mapper/OrderRequestMapper.xml`
- Modify: `src/main/java/com/zewbby/smartticket/controller/TicketPurchasePlanController.java`
- Test: `src/test/java/com/zewbby/smartticket/service/impl/TicketPurchasePlanServiceImplTest.java`
- Test: `src/test/java/com/zewbby/smartticket/service/impl/OrderServiceImplTest.java`

**Interfaces:**
- `SubmitPurchasePlanRequest` contains only `version`, `idempotencyToken`, and optional `admissionToken`; `audienceIds`, session, category, and quantity are not accepted as authoritative inputs.
- `TicketPurchasePlanService.submit` permits only `READY` for the first attempt and `FAILED` for retry, requires `saleStartTime <= now < saleEndTime`, and reads selected snapshots from the plan. It generates `orderRequestId` before the conditional `beginSubmitting` update.
- `CreateOrderRequest` gains an internal `requestId` (`@JsonIgnore`); `OrderServiceImpl` uses it when present, otherwise preserves the old deterministic generator for ordinary requests.
- On a synchronous failure after `beginSubmitting`, the plan becomes `RECONCILIATION_REQUIRED`, never directly `FAILED`. Same idempotency token while `SUBMITTING` returns the existing request result; other tokens are rejected until reconciliation.
- Ordinary `/api/orders/async` remains available, but production order submission must use a real-time sale-window validator when the session has a configured window; legacy null-window data is rejected for appointment submission.

- [ ] Write failing tests for pre-sale rejection, in-window acceptance, post-sale rejection, server-side snapshot use, no audience rewrite, deterministic retry response, and bind/send failure entering reconciliation.
- [ ] Run focused tests and confirm old DTO/service behavior fails them.
- [ ] Implement request ID pre-binding and real-time window checks without changing RocketMQ/Kafka selection.
- [ ] Rerun order and plan tests; assert the generated message carries `purchasePlanId` and the pre-bound request ID.

### Task 6: Make consumer success and failure transitions transactional

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/mq/AsyncCreateOrderConsumer.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/TicketOrderAudienceMapper.java`
- Modify: `src/main/resources/mapper/TicketOrderAudienceMapper.xml`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/TicketPurchasePlanMapper.java`
- Modify: `src/main/resources/mapper/TicketPurchasePlanMapper.xml`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/OrderRequestMapper.java`
- Modify: `src/main/resources/mapper/OrderRequestMapper.xml`
- Test: `src/test/java/com/zewbby/smartticket/mq/AsyncCreateOrderConsumerTest.java`

**Interfaces:**
- For plan requests, one transaction must create `ticket_order`, insert exactly `quantity` order audience snapshots, transition plan `SUBMITTING/RECONCILIATION_REQUIRED → ORDER_CREATED`, and transition request `PROCESSING → SUCCESS`. Any row-count mismatch throws a retryable exception and rolls back.
- `completePurchasePlan` must be idempotent: if the order audience rows already exist with the same order, do not duplicate them; if rows are partial or disagree, throw a data-integrity exception.
- Batch flow must complete each plan before marking its request success, or otherwise keep all operations in the same transaction with explicit row-count checks. It must not leave `SUCCESS` requests with an unconsumed plan.
- Consumer failure marks the plan `FAILED` only after the request is safely compensated (`COMPENSATED`) or had no Redis deduction; otherwise it remains reconcilable.
- Add a real-time sale-window recheck for plan messages before MySQL stock deduction; ordinary requests retain existing relation checks.

- [ ] Write tests for exact insert count, zero-row plan transition, duplicate delivery, partial snapshot, batch path, and compensation-before-plan-failure.
- [ ] Run consumer tests to verify the old ignored return values and ordering fail.
- [ ] Implement idempotent snapshot insertion/verification and transactional state transitions.
- [ ] Rerun all consumer tests and inspect transaction-related logs/assertions.

### Task 7: Add plan/request reconciliation and remove order-state mirroring

**Files:**
- Modify: `src/main/java/com/zewbby/smartticket/enums/PurchasePlanStatusEnum.java`
- Modify: `src/main/java/com/zewbby/smartticket/mapper/OrderRequestMapper.java`
- Modify: `src/main/resources/mapper/OrderRequestMapper.xml`
- Create: `src/main/java/com/zewbby/smartticket/service/PurchasePlanReconciliationService.java`
- Create: `src/main/java/com/zewbby/smartticket/service/impl/PurchasePlanReconciliationServiceImpl.java`
- Create: `src/main/java/com/zewbby/smartticket/task/PurchasePlanReconciliationScanTask.java`
- Modify: `src/main/java/com/zewbby/smartticket/config/PurchasePlanProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.example.yml`
- Modify: `src/main/java/com/zewbby/smartticket/service/impl/OrderServiceImpl.java`
- Modify: `src/main/java/com/zewbby/smartticket/service/impl/PaymentServiceImpl.java`
- Modify: `src/test/java/com/zewbby/smartticket/service/impl/OrderServiceImplTest.java`
- Modify: `src/test/java/com/zewbby/smartticket/service/impl/PaymentServiceImplTest.java`
- Create: `src/test/java/com/zewbby/smartticket/service/impl/PurchasePlanReconciliationServiceImplTest.java`

**Interfaces:**
- Reconciliation scans stale `SUBMITTING`/`RECONCILIATION_REQUIRED` plans. It loads the pre-bound request ID and resolves: `SUCCESS + orderId` → `ORDER_CREATED`; `FAILED/COMPENSATED` with safe compensation → `FAILED`; `QUEUED/PRE_DEDUCTED/PROCESSING` → keep/bind `SUBMITTING`; missing request or unsafe compensation → remain `RECONCILIATION_REQUIRED` with an operational log/metric.
- Add lease/updated-at thresholds so `PROCESSING` and `COMPENSATING` request recovery is retried by existing compensation scanners; do not create a second Redis release implementation.
- Remove calls and mapper methods that set plans to `PAID` or `CANCELLED` after order creation. `ORDER_CREATED` remains the terminal consumed plan state while order status changes independently.
- Payment/订单 tests must assert no reservation state mirror calls and preserve order lifecycle behavior.

- [ ] Write failing reconciliation and no-mirror tests.
- [ ] Run focused tests and confirm old state mirroring/recovery gaps fail.
- [ ] Implement service/task/mapper recovery and remove obsolete mirror calls.
- [ ] Rerun focused tests and validate metrics/logging for unresolved plans.

### Task 8: Update API/documentation contract and verify the complete module

**Files:**
- Modify: `README.md`
- Modify: `docs/api/purchase-plan.http`
- Modify: `docs/api/phase2-api.md`
- Modify: `docs/api/show.http`
- Modify: `src/test/java/com/zewbby/smartticket/mapper/MapperSqlContractTest.java`
- Modify or create: controller/API contract tests for purchase plans and sessions

**Interfaces:**
- Document `complete` before sale and `submit` during sale as separate actions.
- Remove client `audienceIds` from the抢票提交 example and remove claims that plans become `PAID/CANCELLED`.
- Document no inventory/price guarantee, exact audience-count invariant, sale-window rejection, ordinary抢票 fallback for uncompleted plans, and reconciliation status.

- [ ] Update docs and add contract assertions for endpoint names and status vocabulary.
- [ ] Run `git diff --check` and the complete Maven test suite with JDK 21.
- [ ] Confirm no JMeter command was run and no unrelated file is staged.
- [ ] Review `git diff --cached --name-only` against the file boundary, then commit all module changes once with a Chinese message: `完善预约计划与开售抢票模块`.

## Self-review checklist

- Spec coverage: sale window, editable versions, exact audience count, snapshot freeze, one active plan, split actions, no inventory reservation, request pre-binding, reconciliation, consumer transaction, no order-state mirror, docs and tests are all mapped to tasks.
- Placeholder scan: no task relies on an unspecified future file or unnamed method; all new methods and state codes are named above.
- Type consistency: all timestamps use `LocalDateTime`; mapper time predicates receive an explicit `now`; request IDs are `String`; plan versions are `Integer` and incremented only by conditional updates.
- Known compatibility rule: legacy `confirm-spec` may remain as a delegating alias during migration, but no code may retain `SPEC_SELECTED` or plan `PAID` as a new transition.
