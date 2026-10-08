# 下一有限视觉批次：四人身体间距

状态 SOURCE_LOCATED_DESIGN_PENDING_IMPLEMENTATION；同一WORLD-FULL-01，v94已个人发布并公网/postflight复核，不预占新版本、不复用94App回执为下一代码检查。

已用CPU prepare_context定位并实际阅读BattleScreen.kt、BattleVisualGeometry.kt、MainActivity.drawBattleScene/BattleActionStep及BattleScreenTest。服务器私有只读记录 `.local-ai/battle-pose-next/party-body-next-notes.md` 固定来源hash；本轮还实际目视本94三字体四人idle/attack/cast及治疗原图，确实存在身体轮廓重叠，2x两行卡片挤占场地。旧测试只证inside/foot/48dp，不能作为不遮挡验收。

最小入口是battleSceneLayout的illustrated allySprites：x仅0.42倍allyField跨度、单宽可达0.48，四人当前横向间距约0.14倍，允许大面积相互覆盖。下一实施在同一函数调整身体槽位宽/间距和对角足点，保留原角色slot/order/卡片/命中/敌布局、获批双排大字及系统fontScale；不靠缩小字体，不改规则或另建布局系统。battleVisualBodyBounds依原裁框保比和脚底，MainActivity原attack位移最大arena.w*0.055，准备期反向仅0.08倍，应一同检查移动边界与其他角色遮挡，不能只验idle盒。

先对实际已审裁框四idle/哪吒attack/小龙女cast投影量化重叠与足点；提出有界间距适配，再新JVM覆盖一至四人/三字体及实际960与2640safe范围、动作准备/前移/回位，原48dp/信息层/完整state/RNG门槛保持。仅现有合法动作，杨戬/姜子牙未覆盖姿态留idle或native，不编造动作/法术/入队。

实施前原Linux preflight；每次改代码全文diff/真实源码/相关新测试后codex_only与Stop，再原Actions同source/同审核签名App实际新静态/动作/正常片/完整保存/外部冷启取证。缺手机/声音仅阻塞对应验收，不能凭几何或截图库存在关闭。达到原门槛才发布一版、公网完整字节/postflight后接续。完整结局、真实账号及十类欠账保持OPEN。
