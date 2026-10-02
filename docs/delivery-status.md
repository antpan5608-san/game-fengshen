DELIVERY_REPORT

task_id: WORLD-01  
status: READY_FOR_REVIEW

【版本】  
APK版本与versionCode: `0.7.2-world-01` / 13，`org.fengshen.dev`  
构建标识: `v13/694f5ffecf0e`（APK SHA-256 前缀）  
APK本地路径: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\fengshen-world-01-v13-debug.apk`  
实际可用下载地址: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=13 （应用内更新按钮可下载并交系统安装；手工下载需保存为 `.apk`）  
APK SHA-256: `694f5ffecf0ed83601ff8e0410bb5727bd33cb4de9429a7ed091469c0b8ab690`；2,726,909 字节  
运行时内容版本: `opening-segment-001-c6`  
运行时内容来源: APK 内 `development/manifest.json`，`AssetSource → ContentLoader` SHA-256 校验；当前无外部内容下载缓存  
运行时内容hash: manifest SHA-256 `fce91e922603728cfd2eadd59ef5ca03dcc424f13cf5c842555ba36f2361f3f9`

【三个问题的根因】

试玩限制:  
  实际原因: 导出器仅把地图 16 的 13 个正常轨迹格放入 `enabledCells`；`Scene.check` 再按该集合拒绝其他格，画面另有固定“试玩区域”提示。地图 114 已按普通碰撞类别开放。  
  文件/函数/配置位置: `tools/export_development.py::export` 的 `world_observed`、`android/app/src/main/java/org/fengshen/dev/Core.kt::Scene.check`、`MainActivity.kt::render`。  
  修复方式: 16 和 0 的普通步行类别 0/2 按实际 ROM 网格开放；特殊入口只在明确出口格开放。保留地形、边界与动态 NPC 阻挡，提示改显示具体 `World.message`。

不能返回114:  
  实际原因: ROM 有 16→114 行，但 c4 包只导出 114→16；`ContentLoader` 又强制只接受该方向。并非原版单向入口。  
  文件/函数/配置位置: `tools/export_development.py` 的 `package.exits`、`Content.kt::ContentLoader.load` 出口校验、`Core.kt::World.tick`。  
  修复方式: 导出 ROM 反向行 `DE4C: CB 8E 72 08 1D`，正常按键回放核对地图/落点与落地向下朝向；统一出口执行先验证目标再提交。

只有两张地图:  
  实际原因: c4 导出器仅产生 scene.json/scene16.json 和两张图集；加载器固定读取地图 114、16。原 ROM 的地图 0 可用，并非缺图。  
  文件/函数/配置位置: `tools/export_development.py::export`、`Content.kt::ContentLoader.load` 的固定场景列表、开发包 manifest。  
  修复方式: 用现有 `extract_map` 导出 ROM 地图 0 与图集；scene.json 的地图清单经同一个 manifest 校验/加载入口读取。未另造 importer。

【地图实际进度】  
修改前正常可达地图ID: 114、16（16 仅 13 个观察格）  
修改后正常可达地图ID: 114、16、0  
最终内容包地图ID: 114、16、0  
地图数量: ROM 已提取 12；本轮转换及包内 3；最终 App 正常可达 3。  
新增场景ID与名称: ROM 地图 0；“陈塘村”为 Reference 与 ROM 画面对应的暂定名称，未冒充 ROM 文本已验证。  
从新游戏的实际操作路线: 114 起点 (8,21) → 可选正常交谈仆人取小刀 → 114 南口 (8,29) → 16 (203,142)；上/下进家门返回 114，离门/再入重返 16；从 (203,142) 上、左、上两格、左两格、上九格、右两格至 16 (202,130) → 0 (0,15)；上到 (0,14) 后向左越界 → 16 (202,130)。回放未设 Flag、传送或改坐标。  
仍未开放的入口或区域及原因: 地图 16 其他特殊碰撞类别尚未解释；地图 0 NPC、店屋内景和事件未导入；原版随机遇敌可在通往村庄途中发生，本轮 Android 尚无战斗。普通类别 0/2 的开放不等于这些内容已完成。

【连接证据】

- 114 (8,29) → 16 (203,142)；步入门格、无强制前置谈话；ROM 模块 8 `E4A1` 行 `08 1D 10 CB 8E`，既有正常输入回放；落地朝下；ROM 行与玩法已验证；最终 Android 正常进入。
- 16 (203,142) → 114 (8,29)；先离开落点，再步入家门格、无 Flag 条件；ROM 模块 8 `DE4C` 行 `CB 8E 72 08 1D`，FCEUX 冷启动正常输入第 915 帧；落地朝下；ROM 行与玩法已验证；最终 Android 往返 10 次。
- 16 (202,130) → 0 (0,15)；步入建筑入口、无前置事件；ROM 模块 8 `DE51` 行 `CA 82 00 00 0F`，正常输入第 1115 帧；落地朝下；ROM 行与玩法已验证；最终 Android 进入并往返 3 次。
- 16 (203,130) → 0 (0,15)；同一建筑的相邻入口格；ROM 模块 8 `DE56` 行 `CB 82 00 00 0F`；ROM 行已核对，独立原版按键回放未执行，**玩法与落地朝向暂定**；Android 共用出口逻辑但不将它计入 A3。
- 0 (0,14) 左边界 → 16 (202,130)；必须向左越界，非落点自动触发；ROM 模块 8 `DDC9` 行 `FF 82 10 CA 82`，原版正常输入第 1303 帧、落点 (202,130)、朝下；ROM 行与玩法已验证；最终 Android 往返 3 次。

证据索引: `game-data/provenance/world01.json` 记录 ROM SHA-256、每条行偏移/原始字节、只读 RAM 的正常控制器回放脚本及轨迹哈希。ROM/PPU/RAM 和派生包均在 Git 忽略目录，未随 APK 单独公开。原版回放另一输入时序遇到“百角海膽”随机战斗；本轮没有伪造战斗来消除此差异。

【验收】  
A1去除人工试玩门禁: PASS；`Scene.check` 使用网格碰撞及受限特殊格，超过旧观察路径的普通格可移动。  
A2两图往返10次: PASS，最终 APK 的模拟器触控回放实际完成 10 次；落点与地图 ID 断言通过。  
A3新增地图正常可达并返回: PASS，最终 APK 的模拟器触控回放实际完成 3 次。  
A4真实碰撞保持: PASS，地图 114 NPC 与墙体、地图 16 地形类别 3、地图 0 阻挡类别 1 仍拦截。地图 0 NPC 尚未迁入。  
A5状态与重启恢复: PASS，同一触控回放从正常仆人对话取得小刀，10+3 次往返后 Flag/背包仍为一次赠物；额外执行 `am force-stop` 后重启，前后同为地图 0、像素坐标 (8,248)、小刀 1、赠刀 Flag=true、内容 c6。装备/背包转移旧回归仍通过。  
A6升级后新内容生效: PASS，模拟器覆盖安装 v11→v13；v11 正常摇杆进入 16 后的 c4 存档 `mapId=16,x=3256,y=2280` 升级为 c6，地图/坐标保持；无卸载、清档或内容缓存覆盖。模拟器首次冷加载超过 15 秒，继续等待后自动完成迁移。  
A7最终APK一致性: PASS，已安装 `base.apk` SHA-256 与本地发布 APK 完全相同，系统 `versionCode=12`；公网完整下载与元数据哈希校验通过。

【测试与证据】  
实际执行的构建/测试命令: `.\.venv\Scripts\python.exe tools/export_development.py`；`.\phase1.ps1 test`；`.\build-android.ps1 -SkipExport -LocalOnly`（Gradle `testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`）；`adb shell am instrument -w -r org.fengshen.dev.test/android.test.InstrumentationTestRunner`；`tests/test_publish.ps1`；`publish-apk.ps1 -CheckOnly` 与 `publish-apk.ps1`。  
通过/失败/跳过数量及原因: Python 84/0/0；Android JVM 18/0/0；Android 仪器 32/0/0；发布门禁 4/0/0；同一最终 App 触控路线又单独重跑 1/0/0，并验证进程强制停止后的存档。联网真实云账号破坏性测试未执行：测试在无私有凭据时记录 `SKIPPED_LIVE_CLOUD_NO_PRIVATE_CREDENTIAL`，本轮不改云功能。早期触控脚本曾因冷启动加载及持续输入多走一格失败，改为等待场景加载并按实际位置补充正常触控后重跑通过；验收录像来自通过的最终回放。  
正常流程录屏: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\world01-v13-emulator.mp4`（134.742 秒；最终 Android App，模拟器正常触控；含取小刀、地图往返、停止进程与重启）  
关键截图: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\world01-v13-map114-emulator.png`、`world01-v13-map16-emulator.png`、`world01-v13-map0-emulator.png`、`world01-v13-restored-emulator.png`。  
模拟器型号/系统版本: Android SDK built for x86_64，Android 15 / API 35。  
一加13T真机: NOT_RUN；待用户用发布包核对触控手感与画面。

【回归】  
赠刀: 正常触控流程后仅 1 把，复谈/往返/恢复无重复。  
装备与背包: 现有卸装/再装备往返测试通过，地图回放未改变装备。  
菜单/HUD/输入隔离: 旧仪器测试通过；本轮没有调整布局配色。  
本地存档: c4→c6 实际覆盖升级及停止进程恢复通过；云端业务未改。  
已知新问题: 地图 0 缺少 NPC/建筑事件；地图 16 普通随机遇敌尚未实现，故目前的村庄路径可达不代表原版战斗流程完整。模拟器冷加载 c6 可能超过 15 秒，OnePlus 13T 上的等待时间未测。

【供下一轮规划的事实】  
当前正常流程最远到达点: ROM 地图 0（陈塘村，名称暂定）并能从原版边界返回地图 16；南海龙王里程碑未完成。  
到下一剧情/战斗节点的具体阻塞，最多3项: ① 地图 16 随机遇敌与普通战斗执行逻辑缺失；② 地图 0 NPC/店屋及事件数据与执行缺失；③ 后续南海方向的具体出口和条件尚需目标 ROM 正常流程证据。  
每项缺的是数据、运行逻辑还是证据: ① 运行逻辑及敌人行为核对；② 数据、逻辑与运行证据；③ 证据及相应连接数据。  
本轮实际复用的模块: `tools/forensics/fengshen246.py::extract_map`、`tools/export_development.py`、`ContentLoader`/`AssetSource`/`DirectorySource`、`Scene.check`/`World.tick`、`SaveSnapshot`、既有 `GameView` 摇杆/场景渲染、Gradle 8.10.2、`publish-apk.ps1` 的 Fengshen 独立 OSS 发布。  
本轮新增的最小实现: 地图 0 图集/数据包；地图清单加载；ROM 出口与方向边界触发；目标落点校验；旧 c4 存档兼容；对应 ROM 与 Android 回归证据。

END_DELIVERY_REPORT



DELIVERY_REPORT

task_id: INPUT-01  
status: READY_FOR_REVIEW

【版本】  
APK版本: `0.7.4-input-01` / versionCode 15 / `org.fengshen.dev`  
APK路径: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\fengshen-input-01-v15-debug.apk`  
下载地址: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=15 （已覆盖上传并从公网校验）  
APK SHA-256: `7264ebe9be45c1c00d3d3bb0094dca7c35478f452e24dcf1374d437ec1d97e52`，2,985,874 字节

【实际修改】  
输入入口及修改位置: `Core.kt` 的 `joystickDirection`、`InputState.movementIntent`；`MainActivity.kt` 的 `GameView.doFrame` 接入原 `FixedClock`。  
方向滞后实现位置: `Core.kt::MovementTuning.switchRatio` 与 `joystickDirection`；输入主方向只随摇杆角度更新，不受实际滑墙方向覆盖。  
碰撞探测复用位置: `Scene.check` 的同一碰撞类别在 `Scene.blockType` 提供只读分类；`World.probe` 先辨认既有边界出口，开发边界不允许滑墙。步进过程中再次检查目标格。  
沿墙速度实现位置: `World.tickIntent` 固定本格方向和 `stepScale`，用逻辑帧的分数像素累计与最近帧收尾；完成后仍走原有出口结算。  
是否仍为严格四向移动: 是；每一步只有一个轴变化，角色朝向取实际方向。  
是否保持现有逐格坐标体系: 是；16 像素一格、整数世界坐标、碰撞与出口格不变。  
是否引入新依赖: 否；复用 Kotlin 标准库、现有 Android Canvas/MotionEvent 与 Gradle 8.10.2。

【实际参数】  
正常速度来源: 既有 `World.tick` 逻辑速度，每 60 Hz 固定帧 2 像素，16 像素一格需 8 帧（133.3 ms），即 V=120 像素/秒。  
死区: 默认摇杆半径 15%，仍可经现有 controls JSON 调整；旧用户自定义值保留。  
方向保持角度: 对角线两侧各 8°，初次精确相等选横向。  
最小滑动分量: 0.15；纯方向撞墙或剩余分量更小则停。  
一步内速度与方向如何处理: 开始一格时只读探测两轴，物理墙只保留未阻挡分量；本格方向和系数固定，输入变化只影响下一格。松手不启动下一格，已开始的合法格收尾；结束仍复核动态阻挡并只结算一次事件/出口。

【验收】  
T1: PASS；空旷处偏右只向右、偏上只向上，8 帧一格，逐帧无双轴位移。  
T2: PASS；同角度 40% 与 100% 推距正常均 8 帧，沿墙 0.6 分量均 13 帧。  
T3: PASS；水平历史在 50° 保持、54° 切上；垂直历史在 40° 保持、36° 切右；中心死区无意图。  
T4: PASS；横墙 45° 目标 11.31 帧/188.6 ms，实测 11 帧/183.3 ms（0.727V，距 0.707V 约 +2.8%）；水平分量 0.6 目标 13.33 帧/222.2 ms，实测 13 帧/216.7 ms（0.615V，约 +2.6%）；主方向为横向且纵向受阻时 0.8 分量为 10 帧/166.7 ms。  
T5: PASS；竖墙 45° 同为 11 帧，垂直分量 0.6 为 13 帧；主方向为纵向且横向受阻时 0.8 分量为 10 帧，全部单轴。  
T6: PASS；纯方向撞墙、双轴受阻、0.14 小分量和开发边界均不滑动；无穿角。  
T7: PASS；松手、菜单、触摸取消和 `onPause` 使用的 `active=false` 路径清意图，当前格收尾，恢复后无自动连续步。  
T8: PASS；真实地图格上的横墙/竖墙触控通过；NPC、出口优先、动态阻挡、事件/赠物、本地存档以及 WORLD-01 10 次家门往返和 3 次村庄往返的完整 Android 回归通过。开发边界未被滑墙绕过。

【证据】  
构建命令和结果: `.\build-android.ps1 -SkipExport -LocalOnly`，Gradle `testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`，PASS；原 c6 开发内容包未改。  
实际测试命令和结果: `.\phase1.ps1 test` 84/84；Android JVM 26/26（`CoreTest` 18、`Input01Test` 8）；`adb shell am instrument -w -r org.fengshen.dev.test/android.test.InstrumentationTestRunner` 33/33；`tests/test_publish.ps1` 4/4；`publish-apk.ps1 -CheckOnly` 通过；正式 `publish-apk.ps1` 的 v14→v15 签名/版本兼容、公网 APK SHA/大小和元数据校验通过。  
正常移动及横墙/竖墙滑动录屏: `F:\apps\fengshen-remake\artifacts\input-01\input01-v15-final-emulator.mp4`（41.63 秒；实际 Android App 的仪器触控，以测试入口放在现有地图可行走格；包括菜单取消；不用于证明原版正常路线）；SHA-256 `a1edd050e753db137bb70fd521bc886e4adb7de220281a34c145e7c56a044a19`。  
模拟器型号与系统: Android SDK built for x86_64，Android 15 / API 35，1080×2340、440 dpi。  
一加13T真机: NOT_RUN；等待用户升级后反馈手感。

【回归与限制】  
当前地图连通状态是否改变: 否；仍为现有 c6 的 114↔16↔0，未改地图、事件或出口定义。  
WORLD-01未完成项是否仍保留: 是；地图 16/0 的未知特殊格、随机遇敌与村内事件仍按原报告限制，本次不宣称补完南海龙王里程碑。  
已知问题，最多3项: ① 一加 13T 真机手感未验证；② 固定逻辑帧使单格减速存在不超过半帧的耗时量化；③ 其他原版地图/战斗内容缺口沿用 WORLD-01 报告，本轮未扩展。

END_DELIVERY_REPORT

DELIVERY_REPORT

task_id: BATTLE-01  
status: PARTIAL

【基线与版本】  
实际基线版本: v15 / `0.7.4-input-01`、内容 c6；WORLD-01 三图往返保留。  
INPUT-01状态: 已合并；四向角度、沿墙减速测试保留。  
新APK版本与versionCode: `0.7.5-battle-01-partial` / 16。  
构建标识: `org.fengshen.dev`、Gradle 8.10.2、`opening-segment-001-c7`。  
APK本地路径: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\fengshen-battle-01-v16-debug.apk`。  
实际下载地址: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=16 （Fengshen 独立对象，已覆盖并公网校验；Language 对象未改）。  
APK SHA-256: `f50d1122af442b4c6216d7f3a37e19d517a05f704ecd4f9b33d5937827f96a92`；3,087,518 字节。  
运行时内容版本、来源与hash: c7 随包经 `AssetSource → ContentLoader` 校验；`combat.json` SHA-256 `73305e0413cf99e2e11f9432f74cadf2c778372fd84ff92f27fe721601408f83`；来源见 `game-data/provenance/battle01.json`，目标 ROM SHA-256 `f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25`。

【正常可玩流程】  
Playable From: 新游戏或兼容 c1–c6 本地存档，地图 114/0。  
Playable To: 地图 16 首个分区的普通物理胜利并再次正常遇敌；南海龙王里程碑未完成。  
实际操作路线: Android 仪器触控从地图 114 与仆人交谈获刀、原出口到 16、正常走格遇敌、点选攻击、胜利、回地图、再走格遇第二敌群、进程重启核对存档；未用调试传送/强制敌群。  
本轮启用的原版遇敌分区: 地图 16 zone 0，世界格 `(193,128,216,151)` 与 `(187,100,221,117)`，下界不含、上界包含。  
本轮未启用的其他区域: 地图 16 其他分区、地图 114/0 遭遇及未知特殊事件。

【复刻覆盖】  
敌群条目数及ID: ROM 组 0 的 19 条，ID 0–18，全部经包校验和独立运行测试；组 11 为样本敌人 2/3。  
敌人ID及名称: 1 名称 UNKNOWN，界面明确显示“原版敌人 1”；2 臭甲虫；3 百角海胆。战斗未接入原版敌人像素图。  
已支持的敌方行为: 当前三敌 `behaviorByte=0` 的普通物理命中检查与伤害；未验证其他行动分支。  
已支持的玩家指令: 选目标、普通攻击、胜利返回；失败时明确标注开发版“读回战前存档”。  
未实现但原版存在的指令/行为: 逃跑、玩家未命中/暴击、未观测的行动顺序/特殊分支及原版失败处理；法术/道具禁用。  
奖励与升级规则来源: ROM 敌人各自 EXP/银两及哪吒成长表；组 11 合计 EXP +5/银两 +3，绝非通用固定值；已核前两级阈值 12/27。  
失败与逃跑行为来源: 原版结论不足；失败不发奖、保留战前安全存档，开发版显式恢复不冒充原版。  
随机机制差异: ROM 逻辑在对齐步以 `$5B` 计数、比较自由运行 `$43` 与 16，50 步强制检查；Android 在完整一步后使用独立安全随机字节，序列不等价；逃跑后的 `$06E2` 宽限分支尚未接入。  
仍使用的暂定数据: 殷氏赠金/开场时机等既有 Reference 内容未在本轮提升为 ROM VERIFIED；敌人 1 名称和敌人战斗图仍未知。

【原版对照】  
原版样本及起始条件: 同指纹 ROM 冷启动、正常手柄输入；地图 16 世界格 `(201,151)` 于第 1765 帧进组 11，敌 2/3；样本 HP 20→8、EXP +5、银两 +3。  
Android对应条件: c7 地图 16 zone 0、初始哪吒与小刀；正常随机抽组；组 11 在受控字节单测中核对首回合（敌 2 未命中、敌 3 造成 3）。  
已吻合的字段和行为: 区域边界、19 组/敌人 ID、样本敌方首回合与组 11 奖励和、完整格一次触发；最终 APK 的正常录像出现组 3 胜利 EXP +1/银两 +1，随后再遇组 11。  
尚未吻合或未验证的部分: NES RNG 字节、玩家命中/暴击、全部行动顺序、逃跑、原版失败与完整组 11 逐回合 HP 20→8；不宣称公式全面等价。

【验收】  
B1: PASS；正常 Android 摇杆路线在地图 16 遇敌，ROM 19 条组均加载并在仪器测试中可执行。  
B2: PASS（普通物理范围）；点选目标会扣真实 HP，敌方命中/伤害执行，战斗期间地图坐标不变、操作层隔离；未开放命令不伪装可用。  
B3: FAIL；受控组 11 首回合和奖励相符，完整 ROM 逐回合对照及玩家命中分支未验证。  
B4: PASS（当前成长范围）；ROM 组奖励求和、阈值 12/27、跨阈值及一次性结算通过单测；最终 App 正常胜利 EXP +1/银两 +1 并持久化。  
B5: FAIL；最终 App 胜利后再遇组 11 已通过；原版逃跑/失败未复现，仅有明确标注的开发版失败恢复。  
B6: PASS（本地）；战后保存、停止 Activity、重启恢复奖励/位置/赠刀；c1–c6 迁移和 v15→v16 包名签名兼容检查通过。未用真实云账号写入测试。  
B7: PASS；Phase 1 85/85，Android JVM 33/33，最终战斗间距改动前完整仪器 34/34，改动后正常路线/胜利/再遇/重启定向 1/1；WORLD-01 独立 10+3 往返仍在仪器测试中。  
B8: PASS（模拟器产物）；最终 v16 APK 已安装、c7 包已加载、正常 App 录屏与截图已核；一加 13T 实机未测。

【工程证据】  
构建命令及结果: `.\build-android.ps1 -SkipExport -LocalOnly` PASS；`.\publish-apk.ps1 -CheckOnly` PASS；正式 `.\publish-apk.ps1` 公网 hash/大小验证 PASS。  
测试命令及通过/失败/跳过数量: `.\phase1.ps1 test` 85/0/0；Gradle `:app:testDebugUnitTest` 33/0/0；`adb shell am instrument -w -r ...` 34/0/0；最终 APK 的定向正常游玩仪器 1/0/0；`tests/test_publish.ps1` 4/0/0。  
最终App正常流程录屏: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\battle01-v16-final-normal-play-emulator.mp4`，实际 Android 15 模拟器 App，约 121.9 秒，SHA-256 `a728c4661a79f3150737c5ea2f37f8bb20426bd4b3083440b9d6c4cdb768a0b`；正常输入录像，非 FCEUX。  
关键截图: `artifacts/checkpoint-ui/battle01-v16-final-result-emulator.png` 与 `battle01-v16-final-again-emulator.png`（实际最终 Android App；首战组 3 与再次遇敌组 11）。  
模拟器型号与系统: Android SDK built for x86_64，Android 15/API 35，1080×2340、横屏运行。  
冷启动分段耗时: 最终模拟器 `am start -W` Activity 约 2.894 秒；首次安装后曾出现约 10.64 秒显示耗时；内容哈希校验、JSON/图集解析和首个游戏帧没有分别计时，写 NOT_RUN，不把 Activity 时间冒充首帧时间。  
一加13T真机: NOT_RUN。

【复用与回归】  
实际复用的数据、文件和模块: `fengshen246.py` ROM Reader/敌人成长提取、`export_development.py`、`ContentLoader`/`AssetSource`、`World.tickIntent`/`Scene.check`/`FixedClock`、`GameView` Canvas/触控模态层、`SaveSnapshot`、Gradle 8.10.2、`publish-apk.ps1`。  
新增的最小运行逻辑: ROM zone 0/19 组导出；完整格遭遇门槛；三敌普通物理回合、一次性奖励成长；战斗模态面板及开发版战败恢复；存档遇敌计数向后兼容。  
三图往返: 地图/出口定义未改；纯 World 的 10 次家门和 3 次村庄往返仍通过。实际当前 UI 长途行走可被正常战斗打断，旧“全程无战斗”的触控断言已按需求更新。  
赠刀与装备: 旧 NPC、稳定物品 ID 与装备数据保留；战斗只在装备右手小刀时读取既有攻击加成。  
本地存档和旧档升级: c1–c6 可读，`encounterSteps` 缺省为 0；战中仅保留战前检查点，胜利一起保存角色/金钱；没有本轮新增云端机制。  
输入与面板: 摇杆、A/B、HUD、菜单及 INPUT-01 保留；战斗模态清键，返回地图须新触摸。  
已知新问题: ① 逃跑/原版失败未复现；② 玩家命中/暴击与更多行动顺序需 ROM 核验；③ 敌人 1 名称、原版战斗图形未接入。

【下一轮规划需要的事实】  
当前最远可达且可玩的原版节点: 地图 16 首分区普通物理胜利后再次正常遇敌；地图 0 仍可由既有 World 连接到达，但此次未宣称经过所有战斗连续推进至村庄剧情。  
到南海龙王里程碑的最近阻塞，最多3项: ① BATTLE-01 的原版逃跑/失败及玩家命中分支；② 地图 0 NPC/店屋及事件；③ 后续南海方向出口与条件。  
每项缺的是数据、代码还是原版证据: ① ROM 行为证据及执行代码；② 原版数据、事件逻辑与运行证据；③ ROM 正常流程证据及连接数据。  
地图0 NPC/店屋的已有数据情况: 已有地图/图集/碰撞和进出连接，NPC、商店及建筑事件未导入可执行包。  
南海方向连接的已有数据情况: 本轮无可验证出口/触发条件，不凭攻略标题补造。

END_DELIVERY_REPORT

---

DELIVERY_REPORT

task_id: AUDIO-LOG-01
status: PARTIAL

【版本】
实际基线: v16 / 0.7.5-battle-01-partial / c7；WORLD-01、INPUT-01 保留，BATTLE-01 仍 PARTIAL。
新APK版本/versionCode: 0.7.6-audio-log-01 / 17，development/debug，包名/签名不变。
构建标识: audio-log-01-v17。
APK路径/实际下载地址: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\fengshen-audio-log-01-v17-debug.apk`；https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=17 。已覆盖发布，完整公网下载核对13,684,571字节/hash；既有应用内更新入口读取同目录version.json，系统确认安装。
APK SHA-256: `15562dd117d2a703dbb09144943a3ccb8fe737fbe85fd68803bf702aa787da79`。
运行时内容版本/来源/hash: opening-segment-001-c8 / 随包development，AssetSource→ContentLoader校验 / manifest SHA-256 `dba4f0c05c377ee1f8697aadd4916c13552f18ca9b6ee378183a5d37fe81742f`。DirectorySource仍共用入口；内容服务端下载/缓存尚未完成。
服务端部署标识: fengshen-remake-cloud；Linux二进制SHA-256 `47bb98e2a4edaeec22cfb4f040cddcdca1eebc7f8a6335f82573c3dbf2b5595d`。已部署、重启并读回日志；Language未修改。

【任务开始巡检】
执行时间与命令: 2026-10-01 00:21:40 +08:00，`./check-runtime.ps1`。
状态: NOT_AVAILABLE / diagnostics_not_configured。
查询环境与时间范围: fleetpilots.com/fengshen-api；当时无可用诊断区间。
查询的两个发布版本: v16/v15，可信发布记录，二者没有客户端诊断埋点。
模拟器/真实设备会话数: 不可统计，不写成零故障。
最后上报时间: 不可取得。
发现问题及证据: 尚无链路；`reports/runtime-audio-log-01-initial.json`保留原结果。自举并实际App上传后重新巡检，发现下述超时。
实际执行限制: 未伪造旧版样本；未连接一加13T。

【问题修复】
逐项列出RUNTIME_FIXES:
1. 本地候选v17压力测试卡住，来源`audio-log01-offline-initial-aborted.txt`、`audio-log01-hang-java.txt`（09-30 18:15–18:24 UTC），不是线上故障。反复安排WorkManager、超限时反复解析全部队列放大IO压力。Diagnostics.kt合并上传安排，逐项优先淘汰旧低优先级并提前停止，清除中断临时文件；压力测试在Activity结束后独立运行。450事件/1MiB保留FATAL、有丢弃统计、关闭清空，36.051秒PASS。原失败/线程证据保留，不据此声明音频超时修复。
2. 巡检解析失败，来源`audio-log01-query-parser-failure.json`，09-30 18:41:41 UTC，UNAVAILABLE。加载前contentVersion合法为空，JSON contents空键被旧PowerShell对象解析拒绝。check-runtime.ps1改为Hashtable并join多行；实际管理员查询和按ID只读回归通过。
3. 模拟器HTTPS上传失败，来源`audio-log01-app-upload-dns-failure.txt`，AVD默认DNS超时。仅让AVD使用本机已有代理10.0.2.2:7890；未改App域名/TLS或真机。App上传、离线补传实际读回通过；真机网络NOT_RUN。
4. 未解决：v17 audio_play_error/ERROR_CODE_TIMEOUT累计5次。早期样例`0aaa1478-ac12-4577-81b3-9bf704582251`，最近`757f012a-0f99-4261-bfab-f735682afae9`，09-30 18:54:38.417924729 UTC接收。初次2→最终5，回归/录屏又增加3。播放器只记录错误码/曲目，缺内部cause和线程栈；本地Release时间与部分错误相邻仅是线索，根因未确认，可能音乐中断。未删日志、改级别或标成test=true；最终覆盖升级检查后仍5次。
已修复: 1–3；发布先完整校验再修改保留集合、查询无写副作用也经隔离测试确认，不伪称曾发生线上损坏。
仍未解决及原因: 4，缺内部原因与独立复现，保持PARTIAL。
回归验证: 既有仪器34/34、新音频诊断6/6、Go9/9；超时仍在巡检中可见。

【音频】
各地图与战斗/胜利的曲目映射: 114→original.bgm.002；16→003；0→005；普通战斗→004；当前胜利/奖励继续004，返回恢复地图曲目。同曲不重新prepare，不编造独立胜利曲。
音效事件与资源映射: attack/hurt接既有实际战斗回调；confirm/cancel/attack/hurt四类干净资源未可靠对应，audio.json missingEffects明列，当前静音，无系统叮声/静音占位/含BGM攻击录音。
来源与验证状态: ROM SHA-256 `f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25`；FCEUX2.6.6正常手柄/WAV比较本地Reference commit `d636453f14f86a096f9d293bf3facfd96cfcb614`四个bgm_00N.mp3。逐资产/hash/录音时间窗见`game-data/provenance/audio-log01.json`。场景/旋律HIGH，循环INFERRED，非波形字节等价，未提升canonical。Reference未找到许可证声明，保留来源限制。
实际复用播放器/新增依赖: 原工程缺播放器，新增薄GameAudio，Media3 ExoPlayer1.4.1/Android SoundPool；WorkManager2.9.1补可靠上传。复用export_development.py、ContentLoader、GameView生命周期/设置、CloudTokenStore/现有账户会话、Go HTTP/systemd、独立发布。保留Kotlin2.0.21/AGP8.7.3/Gradle8.10.2/JDK17，无新引擎、模拟内核或第二导入器。
生命周期与循环验证: 单播放器/单手动焦点；菜单继续、后台暂停、恢复、开关/音量持久化仪器通过，004跨循环、胜利不重复准备。002/003/004/005循环窗2000–24363/30752/20636/36077ms，约50ms搜索推断，非采样精确。外部App抢焦点/真机听感NOT_RUN。
仍缺少的音频: 四类短音效、精确循环边界、超时稳定性。
带声音的App运行证据: `artifacts/checkpoint-ui/audio-log01-normal-with-audio.mp4`，最终v17正常摇杆/NPC/出口/遇敌/攻击/胜利/返回；实际Windows WASAPI输出回采，无麦克风或ROM声画替换。PCM RMS655.01、峰值5996、音轨47.66秒；画面与声音独立启动，非采样同步，录屏有黑边。另有8秒实际播放`audio-log01-app-playback.wav`，RMS446.35。三图`audio-log01-map114.png`、`audio-log01-map16.png`、`audio-log01-battle-result.png`均来自实际模拟器App。

【日志链路】
客户端采集与缓冲位置: Diagnostics.kt/GameApplication，应用私有files/diagnostics，UUID事件/批次/会话、版本/hash、单调序号和字段白名单、原子写入。MainActivity/ContentLoader/GameAudio接真实启动、前后台、分段加载、成功提交的首个可交互Surface帧、换图、战斗/一次奖励、存档/音频/异常；无每帧/摇杆日志。
实际上传/查询接口（无凭据）: `POST https://fleetpilots.com/fengshen-api/v1/diagnostics/installations`、`POST .../diagnostics/batches`；管理员`GET .../admin/diagnostics/summary`（可选eventID）和`/retention`。check-runtime.ps1经受保护SSH查询服务器本机同接口。
鉴权与脱敏方式: 优先已有会话，否则随机仅写安装令牌/Keystore；独立管理员读权限不进APK。字段白名单，URL/Bearer/password/token/key脱敏、栈截断，无存档/剧情正文或全系统Logcat。安装6/IP/小时、最多1000；上传60/token/分钟、120/IP/分钟；无硬件身份和麦克风采集。
私有存储位置及访问限制: `/var/lib/fengshen-remake-diagnostics/diagnostics.json`，目录700/文件600，fengshen-cloud，原子持久化，独立于玩家PostgreSQL。Linux匿名读写401、私有路径404，重启后按原ID读回。
本地/服务端容量上限: 本地10MiB，优先淘汰旧INFO/WARN并统计；解压批256KiB/128事件、单事件4KiB；网关压缩体64KiB，客户端选批60KiB。每版20MiB（事件16MiB/索引4MiB），硬上限/丢弃计数；退避、发生版本不改写、事件/批次幂等。关闭取消任务/请求并清空队列，不改存档。
最终App事件ID到服务端读回的证据: 实际上传`591e17f3-3a33-476a-ab9f-c5443faff114`；离线补传`1f3d5b88-094b-4e83-8009-57301c174368`；受控未处理异常`a984f0aa-295a-4eb6-9d37-db887a86a2c5`。真实App生成/上传，按相同ID读回v17/c8/上述hash/批次/接收时间，`reports/diagnostic-readback-<ID>.json`。均test=true，不计正常故障，不是curl样本。
退出原因采集的实际范围和限制: API30+本包最近10条ApplicationExitInfo，时间去重；强停不判崩溃。可能无栈/发生APK版本，明标occurrence_apk_version_unknown，仅给观测版本；受控异常同步写最小记录后调用原异常链，下一启动补传。

【两版保留】
实际保留的两个发布版本: 17/0.7.6-audio-log-01、16/0.7.5-battle-01-partial。
版本权威来源: 已验证公网Fengshen OSS version.json及匹配包名/签名/hash的current/previous，管理员可信登记；客户端不能建发布版，c7/c8不是保留单位。
发布清理触发方式: publish-apk.ps1公网验证→server/register-release.ps1立即登记/清理，启动补偿；失败可查询且阻止成功声明，重跑登记，不等新客户端。
隔离三版本测试结果: 临时私有目录连续1/2/3，仅留2/3；无3版客户端也清理1；迟到410、重启、并发上传/清理通过；同锁防旧版重写，不改真实历史。
实际清理数量与失败数: cleanedEvents=0、cleanupFailures=0；43个批次收据，storedVersionCounts={17:499}。
迟到旧版批次处理: 410 expired_apk_version，拒绝落盘；客户端删除相应过期事件/批次，不无限重试。
是否存在旧版残留: 查询无15或更旧诊断事件/索引残留；未删APK、ROM、发布历史、账号或玩家存档。
没有历史样本时的说明: 有可信15/16/17发布记录，但15/16未埋点，无历史运行日志可删；生产清理0不证明删过旧日志，三版清理由隔离测试证明。

【验收】
G1: FAIL（未完整）；四首BGM/场景映射和实际声音通过，四类短音效/精确循环仍缺。
G2: FAIL；生命周期功能测试通过，但5次真实播放器超时未定位，不能声明稳定性通过。
G3: PASS；最终App事件ID/v17/c8/hash实际服务端读回。
G4: PASS（模拟器）；真实断网仍播放/游戏/保存，恢复补传同ID；受控异常下一启动上传，去重测试和关闭不改存档通过。
G5: PASS；隔离三版/重启/并发/迟到通过；生产17/16、0清理失败，无伪造旧版日志。
G6: PASS；私有权限、匿名拒绝、脱敏/容量通过，不触碰其他项目/玩家数据。
G7: PASS；开始NOT_AVAILABLE、发布后ISSUES_FOUND均为实际查询，固定JSON/Markdown保留，不以健康200替代。
G8: PASS（已有开发功能/模拟器）；三图World、INPUT、赠刀/装备/背包、当前战斗/存档34项通过。实际v16正常游玩c7存档→install -r v17→启动/停止c8，全部游戏字段一致，安装前后偏好文件字节相同。真机NOT_RUN，不提升BATTLE-01完整性。

【发布后巡检】
执行命令: `./check-runtime.ps1 -Stage postflight`。
实际查询时间: 2026-10-01 03:13:53.9204718 +08:00。
查询版本和样本数: 权威17/16；499事件，5条test=true另计；10模拟器会话（含仪器测试普通事件）、真实0。最后上报03:12:42.405606797；接收区间02:38:12.711925244–03:12:42.405606797。17有样本，16无埋点。
新增错误与结果: ISSUES_FOUND；TIMEOUT初次2→最终5，未解决，覆盖升级后未继续增加。首帧实际样例6349ms，校验129/解析5735/图集4/音频134ms（另一次首帧7623ms）；是模拟器样例，Surface提交计时，不是am start -W或性能承诺。
仅模拟器还是包含一加13T: 仅模拟器，无真机健康结论。

【工程与回归】
实际构建/测试命令:
- `./build-android.ps1 -LocalOnly`：导出/Gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest，BUILD SUCCESSFUL；`./publish-apk.ps1`：PUBLISHED_AND_VERIFIED。默认`./build-android.ps1`自动发布，使用现有受保护配置。
- `.venv/Scripts/python.exe tools/run_phase1_tests.py`86/86；`tools/check_audio_sources.py`四首资源解码/hash/ROM录音比较通过。
- Gradle :app:testDebugUnitTest 33/33；`adb shell am instrument -w -e class org.fengshen.dev.ApkUpdateTest,org.fengshen.dev.ContentTest,org.fengshen.dev.TouchTest org.fengshen.dev.test/android.test.InstrumentationTestRunner`34/34（210.452秒）。
- AudioDiagnosticsTest分别运行4项离线/播放器/包/开关、独立容量1、实际上传1，共6/6。ControlledCrashTest独立预期退出，按原ID读回，不算吞异常或普通套件成功。
- `cd server; F:/apps/go/bin/go.exe test ./...`9/9；服务器Linux diagnostics7/7及权限/匿名检查；`./tests/test_publish.ps1`6/6。
- `.venv/Scripts/python.exe tools/record_app_audio.py`正常App用例1/1；`tools/check_apk_upgrade.py`v16正常用例1/1+实际覆盖状态比较PASS。只在AVD运行，没有卸载/清空用户手机。
通过/失败/跳过数量: 最终Python86/JVM33/既有仪器34/新仪器6/Go9/发布6全部通过，无未解决断言失败或标准套件跳过；Linux7与正常用例为重复/独立环境验证。Go race因CGO不可用NOT_RUN，真机/外部音频抢焦点NOT_RUN。早期超时/错误线程访问证据保留；功能G1/G2仍FAIL，不能被测试通过覆盖。
正常流程录屏: `F:\apps\fengshen-remake\artifacts\checkpoint-ui\audio-log01-normal-with-audio.mp4`，SHA-256 `26a1bbf03cb68f839f0f86c6c72a0a3a2d554ba7c5be020d010e11d3ad011bce`；录音与三图同目录，非ROM替代，黑边/独立音轨限制已说明。
模拟器环境: Fengshen_A_API35，Android SDK built for x86_64，Android15/API35，1080×2340/density440，App横屏、音频启用；DNS失败经本机已有代理完成HTTPS。
一加13T: NOT_RUN。
WORLD-01/INPUT-01回归: 连接/坐标/碰撞、四向角度/沿墙速度、模态、赠刀/装备未改规则；10家门+3村庄World往返通过，未扩大边界。
BATTLE-01仍未完成项: 原版逃跑/失败、玩家命中/暴击、部分顺序、敌人图形/名称；开发版安全失败恢复不算原版，不因声音/日志标成完成。
下一轮最重要的实际阻塞，最多3项: ① 播放器Release附近超时原因/稳定性；② 四类干净原版音效和事件对应；③ 精确循环/OnePlus13T生命周期与听感。

END_DELIVERY_REPORT

# APK-UPDATE-01 交付（2026-10-01）

task_id: APK-UPDATE-01
status: READY_FOR_REVIEW（模拟器验收；一加13T复测待确认）

修复代码中可确认的误报路径：旧接收器将缺失状态默认成安装失败，将用户取消一并显示失败；安装会话/目标未持久化，应用替换进程后不能恢复结果。v18分别处理等待、取消、未知/无归属回调与真实失败，并优先根据PackageManager实际版本确认安装结果。取消清除会话并允许重试；真实失败保留错误及可理解提示。权限返回恢复已校验APK；同版本不重复提交；元数据禁缓存，原包名/签名/版本/大小/SHA校验仍执行。

原手机报错根因的限制：v17没有更新阶段诊断，用户尚未提供完整提示/发生阶段，且当前没有真机ADB连接。已有PKX110/API36/Android16的v17成功加载、首帧和存档上传，只能证明这些行为；不能据此声明原手机报错已复现或一加13T升级验收通过。

【实际版本与发布】
APK版本: v18 / 0.7.7-apk-update-fix，包名org.fengshen.dev。
APK路径: F:\apps\fengshen-remake\artifacts\checkpoint-ui\fengshen-apk-update-01-v18-debug.apk。
下载地址: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=18
APK SHA-256: b4bb6c1ea184798a470c5924be8cd4a6ec62dbb45fd22e126a908776edc72950；13,639,399字节。
保持原签名5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6；无需卸载或清空数据。
已覆盖Fengshen独立APK与version.json两个OSS对象，完整公网下载/hash/大小校验通过，PUBLISHED_AND_VERIFIED；未改Language代码、签名、路由、对象或数据库。私有诊断服务仅扩展安全字段白名单，沿用原服务部署；玩家存档数据库未改。

【实际复用与修改】
- ADAPT: android/app/src/main/java/org/fengshen/dev/ApkUpdate.kt，沿用ApkUpdater下载验证/PackageInstaller/接收器；只补安装状态恢复、正确分类、权限返回及阶段诊断。升级器私有SharedPreferences不是第二套玩家状态/存档。
- ADAPT: MainActivity.kt结束时关闭升级网络请求；Diagnostics.kt与server/internal/diagnostics/diagnostics.go只允许stage、targetVersion、installedVersion、installSessionID、status五个新字段。AndroidManifest.xml保留旧回调兼容并接应用替换/新会话URI。
- REUSE: ContentLoader、SaveSnapshot/本地与云端存档、GameView/World/UI完全保留。内容仍opening-segment-001-c8，hash dba4f0c05c377ee1f8697aadd4916c13552f18ca9b6ee378183a5d37fe81742f；未迁移内容服务端。
- ADAPT: publish-apk.ps1读取巡检JSON改为Hashtable，支持真实的空contentVersion键；tests/test_publish.ps1原六项安全断言保留，未降标准。build-android.ps1默认路径/Gradle版本与发布说明递增。
- QA: ApkUpdateTest.kt补四项分类/归属用例；UpdateFlowTest.kt和tools/check_app_update_flow.py只驱动AVD真实下载/系统确认/取消并比较存档，不成为产品更新实现。
现有依赖: Android SDK PackageInstaller/HttpURLConnection、Kotlin2.0.21、AGP8.7.3、Gradle8.10.2、JDK17；原WorkManager2.9.1诊断上传及Media3 1.4.1保持。未引入新依赖。取消/缩小工作: 不重写下载器、玩家状态或发布平台，不扩展音频、战斗、地图和后台。

【构建与回归】
- ./build-android.ps1 -LocalOnly及最终Gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline，BUILD SUCCESSFUL；./publish-apk.ps1，PUBLISHED_AND_VERIFIED。可重复默认./build-android.ps1（构建后自动独立发布；已发布同版本只能重传相同字节）。
- .venv/Scripts/python.exe tools/run_phase1_tests.py，86/86。
- Gradle :app:testDebugUnitTest，33/33。
- adb -s emulator-5554 shell am instrument -w -e class org.fengshen.dev.ApkUpdateTest,org.fengshen.dev.ContentTest,org.fengshen.dev.TouchTest org.fengshen.dev.test/android.test.InstrumentationTestRunner，38/38，202.733秒；既有34项完整保留。
- cd server; F:/apps/go/bin/go.exe test ./...，10/10。
- ./tests/test_publish.ps1，6/6；首次空键解析导致缺凭据断言失败的记录保留，修复解析后六项通过。
- UpdateFlowTest#testCheckPublicReleaseIsAlreadyInstalled，1/1；#testSameVersionDoesNotCreateInstallSession，1/1，均公网发布v18后执行。
- .venv/Scripts/python.exe tools/check_app_update_flow.py cancel，PASS：实际系统取消，安装状态清除、仍v18、存档一致。fixture是同字节APK18重装并明确test=true；目标19仅用于防止测试被已安装版本分支短路，未生成/发布v19，不是成功升级证据。
- .venv/Scripts/python.exe tools/check_app_update_flow.py upgrade，PASS：AVD以adb install -r -d恢复v17仅为测试准备，随后v17 App公网下载并验签/校验→实际App安装按钮→实际系统UPDATE确认→PackageManager版本18→启动地图→所有存档字段完全一致。升级本身未使用adb install。App缓存下载hash与实际发布b4bb…950一致。
- 自升级替换进程会终止正在运行的仪器，驱动日志Process crashed不计普通测试OK；外部验收以真实系统确认、安装后版本及存档一致为依据。前三次QA驱动失败（Activity过早结束、UIAutomation争用、将版本名update误识别为系统确认）保留原报告；只改测试驱动，未修改已发布APK。最终要求系统窗口packageName不同于App才进行确认。
- 最后测试驱动编译仅: Gradle :app:assembleDebugAndroidTest --offline，BUILD SUCCESSFUL；重新确认发布APK SHA仍一致。

【实际Android证据】
AVD: Fengshen_A_API35，Android15/API35，Android SDK built for x86_64，1080×2340/density440，App横屏；无一加13T连接。一加13T: NOT_RUN。
录屏: F:\apps\fengshen-remake\artifacts\checkpoint-ui\apk-update01-real-upgrade.mp4，51.84秒，实际Android App下载提示/系统确认/恢复地图；Android原始screenrecord，portrait容器带黑边，无音轨，不以此证明音频。
截图同目录: apk-update01-download-verified.png（实际App验包提示）、apk-update01-upgrade-system.png（实际系统确认）、apk-update01-upgraded-map.png（实际v18恢复地图）。
取消系统页: apk-update01-system-confirmation.png；cancel-feedback.png为仪器结束后Launcher画面，不作为App可见反馈截图。取消功能以状态和服务器同ID读回验证。

【发布前后巡检与实际诊断读回】
./check-runtime.ps1: 2026-10-01 07:34:22 +08:00，ISSUES_FOUND；540事件/11普通会话（10模拟器、1真机），5测试事件；最后收到07:29:44。真实v17已能加载c8/保存，首帧样例889ms，不能推定升级行为。
./check-runtime.ps1 -Stage postflight: 2026-10-01 08:26:09 +08:00，ISSUES_FOUND；598事件/20普通会话（19模拟器、1真机），7测试事件，最后08:25:53。无新增上传错误；既有audio_play_error/ERROR_CODE_TIMEOUT仍5次，未删除或改级别。此查询只涵盖实际上传，非全设备健康证明。
权威保留版本18/17，storedVersionCounts 18:20、17:578；cleanedEvents0、cleanupFailures0，没有伪造旧日志清理。
实际PackageInstaller取消事件72ba571c-ec77-4b92-88de-de8dd4c9da7d，原ID经管理员summary读回：v18、stage=cancelled、status=3、severity=INFO、test=true；五个白名单字段保留。证据reports/apk-update01-cancel-event-readback.json，非curl伪造事件。reports/apk-update01-events-readback.json为私有既有日志只读筛选，不输出安装令牌/账号。

【真机复测步骤与剩余限制】
游戏菜单→设置→检查应用更新→安装→Android系统确认；如系统要求安装来源权限，在系统设置允许后返回。安装完成重新打开，设置确认版本18并继续原存档。无需卸载/清空数据。再次检查应提示当前最新；取消应可再检查，不应显示安装失败。
1. 原手机完整提示/阶段仍未知；一加13T复测待反馈，不能将AVD通过当真机通过。
2. AUDIO-LOG-01的5次播放器超时和短音效/精确循环仍未解决，保持PARTIAL。
3. BATTLE-01原逃跑/失败等欠账保留，地图连通/UI/INPUT/内容规则未改，本轮不声明南海里程碑完成。

END_DELIVERY_REPORT

# BATTLE-02 / v19 最终交付（2026-10-01）

DELIVERY_REPORT

task_id: BATTLE-02
scope_revision: original-battle-presentation
status: PARTIAL

【版本】
实际基线：v18 / 0.7.7-apk-update-fix，保留更新器修复及已有未提交成果。
APK版本/versionCode：0.7.8-battle-02 / 19；包名与签名保持。
APK路径：F:/apps/fengshen-remake/artifacts/checkpoint-ui/fengshen-battle-02-v19-debug.apk
下载地址：https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=19
APK SHA-256：731c95939f641cc3bd4cee0c01fa2b4e9071dad823ec7bab9761f325285a1431
运行内容：opening-segment-001-c9；复用现有导出器，原版 ROM 图块、运行证据与仍标明暂定的既有 Reference 字段；26个文件已核对最终APK内manifest/文件hash。
内容 manifest SHA-256：0b1a2bc33f1d9b8ab3f6796d8cde87c72d0b998ae6e2f421a0567925ffb53fa9
已覆盖发布且公网完整校验通过，仅Fengshen独立APK/version.json对象，未改Language。APK更新与内容包下载不同；本版仍加载随包开发内容，未宣称服务端内容下载已完成。

【服务端巡检】
开工：2026-10-01T03:17:13.7164336Z，./check-runtime.ps1，可信发布记录最近两版18/17。
v17：621事件、5测试事件、14模拟器/1真实设备会话，接收区间09-30T18:38:12Z至10-01T03:09:52Z；旧audio_play_error/ERROR_CODE_TIMEOUT共5次。
v18开工窗口：132事件、2测试事件、5模拟器/1真实设备会话，00:14:43Z至03:15:57Z，无新增上传ERROR；未发现上传的崩溃、存档失败或重复结算。
最后发布后：2026-10-01T06:09:13.4920159Z，./check-runtime.ps1 -Stage postflight；权威保留19/18。
v19：278事件、81测试事件、8总会话；普通样本6模拟器/1真实设备会话。区间05:30:13Z至06:03:18Z。
v18：376事件、2测试事件、7普通会话（6模拟器/1真实设备）。区间00:14:43Z至05:58:37Z。
真实设备为PKX110/API36/Android16；v19样本包含遇敌、胜利结算及保存成功。无新增上传ERROR，不代表未上传过程或真机声音/手感已验收；模拟器正常与test事件可能共享QA会话。
17的621条按最近两版规则清理，cleanupFailures=0；历史故障只保留脱敏分析，不另存完整旧日志。
修复：音频线程/生命周期守卫、原因链诊断，战斗呈现/输入/结果，旧存档兼容，以及现有云存档encounterSteps字段缺失。后者严格解析实际返回400 invalid_json，补0..255字段和兼容HTTP测试后部署；不改数据库结构，不写真实云进度。
音频历史事件757f012a-0f99-4261-bfab-f735682afae9没有cause/timeoutOperation，根因UNCONFIRMED。两轮有限定位未复现，不宣称已根治。
音频覆盖12轮播放器切图/焦点/暂停恢复和6轮Activity生命周期；完整回归再次覆盖，共24轮播放器、12轮Activity，无新超时或播放进度失败。关闭后旧回调、重复close和调用线程均有保护。最终App录音有声音，短音效与精确循环端点仍缺。
v18更新器保留；AVD实际v18 App公网下载v19→系统确认→安装版本19→旧档保留通过。取消不显示升级成功，真实校验/安装拒绝仍报错。原手机误报根因仍待真机确认。

【点触后结束的根因】
旧调用链：敌人数据行触摸→attack→本次玩家攻击及存活敌人行动→立即显示结果；缺少可观察行动呈现。未发现一次调用自动跑完整场的循环。
截图Lv4/HP30符合当前成长表：基础攻击14；敌人3 HP11/防御3，无小刀已可造成11，小刀+2后伤害13封顶11。因此合法一击可能成立，未改敌人HP/角色攻击强制多回合；手机实际存档攻击值未读到，不能据截图证明所有原版伤害规则。
修改Battle.kt/OpeningBattle、BattlePresentation及MainActivity.kt/GameView。现在先选指令/目标，再提交一次；已计算结果逐步播放，触摸阶段revision保护，抬起事件不能同时确认奖励。绘制不执行战斗计算或结算。
受控Lv4一击、同种多实例、长按/重复输入通过；正常最终录像敌群15两只敌人1，需要两次指令才胜利。

【原版参照】
ROM SHA-256：f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25；Mapper246。
FCEUX2.6.6冷启动正常手柄输入；地图16分区0，敌群11（敌人2/3），哪吒Lv1 HP20 MP0、武器0。未写RAM、未直接胜利。
五状态：入场1785帧；等待2250；行动2400；受击2520；胜利3720；返回野外可见4020。
原版完整战斗录像：artifacts/checkpoint-ui/battle02-original-normal.mp4（无音轨，不能作为App声音证据）。
五组对照：battle02-compare-entry/command/action/hit/result.png，ROM左/App右；App取最终录像24/28/32/33/44秒。
最终App录像：artifacts/checkpoint-ui/battle02-final-normal-with-audio.mp4，实际APK19/c9，正常路线随机敌群15、敌人1两只；没有强制敌群。
差异：随机敌群不同；字体、动作完整度、文字时序/框位置与原版不完全一致，奖励当前合并显示；敌人1名称尚未恢复。不是像素/规则全等价。

【素材与场景】
敌人1→ROM红色人形32×48（名称未确认）；2→臭甲蟲32×32；3→百角海膽40×40。真实银行/调色板与组合图块证据保留，独立图块重建并核对像素，不用完整截图作运行背景。
全部19敌群保留，每只按slot独立实例、HP、站位/触摸区、受击/死亡；逻辑位置x=16+32*slot、y=72。
黑色战场与ROM地平线素材；戰鬥/法術/寶箱/防禦/逃跑指令、队伍状态和结果区域已接入。仅普通攻击/逃跑开放，其他明确禁用。三敌、背景和小角色姿态经统一ContentLoader加载。
ADAPT：GameView现有触控/逻辑时钟与OpeningBattle；新增最小呈现队列，不另建战斗引擎。
REUSE：export_development.py/tile_image、ContentLoader、World/OpeningEncounter、SaveSnapshot及现有发布/诊断链路。依赖仍Media3 1.4.1、WorkManager2.9.1；未新增依赖。
NEW最小范围：原来没有的不可变行动显示快照及阶段输入守卫；不建立动画平台。取消第二套资源工具、更新器页面、日志平台及全量图形研究。
仍缺：敌人1真实名称、完整武器/敌人动画、原版全字库、行动短音效和精确循环。

【战斗过程】
一次确认只执行当前合法攻击或逃跑指令，计算一轮一次。ENTRY→COMMAND→TARGET→ACTING→RESULT/下一指令；固定逻辑时钟依次显示提示、小角色姿态、目标闪烁/死亡和结果。
行动中不接收重复提交；down/up必须同阶段/同命中，阶段revision隔离，后台清输入。结果提交及奖励settled守卫只执行一次。
胜利：当前已开放敌群奖励/成长一次更新统一状态并保存。最终正常录像两轮胜利，+2EXP/+2银两，重启一致。
逃跑：ROM 9:8A49，随机字节b变换(5*b+2+(b>>7)+((b>>6)&1))&255；与(127+敏捷-首个存活敌人敏捷)&255比较。敌人按敏捷降序、slot稳定排序，成功不领奖/不执行敌方行动；失败消耗回合并由敌方继续。当前位置/当前HPMP保存，遇敌计数按原版清0，无人为宽限。Android独立随机流不冒充NES逐帧随机序列。
ROM六次逃跑样本五失败/一成功；App正常探索测试21次尝试、6成功/15失败，不将样本频率当固定成功率。
战败：ROM正常防御至HP0，出现全员阵亡，0:812D清空与0:B795初始化；当前单哪吒新游戏分支回114(8,21)，Lv1 HP20 MP0 EXP0，初始金钱/装备，背包与剧情状态重置。App正常输入因敌方伤害战败，按此更新统一存档。移动端开场已看标记避免暂定旁白重复。
正常战败执行上述规则；App异常退出仍恢复安全检查点。原卡带已有手动存档的加载/继续分支尚未迁移，不宣称全部战败条件已等价。
未完成：玩家命中/暴击、完整行动顺序、法术/战斗物品/防御指令及Boss规则。

【剧情范围】
可以从新游戏→现有开场/114互动→可选仆人赠刀与小刀装备→自由离家→地图16已启用分区正常遇敌/普通攻击、胜利、逃跑或战败，并往返114/16/0。
既有NPC文本与赠刀可玩；开场旁白时机、殷氏100银两仍是Reference暂定，NPC走位和部分flag时机未完全核验，不改成ROM VERIFIED。本轮无新增NPC/剧情，赠刀不重复计作成果。
修复战斗跨阶段触摸与恢复输入；已有对话分页/首次复谈/一次赠刀按原流程回归，没有把开场文本静默略过。
地图0仅地图和出入口；NPC、店屋内景、商店及必要事件未进入可执行包。南海剧情里程碑未完成，地图0内容欠账仍保留。

【验收】
V1 PASS：同指纹ROM五状态+实际正常战斗录像。
V2 FAIL（完整还原）：三敌真实图形/实例、背景、指令/状态已接入，默认不再是数据弹窗；真实名称1、完整动作/字形仍缺。
V3 PASS：真实App可观察单次指令、行动/受击/死亡与结算；受控连点/长按/同种实例/一击通过，不自动执行整场。
V4 PASS（限定当前单哪吒普通、新游戏战败分支）：正常胜利/逃跑成功失败/战败及一次结果保存通过；全战败存档分支和完整战斗规则仍未等价。
V5 PASS：五组ROM左/App右实际状态对照，标明随机组和视觉差异。
V6 PASS（当前区段）：正常路线进入战斗，赠刀/装备、三图连接、存档重启、INPUT-01及实际覆盖升级通过。
V7 PASS（已覆盖窗口）：开工/发布后实查，最终APK/content/hash一致，App有声录像及实际HTTPS诊断读回；真机人工验收NOT_RUN，不以零上报错误证明根治。
整体PARTIAL；没有用测试数替代缺失的原版内容。

【工程与回归】
构建：./build-android.ps1 -LocalOnly；最终Gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline，BUILD SUCCESSFUL。
Python：.venv/Scripts/python.exe tools/run_phase1_tests.py，90/90。
JVM：:app:testDebugUnitTest，37/37。
Go：F:/apps/go/bin/go.exe test -json ./...（server），12/12，含严格云存档字段兼容；已原地部署Fengshen服务，未迁移数据库。
发布安全：./tests/test_publish.ps1，6/6；./publish-apk.ps1，PUBLISHED_AND_VERIFIED。
Android：adb -s emulator-5554 shell am instrument -w -e class org.fengshen.dev.ContentTest,org.fengshen.dev.TouchTest,org.fengshen.dev.ApkUpdateTest,org.fengshen.dev.AudioDiagnosticsTest,org.fengshen.dev.AudioBattle02Test org.fengshen.dev.test/android.test.InstrumentationTestRunner。
全量47项首轮46通过/1失败：容量测试在线上传竞态。保留断言与失败记录，测试隔离网络后该项+新增受控用例2/2，AudioDiagnostics整类6/6；合计覆盖48个不同回归用例，不冒称一次全量48通过。发布后App更新/诊断读回3/3。
正常路线/逃跑战败、最终录屏各独立运行通过；没有写真实云存档。真实账号云恢复未执行；故意崩溃仪器未混入回归。
最终录像70秒/2340×1080，来自Fengshen_A_API35 Android15/API35/x86_64；Windows WASAPI实际播放器输出，RMS762/峰值6765，无外加配乐。屏录与声音非采样级同步，AVD屏录约4.37fps不证明真机帧率。
旧c8档App覆盖升级至c9后位置/物品/装备/赠物flag一致；c1..c8兼容，正常战后保存/force-stop/restart一致，无卸载清档。升级替换结束仪器进程属驱动预期，验收另核安装版本/hash/存档，不能当作普通测试成功码。
WORLD-01三图往返、碰撞和INPUT-01四向沿墙参数保持，34格白名单没有恢复。
一加13T：NOT_RUN（有服务端真实样本，无本轮ADB/人工手感与声音验收）。
停止本轮；不自动进入地图0或其他内容开发。

END_DELIVERY_REPORT


DELIVERY_REPORT

task_id: TOWN-01
status: READY_FOR_REVIEW（限定已确认三店；不表示地图0/南海完整恢复）

【版本】
实际基线: v19 / 0.7.8-battle-02，保留全部现有工作；本轮先发布v20，最终原版问句校正后递增v21。
新APK版本/versionCode: 0.8.1-town-01 / 21。包名与签名保持，不需卸载。
APK路径: F:/apps/fengshen-remake/artifacts/checkpoint-ui/fengshen-town-01-v21-debug.apk
下载地址: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=21
APK SHA-256: ed700b85c79bd3abfdc64dbf35cfa517b68d4ad9b4eaa7158c3ad20aa3dd30d1
内容版本/来源/hash: opening-segment-001-c11；ROM/运行证据与保留标注的既有暂定字段；manifest SHA-256 3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147。最终APK内41文件逐一核验。

【开工巡检】
执行时间与命令: 2026-10-01 06:21:55 UTC，.\check-runtime.ps1。发布前追加08:21:06 UTC同一命令。
实际查询版本: 开工可信19/18；追加可信20/19，均由发布记录确定。
模拟器/真机样本: 开工654事件，其中83测试事件；普通12模拟器会话、2真实设备会话（两个版本各有PKX110样本）。观察00:14:43—06:03:18 UTC。服务端真机样本不是本轮连接手机验收。
发现问题: 上传窗口无普通ERROR；不能据此证明所有未上传过程正常。追加20/19有364事件/108测试、普通11模拟器/1真机会话。
纳入本轮修复的问题: 地图0方向边缘被未开放类别误挡；未接入真实门/室内/店员/交易；装备预览与属性缺失；最终百货店问句简化已修正。
仍未解决问题: 历史audio_play_error/ERROR_CODE_TIMEOUT根因UNCONFIRMED；旧手机更新误报原因仍待真机确认。只保留脱敏旧故障结论，不归档完整旧运行日志。

【原版依据】
ROM指纹: f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25；Mapper246。
物品/装备显示方式依据: module2 CE88/F002组合预览、CF24选择CHR银行212/214/216；E95E/E960贡献表及EF88/EF9B/EFA6哪吒槽位列表；原装备截图equipment-probe-final/frame-1170.png。
商店入口与交易流程依据: module0 CBE3/CBFB门口类别21/22/23→17/18/19；module8 DFD8/DFDE的FE原位置返回；module2 E685/E68D商品/价目，BEB8→EE8B半价出售例程。
原版截图/录像位置: private-derived/town01-weapon-trade/006218-transaction.png、town01-armor-trade/006191-transaction.png、town01-items-edges/006367-transaction.png；正常手柄取证及所有片段offset/length/hash在game-data/provenance/town01.json。当前原版参照为截图/按键记录，非新增原版录像。
哪些已验证: 三店入口/室内/柜台站位/返回、8商品名字/价目、6装备预览与限定贡献、药草实际卖出。Reference棍棒/鐵劍与本ROM手刀/長劍冲突，使用ROM名字。
哪些仍待验证: 跨类别出售完整操作、已穿装备直接替换副作用、药品使用条件/效果、客栈与住宅事件。未把整条记录/全量canonical提升VERIFIED。

【地图0商店/建筑】
已确认建筑列表及入口方式: 兵器店门(13,18)→17；防具店门(6,19)→18；百货店门(24,25)→19，均正常走门触发。
是否已接入室内/功能场景: 三张真实16×15室内网格/图集分别接入，退出返回各自原门位置。
店员/交互点情况: 17/18店员(7,5)，站(7,7)隔柜台交谈；19店员(6,5)，站(6,7)。原版问句来自实际截图，未编造。
仍未接入的建筑及原因: 客栈(6,25)/类别25的收费/恢复效果未核；类别26/27/28住宅和特殊建筑缺必要事件数据。保持明确开发限制，不连同一假室内。

【物品/装备展示】
原版是否有独立图像: 当前装备有；已接入6张。当前原版百货店药品列表未观察独立预览，保留名称/数量/价格，不伪造图标，不推断全游戏都无药品图标。
Android当前采用的展示方式: 原版黑底白框、物品名称/数量、装备槽位/图像/贡献与实际总属性；保留已确认的角色HUD和手机模态面板。点选/翻页/按钮命中为触摸适配，不宣称原版像素等价。
已接入图像/图块映射: 小刀/手刀/長劍→weapon0/1/2；肚兜/布衣/麻鞋→armor0/1/28；ROM组合图块解码，nearest-neighbor。
已展示属性字段: 当前角色等级/HP/MP/EXP及原有可读属性；装备攻击力、防御力、迴避力，槽位右手/左手/身体/脚。左手当前为空，未添加现代槽位。
仍缺内容: 药品效果/使用说明的可执行迁移、左手商品及直接替换、全字库/精确调色和原版逐像素菜单。

【交易逻辑】
已实现商店: 17兵器、18防具、19百货；买/卖/不要及退出真实可用。
可买商品: 小刀15、手刀50、長劍120；肚兜30、布衣80、麻鞋200；藥草15、牛黃丸20（两）。
可卖商品: 各店当前已确认库存对应的上述商品；跨类别卖出尚未迁移，属于开发限制。
价格来源: 目标ROM价目表；卖价max(1,floor(买价/2))，原版药草卖出+7另有运行证据。
购买结果: 正常流程买手刀扣50、肚兜扣30、药草扣15，真实背包各+1，不自动装备；钱不足/数量上限等失败不扣款。
卖出结果: 正常药草卖出加7、数量减1。
是否支持装备后属性变化: 是；先解除小刀，再装备手刀，攻击10→8→13，物品转移与存档一致。防具定义/卸装/重装及贡献通过数据测试。直接替换暂不开放。
是否存在重复结算问题: 已执行每笔结果10次连点、后台取消和重入断言，没有重复扣款/获物；合法再次购买须明确重新提交。

【验收】
T1: PASS（本轮三店/8商品范围），目标ROM片段+正常原版截图+provenance。
T2: PASS（三店），正常进17/18/19与各自返回；未知客栈/住宅仍未开放。
T3: PASS（范围内商品），三店实际买入及药草卖出，资金与数量断言；跨类别出售仍缺。
T4: PASS，统一状态、装备转移、数量/资金、保存与真实force-stop/restart一致。
T5: PASS（当前6装备/药品列表），原版图块/属性已显示，不再只有小刀×1；全字形/全部物品仍非等价。
T6: PASS（现有区段），最终App从新游戏经114/16/0到三店，买卖、退出、装备、继续；无调试传送。
T7: PASS（本次窗口），发布字节与内容hash校验、App事件实际认证读回、巡检重试成功；一加13T NOT_RUN。

【工程与回归】
复用/实际修改: ADAPT tools/forensics/fengshen246.py::extract_town_shops、tools/export_development.py、Content.kt::ContentLoader、Core.kt::Scene.probeFrom、MainActivity.kt::GameView、SaveState.kt::OpeningEquipment/SaveSnapshot、Battle.kt既有装备贡献入口。NEW仅现有统一状态上的TownTrade买卖计算和SHOP层；REUSE World逐格/出口、GameAudio、Diagnostics、云存档、原发布/录屏工具。
依赖: Media3 1.4.1、WorkManager 2.9.1、JUnit 4.13.2，未新增依赖/引擎。取消全游戏商店研究、第二套导入器、现代商城/编辑器方案，只恢复三店当前内容。
实际构建和测试命令: .\build-android.ps1 -LocalOnly；.\phase1.ps1 test；server目录F:/apps/go/bin/go.exe test -json ./...；.\tests\test_publish.ps1；adb -s emulator-5554 shell am instrument -w -e class org.fengshen.dev.ContentTest,org.fengshen.dev.TouchTest,org.fengshen.dev.ApkUpdateTest,org.fengshen.dev.AudioDiagnosticsTest,org.fengshen.dev.AudioBattle02Test org.fengshen.dev.test/android.test.InstrumentationTestRunner。
通过/失败/跳过数量: 最终构建BUILD SUCCESSFUL；Python97/97、JVM37/37、Go12/12、发布安全6/6。完整Android在v20首轮49通过/1失败/0错误，固定逃跑手势走到真实墙；保留断言，按实际落点选择可走邻格后1/1通过。当前v21 Content12/12、正常三店1/1、HTTPS读回1/1、取消1/1，真实20→21系统升级通过；未冒称单次全量50通过。真实账号云恢复NOT_RUN（相关用例条件跳过），没有覆盖真实云进度。早期AAPT2链接失败与初次商店路线测试失败重试后通过，不算成功尝试。
最终App正常流程录屏: F:/apps/fengshen-remake/artifacts/checkpoint-ui/town01-v21-final-normal-with-audio.mp4；Android15/API35 x86_64模拟器，实际播放器输出，无外加配乐。录屏/声音非采样级同步，不能证明手机性能。
关键截图: artifacts/checkpoint-ui/town01-v21-{town,shop17,shop18,shop19,inventory,equipped-handknife}-emulator.png。
旧档升级结果: 已实际App下载19→20、20→21并系统确认；c9→c10→c11的位置/物品/装备/flags保持，无卸载清档。c1..c10兼容保留；最终保存/停止进程/恢复相等。
WORLD-01/INPUT-01/BATTLE-02回归: 三图连接、四向/沿墙/取消、赠刀、HUD/输入隔离、普通战斗与已实现的逃跑/新游戏战败分支保留；正常输入逃跑复测22成功/16失败，最终敌方伤害战败。BATTLE-02其余规则未因此完成。
一加13T: NOT_RUN；仅emulator-5554连接。

【发布后巡检】
执行: .\check-runtime.ps1 -Stage postflight。08:31:31 UTC首次SSH ECONNRESET/握手前断开，失败记录保留；08:33:01 UTC重试实际成功。
实际保留版本:21/20；事件42/55，总97，其中41测试；普通11模拟器会话，0真机。最近30事件可确认v21有4个普通标记会话（QA模拟器，不当作手机用户），不是该版全部会话统计。
窗口:08:13:38—08:29:45 UTC；无普通上传ERROR，cleanupFailures=0。仅此覆盖，不宣称历史音频已根治。
App读回:81ac9681-d5b5-4663-9ad8-979c93d4ff20，version21/test=true/c11与最终hash匹配。只覆盖Fengshen独立APK/version.json，Language内容未改。

【未完成清单（累计；只有实际完成才移除）】
1. TOWN-01：跨类别出售完整流程、原版已穿装备直接替换及满包副作用规则、左手/其余商品迁移；TOUCH-UX-01已实现Android原卸下→装备等价原子封装，不替代原版规则取证；牛黃丸实际使用效果和合法场景/目标。TOWN-02/v23已关闭药草地图/菜单使用及补给闭环；战斗药草指令仍归第4类，其他药品不关闭。
2. 地图0：客栈收费/恢复/离店，住宅与特殊建筑、其余NPC/宝箱/剧情事件及条件；新室内原版BGM尚未核实并未启用。
3. WORLD-01后续：地图16其余遭遇区/特殊格/事件与后续必经连接；南海龙宫/Boss/胜后状态未连续打通。34格白名单已解除，三图往返不再欠账。
4. BATTLE-01/02：玩家命中/暴击、完整行动顺序、法术/战斗物品/防御指令、Boss行为；原卡带已有手动存档后的战败加载分支。当前已核单哪吒普通逃跑和新游戏战败分支已实现，不再写成全部逃跑/战败未实现。
5. 战斗展示：敌人1原名、完整武器/敌人动作、原版全字库、准确文字时序/框位/逐项奖励与调色差异；NES随机序列不与Android独立随机流等价。
6. AUDIO-LOG-01：历史超时根因UNCONFIRMED、四类短音效、精确循环边界、长时真机稳定性；本轮12播放器/6Activity生命周期无播放器错误，AVD底层音频HAL曾有I/O警告，不以状态测试证明整个设备音频无故障。
7. 开局内容：开场旁白时机、NPC移动、部分首谈/复谈/事件flag时机；殷氏100金额已增加局部ROM写入证据，整事件仍未完全验证。后续角色入队/法术/剧情尚未迁移。
8. 更新器/设备：一加13T原误报根因与覆盖升级、触控、声音、长时性能实机验收待反馈；模拟器不能代替。
9. 云端/内容：实际账号本地丢失/多设备恢复验收NOT_RUN；Go版本化内容包发布及Android下载/校验/缓存/离线回退尚未接通。现有pgsql个人存档保留；APK自升级不等于内容服务端化。
10. 正式原版/发布门槛：全量地图/剧情/规则与canonical仍未开放，南海龙王里程碑未完成；不重启全量研究，不用未知阻塞现有已核功能。
距离下一原版剧情节点最近3个阻塞: 地图0必要NPC/服务事件；通往南海的真实连接/条件；剧情Boss与胜后状态。

本轮停止，等待手机体验反馈。

END_DELIVERY_REPORT


DELIVERY_REPORT

task_id: ANDROID-CI-01
status: PUBLISHED_AND_VERIFIED（云构建/同签名/原服务器发布已交付；游戏内容里程碑仍PARTIAL）

## v22 云端正式交付（2026-10-01 最新状态）

状态：`PUBLISHED_AND_VERIFIED`；ANDROID-CI-01 本轮完成。v22 / `0.8.2-ci-release` 于北京时间 2026-10-01 23:17:27（UTC 15:17:27）发布；生产 workflow 于 23:21:47 全部成功。游戏仍为 c11 开发内容，不代表南海龙王或完整原版里程碑完成。

- APK 来源提交：`f7dfea747999f6b1ee497179f98eb6cc7ed94f6a`；[构建 36882142423](https://github.com/antpan5608-san/game-fengshen/actions/runs/36882142423)、[发布 36882936289](https://github.com/antpan5608-san/game-fengshen/actions/runs/36882936289) 均 completed/success，构建与发布同一 main 提交。后续文档提交不改变已审核 APK 来源。
- 下载：[v22 APK](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=22)；11,316,827 字节，SHA-256 `5cecfe1c3a4208ea077ef8da7fb338e46ad9419dc2551dfb7a61952b230bf72f`。包名 `org.fengshen.dev`，既有签名证书保持；release 非 debuggable、版本、签名、内容及旧包兼容已由原 runner 复核。
- c11 / `opening-segment-001-c11`，manifest SHA `3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147`。本任务于北京时间 23:23:22 独立下载正式服务器 APK，完整 SHA/大小与 version.json/构建 receipt 一致，原 `tools/ci_apk.py::content` 验证 42 文件（包含 manifest；41 内容文件）及全部文件 hash。
- 自动审批实际成功：原 `android-publish.yml` 的 approve job 使用仓库 Secret `FENGSHEN_DEPLOY_REVIEW_TOKEN`，通过原 reviewer 政策批准 fengshen-production。随后 publish job 全部成功，无需再次人工点击。现有集成本身的 Deployments API 403 不影响这个已验证的 runner 途径；不代表当前云任务拿到了该 Secret 值。令牌到期/撤销或保护规则变化仍可能明确失败。
- 实际 `./check-runtime.ps1` preflight：UTC 15:17:21，可信21/20，418事件/41测试事件，普通11模拟器+1真机会话，窗口08:13:38—14:11:04，NO_ISSUES_OBSERVED。
- 实际 postflight：UTC 15:21:41，可信22/21，363事件/14测试事件，普通6模拟器+1真机会话，窗口08:27:54—14:11:04，NO_ISSUES_OBSERVED；清理失败0。当前保留样本来自v21，v22尚无上传样本；历史设备日志不是本轮手机验收，不证明v22运行或历史音频根因修复。
- 保留既有两版规则及每版20MiB容量；本次上传只覆盖 Fengshen APK/version.json 两对象。ROM、密钥和电脑未提交资料没有由本次迁移取得；无新游戏源码、UI或功能开发。
- 验证：实际云端 testReleaseUnitTest/assembleRelease/签名/内容与旧包兼容检查通过；本地自动审批实际 Bash fixture14项、既有 transport12项通过。没有本轮 adb/模拟器/App录屏或真实账号云恢复测试；一加13T覆盖升级、触控、声音、长时性能均 NOT_RUN。

下一迭代继续复用现有构建与发布，versionCode 必须大于22；既有默认22是首次构建的历史值，必须显式覆盖。累计十类未完成项与下一原版节点三个阻塞全部保持，详见最新交付报告。


【复用与最小修复】
REUSE 原 android-build.yml/build-ci.ps1/ci_apk.py/check-reviewed-apk.ps1、check-runtime.ps1/publish-apk.ps1/register-release.ps1、独立OSS目标与原个人存档/诊断最近两版机制。ADAPT 原 android-publish.yml 两处报告读取为 -AsHashtable，支持合法空 contentVersion 映射键；在同一workflow增加approve job，先确认成功手动main同提交构建，再批准唯一目标环境。不新增workflow、上传凭据体系或游戏实现；保留主分支、reviewer、同产物hash、包/签名/内容/巡检与公网字节校验。依赖保持：Windows2022、JDK17、SDK35/build-tools35.0.0、Gradle8.10.2、AGP8.7.3、Kotlin2.0.21、ssh2 1.17.0、ossutil2.4.0。

【失败和边界】
36868615998：审批与preflight通过，但旧JSON读取失败，上传前终止；失败保留，后续真实生产运行验证AsHashtable修复。36871347819：旧提交等待审批时取消，尚未上传，避免两份v22竞争。GitHub集成review API403保留；后续runner Secret已实际批准。官方artifact下载redirect的代理CONNECT403未绕过；通过官方日志API取核验receipt，成功发布后再独立下载正式APK。没有禁用TLS/删除平台代理/删除环境保护，不读或输出Secret值。原版ROM/电脑未提交证据、手机与本地签名密钥未迁移，云CI使用已锁定c11包。最近窗口无ERROR不证明未上传过程或v22手机正常。

【可核验本地结果】
/workspace/game-fengshen/artifacts/published/fengshen-remake-v22-release.apk
artifacts/published/v22-public-verification.json、v22-workflow-verification.json、artifacts/ci/v22-final-build-log-receipt.json、auto-approval-test-results.json 均位于Git忽略目录；仅脱敏验证摘要和APK，不另归档原始旧诊断日志。云构建APKartifact保留30天，单元测试XML14天，生产receipt30天；正式对象保持既有.bin分发契约，手动下载安装需去掉文件名末尾.bin，应用设置→检查应用更新沿用原路径。

【未完成清单（累计；只有实际完成才移除）】
此处为十类累计欠账的权威清单，按TOWN-02、TOUCH-UX-01与NANHAI-01实际交付更新；原有“本轮”及播放器/模拟器次数指TOWN-01历史验证，不是云端CI重跑。原历史条目可由Git追溯。

1. TOWN-01：跨类别出售完整流程、原版已穿装备直接替换及满包副作用规则、左手/其余商品迁移；牛黃丸及其他药品实际使用效果和合法场景/目标。TOWN-02/v23已关闭药草地图/菜单使用与补给闭环；战斗药草指令仍归第4类。TOUCH-UX-01已实现Android原卸下→装备等价原子封装，不替代原版直接替换/满包规则取证。
2. 地图0：客栈收费/恢复/离店，住宅与特殊建筑、其余NPC/宝箱/剧情事件及条件；新室内原版BGM尚未核实并未启用。
3. WORLD-01后续：地图16其余遭遇区/特殊格/事件与后续必经连接；NANHAI-01已关闭海底25/龙宫97必经连接、必要NPC、此Boss胜后及正常保存恢复；北部group4敌10/11行为7、两宫内宝箱奖励与后续连接/事件未开放。34格白名单已解除，三图往返不再欠账。
4. BATTLE-01/02：完整暴击语义、多角色/其他分支行动顺序、法术/战斗物品/防御指令及其他Boss；NANHAI-01已恢复当前哪吒武器-1/0/1/2的已核命中/倍伤/敏捷排序、南海龙王物理/冰/逃跑与掉落；原卡带已有手动存档后的战败加载分支。当前已核单哪吒普通逃跑和新游戏战败分支已实现，不再写成全部逃跑/战败未实现。
5. 战斗展示：敌人1原名、完整武器/敌人动作、原版全字库、准确文字时序/框位/逐项奖励与调色差异；NES随机序列不与Android独立随机流等价；敌4–7名称及六神丸名称仍PROVISIONAL，南海龙王原静态图/位置已接入，不代表完整动作。
6. AUDIO-LOG-01：历史超时根因UNCONFIRMED、四类短音效、精确循环边界、长时真机稳定性；本轮12播放器/6Activity生命周期无播放器错误，AVD底层音频HAL曾有I/O警告，不以状态测试证明整个设备音频无故障。新海底/龙宫BGM来自固定Reference，场景关联PROVISIONAL，目标原曲/精确循环仍未核；本轮声音NOT_RUN。
7. 开局内容：开场旁白时机、NPC移动、部分首谈/复谈/事件flag时机；殷氏100金额已增加局部ROM写入证据，整事件仍未完全验证。后续角色入队/法术/剧情尚未迁移。
8. 更新器/设备：一加13T原误报根因与覆盖升级、触控、声音、长时性能实机验收待反馈；模拟器不能代替。
9. 云端/内容：实际账号本地丢失/多设备恢复验收NOT_RUN；Go版本化内容包发布及Android下载/校验/缓存/离线回退尚未接通。现有pgsql个人存档保留；APK自升级不等于内容服务端化。
10. 正式原版/发布门槛：全量地图/剧情/规则与canonical仍未开放，NANHAI-01南海龙王连续里程碑已通过正常Android验收，完整全游戏门槛仍未完成；不重启全量研究，不用未知阻塞现有已核功能。
距离下一原版剧情节点最近3个阻塞: 后续海域真实连接/事件（数据/原版证据）；group4敌10/11行为7（数据/代码）；下一剧情战斗与必要指令/奖励（数据/代码/正常运行证据）。村庄可选服务不新增为路线前置。


本轮云端v22发布完成；不自动展开新玩法区段，等待设备反馈或下一项具体开发任务。

END_DELIVERY_REPORT


DELIVERY_REPORT

task_id: TOWN-02
scope_revision: herb-use-and-supply
status: BLOCKED
execution_kind: IMPLEMENTED

本轮实施并验证的是巡检与可信基底接续工具；药草业务功能未实现，不能称为内容已完成。必要原版使用证据仍缺，取回单个已登记ROM的许可尚未收到。

【基线与修改】
仓库、分支、开始提交: /workspace/game-fengshen，main，1f9a72f12cbb4dc651a49f8fd952ffeb8f10b2bf；开始工作区干净。
结束提交/PR或未提交diff: 巡检接续提交d0ab51167161c65c7a91c6dbaec2192aa505ce1a、基底验证提交e445ca200a162ca460fe8d8cd3ed21d612a386e1已推送；本报告、skill与最终门禁修订随本轮收尾提交，实际结束HEAD见交付正文。
实际基线APK/内容: 游戏v21/c11，可信已发布v22/0.8.2-ci-release/c11；APK来源f7dfea747999f6b1ee497179f98eb6cc7ed94f6a，后续文档/工具提交不同不代表旧发布错误。
本轮修改文件: 原android-publish.yml、android-build.yml、check-runtime.ps1、tests/test_environment_review.py、AGENTS.md、docs/current-task.md/android-ci.md/delivery-status.md、docs/history/authorization-through-android-ci-01.md、唯一内容迭代SKILL.md。没有修改Android业务源码、内容pin或服务器代码。
玩家实际新增能力: 无；药草使用未启用。
仅保留的历史成果: 三店买卖/返回、三图往返、装备、普通战斗/逃跑/新游戏战败、输入、音乐/诊断、更新器和现有本地/云存档。

【本轮巡检】
执行位置: 原Actions受保护fengshen-production环境。当前环境pwsh不可用，初始NOT_AVAILABLE；通过最小只读适配恢复。
命令/run_id: gh workflow run android-publish.yml --ref main -f mode=inspect；36888836939，d0ab511。approve/inspect成功，publish跳过。
查询时间与版本: 2026-10-01T16:02:05.7395193Z，可信22/21。
正常/测试、模拟器/真机: 总452事件，14测试事件；普通8会话（6模拟器/2真机）；storedVersionCounts=21:370、22:82。窗口08:27:54.845931639Z—15:48:08.659569069Z。
发现问题、修复及剩余: 窗口NO_ISSUES_OBSERVED、errors={}、cleanupFailures=0。没有发现需修复的当前上传故障；历史音频超时根因仍UNCONFIRMED。
查询限制: 仅已上传样本，不代替本轮手机/App验收。inspect只GET summary，未调用发布/登记/清理，不注入OSS凭据，只保留脱敏摘要，无samples/完整旧日志。

【资源与证据】
可信基底APK: 构建36882142423的不可变fengshen-signed-apk，原runner在验证运行36889913121实际成功下载，核对receipt来源提交/run/hash并验签、包名、版本与内容。当前任务直接下载该artifact返回HTTP403，签名URL未输出、未绕过代理。
公网回退也已实际核验: 重新读取version.json并下载完整正式对象，不依赖?v=22；版本22，大小11316827，SHA256=5cecfe1c3a4208ea077ef8da7fb338e46ad9419dc2551dfb7a61952b230bf72f；官方SDK35工具验签/包名/非debuggable通过。
基底内容: opening-segment-001-c11，manifest SHA256=3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147，42文件含manifest（41内容文件）。原ci_apk.restore已恢复到原assets入口。
药草稳定ID: rom.medicine.0，category-local originalId=0；名称藥草、买价15/卖价7/maxCount10。
原版使用规则: 未核；地图可用性、合法目标/不可用状态、恢复计算、上限截断、满HP/取消/失败消耗及消耗时机均不能由买卖证据推断。
证据来源与范围: 已提交town01.json与恢复的scene.json；verifiedFields仅name/originalId/category/buyPrice/sellPrice，remainingUnknown明列medicine effects/use conditions。现有extract_town_shops只读取商品/名字/价格/相关买卖例程，不提取药效。
必要私有输入取得情况: 无。用户明确答复目前没有接入私有资源；未读取电脑F盘。
最小未取得输入: private-inputs/town02/target.nes，一个完整iNES/Mapper246文件，1048592字节，SHA256=f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25；用途仅药草行为局部取证。已有rom-acquisition.json登记固定来源，但game-data-inventory.md的旧准入规则禁止公共补ROM，新的单文件取回请求尚待批准，未下载。
是否要求完整历史目录: NO。地图/音频/图集已复用可信c11；本轮无需1958个历史文件。新的正常输入/受控边界证据需在取得ROM及原版运行能力后局部生成。

【实际功能】
购买→离店→使用路径: 前两步沿用历史已有功能；本轮没有完成使用步骤，不计正常流程PASS。
可用场景与目标、效果与消耗: 未核，未实现。
取消、满血、非法目标、重复输入: 原版规则与实际App均NOT_RUN；没有猜数值、消耗药草或改HP。
HUD/背包/存档一致性: 本轮未执行药草流程验证，旧实现保留，未清档/写用户云进度。
是否人为增加出村前置: NO。

【内容与CI】
内容是否改变: NO；基底与目标仍同c11/hash，未伪造新内容版本。
复用函数: ci_apk.fetch/verify_apk/content/receipt/restore、原build-ci.ps1与两份Actions、check-runtime.ps1/runtime-admin.mjs。
实际方法: 原build workflow增加verify-base读取成功main构建的不可变APK，先审receipt/来源提交/run/字节，再验签/内容并restore；不生成新APK，不接签名/服务器Secrets。生产approve/publish新增实际唯一成功build job检查，验证基底成功不能伪装APK构建。
新内容进入原workflow: 因没有可导出药草新定义，此链路NOT_RUN，未关闭pin校验或伪装ZIP。
干净工作区可重复性: 实际checkout源e445ca2的runner完成基底restore，12项transport测试通过；只证明已有基底恢复，不证明新药草构建可重复。
构建run_id/来源提交/最终APK: 36889913121是验证运行，不是新APK构建；源e445ca2，实际build job跳过。没有本轮最终APK hash。

【验证】
原版药草依据、合法使用、边界/取消、重复提交、药草保存冷启动、旧档覆盖升级: 全部NOT_RUN。
三店/装备/三图/战斗/INPUT-01: 本轮未重跑App回归，历史结果保留，不计本轮PASS。
正常App录像: 无。
实际工具验证: Linux python -m unittest discover -s tests -p test_ci_apk.py，12/12；python tests/test_environment_review.py，19/19（真实Bash代码/隔离API，无真实发布）。原Windows runner verify-base actual下载/receipt/验签/restore与12项测试成功；inspect原PowerShell解析/脱敏摘要实际成功。
工具失败修正: 本地aapt首轮缺同SDK包lib64/libc++.so，补齐经官方checksum验证的该库后实际verify/restore通过，首轮不计成功。
Android能力: 当前环境无adb/SDK平台/emulator/KVM；仅补官方aapt/apksigner及依赖验证APK。原Linux runner实际adb=true、emulator=false、AVD空、KVM不可读写；未启动设备，不以adb存在写App已运行。
一加13T: NOT_RUN。

【复用与skill】
实际复用: 既有恢复/验证/巡检/SSH bridge/自动review，Kotlin与游戏模块未复制；没有第二套工作流/设备农场/导入器。
skill: .agents/skills/fengshen-content-iteration/SKILL.md；原无对应skill，本轮仅新增此一个。记录已验证的基底恢复、局部证据定位、原inspect/verify-base入口与相关测试，不含版本号、Secret或历史日志。
已验证步骤: 当前Linux验包/restore及12/19测试，原runner不可变基底验证和只读巡检。
过期规则收敛: 原AGENTS与ANDROID-CI任务原文完整归档；AGENTS只留稳定规则，current-task唯一TOWN-02，未删除历史决策/欠账。十类清单仍在权威位置。
未验证方法: 药草原版取证、局部新内容导出及进入签名CI、App仪器/正常操作与真机；skill明确待核。无提速百分比声明。

【产物与发布】
本轮候选APK: 无；artifacts/town02/base/fengshen-remake-v22-release.apk仅旧可信基底，不是新功能候选。
可下载验证artifact: 36889913121的fengshen-base-verification（仅receipt）；36888836939的fengshen-runtime-summary（脱敏摘要）。本地artifacts/town02/保留同类回执，均Git忽略。
版本/versionCode: 未产生新版；正式版本仍0.8.2-ci-release/22。
签名/内容校验: 基底包名org.fengshen.dev、既有signer 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6、内容/hash/大小通过。
发布状态: NOT_PUBLISHED（本轮）；旧v22正式对象保持，不修改Language。
现有正式地址: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=22；hash=5cecfe1c3a4208ea077ef8da7fb338e46ad9419dc2551dfb7a61952b230bf72f。
发布后巡检: NOT_RUN，本轮未发布。
未发布原因: 无药草原版规则证据、未实现使用功能、无实际App闭环验收；工具/规则/skill改动不作为游戏发布理由。

【累计欠账】
权威位置: docs/delivery-status.md的ANDROID-CI-01最近完整十项清单（当前598行起），不复制全文；仍10类。
真正关闭的游戏项目: 无，药草使用未关闭。工程接续已补只读巡检/不可变基底验证，不冒充游戏完成。
仍阻塞: 单个ROM的合法取得及药草原版规则/运行证据，后续App运行验收；已有音频、更新器、其他物品等欠账保持。
南海下一真实节点最近3个阻塞: 地图0必要NPC/服务事件；真实南海连接/条件；剧情Boss及胜后状态。药草为可选补给，不新增出村条件。

END_DELIVERY_REPORT


DELIVERY_REPORT

task_id: TOWN-02
scope_revision: herb-use-and-supply
status: READY_FOR_REVIEW（地图/菜单药草闭环；不是完整物品或南海里程碑完成）
execution_kind: IMPLEMENTED

【基线与修改】
仓库/分支: /workspace/game-fengshen，main；开始98edf3d31cd49679dae83824eaac0fa0208d2859，工作区干净，未reset/clean。
游戏基线v21/c11；发布基线v22/0.8.2-ci-release，APK来源f7dfea747999f6b1ee497179f98eb6cc7ed94f6a。
本轮APK来源: 77cdc6b7f822628e0fe8b2a4292e6d396d9089f3；交付文档收尾提交另记，不改变已审核APK来源。
修改文件: Content.kt、MainActivity.kt、SaveState.kt、HerbUseTest.kt、TouchTest.kt、app/build.gradle.kts；原export_development.py、ci_apk.py、record_app_audio.py、build-ci.ps1及两份Actions工作流；ci/content-source.json、check-reviewed-apk.ps1、run-town02-runtime.sh、town02-herb.json、probe-town02-herb.lua、相关Python测试；AGENTS.md、当前任务/CI及冲突规则文档、既有内容迭代skill。
新增能力: 正常药店买药草→离店→地图物品面板对存活角色使用→HP/数量/HUD/存档同步→停止进程重启继续。
仅保留的历史成果: 三店/三图、赠刀/装备、现有普通战斗/逃跑/新游戏战败、全屏输入/HUD、音频/诊断、更新器、本地及云存档。未新增牛黃丸、客栈、新地图、Boss或战斗物品。

【本轮巡检】
位置: 原Actions受保护环境；本地无PowerShell和服务器凭据，未把runner Secrets当成本地凭据。
开工: gh workflow run android-publish.yml --ref main -f mode=inspect；run36892719605，approve/inspect成功、publish跳过。
查询: 2026-10-01 16:32:41 UTC，可信v22/v21；452事件、14测试事件，普通8会话（6模拟器/2历史真机）；窗口08:27:54—15:48:08。NO_ISSUES_OBSERVED、cleanupFailures=0。
限制: 仅上传日志；历史真机不是本轮真机验收，无ERROR不证明所有运行过程正常。历史音频超时根因仍UNCONFIRMED。发布前18:01:44 UTC再次执行原check-runtime.ps1，仍452/14及22/21，NO_ISSUES_OBSERVED；发布后结果见产物部分。

【资源与证据】
可信基底: 原不可变构建36882142423，runner实际下载并核对receipt；本地公网回退重查metadata和完整字节，未把?v=22当作不可变保证。
v22 APK SHA-256: 5cecfe1c3a4208ea077ef8da7fb338e46ad9419dc2551dfb7a61952b230bf72f；11316827字节。
c11 manifest: 3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147。
药草ID: rom.medicine.0（原版medicine局部ID0）；现有买15/卖7/上限10保留。
新输入仅一个目标ROM，private-inputs/town02/target.nes，1048592字节、Mapper246、完整SHA f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25；匹配后直接复用原偏移/Reader，不重做全量逆向。
证据: game-data/provenance/town02-herb.json；module2 9651药效、A22C消耗、9615确认/取消、9AA4目标选择。FCEUX实际执行六类受控状态实验：受伤5→55，近满95→100，满100→100且消耗1；取消、战败目标、无物品均不改变HP/数量。原版fixture明确是CONTROLLED_STATE_EXPERIMENT，不能冒充正常游玩录像。
规则: 地图/菜单可用；存活队员合法；恢复50HP并截断maxHp，成功消耗1，满HP也消耗；取消/非法目标/无数量不消耗；确认之后结算。战斗物品继续未实现。
未取得: 原电脑历史私有目录、手机和本地生产密钥仍未迁移，不阻塞本次已有证据的药草实现。只需一加13T后续设备验收，不再要求完整1958文件/历史目录或全部源码。
许可: ROM来源许可UNKNOWN；hash匹配只证明文件一致，个人用途不等于第三方授权。

【资源复用】
公开资源: luzeming0211/fc固定提交23d6234710a5e09db33b17aa21722eb2bc79945e中的单个目标NES；Debian官方FCEUX/Lua5.1/Xvfb、官方Android SDK及匹配JDK-headless。来源/日期/hash/许可记录复用既有provenance，原ROM/回放不进公开Git或artifact。
现有工具恢复: ci_apk验证/恢复c11；原export_development局部生成药草定义；原Reader和FCEUX Lua仅调查本功能；未变地图/图集/音频逐字节复用。
数据状态: 本轮药草规则有目标ROM及实际受控运行证据；未新增凭空或暂定药效。其余既有暂定字段不升级为全量VERIFIED。
取消等待: 公网补ROM逐链接确认、仅F盘历史输入、迁回整目录及“先有新APK才能导出新APK”的循环依赖已解除；没有删掉数据/凭据/测试/发布边界。

【实际功能】
路径: 新游戏正常赠金/赠刀→114/16/0→已接入百货店地图19买药草15→离店→通过普通探索/战斗受伤→HUD物品页选药草→使用当前合法角色→保存→外部force-stop→重启继续走动。
目标/场景: 原地图/菜单中的存活角色；当前哪吒一名目标沿用原角色面板，无多层确认。
效果/消耗: min(maxHp,HP+50)，一次确认减1；最后一份移除条目；满HP仍减1。
取消/无物品/非法目标无副作用；成功清空本次选择和待提交触摸，10次重复输入/同次多点不重复结算；暂停/焦点丢失清除悬挂提交。
HUD、背包和统一存档一致；渲染及刷新不结算。真实用户存档/云进度未清空。
是否人为增加出村前置: NO。

【内容与CI】
确有内容变化: 新增已核药草使用定义/来源和说明；c11→opening-segment-001-c12。
目标manifest SHA-256: 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8。
原c11基底与新c12目标分别锁定；export_from_base由原导出器提供，ci_apk.restore验证基底字节/签名/内容→局部转换→验证目标hash→写原ContentLoader入口。未关闭校验或伪造APK。
干净临时目录两次恢复42文件一致；原CI干净checkout成功构建，不依赖手工assets或整个历史目录。CI只需可信基底和已提交局部provenance，不接收完整ROM。
构建: run36902536271，同main来源77cdc6b7f822628e0fe8b2a4292e6d396d9089f3；最终APK hash 1a5a5e10f2793c1418a83a2da3b218ebdc2ff2274b2f02d3d1a30c2153d63bbf。

【验证】
原版依据: 六类受控实验及静态例程核对通过，所有私有证据文件hash与已提交provenance一致。
Python transport12/12、局部导出5/5、自动审批隔离fixture22/22；本地JVM44/44（含新增药草7），原CI testReleaseUnitTest/签名构建成功。
同一签名候选Android运行: 21项仪器测试通过（Content12及9项Touch方法，含旧版保存/覆盖、受控边界、三店买卖、装备、INPUT-01/摇杆隔离、正常闭环、冷启动继续探索）。本地Content12/12及受控药草UI1/1也通过，不将它们重复计为候选的新测试数量。
正常闭环不使用调试赠药/改HP/传送/强胜；受控边界fixture单独标记。取消、合法使用、近满/满HP、无物品/战败或非法目标、重复提交、暂停、保存冷启动及22→23实际adb install -r覆盖通过。
实际命令: 原build-ci.ps1 -RuntimeTests（Gradle wrapper testReleaseUnitTest/assembleRelease/assembleReleaseAndroidTest）；ci/run-town02-runtime.sh；原record_app_audio.py town02-ci testNormalHerbSupplyLoop --silent；相关Python unittest/审批fixture；原FCEUX Lua探针。
失败保留: 首次CI runtime默认AVD路径不存在，显式任务目录修复后成功；本地软件AVD两次System UI ANR，停止并保留失败。首轮安装时PackageManager未就绪和旧高分辨率运行失败未计通过。没有掩盖失败或降低业务断言。
正常App录像: 原run的fengshen-town02-runtime-evidence/town02-ci-normal-00.mp4，静音实际屏幕录制；声音NOT_RUN，不以播放器状态宣称真机音频稳定。
一加13T: NOT_RUN；真实账号多设备云恢复、更新误报根因、长时性能及历史音频根因仍未完成。

【复用与skill】
复用TownTrade、OpeningEquipment、现有角色/物品面板、统一状态/SaveSnapshot、ContentLoader、World/InputState、取证/导出/恢复和原CI/巡检/发布/录屏。没有第二套商店、导入器、引擎或发布平台。
skill: .agents/skills/fengshen-content-iteration/SKILL.md；沉淀固定公开输入+hash、局部证据定位、可信基底→目标导出、wrapper环境补齐、原同签名AVD覆盖/正常录像/冷启动、同产物运行回执审核。
仅写实际成功方法；修正默认AVD目录假设，明确本地软件AVD不保证成功。真机/声音/真实云恢复继续待核，不编造提速百分比。

【产物与发布】
候选: 构建36902536271的fengshen-signed-apk，fengshen-remake-v23-release.apk；运行证据同run的fengshen-town02-runtime-evidence。
版本: 0.8.3-town-02 / 23；构建前实际服务器最新22，23为递增版本。
包名org.fengshen.dev，签名SHA-256: 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6；release非debuggable、c12全部42文件及完整APK字节校验通过。
发布状态: PUBLISHED_AND_VERIFIED；原工作流36903560942，自动审批/同提交/同hash/签名/内容/运行回执门禁保持，只写既有两个Fengshen对象，Language未修改。
正式下载: https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=23
APK SHA-256: 1a5a5e10f2793c1418a83a2da3b218ebdc2ff2274b2f02d3d1a30c2153d63bbf；11322079字节；独立公网下载与receipt/metadata一致。
发布后巡检: 2026-10-01 18:02:38 UTC，可信23/22，82事件/0测试，普通1历史真机会话/0模拟器；样本仅来自v22，v23尚无已上传样本。NO_ISSUES_OBSERVED、清理失败0。不能据此证明v23真机正常。
未发布原因: 无；正式上传及独立校验已完成。

【累计欠账】
权威位置: docs/delivery-status.md的十类累计清单（ANDROID-CI-01段），本次只更新药草子项，其余完整保留；仍10类，不能把一项药草闭环写成完整TOWN或完整原版。
真正关闭: 药草地图/菜单效果、合法目标与数量规则、正常购买补给闭环、模拟器保存/冷启动/覆盖验证；药草战斗指令不关闭。
仍欠: 牛黃丸/其他物品、跨类别出售/装备替换/满包/左手、地图0事件/客栈、南海连接/Boss、完整战斗/展示、开局剧情、音频、真机更新/操作、真实云恢复/版本化内容及全量canonical；无新剧情前置。
到南海最近3个阻塞: 地图0必要NPC/服务/剧情事件；真实连接和剧情条件；剧情Boss及胜后连续状态。完成本任务停止，不展开其他区段。

END_DELIVERY_REPORT


DELIVERY_REPORT

task_id: TOUCH-UX-01
status: READY_FOR_REVIEW

【基线与巡检】
仓库/workspace/game-fengshen，main；开始提交7e280ed55c66cd59ed149f0a3b1acbad545a1a30，工作区原干净，无并发TOWN-02修改。结束游戏源码/正式APK来源ef29edb9192bb299ed493b767c52b23c450b2094；发布后仅补交付与已验证skill记录，不改变APK来源。
实际基线v23/0.8.3-town-02/c12；TOWN-02药草已实施发布，本轮直接复用。v21/c11历史三店等成果继续保留。
实际开工inspect 36906370361：2026-10-01 18:23:15 UTC，可信23/22，445事件、0测试，普通3真机会话/0模拟器，NO_ISSUES_OBSERVED。首次gh401已恢复，旧TOWN-02标签巡检不冒充本轮结果。
发布前20:07:38 UTC：23/22，511事件、0测试、普通3真机会话。发布后20:07:55 UTC：24/23，424事件、0测试、普通2真机会话/0模拟器，全部来自23；24尚无上传样本。均NO_ISSUES_OBSERVED、cleanupFailures=0；没有样本不能证明新版本健康。
未观察到需纳入本轮的上传崩溃/交易/丢档故障；历史音频超时根因仍UNCONFIRMED。巡检只覆盖实际上传日志，历史真机不是本轮实机验收。

【实际交互问题】
旧商店drawShop/shopAction/runShopAction以上一件/下一件选商品，交易进入RESULT还需继续；物品panelAction/runPanelAction固定四项分页，窄面板与列表下标容易限制触控。实际旧APK已录制对照。
保留原TownTrade、药效、库存/合法装备及统一状态；已合理的属性/法术页不重做，地图/战斗布局/头像/音频架构不扩改。
改造MainActivity.kt和新增局部TouchUi.kt：物品/装备/商店滚动直接选择，共享绘制/命中布局，宽屏列表+详情、窄屏列表→详情；至少48dp，适配安全窗口/字体。商品显示真实名称、持有量、价格及已有图像。普通交易留在当前列表，结果非阻塞；失败保留现场。
SaveState.kt新增纯OpeningEquipment.replace，等价于原卸下→装备，原子提交最终结果；候选失效/原回包上限阻止时不丢装备。UI不强行串两个有副作用调用。
拒绝猜测：牛黃丸/其他物品效果、战斗药品、原版直接替换/满包特殊规则、未入队目标和新槽位。后者仍属原版证据欠账，不被Android事务封装关闭。

【可操作结果】
物品浏览：滚动真实库存；点击只选中/看详情。实际能力区分可用、条件不满足、无合法目标、待接入和其他场景限制。
装备/卸下：选背包装备→装备给当前角色；选当前槽→卸下或选择真实候选。纯预览复用同一计算，装备加成与总攻击分别标注。切角色不重排队伍。
购买：选商品→购买1件·X两。卖出：切卖出列表、选真实可卖库存→卖出1件·X两。价格/每次一件/范围/数量限制不变；不自动卸装出售。最后一件售完清空选择，旧触摸不移到下一件。
药草：复用rom.medicine.0和已核HerbUse，地图/菜单存活队员可用；HP加50截断maxHp，成功消耗1，满HP也消耗并明确提示；取消/无库存/非法目标不消耗。牛黃丸待接入；战斗物品仍未实现。不新增出村前置。
实际点击计数从“列表已打开、当前角色已选中”开始：非首行购买旧3→新2；出售旧2→新2（旧为已选商品+继续，新为点行+卖出）；背包直接替换旧5→新2；新卸下2、药草使用2。不计开菜单/切买卖标签，不编造百分比。
按下只记录稳定物品/角色/槽位/商店/模式/状态；抬起再核验。同手势最多一次提交，滚动、CANCEL、多点、切页/目标/后台清过期输入；重复UP不结算，下一次合法独立点击仍正常。渲染不扣钱/物品，模态开关隔离地图输入。
成功反馈对应实际保存结果；保存失败路径回滚并显示失败，磁盘故障注入未执行。正常保存、冷启动与升级一致性已实测。

【规则与数据】
业务入口：TownTrade、OpeningEquipment、HerbUse、GameState/SaveSnapshot，ContentLoader/World/InputState继续复用；未创建第二套交易、引擎、导入器或UI框架。
游戏数值/价格/数量上限/槽位/药效/剧情条件未改变，c12内容不变。明确修复交互误提交风险、列表重排错对象风险及实际2×字体按钮/导航重叠；多点触摸改为取消整组手势。
覆盖升级v23→v24实际adb install -r、同签名、未卸载清档，旧位置/钱/库存/装备/flags保存相等。受控fixture仅隔离AVD；未修改用户真实存档/云端进度。

【skill】
路径.agents/skills/fengshen-touch-ux/SKILL.md，name: fengshen-touch-ux；AGENTS.md增加一条范围规则，docs/android-ui-design.md记录局部触控约定。
description限定已授权Android物品/装备/已有商店的光标导航、A/B多确认、逐件翻页和误触；排除纯ROM/音频/数值/构建，不授权全库UI或规则变更。
结构检查PASS；六例人工审阅：商店翻页/装备多确认ADAPT，音频/南海坐标OUT_OF_SCOPE，自动卖全装备买最贵ASK，已有合理直接点选PRESERVE。未据负例改动对应系统。
显式调用与不提skill名的隐式CLI均在隔离目录实际尝试，因Codex CLI自身认证HTTP401失败，自动匹配NOT_RUN；没有以测试提示词冒充通过，也不影响已正常工作的GitHub授权。未改全局权限/配置，未调用生产发布测试skill。
实际沉淀：共享布局/稳定ID取坐标、纯事务与手势验证、旧签名方法ABI兼容、滚动到足够点击区域、原AVD覆盖与正常静音录屏/冷启动、native截图真实尺寸/大字体审查及失败白名单取证。不保存版本、Secret、ROM或全量日志，不编造提速。

【验收】
U1 PASS：当前物品/装备/买卖由直接点选和动作按钮完成，不依赖虚拟方向键/A/B或上一件/下一件。
U2 PASS：点列表只选中，明确动作才改状态，实际仪器断言/录像。
U3 PASS：滚动、重复UP、多点取消、交易后列表重排/最后一件售完、状态改变与失效手势有边界验证；下一合法点击可继续交易。
U4 PASS：相同fixture下纯装备替换/卸下与原合法序列一致，交易仍用原TownTrade；药效边界断言保留。手势策略改动单独如上说明。
U5 PASS（模拟器）：保存/外部force-stop/冷启动/继续探索、同签名覆盖升级、INPUT-01/摇杆隔离回归；真实账号云恢复与保存磁盘故障注入NOT_RUN。
U6 PASS（尺寸模拟）：实际截图2640×1216，GameView2640×1080、density3，字体1/1.3/2完成区域断言与截图审阅；动作/价格完整、触摸框不重叠，2×部分详情滚动。一加13T实机NOT_RUN。
U7 PASS（结构及人工六例），自动显式/隐式匹配NOT_RUN，原因如skill部分。
U8 PASS（云门禁/字节/本轮巡检），v24上传样本及手机健康结论尚不可取得，未冒称实机通过。

【产物】
实际命令：原build-ci.ps1 -VersionCode 24 -VersionName 0.8.4-touch-ux-01 -RuntimeTests；原ci/run-town02-runtime.sh；python tools/record_app_audio.py touch-ux-after testNormalTouchUxSupplyAndEquipment --silent；旧对照为python tools/record_app_audio.py touch-ux-before testTouchUxBaselineClickPath --silent --comparison；Gradle wrapper testReleaseUnitTest/assembleRelease/assembleReleaseAndroidTest，本地testDebugUnitTest/assembleDebugAndroidTest；相关Python unittest/自动审批fixture；ci_apk.py verify；原android-publish.yml及check-runtime.ps1 pre/postflight。
最终JVM51/51（新增UX7项），Python transport12/12、局部导出5/5、自动审批隔离fixture22/22；最终原API30 AVD合计27次测试执行通过（Content12+15次Touch执行，其中字体方法跑三档）。没有把重复执行写成27个独立用例，未重跑全量Python/Go/音频套件。
失败保留：旧测试APK公共persistState签名变化引起崩溃，恢复Unit ABI；滚动行不足48dp、旧坐标/多点预期和2×滚动预算失败已针对修复。运行36915073744虽Actions成功，但实际截图尺寸/大字体布局不合格，被拒绝且未发布；最终36917255772修复并实测。失败尝试不计通过。
正式APK：0.8.4-touch-ux-01 / 24；来源ef29edb9192bb299ed493b767c52b23c450b2094。构建36917255772，原发布36919194991，均success，自动审批沿用reviewer/受保护环境。
下载：https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=24
APK SHA256：def359de888614152768bdb70c4f12a5e09a653124db6eb0650e96a9edb76300；11353567字节。完整公网下载与审核artifact/receipt/metadata一致；?v=24本身不是不可变保证。
包名org.fengshen.dev；既有签名SHA256 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6。
内容opening-segment-001-c12，manifest SHA256 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8，42文件验证通过，无内容变更，不强制另发内容版本。
正常实际App视频：artifacts/touch-ux/reviewed-runtime/checkpoint-ui/touch-ux-after-normal-00.mp4；旧受控对照touch-ux-before-normal-00.mp4。最终正常视频50秒，外部停止/恢复结果另见touch-ux-after-recording.json；静音，声音NOT_RUN。
截图：同目录touch-ux-phone-{items,equipment,shop}-{1.0,1.3,2.0}.png；本地正式下载artifacts/published/fengshen-remake-v24-release.apk。运行证据继续原artifact名fengshen-town02-runtime-evidence。
发布状态PUBLISHED_AND_VERIFIED。2026-10-01 20:07:43 UTC发布，只覆盖既有两个Fengshen对象，Language未改；发布后巡检成功并保留24/23日志，24零样本限制如上。一加13T：NOT_RUN；声音/长时性能：NOT_RUN。

【未完成】
TOWN-02地图/菜单药草闭环由此前v23关闭，本轮仅复用；药草战斗指令、牛黃丸/其他物品仍欠账，不借UX关闭。
本轮未验证：实体手柄完整操作、实际多队员选择、所有窄屏设备、保存磁盘故障、Codex自动skill匹配及一加13T实机。当前单队员/宽屏正常流程已实测；属性/法术等其他菜单不在本轮范围。
十类累计清单权威位置仍为docs/delivery-status.md的“未完成清单（累计；只有实际完成才移除）”；完整保留。Android等价原子替换已实现，但原卡带直接替换/满包规则待核，不冒称原版完成。
下一原版节点最近3个阻塞：地图0必要NPC/服务/剧情事件；通往南海的真实连接/剧情条件；剧情Boss及胜后连续状态。
本轮结束，不自动开始新地图、战斗或其他区段。

END_DELIVERY_REPORT


DELIVERY_REPORT

task_id: NANHAI-01
status: READY_FOR_REVIEW
execution_kind: IMPLEMENTED

【执行时间与实际工作】
实际开始/结束时间: 2026-10-01 20:33:25 UTC / 2026-10-02 02:12:15 UTC（结案记录生成；后续文档提交另记）。
总耗时及平台限制: 5小时38分钟，10小时上限内完成；本地无KVM的软件AVD两次ANR后复用原Actions KVM，不冒充本地运行成功。
起始与结束提交: 起始3e799fb50594fee63b8fd7f8dbba659da1daae84；最终APK来源d87bb7540018913ad17d3865264924add6fa2580；结案文档提交另记。
M1: PASS，正常进入海底并往返；M2: PASS，正常抵达守卫/龙王触发点；M3: PASS，真实战斗获胜、胜后再入与重启继续。
最后通过的可运行检查点: [最终构建/运行36950387932](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932)，原AOSP API30 KVM；主线1384.244秒、冷启动测试3.314秒均OK。
是否提前停止及实际原因: 提前达标结束；没有平台预算中断，不推进下一地区。

【开工和后续巡检】
命令/run_id: 原android-publish.yml mode=inspect + check-runtime.ps1；开工36922834320，检查点36933053909/36941528938/36946324010；实际发布[36953281754](https://github.com/antpan5608-san/game-fengshen/actions/runs/36953281754)自带preflight/postflight。
查询时间和可信版本: 开工2026-10-01 20:38:20Z，24/23；最近检查点2026-10-02 00:30:51Z；发布前后preflight 01:57:14Z查询24/23；postflight 01:57:32Z查询25/24，均NO_ISSUES_OBSERVED，errors={}、cleanupFailures=0。
模拟器/真机、正常/测试样本: 开工720事件、0测试、普通真机3会话/模拟器0，v24=290/v23=430；发布前727事件/0测试/普通真机3会话/模拟器0，24=297/23=430；发布后297事件/0测试/普通真机1会话/模拟器0，仅v24；旧v23已按两版保留规则移出。
发现的实际问题: 上传窗口无ERROR、清理失败0；仅覆盖上传样本，旧音频超时仍UNCONFIRMED。
本轮修复: 本段换图计数清零/目标日志字段、Boss完整图形位置；Windows PNG/UTF-8/路径测试、UI线程完成状态观察、随机命中测试假定及取证收集顺序问题修正。胜后再入测试修正合法转向预期，不放松经济/剧情断言。
仍未解决与覆盖限制: 历史日志不等于本轮手机验收；v25无上传样本，不能宣称v25真机健康；发布前窗口18:17:50—次日01:05:15；一加13T与声音NOT_RUN。

【玩家实际进度】
原版起点: 正常新游戏114(8,21)。
本次正常最远终点: 龙王胜后97(15,5)自由操作；冷启动后正常继续到97(15,6)，Lv8/HP57/57/EXP850/银两361/药草3/背包長劍1，胜标保留。
逐节点操作路线: 114离家→16→可选0及三店补给→16(199,130)→25(39,42)→25(29,44)→97(15,29)→守卫→97(15,4)龙王→真实战斗→胜后对白→离宫/再入/复谈→保存/外部停止进程/重启继续。
与v24相比真正新增的可玩内容: 两张真实必经地图、四连接、沿途敌群、三NPC及龙王剧情战斗/胜后一次结算；限定原版命中/倍伤/敏捷排序/掉落。三店/药草/直接触控/装备/三图/输入/云存档/更新器是保留历史成果。
是否完成南海龙王胜后: YES，限本段连续里程碑；不是全游戏原版完整验收。
不能到达的节点和具体原因: 海底北部group4未接入行为/后续地图事件；未开放后续大区，无虚假剧情锁。练级与买药只是验收策略，不新增任何出村/等级/供给前置。

【地图与剧情】
原有和新增不同地图ID: 原114/16/0/17/18/19；新增25/97；累计8。
主场景/室内/分块: 新增主场景1（海底）/室内1（龙宫）/额外分块0。
已提取、已打包、正常可达: 新增2/2/2；原三店及三图在同候选回归，合计8个不同ROM地图。
连接与落点: 16(199,130)→25(39,42)、25(39,42)→16(199,130)、25(29,44)→97(15,29)、97(15,29)→25(29,44)；四条独立原记录，落地方向DOWN，已核换图计数0。
新增NPC/对话/事件: 龙王151及守卫152×2；文本107.0/1/2/3；主线实测中间守卫和龙王，另一守卫数据/文本核验。
必要条件及来源: 正常原版控制器流程与匹配ROM出口/脚本；龙王首谈触发真实战斗，胜标0761&1映射统一状态，只胜后写入。
可选互动: 村庄对话/购物/用药/练级不是剧情前置；两宫内宝箱只恢复原图，奖励未编造。
未开放局部内容: 宝箱事件、北部海域与后续故事；其他村庄建筑欠账保留。

【Boss与胜后】
Boss真实ID/名称/素材: ROM137 / source156 / 南海龍王；原128×112图形，已核位置(64,0)，渲染和命中共用区域。
数据和行为依据: 匹配ROM记录、限定代码范围、正常原版实战；HP120、攻16、防13、敏8、EXP60、银两100；物理与冰8，冰分支41/128；同字节命中/倍伤与敏捷排序。
实际执行能力/正常Android结果: 正常Lv8、实际購長劍/布衣后攻击；实际冰与物理行动可见，战胜HP120龙王，HP57→35，EXP763→823、银两246→346，实际随机获得長劍1；后续正常遇敌奖励另记。
失败/逃跑规则: Boss逃跑必败且消耗行动；有时会miss，测试不强制反击命中。HP1受控fixture以正常战斗输入确认新游戏战败分支；原卡带手动存档后的战败加载仍欠账。
奖励与一次性状态: EXP60/银两100、長劍随机50/128；分类容量/堆叠满只跳过掉落，不丢旧物品；胜标与奖励一次提交，待对白保存/恢复不重结算。
胜后下一可操作状态: 留在97，自由移动；正常离宫→海底→再入龙宫，复谈不重战/不重奖；最终外部force-stop/重启存档一致，再走一步，角色/钱/库存/装备/flag不变。
尚未等价: 完整动作/字库/调色/时序、所有法术与战斗药品、NES随机序列；地图97原版拒绝手动存档，Android沿用自动SaveSnapshot便利适配并独立验重启。

【资源与实现】
可信基底: v24/0.8.4-touch-ux-01，build36917255772，APK来源ef29edb9192bb299ed493b767c52b23c450b2094；SHA-256 def359de888614152768bdb70c4f12a5e09a653124db6eb0650e96a9edb76300。
资源/指纹/来源: 复用匹配缓存ROM完整SHA f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25（luzeming0211/fc固定23d62347）；公开Reference v5100v5100/FengShenBang固定d636453f，MainData及两MP3；许可UNKNOWN，技术一致不代表授权。原Windows完整目录/手机未接入。
新增取证: 只查两地图、四出口、必要NPC文本、遭遇/碰撞、Boss/物理/掉落/胜后flag与换图计数；原版正常游玩和受控实验分开。
已验证/暂定: 关键路线、数值、触发、一次性状态按ROM与正常证据核；敌4–7名称、六神丸名称、新BGM场景关联为PROVISIONAL；成长2–7正常原版已核，后续行的运行覆盖另记。
复用模块: 原Reader/ROM提取器、export_from_base、ci_apk.restore/verify、ContentLoader、World/InputState、OpeningBattle、TownTrade/OpeningEquipment/HerbUse、统一状态/SaveSnapshot、GameAudio/诊断/更新器及原两workflow。
最小逻辑: 局部地图/实体/事件数据、本场行动/掉落/剧情结算、可选图形origin；未新建引擎/导入器/素材平台。
素材和内容生成: 不变33媒体字节复用；旧场景封套版本跟随整包，地图16仅补真实海岸入口；可信c12基底+本段可追溯局部导出形成c13/59文件，不要求先有新APK。干净worktree和最终Windows checkout生成同manifest。

【验收】
N1: PASS，实际四次inspect及本次发布前/后巡检；v25无样本限制保留。
N2: PASS，正常新游戏连续到达南海龙王胜后，不是只加载地图。
N3: PASS，必要守卫/龙王对白、开战及胜标实际执行，没有虚构供给或等级锁。
N4: PASS，真实Boss图形、物理/冰行动与正常胜利，最终原片/截图可查。
N5: PASS，正常胜后离宫再入、复谈不重奖和冷启；待对白恢复/满堆叠另用受控fixture验证。
N6: PASS（本轮适用分支），Boss逃跑必败耗行动与HP1真实输入战败受控验证；原卡带已有手动存档加载仍未实现。
N7: PASS，实际adb install -r v24→v25，旧位置/方向/角色/钱/库存/装备/flag保持；不是卸载清档。
N8: PASS，同候选三店、药草满HP消耗、装备原子替换、TOUCH-UX和INPUT-01回归。
N9: PASS，最终构建/审核/审批/发布同d87提交/同签名/同APK19c53…，完整公网APK和59内容hash独立复核。
N10: PASS（本段可追溯与诚实标记），provenance逐范围保留，暂定/差异与私有原版证据分别记录；不等于全canonical VERIFIED。
实际命令/数量: 原build-ci.ps1、Gradle wrapper release门禁；Python相关12+5+7=24/24，JVM69/69（18个本段方法）；原ci/run-town02-runtime.sh。Android共29次测试入口、27种方法（字体方法重复3次），其中私有云读因无凭据返回NOT_RUN；Content12入口和其他断言通过，正常主线/冷启各1次。相关完整回归均在此审核产物上执行。失败尝试不算通过、不把重复运行算独立方法。全量历史Python曾68方法通过/8跳过，ImportIntegrityTests因缺game-data/raw/reference-project/dataset.json出现setUpClass错误，未伪称全量通过；该历史输入不在本段依赖。

【可查看的运行证据】
Actions run与artifact: [构建run36950387932](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932)；[fengshen-signed-apk](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932/artifacts/11204196500)（fengshen-remake-v25-release.apk）；[fengshen-town02-runtime-evidence完整原片](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932/artifacts/11203754303)；[fengshen-nanhai-checkpoints截图/索引](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932/artifacts/11203854195)；[fengshen-nanhai-entry-clip](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932/artifacts/11203849166)；[fengshen-nanhai-final-clips](https://github.com/antpan5608-san/game-fengshen/actions/runs/36950387932/artifacts/11203849171)。
正常录像/时段/hash:

`nanhai-ci-normal-00.mp4`：进入海底约136.45s；SHA-256 `c5779172c6415a90284509ee1de3aa22852a179cb46387c9a91c53b1c7a773f0`

`nanhai-ci-normal-07.mp4`：守卫55.67s/龙王58.58s/冰74.59s/胜利83.70s/胜后84.23s/离宫98.48s/再入99.30s/复谈不重奖116.55s；SHA-256 `830c3fc3f819f534ed9515fe2e2be52525b117f1a9344ae9986f0cbe9fdfe869`

`nanhai-ci-cold-restart.mp4`：外部force-stop、实际重启和继续，全段；SHA-256 `5c90b14514e7299c5323bb592409ec9ce36886b8ff2d8ffee601387140c7a6ae`

全部8段正常原片及外部重启片保留；时间为索引近似，画面以原片为准，已独立核三份小型原片hash并实际抽看。
至少五张实际截图: 同上述checkpoints artifact内：`nanhai-sea-entry.png`、`nanhai-guard-dialogue.png`、`nanhai-boss-ice.png`、`nanhai-boss-victory-result.png`、`nanhai-reentered-victory-dialogue.png`、`nanhai-cold-restored-continue.png`；已实际查看，非生成示意图。
原版参照/Android对应: game-data/provenance/nanhai01.json保留ROM范围/hash与私有正常记录索引；原ROM/私有回放未公开上传。
连续性/调试状态: NORMAL_CONTROLLER_NEW_GAME_NO_STATE_INJECTION；正常117场/地图药草19次；每段保留uptime、只读world存档边界及事件索引，短录屏切换间隙明确，战斗live HP看原片；未改等级/HP/物品/flag/RNG或强制胜利。受控fixture单列。
声音: NOT_RUN，实际原片无音轨，不替换原版音轨。一加13T: NOT_RUN。录像/截图artifact保留14天，APK30天。

【构建和发布】
APK版本/versionCode: 0.8.5-nanhai-01 / 25；12790724字节。
来源提交: d87bb7540018913ad17d3865264924add6fa2580；构建/runtime 36950387932；审批/发布[36953281754](https://github.com/antpan5608-san/game-fengshen/actions/runs/36953281754)。
下载/hash/签名: [正式下载 v25](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=25)，可沿App检查更新覆盖安装；手动文件须去掉.bin后缀。公网对象后续可覆盖，不可变来源见构建artifact; SHA-256 19c53eae0f804f86cfcf796b7c72f63459f946feaad54a8f7bef7e5b9ccf8280；org.fengshen.dev；证书SHA-256 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6。
内容版本/manifest: opening-segment-001-c13；badb0194e1342b66732cb2258fed7da2e80910f46fa74cccaac2a339cc4fcbc4。基底c12/8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8与目标分开固定。
旧档覆盖升级: 实际隔离AVD v24→25覆盖PASS；保存/冷启PASS；真实一加13T覆盖NOT_RUN，不覆盖真实玩家云进度。
发布状态/发布后巡检/未发布原因: PUBLISHED_AND_VERIFIED；2026-10-02 01:57:19.9185492Z正式发布，原审批/发布和独立公网验签/完整字节/内容校验成功；发布后只保留25/24日志，25暂无样本；无未发布原因。
只更新既有Fengshen APK.bin/version.json；Language不变；同源/签名/审核hash、原reviewer和日志最近两版保护保留。

【复用与skill】
已验证方法: 局部场景/ROM recipe导出、跨平台固定PNG/RGBA校验、独立出口/计数、Boss规则与一次结算、正常控制器连续流程、原片分段/状态边界及实际冷启；以最终run为准。
直接复用: c12媒体/原loader/业务/存档/触控与原CI，未重复1958历史输入或全量研究。
失败经验修正: 不按平台PNG压缩字节碰运气、不依赖默认中文编码/斜杠、不后台观察UI换图中间态、不假定第一回合必命中、不在pull前读App索引；交谈合法转向与经济变更分开。
skill位置: .agents/skills/fengshen-content-iteration/SKILL.md 与 fengshen-touch-ux/SKILL.md；内容skill补已实跑连续主线/原片边界/冷启/发布方法；触控skill只补原图形origin共用绘制命中、UI线程观察及合法交谈转向，其他合理界面PRESERVE。
未验证: 隐式CLI匹配未复测（历史401）；本轮显式读取、结构检查/六类场景人工复核，非自动触发PASS；真机/声音/真实账号恢复仍待实测，不编造提速百分比。

【累计欠账】
权威清单: docs/delivery-status.md“未完成清单（累计）”，十类保留。
本轮关闭: 南海必经路线/剧情/Boss胜后子项；本段已核物理命中/倍率/排序、本场Boss行为与掉落。
本轮新增/剩余: 宫内两宝箱奖励、北部group4行为/后续入口、新BGM精确映射/循环；完整动画/字库/规则、战斗药草、全村事件、真机与云端欠账均未关闭。
下一真实节点最近3阻塞: ①后续海域真实连接/事件（数据/原版证据）；②group4敌10/11行为7（数据/代码）；③下一剧情战斗及必要指令/奖励（数据/代码/正常运行证据）。不自动开下一大区。

END_DELIVERY_REPORT
