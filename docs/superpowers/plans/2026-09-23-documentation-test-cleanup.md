# 文档与测试整理实施计划

> **For agentic workers:** 本计划用于当前仓库的文档和测试资产整理，执行时保留工作区既有业务改动。

**目标：** 建立可导航的文档入口，收拢低风险的文档命名，修复已知断链，并避免生成物继续污染仓库状态。

**范围：** 只处理文档组织、文档路径引用和生成物忽略规则；不删除有运行依赖或契约断言的测试源码，不修改业务行为。

**约束：**

- `docs/sql/schema.sql`、`docs/sql/data.sql` 和 `docs/sql/local-schema-repair.sql` 必须保留原路径或同步更新所有硬编码引用。
- 预约、艺人和搜索模块的未跟踪源码、测试、接口示例及实施计划属于现有工作，不纳入删除。
- `reports/` 与根目录 `jmeter.log` 是本地生成物，本次只加入 `.gitignore`，不删除现有文件。
- 所有验证命令从仓库根目录执行；最终提交说明（如提交）使用中文。

## 执行步骤

- [x] 新增 `docs/README.md`，说明目录职责、当前接口样例和历史调试样例。
- [x] 将系统流程阅读指南移到 `docs/architecture/`，将压测报告模板移到 `docs/performance/`。
- [x] 同步 README、阅读指南和 `MapperSqlContractTest` 的硬编码路径。
- [x] 修复异步全链路 HTTP 示例中对不存在 SQL 文件的引用。
- [x] 在 `.gitignore` 中忽略 `/jmeter.log` 和 `reports/`。
- [x] 扫描文档引用，按用户要求不在本次新分支提交前重跑测试。

## 交接记录（2026-09-23）

- 本次文档整理单独提交到 `codex/documentation-cleanup`；按用户后续指示，不合并或推送到 `master`/`main`，也不重跑测试。
- 与艺人榜、搜索、预约计划及观演人相关的业务实现、SQL/schema 和测试改动仍留在工作区，不属于该文档提交；不得用 `reset`、`clean` 或批量恢复覆盖这些改动。
- `reports/`、`jmeter.log` 和 `.DS_Store` 保留为本机文件；生成物由 `.gitignore` 排除。
- 继续任务前先检查 `git status`、当前分支和提交记录。完整测试需在用户要求继续代码整合时用 JDK 21 重新运行；本次用户明确要求跳过测试。

### 下次续接提示

```text
请从 smart-ticket-lite 当前工作区继续。先检查当前分支、最近提交和 git status；上一轮把文档整理单独提交到了 codex/documentation-cleanup，没有合并 master/main，也按要求没有重跑测试。预约计划、艺人榜、搜索和观演人相关的业务代码、SQL/schema 与测试仍可能是未提交改动，必须保留，不要 reset、clean、覆盖或擅自合并到 master。先逐类盘点未提交改动并报告风险，再按我这次的新要求处理。
```
