task_id: NANHAI-01
status: IN_PROGRESS
execution_kind: IMPLEMENTED

# 南海龙王区段连续恢复

当前唯一任务：从正常新游戏连续推进真实南海路线→龙宫剧情/Boss触发→本场真实战斗/胜后→下一可操作状态→保存/外部停止/重启继续。可选补给不变为出发前置。内部M1→M2→M3检查点通过后自动继续，不按旧单阶段停止规则等待用户；整个任务结束才停止，不扩下一大区。

开始2026-10-01 20:33:25 UTC；预算上限2026-10-02 06:33:25 UTC；约75%预算04:03:25 UTC停止扩大范围并收敛验收。平台连续性不保证；每实质检查点或45–60分钟保存本文件/提交与复现动作。

起始main 3e799fb50594fee63b8fd7f8dbba659da1daae84，干净，无待整合修改。实际生产v24/0.8.4-touch-ux-01/c12，来源ef29edb9192bb299ed493b767c52b23c450b2094，构建36917255772；APK SHA def359de888614152768bdb70c4f12a5e09a653124db6eb0650e96a9edb76300，c12 manifest 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8。实际服务器metadata已确认24。保留全部已交付三图/三店、战斗/逃跑/战败、药草、装备、TOUCH-UX/INPUT/HUD/音频/更新/存档。

内容与触控skill已直接读取；不重试自动CLI匹配认证。目标ROM缓存private-inputs/town02/target.nes完整SHA和1048592字节匹配；不重复下载或全量研究。当前环境HTTP策略enforced/unrestricted，平台代理保留；无本地生产Secret，由原受保护Actions执行巡检/发布。

## 续跑状态

- 当前M1：限定路线/出口/碰撞/遭遇证据定位；M2/M3尚未实施。
- 最近通过：基线/ROM一致性核验；本轮业务巡检待执行，不用历史数据冒充。
- 辅助任务：限定路线及Boss取证，各自在scratch隔离，主任务唯一集成/打包/发布；不并发改共享源/manifest/CI。
- 下一动作：提交当前任务入口→原inspect模式实际开工巡检；同时定位原导出器/运行Schema的最小扩展。
- 阻塞：真实南海地图/必要条件/Boss行为及胜后尚待本段证据，不猜数值或路线。
- 剩余预算：开工约10小时；阶段/提交/证据随后按实质检查点更新。

相关原版路线以docs/original-playthrough-roadmap.md新增当前有效段为准，旧表明确历史；累计十类清单权威仍docs/delivery-status.md。候选未达正常App/升级/同源同产物/巡检门槛不发生产；原则上本轮最终一次发布，只写原两个Fengshen对象，不改Language。手机/声音不可运行记NOT_RUN。
