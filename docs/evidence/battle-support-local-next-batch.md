# 原治疗局部光效：当前有限接续批次

状态：IMPLEMENTED_LOCAL_VERIFIED；拟93 / 0.8.23-support-visual-personal，尚未正式签名/App/发布。线上个人92、稳定82，WORLD-FULL-01 IN_PROGRESS/PARTIAL及所有三项终点/十类欠账保持。

发布92后请求prepare_context定位返回工具错误，按AGENTS退回rg；实际读BattleVisualGeometry、MainActivity统一drawBattleScene/旧fallback绘制、BattleActionStep原magic执行阶段及真实target身份、TouchTest原三字体只读队列、rawproof与对应测试全文。没有等待或强制本地推理；既有CPU导航成果保留。

最小生产差异仅BattleVisualGeometry新增纯target投影及MainActivity三处条件：actorSlot/targetSlot为空且actor/target均属于现有party，abilityId精确为原HEAL/ANTIDOTE，kind为SPECIAL/HEAL/STATUS，才能使用真实目标局部柔和光环；复用原HEAL的绘制/时间/sprite框，扣费TEXT无光效。已识别准备SPECIAL停止全arena白遮罩；未知/敌special/物品继续旧反馈，legacy无scene分支不改。失败/倒下目标只显示原准备或状态反馈，不推断成功治疗，不改规则或新增回血。

Battle/SaveState/SaveHistory/CloudSave/WorldItems/OriginalBattleMagic及Content/BattleVisualAssets字节保持本批前一致；c62/392、16图清单/原签名不改。新增JVMguard覆盖已识别两法术三阶段/只真实target/不读提示词/保持所有帧状态，拒actor/target缺失/不存在/敌slot/未知/物品/扣费/物理等。原真实instrument队列增加supportTarget/arenaFlash字段与断言，36门禁保留；rawproof新严格source-scope拒旧报告/错目标/全场闪光/非布尔字段，不复用92raw。

真实新447 JVM/95 suites零失败错误跳过，原应用和仪器DEBUG构建12秒成功；7组相关Python68项通过（含visual/raw/CI运输/法术/原handoff），最终新视觉6项另复测。只是本地编译/纯投影/raw传输检查，不是新App/视觉验收。日志在服务器receipts/visual-local-effects-{build.log,python.log,python-final.log}。新preflight NO_DATA，errors空、cleanup0、保留92/91，不能当健康通过。

下一门槛：实际完整差异阅读/最新codex_only与Stop；代码提交/冻结同source main，原Actions93签名/21Content/36门禁，查看原三字体准备/HEAL/解毒STATUS图片及正常攻击奖励用药录像/完整cold，检查合法target局部效果与unknown/敌fallback。仅门槛真实满足才原自动审核发布同APK/完整公网/postflight；任何失败原件保留。手机/声音/完整主线未验，按战斗需求准备、四人重叠、2倍字体和其他敌图/动作仍OPEN。
