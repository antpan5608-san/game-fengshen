task_id: NANHAI-01
status: IN_PROGRESS
execution_kind: IMPLEMENTED

# 南海龙王区段连续恢复

当前唯一任务：从正常新游戏连续推进真实南海路线→龙宫剧情/Boss触发→本场真实战斗/胜后→下一可操作状态→保存/外部停止/重启继续。可选补给不变为出发前置。内部M1→M2→M3检查点通过后自动继续，不按旧单阶段停止规则等待用户；整个任务结束才停止，不扩下一大区。

开始2026-10-01 20:33:25 UTC；预算上限2026-10-02 06:33:25 UTC；约75%预算04:03:25 UTC停止扩大范围并收敛验收。平台连续性不保证；每实质检查点或45–60分钟保存本文件/提交与复现动作。

起始main 3e799fb50594fee63b8fd7f8dbba659da1daae84，干净，无待整合修改。实际生产v24/0.8.4-touch-ux-01/c12，来源ef29edb9192bb299ed493b767c52b23c450b2094，构建36917255772；APK SHA def359de888614152768bdb70c4f12a5e09a653124db6eb0650e96a9edb76300，c12 manifest 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8。实际服务器metadata已确认24。保留全部已交付三图/三店、战斗/逃跑/战败、药草、装备、TOUCH-UX/INPUT/HUD/音频/更新/存档。

内容与触控skill已直接读取；不重试自动CLI匹配认证。目标ROM缓存private-inputs/town02/target.nes完整SHA和1048592字节匹配；不重复下载或全量研究。当前环境HTTP策略enforced/unrestricted，平台代理保留；无本地生产Secret，由原受保护Actions执行巡检/发布。

## 续跑状态

- 当前M1/M2/M3已完成首次源码集成，正常Android连续流程尚NOT_RUN；总体IN_PROGRESS，不按资源/单元测试成功关闭里程碑。
- 最近本地可构建检查点：首次Loader IntArray/List类型错误已修正；debug/仪器APK构建成功，JVM69/69（含本段18业务边界）；Python局部导出5/5、原CI安全12/12、药草导出5/5。随后新增真实地平线/宝箱显示与正常/受控验收方法并再次编译；authoring新增说明后旧target pin被门禁拒绝，已按实际导出manifest重新固定hash，未关闭校验。最新同pin构建/测试完成后启动原CI。
- 本轮开工inspect36922834320实际成功，2026-10-01T20:38:20.259042505Z：可信24/23，事件720、测试0、普通真机会话3/模拟器0，错误{}，NO_ISSUES_OBSERVED；v24事件290/v23事件430。范围仅上传样本，非本轮手机验收，旧音频超时UNCONFIRMED。
- 原版限定证据已取得：16(199,130)↔25(39,42)，25(29,44)↔97(15,29)，各落点方向DOWN；group1海底12群、group2宫内13群，宫内HIGH>=245门槛；必要NPC、首/胜后原文；BossROM137/source156/HP120/攻16/防13/敏8/EXP60/钱100/冰8/逃跑必败耗行动/随机長劍50÷128；正常原版新游戏→练7→购長劍→路线→Boss获胜→复谈不重奖全部已由controller-only验证，0761&1胜后置位，0727错误猜测已拒绝。
- 原版地图97手动存档明确拒绝，未有效手动保存的poweron回开局不算恢复成功；Android沿用原有自动SaveSnapshot便利存档，另验强制停止/重启。原版手动存档战败加载分支继续欠账。
- 新增内容通过原export_from_base接续：两必经地图/四连接/三NPC/两可选宝箱原图但交互未开放/四正常敌人及Boss/地图遇敌/本场规则/掉落/本场背景。未变化c12媒体逐字节复用。北部海底实际group4未接入区域明确开发边界，旧地图不缩回坐标白名单。新增BGM来自固定Reference，PROVISIONAL，许可/目标旋律/精确loop仍未知。
- 两辅助任务仅scratch隔离证据/测试草稿，已完成停止；主任务唯一集成打包发布。ROM/rawPPU/RAM/原始截图/回放仍私有，公开provenance仅范围、hash、派生recipe与来源。
- 下一动作：以可信v24不可变artifact核验receipt/同签名→局部导出目标→原Actions构建与KVM AVD实际覆盖升级/回归→正常新游戏供给/练级/路线/Boss/胜后→外部force-stop恢复。失败修复后重建同提交候选，未达门槛不发生产。
- 当前具体阻塞：无不可替代资源/权限阻塞；Android正常M1—M3及候选同源/签名/巡检门槛未执行，当前签名/服务器Secrets只在runner内。
- 时间检查点：2026-10-01约22:05 UTC，已用约1.5小时，剩余约8.5小时。首源码检查点40db7d2；原CI36931479402在Windows导出时失败：Pillow PNG压缩字节与Linux不同，非ROM/像素差异。原导出器现使用固定无压缩DEFLATE PNG，新图先验证已审核RGBA hash再验证目标文件/manifest hash；旧素材字节不变，原源PNG hash保留provenance。本地局部导出7/7通过，跨平台成功仍待新CI。
- 独立干净worktree40db首次恢复59文件成功，使用原入口固定公开输入并核完整ROM/MP3 hash；未迁完整旧目录。全量Python历史ImportIntegrityTests仅缺game-data/raw/reference-project/dataset.json而setUpClass失败，8项跳过、68方法通过；这是未恢复全量历史Reference依赖，不伪报全量通过，不要求全部迁回。另22自动审批安全fixture断言通过。
- 原版现有RAM/static范围补核：Boss初始化遇敌计数32不清，胜后恢复世界清0。仅完成胜后待对话后清零，不在开战初始化伪造清零；受控App验收新增此断言。
- 04:03:25 UTC按75%收敛，截止06:33:25 UTC。下一条动作：提交修复后原工作流重建并实际运行normal新游戏路线。

相关原版路线以docs/original-playthrough-roadmap.md的“当前有效状态”段为准，旧表明确历史；累计十类清单权威仍docs/delivery-status.md。最终只有正常App M1—M3与同产物/升级/巡检通过才READY_FOR_REVIEW，手机和声音不能运行记NOT_RUN。原则上本轮最终一次发布，只写原两个Fengshen对象，不改Language。
