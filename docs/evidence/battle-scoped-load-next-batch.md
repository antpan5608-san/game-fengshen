# 下一有限批次：按当前战斗准备图像

状态：IMPLEMENTED_LOCAL_VERIFIED；拟94 / 0.8.24-scoped-visual-personal，尚未正式签名/App/发布。线上个人93、稳定82、c62与全部未完成项保持；同一WORLD-FULL-01，不创建新任务。以下原只读设计保留为实施前记录。

新有限实施：BattleVisualAssets保持原五参数完整16图构造/封闭Source零绘制读取及坏图15门槛，分离可复用BattleVisualPreparer和不持有Source/cache的不可变lookup。BattleVisualRequest复制真实身份，只取审核清单的actor已有portrait/idle/attack/cast、精确enemy ID和map/blackScene背景；未知保持native回退，失败不缓存可重试，旧bundle不recycle。生产ContentLoader.loadForPlay仅准备四头像，原load签名保留完整包取证；同一development-content-loader改可关闭单队列。随机/story入口都按battle对象/UUID/epoch请求，UI同时核当前GameView和未销毁Activity；退出拒旧epoch，onDestroy拒新请求/取消队列，结果仅替换绘制/命中共用lookup，规则/tick/RNG/存档不改。

新本地450 JVM/96 suites零失败错误跳过，生产接入后v2确实执行testDebugUnitTest；最终只增仪器合并断言/清单pin，JVM为UP-TO-DATE不冒称再次执行。61相关Python实际最终5.538秒通过（含原environment-review嵌套32项自动审批fixture），应用/仪器DEBUG新编译成功。DEBUG参数94仅编译候选，不是签名或App通过。ci新scope7d9b724e绑定四头像/精确12图/旧epoch/另一battle/退出/销毁owner拒绝，严格拒旧16预热报告/遗漏/非布尔值；36原个人门禁保留。ContentTest新增真实decoder设计的4头像/5图/7图/共享缓存/8图合并/坏图恢复断言；TouchTest新增实际生产准备与owner guards断言，目前仅编译，待原Actions真实执行，不把断言存在称App证据。

新preflight 2026-10-07T23:19:07Z为NO_DATA，保留93/92，不能当手机健康。来源范围仍已审r2原16图/清单，未增或改素材。拟94须新同源Actions/签名/21Content和36门禁/三字体原图及正常供应攻击胜利治疗完整save与外部cold实际回执后才能原批准发布；不能复用93App。证据在服务器`/srv/fengshen-dev/receipts/battle-scoped-load-preflight.json`、`battle-scoped-load-build-v2.log`、`battle-scoped-load-build-final.log`和`battle-scoped-load-python-final.log`。所有真机/声音/正常完整结局/其他视觉覆盖和十类欠账保持OPEN；候选失败保留，不发布未验包。

已用CPU prepare_context只读定位、实际读取ContentLoader/Content、BattleVisualAssets/ResourceMap、MainActivity内容线程/Choreographer/两战斗入口/portrait与统一绘制、ContentTest/TouchTest。没有等待本地推理。只读设计/清单尺寸估算保存在服务器`.local-ai/battle-pose-next/scoped-load-notes.md`与`scoped-load-budget-research.json`，估算不提升为实际性能证据。

最小接入复用现有ContentLoader的准备通道与有界ResourceMap。MainActivity当前是一次性development-content-loader Thread；将同一通道变成可关闭单线程队列，启动只准备普通HUD/角色信息必需头像，战斗请求按真实party ID、精确enemy ID和原map/blackScene背景映射准备已有idle/动作图。未知/缺图继续原native回退，不能按文本或角色名猜新法术/环境。解码、源读取、缓存锁均只在worker，Canvas与命中共同读取不可变准备bundle。

随机入口processCompletedStep和story入口startStoryBattle必须同时绑定同一GameView、battle对象、battleID和epoch；UI挂接拒绝已退出战斗、新battle、旧activity、destroyed/closed结果。onDestroy拒新请求/取消未开始队列，已开始有界解码完成可丢弃结果；旧bundle被帧引用时不recycle bitmap。图像到达仅更新呈现，不改规则tick、计时、输入revision、RNG、结算或SaveSnapshot；不等待UI线程。

显式完整16图decoder取证与生产按需路径分开。保留ContentTest的完整hash、坏图15、64MiB有界缓存和封闭Source后绘制零读取门槛；新增真实两次battle/退出/冷启/新activity拒过期、缺图回退及正常图像到达证据。旧受控测试直接反射注入battle，须显式走原准备API，不能全16预热后冒称生产按需。启动/实际bundle/缓存保留/暂态解码峰值分开测，64MiB缓存不保证进程峰值或手机60fps。

只读尺寸估算：单哪吒+敌1、map7/16约5图11.4MB；四人+敌1约12图18.9MB；未知/未映射背景不擅自添加映射。以上ceil尺寸×4未包含Androidpadding、旧bundle和进程开销，仍NOT_MEASURED。字体2x现行双行是获批大字体适配，不能简单取消限制或缩小系统字体；姓名/HP/MP需实际Paint量宽与App目视，另批处理。

素材范围继续已审核r2的16图/来源/授权链，当前审核清单没有其他敌图、杨戬/姜子牙非待机动作、全部受击/倒地/胜利等图集；有待候选逐项核来源/hash/可发布范围。该缺口只阻塞对应视觉覆盖，不改变游戏规则或清空输入。

先原Linux preflight，有限代码→完整真实diff/源码审查→相关新JVM/Python/decoder/仪器检查→新codex_only/Stop→原Actions同源签名、21Content/36门禁及新的按需证据/实际图片/正常奖励用药完整cold。只有原门槛满足才原审核发布同APK、完整公网字节/postflight并接续。服务器暂无ADB连接设备；真机/声音/完整主线/真实账号恢复及三项长期终点继续OPEN，不能写长期完成或暂停回执。
