task_id: TOWN-02
scope_revision: herb-use-and-supply
status: READY_FOR_REVIEW
execution_kind: IMPLEMENTED

# 开局药草使用与补给闭环

本次已实施并发布，完成后停止，不启动其他区段。范围仅rom.medicine.0地图/菜单使用；药草是可选补给，没有出村前置。保留既有三店/三图、战斗、输入、音频、更新器和旧档/云存档。牛黃丸、客栈/NPC、新地图、Boss、战斗物品与真机验收不属于本次完成项。

用户最新公开资源授权见AGENTS.md：普通公开获取、版本/hash校验、必要来源/许可记录、缓存复用，不等整历史目录；许可UNKNOWN不声称已授权。历史首次接续BLOCKED报告保留追溯，本轮已经解除当时的ROM/药草证据阻塞。累计十类清单仍在docs/delivery-status.md的权威清单，只有药草地图/菜单子项关闭。

## 实际交付

- 开始main 98edf3d31cd49679dae83824eaac0fa0208d2859，游戏v21/c11、已发布v22；开工巡检36892719605于16:32:41 UTC实际查询22/21，452事件/14测试，6模拟器+2历史真机会话，无上传错误。不是本轮手机验收。
- 单个公开目标ROM完整hash匹配，private-inputs缓存，许可UNKNOWN；六类原版菜单受控实验和静态例程确定恢复50HP/截断、满HP消耗、取消/战败目标/无物品不消耗。详见game-data/provenance/town02-herb.json，不把fixture当正常游玩。
- 原物品面板/统一HerbUse/存档接入，触摸一次结算；c11旧档兼容。原导出器可信c11基底局部生成c12，不变素材逐字节复用；干净checkout及临时目录恢复验证。
- 最终构建36902536271，来源77cdc6b7f822628e0fe8b2a4292e6d396d9089f3，签名v23/0.8.3-town-02/c12。APK SHA256 1a5a5e10f2793c1418a83a2da3b218ebdc2ff2274b2f02d3d1a30c2153d63bbf；11322079字节。目标manifest 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8。
- Python transport12/局部导出5/审批fixture22通过；本地JVM44通过，原CI release构建/测试通过。同一正式签名候选21项仪器测试通过：旧v22正常存档并adb install -r覆盖、边界、Content12、三店买卖/装备/INPUT-01/摇杆隔离、正常药店购买/受伤使用、停止进程重启并继续探索。实际录像静音，声音NOT_RUN；本地软件AVD两次System UI ANR保留失败，未冒称通过。
- 发布36903560942同提交成功，自动审批/运行回执/同APK hash门禁通过，仅写既有两Fengshen对象，Language未改。18:01:49 UTC发布；18:04:11 UTC独立下载正式APK，metadata/字节/签名/c12全内容一致。
- 发布后18:02:38 UTC实际巡检23/22，82事件/0测试、1历史真机会话，样本仅v22，v23无上传样本，NO_ISSUES_OBSERVED/清理失败0。一加13T、长时声音/性能、真实账号多设备恢复均NOT_RUN；历史音频/手机更新根因不关闭。

完整固定交付正文和最新欠账变化见docs/delivery-status.md最后TOWN-02报告。已发布APK来源固定；本次交付文档提交不改变该产物来源，不因此发布新版本。
