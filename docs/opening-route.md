# 原版开局至第一次换图：开发包与 Android 对照

本轮终点为内部地图 114 南侧边界触发原版出口，进入内部地图 16 并到达玩家格 `(203,142)`。这是可连续操作的**必经移动/换图区段**，不是开局剧情、可选 NPC、野外事件或完整游戏的验收。地图 ID 不按 Reference Project 重新编号。

|顺序|原版预期与依据|Android 实测 / 差异|
|---|---|---|
|初始状态|地图 114，32×30，哪吒在 `(8,21)`；`docs/original-baseline.md` 与开局运行时采样|`ContentLoader` 加载 ROM 网格/图集；Android 仪器测试和模拟器画面均从该格启动。角色数值、旁白和可选 NPC 的完整呈现本轮未接入。|
|开局区域移动|正常步行碰撞：类别 0 可走、1 阻挡、2 可走并有遮挡；`game-data/raw/rom/collision-opening.json` 与运行时分支|`Scene.check()` 读取开发包逐格类别。地图 114 的 443 个静态可走格已解除旧 34 格观察白名单；原始 NPC 坐标仍作为动态对象开发边界，不冒充原版墙。移动步频尚未逐帧证明与原版一致。|
|NPC、对话、状态|已记录的正常输入出门轨迹没有先交谈、获物或设置 Flag 的必要步骤；父母等 NPC 有源记录但不是出门前置条件|不编造前置任务、赠物或对话；A/B 在地图层可见但禁用。原版 NPC 外观、移动、可选对话及物品变化未实现，因此不能宣称开局全内容已恢复。|
|真实出口|地图 114 南边界 `(8,29)`，ROM file offset `287921` 的 5 字节 `08 1D 10 CB 8E`；`game-data/raw/rom/transitions-v1.json`，原版正常输入重放 frame 840→843|触屏连续向下可触发同一地图 ID 转换；`World` 只在完成合法整格移动后触发。类别 2 不是传送，未被误当出口。|
|下一图到达|地图 16，256×181，玩家 `(203,142)`、原版相机锚 `(196,135)`，运行时 frame 888；地图与可见 900 PPU 索引另见 `docs/original-baseline.md`|仪器 golden 测试核对 `(203,142)`，模拟器实录显示地图 16。现代横屏相机与原版相机锚不同，但玩家世界坐标相同。地图 16 目前只允许首次到达附近 13 个观察格；动态 NPC、遇敌、返程出口仍未接入。|
|本地恢复|原卡带二进制存档不是本轮目标；Android 区段要求重启后位置/内容版本一致|完成格后保存 `contentVersion/mapId/x/y/direction`。模拟器停止进程后重启，仍在地图 16 的相同像素坐标；无一次性事件，因此暂无 Flag/物品持久化断言。|

开发包 `opening-to-world-b1` 由现有 `tools/export_development.py` 导出到 `game-data/packages/development/`，再随 APK 提供给既有 `ContentLoader`。包含地图 114/16 网格、各自 CHR 图集、碰撞类别、原版出口记录、方向姿态及 SHA-256 清单；原 ROM、派生文件和包本身遵守 `.gitignore`，未作为单独内容服务发布。Reference Project 只辅助定位，不作真值或自动 canonical 提升。

复用 `tools/export_development.py`、`ContentLoader` / `AssetSource` / `DirectorySource`、`World`、`Scene.check()`、`GameView` / `InputState` 和 Android `PackageInstaller`。薄适配仅增加第二图开发数据、ROM 出口执行、地图 ID 存档和 APK 内更新入口；没有另建提取器、数据协议或 RPG 引擎。

实证范围：`./phase1.ps1 test` 80/80、Android JVM 13/13、API 35 x86_64 模拟器仪器测试 11/11、`./tests/test_publish.ps1` 4/4。模拟器触屏连续出门的最终 v6 录屏为 `artifacts/checkpoint-ui/opening-route-v6-emulator.mp4`；开局与到达截图分别为 `opening-v6-emulator.png`、`arrival-v6-emulator.png`。停止进程后重启，存档的地图 16、像素坐标 `(3256,2312)` 与恢复后画面均相同。录屏与截图均来自实际 Android App，非原版模拟器。

发布版本 `0.3.0-opening-route` / versionCode 6 经独立 Fengshen OSS 对象覆盖上传及公网哈希校验。另以 v5 安装包在模拟器内点击“检查应用更新”，完成元数据读取、APK 下载/校验、来源授权、Android 系统安装确认，系统已安装版本变成 6。应用内更新不是静默安装；一加 13T 对本版本的真机画面、手感和更新安装体验仍待用户验收。下一段先验证原版地图 16 的返程或实际必经事件，再迁移对应内容；不把未知野外格全部开放。
