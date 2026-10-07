# v90：战斗提神术与解毒术个人版

PUBLISHED_AND_VERIFIED，2026-10-08北京时间；quality=PERSONAL_TEST，manual_acceptance=PENDING。仅关闭本批小龙女两项初始战斗法术接入/同候选触控与存档验证，MAGIC-ORIGINAL-01 A/B/C、整体参考重制体验、完整世界、正常结局和累计欠账继续 OPEN。

| 项目 | 实际值 |
|---|---|
| 版本 | 90 / 0.8.20-battle-magic-personal |
| APK 来源 | 732440a997664ced63f3f0ecf2023be5a1b29e23 |
| 原构建 / 发布 | 37648979694 / 37655007489，均 SUCCESS |
| 完整 APK | 32,806,652 字节 |
| SHA256 | 2a09da9432e3794f398020a7f427664110403a095d9fe55fe76932981f926003 |
| 包名 / 签名 | org.fengshen.dev / 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6 |
| 内容 | opening-segment-001-c62 / 392 文件 |
| 内容清单 SHA | 625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef |
| 实际 publishedAt | 2026-10-07T16:50:21.8381063Z，即北京时间10月8日00:50 |
| 下载 | [原 Fengshen 下载入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=90) |

## 行为与原版边界

战斗轮到小龙女输入时，法术列表开放已习得提神术（等级1）与解毒术（等级10），动作→真实队友目标→明确确认。每项3MP，浏览/滚动/取消不扣费或采RNG；原队伍收集完成后，沿原敏捷调度在施法者行动格执行。提神术按固定施法者身份等级恢复并截断上限，不复活；解毒仅解除exact状态2。原效果帧先变HP/状态，之后扣MP，再原post-HP状态处理；满血/无效果仍扣费。敌先击倒施法者则原调度跳过，目标先倒仍付费。自疗从更新后对象扣MP，不覆盖治疗。

复用 OpeningBattle、BattlePresentation、原物品模态和战后 SaveSnapshot 事务；无第二引擎/状态/RNG/存档。旧构造和旧帧MP兼容保持，地图提神术独立。杨戬/姜子牙的初始法术、通用法术伤害/RNG、控制和其他地图能力未开放，不能用一次受控伤害292样本推完整公式。

## 同候选真实验收

- 437 release JVM /94 suites，0失败/错误/跳过；21 Content 和33个人门禁通过，旧31保持。六项原CPU表及习得表逐字节对照，四人队列/初始MP/死目标/先死施法者/自疗/晚扣费边界通过。
- 本次实际v89/c62覆盖升级保留完整原存档、不制造内容迁移备份；受控c60/c51首次迁移备份检查通过，原不可恢复存档守卫保持。
- 原 raw digests 独立复算并匹配回执，含姜、room28、旧战斗UI、地图法术及新battle magic；13段原静音录像全文件SHA、6组完整外部force-stop冷启前后保存等值及偏好恢复通过。
- 新22张法术PNG全部实际查看：三字体1/1.3/2的列表/目标、治疗效果MP44→扣费41、解毒效果MP41→扣费38、胜利与cold。font2解毒术由真实滚动可见后选中；名字、目标HP、施法者MP和核心效果/费用清楚，其他条件继续详情滚动。
- 已实际查看13原片末帧及新法术原片4/7/10/13/16秒采样；未声称完整连续播放全部录像。个人首段末帧黑色过渡；后段App，部分正常fixture结束回Launcher；法术和全部cold末帧App。PNG不替代完整存档比较。
- 默认字体法术图为实际960×540，1.3/2图2640×1216；按各自真实viewport/报告核验，不推断同一真实手机。四人法术为明确隔离fixture，不是正常四人入队或通关证据。

正式 APK、发布回执、公网 version.json/完整APK逐字节相同，原 ci_apk 再核签名/版本/c62/392；只发布原两个Fengshen OSS对象。下一覆盖基底使用真实90，历史iteration内容导出基底27保持。

## 失败与审查保留

source7f011ab的首run37641724001打包成功但runtime未创建，GitHub实际Internal server error/correlation3ce665b7-74b6-4829-b956-e88f81db6c1b，failed retry被拒。第二run37644034300的font2隔离驱动未先滚动到第二项解毒术，实际selected为空，TouchTest:120 expected yangjian/actual xiaolongnv；不是已确认玩家法术故障，failed APK不发布。复用既有scrollToBattleItem并新增稳定选中ID及滚动不改队伍/MP/inputRevision/RNG/完整保存断言后，新source732/新同源run才通过。

初轮4B32全文、新最终6文本和28字节相同已读缓存审查均实际决定。修正4B snapshot451fb13093b9a36d772fb8c67fdb50117dcc4ee551dc6b23928efe251c042036，1全新15.735s全文已读且CLI reviewed；拒绝删除无早采样/提交断言、为隔离测试加生产API或使用不存在的TextMeasurements。修正本地仪器编译1m58s，JVM UP-TO-DATE保留之前437，不冒称该次本地重跑。新正式release437确已执行。

## 发布后巡检与继续范围

postflight2026-10-07T16:50:37.857701856Z（北京时间10月8日00:50），NO_ISSUES_OBSERVED/errors{}、cleanupFailures0，权威保留90/89。3864事件、1实机会话均89，90样本0；该上传窗口不是90真机验收。真机、一加13T60fps/内存、声音、账号多设备云恢复、正常完整主线和全部十类欠账仍待实际验证，稳定历史版82不变。

下一安全批次优先 [REMAKE-EXPERIENCE-01](../plans/remake-experience-01.md) 的正常可见交互→物品→战斗攻击/已实现治疗→奖励→保存外部cold流程，以及BATTLE-VISUAL-02场景/头像/斜列站位/动作。四人/Boss另验，不能等待全部26法术/全世界才先展示。自行制作四头像/四待机/四环境候选已私有保存；尚未接App或证明重制体验达标。长期目标保持ACTIVE，阶段发布后继续。
