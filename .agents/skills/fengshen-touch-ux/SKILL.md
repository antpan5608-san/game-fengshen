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
5. 小范围复用TownTrade、OpeningEquipment、HerbUse、统一状态/SaveSnapshot和SurfaceView/Canvas；选中不提交，明确动作才结算。装备预览用纯结果，不临时改真实角色；未实现动作按实际原因标注。
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
