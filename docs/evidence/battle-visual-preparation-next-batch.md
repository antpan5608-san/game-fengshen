# 下一有限视觉批次：首战准备时序取证

状态：SOURCE_READ_AND_NEXT_SCOPE_IDENTIFIED_NOT_IMPLEMENTED；当前97个人版已验发布，稳定82／WORLD-FULL-01 IN_PROGRESS/PARTIAL及全部欠账保持。无新独立任务。

本轮CPU prepare_context成功（background_summaries=false），再读真实MainActivity.onCreate/prepareBattleVisuals/GameView.requestBattleVisuals/deliverBattleVisuals、Content.kt 180–197与1229–1239、完整BattleVisualAssets.kt及TouchTest.awaitBattleVisuals/原正常起始战斗路线。ContentLoader的真实定义在Content.kt；此前猜独立ContentLoader.kt/BattleEnemyFeedback.kt路径的只读命令失败不算游戏测试，已按真实定义读取。

实际97正常录像37秒与受控早期截图可见原native／头像回退；随后已准备插画可见。当前代码只在启动准备四头像，战斗请求时复位startup bundle，在原contentWorker单队列读取当前party/enemies/map/blackScene选择，worker完成后通过owner/当前battle/id/epoch/layer守卫交付。原受控姿态测试等待battleVisualPrepared后核12份当前资产；正常路线不等待，保持原真实玩法。不能据这些证据声称哪一步慢、连续回退时长、主线程卡顿或真机性能。

下一最小操作：先执行Linux实际preflight，再为同一请求记录单调时钟的提交、worker开始/准备结束、UI接收/拒绝和实际已准备首绘制节点；仅统计脱敏时长/作用域/准备计数，不含完整存档、坐标、账号或素材。复用现有Diagnostics与原单队列，不在Canvas解码/I/O，不阻塞原输入/战斗时钟，不改变owner/epoch守卫、cache64MiB或规则。补真正有界时序/过期拒绝检查及原三字体仪器实际取证；原完整36门禁/正常起始交互/物品/自然攻击/治疗/奖励/全save冷启均保留。只有实际原因成立后才选择局部缓存复用/已获批scope预备，不盲目重新全量启动解码。

无外部权限缺口阻碍该局部工作；当前确实缺的是本请求排队/准备/UI交付/首绘制时长原证据。手机13T/声音/真实账号与完整主线外部验收仍需真实输入，未验不关闭。新代码后完整审查、相关真实测试、新record_review_decision/Stop，同源Actions新签名/App通过才发布下一版，不借97回执验新代码。

多敌短条可独立后续接续：`/srv/fengshen-dev/receipts/multi-enemy-gauge-v97-next-design.json`用本版原三字体/四队型六敌72份度量重新计算，24份拟缩短，条框仍在旧范围且脚底不交叠；该计算只为设计，不是新JVM或新App。真实BattleScreen.battleEnemySceneLayout与MainActivity.battleEnemyFeedback/Canvas共用路径已读；完整编号、详情、原sprite/48dp目标/可见HP模型保持。资源缺口详见[当前覆盖](visual-resource-coverage-20261008.md)，缺来源的敌2/3/其余姿态不能直接接入。

在服务器保存checkpoint并沿同一thread继续，不依赖Windows，不设development-paused或长期完成标记。
