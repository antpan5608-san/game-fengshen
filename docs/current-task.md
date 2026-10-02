# 当前任务

task_id: NANHAI-01
status: READY_FOR_REVIEW
execution_kind: IMPLEMENTED
scope: 南海龙王区段连续恢复（M1→M2→M3）

## 当前有效状态

- 任务20:33:25 UTC开始；本段实现/验收/正式发布完成，结案记录生成2026-10-02T02:12:15.520487+00:00，10小时预算内提前达标。完成后STOP，不自动推进其他海域。
- 开始main:3e799fb50594fee63b8fd7f8dbba659da1daae84；实际旧APK基底v24/c12，来源ef29edb9192bb299ed493b767c52b23c450b2094/build36917255772。文档提交与APK来源不同不代表发布错误。
- 当前正式APK:v25/0.8.5-nanhai-01；实际来源d87bb7540018913ad17d3865264924add6fa2580；构建/runtime36950387932；审批/发布36953281754。发布后文档提交允许不同，不能将其说成APK来源。
- APK SHA:19c53eae0f804f86cfcf796b7c72f63459f946feaad54a8f7bef7e5b9ccf8280，12790724字节；包org.fengshen.dev/原签名5c460557…；内容opening-segment-001-c13/manifest badb0194e1342b66732cb2258fed7da2e80910f46fa74cccaac2a339cc4fcbc4，59文件。33旧媒体逐字节保留。
- M1 PASS:114→16→可选0/三店→16(199,130)→25(39,42)，独立返程也通过。
- M2 PASS:25(29,44)→97(15,29)→必要守卫与龙王(15,3)，站15,4正常触发，不新增供给/等级条件。
- M3 PASS:正常117场战斗/19地图药草，真实龙王物理/冰、胜利+60EXP/+100银两及本次随机長劍1；胜后离宫再入/复谈不重奖；保存/外部force-stop/实际冷启继续。最终97(15,6)、Lv8、HP57、EXP850、银两361、药草3/背包長劍1，胜标保留。
- 正常主线1384.244秒/冷启3.314秒OK；v24→25同签名install-r覆盖、JVM69、Python相关24、三店/装备/满HP药草/TOUCH-UX/INPUT-01与2640×1216三字体回归通过。Content私有云读无凭据NOT_RUN；一加13T/声音NOT_RUN。

## 实际巡检与发布

- 原受保护inspect36922834320/36933053909/36941528938/36946324010真实查询24/23；不引用为本轮真机验收。
- 发布preflight01:57:14Z，24/23:727事件/0测试/普通真机3/模拟器0，errors={}，cleanup0。发布postflight01:57:32Z，25/24:297事件全部24/0测试/普通真机1/模拟器0，errors={}，cleanup0；25无样本，健康覆盖不足。
- 01:57:19.9185492Z正式发布；独立公网完整字节/包/证书/59内容匹配同一审核APK。只写原Fengshen两个对象，Language/真实云存档不变，原reviewer/签名/同来源保护保留。

## 证据、资源与限制

- 原版范围见game-data/provenance/nanhai01.json；目标ROMf3596ffd…完整SHA匹配，缓存与固定公开Reference复用，license UNKNOWN。原ROM/私有回放/密钥未进公开Git/artifact；原Windows整目录和手机未接入。
- 四出口独立记录与落点方向DOWN、完成换图计数0已核；不交换猜反向。地图97原版拒绝手动保存，Android自动SaveSnapshot为沿用便利适配，不冒充原卡手动保存能力。
- 早期失败/取消尝试不算通过；Windows PNG/UTF8/路径、UI观察、随机命中假定、pull顺序与合法NPC朝向预期的修正可由Git历史及原run追溯。最近完整可靠候选是36950387932，不能用旧候选结果替代。
- 原片/六关键截图与状态索引在本次run的fengshen-town02-runtime-evidence、fengshen-nanhai-checkpoints、fengshen-nanhai-entry-clip、fengshen-nanhai-final-clips；详情/hash/时段见docs/delivery-status.md最后DELIVERY_REPORT。录像/截图14天，APK30天。
- 本地无KVM软件AVD两次ANR后用原Actions KVM，未建设第二套平台；技能隐式CLI匹配历史401，不重试或修改全局认证。

## 保留成果与未完成

三店/原三图/药草地图使用（+50截断、满血消耗、取消/死亡/无物品不消耗）、装备与直接触控、普通战斗/逃跑/新游戏战败、INPUT-01、音频、存档/云存档、更新器/诊断均保留。

十类累计欠账唯一权威:docs/delivery-status.md“未完成清单（累计）”。本段已关闭南海必经/此Boss子项，不关闭全村/全物品/战斗药草/全规则与canonical/真机/真实云恢复。北部group4、两可选宝箱奖励、完整图形动作/字库/调色/时序与新BGM精确映射仍未开放或暂定。

下一动作:STOP。等待本次验收或新任务授权，不自动开后续大区。稳定执行方法复用两个现有skill，不在任务文档复制脚本。
