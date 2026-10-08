# 下一有限视觉批次：敌人附近的名称与短血条

状态：DESIGN_PROTOTYPED，游戏源码 NOT_IMPLEMENTED，Android 实际量字／新 JVM／App／发布 NOT_RUN。属于既有 WORLD-FULL-01；v95 已按原同源审核发布并公网／postflight 核验；本设计不作为下一源码的验收。

已用 CPU `prepare_context` 导航并读取真实 `BattleScreen.battleEnemySceneLayout`、`MainActivity.battleEnemyBox`／统一绘制／`battleLine`、`BattleScreenTest`、两项三字体 phone/party 仪器测试及 `battle_ui_evidence.py`。当前单敌标签和血条宽度来自整个敌方 cell；编译布局探针显示原标签与血条有 2–4dp 几何重叠。该探针不是新 App 故障复现。

只复用现有已审敌 1／137 crop 和 native 回退，不新增素材、名称、HP、能力或规则。保留原图像比例和缩放、48dp 命中 cell、多人／六敌 compact 编号、状态卡、系统字号及完整信息层。

最小适配是共享只读反馈布局：根据实际绘制 sprite、cell、Android Paint 的完整显示文字量宽及 fontMetrics 行高，优先放在图框下方；完整文字行、间隔、血条和底部留白放不下时，使用图框侧边空白。宽度受 cell/可用空白限制。仅减少空白，不缩小字号或进一步压缩大字小战场精灵；极端输入无法适配时保留有界回退并明确未解决。

服务器设计入口 `.local-ai/battle-pose-next/enemy-feedback-next-notes.md`，数值原型 `enemy_feedback_geometry_draft.py` 和结果 `enemy-feedback-geometry-draft-result.json`。24 个真实编译单敌 cell（两窗口、三字体、一至四人）接两张真实 crop；合成文本宽度／行高范围共 1440 组，768 下方、672 侧边，框内且无相互重叠。合成范围不是实际 Android 字体测量，也不计作 JVM 或 App 测试。

下一代码批次保留原五参数布局 ABI，由 Canvas 和仪器共用同一反馈结果。新 JVM 核真实裁框、文字边界、精灵／命中不变、标签／血条分离、紧凑多敌原几何及无效数值拒绝。沿原两项三字体测试，将实际 sprite／label／gauge、目标身份、显示文字宽高加入已有 `mobile-phone-F.json`／`mobile-party-phone-F.json` 的 raw 哈希绑定；旧报告缺字段、错目标、NaN／bool、越界／重叠必须拒绝，不另建验收通道。

新 App 须继续正常起始交互、物品、自然战斗攻击、合法药草、奖励、完整保存与外部冷启；四人／Boss／已实现原治疗单列受控。原 36 门禁、旧档覆盖、RNG 与完整保存不变继续实测；实际三字体图片和原录像采样须逐版检查。真机／声音／完整原版结局、完整美术与全部累计欠账仍 OPEN。通过新审查及原同源签名 App 后才原批准发布、公网完整字节与 postflight，再接续。
