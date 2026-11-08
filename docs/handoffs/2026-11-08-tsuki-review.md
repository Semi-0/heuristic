> Received from the existing Docker Tsuki agent at `/workspace/inbox/2026-11-08-heuristic-reference/TSUKI-REVIEW.md`. Original SHA-256: `6e6a04c238b0ee96d0eddb077f537d6e3b1dd73b07e19914cf0b4b9d6b08a8a3`. The original review follows unchanged. Its statement that bulk publication was awaiting approval reflects the time of writing; the user subsequently chose local-only bulk evidence.

# Heuristic → Tsuki / 小宝：有界参考评审

日期：2026-11-08。状态：静态阅读与书面候选；未执行程序、编译、测试、模型调用、Minecraft、服务或部署，未更改内核、账本或原工作树。只新增本文件。后续运行须由主任务另行授权；未安排自动作业或提醒。

## 版本、完整性与实际阅读范围

- 包入口：[REFERENCE.md](REFERENCE.md)。`PAYLOAD.json` 登记 heuristic `c3ba1a1b7a675f0f515c9968592c25025ac66659`、backend reference `3c3f643b694aa2ef2ec85f239412a8d5627f717a`、editor `602eb67f70020982ae56a6e1522fa4d119889c64`、实验 runtime pin `2773dffc1cf5a2b129559aa445d66d09f96d4dd7`。这是包内元数据，不冒充对这些完整 Git 树的重新检出验证。
- 转交方提供的包 SHA-256：`2e0b440c39bb6ecbd8fb41533e61b291ca5680526e50be412f23064eac1301bc`；本轮未对压缩包本体重新计算。实际枚举 heuristic 下 307 个 `.lain` 文件，未逐个通读。
- 全文阅读：入口、[交接](heuristic/docs/handoffs/2026-11-08-heuristic-to-tsuki.md)、[研究状态](heuristic/docs/research-status-2026-11-08.md)、[session API 审计](heuristic/docs/2026-11-08-session-api-audit.md)、[未改内核交叉检查](heuristic/docs/2026-11-08-unchanged-kernel-cross-check.md)、[实验意图](heuristic/docs/experiment-intention.md)、[session API 测试源码](heuristic/test/lain_harness/session_api_test.clj)、[producer 扩展实现](heuristic/src/lain_harness/session_publication.clj)、[Lain 阶段编排](heuristic/experiments/phase-orchestration.lain)。
- 入口、交接、研究状态、两份 API/交叉检查报告及 session API 测试源码共六文件的 SHA-256 均实际核对，与 `PAYLOAD.json` 一致。未全面校验 bulk archives，未解压或发布它们。
- 904 行原始 harness proposal、其余源程序、所有原始运行日志和历史归档未在本轮全文审阅。下列实验数字来自已读报告，均不是本 agent 新增的独立实测。
- 小宝接口依据此前只读核对的 `mac-source/core/src/{core,lattice,types,nogoods}.ts`、`src/{memory,relational,pc-partial,partial-engine}.ts` 及冻结 `TSUKI-1-v1a`；见既有 [小宝评估](../2026-11-08-xiaobao-tsuki-via-lain.md)。本轮未复核 Mac 主线最新提交，旧报告中 TTMS 的一概 AND 判断已在当前对话纠正。

## 已观察：保留原条件和失败记录

这里“已观察”区分本轮直接读到的源码结构与来源报告中的历史运行结果。

| 项目 | 来源报告的结果或源码事实 | 不能推出 |
| --- | --- | --- |
| 编译后的 list 消费者 | session API 报告 3 tests / 36 assertions；测试通过源码扩展编译 `car/cdr`，检查 flat/nested 结果、定义支持撤回与恢复、稳定输出 cell、形状更新和重复/冲突声明 | 原始 leaf 存储必须消失；所有 compound/domain 均满足同一证明契约 |
| runtime composition | 报告 2 tests / 15 assertions；closure/selector 对照另报 11 个通过 | 整体 harness 或所有 selector 生命周期通过 |
| 特定 select 重汇合 | subtotal 185→257 的特定表达式 correction 变矛盾；等价 if 在四个生命周期阶段通过 | 所有 select 都失败；必须修改内核；if 已在完整 print-shop 恢复中验收 |
| 当前恢复 | 完成 context 边界使控制进入 analysis、generalization、initial implementation；编译拒绝故意无效的 suffix，并保留已接受 prefix；后续 repair 请求未发出，round one 超时 | 全恢复通过；模型能力导致该控制失败；超时已由边界修正解决 |
| 最新恢复测试记录 | 5 tests 中 11 passing assertions、0 assertion failures、2 timeout errors | 恢复模块全绿 |
| 历史 print-shop | 15 次真实调用，35 个初始数值正确、4 次保留 callable 的后续调用；10 项 premise 检查与4项 mutation 检查失败 | 数值正确即支持完整，或当前单编排设计已可靠完成 |
| 历史 host controller / patched kernel | 保留真实 repair 历史；另有 84/84 的已撤销内核变体 | 当前验收；可以复用第二 host controller 或应用归档 patch |
| 持久化 | 已观察到的复用主要发生于存活 session/lexical child | 进程退出后的持久重开、原身份恢复或效果恰好一次 |

来源报告中的 28 focused tests / 460 passing assertions、legacy import 错误和超时需按模块与版本保留，不能与后续新增模块混算为一个完整通过总数。CI/部署收据也不是 heuristic 全流程验收。

## 接口映射：候选对应，不宣称等价

| 小宝 / Tsuki 边界 | Heuristic / Lain 可参考接口 | 状态与约束 |
| --- | --- | --- |
| producer 身份、来源修订 | session-publication 使用 endpoint + epoch 构成不可变扩展身份；相同声明 replay、冲突复用拒绝 | 已读源码；与贡献 tag、writer ID、ledger event ID 是不同身份，不直接合并为一个键 |
| 原始元素与 `read()` 支持投影 | 原始 returned-list 存储、编译后的 accessor、可用值与 support 分开观察 | 已观察的有用方法；不因原始对象残留判撤回失败，也不因 accessor 成功判全部 OR 语义等价 |
| writerSupersedes | Lain 保留 callable、端点、连接与扩展身份 | 未决：小宝是 cell × writer 的函数输出取代，不能用观察版本 dominance 或扩展幂等直接替代 |
| zset 贡献身份 | 按标签去重的领域值 + 显式消费端口 | 提议：作为关系/领域值组合；本包未证明 tagged-union 和求和投影的完整实现与格律 |
| 支持、owner nogood、一致性视图 | premise publication、可用性检查与连接观察可供诊断 | 未决：该 harness 未证明 owner-scoped nogood、跨视图隔离或小宝账本重建等价 |
| 规则候选准入 | 完成 response → source admission receipt → accepted forms → outputs/support observations | 提议：对应 Tsuki 规则候选的分层报告，不直接赋予正式规则发布权 |
| ledger 与正式决策 | source/thinking/reply/receipt/history 分账，session 与 child 身份分开 | 可借鉴审计结构；正式 SQLite/Markdown 仍归小宝主任务，不迁入 harness |

特别注意：这个包的 `session_publication.clj` 用 `collection/content` 构造 support-only producer。不能将其结果直接等同于另一个公共 `source-publication/active`、`retracted` 所用的 state-aware 内容路径；比较时必须记录具体入口与 projection。

## 提议一：同值双来源的消费端合同（仅书面，不执行）

三引擎：Lain TTMS、Lain `datastructures/tms`、小宝 core；TTMS 再分 support-only 与 state-aware 两配置。统一业务值与来源映射，不要求各引擎内部 support 编码相同。

序列：A 与 B 独立断言同值 → 撤 A → 恢复 A 的新修订 → 撤 B → 两者均撤。每阶段分别记录：原始 evidence/claims/elements、公开 projection、编译后的消费端值、支持、unusable/conflict/pending、旧快照和输出端点身份。小宝读取 `support` 并非完整 DNF，需要同时记录原始元素；精确值、无 witnesses 的 zset 及至少一个下游 writer 应分案例。

当前已知：此前对话的有界独立实测中，TTMS support-only 在撤 A 后不可用，而公共 state-aware 路径保留 B。小宝现有测试源码已有独立来源存活案例。其余同合同跨引擎结果尚待授权后验证。此最小案例不证明 owner 隔离、多世界、全域支持完整性或重启等价。

## 提议二：候选规则准入与修复反馈关系（仅书面，不执行）

候选关系：`完成回复 → 准入回执 → 已接收前缀/诊断 → 结果与支持观察 → 有界 repair 请求`。用独立 response/form/revision/attempt 身份关联；编译成功、输出就绪、数值正确、必需支持齐全、正式采纳分别出报告。

未来获准的最小控制可使用固定无效 suffix，不调用新模型。预期应保留有效前缀并只产生一次相应失败反馈/repair 请求；重复 receipt 不重复请求，源失败不被 host 静默修成答案，达到预算后形成明确终态。先观察 receipt、attempt、feedback、trigger、request 各边，再判断断点。当前材料只能把 stall 定位在编译报告之后与请求之前，不能从静态代码确定具体根因或承诺修复。

`cell:first` 只用于“每阶段一次完成的 context observation”，不是冻结活领域 cell 的通用办法。将这种阶段快照误用为 Tsuki 领域状态会掩盖后续撤回，必须独立审核。

## 未决与下次共同评审入口

1. 返回接口形状变化允许范围、动态端口与访问器生命周期：需主任务决定合同，不沿用旧“必须抛异常”的预期。
2. 支持缩减时 select/if 的差异：保留最小源、输入版本和当前 runtime pin；不带入已撤销内核补丁。
3. owner nogood、跨视图隔离、writer 输出失效及重建后可观察等价：独立于 harness 编译/恢复验收。
4. closure 的存活会话复用和跨进程持久化恢复是两项验收；持久化也不得自动重发旧效果。
5. 本包 backend reference、实验 pin 与原 Tsuki 评审 `a6edf223…` 不同；新候选须先固定实际源码差异，再决定哪些结果可复用。
6. bulk archives 尚未获远端发布授权；保留本地参考，不复制发布、不运行恢复脚本。本报告不安排几天后的自动执行。

本次扩展仅是本文件中的映射和两份书面探针合同。正式文件、分级、规则采纳与下一步授权仍由 Mac 主任务收口。
