"""Rebuild the human-readable Phase 1 evidence reports from recorded observations."""
from .common import ROOT,load,save

def write_reports(result,comparison):
    if 'verticalSlice' in result:
        from .vertical_reports import write_reports as write_v1
        return write_v1(result,comparison)
    raw=ROOT/'game-data/raw/rom';growth=load(raw/'progression-reference-candidates.json')
    candidate_differences=[{'referenceId':x['referenceId'],'level':x['level'],'differences':x['differences']} for x in growth['records'] if x['differences']]
    blocks=[
      {'domain':'ROM identity','blocker':'No own-cartridge dump or publisher-authenticated checksum.',
       'evidence':'Headerless SHA1/MD5 exactly match TASVideos 8824S / 5785M; title shows C&E 1995.',
       'hypothesis':'Same ROM payload as the published FengShenBang TAS, not proof of every release or a cartridge audit.',
       'next':'Compare an independently dumped cartridge if available; never identify by filename.'},
      {'domain':'Charset / dialogue','blocker':'Only one event font context mapped; shared message pointers do not imply shared fonts.',
       'evidence':'54 glyphs in CHR banks 78/79; group124/message10 decoded from file1B994, 71 bytes; 25 other bounded byte streams retained.',
       'hypothesis':'Event scripts choose CHR font independently of text pointer group.',
       'next':'Hook writes to font bank variables/mapper registers around a second NPC; isolate its glyphs before decoding.'},
      {'domain':'Map / NPC','blocker':'Collision bits, NPC coordinate bias, entity/script identity and complete transition semantics remain partial.',
       'evidence':'Map114 32x30, 900/900 PPU indices equal; 10 maps extracted; 10 fixed14-byte NPC records; exit114->16 observed.',
       'hypothesis':'NPC coordinates include a seven-metatile padding; verify through position-update routine, do not subtract it by visual guess.',
       'next':'Trace one NPC record into OAM and one blocked movement; verify the second map viewport before batch promotion.'},
      {'domain':'Enemies / bosses','blocker':'Only enemy2/3 five numeric fields are runtime checked; flags, names encoding, skills and bosses unresolved.',
       'evidence':'177 sequential pointers/16-byte records, includes dummy0; loader8EA2 and battle damage/reward routines identified.',
       'hypothesis':'Remaining IDs are enemy/stat variants; reference174 IDs may not be a one-to-one enumeration.',
       'next':'Capture a third enemy ID, its graphics/name and skill branch; do not pair records solely by numeric ID.'},
      {'domain':'Progression','blocker':'No observed level-up; new-game remaining-EXP display7 versus table threshold12 unresolved.',
       'evidence':'Bank45 at C000 via module9 AED3/AEF5; four80-row growth and uint24 EXP tables; seven-byte additive stat routine.',
       'hypothesis':'Displayed remaining EXP is initialized separately from cumulative threshold; table row0 also differs from initial spirit4.',
       'next':'Replay accumulated EXP across7 and12 while tracing D208/D29E; inspect carry/saturation and actor indexing before canonical promotion.'},
      {'domain':'Items / weapons / armor / skills / shops','blocker':'Full data layouts, names and effect/price meanings not established in this bounded opening investigation.',
       'evidence':'Initial equipment writes at B825-B852; inventory reads module2 A845-A87E; weapon contribution0548, armor0550 used by battle.',
       'hypothesis':'Inventory IDs and combat contributions are separate tables; no reference field copied into a ROM result.',
       'next':'Trace one equipment swap and one shop transaction to the ROM reads; track item/skill menu dispatch independently of dialogue.'},
      {'domain':'Gameplay formulas','blocker':'One ordinary battle covers no critical, spell, boss, item-effect or encounter-rate distribution.',
       'evidence':'Enemy2/3 defeated, HP20->8, EXP+5, money+3; module9 normal attack and reward branches traced.',
       'hypothesis':'Normal branch examples explain observed8/7 and3 damage, but state flags/multiplier alter other branches.',
       'next':'Controlled guard/weapon comparisons, then critical/skill traces; keep formulas branch-specific.'}
    ]
    save(ROOT/'reports/rom-blockers.json',{'status':'PHASE_1_INCOMPLETE','records':blocks})
    semantic_counts={'VERIFIED':result['charsetGlyphsVerified']+1+result['mapsWithObservedViewport']+result['enemyRecordsWithRuntimeCheck'],
      'HIGH':(10-result['mapsWithObservedViewport'])+10+(177-result['enemyRecordsWithRuntimeCheck'])+320+1,'MEDIUM':0,'LOW':0,'UNKNOWN':25}
    result['semanticConfidenceCounts']=semantic_counts
    result['semanticConfidenceCountUnit']='54 charset glyphs + 26 dialogue streams + 10 maps + 10 NPC records + 177 enemy records + 320 growth rows + 1 initial-character record; overlapping domains, not canonical entities'
    result['blockers']='reports/rom-blockers.json';save(ROOT/'reports/rom-research.json',result)
    counts='\n'.join(f'|{d}|{comparison["summary"][d]["originalVerified"]}/{comparison["summary"][d]["referenceCount"]}|{comparison["summary"][d]["statuses"]["LIKELY_MATCH"]}|{comparison["summary"][d]["statuses"]["MODIFIED"]}|{comparison["summary"][d]["statuses"]["UNKNOWN"]}|' for d in ('maps','dialogues','enemies','progression'))
    statistics='''统计口径：下面的 verified 是 **完整 Reference 研究记录与原版等价**；部分字段相同不能提升整行。
已独立提取/核查的 ROM 内容单列，不把 MODIFIED 当作 MATCH。

|域|完整 Reference verified|LIKELY_MATCH（部分字段）|MODIFIED|UNKNOWN|
|---|---:|---:|---:|---:|
'''+counts+f'''

ROM 成果：54 字形、1 段完整原文、1 张地图几何及局部运行时图块、2 个敌人各5个数值字段已核查；
另外9张地图、175条敌人记录、320条成长记录保留为候选。10条NPC源记录已提取，但身份/坐标语义不完整。
地图维度比较另有1项 MODIFIED（32×30 vs 35×35，开局对应关系 HIGH），未伪造 Reference TMX 哈希。

语义对象 confidence 数量：{semantic_counts}。计数单位见 `reports/rom-research.json`，不等于已验证的 Reference 行数。
原始 evidence records confidence 数量：{result['confidenceCounts']}。VERIFIED 字节范围不自动证明整实体语义。
'''
    baseline=f'''# Original Baseline

状态：**PARTIAL_BASELINE — PHASE_1_INCOMPLETE**。`READY_FOR_PHASE_2 = NO`。

研究对象 SHA-256：`{result['romSha256']}`。所有结论仅适用于此指纹。
去头 SHA-1 `d132723fec0275137cd1a3c65b0cc41c40d78f86`、MD5 `97a660fb70152637c21cb220d15d9a4e`
与 [TASVideos 8824S](https://tasvideos.org/8824S) / [5785M](https://tasvideos.org/5785M) 相符。
这提高游戏载荷身份置信度至 HIGH；没有把公开合集文件名或哈希一致当成自行验卡证据。

## 开局 Vertical Slice

1. FCEUX 2.6.6、NTSC、正常输入：Start@180/360，从新游戏开始。未使用作弊或写入模拟器 RAM。
2. 开局地图内部 ID `0x72`（114），32×30 个16×16 metatile。第450帧 PPU 的固定30×30 tile区域：900/900字节相等。
3. 哪吒初始界面：1级、EXP0、升级7、HP20/20、MP0/0、力量8、敏捷2、体能4、精神4、金钱0。
   ROM初始化例程：模块0 CPU B7C2-B854，文件 `0x37D2` 起；真实级别索引与完整成长另行追踪。
4. NPC列表：文件 `0x45355`，10×14字节+FF；上移接近中央NPC，打开对话并读取首段原文。
   李靖身份与Reference story7关联 HIGH；原始文本本身已核验，不能提升所有NPC字段。
5. 原文含“都是你**做**的好事”，Reference story7为“都是你**惹**的好事”。引号、繁简差异也保留。
6. 正常向下移动，状态探针第840帧地图114、第843帧地图16；出口行 `0x464B1` 为 `08 1D 10 CB 8E`。
   开局地图名称“哪吒家/陈塘关”是场景识别与Reference辅助命名，ROM数值ID不会改成Reference ID1。
7. 野外遇敌：ROM敌人2 臭甲蟲、3 百角海膽；HP10/11、攻击7/9、防御2/3、EXP2/3、金钱1/2。
8. 首场战斗获胜，哪吒HP20→8，获得EXP5、金钱3。完整输入在 `probe-battle.lua`。
   玩家普通攻击分支示例：8+2−2=8，8+2−3=7；敌方普通攻击示例：9−2−4=3。
   这些是已观察分支，不是通用伤害公式；暴击、技能、守御、异常、多人分配仍有未验证分支。

证据图：ROM指纹 → 绝对offset/routine → 原始范围SHA-256 → raw JSON → 语义claim → Reference字段比较。
`game-data/provenance/original.json`、`rom-offsets.json`、`gameplay-captures.json`可逐层回查。
PNG在请求帧的下一帧保存，RAM/PPU在标注帧采样；没有假称完全同帧。失败的存档重放试验不计入证据。

## 验证覆盖

{statistics}

## 不能关闭 Phase 1 的原因

Collision/NPC坐标与脚本尚不完整；第二张地图尚未做运行时匹配；成长存在7/12阈值疑点；
全局字库、物品/技能/商店效果、Boss与完整战斗分支尚未建立可用的 canonical 语义。
已有可回放开局样本，但仍是部分基线。canonical保持 `BLOCKED_UNVERIFIED`，`runtimeReady=false`。
'''
    (ROOT/'docs/original-baseline.md').write_text(baseline,encoding='utf-8')
    comparison_append=f'''

## ROM 实际对照（自动生成）

{statistics}

story7：ROM完整原文见 `game-data/raw/rom/dialogues.json`，未进行模糊匹配或繁简替换后标记 VERIFIED。
两个enemy五字段相同、初始progression六字段相同，全部仅为 LIKELY_MATCH；flags/skills/reward字段等未覆盖。EXP因显示/累计阈值语义待核，未纳入正式字段比较。
另有哪吒80级属性累计候选比较：{growth['counts']}，仅为静态路径假设，存放在
`game-data/raw/rom/progression-reference-candidates.json`，不加入正式VERIFIED统计。
EXP显示7、表值12保留为未解决差异；不得为了配合Reference将12改成7。
属性累计中的待核差异：{candidate_differences}。这是静态推导候选差异，尚未升级为已证实 MODIFIED。

Reference整体原版内容置信度：**MEDIUM（局部有直接支持，不能推及全库）**。
改编发现：已证实对话措辞差异；地图尺寸结构差异；原有22个现代内容标记行仍保留，且与ROM比对差异分开统计。
Reference是研究提示和现代改编资料，不是Original Truth。
'''
    with (ROOT/'docs/reference-vs-original.md').open('a',encoding='utf-8') as f:f.write(comparison_append)
    lines=['# Phase 1 Report','','状态：**PHASE_1_INCOMPLETE / PARTIAL_BASELINE**。`READY_FOR_PHASE_2 = NO`。','',
      'ROM 阻塞已解除；本次完成一轮有界逆向与开局回放。没有开始 Phase 2、Android 或 Server。','',statistics,
      '## 工具与重放','',
      '```powershell','./phase1.ps1 rom analyze','./phase1.ps1 rom research reference/rom/candidate-f3596ffda5c1b838.nes',
      './phase1.ps1 compare','./phase1.ps1 validate','./phase1.ps1 test','```','',
      '`research`使用现有NES解析、CHR导出、比较器及provenance架构；仅对严格匹配的SHA-256启用游戏profile。',
      '输出：`game-data/raw/rom/` 中64张CHR图、charset、dialogues、10张地图及Viewer、NPC、敌人、成长、battle、bounded routines.asm。',
      '模拟器用官方FCEUX 2.6.6本地可执行文件；控制器脚本见 `tools/rom-extractor/`。采样目录全部在忽略的private-derived下。',
      '本地ROM golden缺文件时跳过，合成fixture测试仍运行；测试没有网络下载逻辑。原有36项测试保留。',
      '验证器的204个既有WARNING及4个UNKNOWN不是ROM已验证；实际最新结果见reports/data-validation.json。',
      'canonical不自动接纳Reference数据，且单独GAMEPLAY证据也不能绕过ROM范围证据。','',
      '## 阻塞与下一调查','', '|领域|BLOCKER|CURRENT EVIDENCE|HYPOTHESIS|NEXT INVESTIGATION|','|---|---|---|---|---|']
    lines += ['|'+ '|'.join(b[k] for k in ('domain','blocker','evidence','hypothesis','next'))+'|' for b in blocks]
    lines += ['','建议：继续Phase1，先完成一次升级阈值实测、NPC坐标/碰撞验证和第二张地图运行时检查。',
      'ROM、导出图块与二进制均被Git忽略；本次未提交或分发ROM。`READY_FOR_PHASE_2 = NO`。']
    tests=ROOT/'reports/phase1-tests.json'
    if tests.exists():
        t=load(tests);lines += ['',f"最近测试记录：{t['status']}，{t['testsRun']}项（保留原36项），failures={t['failures']}，errors={t['errors']}。见reports/phase1-tests.json。"]
    (ROOT/'docs/phase1-report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')

def rom_section(result):
    if 'verticalSlice' in result:
        from .vertical_reports import rom_section as section_v1
        return section_v1(result)
    return f'''

## Fingerprint-bound original research

状态：PARTIAL_BASELINE。指纹 `{result['romSha256']}`。
Mapper246，PRG512KiB、CHR512KiB、battery标记true。头未声明RAM容量；FCEUX mapper246实现使用6800-6FFF的2KiB WRAM。
镜像水平，trainer无；物理卡带RAM未测量。格式结构正确不等于独立发行版本认证。
完整文件SHA-1/MD5也与 [TASVideos game version4600](https://tasvideos.org/Games/4112/Versions/View/4600) 一致，身份交叉证据记录在rom-acquisition.json。

字库：CHR文件offset `0xA7010`、4096字节；54个已读字形。16×16字符由4个8×8 tile组成，
tile索引 `10 + 4*(code & 0x3F)`，code bit7选位平面。此映射只适用于观测到的事件字体，绝非全局Big5/GB2312。
控制符C2换行、C3终止；40/41引号、42/43标点、C0/C1感叹/问号。其他特殊字符/变量保持UNKNOWN。
文字两级指针：模块3 CPU80D3-8100；首段group124/message10，文件1B994长度71。

地图：模块0表DCFC（头）、DB9E（块指针）、E22B（模块）、A296（CHR）；普通块16×15。
开局map114头 `0x6006`，宽32高30；块表EA93；metatile8100/属性8500位于模块7。
10图已重建，仅开局900个PPU tile索引进行运行时核对。全图碰撞、出入口、NPC等不自动VERIFIED。

敌人：模块1 EA58开始的177个连续指针，EBBA开始16字节记录；包含dummy，不等于177种已验证敌人。
两个开局敌人的五个数值字段对照运行时通过。成长：PRG8KiB bank45映射C000-DFFF，D200/D296指针，4×80条。
普通32KiB模块地址换算不能应用于这个混合银行。成长显示7/累计阈值12疑点保留。

所有offset、长度、范围哈希、含义与sourceRefs见 `game-data/provenance/rom-offsets.json` 和 `original.json`。
全部运行图/原始数据留在忽略目录；详细结论见 [Original Baseline](original-baseline.md) 与 [Phase 1 Report](phase1-report.md)。
'''
