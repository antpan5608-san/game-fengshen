# Reference Audit

审计日期：2026-09-29。范围：Phase 0，仅分析，不修复参考工程，不开发 Android 或服务端。

## 结论

参考仓库是 **Cocos2d-x C++ 原型代码 + 较丰富的复刻版内容库**，不是能够从开局通关的游戏工程。数据库包含开局至结局的候选剧情、末期地图和纣王战，但缺少将它们执行起来的移动、事件、战斗、存档等核心实现。不能据此宣称“完整原版主线已经拥有”。

发现明显改编标记：`map.id=259` 黑白牛工作室、`monster.id=174` 黑白牛、`story.id=630..639` 相关内容；`object.id=132` 描述含 VIP，`store.id=16` 购买额外存档位，`store.id=19..21` 购买金币。它们说明整个数据源需要逐项核对，而不是仅删掉最后几条就视为原版。

**可量化的是候选数据规模，无法给出原版复用百分比。** 原版总量和版本基准尚未建立；代码完整度与数据量不可相互替代。

## 获取与证据边界

- [参考仓库固定提交](https://github.com/v5100v5100/FengShenBang/tree/d636453f14f86a096f9d293bf3facfd96cfcb614)，提交日期 2020-05-28。
- 两次 `git clone`（第二次 HTTP/1.1、浅克隆）均因连接重置失败。随后取得 GitHub 官方固定提交 ZIP，并按官方递归 Git tree 下载、核验全部文件；本地是**完整源码快照，不是成功克隆的 Git 历史**。
- 快照：`F:/apps/FengShenBang-reference`。未改动任何参考文件。1,958 个文件的 Git blob SHA-1 全部匹配该提交，另存 SHA-256 清单。Git tree 未截断。
- ZIP：`F:/apps/FengShenBang-reference-d636453.zip`，SHA-256 `8c779ead6326a82cc8f2ee1f57075c178c74b3d84e22c024f3247988ab137dbd`。
- 新文档仓库：`F:/apps/fengshen-remake`，本地 Git 已初始化，未提交、未推送、未发布游戏素材。
- 环境已定位 Git 2.54、Python 3.12、Java 17、Go、Android adb、Docker CLI；没有借“工具存在”宣称 Android/容器构建已通过。参考快照没有 CMake、Gradle、平台工程、引擎依赖锁定或构建说明。

证据：[`audit-summary.json`](evidence/audit-summary.json)、[`file-inventory.json`](evidence/file-inventory.json)、[`database-tables.json`](evidence/database-tables.json)、[`map-catalog.csv`](evidence/map-catalog.csv)。所有源码位置下文相对于参考根目录；`DB` 统一指 `Resources/res/MainData`，可用表名、主键复查。

## 全量静态检查范围

|范围|执行结果|能够说明 / 不能说明|
|---|---|---|
|全部文件|1,958 文件，29,972,389 字节，逐文件哈希匹配|快照完整；不代表原版内容完整|
|全部源码|16 cpp、19 h、1 c；项目自身 34 文件、2,079 行，另有 sqlite3.c/h|阅读业务源码与声明；SQLite 为 3.29.0 第三方库，未逐行审计其数据库内核|
|配置/数据|20 张 SQLite 表，`integrity_check=ok`；没有数据库外独立剧情 JSON/CSV|表与记录可读；语义仍需验证|
|地图|259/259 XML 解析并解压所有图层；图块数、声明的 GID 范围、图片路径通过检查|不等于已验证碰撞、路径、坐标约定|
|图片|1,154/1,154 PNG 完整解码并登记尺寸；代表性图块进行目视检查|不代表每一幅与 ROM 逐像素对照|
|plist|39/39 解析，登记 frame 与 metadata|尚未将图集还原成运行时动画|
|CCBI|413/413 为 v5；缓存字符串、413 条顶层 sequence 元数据解析通过；含路径资源引用未缺失|没有完整解释节点树、逐节点关键帧，也没有播放每个动画|
|CSB|1 个 `MainScene.csb`，哈希/文件存在性登记|二进制节点图未解码，不作为剧情逻辑证据|
|音频|23 BGM + 29 音效，共 52 MP3，MPEG 元信息解析通过；总时长约 1,239.19 秒|未逐帧音频解码/逐曲听辨/原版 APU 对照；循环点 UNKNOWN|
|字体|2 TTF 可解析，均不含 U+4E00..U+9FFF 字符|没有找到可复用的原版中文位图字库；其他 CJK 扩展不在该统计口径|
|引用|显式非零外键候选及声明的条件型引用检查未发现悬空 ID；表内 id 未发现重复|无正式 SQL 外键约束；条件类型映射部分属于假设，不证明事件可执行|
|资源关联|4,092条DB路径/命名约定探测及CCBI含路径资源大小写检查通过|映射约定尚需执行语义确认；详见asset-reference-checks.json|

## 运行代码证据

1. `Classes/AppDelegate.cpp:46` 创建 `GameStartScene`；`GameStartScene.cpp:28` 是测试文本，`:41` 加载角色展示，没有进入完整新游戏的实现。
2. `GameScene.cpp:33` 只硬编码 `map_001.tmx`；`:51` 起方向键处理只打印日志，`:82` 的 `loadMap` 只读两种数据，没有实现地图运行。
3. `Npc.cpp:7` 的 `createNpc` 返回空指针，`:15` 自动移动为空；`Hero.cpp`、`Monster.cpp`、`GameDirector.cpp` 只有构造/析构。
4. `GameSceneManager.cpp:19` 切图为空；`MapLayer.h` 的 `loadHero/loadNpc/loadMap` 只有声明。
5. `DataManager.h:51`、`:56` 的存读档只有声明；DB 的 `z_save_base_info` 无记录，且其字段远不足以恢复队伍/背包/Flag。
6. `DataManager.cpp:27..31` 对空指针 `sql` 执行 `sprintf`，并不检查 `dataSet[0]` 是否存在；`:60..62` 算出了完整路径却仍用相对路径打开 DB；`:121` 直接将 SQL NULL 当字符串处理有风险。**这些是静态发现，未伪装成运行复现。**
7. `GameScene.cpp:85` 调用 `readMapBoxList`，仓库里没有对应定义；没有配套构建工程，因此未构建/启动参考游戏。
8. `DataItemDefine.h` 的多个 `switch_on/off` 使用 `uint8_t`，DB 却出现 397、511、638、9999 等值，按此结构搬运会截断。`MapNpcStruct` 丢失 DB 的部分移动范围字段；`RoleLvStruct.attack` 与 DB `at`、`BattleStruct.monser_*` 与 DB `monster_*` 命名不一致。
9. `PublicDefine.h/PublicResourceDefine.h` 含塔防、萝卜、炮塔等残留定义；它们不是本作战斗公式。`SoundEffectManager` 是空壳和重复宏。

## 38 项审计矩阵

判定口径：**完整**必须有足够内容与运行/原版证据；**部分实现**可仅指存在候选数据或原型，细节见该行；**缺失**指在固定快照中未找到所需实现。原版真实性另列，不以“部分实现”暗示已经可玩。低/中/高是提取难度；“可转”不等于获准再分发。

|#|审计项|状态|源码位置|资源/数据位置与发现|提取 / 新项目使用|
|---|---|---|---|---|---|
|1|完整地图与数量|部分实现|GameScene.cpp:33|DB map 259；map/map_001..259.tmx；含世界分块及改编地图|低；可转，原版全量 UNKNOWN|
|2|Tile / TileMap|部分实现|MapLayer.cpp:22|259 TMX；138 张 map 目录 PNG；图块 80×80|低；解析后转，像素真实性待核|
|3|地图碰撞|部分实现|无碰撞执行器|所有地图有 mac；另有 macair、through/though/down 等|中；必须解码语义，不直接硬搬|
|4|NPC 坐标|部分实现|DataItemDefine.h MapNpcStruct；Npc.cpp:7|DB map_npc 643 行，含同人物状态变体、动画占位；368 个不同 zero_id|低；保留原坐标，验证坐标系|
|5|NPC 对话|部分实现|Npc.h storyList；TextPrinter.cpp|DB story 639 行，均有文本，关联 map_npc.storyId|低；文本易取，非完整对话系统|
|6|完整剧情文本|部分实现|GameStartStoryScene.cpp 空壳|story 1..639 有开局与结局；混入改编|低；原版逐句覆盖 UNKNOWN|
|7|剧情事件|部分实现|GameDirector.cpp 空壳|story、auto_move 137、map_object 222、battle|中/高；缺执行语义|
|8|剧情 Flag|部分实现|DataItemDefine.h switch 字段|多表 switch_on/off；无 Flag 定义表和写入规则|高；0/9999 暂作原始值，不能猜布尔值|
|9|场景切换|部分实现|GameSceneManager.cpp:19 空方法|map_warp_point 3,548；map.jump_*；story 场景参数|中；数据可转，触发优先级 UNKNOWN|
|10|四主角数据|部分实现|Role.h、Hero.cpp|role_lv 四组；NPC/角色图像；没有 character 身份表|中；身份/技能编号需映射验证|
|11|基础属性|部分实现|RoleLvStruct / MonsterStruct|role_lv 的 hp/mp/力量/敏捷/体能/精神/at/df|低；可转，原版数值未核|
|12|等级|部分实现|Role.h level|roleId 1:1–80，2:12–80，3:24–80，4:38–80|低；区间内无断档；无升级逻辑|
|13|经验成长|部分实现|RoleLvStruct|role_lv 249 行 exp/reward_exp；末级 exp=0|中；exp 为累计/升下级需求尚 UNKNOWN|
|14|武器|部分实现|ObjectStruct|object.type=2 共57行（候选分类），有属性、双手、角色限制|低/中；须排除改编装备|
|15|防具|部分实现|ObjectStruct|object.type=3 共46行，含 mini_type 分类|低/中；装备槽和叠加规则待核|
|16|道具|部分实现|ObjectStruct|object 总147行，混有装备/货币/额外内容|低；结构可转，效果要显式实现|
|17|法术/技能|部分实现|SkillStruct|skill 34：26条角色技能候选、8条怪物技能；desc 含部分公式文字|中；不能把 desc 当可执行公式|
|18|怪物|部分实现|Monster.cpp 空壳|monster 174，type=1 134行|低；可提取候选图鉴与属性|
|19|Boss|部分实现|无 Boss 战斗逻辑|monster.type=0 40行；含纣王171和改编黑白牛174|低/中；40是标签数，不是原版 Boss 总数|
|20|怪物属性|部分实现|MonsterStruct|hp/atk/def/agi/exp/money/skill/drop 等|低；可转，精确性未知|
|21|战斗公式|缺失|GameDirector/Monster 为空|未发现完整物理/命中/逃跑/行动顺序公式|高；需要新证据，不能自拟|
|22|伤害计算|缺失|无伤害结算实现|skill.desc 有零散表达式，monster.skillHurtNum 有参数|高；缺完整算法、取整、随机规则|
|23|随机遇敌机制|部分实现|无遇敌循环|battle 153、map_monster 58、18图有 monster 层|中/高；分组可取，选择语义待核|
|24|遇敌率|缺失|无步数/RNG 判定|没有可信每步概率或计步算法；LianJi 不是遇敌率|高；ROM/实测|
|25|商店|部分实现|MapBuyStruct；无交易实现|map_buy 230商品行、51组(mapId,npcId)；store为另一套额外商店候选|低/中；两表不能混为原版商店|
|26|商品价格|部分实现|ObjectStruct|object.buy_price/sell_price、NPC cost_money、store.price|低；原版价目需核验|
|27|宝箱|部分实现|MapBoxStruct；读取仅声明|map_box 175，含 is_box 隐藏物品标识|低；需要一次性领取/满包规则|
|28|金钱系统|部分实现|无余额/交易引擎|monster.money、obj129银两、价格/奖励字段|低/中；货币与物品分离，规则待核|
|29|Sprite|部分实现|Role.cpp:36|resCCB/Role、Battle、ccbFiles/Role|低/中；PNG易用，动画图集要转换|
|30|Tile|部分实现|MapLayer.cpp|map PNG 与 TMX tileset|低；并非全都可以5倍无损还原|
|31|UI 图片|部分实现|GameStartScene、Role 示例|resCCB/Lobby、Object、Icon、Battle，39 plist|低/中；含复刻版 UI，原版一致性未知|
|32|动画|部分实现|Role.cpp CCBReader|413 CCBI v5；资源有顶层 sequence，缺完整游戏接入|中/高；抽成 atlas/clip，帧时序待核|
|33|BGM|部分实现|SoundEffectManager 空壳|Sound/bgm_001..023.mp3，map.bgm|低；格式可读，曲目对应/循环/原版音色待核|
|34|音效|部分实现|SoundEffectManager.h 重复宏|Sound/sound_001..029.mp3|低；触发时机/编号意义待核|
|35|存档系统|部分实现|DataManager.h:51/56 仅声明|z_save_base_info 空表；z_save_system 1行设置|实现缺失；旧档无可靠迁移基础|
|36|游戏状态|部分实现|GameSceneManager.h 若干成员|零散角色字段和空存档表，没有完整状态模型|重新设计，不照搬|
|37|完整主线|部分实现|没有事件解释器|battle144→story579；story579..596有结局段落|可提取候选；无法证明全程触发闭环|
|38|开局至最终 Boss 通关|缺失|入口为演示场景，移动/战斗/存档缺失|仅末期数据存在|当前快照不具备完整可玩实现；未作实机通关声称|

## 不能忽略的数据语义

- 地图的全部 mac 层不是透明度层的同义词。属性 `through` 与拼写 `though` 并存；值 0..5、`down` 0..2、`macair` 必须逐项解释。未知值不可默认可走。
- `battle.type=0` 的槽位看起来引用 monster；`type=1` 看起来引用 map_monster 池。例如 battle144 的171是纣王，battle143 的54对应池。**这是由记录交叉一致性支持的解释假设，运行代码没有实现，转换前需实证。**
- `monster` 图层的 `monsterId` 可以大于58，不能直接视为 `map_monster.id`；可能是 battle ID。必须保留源 namespace。
- skill.roleId=1 的提神术描述指向小龙女，而 role_lv.roleId=1 组 MP=0；不能直接联表。四组等级与人物的最终对应均需要确认身份基准。
- 仅按 warp+jump、忽略 Flag/碰撞，从 map1 可达233/259；未达22..47为地狱等区域。story66 提供到 map30 的剧情跳转证据，**因此这26张不能判成缺图或死图**。这也证明单一地图邻接检查不足以证明通关。
- map目录138张 PNG 中，仅28张满足整图逐像素“精确5×复制”测试；其余110张不满足该条件。不能统一缩为16×16图块并声称无损恢复 FC。
- TMX共有190处图片尺寸声明与PNG实际尺寸不同，例如mac.png被声明为240×160但实际为240×240。见[map-warnings.json](evidence/map-warnings.json)。这是单独的迁移warning，不是XML损坏；重新计算firstgid时必须防止吞掉下一tileset的编号区间。

## 来源及许可证

参考根目录仅 README 与代码、素材，没有项目 LICENSE/COPYING/NOTICE；README 未说明数据/图片/音频来源与授权。记录为 `license=UNKNOWN`、`provenance=reference-remake`，不能按“GitHub公开”推断可再分发。

sqlite3.c/h 的版权放弃说明仅属于 SQLite，不扩展至游戏内容。Arial 内嵌声明带有 Monotype 权利及使用条件；Marker Felt 也有作者/共享软件标记。两者都不能当作原版中文字库或自动获准嵌入的免费字体。

Phase 0 的外部资料仅用于格式与补全方案；没有下载 ROM，没有将网上攻略生成成游戏数据。后续 ROM 仅接收用户本地输入，记录 hash，禁止进入 Git 或公开构建下载。

## 审计可信范围与剩余 UNKNOWN

本报告完成的是固定快照的递归静态审计、全量格式检查、数据库关系检查和设计。不包含原版逐句对照、全部动画播放、音频听辨、完整 CCB 节点执行、原工程构建或真实通关。没有 ROM 版本和可执行参考构建，原版总地图数、对话数、精确公式、剧情 Flag 写入规则、完整流程覆盖率仍为 UNKNOWN。下一阶段应先做数据真实性与语义取证，详见实施计划。
