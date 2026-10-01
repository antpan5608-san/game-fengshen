# Migration Strategy

本计划只规定未来迁移步骤；Phase 0没有生成正式game-data，没有复制资源进APK，没有修改参考项目。以 [reference-audit.md](reference-audit.md) 的固定快照为起点，遵守“不猜原版数据”。

## 三层产物

1. **Raw/研究层**：只读参考快照、用户ROM、原始SQL/TMX/CCBI导出，保持原始类型、字段、哨兵、hash；放仓库外或private-derived，不随发布分发。
2. **Canonical候选层**：稳定ID、坐标/单位/类型规范化，带source map和UNKNOWN；原版候选与疑似改编分区。只有语义确定后才能运行。
3. **Verified发布层**：通过结构/引用/规则与来源审核的内容，加profile和baseGameId；打包时不把隔离区、ROM、整个来源快照一并装入。

转换必须确定性：同一输入hash、映射配置及工具版本产生相同canonical内容与报告；JSON排序、换行、压缩时间戳固定。保留原值与变换说明，允许沿输出ID回到源表主键或TMX图层格子。

## 直接使用、转换与补提取矩阵

“直接使用”仅表示技术上文件可读，仍受来源与许可证状态限制。第一版不批量发布UNKNOWN权限的素材。

|内容|迁移方式|步骤|验收|
|---|---|---|---|
|PNG候选|可直接读取，按需转换|先登记像素/色板/来源；仅有确证的图片做缩放恢复；重建atlas|无裁切漂移、锚点正确、与选定原版画面比对|
|MP3候选|可直接读取；循环元数据需补|探测格式，场景映射与循环听辨；是否改用ROM录制音频另决策|切图/战斗切歌、循环无缝、音量与音效抢占有证据|
|SQLite文本/标量|结构导出，不直接认定原版|只读SELECT，完整保留NULL/0/字符串"0"与行ID；UTF-8输出|逐表行数、字段集合与hash可复验|
|TMX|转换|base64/zlib→小端GID；firstgid→tileset/local index；保留翻转位、图层顺序、源尺寸|全部格子数一致、所有引用存在、视觉抽样对照|
|碰撞/遇敌图层|转换前研究|mac/macair/monster与through/though/down/monsterId逐值建词典|每个已用值有含义和证据；未知值阻止正式导入|
|NPC|转换|identity/instance/variant分离；范围全字段保留；map0说话者独立|相同zero_id不盲合并；每状态位置/可见性验证|
|story/auto_move/Flag|转换+重新取证|拆文本与动作；整理条件/Flag写入、异步续接、主角入离队|每个opcode/哨兵有依据；可重放有证据的小段剧情|
|物品/装备/技能/商店|转换+筛查|原版候选与改编隔离；roleId显式映射；效果从描述提候选后验证|不把商城store当map_buy；不给未知数值默认值|
|人物成长/怪物数值|导出候选+ROM比对|逐级表、怪物属性/掉落来源登记|关键等级与边界值逐项核，非线性不插值|
|战斗公式/遇敌率|重新提取，优先ROM|跟踪6502/内存/APU之外的CPU逻辑，记录取整/RNG/行动规则|golden trace逐中间值匹配；无ROM时保持UNKNOWN|
|PLIST/CCBI|转换|保留裁切/旋转/offset；解出节点关键帧→sprite clips/anchor/event cues|动画导出总时长、帧次序、回调与音效时间点验证|
|原版中文字库|ROM提取/人工映射|字形定位、编码表、文本控制码、Unicode对照|对话用字覆盖、无误字、原版字形比对|
|剧情缺口/隐藏物品位置|攻略补充→录像→人工核验|先做候选引用，附URL/时间戳，不直接批量生成事件|有版本/坐标/触发前后状态证据|
|存档|重新设计|原仓无完整可用玩家存档；不移植空API|新Save round-trip、损坏恢复、版本迁移测试|
|C++运行代码|不作为Kotlin可直接复用代码|提炼领域字段与行为线索，不搬空类/塔防残留/潜在崩溃点|纯Kotlin规则模块用实际证据验证|

## 有序导入步骤

### M0 冻结输入

以 commit/hash pin输入；保留 [file-inventory.json](evidence/file-inventory.json)。本阶段已做。下一次审计新commit必须重新生成证据并比较差异，不能让master变化覆盖旧结论。

### M1 无损结构导出

独立`reference-import`工具读取MainData只读URI，按源表导出raw JSON；每张表写row count、schema和NULL统计。map_npc的minPosY/maxPosX不能因为C++结构遗漏而漏导；switch字段不能降到uint8。保留数据库声明类型拼写`test`，但canonical按实际值确定，不据其拼写强推TEXT。

TMX转换先不改变坐标原点或80像素尺度。导出所有layers和tileproperties；通过fixture验证little-endian、压缩、空GID和flip位。所有259图生成清单，地图查看器叠加源坐标、NPC、传送、碰撞、遇敌与Flag条件。

### M2 语义字典与基准

建立`legacy-semantics`表：source field/value → canonical meaning → evidence → status。优先解码：

- 方向值5/6/7/8与原点、世界图offset。
- switch_on/off的读条件、0/9999哨兵，以及哪些动作何时写Flag。
- story.nextSceneId的0/1/2/3；2→map、3→battle已有记录支持，但仍需运行/原版证据。
- battle.type区分enemy/pool；monster层monsterId是否指battle，随机槽位是否放回采样。
- auto_move.type 0..5、map_object.type 1..8、map_npc.type 0..6、warp.type 0..2。
- role_lv与skill不同编号体系、装备角色限制、经验阈值、奖励字段与货币obj129。
- through/though/down/macair的通行/地形含义。拼写归一化是有证据的变换，不能静默纠错。

每个UNKNOWN有独立任务、需要的证据和影响范围；不为了让验证器全绿而填默认值。原版基准建立前，candidate数据包只用于研究，不叫verified-original。

### M3 改编内容隔离

先将store全表及工作室/VIP/购买金币/额外存档位证据相关记录标记suspectedAddition。map257/258/259、story630..639、monster174、battle151..153和object130..147是重点核查候选，**这不是“ID≥某值全部删除”的可靠规则**。

对每个候选做反向引用闭包：商店、箱子、NPC、事件、地图连接、战斗奖励、图像/音频都可能引用。隔离时保留边的诊断，不直接删到引用断裂；相关剧情是否属于原版必须有原版比对后决定。即使早期ID也可能被改数值，所以其余记录不得默认verified。

### M4 原版取证与ROM研究

用户提供ROM后先本地hash/版本profile，不自动上传。提取分probe/decode/export三步：probe只报告候选；decode保持原值/位置；export仅输出已确认字段。不同hash不得复用硬编码offset，除非特征/版本差异已验证。

ROM研究有独立输出manifest：输入hash、header、mapper、bank、文件范围、解码算法版本、候选含义、验证截图/轨迹索引；未知数据不扩充成臆测内容。首轮目标是原版字库/地图与一组人物、敌人/战斗数据，建立可重放基准，不承诺一次解析整部游戏。

无ROM仍可进行M1和部分格式校验，但忠实度、战斗公式与某些事件语义不能越过门槛。公开攻略用于交叉清单，视频用于实测表现，人工做文本/画面/流程核对；不要用录像里一次伤害拟合唯一公式。

### M5 小范围canonical闭环

选有充分证据的一小段开局场景，明确地图、NPC、文本、物品、场景出入口与剧情状态；不是随意创造“陈塘关示例”。为这部分生成严格Schema对象和source map，保留未支持opcode诊断。

用独立验证器与地图查看器检查后，再按获确认的下一阶段计划创建Android切片。后续战斗必须先有公式证据，事件必须有Flag语义。需要验证的内容多于已知内容时，先缩小切片范围。

### M6 分章扩展与发布

按原版实际流程分批确认：每批输入变化可复查、数据与来源差异报告可读、章节入口/出口/关键Boss/存档checkpoint可重放。发布前所有必需UNKNOWN清零，疑似改编隔离，生成不可变hash包。

不以“639条全部转换成功”代替剧情验收。全主线覆盖需要从新游戏的连续流程、关键分支状态、最终Boss与结局，并做保存/重启恢复测试。

## 数据版本与迁移边界

ID首次分配后稳定，不因数组排序或新的原版分类而重编号；legacyId放source locator。旧包/旧档迁移记录包括rename/remove/replace ID与状态转换。删除一个剧情Flag时必须说明旧档如何继续，不用空值掩盖。

迁移输出全量变更摘要：实体增删改、引用变化、公式/经验变化、旧档兼容性、资源hash变化、待人工验证项。已知可逆状态保留rollback；不可逆迁移也必须保留原档副本和原包，不能覆盖。

## 迁移阶段的测试

- 导出一致性：源表行数/主键集合/字段/Unicode/NULL逐项比对，重复运行字节稳定。
- 地图：259图格数与GID回溯、tileset、flip、遮挡；实际图片尺寸与声明差异作为warning单独报告。
- 语义：每个原始枚举有覆盖fixture，未支持值失败；坐标转换使用已确认地标验证，不单纯自身往返。
- 剧情：含战后续接、特殊道具、NPC消失、入离队、不可逆切图的轨迹；宝箱/商店的中断不会重复奖励。
- 数值：与指定ROM/原版运行证据比对，不以参考库自比对充当真实性测试。
- 发布：包可验证、旧版本可启动、迁移失败仍能读旧档、被档引用旧包不被清理。

迁移授权边界：这里没有进行APK构建、Go API开发、ROM逆向实施或任何部署。Phase 0交付后等待确认。
