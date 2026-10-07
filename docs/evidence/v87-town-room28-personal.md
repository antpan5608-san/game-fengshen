# v87 陈塘村房间个人版实际交付

2026-10-07；PUBLISHED_AND_VERIFIED，PERSONAL_TEST，manual_acceptance=PENDING。历史稳定版仍 v82。

陈塘村原来提示开发边界的小房间现在按原门进入和返回。村民提供只读粮仓线索；隐藏药草首次调查取得一次，取消、重复点击、返程再入和外部冷启动不重复赠送。继续使用原 World、碰撞、物品、保存和加载器，没有另建地图或存档系统。线索的 Unicode 转录仍保留 PROVISIONAL。

| 项目 | 实际值 |
|---|---|
| 版本 | 87 / 0.8.17-town-room28-personal |
| 冻结 APK 来源 | af4f1d546da0ff5d88cf428f00a254140862a32a |
| 原构建 | [37587677750](https://github.com/antpan5608-san/game-fengshen/actions/runs/37587677750)，SUCCESS |
| 原发布 | [37590470949](https://github.com/antpan5608-san/game-fengshen/actions/runs/37590470949)，SUCCESS |
| APK 全字节 | 32,780,784 |
| APK SHA-256 | d4b1d24e6b0e58a03c30b10f8ff1df94acd0d10be2be64e998aae940a6fd68ed |
| 包名 | org.fengshen.dev |
| 签名 SHA-256 | 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6 |
| 内容 | opening-segment-001-c62；392 文件、73 依赖图，非可玩地图分母 |
| Manifest SHA-256 | 625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef |

[原安装包入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=87)。沿用同签名覆盖升级，保留玩家进度。

原版输入已实际核房间 28 入口、FE 返回、村民只读对白和一次性隐藏药草；350 个原 CPU 入口、碰撞、对白和取得案例通过。此前 DEBUG 局部路线通过后，本次重新运行正式签名候选，不能用 DEBUG 或旧候选代替。

同一正式候选实际通过 418 release JVM / 91 suites（零失败、错误、跳过）、21 Content 和全部 29 个人门禁（原 26 加房间 3）。正常新游戏触摸进入房间、只读线索、取消及首次药草、重复 UP、原门返程、室内保存和再入通过；这段局部正常路线不是新游戏至结局。外部 force-stop 后完整 snapshot、索引 expected、before/after 等值，再入不重赠，原偏好恢复。

真实 v86/c61 正常赠刀存档覆盖到 c62 后，位置、角色、金钱、库存、旗和步数完整保留，首份迁移前 raw 备份保留。随后两次明确标记的 controlled c60/c51 版本字段迁移继续保护首份备份。生产兼容只新增已发布 c61 到精确 c62，不放开未知未来版本或回退。

原 87 战斗 PNG、7 房间 PNG、6 尺寸 JSON、11 仪器日志和 8 MP4 全字节独立核验；四组 cold 完整等值。当前原始 proof SHA-256：

- Battle UI：31d47aaa0c0efc42b66ea80d5fc3009f8ac9d7e8844e653c0a935cd18f6be7cb。
- Jiang recorder：de98f8b614d832080d601b518c5c0228c5973de0f061e59b20363228b8da1714；cold：80373ddfcc910d8eb0b3fa866abd0a385acfa936b2eb522dcf044e157e2b90d0。
- Room recorder：98e15aa8f2c9786eb76e2b151ce800442a6469d837abcacfe95578e54be68095；cold：614edc796e69d334c814066daa0258757b720110276db30cae28ee3501b04a1d；UI：8b4aad5bdc17cab875930e17630c152b28e5b47689baab8febc965c698365c2f。

实际目视本候选全部 7 房间图、三字体四人/六敌 6 图、2 倍字胜利图、后续恢复图和全部 8 原片末帧。早期 room cold PNG 未绘制 HUD，后续恢复/再调查有 HUD 和控件。Personal-r1、history、Jiang 首段末帧是 Android 桌面，room 首段和四段 cold 末帧是 App；没有声称逐段全程播放。放大原始像素复核，六敌三字体实例编号清楚且无重叠；先前缩略图误判已纠正。2 倍字体敌图偏小，保留界面欠账；几何 PASS 不代表所有文字可读性已完成。

真实失败保留：a04/run37581950501 在实际 c61→c62 升级失败，根因历史兼容列表漏 c61；有界修复 b8/run37583981445 后全部 29 门禁通过。首次 publish37586776216 在依赖安装前执行 scope 查询，因新 room 模块顶层导入 PIL 而失败，未上传。af4 将图像导入限于实际 proof 校验，真实无 site-packages 查询通过、缺 Pillow 的图片校验仍拒绝；20 项回归和新 4B 两片段审查/实际决策后重新构建及完整验收，未复用改变来源后的旧 APK。

公网重新下载全部 32,780,784 字节并执行原 ci_apk.verify：版本、包名、签名、非 debug、内容全部通过。原 postflight 2026-10-07T07:57:37.9702317Z 为 NO_ISSUES_OBSERVED，errors 空、cleanupFailures=0，权威保留 87/86；16 事件来自已上报的一个 v86 会话，不能证明 v87 真机健康。只写原两个 Fengshen 发布对象，保持同源、同审核 APK/hash、reviewer 和存档安全。

只关闭房间 28 本批进入/交互/保存恢复和已验证升级缺口。正常主线至结局、全部角色/装备/物品及小窗口、导航、法术、真机、声音、真实云恢复/内容更新和十类累计欠账仍开放。总体 PARTIAL，ALL_MAPS_USABLE=NO，分母 UNKNOWN。唯一执行者接续原版法术取证及上述具体界面问题。
