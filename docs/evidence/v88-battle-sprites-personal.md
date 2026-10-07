# v88 大字紧凑战斗图形个人版实际交付

2026-10-07；PUBLISHED_AND_VERIFIED，PERSONAL_TEST，manual_acceptance=PENDING。历史稳定版仍 v82。

2 倍字体、三至四人紧凑敌人行中，原 32×48 图形曾把 1.875 倍适配空间取整为1，只显示48像素高。本版使用该行原有90像素空间，保留最近邻、原图、比例和独立命中单元。实际原像素前后截图已查看，图形明显扩大；其他字体、非紧凑行及其余整数放大分支保留。只关闭这处有限图形问题，不代表全部界面已完成。

| 项目 | 实际值 |
|---|---|
| 版本 | 88 / 0.8.18-battle-sprites-personal |
| 冻结 APK 来源 | 480d376fe312c055bbd5079b8bbd5674e77d72f9 |
| 原构建 | [37595383488](https://github.com/antpan5608-san/game-fengshen/actions/runs/37595383488)，SUCCESS |
| 原发布 | [37599015986](https://github.com/antpan5608-san/game-fengshen/actions/runs/37599015986)，SUCCESS |
| APK 全字节 | 32,780,824 |
| APK SHA-256 | 6090de31b43e6d5ff188f0625f91d8e6a7d6df24e571b1c782d1050484c6c8a2 |
| 包名 | org.fengshen.dev |
| 签名 SHA-256 | 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6 |
| 内容 | opening-segment-001-c62；392 文件、73 依赖图，非可玩分母 |
| Manifest SHA-256 | 625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef |

[原安装包入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=88)。沿用同签名覆盖升级保留进度。

正式同源候选实际通过 418 release JVM / 91 suites（零失败、错误、跳过）、21 Content 和全部29个人门禁。基线换为已发布真实v87，同c62覆盖后完整赠刀旧档一致、不伪造迁移备份；后续两个明确标记的controlled c60/c51迁移保护首份raw备份。c61→c62生产兼容及原回退/未知版本拒绝仍保留。

原87战斗PNG、7房间PNG、6尺寸JSON、11仪器日志、8段MP4完整字节与四组cold before/after完整等值独立核验，偏好恢复。实际查看本包三字体四人六敌图、7房间图、8原片末帧和2倍字原像素前后对比。Personal/history/Jiang首段末帧为桌面，room首段及四段cold为App；未全程播放或验声音。本次正常房间路线HP11，与旧v87HP15不同，按本次完整保存状态比较，没有借用旧值。早期room cold图无HUD，后续再入图及cold末帧控件可见。

当前proof SHA-256：

- Battle UI：792f72baa94f106ee22f4ca1d7c08c30d7e475487427c4eae23c3980ea89209a。
- Jiang recorder：c2625b00822cab52abfe8b6b7832eeca59991762402bbd6cfc27ba589da4fa05；cold：80373ddfcc910d8eb0b3fa866abd0a385acfa936b2eb522dcf044e157e2b90d0。
- Room recorder：b99f01a827f5fe080f22d0f31ff7f9591b8d1ba2cf1951fd715fbd592f3e2b6c；cold：825b13c7e6dc65d6cfae56e746bac27ec694f67e646db0bb13ed33026a1eb2ac；UI：04cdb940474538459d48278192ada71a641e3ce4b2ba58efae56872c810edf65。

本地Debug418JVM/91 suites和仪器编译2m45s、16原scope/raw proof回归51.586s通过。最初release调用缺原签名key、版本参数未引号及错误测试模块名保留为本地调用失败，纠正后以Debug检查；正式release418由原CI完成，没有修改签名要求。4B 2全新片段27.594s实际读完并登记reviewed；拒绝把旧缩小分支、缺图默认或更新基线当新故障，以及改成线性插值。

公网重下载完整32,780,824字节，原ci_apk.verify独立核包名、签名、版本、非debug和内容通过。实际postflight 2026-10-07T09:13:56.7748911Z为**NO_DATA**：0事件、0会话，errors空、cleanupFailures=0，权威保留88/87。旧v86日志按原两版保留规则清理；当前没有v87/v88实机样本，健康UNKNOWN，不能写成实机无故障或NO_ISSUES_OBSERVED。仅原两个Fengshen发布对象，同源/同审核APK/hash/reviewer/存档保护保持。

继续[法术原版基础取证](magic-original-foundation.md)。目录和执行尚未开放，初始原生MP/目标验证仍在推进；完整主线、全部角色/装备/物品及小窗口、导航、真机/声音、云恢复/内容更新和十类累计欠账仍未完成。总体PARTIAL，ALL_MAPS_USABLE=NO，分母UNKNOWN。
