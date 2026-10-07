# 战斗提神术与解毒术：当前有限代码批次

状态：IMPLEMENTED / LOCAL_TESTED / PACKAGED；source7f011ab的实际同候选 App 验收失败，定向修隔离驱动中，未 App 通过/发布/公网。当前线上仍 v89，长期 MAGIC-ORIGINAL-01 A/B/C、BATTLE-VISUAL-02 和所有未完成欠账不关闭。

## 正式候选失败与定向修正

2026-10-07原build37641724001成功，runtime无job/未执行，页面Internal server error、correlation3ce665b7-74b6-4829-b956-e88f81db6c1b；failed rerun被平台拒绝。相同源7f011ab再运行37644034300，build成功，437 release JVM/94 suites零失败；APK32,806,652字节/fullSHA02ab63d679e27a4d116b833955a50c516ae8050649aa9e9f7d6da26d47f6ba2e经独立签名/版本/内容验包。两个同源构建字节不同，不复用首包SHA。

真实App在2倍字体TouchTest:120、otherCommands:76失败：expected yangjian / actual xiaolongnv。实际selection PNG仅显示提神术，解毒术在滚动区域下方，选择为空；1/1.3倍字体HEAL44→TEXT41 / STATUS41→TEXT38及战后完整保存JSON实际通过。外部cold和最终33门禁未到达，不称App通过。分类TEST_HARNESS，复用已有scrollToBattleItem真实手势使目标行至少48dp可点，再按稳定ID选择，新增实际selectedBattleItem及滚动/选择不变队伍、MP、inputRevision、RNG、完整保存断言；不修改规则或删大字检查。修改后另行新4B/编译/同源App；failed候选不发布。原失败图/日志保留私有忽略目录。

## 原版依据与边界

复用 [battle-magic-foundation.md](battle-magic-foundation.md) 的六项原 CPU 表与原习得320矩阵。小龙女 actorIndex1：提神术等级1、解毒术等级10，均3MP。初始 MP 不足不能选；输入收集/浏览/取消不扣费。原提神术固定施法者身份等级，治疗 `3*(displayLevel-1)+20`、上限截断，目标死亡 bit20 不复活；满血、死亡目标或健康目标解毒均在行动后扣3。解毒仅对 exact status2 生效。效果→晚扣费→原 A752 状态收尾各有独立帧，保存仍是原战后事务。Native原65535MP控制值不扩玩家0..9999存档域；state64中文名未核，不提升为已知名称。

原进入战斗的 active party 过滤仍保留；纯效果表允许 state64 的证据是战斗建立后受控变更，不能推成战前 state64 角色必然在队内。杨戬/姜子牙的初始法术、攻击公式/RNG、其余控制/地图能力未开放。

## 最小现有接口适配

OriginalBattleMagic 接现有 OpeningBattle 稳定角色命令和 originalRound 排序；无第二战斗、队伍、MP、存档或随机发生器。小龙女排队只递增原 inputRevision，待四个实际输入完成再运行；敌先击倒施法者通过原 canAct 跳过，目标先倒仍晚付费。自身治疗先更新 target，再从更新后的施法者扣费，避免旧 player 对象覆盖治疗。

BattleActionStep.partyMp 新增为构造体外字段，旧构造/跨APK ABI保持。BattlePartyView 与旧窄屏分支仅读当前帧 MP；旧帧缺新字段时兼容回退原角色MP。战斗法术沿原物品模态几何列表→目标→确认，绑定 battleId/inputRevision/actor/revision；取消、多指、重复UP、旧回调不提交。角色页地图能力仍独立，不套用 battle 谓词或 post-HP。

## 已实际完成的本地验证

- 第一组原 CPU TSV 对照6 JVM通过；完整接入后437 Debug JVM /94 suites，failures/errors/skips均0；新队列8项覆盖四人无早扣费/采样、自疗、满血/健康/死亡目标、先死目标/施法者、初始不足、旧帧MP回退。
- 同时实际 compileDebugAndroidTestKotlin 通过，最终本地调用2m36s；新增实际触控/阶段/完整保存/冷启入口仅编译，未称作 App PASS。
- 初次完整本地测试因新增测试 helper 循环在回合后继续收集下一轮而不退出，实际识别本次 Gradle Test Executor PID/父进程/命令后停止，保留7m41s失败。helper改有界最多四次并要求一个完整回合；修正后完整437通过。游戏回合未为此修改。
- Python raw proof/范围/原表/录屏回归实际41项通过，随后新增 JVM 资源逐字节与原6表/学习表绑定后新子集15项通过。最终追加回归结果另记，synthetic transport fixtures只用于校验发布拒绝，不是 App 证据。

## 实际 App 门禁与发布约束

原31门禁全部保留，新增 battleMagicFourRoleTouchPhasesAndFonts、battleMagicFullSaveExternalCold，共33。三字体1/1.3/2的隔离四人真实触摸、HEAL44→TEXT41 / STATUS41→TEXT38完整队伍阶段图/JSON、实际物理胜利/原事务提交、原录像器完整保存外部冷启及偏好恢复必须同正式候选通过。首次实际执行范围与失败见上文；完整33门禁尚未通过。测试不能在真实设备上写fixture，原IsolatedGameTestCase守卫保留。录屏为静音，无真机/声音/正常入队/完整主线宣称。

runtimeBaseline仍实际已发布 v89/source588f60d/build37624018494/完整SHA1cf7f56c…，内容 c62/manifest625a…/392文件与历史导出基底保持。runtime-scope 的33项内容hash已重新绑定；历史 golden scope/旧29、31验收仍按原版本有效。新候选code90时不能重哈希删除战斗法术门禁或raw digest，正式版本号和来源以实际构建回执为准。

原业务前 inspect37635216455 SUCCESS；2026-10-07T14:16:17.215308353Z 实际状态 NO_ISSUES_OBSERVED，errors{}、cleanup0，保留89/88，349事件/2 realDeviceSessions均来自88，89样本0。这不是本候选的手机或App验收。

4B 完整批次审查、实际逐条决定、正式同源App/视觉/发布结果须后续记录，不能由本文件预填成功。

最终本地追加49项 Python SUCCESS10.206s、27历史UI/handoff/c61回归SUCCESS16.086s；原runtime Bash语法及diff-check通过。原collector遗漏battle-magic前缀已精确加入png/json白名单，真实PYEVIDENCE块使用隔离adb传输测试证明新文件被收集、raw/player输入仍排除；这不是App。scope按严格LF核hash后固定33门禁。

4B初轮32全新431.203s全部实际阅读；最终snapshot5ac39123f5b8956d5dcf4a9701dc58d685801a26f4640ae93621a64e5dbf3086，34段/6全新97.641s/无未覆盖，28缓存文本逐字节与已读相同，新6段全读，已原CLI决定登记reviewed。保留已核原版边界、原失败/ABI/存档保护和实际App未执行状态；拒绝模型删除硬门禁、混淆发布回执与玩家存档、虚构缺方法/变量/MP写入及把合成测试冒充App的建议。

滚动修正实际compileDebugAndroidTestKotlin通过1m58s；Debug JVM任务UP-TO-DATE，既有437结果保持，未冒称此次重跑437。新4B snapshot451fb13093b9a36d772fb8c67fdb50117dcc4ee551dc6b23928efe251c042036，1全新/15.735s、无未覆盖、全文已读。拒绝删除输入收集期间RNG/inputRevision断言（该阶段本来不得采样/提交）、为隔离测试增加生产API、使用不存在的TextMeasurements；反射限既有隔离测试，无玩家数据写入，实际PNG仍须查看。修正后正式同源验收待执行。
