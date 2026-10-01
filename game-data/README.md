# Game Data：来源与准入

- `raw/reference-project/`：只读参考快照的完整提取结果，Git忽略，可重建。未知值原样保留。
- `raw/rom/`：指纹绑定的ROM提取、字库/原文/地图/敌人/成长候选、运行时对照与离线地图Viewer。候选与已核查字段分开。
- `provenance/reference-project.json`：9,025条来源（7,105行+259 TMX+1,661资源）。commit+文件hash+表/行位置或文件位置可回查；字段位于完整data对象且可由column进一步细化。
- `provenance/rom-offsets.json`：确认的字节范围与例程，RESEARCH_IN_PROGRESS；每项绑定ROM SHA-256。
- `provenance/original.json`：ROM范围、部分语义声明及运行画面证据；`gameplay-captures.json`记录原始采样文件哈希。
- `canonical/baseline.json`：当前全部域为空、BLOCKED_UNVERIFIED、runtimeReady=false；已有部分证据不足以填齐实体契约。
- `schemas/`：JSON Schema 2020-12来源、raw、比较输入、ROMoffset、研究canonical契约。

独立来源manifest避免每行重复大段定位信息，每条raw数据、TMX、asset、canonical记录都有sourceRefs。source支持ORIGINAL_ROM、REFERENCE_PROJECT、GAMEPLAY_VERIFIED、GUIDE、VIDEO、MANUAL、INFERRED、UNKNOWN；confidence支持VERIFIED/HIGH/MEDIUM/LOW/UNKNOWN。参考提取的字节精确性由hash表达，原版置信度独立保持LOW/false。

原版验证声明必须有VERIFIED的ROM证据；ROM证据包含romSha256/offset/length/meaning，实机证据可补强ROM，须有文件/URL和内容定位说明；证据还应说明版本及解释过程。工具可以验证来源链的结构和定位字段，不能替代人工核实证据真实性。仅有实机、GUIDE/VIDEO/MANUAL/INFERRED不能单独把一行升级为originalVerified。

`./phase1.ps1 compare --original path.json --sources provenance.json`生成逐ID差异。参考全文存于raw；比较记录按证据覆盖范围给出MATCH等六类；缺原版时所有域都为UNKNOWN。部分字段一致只给LIKELY_MATCH。

`./phase1.ps1 canonical --candidate candidate.json --sources provenance.json`只接收人工语义化、逐实体有已核来源的候选。现代内容词及其来源链在这里排除，排除后有悬挂引用就拒绝整次生成。没有raw到canonical的自动强制转换。测试profile不能发布到canonical。

## 与Phase 0 Schema的关系

Phase 0 `docs/data-schema.md`是未来运行包Draft 1。本阶段落实的是独立`forensic-baseline-v1`**研究验收契约**，用于来源门槛、故障注入和结构审查；没有完成运行包的Condition/Flag/完整Map层/战斗规则/Kotlin模型。

沿用全局命名ID、sourceRefs、左上零基canonical坐标、严格未知字段、递归受限动作原则。这里的`startBattle.enemyId`只是一条用于检查敌人引用的研究断言，不是未来运行时battleId配置，不能送入Android。完整Phase 0的Battle/Encounter/Character等模型必须在语义核实后另行落实，禁止把简化研究契约当成生产包。

canonical未来才是Android/Server最终数据层；目前没有任何可消费游戏包，也没有客户端直接读取raw的实现。原版必需信息缺失时宁可空并阻断，不补默认值让它看似可运行。
