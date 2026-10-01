# Original Baseline — Vertical Slice V1

**PARTIAL_V1 / PHASE_1_INCOMPLETE。READY_FOR_PHASE_2 = NO。**

本轮完成升级阈值解疑、前两次升级回放、开局字体切换、NPC坐标、正常步行碰撞、第二张地图图块及出门到达链路。
没有开始Android、Server、游戏UI或发布正式canonical。证据Viewer仅用于研究。

## 当前覆盖

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


## EXP 与前两次升级

EXP是RAM0508–050A的uint24累计值，初始为0；级别0504从0开始显示为1级。
初始界面“升级7”是模块0 B814单独写入6930缓存的常量，不是经验表阈值，也不是初始已有5EXP。
第一次战后EXP5、剩余7恰好等于12−5；累计8仍1级；累计13触发2级；累计27触发3级。
所以未把表中的12改成7，也没有复制Reference公式解释ROM。

|升级|累计EXP|累计阈值|maxHP|力量|体能|敏捷|精神|下一升级剩余|
|---|---:|---:|---|---|---|---|---|---:|
|1→2|13|12|20→23|8→10|4→5|2→3|4→4|14|
|2→3|27|27|23→26|10→12|5→6|3→4|4→5|53|


升级时当前HP也各增加3（两次采样1→4）；累计EXP不归零。
四名角色拥有各自80条uint24累计阈值及7字节属性增量表。初始常量与row0不同，尤其精神初始4，不能被row0的1覆盖。
本轮仅哪吒前两行增量通过运行时验证；其他角色、特殊高等级、敏捷溢出影响精神carry仍需单独调查。
增长采样点D29E在level已增加而属性尚未增加之后；结合按顺序的level-check日志验证升级前后，未误读同帧覆盖的截图。
level-probe使用正常控制器输入，死亡后5次内存savestate恢复并核对HP/EXP；无作弊、无写入模拟器RAM。branch号保留，不能将重放分支当独立随机样本。

## 地图、NPC、Collision、Transition

开局内部map114为32×30，初始player tile(8,21)；场景名“哪吒家”是辅助命名，未把内部ID改为Reference map1。
NPC10×14字节位于file45355，运行时复制到0402+22×slot（玩家slot0）。十条源字节均吻合。
父亲配置tile(8,15)、sprite40、dialogue10/11；母亲tile(7,15)、sprite41、dialogue1/0。
rawPixel→metatile为floor((raw−120)/16)，mapPixelAnchor=raw−112，viewportAnchor=raw−(cameraTile+7)×16。
byte0低7位为sprite ID；byte10低2位为方向；byte3为movement program；bytes8..9是模块0动画数据指针，已纠正V0 eventRoutineCandidate命名。
byte12/13为事件selector/mask，首谈/复谈分派可追踪；具体动作与完整移动轨迹没有整体提升VERIFIED。

town tileset4正常步行class0可走、1阻挡、2可走且设置角色背景遮挡。**class2不是地图传送。**
world tileset1正常foot分支中1/3/4/5/7阻挡，其余0..26在此分支放行；boat/flying/event处理另有状态条件。
实际观测类别：初始0/1/2，world0/2/4/5；未走到的类别只有静态分支证据，不称全图gameplay VERIFIED。
出门通过地图边界处理，5字节行08 1D 10 CB 8E指向map16、player(203,142)、camera(196,135)。
frame840仍114，843地图ID变16，888对象初始化后spawn相符。起始地图return entry候选(8,29)，与新游戏(8,21)不同。

world map16为256×181；普通模块4行0..149，特殊模块15行150..180。末行180按原例程继续寻址，保留为结构候选。
frame960固定metatile x197..211,y145..159的900个PPU索引全部相符，覆盖两个PRG模块。
Viewer可开关NPC与Collision，点击显示索引、sprite、direction、dialogue和event字段；第二图未知动态NPC明确标UNKNOWN。
50地图扩展未运行：两张地图完整事件/NPC/往返语义尚未达到用户规定前置条件。

## Dialogue、Enemy、Battle

初始地图message0..9使用font0/1，10..11使用78/79，group=map+10=124；父母两段实际bank写入吻合。
12段文本独立解码，父亲和母亲两段实测。保留“都是你做的好事”、原字形“体/體”及原始引号，不按Reference替换。
C2换行、C3终止，44作为填充；等待输入、变量/角色名及开局完整旁白事件链仍不能声称全部验证。

敌人177条全部保留，包括dummy-like0；ID1/2/3五个数值字段已与运行时核对，剩余6字节语义未完成。
与Reference174条按五字段显式签名调查：{'NUMERIC_SIGNATURE_CANDIDATE': 110, 'UNPAIRED': 64}。ID顺序不是同一套；boss在ROM后段，Reference按剧情重排；Reference存在3组相同数值签名。
这些不是自动同一身份认证，也不能凭64条未配对记录推断ROM缺失。名称/技能/Boss身份另行验证。

首战实际获胜：HP20→8，EXP+5，money+3；普通player分支strength+weapon−enemy defense，再经过倍率与条件分支；
enemy分支attack−armor−stamina，至少1，再经过guard/status。只核对已走到分支；critical选择和RNG分布仍UNKNOWN。
胜利奖励→累计EXP→bank45 level check→增长的链路由本轮连续战斗和前两次升级补齐。

## 与 Reference 的关系

|域|完整Reference verified|LIKELY_MATCH|MODIFIED|UNKNOWN|
|---|---:|---:|---:|---:|
|maps|0/259|0|0|259|
|dialogues|0/639|0|2|637|
|enemies|0/174|3|0|171|
|progression|0/249|1|0|248|


完整Reference记录等价仍全部0；这是严格口径，不否定上面的ROM局部验证。
Reference定位为INDEX/HINT/COMPARISON/MIGRATION SOURCE，整体原版内容置信度MEDIUM，不能批量当Original Truth。
研究对象置信度：**{'HIGH': 626, 'LOW': 0, 'MEDIUM': 0, 'UNKNOWN': 14, 'VERIFIED': 74}**。计数口径：54 font78 glyphs +115 font0 glyphs +26 streams (12 decoded/14 unresolved) +11 maps(viewport scope) +10 NPC source records +177 enemy stat records +320 progression rows +1 initial character; scoped confidence, never full reference equivalence。

Difference Ledger状态：{'MODIFIED': 3, 'UNKNOWN': 2, 'RESOLVED': 2, 'UNPAIRED': 1}。其中MODIFIED包括文字格式和地图维度，不能全部解释成剧情改编。


## 最小完成阻塞

- **开局NPC/Dialogue序列**：12段已解码，仅2段实测；赠物、事件标志、重复对话和完整输入等待序列尚未验收。 下一步：按NPC逐条正常输入回放首谈/复谈；记录金钱、背包和事件flag变化；补齐开局旁白上下文。
- **NPC移动/第二地图事件**：初始movement program解释不全；world动态actor列表不等于普通固定NPC表。 下一步：追踪world对象加载A93B/D32B及当前区域指针；先验证一个地点/事件与其碰撞。
- **第二地图/往返路径**：出门到达已验证；返回入口和未访问事件/特殊碰撞条件未完成。 下一步：从野外正常走回初始地图，记录入口分派与目标spawn；加入反向golden。

上述最小阻塞未清除，Phase1不结束。全局Boss/魔法/全表调查是后续研究项，不拿来扩大本轮最小验收范围。
官方canonical仍BLOCKED_UNVERIFIED、runtimeReady=false。canonical-candidate仅保存已核前两次升级研究记录，runtimeConsumable=false，不能被Android/Server消费。
OnePlus13T未来UI要求已完整记录在[android-ui-design.md](android-ui-design.md)，本轮没有实现UI。
