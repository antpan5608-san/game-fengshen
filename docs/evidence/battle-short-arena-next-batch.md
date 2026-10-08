# 下一有限视觉批次：大字体短战场角色比例

状态：IMPLEMENTED_LOCAL_CHECKED_NOT_PACKAGED_NOT_APP_VERIFIED；同一WORLD-FULL-01/BATTLE-VISUAL-02，当前已公开验明96，稳定82／c62／全部欠账保持。

实际96三字体原PNG确认：2倍字三四人两排状态卡保持可读，但76dp高战场中的身体偏小。首选保留原卡片、字号、48dp命中、敌方图框/短反馈和统一斜列，减少角色身体框上下空白；仅illustrated且party>1、allyField.h<96dp适配，单人及普通高战场保持原几何。

已经用prepare_context导航并读真实BattleScreen、MainActivity.battlePartyBodies、BattleVisualGeometry、BattleScreenTest、TouchTest原三字体/动作队列和raw-proof。本次新prepare_context返回Error，已记录并退回rg+真实源码，不等待推理。六张既有已审裁框来自四人idle和哪吒attack/小龙女cast，无新素材/名称/能力；旧Windows master原始素材不发模型。

服务器设计 `.local-ai/battle-pose-next/short-arena-body-next-notes.md` 及 `short-arena-body-geometry-design.json`：102个历史95窗口数值组合、22拟改变，数值拟增高17.5%–29.03%；不是新JVM或App测试。候选身体高度(.8+.14t)、脚底(.84+.15t)沿同一独立槽位，不另建布局。实施前须用实际96raw核尺寸，确保六crop与每个原进攻/返回阶段不叠、不越界、其他身体不动、命中/状态/敌方结果相等。

下一动作：Linux实际preflight；最小共享纯布局及真实JVM边界/场景回归；原三字体仪器观察同Canvas实际prepared crop/四身体/99进度并绑定新raw字段，状态和RNG不变。新代码完整审查/真实相关测试/record_review_decision/Stop后才冻结原main/新Actions签名App。保持原21Content／36门禁、正常起始交互/物品/自然攻击/合法治疗/奖励/完整保存和外部cold，受控四人/Boss另列；实际看新原PNG/录像，门槛满足才原逐版批准发布、完整公网/postflight，再主动接续。

本批IMPLEMENTED已达成；PACKAGED/APP_VERIFIED/PUBLISHED均未达成；真机13T帧耗时/内存、声音、完整原结局、原法术A/B/C、寻路、完整美术及十类欠账保持OPEN。服务器唯一写入者，无新独立任务或暂停/完成标记。

2026-10-08T05:04:31.765209+00:00 当前有限代码已实施：只改BattleScreen的两项多人短场系数，原ContentLoader／Canvas共享body projection／BattleActionStep／七规则源码未改。新456 JVM／96 suites失败错误跳过0、44相关Python（visual proof／APK／handoff）、DEBUG应用和仪器构建通过，409内容／视觉字节同已公开96。首轮456中1项因较高桌面场错误假定短场失败，日志/XML保留；测试仅对实际短场执行99进度，正常桌面原几何继续另验，复测通过。三字体仪器新原字段尚未App运行；raw新bodyScale／density／arena／allyField／envelope及99倍数有限投影，拒旧协议、NaN/bool、越界、脚底不合与短场过多留白；本机synthetic检查不当App。旧96真实raw按新协议已实际拒绝，不能复用96图片或cold验新候选。原21Content／36门禁保留，scope新hash119b9cf6；本批preflight04:55:32Z查询成功errors空cleanup0，仅95样本。拟97尚未冻结／签名／App／发布，须新codex_only／Stop→选择性提交main→原Actions97新验收。
