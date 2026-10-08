最新状态：v97已按原同源Actions发布并核验公网全字节/postflight（NO_DATA，不当手机健康），稳定82和所有欠账保持。已用本版真实三字体/四队型六敌72份raw重新计算短条设计，24份拟缩短；新回执`/srv/fengshen-dev/receipts/multi-enemy-gauge-v97-next-design.json`，仅设计、未写下一游戏代码、未新App验证。先接[首战准备时序取证](battle-visual-preparation-next-batch.md)，以下96设计和发布前状态保持历史。

# 当前视觉资源覆盖与下一有限批次

2026-10-08服务器同会话核查；WORLD-FULL-01仍IN_PROGRESS/PARTIAL。当前正式候选97/source `63b600fe025c8ff1ba4c02b71bdddbc28e0ab9f7`／原Actions37730631440的build成功、App仍执行；线上实际96、稳定82。此文仅记录下一步，不改变冻结source，不是新任务或游戏发布。

已通过CPU prepare_context导航，再读真实BattleVisualAssets、ContentLoader、BattleScreen、MainActivity及TouchTest。独立新签名97解包清单与本机manifest、16份来源记录逐项hash一致，均为原获批OWN_BUILTIN_IMAGE_GEN_EXACT_COPY。409份素材字节与96相等；私有master、ROM和存档不进入模型。

| 已批准资源 | 实际覆盖 | 仍缺范围 |
|---|---|---|
| 四人头像/待机 | 哪吒、小龙女、杨戬、姜子牙 | 全角色/动作完整覆盖未达成 |
| 专用姿态 | 哪吒攻击、小龙女施法 | 其余专用攻击/已实现能力姿态，当前合法idle回退继续可玩 |
| 插画敌图 | 已有敌1、137 | 当前c62有83份敌人定义，其余81份用原native；此83不是全世界分母 |
| 四环境 | 草地、海岸、海底、洞窟；显式map16/10/25/97/85/7 | 其余地图整体风格覆盖；当前73份scene JSON不是全世界可玩证明 |
| 正常可玩片段 | 原正常谈话/调查、药草、攻击/奖励/保存/外部cold沿原Actions逐版核 | 完整正常结局、一加13T实际操作/帧耗时/内存、声音和真实云恢复仍OPEN |

16图manifest `8dc53b77027055a2b9a2ec37a80e2aba3113e668bd350ab822c14c3d85794f30`。资源库存回执：`/srv/fengshen-dev/receipts/visual-c62-resource-coverage-20261008.json`。没有manifest批准且准备来源完整的敌2/3新插画可直接接入；先保留原图，不能用未知候选冒称已授权发布。生成或获取补缺时单独保存授权来源、提示词/加工、SHA、裁框、实际目视和App验证，原规则与名称UNKNOWN保持。

## 下一有限候选：紧凑多敌短血条

先完成97的原同源App/原截图录像/完整cold审查和门槛，通过才发布公网/postflight。随后在已获批战斗视觉欠账内接多敌反馈；不改敌sprite、实例slot、48dp命中格、文字字号、状态卡或可见HP模型。

实际读取96原三字体/一至四人native六敌的72份反馈度量，按现有单敌反馈函数计算：72份均无法同时容下其下置/侧置标签与血条。该结果仅为旧App数据上的设计计算，不是97或下一候选App/JVM，不能直接把单敌适配套用到多敌。

较小安全方案：只在原紧凑多敌分支将血条宽度限制到`min(旧宽度,max(24dp,原sprite宽+8dp))`并按sprite中心在原血条范围内对齐；原y/高度、完整编号、原sprite与目标保持。旧72份度量中24份预计缩短，全部拟条在原范围内、sprite脚底不超过原条顶。详见服务器`multi-enemy-feedback-next-design.json`（拒绝直接套用）和`multi-enemy-gauge-next-design.json`（未实施设计）；不是新App通过。

实施前重新使用当前最新原度量。复用BattleEnemyFeedback/BattleSceneLayout及Canvas同一投影，补真正JVM多场景边界、原Android Paint和三字体实际反馈验证；新协议明确区分有限条适配与完整单敌标签，保留原所有门禁/状态/RNG/cold，不能让旧raw通过新source审查。实际目视原三字体六敌/四人/Boss与正常片段后，重新完整审查/真实相关测试/record_review_decision/Stop，同源Actions门槛满足才逐版发布。

完整多敌名称、全敌插画、四人其余专用动作、地图/NPC/物品/商店/对话统一视觉等仍欠账；此有限方案不能关闭整套BATTLE-VISUAL-02或三项长期终点。
