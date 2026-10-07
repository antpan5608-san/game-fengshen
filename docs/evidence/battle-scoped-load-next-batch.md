# 下一有限批次：按当前战斗准备图像

状态：SOURCE_LOCATED / NOT_IMPLEMENTED；尚未冻结版本、App或发布。线上个人93、稳定82、c62与全部未完成项保持；同一WORLD-FULL-01，不创建新任务。当前缺口是生产启动仍准备固定16图，实际保留39,316,116字节；不能据此声称按战斗加载或手机性能通过。

已用CPU prepare_context只读定位、实际读取ContentLoader/Content、BattleVisualAssets/ResourceMap、MainActivity内容线程/Choreographer/两战斗入口/portrait与统一绘制、ContentTest/TouchTest。没有等待本地推理。只读设计/清单尺寸估算保存在服务器`.local-ai/battle-pose-next/scoped-load-notes.md`与`scoped-load-budget-research.json`，估算不提升为实际性能证据。

最小接入复用现有ContentLoader的准备通道与有界ResourceMap。MainActivity当前是一次性development-content-loader Thread；将同一通道变成可关闭单线程队列，启动只准备普通HUD/角色信息必需头像，战斗请求按真实party ID、精确enemy ID和原map/blackScene背景映射准备已有idle/动作图。未知/缺图继续原native回退，不能按文本或角色名猜新法术/环境。解码、源读取、缓存锁均只在worker，Canvas与命中共同读取不可变准备bundle。

随机入口processCompletedStep和story入口startStoryBattle必须同时绑定同一GameView、battle对象、battleID和epoch；UI挂接拒绝已退出战斗、新battle、旧activity、destroyed/closed结果。onDestroy拒新请求/取消未开始队列，已开始有界解码完成可丢弃结果；旧bundle被帧引用时不recycle bitmap。图像到达仅更新呈现，不改规则tick、计时、输入revision、RNG、结算或SaveSnapshot；不等待UI线程。

显式完整16图decoder取证与生产按需路径分开。保留ContentTest的完整hash、坏图15、64MiB有界缓存和封闭Source后绘制零读取门槛；新增真实两次battle/退出/冷启/新activity拒过期、缺图回退及正常图像到达证据。旧受控测试直接反射注入battle，须显式走原准备API，不能全16预热后冒称生产按需。启动/实际bundle/缓存保留/暂态解码峰值分开测，64MiB缓存不保证进程峰值或手机60fps。

只读尺寸估算：单哪吒+敌1、map7/16约5图11.4MB；四人+敌1约12图18.9MB；未知/未映射背景不擅自添加映射。以上ceil尺寸×4未包含Androidpadding、旧bundle和进程开销，仍NOT_MEASURED。字体2x现行双行是获批大字体适配，不能简单取消限制或缩小系统字体；姓名/HP/MP需实际Paint量宽与App目视，另批处理。

素材范围继续已审核r2的16图/来源/授权链，当前审核清单没有其他敌图、杨戬/姜子牙非待机动作、全部受击/倒地/胜利等图集；有待候选逐项核来源/hash/可发布范围。该缺口只阻塞对应视觉覆盖，不改变游戏规则或清空输入。

先原Linux preflight，有限代码→完整真实diff/源码审查→相关新JVM/Python/decoder/仪器检查→新codex_only/Stop→原Actions同源签名、21Content/36门禁及新的按需证据/实际图片/正常奖励用药完整cold。只有原门槛满足才原审核发布同APK、完整公网字节/postflight并接续。服务器暂无ADB连接设备；真机/声音/完整主线/真实账号恢复及三项长期终点继续OPEN，不能写长期完成或暂停回执。
