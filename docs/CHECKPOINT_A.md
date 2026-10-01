# 检查点 A — 第一份 Android 操作验证 APK

日期：2026-09-30。**A已交付；等待OnePlus13T手机反馈。研究READY_FOR_PHASE_2仍NO，完整游戏未发布。**

当前发布规则补充：用户已授权每次 APK 更新覆盖上传到同一 OSS 平台的独立 `artifacts/fengshen-remake/app/` 目录，取代本页早期“APK不上传”限制。原 ROM/独立派生包仍不上传。此处保留此前交付记录，最新发布状态见文末。

## 交付与构建

- 本地APK：`F:/apps/fengshen-remake/artifacts/checkpoint-a/fengshen-operation-a-debug.apk`。
- applicationId：`org.fengshen.dev`；版本`0.1.0-operation-a`；Android API30+；Debug签名；无原生ABI限制。
- 大小：2,512,060字节；SHA-256：`54748e7c4d2f76f6f37a6fc24b309f3da167df3fb95eaebba937ac7848decbdf`。
- 不上传、不公开发布；ROM、数据包、APK、截图与随包素材均保持Git忽略。

从项目根目录PowerShell执行：

```powershell
./build-android.ps1
```

脚本复用现有Python环境导出本地development包，再执行Android单测、Debug APK与设备测试APK构建，复制交付APK并输出SHA-256。
仅重新构建已导出的内容可用`./build-android.ps1 -SkipExport`。工具链：JDK17、SDK35/build-tools35、Gradle8.10.2、AGP8.7.3、Kotlin2.0.21。
脚本优先使用已安装的Gradle8.10.2缓存；否则使用仓库Gradle Wrapper。直接wrapper在本机访问services.gradle.org出现过10秒超时，因此没有为了重新下载而放弃已有可用工具链。首次缺依赖时需网络；后续依赖缓存可复用。

## 实际开放范围

|部分|实现与边界|
|---|---|
|地图|真实Map114的32×30 metatile网格；每格16×16，图块图集逐格渲染；不是截图背景|
|画面|256×240逻辑缓冲、相机跟随、最近邻；整数方形像素或保持比例最大适配|
|玩家|独立玩家坐标；4个ROM图块/OAM实测方向姿态；无完整行走帧动画|
|移动|34个实际轨迹访问过的地面格；初始(8,21)，固定逻辑步长，不随呈现刷新率改变速度|
|碰撞|原版town class1阻挡；不在开放集合内是开发边界，明确分开显示|
|范围提示|未开放地面变暗，紫色边线为开发限制，不是原版墙体/光效|
|触控|D-pad长按与滑动换向，方向+A/B多触点，移出/CANCEL/失焦/后台清键，按下反馈|
|A/B|本轮只测试触控按下反馈，没有对话/战斗功能|
|START|开发暂停/恢复，不是原版菜单|
|MENU|独立现代设置，缩放、调试、可关闭震动、控件JSON参数、恢复默认、调试重置位置|
|生命周期|后台暂停，无墙钟补帧；返回输入为空。Android重建Bundle保存坐标、朝向及未走完步长；不是游戏存档系统|
|内容入口|ContentLoader统一接收AssetSource或DirectorySource；读取manifest并校验SHA-256和结构。APK只携带development包|

静态部分：背景地图、四个玩家姿态。交互部分：输入、玩家位置、相机、已启用碰撞、开发边界、设置与生命周期。
NPC、首谈/复谈、赠物、剧情Flag、对话预览、门遮挡交互、出口/换图、战斗、升级、完整存档、网络下载/Server均未开放。
最终完整复刻目标保留；这些是A的开发限制，不是对原版功能的描述。

## 数据及真实性

版本`map114-a1`，生成于`game-data/packages/development/map114-a1/`，复制至忽略的Android assets目录。
`tools/export_development.py`是现有研究成果的运行格式适配层，复用Reader、已提取网格和evidence validator，未重写ROM importer。
来源ROM SHA-256：`f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25`。

- 地图来源raw/rom/maps/114.json；原始ID114；grid SHA-256`c39ee79af3560c157b78747693b93d7914140f38037c5c218dbdbeb2c663e25f`。
- 图块来自ROM CHR230/231，metatile/palette属性来自已提取表；RGBA颜色采用FCEUX.pal近似，记录palette hash，不宣称电视硬件色彩已验收。
- 玩家姿态来自slice-probe帧2280/600/2080/2440的OAM和PPU；每段CHR匹配回ROM绝对offset/hash。仅补足本轮“显示方向玩家”的资源转换。
- 开放集合来自slice-probe/world-probe实际trajectory中map114、y16..25且collision class0的格子，34格连通。未用Reference扩充。
- scene.json包含来源、原始ID、版本、资源哈希、验证范围和限制；manifest覆盖每个运行文件。Android不解析ROM。
- 正式canonical继续BLOCKED_UNVERIFIED；canonical-candidate未被Android消费；未更改UNKNOWN或完整Reference等价统计。

## 测试和运行证据

|检查|实际结果|
|---|---|
|开始时基线|原有73项全部通过|
|交付前Python全回归|77/77通过，原73项保留，新增4项development导出检查；0跳过|
|Android Kotlin/JVM|11/11通过：数据形状、坐标/相机、碰撞/开发边界、输入、多尺寸/insets布局、60/90/120Hz逻辑、取消与中途步长恢复|
|Android设备测试|API35 x86_64模拟器4/4通过：内置与目录相同读取、坏hash拒绝、路径拒绝、多指/滑动/移出/CANCEL/后台清键|
|构建|Debug APK及androidTest APK成功；apksigner验证通过|
|正常启动/移动|APK实际安装；(8,21)长按上、左到(4,16)，原版墙阻挡提示出现|
|后台恢复|Home后回到Activity，移动/碰撞画面与恢复截图逐像素完全相同|
|真实OnePlus13T|未连接、未安装验收，不以模拟器冒充|

日志：private-derived/checkpoint-a-baseline-tests.log、checkpoint-a-regression.log、android-build-final.log、android-instrumentation.log。
首轮模拟器检测出过窗口decor建立前读取InsetsController导致启动崩溃，已修复初始化顺序，并完整重跑4项设备测试；没有隐藏失败或降低断言。
模拟器WHPX，API35 default x86_64，Pixel5设备配置；实际窗口2340×1080，density2.75，safe insets(136,66,132,0)，逻辑视口1024×960、4倍整数，约60Hz。这些不是OnePlus13T指标。

本地运行截图（均为Android模拟器，不是真机）：

- `artifacts/checkpoint-a/emulator-initial.png`：初始场景。
- `artifacts/checkpoint-a/emulator-movement-collision.png`：移动与原版碰撞。
- `artifacts/checkpoint-a/emulator-resumed.png`：后台恢复。
- `artifacts/checkpoint-a/emulator-device-info.png`：真实运行时设备信息。

## UI落实情况

布局读取WindowMetrics、density和[WindowInsets](https://developer.android.com/reference/android/view/WindowInsets)；合并systemBars/displayCutout/systemGestures安全区域，不按手机型号硬编码。系统栏与挖孔处理参照[Android官方说明](https://developer.android.com/develop/ui/views/layout/display-cutout)。
左右控制区不覆盖游戏逻辑视口，Start/Menu位于下方独立区域。可切换控件边界与设备调试；JSON参数可配置相对XY、尺寸和透明度，并本地保存和恢复默认。
本轮设置采用平台Dialog，避免为少量设置引入大型UI框架；游戏地图保留像素风格。
待实现：可视化拖拽编辑器、电视比例校正、完整原版Game UI、行走动画、设备高刷新率/长时间舒适性实测、所有OnePlus13T验收项。非整数FIT不称像素完美。

## 下一检查点实际阻塞（最多3项）

1. 等待OnePlus13T安装和触控/挖孔/缩放反馈，这是用户要求的A后停止点。
2. B尚未实现manifest/下载/校验缓存回退/安全激活；读取接口已具备，无需为了B先恢复全地图。
3. C所需NPC事件、往返与战斗/存档语义仍按原证据逐项补齐；不阻塞已交付A。

完成A后停止，未同时启动B或C。

## 补充执行：可验证的复用（2026-09-30）

已将用户补充规则合并至根 AGENTS.md，并在本会话重新读取。此次只调整已有 A 的导出适配，不新增计划、审计阶段或依赖。

- **ADAPT**：`tools/export_development.py:export()` 继续复用 `fengshen246.Reader`、已提取 `raw/rom/maps/114.json` 和 `validator.validate_original_artifacts()`；删除自身的 NES 双位平面解码，改用 `tools/forensics/rom.py:tile_image()`。本层只保留运行包组装、调色板映射、透明度和 OAM 翻转。Pillow 沿用 11.3.0。
- **REUSE**：实际查看 `Content.kt` 的 `ContentLoader.load()` / `AssetSource` / `DirectorySource`，以及 `Core.kt` 的 `World` / `Scene.check()` / `InputState` / `layout()`。已有统一读取、移动碰撞和输入布局满足 A，本次没有第二套实现。`MainActivity.kt:GameView` 沿用 Android SDK 35 的 SurfaceView/Canvas/MotionEvent；最低 API30。
- 构建沿用 `build-android.ps1`、Gradle 8.10.2、AGP 8.7.3、Kotlin 2.0.21、JUnit 4.13.2。没有发现需新增外部依赖的具体缺口，未开展候选搜索，也不宣称做过外部方案或维护状态全面调查。
- 实际重导出后，6 个运行文件及 manifest 字节哈希完全一致，34 格开放范围未变；原资源测试补入 5 张图像的改动前 golden SHA-256，测试总数不增加。`./phase1.ps1 test` 完整回归 **77/77**，0 跳过；`./build-android.ps1 -SkipExport` 构建成功；另外以 `:app:testDebugUnitTest --rerun-tasks` 实际重跑 JVM 测试 **11/11**。日志为 `private-derived/checkpoint-a-reuse-regression.log`、`android-build-reuse.log`、`android-unit-reuse.log`。
- APK SHA-256 仍为本页交付值。本次未修改 Android 运行代码且资源完全一致，未重复模拟器运行；上方 4 项设备测试和截图属于此前 A 验收。OnePlus13T 仍待真机反馈。
- 因复用而缩小：开发导出器不再维护独立 CHR 解码，只做索引图到运行素材的薄适配；没有新增通用资源框架、提取器或校验器。A/B/C 范围与原版证据门槛保持原决策，停在 A。

## APK 固定地址覆盖发布（2026-09-30）

用户授权已记录并重新读取根 AGENTS.md。`build-android.ps1` 现在默认在构建成功后调用 `publish-apk.ps1`；明确本地检查可用 `-LocalOnly`。已有 APK 直接运行 `./publish-apk.ps1`，无需重建。`-CheckOnly` 仅验证本地 APK，不宣称上传。

固定对象：`oss://kubernetes-fleetpilot/artifacts/fengshen-remake/app/fengshen-remake.apk.bin`；匹配元数据为同目录 `version.json`。与 Language 一致使用 application/octet-stream 和 .bin 对象，下载后去掉末尾 .bin 再安装；设置 no-cache，链接附 `?v=<versionCode>`。每次覆盖这两个对象，历史只保留本地 current/previous；实际字节改变必须递增版本号。上传后完整下载一次验证 SHA-256/大小，再写入并验证元数据。

复用 Language `tools/publish_apk_with_remote_oss_env.mjs`（ssh2 1.17.0）及 `scripts/check-upgrade-compat.ps1`，不修改其文件。上传脚本根据 Language `publish-minor-apk.ps1` 作路径隔离的薄适配，调用现有 ossutil 2.4.0；原脚本写死 Language 路径，因此不能直接执行。无需修改 VM 版本接口、服务、数据库、Nginx 或 Language 的 OSS 对象；不实现应用内更新器，也不启动 B/C。

凭据从当前进程 ALIYUN_* 或既有服务器环境加载器获得。若本机未配置 SSH 凭据，在项目根目录交互执行 `./configure-publish.ps1`；Windows PSCredential/DPAPI 加密保存到已忽略的 `private-inputs/oss-ssh.clixml`。后续自动加载并仅通过子进程环境传递；不保存明文 OSS 密钥。只读检查 Language 工程及其记录的备份位置未找到现成 SSH 登录凭据，不借用 Android 签名秘密。

本次实测：`./publish-apk.ps1 -CheckOnly` 通过；`./tests/test_publish.ps1` 的 4 项发布门禁检查通过（包/目标、无凭据拒绝、错误平台拒绝、错误APK拒绝）；`./build-android.ps1 -SkipExport -LocalOnly` 构建通过，APK哈希未变；3个 Language 复用脚本哈希未变。Android运行代码未修改，未重跑模拟器或宣称真机验收。

**当前状态：PUBLISHED_AND_VERIFIED。** 2026-09-30 01:15（Asia/Shanghai）完成固定对象覆盖上传，完整公网下载 SHA-256/大小与本页本地交付值一致；version.json 上传后也逐字节校验通过。版本 `0.1.0-operation-a` / code 1，2,512,060 字节。本地回执与已发布 APK 保存在 `artifacts/published/current.json` / `current.apk`。

[手机下载](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=1)：下载后重命名为 `fengshen-remake.apk` 安装。当前 A 仍为既有 development 签名，未冒充正式 Release；首次发布无上一在线版本，旧到新版本的升级兼容检查留待下一次实际升级执行。OnePlus13T 未真机验证。

实际修正：最初误用网站域名连接 SSH，现按 Language 发布脚本及 known_hosts 使用 `204.44.123.101:10080`；没有变更 Language。最初附加 `.apk` Content-Disposition 导致 OSS `ApkDownloadForbidden`，移除该额外属性、沿用 Language 的二进制下载方式后完成校验。OSS 默认域名限制参见[官方说明](https://help.aliyun.com/zh/oss/what-to-do-with-apkdownloadforbidden-reporting-an-error)。失败未当成成功，也未在失败时发布版本元数据。
