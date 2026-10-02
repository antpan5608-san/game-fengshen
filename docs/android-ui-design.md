# Future Android UI — OnePlus 13T Requirements

## 当前生效：全屏场景与悬浮摇杆（2026-09-30 真机截图反馈）

当前游戏信息层增量（2026-09-30）：地图探索时在左上安全区显示可点击的主角色头像、姓名、等级、HP 和 MP；头像从开发内容包的 `CharacterDefinition.portraitAsset` 读取 ROM 实测角色精灵并最近邻放大。点击进入约 40% 安全宽度的左侧角色面板，地图留在暗化背景，摇杆、地图 A/B、菜单均隐藏且失去输入；关闭后返回进入前的地图或菜单层。面板只读取统一存档中的队伍、角色属性、共享背包和银两。属性/物品可查看；装备和法术因缺少可靠运行状态标注“尚未开放”。最大 MP 缺少初始记录时显示未知，不推造数值。该面板属于游戏 UI，不替换 Android 系统设置页；OnePlus 13T 实机手感仍待验收。

用户明确取消“中间固定原版画面 + 左右独立操作区”的默认布局。本节取代下方检查点 A 三分区/D-pad 及“Android 未授权”历史文字；原版内容真实性仍由数据证据控制，显示和触控采用现代横屏手游方式。

- 底层：Map114 的真实 32×30 metatile 网格逐格绘制，全窗显示；角色使用相同世界到屏幕矩阵，独立摄像机视野等比缩放、跟随、按真实世界边界裁切。世界 512×480 与逻辑基线 256×240 均不等于手机像素；原版比例、整数缩放可选，非整数全屏不宣称严格像素完美。
- 上层：地图模式常驻左下半透明摇杆、右下 A/B 和右上独立小菜单入口。A/B 暂无可靠地图功能时保持位置并灰显、禁止命中；不能以虚构 NPC 互动让其可用。暂停菜单隐藏地图控件，仅面板菜单项与关闭键接收触摸，遮罩不关闭菜单也不穿透；设置页关闭回到暂停菜单。对话和菜单独立于世界裁切。
- 默认不绘紫色开发路径、不常驻地图 ID/坐标/字体说明/碰撞诊断。旧 34 格白名单已取消：地图 114 现在按原版静态碰撞类别移动，动态 NPC 格暂设明确开发边界；地图 16 仅开放首次到达附近已观察格。未知区域不擅自放行，也不称作原版墙体。开发者调试层仍可由设置打开。
- WindowMetrics、density、刘海与系统手势 insets 用于控件和文字安全位置；场景本身扩展到全窗。横屏正反向重算布局，逻辑时钟与刷新率解耦，触摸取消、后台和恢复清键；在一加 13T 真机反馈前只报告模拟器结果。
- 后续逐功能开放完整 NPC/事件/本地继续游戏，再往返、回合制战斗。Go manifest/版本/数据包与客户端校验缓存、离线运行的架构继续保留，本轮不开发服务端。

下文记录最初设计和检查点 A 的约束，若与本节冲突，以本节为准。

执行状态更新（2026-09-30）：用户已授权检查点A开始落实下列需求。本节取代本文件原“只记录/禁止任何Android”的执行限制，原文保留为历史。允许明确范围的development运行包，仍不允许Android直接消费研究canonical-candidate。实现/待实现与真机状态将记录在CHECKPOINT_A.md和ONEPLUS_13T_ACCEPTANCE.md；原有完整UI要求不删减。

检查点A落实：横屏三分区、WindowMetrics/density/insets安全区、256×240逻辑画布、整数/FIT最近邻、多指/长按/滑动/CANCEL/后台清键、固定步长、平台设置对话框、JSON控件参数本地保存/恢复、设备与布局调试已实现。TV比例校正和拖拽编辑器待实现；未连接OnePlus13T，真机所有验收仍待测。完整交付证据见[CHECKPOINT_A.md](CHECKPOINT_A.md)。

状态：**需求记录 / 未实现**。当前仍为 Phase 1 — Original Game Baseline & Data Forensics。
本文件来自用户本轮明确要求；不授权开始 Android、Server、游戏 UI、云存档或正式 canonical 发布。
`READY_FOR_PHASE_2` 由原版 Vertical Slice 的证据验收决定，不能由本设计文档代替。

## 参考设备与适配原则

Primary Reference Device：**OnePlus 13T / 一加 13T**。优先获得该设备的最佳横屏体验，同时保持其他 Android 手机响应式适配。
原则为 **REFERENCE DEVICE + RESPONSIVE LAYOUT**。禁止按型号分支硬编码位置、屏幕像素或分辨率；不把设备营销规格当作当前窗口可用空间。

未来运行时读取并记录：Window Metrics、Density、Orientation、Display Cutout、System Bar Insets、Gesture Insets、Safe Drawing Area、Refresh Rate。
窗口、方向、导航模式、系统栏或刷新率变化时重新计算布局。建立 Device Info Debug Page 显示这些原始值、可用区域、逻辑画布、缩放因子和实际控制位置，便于截图复核。

## 游戏画面与布局

**LANDSCAPE FIRST**。使用固定 Game Logical Canvas，将原版 Tile、Sprite、Dialogue、Battle UI 按原版视觉规则绘入逻辑画布，再以 Nearest Neighbor 缩放到 Available Game Viewport。
当前研究图像为 256×240；该值是 FC 逻辑画布基线，不是 Android 屏幕像素常量。裁边/显示比例策略仍须与原版基线确认。
禁止横向拉伸 FC 画面来铺满现代宽屏；像素、美术与对话/战斗窗口比例不得变形。

历史检查点 A 使用 **LEFT CONTROL AREA + GAME VIEWPORT + RIGHT CONTROL AREA**；当前已由上方全窗场景和悬浮操作层取代。安全边距只约束控件、文字及重要交互，不能挤小整个地图层。

## 触摸输入

必须支持 D-PAD、A、B、START、SELECT / MENU。D-PAD 位于左手拇指自然区域，A/B 位于右手拇指自然区域，允许斜向排列。
支持 Long Press、Slide Between Directions、Multi Touch、Pressed State；Haptic Feedback 可选并允许关闭。
方向滑动时无需抬手再点。维护每个触点的归属、按下/移动/抬起/取消状态；失焦、后台、锁屏时释放输入，避免按键卡住。
触摸采样和渲染频率不能改变按键在游戏逻辑中的持续时间、自动重复节奏或角色移动速度。

## Control Layout Editor 与本地设置

未来用户可以分别调整 D-PAD、A、B、START、SELECT 的 **X、Y、Size**，并调整 **Opacity**，保存到 Local Settings。
提供可恢复的推荐默认布局。OnePlus 13T 推荐值来自真机反馈，但通过安全区域的相对位置和密度适配表达；用户可修改，不能依赖型号硬编码。
设置需处理窗口比例变化、手势安全区和控件越界；保留重置入口，避免用户把全部控制移到屏幕外后无法恢复。

## Display Modes

|模式|目标|约束|
|---|---|---|
|ORIGINAL|尽可能接近原版显示比例|原版比例和裁边规则需有基线证据；保持形状|
|INTEGER_SCALE|Pixel Perfect 优先|使用能放入视口的整数倍缩放、最近邻采样；允许留边|
|FIT|不变形情况下最大利用可用视口|等比缩放；非整数倍不能宣称每个逻辑像素都等大|

禁止默认 STRETCH。显示模式改变显示映射，不改变地图坐标、碰撞、相机、游戏逻辑或触摸语义。

## 刷新率与时序

Game Logic 必须与 Display Refresh Rate 解耦。设备处于 60 / 90 / 120 Hz **或其他实际刷新率**时，Movement Speed、Encounter Rate、Battle Speed、Timer、Animation Logic Timing 必须相同。
上述数值是验收场景，不是断言 OnePlus 13T 支持每个档位。以运行时实际支持/生效值为准。
高刷新率可降低输入延迟、提高显示平滑度，不得提高游戏逻辑速度；逻辑步进节拍由后续原版时序证据确定。

## Game UI 与 Android UI 分离

|Game UI：保持 FC 原版视觉语言|Android UI：允许现代界面|
|---|---|
|Dialogue、Battle、Inventory、Equipment、Shop、Original Menu|Settings、Save Management、Cloud Save、Game Data Update、Control Editor、Debug、Device Info|

禁止把 Material UI 风格直接混入原版游戏场景。这里列出 Cloud Save 只是未来界面分类，本 Phase 不实现云存档。
Android / Server 不能消费 `canonical-candidate/`；只有未来通过验收的正式数据包才能用于运行时。

## OnePlus 13T 真机验收与截图迭代

未来 Android Phase 建立 `docs/ONEPLUS_13T_ACCEPTANCE.md`，逐项保存实际设备指标、步骤、截图、结果和待修问题。
验收必须覆盖：Game View 清晰度、Pixel Perfect、Aspect Ratio、Display Cutout、Gesture Navigation、D-PAD Ergonomics、A/B Ergonomics、Dialogue Readability、Battle Menu、Fullscreen、Background / Resume、Screen Lock / Resume、Rotation、Save State Safety、High Refresh Rate、Long Session Comfort。

每完成 World、Town、Dialogue、Battle、Inventory、Equipment、Shop、Save，优先在 OnePlus 13T 真机运行，结合 **Screenshot + Runtime Device Metrics + Touch Feedback** 调整。
模拟器可用于开发回归，不能独自决定最终 UI 验收结果。其他 Android 设备/窗口比例的响应式回归也必须保留。

## 当前实现边界

本次仅新增需求文档并同步架构约束。没有 Android 页面、游戏 UI 代码、Server、云存档或可发布 canonical 数据。


## 当前授权触控迭代

以上“当前实现边界”为早期文档状态；现有Android成果与任务状态以current-task和delivery-status为准。
只适配当前任务被授权的界面，像素素材与Canvas继续复用；MOBILE-PLAY-01明确授权成长信息与现有战斗触控，地图和其他菜单不扩展。
物品/商店列表点击只选中，明确动作按钮才提交；现有战斗默认模式授权一次点存活敌人提交攻击，信息入口不耗行动，药品行不套用点怪即执行。横屏两栏，窄窗口单层列表→详情。主要行和按钮至少48dp，按实际安全区与fontScale布局；绘制和命中共用TouchModalLayout。
物品/队员/槽位/商店稳定ID、模式、状态版本在按下时绑定，滚动、CANCEL、多点、切后台或状态变化取消点击。每个手势最多一个命令，独立下一点击仍可操作。
装备用纯业务结果预览；替换只原子提交原卸下→装备等价结果，失败不丢物品。交易用原TownTrade，一次一件，留在列表并显示真实结果，无普通RESULT继续确认。
药效/价格/合法目标查询现有定义和业务能力，未实现与暂不可用分开标注。存档失败准确提示并回滚本次变更，不由渲染结算。
验证分开记录真实正常路线、隔离fixture、新旧等价、冷启动/覆盖升级、模拟器尺寸与真机。skill职责及分类见对应SKILL.md；自动匹配失败不能写PASS。
