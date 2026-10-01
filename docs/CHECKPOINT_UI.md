# 全屏场景与摇杆：可安装验证版 v3

2026-09-30。已按一加 13T 用户截图调整默认界面；新版本仍待该真机实际操作反馈。研究 `READY_FOR_PHASE_2 = NO` 与来源验证状态未改。

## 交付

- APK：`F:/apps/fengshen-remake/artifacts/checkpoint-ui/fengshen-fullscreen-v3-debug.apk`；`org.fengshen.dev`，versionCode 3，versionName `0.2.1-fullscreen-joystick`，2452515 字节，SHA-256 `4e33dc4ce560ecf5f74284729f94630c9ecc39f5000f15875d8f3f7cf180c784`。既有 development 签名与 A 版一致，升级检查通过。
- [已验证手机下载地址](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=3)：下载后去掉 `.bin` 改成 `.apk` 安装。公网完整下载哈希、大小及版本元数据逐字节验证成功。OSS 只覆盖 fengshen 固定 APK 与同目录 version.json；Language 内容未修改。
- 项目根目录执行 `./build-android.ps1`：导出已有 development 包、构建和测试、覆盖上传并校验。明确只做本地构建时 `./build-android.ps1 -LocalOnly`；已有本地 APK 可执行 `./publish-apk.ps1`。当前产品代码只更新 Android，未实现游戏数据的服务端下载器。
- 同场景模拟器截图：`artifacts/checkpoint-ui/emulator-fullscreen-v3.png`；菜单截图：`artifacts/checkpoint-ui/emulator-menu-v3.png`。实际模拟器屏幕录制：`artifacts/checkpoint-ui/emulator-walk-turn-menu-v3.mp4`，22.33 秒，含连续上移、同指转左、松手、开菜单、B 返回。测试驱动直接向运行中的 GameView 送真实 MotionEvent，World 在固定步进中移动；不是静态演示或合成动画。

## 运行范围与复用

现有 `ContentLoader.load()` / `AssetSource` 继续加载随包 development manifest、图块、四方向角色图和 scene.json；没有 Android ROM 解析或第二套 importer。Map114 的 32×30 metatile（世界 512×480）逐格绘制，图块来自当前已追溯的 ROM 提取包，不是截图背景。`GameView.render()` 使用同一 Canvas 世界矩阵画地图与角色；`World.camera()` 根据手机窗口得到的视野尺寸裁切并跟随。场景底图铺满全窗，不受系统手势安全边距缩小；控件按运行时 density/insets 落在安全区域。默认全屏最大等比放大、最近邻采样；原版比例与整数裁切作为设置可选，非整数放大不宣称严格像素完美。

摇杆扩展现有 `InputState`、`MotionEvent` 和 `World.tick()`：四向、24% 默认死区、近对角滞回、长按滑动换向、多指分别归属；每逻辑 tick 2 个世界像素，16 像素一格，逻辑 60 Hz 独立于刷新率。松手不启动下一格，已开始的一格完成；暂停/后台立即清触点并收齐该格。碰撞仍读 `Scene.check()` 的世界格子。34 格目前是实际可走**开发白名单**，不是原版全图可走结论；默认不再绘紫线/暗化区/常驻坐标，越界只短暂提示“试玩区域到此”。没有把未知区域开放或改成原版墙体。

默认主键 A 在地图打开菜单，在菜单确认；B 仅在菜单出现并返回；顶部小图标同样开关菜单。菜单提供“继续游戏”“设置”“开发信息/范围”，暂停世界输入。原有平台设置继续用于显示模式、震动、调试、摇杆与按钮 JSON 参数及恢复默认。没有 NPC 可完整互动，因此地图 A 不伪装成 NPC 对话键。原版比例/整数显示和调试均不改变世界移动/碰撞。

第二地图 raw `maps/16.json` 已有 256×181 格结构；本轮只对这个尺寸的摄像机边界与基础世界到屏幕计算加单测。该图尚未导入运行包，地图尺寸不作为手机逻辑分辨率，也没有新建第二套图集转换。

新增代码只在 `Core.kt` 的视口/摇杆/收步，以及 `MainActivity.kt` 的既有 SurfaceView/Canvas/输入/菜单内做薄适配。复用 Android SDK 35、Gradle 8.10.2、AGP 8.7.3、Kotlin 2.0.21、JUnit 4.13.2，无新运行依赖。原“三分栏并为控件留空”的方案已取消；未建新引擎、地图编辑器或服务端。正式 canonical 与 ROM 研究证据门槛未解锁。

## 实测与剩余

- Python 研究回归 77/77；Android JVM 13/13；API35 x86_64 模拟器设备测试 5/5；单独录屏回放测试 1/1。`./build-android.ps1 -SkipExport -LocalOnly` 构建 APK 成功。签名、包名、versionCode 2→3 升级兼容检查通过；OSS 公网完整文件及元数据校验成功。
- 一次新增设备测试发现菜单面板与 B 键触区重叠：看似“返回”会误开开发信息。已缩小面板并让悬浮按钮优先命中；修复后重跑 5 项全部通过，最终版本升至 v3。失败记录未被当成通过。v2 已被 v3 覆盖。
- 模拟器运行窗口 2340×1080、density 2.75，截图与录屏均来自模拟器。**一加 13T 新版尚未真机验收**；设备的正反横屏、挖孔、手势区、长时间手感要按 `ONEPLUS_13T_ACCEPTANCE.md` 反馈。
- NPC/对话、事件、出口、换图、战斗、升级、完整本地存档和内容服务下载尚未开放。当前可玩范围是 Map114 的受限移动与真实菜单；这些限制只影响相应功能，不是对原版规则的结论。

下一轮最小任务：①从“开始游戏”进入已验证开局状态；②完成一个有 ROM 实测证据的 NPC 全互动，包含必要首谈/复谈、赠物和 Flag，不截断事件；③最小本地保存/继续游戏，并记录内容版本。随后才接地图往返及原版回合制战斗，逐项交付可检查 APK。
