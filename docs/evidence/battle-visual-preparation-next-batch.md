最新有限实施：拟98 / `0.8.28-visual-timing-personal`，IMPLEMENTED_LOCAL_CHECKED；新462 JVM/97 suites、46 Python和DEBUG两包通过，新完整codex_only a21dd987/Stop current。正式签名/App时序/发布未验；实际线上97保持。以下原未实施描述是历史。

# 下一有限视觉批次：首战准备时序取证

状态：SOURCE_READ_AND_NEXT_SCOPE_IDENTIFIED_NOT_IMPLEMENTED；当前97个人版已验发布，稳定82／WORLD-FULL-01 IN_PROGRESS/PARTIAL及全部欠账保持。无新独立任务。

本轮CPU prepare_context成功（background_summaries=false），再读真实MainActivity.onCreate/prepareBattleVisuals/GameView.requestBattleVisuals/deliverBattleVisuals、Content.kt 180–197与1229–1239、完整BattleVisualAssets.kt及TouchTest.awaitBattleVisuals/原正常起始战斗路线。ContentLoader的真实定义在Content.kt；此前猜独立ContentLoader.kt/BattleEnemyFeedback.kt路径的只读命令失败不算游戏测试，已按真实定义读取。

实际97正常录像37秒与受控早期截图可见原native／头像回退；随后已准备插画可见。当前代码只在启动准备四头像，战斗请求时复位startup bundle，在原contentWorker单队列读取当前party/enemies/map/blackScene选择，worker完成后通过owner/当前battle/id/epoch/layer守卫交付。原受控姿态测试等待battleVisualPrepared后核12份当前资产；正常路线不等待，保持原真实玩法。不能据这些证据声称哪一步慢、连续回退时长、主线程卡顿或真机性能。

下一最小操作：先执行Linux实际preflight，再为同一请求记录单调时钟的提交、worker开始/准备结束、UI接收/拒绝和实际已准备首绘制节点；仅统计脱敏时长/作用域/准备计数，不含完整存档、坐标、账号或素材。复用现有Diagnostics与原单队列，不在Canvas解码/I/O，不阻塞原输入/战斗时钟，不改变owner/epoch守卫、cache64MiB或规则。补真正有界时序/过期拒绝检查及原三字体仪器实际取证；原完整36门禁/正常起始交互/物品/自然攻击/治疗/奖励/全save冷启均保留。只有实际原因成立后才选择局部缓存复用/已获批scope预备，不盲目重新全量启动解码。

无外部权限缺口阻碍该局部工作；当前确实缺的是本请求排队/准备/UI交付/首绘制时长原证据。手机13T/声音/真实账号与完整主线外部验收仍需真实输入，未验不关闭。新代码后完整审查、相关真实测试、新record_review_decision/Stop，同源Actions新签名/App通过才发布下一版，不借97回执验新代码。

多敌短条可独立后续接续：`/srv/fengshen-dev/receipts/multi-enemy-gauge-v97-next-design.json`用本版原三字体/四队型六敌72份度量重新计算，24份拟缩短，条框仍在旧范围且脚底不交叠；该计算只为设计，不是新JVM或新App。真实BattleScreen.battleEnemySceneLayout与MainActivity.battleEnemyFeedback/Canvas共用路径已读；完整编号、详情、原sprite/48dp目标/可见HP模型保持。资源缺口详见[当前覆盖](visual-resource-coverage-20261008.md)，缺来源的敌2/3/其余姿态不能直接接入。

在服务器保存checkpoint并沿同一thread继续，不依赖Windows，不设development-paused或长期完成标记。

## 本批实际代码与验收边界

BattleVisualTiming只保存请求内单调时钟，区分queue/prepare/delivery/ready与UI收到后postDelay/firstPosted；原data class不含battle、source、cache、存档或素材。MainActivity沿原单contentWorker，在实际UI回调构造不可变观测，owner与GameView当前battle/id/epoch/layer守卫保持。真正scene绘制成功、原holder.unlockCanvasAndPost返回后才记一次firstPosted；离线Canvas渲染、信息/物品/结果层、未成功完成render不标送帧。非成功bundle另外标注，不以fallback送帧冒称素材准备成功。

Diagnostics复用服务端已有stage/verificationMs/reason/success，单请求四个完成阶段＋两个首次送帧事件，不发送身份/坐标/完整存档。旧scope/prepared/decodedBytes仍被公网白名单过滤，数量与预算沿原已hash绑定App raw记录；本批没有改服务端或触发服务部署。请求在销毁前未开始的原取消行为保持，未完成请求不编造耗时；已完成却stale的UI结果标注拒绝。

三字体受控原姿态测试等待实际Surface送帧后写preparationTiming，原四阶段/99投影/state/RNG/epoch拒绝断言保持。正常真正新游戏路线按每场结束记录观察，不额外等待素材、不造伤害/敌群，未交付/未送帧保留false/null；至少一个成功准备且真实已送帧报告才通过。正常药草/奖励/完整save与外部cold沿原驱动，四人/Boss另列。

原rawproof强制CURRENT_BATTLE_MONOTONIC_TO_POST_V1、整数非负/非bool、逐阶段总和、实际送帧与正常preparedSucceeded，旧97原证据缺新文件已真实拒绝。新正常取证文件已加入原PERSONAL_TEST artifact范围，collector已实际读路径/时序；36原门禁不减，scope hash `6b2d7ffc6d4a23298f5aba5e812d34813770fe8c5b9848df914e3ac22ad8ce78`。runtimeBaseline保持实际97。

本机新462 JVM/97 suites和46 Python、应用/仪器DEBUG均真实通过（初版与最终v2日志各保留），其中6项新JVM覆盖时序划分、一次首帧、同tick、负/倒序、首帧倒序和大uptime；2项新Python覆盖缺报告/假送帧/错误类型/总和、正常无成功和回退不当准备成功。最新生成XML独立计数；409素材完整字节同已审核97、16份OWN_BUILTIN_IMAGE_GEN_EXACT_COPY来源/hash保持。生产除MainActivity的30份既有Kotlin与97逐字节相等，新增纯计时类不参与规则。误猜独立SaveSnapshot/HerbUse/World/BattlePresentation文件名的只读检查断言失败保留，按真实文件集合纠正，不是游戏测试失败；其他只读查找失败亦不计测试通过。

回执：服务器receipts/visual-timing-local-checks.json、visual-timing-production-source-verified.json、visual-timing-old-raw-rejection.json、visual-timing-build[-v2].log、visual-timing-python-tests[-v2].log。实际preflight05:58:54Z NO_DATA/0实机/errors空cleanup0/权威97/96，不当手机健康。

下一步冻结原main/98同源Actions，独立新签名/462 release JVM与原Python、21Content/36新raw、三字体真实排队/准备/交付/实际送帧及正常每场记录，再看新原图/原片、完整cold/偏好/覆盖升级。仅有编译不能说明首战回退原因或已改善；实际时序后才选择局部改进。全部门槛满足再新record/Stop、原reviewer发布同SHA、公网全字节/postflight并主动接续；声音/13T性能/完整主线/真实云恢复和整套视觉继续OPEN。
