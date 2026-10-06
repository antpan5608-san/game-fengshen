# 本地迁移交接：当前唯一有效任务

task_id: LOCAL-MIGRATION-HANDOFF
parent_task: WORLD-FULL-01（暂停，未完成）
status: WAITING_LOCAL_TAKEOVER
development_location: USER_COMPUTER_ONLY
cloud_role: EXISTING_GITHUB_ACTIONS_BUILD_AND_ACCEPTANCE_ONLY
ALL_MAPS_USABLE: NO

用户2026-10-06最新授权替代本轮旧连续开发/发布授权：停止新增功能与新发布，保存所有工作区公开成果、推送开发分支、提交交接文档后停止。不得合并main、取消既有CI、删除工作区、清档或公开ROM/原始回放/私有日志/密钥。

交接唯一入口：[local-migration-cloud-handoff.md](local-migration-cloud-handoff.md)。完整工作区/分支SHA在docs/handoff/local-migration-inventory.json；私有原始输入仅路径/hash索引在docs/handoff/local-migration-private-input-manifest.json，原始文件没有迁到用户电脑。

生产仍v82/c51-r1；冻结R2候选v83/c50来源75ac819cad0bc387bafbf35ac3ccb352229aab9f，run37408307126不取消，任何后续通过结果也不自动审批/发布。

开发c60及未完成c61保留在work/world-jiameng-next，交接规则/文档位于work/local-migration-handoff。保存历史驱动修改在work/world-05bfcc05。累计未完成项目仍docs/delivery-status.md。

下一条操作由用户电脑执行：安全保留现有本地修改和存档，fetch交接分支，阅读交接文档并核对SHA；私有资源通过安全渠道另行取得。当前云端任务交接结束后不自动续跑。

交接前完整状态保留：[历史快照](history/world-full01-before-local-migration-20261006.md)。该快照的“下一动作/自动发布”已过期，不是当前授权。
