# 首批战斗视觉候选：服务器有限代码批次

最新实际状态：v91已沿原Actions发布并公网完整字节/postflight复核，见[v91交付](v91-battle-visual-personal.md)。下列候选失败及未发布状态保留为历史，不覆盖最新交付。

历史候选检查点：首轮正式build37669616584/source110f8c7已签名，但App在固定窗口指标汇总FAIL，未发布。Windows新增交接已选择性合并缓存/有界读取，修正为强制实际Insets证据后441 JVM/88 Python通过；须新同源Actions。详见 [交接、失败与下一检查点](../handoffs/server-windows-visual-20261008.md)。下文保留首轮本地快照，旧检查不替最新代码App验收。

日期：2026-10-08 北京时间。WORLD-FULL-01 仍 IN_PROGRESS/PARTIAL；本文件不是整套视觉或游戏长期完成报告。当前正式个人版90、历史稳定82；新规划候选91 / 0.8.21-battle-visual-personal，正式签名/App/发布尚 NOT_RUN。

## 实现与素材范围

从已迁入 `.local-ai/battle-visual-02` 的准备记录中选用12份自行生成的原始候选：四种草地/海岸/海底/洞穴背景、四主角头像、四待机全身透明图。全部完整SHA、字节数、尺寸、PNG完整性已核且逐张实际目视；皇冠裁切头像、带色晕的杨戬待机候选继续排除，私有原候选/提示词/失败保留。只复制选中素材的原字节，不上传ROM/商业参考图/原始回放/私有存档/凭据。

选中素材及公开最小provenance在 `game-data/visual/battle-visual-02` 与 `game-data/provenance/battle-visual-02-assets.json`。自行生成且在人类已批准的BATTLE-VISUAL-02范围内；不声称原ROM美术身份或商业图授权。视觉清单SHA `4480914b805e4b5c99feeaf9d207227a60bca4accf3379fd151c13ccbaac571c`，12图共24,718,100字节；APK资产前缀 `assets/battle-visual-02/`，含清单13文件。

c62的392文件、manifest `625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef` 及存档版本保持。视觉哈希单列于原 `ci/content-source.json` 并由原APK验包器复核，不新建规则/存档/构建或发布系统。

ContentLoader通过原ContentSource载入视觉配置，绑定稳定ID，按需校验/解码并限制64 MiB缓存。头像采样4、待机采样2、背景原尺寸；保留透明图原字节，绘制用已记录边界/脚底锚点和等比缩放。地图7/16草地、10海岸、25/97海底、85洞穴是美术映射，不提升为原版图形证据；其他场景保留旧素材并继续欠账。

统一battleSceneLayout新增可选视觉参数，原五参数ABI保持；宽屏1/1.3字体四人单排卡，大字两列。新斜列待机、完整背景、独立头像与敌人紧凑标签复用原绘制/命中。头像与HP/MP区域分开；目标选择与输入revision保护不改。攻击准备/前进/命中停留/返回、治疗局部光环、受击闪烁和敌方死亡淡出仅读取BattleActionStep及BattlePresentation计时，不采RNG、不扣MP、不结算或写存档。

专用攻击/施法/受击/倒地/胜利图集、敌人高清图、完整胜败连续演出、其他地图/探索/NPC/物品/商店统一美术仍OPEN，不能把待机图移动当全套动作完成。

## 已实际执行的检查与保留失败

- 新代码439项JVM/94 suites，失败/错误/跳过均0；相关Python86项/6.909秒通过（原门禁、视觉文件/坏hash/缺图/重复/路径/缺原片/不同完整冷启/历史33门禁保持和新增35门禁）。这不是迁移轮旧437/50回执。
- 新应用与仪器DEBUG构建成功；Python、Bash、PowerShell解析及git diff检查通过。DEBUG不是正式签名或实际App通过。
- 新开工原生产preflight于2026-10-07T18:18:15Z查询，NO_ISSUES_OBSERVED/errors空/cleanup0，3939事件/2会话；只覆盖上传样本，不当候选手机验收。
- 首次DEBUG内容验包实际被拒：迁入的被忽略assets仍为c61/37f0，而项目pin为c62/625a。原错误和失败包保存在 `/srv/fengshen-dev/receipts/visual-local-package-failure.json` 与 `.local-ai/visual-reviewed-inputs/before-c62-restore-debug.apk`。不是改pin接受旧内容，也未运行该错误包。
- 按原流程实际下载已发布v90全部32,806,652字节，SHA `2a09da9432e3794f398020a7f427664110403a095d9fe55fe76932981f926003`，原包名/签名/版本/c62内容通过。原c61资产完整备份 `.local-ai/assets-before-visual-c62-restore` 后，原ci_apk.restore恢复可信c62；失败记录不改成通过。恢复结果在 `visual-c62-input-restored.json`；重建新DEBUG包后，392份c62及13份视觉文件已完整验包通过，60,777,905字节/SHA `83d6f96e52e745e9b7c40a5d0e607891c5c9724c5383fc6da526e1b921e7d289`，仅DEBUG环境验证。

最新实际本地包结果和新审查快照保存 `/srv/fengshen-dev/receipts/visual-local-reviewed.json` 与 `.local-ai/state.sqlite3`；源码变化必须重新人工完整审查、执行相关检查并codex_only登记，不能复用迁移回执。

## 原Actions必要App验收与接续

原21 Content与33个人门禁完整保持，增加实际Android素材hash/缓存/坏图拒绝门禁和正常新游戏用药/战斗后完整外部冷启门禁，共35。旧scope/golden不倒改；实际新候选91不得自洽重hash后删除视觉要求。原签名验包额外核完整视觉清单和全部PNG字节，review入口重算原片/完整冷启/日志证明，缺失或失败拒绝。

正常证据 `testNormalVisualSupplyAttackVictoryAndSave` 复用原 normalTownShops 用药触控路线，并要求至少一次正常攻击胜利/EXP奖励；不允许只靠逃跑取伤代替攻击/奖励。通过原NPC/赠物/商店和自然遭遇，再用实际药草，保存/外部force-stop/实际GameView恢复和继续；不得注入HP、角色、物品、RNG或旗标。原正常房间28谈话/调查/一次药草/返程/cold单列另一正常路线，不冒称这两个独立录制是一段连续剧情。

合法治疗法术、四人/六敌/Boss与1/1.3/2字体继续原受控证明，明确CONTROLLED，不借测试开放入队或习得。原小龙女提神术/解毒术的真实规则、目标先死亡/取消/重复/MP阶段与完整保存不改。实际App截图/原录像需目视；一加13T、声音、正常完整主线与真实账号恢复仍PENDING/NOT_RUN。

当前下一动作：新codex_only回执→冻结同源main候选→原Actions签名/覆盖v90/35门禁→实际读取原图/原片/完整状态与APK字节→只有通过才原批准流程两对象发布、公网完整字节与postflight。正式构建失败、App失败或外部平台缺口要保存具体结果并继续独立工作，不能称发布成功或关闭长期目标。
