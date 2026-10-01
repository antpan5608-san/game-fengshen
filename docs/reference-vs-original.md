# Reference vs Original

状态：**EVIDENCE_REVIEW**。固定参考提交见 raw manifest。

|数据域|Reference|原版已验证|明显改编标签|UNKNOWN|
|---|---:|---:|---:|---:|
|maps|259|0|1|259|
|dialogues|639|0|4|637|
|enemies|174|0|1|171|
|progression|249|0|0|248|
|items|147|0|3|147|
|equipment|103|0|2|103|
|skills|34|0|0|34|
|npcs|643|0|4|643|
|shops|251|0|6|251|
|chests|175|0|0|175|
|events|512|0|1|512|
|assets|1661|0|0|1661|

items与equipment有交集；shops为230条库存行+21条store行，不等于251家店。

现代内容：**22 个唯一数据库行**。匹配字段/原文/来源详见[JSON报告](../reports/reference-vs-original.json)。

|来源行|命中词|
|---|---|
|reference.battle.151|工作室、黑白牛|
|reference.map.259|工作室、黑白牛|
|reference.map_npc.640|黑白牛|
|reference.map_npc.641|黑白牛|
|reference.map_npc.642|黑白牛|
|reference.map_npc.643|黑白牛|
|reference.map_warp_point.3546|工作室、黑白牛|
|reference.map_warp_point.3547|工作室、黑白牛|
|reference.monster.174|工作室、黑白牛|
|reference.object.132|VIP|
|reference.object.138|秒天秒地秒空气|
|reference.object.145|扫荡券|
|reference.store.12|秒天秒地秒空气|
|reference.store.14|扫荡券|
|reference.store.16|程序员、购买.{0,12}存档|
|reference.store.19|购买.{0,12}金币|
|reference.store.20|购买.{0,12}金币|
|reference.store.21|购买.{0,12}金币|
|reference.story.630|游戏制作者、黑白牛|
|reference.story.631|黑白牛|
|reference.story.633|游戏制作者、黑白牛|
|reference.story.639|qq群、微博、贴吧、黑白牛|

## 分类规则

- MATCH：有已核原版证据，显式比较的完整研究记录相同；仍须人工转换语义后才可进入canonical。
- LIKELY_MATCH：有证据的部分字段相同，不能提升整行真实性。
- MODIFIED：有证据的字段确有差异，逐字段列出。
- REFERENCE_ONLY：仅在原版整个域有完整、已验证的枚举证据时使用。
- ROM_ONLY：显式原版独有候选，且记录完整、有已核证据。
- UNKNOWN：无原版、证据不足、映射不明；不得把缺ROM视作不存在。

现代标记筛查覆盖所有表的字符串：工作室/VIP/购买金币/付费/商城/充值/内购/人民币/支付宝/微信支付/购买存档/解锁存档/游戏制作者/黑白牛/程序员/贴吧/微博/qq群/扫荡券/秒天秒地秒空气；另匹配购买与金币/存档间的短文本。
没有命中词不代表原版；关联地图/NPC/敌人可能同属改编，只列为人工调查线索，不按ID区间整段删除。
所有原始行保留；排除仅发生在canonical准入。没有导入原版比较输入时，259地图、639对话逐ID均为UNKNOWN。

比较输入契约见`game-data/schemas/original-comparison.schema.json`；maps比较对象为`{data:原map行,tmxSha256:参考TMX哈希}`，其他表为完整原行，assets为`{path,sha256}`。
TMX哈希不适合直接与ROM数据比较；只有经证据支持的同义转换才可生成该比较对象。数据库字段也需明确单位/编码，不能把ROM字节硬套参考字段。
`complete`表示整个研究记录已覆盖；`completeDomains`还需`coverageSourceRefs`提供整域枚举证据。原版证据必须绑定ROM哈希/offset/length/meaning；实机记录可补强ROM，不能单独替代ROM证据。


## Vertical Slice V1 对照

|域|完整Reference verified|LIKELY_MATCH|MODIFIED|UNKNOWN|
|---|---:|---:|---:|---:|
|maps|0/259|0|0|259|
|dialogues|0/639|0|2|637|
|enemies|0/174|3|0|171|
|progression|0/249|1|0|248|


研究对象置信度：**{'HIGH': 626, 'LOW': 0, 'MEDIUM': 0, 'UNKNOWN': 14, 'VERIFIED': 74}**。计数口径：54 font78 glyphs +115 font0 glyphs +26 streams (12 decoded/14 unresolved) +11 maps(viewport scope) +10 NPC source records +177 enemy stat records +320 progression rows +1 initial character; scoped confidence, never full reference equivalence。

Difference Ledger状态：{'MODIFIED': 3, 'UNKNOWN': 2, 'RESOLVED': 2, 'UNPAIRED': 1}。其中MODIFIED包括文字格式和地图维度，不能全部解释成剧情改编。


原版“做”与Reference“惹”为明确措辞差异；母亲文本差异主要为引号和繁简，不能当作改剧情证据。
地图32×30与Reference35×35是已记录的结构差异，坐标padding对应关系仍需保留独立语义。
新增12段文本的显式关联见raw/rom/dialogue-opening-comparison.json；未实测的HIGH段保持UNKNOWN，不模糊匹配后提升VERIFIED。
“送給你”vs“送个你”等新增候选差异保留待核。哪吒50级HP累计候选4114 vs4144仍UNKNOWN，不冒充运行时差异。
EXP7/12已解疑：7是开局显示缓存，12是累计阈值；详见Original Baseline，两者都保留。
177 vs174不是简单多3条：包括dummy候选、boss重排、重复数值记录和变更字段；110条数值签名候选、64条未配对，不等于110个已验证身份。
Difference Ledger位于game-data/provenance/difference-ledger.json，每项含ID/category/ROM/reference/evidence/decision/status。
Reference整体原版内容置信度**MEDIUM**；原有现代内容标记行独立于本轮ROM差异统计。ROM优先，不做迎合Reference的修改。

## TOWN-01局部差异（2026-10-01）

目标ROM在module2菜单记录与实际原版商店截图确认：weapon id1是「手刀」（50两，攻击贡献5），id2是「長劍」（120两，贡献10）；参考项目对应「棍棒」「鐵劍」不能当作本ROM名字。Android开发包c10使用ROM确认名字、价目、预览和限定贡献表。原版无自动装备购买样本；买入进背包，装备须解除原装备后另装。跨类别出售及直接替换未全迁移，这些是当前开发限制，不是原版规则。

原版装备有像素预览，而非只有文字；6件当前装备图块已由原导出器从ROM组合/银行/调色板重建。百货店当前药品列表原生画面为名称/价格，无已确认药品预览；不造假现代图标。原版菜单字形/分页/时序与移动端仍非像素等价。殷氏100两现有正常ROM写入确认该金额（frame997、CPU0501，C53A），只加强该局部金额证据，首次/复谈事件完整flag时机仍暂定，不把整事件升级VERIFIED。
