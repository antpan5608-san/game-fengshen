最新实际有限批次（2026-10-08T08:30:08.656551+00:00）：拟100 / `0.8.30-map-warm-personal`，IMPLEMENTED_LOCAL_CHECKED；正式签名、新App、发布均PENDING，线上99/稳定82。以下DESIGN段落为实施前历史。

本批最小接法：原contentWorker仍唯一同名单线程，改为显式ThreadPoolExecutor以移除待处理warm Runnable；新纯MapVisualWarmQueue由UI调度，同map/actor集合去重、latest scope合并、最多一项pending、battle进入及pause/focus loss/Surface/Destroy取消。正在解码一张可完成并进入原cache，下一张前检查token，不承诺解码可即时中断。Preparer.warmMap复用原selection/cache与64MiB上限；空enemy/black=false，只选当前角色已有头像/idle/合法pose和当前已批准背景，unknown原回退，不安装任何warm bundle、不写battlePrepared、不读取UI同步cache、不改ContentLoader/BattleActionStep/玩家规则/save。

GameView仅实际成功Surface post之后且active/focused/MAP/no battle提交；UI完成回调核原owner、token、当前map/actors，最多32条只读历史。原正常无grants路线结束取此实际历史，不强制等待warm，不替代原battle真实首送帧。新MAP_POST_SCOPED_CACHE_WARM_V1绑定到原正常raw/hash和runtimeScope，新验证实际单调post→submit→start→complete→delivery、exact approved manifest当前选择、失败/取消/64MiB；旧36门禁和原受控/全部save/cold/偏好保持，不能把丢弃或只unknown空选算预备成功。

新实际检查：`/srv/fengshen-dev/receipts/map-warm-local-build-first.log`，476 JVM/98 suites failures/errors/skipped全0，DEBUG应用/仪器构建成功；aapt确认为100/0.8.30-map-warm-personal。新增10 JVM覆盖重复帧、latest/coalescing、startup/battle保留、late token、cancel/close/retry、正在单图取消、失败cache/小预算和exact scope；`map-warm-python-first.log`为61项相关测试/OK，其中2新测试包含23种篡改/旧协议拒绝及取消/unknown诚实但不算成功。`map-warm-local-checks.json`保存实际计数；原99真实raw e020bd6d被新协议拒绝，`map-warm-old-v99-raw-rejected.json`保留。本批9源码/测试/配置完整实际审查，新codex_only e9ce81e5f979de4584faf884469e44344bf048f74477b5bcbbd44d3a49017558／Stop continue true，不复用99回执。

已核视觉manifest8dc53b77及16 PNG全size/hash仍与99相同，来源沿game-data/provenance/battle-visual-02-assets.json的OWN_BUILTIN_IMAGE_GEN_EXACT_COPY及迁入候选/授权；两个原不合格候选仍排除，无新素材。c62/392与409已批准资源保持，缺完整动作/敌图/探索美术仍OPEN。08:09:14Z新preflight查询成功NO_ISSUES_OBSERVED/errors空/cleanup0，仅15旧98上传事件/1real、无99/100手机验收。

下一步冻结同源main，原android-build.yml新100正式签名/App，独立核新21Content/36raw、MAP预备actual scope/time/正常与四人Boss分列、原图原片、7全cold/偏好及覆盖99。新App尚未运行，不能宣称预备改善、APP_VERIFIED或发布。全部原门槛通过并补真实审查才原reviewer同审核APK发布，公网全字节/postflight后将baseline由99更新为真实100并主动接下一有意义批次。迁入工具dirty、原失败、Windows冻结ZIP/历史/玩家存档保持，长期IN_PROGRESS/PARTIAL，无暂停或完成标记。

# 下一候选范围：地图首帧后的局部图像预备

DESIGN_SOURCE_READ_NOT_IMPLEMENTED；当前99/source a5bb7c6 / Actions37741740866仍冻结验收，线上98/稳定82/c62及长期IN_PROGRESS/PARTIAL保持。本文件仅接续入口，不变更冻结来源，不是新独立任务。

CPU prepare_context本轮成功，已再读真实MainActivity.onCreate/prepareBattleVisuals/firstInteractiveFrame/GameView.doFrame/requestBattleVisuals/createPartyBattle、ContentLoader.loadForPlay、BattleVisualAssets/Preparer/Definition、完整BattleVisualRequest/Selection与ResourceMap。doFrame为原Choreographer回调，实际Surface post后已有首次交互节点；原单contentWorker拥有preparer/cache，UI只接完成bundle。ContentLoader启动仅四头像；当前地图/队伍的准备可复用同一selection与缓存，不能在Canvas/主线程解码或创建第二套缓存/战斗/HP。

v98原AVD正常仅一个真实请求：queue0/prepare663/UI0/ready663/postDelay41/firstPosted704ms；三字体受控缓存后准备0–3ms但排队293–381ms。受控12图bundle18,864,036字节只是bundle保留量，不是进程峰值或手机预算。准备阶段未细分每图校验/解码，不能据这一个样本归因某文件或宣称预备一定改善。新99实际时序到齐后再核范围。

较小拟方案：只有活动/聚焦、真正MAP成功送帧后，按当前map和当前characters稳定ID、空enemy集合、blackScene=false做局部预备；未知map/actor仍原回退，不猜未来敌群或RNG、不改世界、存档、输入或原战斗请求守卫。只在现有worker预热缓存，不安装地图预备bundle到战斗UI，不把预备成功冒称battle素材或首战首帧已准备。

实施前须确定单一有界待处理任务/同scope去重、新map/party合并、battle请求取消尚未开始的旧预备、退出/销毁取消、活动解码每图之间可放弃剩余项的精确方式。保留worker单写cache与原64MiB预算、无主线程同步cache查询；不扩大为全16启动重解码。不能因队列忙加入线程并破坏cache所有权，也不为测试删原守卫。真实游戏首帧不等待預备；race/异常时只能回退，不能堵路线。

验收要求：真正新JVM去重/取消/迟到owner/小预算/优先battle无效预备模型边界；原Android实际准备/诊断stage与raw source/hash绑定，正常新游戏/无grants/不强制等待预备的原片及时序分列受控四人/Boss。原21Content36门禁、全save/外部cold/覆盖98或下一实际发布版、图像hash/缓存上限与失败恢复保持。新source完整审查/相关测试/record/Stop，再原Actions同源正式签名/App原画面通过才逐版发布/public/postflight。

尚缺：具体取消/合并实现与实际Android新测量；本文件没有新JVM、App或性能通过。真机13T帧耗时/内存/声音、真实多设备恢复、完整原主线、全部美术/角色动作/敌图/地图/寻路及累计欠账继续OPEN。原失败/迁入工具dirty/Windows冻结历史和原存档保持。

## 有界队列接法的源码核对（只读设计）

原contentWorker为Executors.newSingleThreadExecutor的委托实例，仅启动加载、battle任务和销毁使用；它没有公开remove待处理Runnable入口。后续实现不能仅无限submit再cancel Future而宣称队列有界。较直接可审范围是在原位置保留同一个、同名字、单worker的ThreadPoolExecutor，明确持有一个可取消warm Runnable：新scope先取消/移除旧待处理项，active warm在每张图之间检查token，battle进入先取消warm再走原submit；UI不读cache或等待任务。需要独立测试原启动/战斗顺序、同scope去重、最多一个待处理warm、旧owner/销毁拒绝和正在decode时取消后剩余项不读取。

GameView.doFrame为主线程Choreographer回调；可在成功render且Surface实际post返回之后、active/focused/MAP且无battle的自然节点捕获不可变map/characters范围。requestBattleVisuals仍原battle/ID/epoch守卫，不把旧map bundle安装进battle。未开始warm可remove，已开始的单张decode不能假称即时取消；最多保留该张的原cache结果并在下一张前停止。仍是方案候选，尚未新代码/App或性能验证；原99冻结验收继续。

## v99 新同源 App 的原因线索

v99/source a5bb7c6/原build37741740866的新 raw 与原片已独立验：正常两次自然遭遇，首请求 queue1/prepare1505/UI13/ready1519/post40/firstPosted1559ms；第二次prepare0/firstPosted71ms。受控三字体queue355–432/prepare0/firstPosted460–503ms，跟正常新游戏分列。首正常原片7.5–8.5s采样仍是原sprite/头像回退，9s已有新角色/背景。不同敌群与机器时序不能和98一个样本作可比性能结论；这些只是进一步局部预备的真实原因线索。

预备失败不阻塞正常战斗重试；同scope去重只阻止重复预热，battle取消后同图首帧可重新入队。Activity pause/focus loss、GameView Surface销毁与Activity销毁须取消旧任务，token在每张校验/解码之前检查；当前单图若已开始可完成缓存，不假称可中断解码。只记录当前范围stage/耗时/成功等既有允许scalar，warm完成不写battlePrepared、不安装content.battleVisual、不修改snapshot/RNG。原21Content36门禁及当前99全save/cold实际基线保持。尚无新warm代码或测试通过。
