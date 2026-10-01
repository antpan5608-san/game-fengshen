# ROM Analysis

状态：**ROM_FILES_ANALYZED**。扫描`reference/rom/`；文件只读，ROM及导出二进制被Git忽略。

## candidate-f3596ffda5c1b838.nes

```json
{
  "fileName": "candidate-f3596ffda5c1b838.nes",
  "fileSize": 1048592,
  "sha256": "f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25",
  "sha1": "293d184ef8fe83b3dac59f3b9545f93434ba2373",
  "md5": "ad00a277f50749da912faf37491801ef",
  "fingerprint": {
    "id": "rom.sha256.f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25",
    "fileSha256": "f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25",
    "prgSha256": "4d17f534594fba7656eadfc5100fb2f84494c1f904056a48d205045d88b7132c",
    "chrSha256": "7dcb9312c1d62b76fd7662e44fd2d77adc03c37cdd2068f111b07ade5ec897b1",
    "prgChrSha256": "5def3698a51b86c07eb358b6234f359d8e69353b2cf4a341a4678a19a86aff52",
    "identityBasis": "file bytes, never filename; payload hashes exclude header, trainer and trailing bytes"
  },
  "format": "iNES",
  "headerHex": "4e45531a204062f00000000000000000",
  "mapper": 246,
  "mapperConfidence": "HEADER_DECLARED",
  "submapper": null,
  "mirroringDeclared": "HORIZONTAL",
  "mirroringNote": "Runtime mirroring may be mapper-controlled; declaration only.",
  "batteryBackedPersistentMemoryFlag": true,
  "prgRam": {
    "legacyDeclaredBytes": null,
    "nonvolatileBytes": null,
    "confidence": "UNKNOWN",
    "note": "Battery flag is not a measured RAM size; byte8=0 is unspecified."
  },
  "chrRam": {
    "volatileBytes": null,
    "nonvolatileBytes": null,
    "confidence": "UNKNOWN"
  },
  "trainer": false,
  "trainerBytes": 0,
  "prgRomBytes": 524288,
  "chrRomBytes": 524288,
  "chrMode": "CHR_ROM",
  "consoleType": 0,
  "timingMode": null,
  "miscRomCount": null,
  "sections": {
    "header": {
      "offset": 0,
      "length": 16
    },
    "trainer": {
      "offset": 16,
      "length": 0
    },
    "prg": {
      "offset": 16,
      "length": 524288
    },
    "chr": {
      "offset": 524304,
      "length": 524288
    },
    "trailing": {
      "offset": 1048592,
      "length": 0
    }
  },
  "warnings": [],
  "gameIdentity": "UNKNOWN",
  "originalVersionVerified": false,
  "status": "STRUCTURALLY_VALID",
  "relativePath": "candidate-f3596ffda5c1b838.nes"
}
```


## 工具与解释边界

- `./phase1.ps1 rom analyze`：扫描并记录哈希及头；可追加文件路径分析单个ROM。结构合法不等于确为未改版《封神榜》。
- `./phase1.ps1 rom dump reference/rom/game.nes --out private-derived/game`：只读导出PRG/CHR/Trainer及manifest。
- `./phase1.ps1 rom banks reference/rom/game.nes --section prg --size 0x4000`：固定大小文件块、熵和哈希；不是已确认的硬件bank/CPU地址。
- `./phase1.ps1 rom search reference/rom/game.nes "4E 45 ?? 1A" --section file`：带通配符二进制模式搜索；返回绝对/分段offset。匹配不证明语义。
- `./phase1.ps1 rom tiles reference/rom/game.nes --out private-derived/chr.png`：CHR平面2bpp图；灰度仅显示索引，不是游戏调色板。可用`--section prg --offset 0x100 --count 256`检查明确选定的候选字节。

NES 2.0支持扩展mapper/submapper、线性/指数乘数ROM长度、易失/非易失RAM shift。iNES byte8=0不编造RAM容量；battery flag只说明持久化标记，不推断具体存档芯片或容量。
CHR大小为0时记录CHR_RAM_INDICATED并拒绝空CHR图导出；字库可能在PRG/压缩/运行时上传中，不能靠头推断其offset。Mirroring为头声明，运行时可能由mapper控制。
Trainer计入PRG起点；截断输入/非法magic/不支持的头型报错；尾部字节保留为trailing段，不静默丢弃。脏iNES padding降低mapper声明置信度。

offset登记契约：`game-data/provenance/rom-offsets.json`，每项必须有ROM SHA-256、offset、length、meaning、evidence来源ID、confidence。实际研究进展见下方profile报告。

## 调查顺序

先确定文件哈希/版本与结构，再检查CHR或PRG中可见字形；依据实际字符表识别文字编码、指针表与控制码，逐条对照639条文本。保留解码原字节与失败标记，不能修改解码结果凑匹配。
随后用对话、地图出入口与战斗出现位置交叉定位地图/敌人/成长/道具；每个确认的offset单独记录证据。

格式依据：[iNES](https://www.nesdev.org/wiki/INES)、[NES 2.0](https://www.nesdev.org/wiki/NES_2.0)、[PPU pattern tables](https://www.nesdev.org/wiki/PPU_pattern_tables)、[Mirroring](https://www.nesdev.org/wiki/Mirroring)。


## Original Vertical Slice V1

指纹 `f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25`。Mapper246 / iNES；PRG512KiB、CHR512KiB；battery=true，水平镜像，无trainer。
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
结构地图11，具有已核固定视口2，整图所有gameplay语义通过0。
`READY_FOR_PHASE_2 = NO`。详细结论见[Original Baseline](original-baseline.md)。


## TOWN-01 — 地图0三店与装备预览（2026-10-01）

同指纹 f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25，Mapper246。复用 Reader/extract_town_shops 与既有导出器；本次仅范围内取证，不提升全量 canonical。证据链、原始片段 offset/length/hash 和实际手柄记录/截图 hash 在 game-data/provenance/town01.json、game-data/raw/rom/shops-town0.json。

地图0门口 (13,18)/(6,19)/(24,25) 的碰撞类别21/22/23，经 module0:CBE3/CBFB 保存调用地图与位置、类别减4，分别进入17/18/19。原始16×15网格/tileset2分别导出，店员17/18在(7,5)，19在(6,5)，隔柜台站(7,7)/(6,7)交互。module8:DFD8/DFDE 的出口原文 07 0c fe 00 00 / 06 0c fe 00 00，FE恢复室外调用位置；并非任意测试室内。

module2:E685 的商店指针，经 E9C8/EC2F/E6D7 得到武器[0,1,2]、防具[0,1,28]、药品[0,6]。价格表 E8FA/EB7D/E78A 分别15/50/120、30/80/200、15/20。原菜单名字实际为小刀/手刀/長劍、肚兜/布衣/麻鞋、藥草/牛黃丸。原版正常交易截图在 private-derived/town01-weapon-name1、weapon-name2、armor-name1、armor-name2、items-edges；参考项目棍棒/鐵劍与该ROM不一致，未迁就参考改名。

卖价依据 module2:BEB8..BEEF 调 EE8B 的无符号24位除2、零商补1，即 max(1,floor(price/2))。正常藥草卖出银两74→81、数量减少提供运行证据。买入进背包，正常样本装备不自动改变。每类16不同记录、当前商品数量上限10，A0EB插入例程和 A190 分类上限表。跨类别卖出仍是开发限制，不能写成原版限制。

原版装备菜单有真实精灵预览：module2:CE88、F002根、CF24 CHR银行选择，武器2K银行212、防具214、麻鞋216。复用 tile_image/tile 从ROM组合图块生成6张PNG，非截图贴图；nearest-neighbor采样。金样小刀形状与实际截图(128,41,16,16)透明区域一致。FCEUX调色板RGB与截图少量色值差异仍存在，不称严格电视色彩全等价。当前百货店原生截图只显示药品名称/价格，未观察药品独立预览，不伪造药品图标，不推断全游戏无图标。

原版装备页有右手/左手/身体/脚与攻击力/防御力/迴避力：private-derived/equipment-probe-final/frame-1170.png。E95E/E960根→E962/EBD5贡献表：小刀+2、手刀+5、長劍+10；肚兜防御+2、布衣+6、麻鞋迴避3。EF88/EF9B/EFA6为哪吒适用槽位列表。初始有刀攻击10，卸刀8，防御6、迴避3；换手刀攻击13。迴避不与基础敏捷简单累加。药品实际使用效果、原版直接替换已穿装备副作用未核，不能标整记录VERIFIED。

地图0原先把方向边缘类别2..9全关闭，挡住去百货店的真实路。CB7D/CEE4分别调源/目标方向，D214..D278是实际四方向/组合阻挡；现有 Scene.probeFrom 薄适配源/目标只读检查，仅map0启用。未知建筑与剧情边界仍不作为滑墙物理墙。客栈(6,25)/类别25及住宅特殊类别26/27/28暂未接入服务，保留 BLOCKER：缺服务价格/状态效果与事件；NEXT：按后续实际路线只核必要建筑。新室内BGM未核并未伪造映射。

最终c11增量：百货店问句直接按 items-edges/006367-transaction.png 读为「要買寶貝嗎？」；c10简化问句已纠正。不适用的药品preview VERIFIED字段移除，装备/药品字段继续分开限定证据范围。
