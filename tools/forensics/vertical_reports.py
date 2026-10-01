"""Human reports generated from the same V1 evidence used by validation/viewer."""
from .common import ROOT, load, save

def rom_section(result):
    v=result['verticalSlice']
    return f'''

## Original Vertical Slice V1

指纹 `{result['romSha256']}`。Mapper246 / iNES；PRG512KiB、CHR512KiB；battery=true，水平镜像，无trainer。
头未声明RAM容量；FCEUX mapper246实现6800–6FFF共2KiB，不能当作物理卡带测量。
完整文件SHA-1/MD5与[TASVideos版本4600](https://tasvideos.org/Games/4112/Versions/View/4600)一致，身份置信度HIGH。

|结构|ROM位置/解释|验证范围|
|---|---|---|
|字体|CHR file80010:4096（banks0/1）；A7010:4096（78/79）|54既有字形VERIFIED，115新增字形HIGH；contextual code，非Big5/GB2312|
|字体选择|模块2 B329–B47D；初始map114，message<10用0/1，>=10用78/79|两次NPC对话运行时bank写入相符；非全局规则|
|文字|模块3 80D3指针；group124、message0..11|12段独立解码，2段运行时核验；C2换行、C3终止，44填充|
|初始地图|map114头file6006，32×30；模块7字典8100/属性8500/碰撞8600|固定900个PPU索引，381次移动采样中的初始区域|
|第二地图|map16头CPU DEAC，256×181；模块4，y>=150切模块15|固定900个PPU索引跨y150边界；末行180只属地址计算结构候选|
|世界分区|模块0 B50B；EC59/EC79两组扩展指针|不能按普通chunk表一直读到底|
|NPC|模块8 D345=file45355，10×14+FF；RAM0402+22×slot|10条源记录与RAM逐字节一致，坐标/方向/图形/对话索引；事件效果部分|
|NPC坐标|模块0 9FC3、A6D6、9CB3|metatile=floor((raw−120)/16)；map anchor=raw−112；viewport=raw−(camera+7)×16|
|碰撞|模块0 CA04、D132、CD59、CF44|foot分支；town0可走、1阻挡、2门遮挡；门不等于transition|
|出口|模块8 E4A1=file464B1，08 1D 10 CB 8E；808C–80DE|114→16，目的tile203,142，camera196,135；反向路径未回放|
|敌人|模块1 EA58，177指针；EBBA起16字节记录|ID1/2/3的HP/攻击/防御/EXP/金钱；其余字节不提升|
|成长|8KiB PRG bank45映射C000；D200阈值指针、D296成长指针|4角色各80条；D208检查、D29E加属性；哪吒前两次升级|
|初始升级7|模块0 B814立即数写6930缓存|不是实际阈值；首阈值12，战后D268–D295按nextThreshold−XP重算|

机器可追溯链：ROM指纹→offset/length/hash→raw JSON→source claim→runtime capture→validator。
`original.json`、`rom-offsets.json`、`gameplay-v1.json`、`difference-ledger.json`保留来源。
CPU地址必须连同模块/8KiB bank解释，不能把混合银行成长例程当连续32KiB模块。
结构地图{v['mapsStructurallyExtracted']}，具有已核固定视口{v['mapsWithVerifiedViewport']}，整图所有gameplay语义通过0。
`READY_FOR_PHASE_2 = NO`。详细结论见[Original Baseline](original-baseline.md)。
'''

def write_reports(result,comparison):
    v=result['verticalSlice'];raw=ROOT/'game-data/raw/rom'
    growth=load(raw/'progression-verified.json');classification=load(raw/'enemy-classification.json')
    ledger=load(ROOT/'game-data/provenance/difference-ledger.json')
    tests=load(ROOT/'reports/phase1-tests.json') if (ROOT/'reports/phase1-tests.json').exists() else {}
    stats='''|项目|结果|验证范围|
|---|---:|---|
'''+f'''|Maps structurally extracted|{v['mapsStructurallyExtracted']}|原10张+第二地图16|
|Maps gameplay checked|{v['mapsWithVerifiedViewport']}|各900个固定PPU索引；整图全部语义验证数0|
|Dialogues decoded / runtime verified|{v['dialoguesDecoded']} / {v['dialoguesRuntimeVerified']}|开局map114的12文本流；父亲/母亲2段实测|
|NPC source records / exact RAM copies|{v['npcSourceRecords']} / {v['npcExactRuntimeCopies']}|源字段与坐标已核；事件/移动未全覆盖|
|Enemies extracted / five fields verified|{v['enemiesExtracted']} / {v['enemiesFiveFieldsVerified']}|包含dummy候选；不是177种已验证敌人|
|Progression extracted / upgrade rows verified|{v['progressionExtracted']} / {v['progressionUpgradeRowsVerified']}|4×80；哪吒1→2、2→3；初始属性另计1|
|Collision checks|{v['collisionChecks']}|正常步行分支；数据与运行时均通过={v['collisionPassed']}|
|50-map scale test|NOT_RUN|用户要求的“两图完整语义验证”前置条件未满足|
'''
    refstats='|域|完整Reference verified|LIKELY_MATCH|MODIFIED|UNKNOWN|\n|---|---:|---:|---:|---:|\n'
    for domain in ('maps','dialogues','enemies','progression'):
        s=comparison['summary'][domain];refstats+=f"|{domain}|{s['originalVerified']}/{s['referenceCount']}|{s['statuses']['LIKELY_MATCH']}|{s['statuses']['MODIFIED']}|{s['statuses']['UNKNOWN']}|\n"
    confidence=f"研究对象置信度：**{v['confidenceCounts']}**。计数口径：{v['confidenceCountUnit']}。\n\n"
    confidence+=f"Difference Ledger状态：{dict(__import__('collections').Counter(r['status'] for r in ledger['records']))}。其中MODIFIED包括文字格式和地图维度，不能全部解释成剧情改编。\n"
    blockers=[
        {'domain':'开局NPC/Dialogue序列','blocker':'12段已解码，仅2段实测；赠物、事件标志、重复对话和完整输入等待序列尚未验收。',
         'evidence':'font0/1与78/79切换实测；10条NPC源字节逐项吻合；母亲文字含一百两。',
         'hypothesis':'事件selector/mask决定首段与重复段，动作由独立脚本执行。',
         'next':'按NPC逐条正常输入回放首谈/复谈；记录金钱、背包和事件flag变化；补齐开局旁白上下文。'},
        {'domain':'NPC移动/第二地图事件','blocker':'初始movement program解释不全；world动态actor列表不等于普通固定NPC表。',
         'evidence':'字节3选择移动程序，bytes8..9是模块0动画指针；world运行时有动态对象。',
         'hypothesis':'世界NPC/地点按区域加载，不能将未提取解释为空。',
         'next':'追踪world对象加载A93B/D32B及当前区域指针；先验证一个地点/事件与其碰撞。'},
        {'domain':'第二地图/往返路径','blocker':'出门到达已验证；返回入口和未访问事件/特殊碰撞条件未完成。',
         'evidence':'114→16；camera196,135，player203,142；900个PPU索引跨模块边界通过，381次collision采样通过。',
         'hypothesis':'边界出口与地图事件入口分属不同分派路径，门class2只是遮挡。',
         'next':'从野外正常走回初始地图，记录入口分派与目标spawn；加入反向golden。'}]
    save(ROOT/'reports/rom-blockers.json',{'status':'PHASE_1_INCOMPLETE','minimalGateBlockers':True,'records':blockers,
        'laterResearch':['其他角色/高等级成长与饱和进位','critical/RNG分布、魔法、道具、Boss分支','其他地区全局字体与文本','完整177敌人名称/技能/flag映射']})
    upgrades='|升级|累计EXP|累计阈值|maxHP|力量|体能|敏捷|精神|下一升级剩余|\n|---|---:|---:|---|---|---|---|---|---:|\n'
    for u in growth['upgrades']:
        if not u['passed']:continue
        a,b=u['beforeGrowth'],u['afterGrowth']
        upgrades+=f"|{u['level']-1}→{u['level']}|{b['xp']}|{u['threshold']}|{a['maxHp']}→{b['maxHp']}|{a['strength']}→{b['strength']}|{a['stamina']}→{b['stamina']}|{a['agility']}→{b['agility']}|{a['spirit']}→{b['spirit']}|{b['remainingExp']}|\n"
    baseline=f'''# Original Baseline — Vertical Slice V1

**PARTIAL_V1 / PHASE_1_INCOMPLETE。READY_FOR_PHASE_2 = NO。**

本轮完成升级阈值解疑、前两次升级回放、开局字体切换、NPC坐标、正常步行碰撞、第二张地图图块及出门到达链路。
没有开始Android、Server、游戏UI或发布正式canonical。证据Viewer仅用于研究。

## 当前覆盖

{stats}

## EXP 与前两次升级

EXP是RAM0508–050A的uint24累计值，初始为0；级别0504从0开始显示为1级。
初始界面“升级7”是模块0 B814单独写入6930缓存的常量，不是经验表阈值，也不是初始已有5EXP。
第一次战后EXP5、剩余7恰好等于12−5；累计8仍1级；累计13触发2级；累计27触发3级。
所以未把表中的12改成7，也没有复制Reference公式解释ROM。

{upgrades}

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
与Reference174条按五字段显式签名调查：{classification['counts']}。ID顺序不是同一套；boss在ROM后段，Reference按剧情重排；Reference存在3组相同数值签名。
这些不是自动同一身份认证，也不能凭64条未配对记录推断ROM缺失。名称/技能/Boss身份另行验证。

首战实际获胜：HP20→8，EXP+5，money+3；普通player分支strength+weapon−enemy defense，再经过倍率与条件分支；
enemy分支attack−armor−stamina，至少1，再经过guard/status。只核对已走到分支；critical选择和RNG分布仍UNKNOWN。
胜利奖励→累计EXP→bank45 level check→增长的链路由本轮连续战斗和前两次升级补齐。

## 与 Reference 的关系

{refstats}

完整Reference记录等价仍全部0；这是严格口径，不否定上面的ROM局部验证。
Reference定位为INDEX/HINT/COMPARISON/MIGRATION SOURCE，整体原版内容置信度MEDIUM，不能批量当Original Truth。
{confidence}

## 最小完成阻塞

'''+ '\n'.join(f"- **{b['domain']}**：{b['blocker']} 下一步：{b['next']}" for b in blockers)+'''

上述最小阻塞未清除，Phase1不结束。全局Boss/魔法/全表调查是后续研究项，不拿来扩大本轮最小验收范围。
官方canonical仍BLOCKED_UNVERIFIED、runtimeReady=false。canonical-candidate仅保存已核前两次升级研究记录，runtimeConsumable=false，不能被Android/Server消费。
OnePlus13T未来UI要求已完整记录在[android-ui-design.md](android-ui-design.md)，本轮没有实现UI。
'''
    (ROOT/'docs/original-baseline.md').write_text(baseline,encoding='utf8')
    refappend=f'''

## Vertical Slice V1 对照

{refstats}

{confidence}

原版“做”与Reference“惹”为明确措辞差异；母亲文本差异主要为引号和繁简，不能当作改剧情证据。
地图32×30与Reference35×35是已记录的结构差异，坐标padding对应关系仍需保留独立语义。
新增12段文本的显式关联见raw/rom/dialogue-opening-comparison.json；未实测的HIGH段保持UNKNOWN，不模糊匹配后提升VERIFIED。
“送給你”vs“送个你”等新增候选差异保留待核。哪吒50级HP累计候选4114 vs4144仍UNKNOWN，不冒充运行时差异。
EXP7/12已解疑：7是开局显示缓存，12是累计阈值；详见Original Baseline，两者都保留。
177 vs174不是简单多3条：包括dummy候选、boss重排、重复数值记录和变更字段；110条数值签名候选、64条未配对，不等于110个已验证身份。
Difference Ledger位于game-data/provenance/difference-ledger.json，每项含ID/category/ROM/reference/evidence/decision/status。
Reference整体原版内容置信度**MEDIUM**；原有现代内容标记行独立于本轮ROM差异统计。ROM优先，不做迎合Reference的修改。
'''
    with (ROOT/'docs/reference-vs-original.md').open('a',encoding='utf8') as f:f.write(refappend)
    testline=f"{tests.get('status','NOT_RECORDED')}：{tests.get('testsRun','?')}项，failures={tests.get('failures','?')}，errors={tests.get('errors','?')}，skipped={tests.get('skipped','?')}。"
    report=f'''# Phase 1 — Original Vertical Slice V1

**READY_FOR_PHASE_2 = NO**。本輪为有界推进，Phase1仍未结束；没有Android、Server、游戏UI或正式canonical发布。

{stats}

{refstats}

{confidence}

Tests：{testline}保留原56项；新增font bank、dialogue、NPC、collision、transition、EXP/升级、reward、enemy、世界分区回归与provenance测试。
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
'''+ '\n'.join('|'+ '|'.join(b[k] for k in ('domain','blocker','evidence','hypothesis','next'))+'|' for b in blockers)+f'''

OnePlus 13T UI requirement recorded：**YES**。完整要求见[android-ui-design.md](android-ui-design.md)，architecture.md已链接。
下一轮先补开局NPC首谈/复谈、赠物与flag，再验证第二图入口/返回链路；其后重评50图结构扩展前置条件。
当前建议继续Phase1，完成后停止，不自动开始Phase2。
'''
    (ROOT/'docs/phase1-report.md').write_text(report,encoding='utf8')
    result['semanticConfidenceCounts']=v['confidenceCounts'];result['semanticConfidenceCountUnit']=v['confidenceCountUnit']
    result['blockers']='reports/rom-blockers.json';save(ROOT/'reports/rom-research.json',result)
