---
name: fengshen-touch-ux
description: 在本项目被授权的Android交互任务中，遇到物品/装备/已有商店的光标导航、A/B层层确认、逐件翻页、重复确认或触摸误触，或当前明确授权的必要对话/战斗触控适配时使用；不用于纯ROM研究、音频、数值或构建任务，不授权全库UI改造或改变游戏规则。
---

# 范围与工作流

只作用于当前任务授权的界面。自动适用不等于全库扫描、永久后台改代码或反复重设计。已合理的直接点选保持。

1. 读AGENTS.md、docs/current-task.md与docs/android-ui-design.md的触控迭代段，查实际代码/可运行界面；不只看旧报告。
2. 分开记录交互问题、功能未实现、数据/原版规则缺失。UI不能补造药效、价格或合法性。
3. 列出当前对象稳定ID、场景、真实库存/队员、合法动作、目标、状态版本及副作用。
4. 逐项判断：
   - PRESERVE：已合理，保持。
   - ADAPT：当前授权内触屏适配，直接做。
   - NEEDS_RULE_EVIDENCE：规则缺失或冲突，UI不推断。
   - ASK：改变玩法、不可逆行为或超范围；提出一个具体问题，不执行新增行为。
   - OUT_OF_SCOPE：当前任务不涉及，记录不修改。
5. 小范围复用TownTrade、OpeningEquipment、HerbUse、统一状态/SaveSnapshot和SurfaceView/Canvas；物品/商店选中不提交，明确动作才结算；当前明确授权的战斗默认攻击模式可一次点存活敌人提交攻击，独立信息入口不消耗行动，药品列表不得套用点怪即执行。装备预览用纯结果，不临时改真实角色；未实现动作按实际原因标注。
6. 对照新旧合法操作的状态与实际点击路径；测稳定对象、滚动/CANCEL、多点、列表变化、后台、存档/重启，dp目标及系统字体。真实运行、fixture和结构检查分开记录。
7. 交付时只增量修正已验证方法，保留失败和限制；没有新方法不凑条目。

来源、内容、巡检、CI及发布接续使用fengshen-content-iteration及原脚本，不在本skill复制第二套。

# 验证边界

匹配正例：商店上一件/下一件改直接点选；装备多次A确认按当前规则简化。
负例：音频超时、南海ROM出口坐标不扩UI。
高风险例：自动卖全部装备买最贵装备属于ASK，不据此改变原版经济。
保持例：已有直接点选+明确提交且没有问题，PRESERVE。
本文件的匹配测试不等于App验收，自动匹配能力以实际环境结果为准。

# 已运行的方法与限制

- 绘制与命中共用TouchUi.kt中的touchModalLayout；panelItemBounds/shopItemBounds按稳定ID取得实际可见区域，不拿旧页面比例当新坐标。
- TouchTest.testTouchUxSelectionScrollAndAtomicEquipment已在签名候选的隔离Actions AVD运行：选择不变状态，滚动后装备/卸下与原纯业务结果一致，重复UP不重复提交。
- testTouchUxBaselineClickPath在旧正式APK实际运行；record_app_audio.py --silent --comparison只用于受控UI对比，不冒充正常游玩或声音验收。
- testExportCurrentSaveForUpgrade/testUpgradeKeepsPreviousSave在原runtime脚本通过。跨APK仪器测试须保留既有public方法的JVM签名：曾把persistState的void返回改为boolean，旧APK测试进程崩溃；已恢复原签名，私有函数返回保存结果。
- 最后一个滚动列表项可能只露出不足48dp的区域；先滚到可点范围再点击，不能靠越界坐标或削弱断言通过。滚动到合法区域后，实际交易边界复测通过；坐标须在当前面板/模式打开后取得。旧药草测试曾预期多点仍提交，现按多点取消改测，独立点击仍执行原药效；药效断言保持。
- skill显式与隐式隔离CLI尝试均遇到认证401；不将结构/人工案例判断写成自动匹配成功。正式APK、真机与声音须按内容skill和任务门禁另验。

- 曾仅设置wm尺寸并得到测试PASS，但截图实际分辨率不同且2×字体动作与标签重叠；必须记录截图真实尺寸及GameView有效窗口，检查真实命中框与完整文字，并目视截图。原CI的退出清理已实际白名单回收隔离测试截图，失败也可审查；不拉用户资料。
- 原runtime已实际用匹配AVD的hw.lcd尺寸与emulator -skin运行；testTouchUxPhoneSizeAndLargeFont同时断言UiAutomation截图尺寸、有效窗口及布局区域，在1/1.3/2字体下通过。短安全窗口用紧凑标题，操作按钮与导航分开，详情可滚动；触摸目标至少48dp，不能以扩大命中框掩盖文字重叠。
- testNormalTouchUxSupplyAndEquipment的实际新游戏路径已录制：真实交易、装备/卸下、受伤后药草使用；原录屏器另执行外部force-stop、恢复状态相等和继续探索。正常流程与受控边界/旧UI对照分别标记，不用fixture证明可玩，不用静音录像证明声音。


# 已验证的限定场景适配

- 新对象图形明显大于旧小怪时，先核原观察矩形，复用数据中的可选origin与同一battleEnemyBox绘制/命中；原控制区/旧敌位置PRESERVE。实际受控几何断言与正常App原片/截图均验证大图完整显示，没有为此重做战斗布局。
- 正常移动的仪器helper应通过UI线程同步观察完成序列/remaining和换图完成；后台读到remaining=0可能处于落门但出口尚未派发的中间态。修观察方式，不改碰撞/业务或调用world.tick制造玩家移动。
- 交谈可能合法转向相邻NPC。实际再入复谈测试仅按已观察的合法朝向修正expected snapshot；完整金钱/角色/物品/装备/flag/位置/计数断言保持，不写真实状态、不删防重复结算断言。


# 当前授权的战斗触控边界

成长进度从同一GrowthRow累计门槛派生，不持久化第二份EXP；缺后续数据不写MAX。敌人信息绑定战斗ID和slot，行动时只读当前BattleActionStep快照。战斗使用自身安全区域，隐藏探索摇杆/A/B；新目标手势绑定输入revision，取消/多点/动画期间不得提交。普通奖励自动返回与Boss胜后剧情确认分开。相关纯计算/布局测试已在本地JVM执行；真实App、字体截图、同产物发布以本轮交付门禁结果为准，尚未运行的步骤不得宣称成功。


## 已执行的移动战斗检查

GrowthRow.level为达到等级、threshold为累计EXP，HUD/详情只派生(E-T(L))/(T(L+1)-T(L))；缺下一级不写MAX，异常诊断不改存档。MobilePlayTest已实际覆盖门槛、跨级、缺数据、同种实例/快照及1/1.3/2字体几何。战斗默认点存活实例攻击，独立信息不耗行动，药品列表选中不消耗；各手势绑定battleID/slot或物品/目标ID及revision，滚动/CANCEL/多点取消，动作期间锁指令。BattleActionStep表示当前阶段HP，最终战斗状态不提前显示。普通奖励自动返回，Boss保留胜后剧情确认。

原KVM已执行同产物旧档覆盖、取消/重复UP、商店/装备/地图与战斗药草、正常升级及连续南海胜后冷启；完整候选必须通过当前任务最终门禁。普通结算自动关闭后，仪器测试曾跨线程先读layer再强转battle而NullPointerException；在UI线程原子观察两者。RESULT与真实提交可能处于同一UI回调的中间态，观察需同时确认battleCommitted，保留经济/剧情断言，不把测试观察错误写客户端崩溃。

字体截图必须人工看完，几何PASS不能替代可读性。已有medicine面板不使用tab时移除空占位；目标HP、恢复/上限及满血消耗三行保证可见，行动顺序/取消说明可滚动，动作按钮独立。testMobileBattlePhoneSizeAndLargeFont实际在1/1.3/2字体核验可视高度、滚动不扣物品/HP及48dp命中；仅证明记录的屏幕/有效窗口，不能推断所有窄屏、真机或声音已通过。


## 已验证的两角色输入边界（本地规则，App待验）

既有OpeningBattle收集稳定角色ID的指令；首人提交只递增inputRevision，不播放回合或采随机数，待真实可输入角色收集完成才运行原排序。显示使用BattleActionStep.partyHp/partyStatus快照，不能把后续第二人的HP提前泄露。信息入口不提交；物品行仅选择，独立目标按钮仅选目标，明确使用才确认。战斗中死队友用草仍消耗但不复活是已核原规则，不能套地图可用性。

原部分逃跑成功不立即结束全队，可能强制下一角色并在其原调度格再次尝试；不得通过UI绕过该规则或退回已预扣物品。13项PartyBattleTest/两项朝向纯测试已实跑；布局在1×/1.3×/2×验证两角色状态空间，实际App画面仍待当前同产物runtime，不能写手机或隐式skill匹配已通过。


## 已编译的连续流程驱动修正（实际新App待验）

原开局zoneMapId/zoneRects与后续zones分开存放；合法正常训练邻格同时查询两域并保留probeFrom/出口检查，不能因漏查主区而认定原路线不可走。launch失败暴露当前加载错误文本，ContentTest先核真实候选内容再执行覆盖升级。全活人08时不能由仪器合成指令，应等待既有控制器推进；新无触控受控验证已编译，runner结果未取得前不得写正常流程PASS。

- 正常续跑曾在原墙前No step失败：规划经过22,28，观察位置却为23,28，完整预计算键序列不能假定长按摇杆总恰好一格。仪器释放与“已开始”观察保持同一UI回调，路径偏离时从实际合法状态再规划；保留碰撞、自然遭遇与出口端点断言，不调用World.tick或修玩家坐标。修正已编译，新App复测待当前runner，不写成已通过方法。


## 已验证的场景续段模型（实际新App待验）

原StoryFollowup现复用同一阶段flag/无副作用proposal接非战斗对白；本地SceneStoryTest已验证取消前无提交、旧阶段命令拒绝、无额外奖励、中毒步成本和完成位。UI仍使用原对白触摸与存档事务，原Boss续段保持同一入口；新App中段再启/取消与正常转世仅已编译，实际runner结果未取得前不可写PASS。单向出口不因冷启测试而制造反向路线。
