本批底层代码仅服务器本地提交 `c6de70b3d3d14f4aeb8b727481cdabe38405c5aa`；新完整codex_only84be73cc/Stop current，492 JVM/100 suites/12新检查真实通过。原main为文档/正式101基线16550c7；当前正式101/source272e5a0，下一正式Actions/App尚未启动。下一入口是下方已列的后台revision控制器与地图单指tap/虚线/原逐格执行，不把本底层提交当玩家功能或全目标完成。

当前底层阶段（2026-10-08T10:42:22.732534+00:00）：IMPLEMENTED_LOCAL_CHECKED / PLAYER_UI_NOT_IMPLEMENTED / APP_NOT_VERIFIED。基底开发提交16550c7；正式游戏仍101/source272e5a0，原build/runtime37757334463与publish37762465126首次SUCCESS、公网全字节/postflight齐备。

已实际实现并读核Core.kt最小共享查询、OriginalBoat.kt原停车条件抽取、MapNavigation.kt防御复制快照与BFS、MapNavigationTest.kt12项。原World/Scene构造与flagsAfterStep ABI保持，World执行/prepareTarget/CompletedStep/RNG/存档不另建；导航目前没有生产UI调用。快照复制数组、集合、方向限制、MapExit body字段与freeBoat，半步拒绝快照。固定上/左/下/右；状态含(x,y,terrainMode,parkedBoat)，落岸位置复用原停车条件的纯投影，不写玩家flags。非目标出口不扩展；纯query保留contact/edge零完成步，域外地面目标拒绝，不把edge虚拟坐标吸附地图。196608状态上限明确SEARCH_LIMIT，取消不交付部分路线；上限不是全地图可达承诺，手机耗时仍待验。

真实新492 DEBUG JVM/100 suites零失败错误跳过、12导航项（含24方向矩阵/原自然World单步、楼梯/船、冻结/取消/原接触与边缘）、DEBUG两包构建及409素材同正式101。直接wrapper exit126用原bash调用恢复，无chmod；定向v2的11项/3个fixture enabled/standing-spawn不合法失败，修正夹具后11通过，再增加边缘项后全492通过。失败日志/XML、v3与全量日志在 `/srv/fengshen-dev/receipts/map-navigation-*`，新实际摘要map-navigation-foundation-local-verified.json；不借101正式测试/App回执充当下一版验收。

本段仅底层可审查检查点；102只作为本地DEBUG版本参数，尚未冻结正式版本/源码或发Actions，不发布无玩家增量的底层阶段。下一自然批次接临时后台控制器（请求revision/起点/map/拓扑判定，过期结果拒绝）；单指按下世界格绑定与HUD/buttons优先；青色虚线/原镜头裁切、停止/手动接管、默认隐藏摇杆/原偏好；原FixedClock自然逐格与每步再验/遇敌剧情换图菜单后台失焦读档全取消。对象稳定ID最短合法交互位置/到达选项确认仍待实现，不关闭完整点击寻路子项。含船park状态可能扩大搜索，必须在新App核实际耗时和可取消性，不从CPU短fixture声称手机性能已验。

新四文件实际完整审查后record/Stop；交付记录与源码仅服务器保存。13T/声音/完整主线/真实云恢复与所有欠账保持OPEN。以下为实施前设计历史。

# 当前地图点击寻路的下一有限接续入口

2026-10-08T09:54:27.390256+00:00，SOURCE_READ_DESIGN_NOT_IMPLEMENTED。沿既定 WORLD-FULL-01 与已批准 v84-feedback-click-navigation 子项接续，无独立任务。当前v101/source272e5a0冻结，原Actions37757334463 App门禁执行；以下未实现、未新测试或App验收，不改冻结main，不当游戏交付。

已用prepare_context导航，再实际读取Core.World(268–435)、Scene.probeFrom/terrainDecision/blockType与定义、InputState、MainActivity MAP FixedClock入口/完成步结算/地图触控/硬件键。现有生产源码尚未发现地面点击规划/导航实现；仪器中的路线驱动不能当玩家功能。原场景解析、触点出口、船只锁、开发边界与动态碰撞必须复用，不能复制第二套规则。

有限完整批次拟先交付当前地图的合法地面点击最短路线、原速度逐格执行与青色虚线/终点；支持改目标、取消和手动接管，以及遇敌/剧情/菜单/换图/后台/失焦/读档全中断。默认隐藏摇杆、原设置可开启，偏好独立于存档。NPC/对象最短合法交互位置与到达选项仍属原批准计划，未实现时精确保留，不以地面导航关闭全项，也不显示隐藏对象或假入队奖励。

最小接法：从原World的probe/edge/contact判断抽共享只读过渡查询，不动现有构造ABI；取得当前Scene的不可变快照（grid/collision、enabled、动态对象及terrain/boat状态）和原出口，四向BFS状态为(x,y,terrainMode)，按上、左、下、右确定等长结果。只以移动步数计最短，不按毒伤/遇敌加权，不跨图借非目标出口绕行；触点入图的零完成步规则保持。查询不调用prepareTarget/enter、随机数或任何结算。

临时控制器使用地图/拓扑/起点/请求revision拒绝过期规划，仅自然完成当前格后替换目标，每格再检查原查询；向World.tickIntent(MoveIntent.cardinal)交方向，FixedClock原速度与processContactTransition/processCompletedStep继续唯一结算。不得用restore/直接坐标/finishStep循环或第二完成步回调来走路；不把导航路径写SaveSnapshot。

触控绑定按下时地图/世界格，单指有效tap才提交；HUD、按钮、菜单优先且不穿透，多指/拖动/CANCEL拒绝。虚线在原地图Canvas/Camera变换中绘制并裁切；完成或中断清除，不因战后/重启自动续走。现有合法赠物、商店、隐藏调查、药草与战斗/save保持。

实施前必须再次读最新真实源码/原健康preflight；针对JVM：共享过渡与原实际World单步的方向/模式/出口/动态对象一致、最短步数/绕墙/边界/阶梯/船、取消/旧revision/无状态与RNG副作用。新同源Android：真tap路线与实际速度/虚线、HUD不穿透、改目标/停止/手动接管、遇敌/毒步/剧情/换图/后台与保存完整cold；正常操作与fixture分列，原21Content36门槛/覆盖上一实际发布版保持。新raw协议绑定源码与实际测量，不借旧App；新代码完整审查/record/Stop齐备后沿原Actions签名、验收、同审核APK逐版发布/public/postflight。

当前未取得正常结局、一加13T帧耗时/内存、声音和真实账号云恢复；全部长期终点及其余视觉/动作/原名欠账仍OPEN。原失败、迁入dirty、冻结Windows历史和玩家存档保留。本入口只是已授权工作接续，不宣称计划登记或准备已经实现。
