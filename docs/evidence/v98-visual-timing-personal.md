# v98 首战准备时序：实际个人交付

PUBLISHED_AND_VERIFIED_PERSONAL；98 / `0.8.28-visual-timing-personal`，quality=PERSONAL_TEST、manual_acceptance=PENDING。稳定82、c62、WORLD-FULL-01 IN_PROGRESS/PARTIAL及所有长期未完成项保持。

游戏source `499119c9168758ee3df44b94a396f4f5398c118f`；原[build37736087805](https://github.com/antpan5608-san/game-fengshen/actions/runs/37736087805)首次build/runtime SUCCESS；原[publish37739270989](https://github.com/antpan5608-san/game-fengshen/actions/runs/37739270989)首次approve/publish SUCCESS。06:45:32Z发布，06:46:14Z独立公网完整62,617,124字节与同审核签名APK相等，SHA256 `fe0630eff3998829cbd8566da7277d3675238cc7d1ca2aacfcf1c54a5a9d38f8`。原包名org.fengshen.dev、证书5c460557、非DEBUG，c62清单625a314a/392文件、视觉r2清单8dc53b77/17文件，409素材逐项同97。唯一成功job artifact的source/run/time/full ZIP digest与安全路径独立核验；current98/previous97，仅原两个Fengshen对象。[个人测试下载](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=98)。后续基线/文档提交不改变审核APK来源。

只为原单contentWorker当前战斗请求增加单调时钟排队、准备、UI交付与真实Surface首次送帧观测。离线Canvas和未完成场景不能冒称送帧；owner/epoch/当前battle/销毁拒绝保持。复用ContentLoader、统一绘制/BattleActionStep/Diagnostics既有白名单。无新素材或法术、奖励、加入、存档规则；新协议CURRENT_BATTLE_MONOTONIC_TO_POST_V1拒绝旧97缺时序的raw。

本机新462 JVM/97 suites零失败错误跳过、59相关Python（46＋13；原嵌套审核32场景另列）及DEBUG两包通过。原正式签名XML新462 release JVM/97 suites零失败错误跳过、实际build日志16组132 Python/16OK；原21Content/36raw服务器重新执行通过。真实看121原PNG/31页、95视频采样（69基础＋26正常首战）/25页，另2正常PNG及4原尺寸复核；14原片完整SHA、7完整cold全状态等值和7prefs恢复通过。未全程播放、未验声音。48身体/87反馈行与97实际几何相等；三字体各1089投影。

| 实际隔离App请求 | 排队ms | 准备ms | UI交付ms | Ready ms | Post延迟ms | 首送帧ms |
|---|---:|---:|---:|---:|---:|---:|
| 正常新游戏首次自然战斗，1样本 | 0 | 663 | 0 | 663 | 41 | 704 |
| 受控四人，字体1 | 322 | 0 | 1 | 323 | 69 | 392 |
| 受控四人，字体1.3 | 293 | 3 | 24 | 320 | 69 | 389 |
| 受控四人，字体2 | 381 | 0 | 0 | 381 | 61 | 442 |

仅该source原AVD实际样本，不是统计性能或13T体验验收。正常32.65/32.80/32.95/33.10秒采样有原背景/头像回退，33.25秒准备插画可见；不能据一个样本定位具体解码文件或声称改善。现阶段不盲目全量启动解码；局部预备方案仍需进一步真实原因与队列守卫验证。

正常真正新游戏/no grants：一次自然甲虫胜利EXP+2/银+1，药草HP19→20/20，最终外部cold HP20/20、EXP2/12、银6、原药草rom.medicine.0数量0，完整save等值。受控四人/Boss另列：治疗HP5→58、MP44→41，解毒后MP38，真实姿态/目标局部光效；不当正常加入或完整主线。缺插画敌图仍用原native黑底，大字详情原滚动保持。

06:46:54Z Linux postflight查询完成：NO_DATA/0事件0实机、errors空/cleanup0，权威98/97，存储计数空，不能当手机健康或声音通过。一加13T、完整正常原结局、真实账号多设备云恢复、完整视觉/声音/所有已批方案和累计欠账仍OPEN。

原证据`.local-ai/visual-actions-37736087805`、`.local-ai/visual-publish-37739270989`；脱敏服务器receipts/visual-timing-*.json及原日志。发布后仅四字段runtimeBaseline更新98，并新执行35项APK/handoff测试通过；完整实际审查的新codex_only `34ef7185972b521b3c915af97244db9e7df0773c6b7dd66ee27641a62a8e795c`、Stop current。所有旧失败和迁入工具dirty保留，不提交工具遗留。主动接续[紧凑多敌短条](battle-compact-gauge-next-batch.md)，不关闭BATTLE-VISUAL-02或长期终点。
