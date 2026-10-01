# Phase 1 — Original Vertical Slice V1

**READY_FOR_PHASE_2 = NO**。本輪为有界推进，Phase1仍未结束；没有Android、Server、游戏UI或正式canonical发布。

|项目|结果|验证范围|
|---|---:|---|
|Maps structurally extracted|11|原10张+第二地图16|
|Maps gameplay checked|2|各900个固定PPU索引；整图全部语义验证数0|
|Dialogues decoded / runtime verified|12 / 2|开局map114的12文本流；父亲/母亲2段实测|
|NPC source records / exact RAM copies|10 / 10|源字段与坐标已核；事件/移动未全覆盖|
|Enemies extracted / five fields verified|177 / 3|包含dummy候选；不是177种已验证敌人|
|Progression extracted / upgrade rows verified|320 / 2|4×80；哪吒1→2、2→3；初始属性另计1|
|Collision checks|381|正常步行分支；数据与运行时均通过=True|
|50-map scale test|NOT_RUN|用户要求的“两图完整语义验证”前置条件未满足|


|域|完整Reference verified|LIKELY_MATCH|MODIFIED|UNKNOWN|
|---|---:|---:|---:|---:|
|maps|0/259|0|0|259|
|dialogues|0/639|0|2|637|
|enemies|0/174|3|0|171|
|progression|0/249|1|0|248|


研究对象置信度：**{'HIGH': 626, 'LOW': 0, 'MEDIUM': 0, 'UNKNOWN': 14, 'VERIFIED': 74}**。计数口径：54 font78 glyphs +115 font0 glyphs +26 streams (12 decoded/14 unresolved) +11 maps(viewport scope) +10 NPC source records +177 enemy stat records +320 progression rows +1 initial character; scoped confidence, never full reference equivalence。

Difference Ledger状态：{'MODIFIED': 3, 'UNKNOWN': 2, 'RESOLVED': 2, 'UNPAIRED': 1}。其中MODIFIED包括文字格式和地图维度，不能全部解释成剧情改编。


Tests：PASS：73项，failures=0，errors=0，skipped=0。保留原56项；新增font bank、dialogue、NPC、collision、transition、EXP/升级、reward、enemy、世界分区回归与provenance测试。
本地ROM或私有运行时捕获缺失时对应golden明确SKIP；测试不下载ROM。测试摘要见reports/phase1-tests.json。

## 可重建命令

```powershell
./phase1.ps1 rom analyze
./phase1.ps1 rom research reference/rom/candidate-f3596ffda5c1b838.nes
./phase1.ps1 compare
./phase1.ps1 validate
./phase1.ps1 test
```

继续扩展现有rom analyzer/profile，没有重复NES解析架构。所有结论绑定SHA-256，ROM、raw图块、运行截图及candidate载荷都保持Git忽略。
Viewer：game-data/raw/rom/vertical-slice-viewer.html；结构化结果：vertical-slice.json、progression-verified.json、npc-opening.json、collision-opening.json、transitions-v1.json、dialogues-opening.json、enemy-classification.json。
运行时重放：tools/rom-extractor/probe-slice.lua、probe-world.lua、probe-level.lua；精确文件哈希见gameplay-v1.json。
level replay的5次已核checkpoint恢复已披露；没有生成EXP/HP、没有作弊、没有修改ROM或模拟器RAM。

## 最小 gate blockers

|领域|BLOCKER|CURRENT EVIDENCE|HYPOTHESIS|NEXT INVESTIGATION|
|---|---|---|---|---|
|开局NPC/Dialogue序列|12段已解码，仅2段实测；赠物、事件标志、重复对话和完整输入等待序列尚未验收。|font0/1与78/79切换实测；10条NPC源字节逐项吻合；母亲文字含一百两。|事件selector/mask决定首段与重复段，动作由独立脚本执行。|按NPC逐条正常输入回放首谈/复谈；记录金钱、背包和事件flag变化；补齐开局旁白上下文。|
|NPC移动/第二地图事件|初始movement program解释不全；world动态actor列表不等于普通固定NPC表。|字节3选择移动程序，bytes8..9是模块0动画指针；world运行时有动态对象。|世界NPC/地点按区域加载，不能将未提取解释为空。|追踪world对象加载A93B/D32B及当前区域指针；先验证一个地点/事件与其碰撞。|
|第二地图/往返路径|出门到达已验证；返回入口和未访问事件/特殊碰撞条件未完成。|114→16；camera196,135，player203,142；900个PPU索引跨模块边界通过，381次collision采样通过。|边界出口与地图事件入口分属不同分派路径，门class2只是遮挡。|从野外正常走回初始地图，记录入口分派与目标spawn；加入反向golden。|

OnePlus 13T UI requirement recorded：**YES**。完整要求见[android-ui-design.md](android-ui-design.md)，architecture.md已链接。
下一轮先补开局NPC首谈/复谈、赠物与flag，再验证第二图入口/返回链路；其后重评50图结构扩展前置条件。
当前建议继续Phase1，完成后停止，不自动开始Phase2。
