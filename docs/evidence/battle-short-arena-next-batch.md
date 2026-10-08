# 下一有限视觉批次：大字体短战场角色比例

状态：DESIGN_REVIEWED_NOT_IMPLEMENTED；同一WORLD-FULL-01/BATTLE-VISUAL-02，当前已公开验明96，稳定82／c62／全部欠账保持。

实际96三字体原PNG确认：2倍字三四人两排状态卡保持可读，但76dp高战场中的身体偏小。首选保留原卡片、字号、48dp命中、敌方图框/短反馈和统一斜列，减少角色身体框上下空白；仅illustrated且party>1、allyField.h<96dp适配，单人及普通高战场保持原几何。

已经用prepare_context导航并读真实BattleScreen、MainActivity.battlePartyBodies、BattleVisualGeometry、BattleScreenTest、TouchTest原三字体/动作队列和raw-proof。本次新prepare_context返回Error，已记录并退回rg+真实源码，不等待推理。六张既有已审裁框来自四人idle和哪吒attack/小龙女cast，无新素材/名称/能力；旧Windows master原始素材不发模型。

服务器设计 `.local-ai/battle-pose-next/short-arena-body-next-notes.md` 及 `short-arena-body-geometry-design.json`：102个历史95窗口数值组合、22拟改变，数值拟增高17.5%–29.03%；不是新JVM或App测试。候选身体高度(.8+.14t)、脚底(.84+.15t)沿同一独立槽位，不另建布局。实施前须用实际96raw核尺寸，确保六crop与每个原进攻/返回阶段不叠、不越界、其他身体不动、命中/状态/敌方结果相等。

下一动作：Linux实际preflight；最小共享纯布局及真实JVM边界/场景回归；原三字体仪器观察同Canvas实际prepared crop/四身体/99进度并绑定新raw字段，状态和RNG不变。新代码完整审查/真实相关测试/record_review_decision/Stop后才冻结原main/新Actions签名App。保持原21Content／36门禁、正常起始交互/物品/自然攻击/合法治疗/奖励/完整保存和外部cold，受控四人/Boss另列；实际看新原PNG/录像，门槛满足才原逐版批准发布、完整公网/postflight，再主动接续。

所有下一批IMPLEMENTED/PACKAGED/APP_VERIFIED/PUBLISHED均未达成；真机13T帧耗时/内存、声音、完整原结局、原法术A/B/C、寻路、完整美术及十类欠账保持OPEN。服务器唯一写入者，无新独立任务或暂停/完成标记。
