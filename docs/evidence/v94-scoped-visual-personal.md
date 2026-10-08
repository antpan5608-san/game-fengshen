# v94 按当前战斗准备图像：实际个人交付

状态 PUBLISHED_AND_VERIFIED_PERSONAL；quality=PERSONAL_TEST、manual_acceptance=PENDING，稳定v82不变，WORLD-FULL-01仍IN_PROGRESS/PARTIAL。只改变既有视觉准备与交付，不改变玩法、法术、奖励、加入或存档。

唯一游戏来源 `cbabdddd60e736898c86087f55843f3b9db3eff2`，原构建[37702699467](https://github.com/antpan5608-san/game-fengshen/actions/runs/37702699467)与原批准发布[37705985271](https://github.com/antpan5608-san/game-fengshen/actions/runs/37705985271)实际SUCCESS。版本94 / 0.8.24-scoped-visual-personal，62,603,864字节，SHA256 `7975cd102139d509f098341a0767d0036a804ca09c59d9702d13281135297a9c`。原包名org.fengshen.dev/证书5c460557保持、非debug；2026-10-08T00:11:11Z独立公网下载逐byte等于审核签名APK，出版回执绑定同source/build/hash与前版93。只有原两个Fengshen对象；下载[个人测试APK](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=94)。发布后文档HEAD不改变实际APK来源。

复用ContentLoader及原development-content-loader单队列，启动准备四头像，按真实party/enemy ID及已审map/blackScene映射准备当前战斗。不可变bundle供统一绘制/命中使用，Source/缓存/解码仅worker；随机和story两入口绑定GameView、battle对象/UUID与epoch，退出/旧战斗/销毁owner的结果丢弃，未完成结果仍走原native回退。显式完整16图取证入口保留，失败不缓存可重试，不recycle帧仍引用的bitmap。

新正式450 release JVM/96 suites零失败错误跳过，原16 Python组实际报告122；本机450/96及最终61相关Python分别真实，最终仅仪器改动时JVM为UP-TO-DATE，不重复计数。原Android21 Content与36个人门禁成功，本机runtime_handoff review重算完整raw/digest实际exit0。三字体startup4/current12、18,864,036 decoded bytes、旧epoch/不同battle/退出/销毁owner拒绝及完整state/RNG不变真实仪器通过；这不是进程峰值、真机内存或60fps实测。

实际查看121必需PNG全部31页、其中9张原大图重复核查、74实际decoded帧19页；不是全程播放/手机/声音。正常新游戏交互/赠物/补给/自然遭遇攻击胜利，36.64–38秒EXP+3、银两+2，39秒HP17/20，40秒已使用药草HP20/20、银两7，之后保存/外部冷启并继续HP20、EXP3。受控四人/法术/Boss单列，提神术HP5→58、MP44→41，解毒后MP38；不当正常加入或主线完成。15原MP4完整SHA、7完整cold前后全状态等值及7隔离偏好恢复独立核验。

实际比较94与93的392份c62和17份visual共409文件逐字节相同；c62 manifest625a314a、视觉r2清单8dc53b77及16图来源/授权保持。本批未增新素材；其余敌图/图集/环境覆盖缺口仍开放。

本地QA helper旧14段假设失败：本次实际首normal分为00/01，七录制共15段。保留失败脚本/日志，改为核原录制索引的有界分段并逐段重算完整SHA，不改原片或回执。紧接采样缺少该派生回执也失败，完成修正后实际74帧取样和目视通过。接续状态脚本错用candidate94键、日志查看错用简名的错误亦未改变游戏/原证据；按真实路径/结构更正。

prepublish Linux preflight 2026-10-08T00:04:28Z、postflight00:11:17Z都查询成功但NO_DATA，正常事件/会话/实机0，errors空/cleanup0，最新权威94/93。服务器ADB实际无设备；一加13T、帧耗时/内存、声音、人工长体验、正常完整原版结局、真实账号多设备恢复均未验。

服务器原证据 `.local-ai/visual-actions-37702699467`；脱敏派生回执 `/srv/fengshen-dev/receipts/battle-scoped-load-{app-reviewed,actual-image-review,video-cold-verified,public-full-byte-verified,postflight}.json`，原失败/迁入dirty/旧历史全部保留。新codex_only快照d68ac7fa真实覆盖全文源码、新正式测试与App图/片，Stopcurrent；发布后覆盖基底仅更新实际94四字段，另做相关检查并新记回执。

下一批主动接[四人身体间距](battle-party-spacing-next-batch.md)。当前四body重叠和2倍字场地小、其他视觉/法术A/B/C/寻路/全世界及十类累计欠账仍OPEN；不写长期完成或暂停文件。
