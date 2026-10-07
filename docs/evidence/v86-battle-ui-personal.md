# v86 战斗界面个人版实际交付

2026-10-07；PERSONAL_TEST，manual_acceptance=PENDING。历史稳定版仍 v82。

敌左我右、四人姓名和 HP/MP 常驻；默认先选指令再选目标，信息浏览不耗行动；当前行动显示、药品目标与核心说明、一次胜利奖励和可滚动详情分区。复用原规则、存档和绘制/命中几何。不能据此宣布全部角色、装备、物品、导航界面或所有设备完成。

| 项目 | 实际值 |
|---|---|
| 版本 | 86 / 0.8.16-battle-ui-personal |
| 冻结 APK 来源 | b13343b1e7ac820021d0f4fe66ebf6dca1e0903f |
| 原构建 | [37570999686](https://github.com/antpan5608-san/game-fengshen/actions/runs/37570999686)，SUCCESS |
| 原发布 | [37572999450](https://github.com/antpan5608-san/game-fengshen/actions/runs/37572999450)，SUCCESS |
| APK 全字节 | 32,513,189 |
| APK SHA-256 | a511d061563c034cc0202335bdb6f35158d3e1ba3f5a39ff10d5288a03db1a22 |
| 包名 | org.fengshen.dev |
| 签名 SHA-256 | 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6 |
| 内容 | opening-segment-001-c61；388 文件、72 依赖图，非可玩分母 |
| Manifest SHA-256 | 37f0f7bb1080f6fe59f3853928c7e5006c2974d6f3ca5698713b2a37f5747557 |

[原安装包入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=86)。沿用覆盖升级，保留玩家进度。

同一签名候选实际通过 415 release JVM / 90 suites（零失败、错误、跳过）、21 Content 与全部 26 个人门禁（原 18 加 UI 8）。真实 v85 同 c61 覆盖完整存档一致且未新建迁移备份；两次明确标记的 controlled c60/c51 版本字段迁移保留首份 raw 备份和完整当前/持久档，不是旧版完整主线。

三字体 1×/1.3×/2×、一至四人、六敌及原 Boss 矩阵和取消、手势、旧回调、药品、奖励、详情、物理键门禁通过。87 原 PNG、6 尺寸 JSON、11 仪器日志全部严格复核，UI proof SHA-256 为 41acb036e4f2768f7ce692feeb490c91198dd9a6465a9e731126b26957e560aa。实际目视本候选 18 张四人/六敌/Boss/药品/奖励及滚动图：姓名、HP/MP、实例编号和核心说明可读；2× 敌人图形较小，更多条件和奖励详情依赖滚动。

原六段 MP4 全字节 SHA、三组 cold before/after 完整等值、原偏好恢复独立通过。Jiang recorder SHA-256 为 359539f93e404f36b1bfb18b78a738cf56b2715d22aec3435e4fc6d2690441c3，cold-boundary 为 dddd784ae0e0303fc8037ed133c166b688d82a7cb55d5eca62492a1c203c63b3。实际查看六末帧及三段 4 秒帧，未声称逐段完整观看；正常 smoke 末帧是测试结束桌面，history 末帧是切换界面，Jiang 4 秒帧是桌面而末帧是实际对白。三个冷启动末帧均为 App。受控局部证据不能替代正常新游戏至结局。

首次仪器 run37538999494 对同内容无备份错误使用 `!!`，归类 TEST_HARNESS；保持生产同内容规则并修复测试后重建。旧来源 0e988 的 run37568180477 完整验收通过，但 publish37570497990 在上传凭据前缺 Pillow 而失败，归类 CI_DEPENDENCY。原发布准备增加与构建相同 Pillow==11.3.0 并保留失败保护，新隔离环境实际安装/原 PNG 校验与新 4B 审查后，以 b133 完整重建并重验；未复用变更来源后的旧 APK。

发布后再次公网下载全部字节，原 ci_apk.verify 独立核包名、签名、版本、非 debug 与全部内容，通过。实际 postflight 2026-10-07T04:46:56.1156322Z：NO_ISSUES_OBSERVED、errors 空、cleanupFailures=0，权威保留 86/85；16 事件来自此前 v85 一个已上报会话，不能作为 v86 真机健康证据。只写原两个 Fengshen 发布对象，原 reviewer/同源/同审核 APK/hash/存档保护保留。

下一批核实房间 28 正常进入、返程和原交互后最小接入；正常主线、完整角色/物品/导航、累计十类欠账、真机、声音与真实云恢复仍开放。总体 PARTIAL、ALL_MAPS_USABLE=NO、分母 UNKNOWN。私有原始取证保留于忽略目录，不进入公共 Git。
