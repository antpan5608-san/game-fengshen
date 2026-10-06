# 本地迁移交接：当前唯一有效任务

task_id: LOCAL-AI-INTEGRATION
parent_task: WORLD-FULL-01（暂停，未完成）
status: LOCAL_TAKEOVER_AND_LOCAL_AI_ACTIVATED
development_location: USER_COMPUTER_ONLY
cloud_role: EXISTING_GITHUB_ACTIONS_BUILD_AND_ACCEPTANCE_ONLY
ALL_MAPS_USABLE: NO

用户2026-10-06最新授权替代本轮旧连续开发/发布授权：停止新增功能与新发布，保存所有工作区公开成果、推送开发分支、提交交接文档后停止。不得合并main、取消既有CI、删除工作区、清档或公开ROM/原始回放/私有日志/密钥。

交接唯一入口：[local-migration-cloud-handoff.md](local-migration-cloud-handoff.md)。完整工作区/分支SHA在docs/handoff/local-migration-inventory.json；私有原始输入仅路径/hash索引在docs/handoff/local-migration-private-input-manifest.json，原始文件没有迁到用户电脑。

生产仍v82/c51-r1；冻结R2候选v83/c50来源75ac819cad0bc387bafbf35ac3ccb352229aab9f，run37408307126不取消，任何后续通过结果也不自动审批/发布。

开发c60及未完成c61保留在work/world-jiameng-next，交接规则/文档位于work/local-migration-handoff。保存历史驱动修改在work/world-05bfcc05。累计未完成项目仍docs/delivery-status.md。

电脑已接管：`F:\apps\game-fengshen` / `codex/local-ai-development`，来源交接提交 `ed442e8dcd1f7f33a68111532b73f9f29055c622`。旧目录原样保留并备份。匹配 ROM 与可信 v27 基底已验 hash；c60 的 378 文件已恢复，405 个 JVM 测试通过，DEBUG 应用及仪器包构建通过。c61 未启用，未发布 APK、未合并 main，原手动回档仪器失败仍保留，不在本轮修改游戏功能。

局域网模型协作工具、项目高推理配置、结束检查和审查回执已实现。用户追加最大权限授权后，已新增本仓库可信记录；Codex 实际配置验证为完全访问、无需审批、gpt-6.1-sol / high、本地 MCP 已启用。已核对的 Stop 定义按当前哈希启用，Codex 返回 trusted。已有聊天重新打开项目后加载最新配置。使用入口：[local-ai-usage.md](local-ai-usage.md)，实测与限制：[local-ai-validation.md](local-ai-validation.md)。云端原始取证目录仍保留，未整包迁出；不因此要求完整历史资源来阻塞当前可恢复资源与工具验证。

当前任务只完成迁移和协作工具。游戏后续研发由电脑另按用户指定范围接续；云端任务已停止，不自动续跑。

交接前完整状态保留：[历史快照](history/world-full01-before-local-migration-20261006.md)。该快照的“下一动作/自动发布”已过期，不是当前授权。
