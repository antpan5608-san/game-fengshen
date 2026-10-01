# Data Schema

设计版本：Draft 1，Phase 0。下面是类型与约束契约，不是已经发布的游戏数据，也不是已经实现的完整JSON Schema验证器。Phase 1将其落成JSON Schema 2020-12、Kotlin模型和校验fixture；未知原版内容不在此补造。

设计直接对应已发现的SQLite/TMX结构，但纠正混用ID、复刻改编内容、碰撞属性语义不明等问题。完整源表字段见 [database-tables.json](evidence/database-tables.json)。

## 通用约定

|类型|定义|
|---|---|
|Id|大小写敏感字符串，`^[a-z][a-z0-9_.-]{1,95}$`；对象全局唯一，建议类型前缀；不依赖数组顺序|
|Ref<T>|Id且必须存在于T索引；跨类型引用非法；明确允许时才可null|
|Int|JSON整数；业务非负数量使用0..2^31−1；时长/经验/金钱上限由核实的ruleset再收紧；算术中间值使用Long并显式溢出策略|
|Ratio|`{numerator:Int,denominator:positive Int}`，0≤numerator≤denominator；不用二进制float隐含舍入概率|
|GridPos|`{x:Int,y:Int}`，发布后的canonical地图左上原点、x向右/y向下，单位格；导入前原坐标不更改|
|Direction|`up/down/left/right`；参考5/6/7/8的实际含义先求证，不根据数字猜|
|Rect|`{x:Int,y:Int,width:positive Int,height:positive Int}`；注明pixel或grid单位|
|AssetRef|资源索引ID，不是任意绝对路径或URL|
|Condition|下文定义的受限判别联合；禁止任意表达式字符串|
|SourceRef|来源索引ID；指向commit/hash/表/行/列或ROM bank+offset/录像时间点|

所有发布实体要求 `id`、`sourceRefs[]`。公共实体Envelope可附 `labels[]`，但未知字段默认报错（`additionalProperties:false`）；扩展放受控`extensions`命名空间并要求客户端能力声明。JSON不允许NaN/Infinity。

来源与可信状态是独立数据，不把UNKNOWN写成可运行数值：

```text
SourceEvidence {
  id, kind: reference|userRom|guide|video|manual,
  locator: {repository?, commit?, path?, sha256?, table?, primaryKey?, column?,
            romProfileId?, bank?, fileOffset?, byteLength?, url?, timestampMs?},
  fidelity: referenceExact|originalVerified|suspectedAddition|unknown,
  licenseStatus: unknown|restricted|permitted,
  verificationNote?, verifiedAgainst?: SourceRef[]
}
DraftUnknown { state:"UNKNOWN", reason:String, taskId:Id, sourceRefs:SourceRef[] }
```

`DraftUnknown`只在研究/隔离区允许。生产包必需字段含UNKNOWN时阻止发布；测试包必须有`profile=synthetic-test`并使用test命名空间。结构示例中的测试事件不能混入原版主线。

## 包、资产与内容基准

```text
Package {
  schemaVersion:positive Int, gameDataVersion:String, contentProfile:original-candidate|verified-original|synthetic-test,
  baseGameId:Id, rulesetId:Id, minClientVersion:String, requiredCapabilities:String[],
  entry:{mapId:Ref<Map>, spawnId:Id, eventId:Ref<Event>?, party:Ref<Character>[]},
  files:[{path:relativePath, sha256:hex64, bytes:nonnegative Int}],
  indexes:{maps:relativePath, actors:relativePath, events:relativePath, battle:relativePath, assets:relativePath, ...},
  sources:relativePath
}
Asset {
  id, kind:tileset|sprite|atlas|ui|font|bgm|sound,
  path:relativePath, sha256:hex64, bytes:nonnegative Int,
  image?:{width:positive Int,height:positive Int,paletteId?:Id},
  audio?:{format:String,channels:positive Int,sampleRate:positive Int,
           loop?:{startSample:nonnegative Int,endSample:positive Int}},
  sourceRefs
}
```

资源相对路径规范化、禁止`..`/盘符/重复条目；文件列表hash不包含自身递归hash，外层HTTP manifest校验ZIP整体。baseGameId锁定目标原版版本，不能只用标题名称。未知loop不默认整首无缝循环。

目录按领域组织即可：world（maps/placements/transitions）、narrative（dialogues/events/flags）、actors（characters/enemies）、inventory（items/equipment/shops/chests）、combat（skills/battles/encounters/progression/rules）、assets及provenance。Boss是Enemy引用的战斗配置，不必建重复敌人表；文本locale可在领域内按textKey共享。不机械复制用户举例的所有空目录。

## Map

```text
Map {
 id, nameKey:Id, width:positive Int, height:positive Int,
 logicalTileSize:{width:positive Int,height:positive Int},
 sourceGeometry:{tileWidth:Int,tileHeight:Int,origin:String,worldOffset?:GridPos},
 tilesets:[{id:Id,assetId:AssetRef,tileWidth:Int,tileHeight:Int,columns:Int,tileCount:Int}],
 layers:[{id:Id,kind:ground|decoration|foreground,z:Int,
          cells:[{tilesetId:Id,tileIndex:nonnegative Int,flipX:Bool,flipY:Bool,transpose:Bool}|null]}],
 collision:{terrainIds:Id[],rulesId:Id},
 encounterZones:[{id:Id,cells:nonnegative Int[],encounterId:Ref<Encounter>}],
 spawns:[{id:Id,pos:GridPos,facing:Direction}],
 npcIds:Ref<NPC>[], objectEventIds:Ref<Event>[], chestIds:Ref<Chest>[],
 transitions:Transition[], overlays:[{assetId:AssetRef,rect:Rect,z:Int,visibleWhen:Condition}],
 onEnterEventId:Ref<Event>?, bgmId:AssetRef?, sourceRefs
}
Transition {
 id:Id, trigger:step|interact|boundary, cells:nonnegative Int[],condition:Condition,
 targetMapId:Ref<Map>,targetSpawnId:Id,priority:Int,eventId:Ref<Event>?
}
```

cells按row-major长度等于width×height；index=y×width+x。空tile用null，不把GID0当第0张图块。导入保存GID的翻转位并通过已确定的tileset范围定位；不要把全局GID当永久资源ID。sourceGeometry只用于追溯；核心只读canonical坐标。

collision.terrainIds长度也等于地图面积；terrain是命名地形，规则明确步行/飞行/水域模式、方向边与触发条件。NPC动态阻挡不烘进静态数组。macValue/through/though/down尚未全部解码，未知格不能发布成“默认通行”。对角移动是否允许要按原版确认。

Map来源：map、TMX、map_mask、map_warp_point；story/auto_move内的跳转也必须纳入场景图。仅资源图路径齐全不构成可达证明。

## NPC

```text
NPC {
 id,mapId:Ref<Map>?,identityId:Id,displayNameKey:Id?,spriteId:AssetRef,
 variants:[{id:Id,priority:Int,when:Condition,visible:Bool,pos:GridPos,
            facing:Direction,footprint:{width:Int,height:Int},blocking:Bool,
            movement:Stationary|Wander|Path,
            onInteractEventId:Ref<Event>?,onTouchEventId:Ref<Event>?}],
 speakerOnly:Bool,sourceRefs
}
Wander {kind:"wander",bounds:Rect,timingRuleId:Id}
Path {kind:"path",waypoints:GridPos[],timingRuleId:Id,repeat:Bool}
```

NPC identity与场景instance分开；map_npc的多个剧情变体不会自动变成多个人。mapId=null仅允许speakerOnly或显式事件生成对象；身份显示信息不能强迫挂在某张图。variant同priority条件同时成立时报错或要求显式顺序，不依赖JSON排列偶然覆盖。zero_id先保留在source映射，不认定它天然是角色主键。

## Dialogue

```text
Dialogue {
 id,locale:String,entryNodeId:Id,
 nodes:[{id:Id,speaker:Ref<NPC>|Ref<Character>|null,
         pages:[{tokens:TextToken[]}],
         nextNodeId:Id?,choices:[{id:Id,textKey:Id,when:Condition,
                                nextNodeId:Id?,eventId:Ref<Event>?}]}],
 sourceRefs
}
TextToken = {kind:"text",value:String}
          | {kind:"lineBreak"} | {kind:"pageBreak"}
          | {kind:"pause",ticks:nonnegative Int}
          | {kind:"variable",name:approvedVariable}
```

保留原文/控制码/分页源数据；不将所有中文固定截3字节。UTF-8/Unicode语义与字体glyph分别处理。`story.type`可能是说话者/呈现类别，不作为事件opcode；`next_story_id`用于构造局部对话边或事件续接，0终止仅在取证映射确认后处理。事件动作与文本分离，避免“播放某句台词就隐式给物品”。

## Character与Progression

```text
Character {
 id,nameKey:Id,overworldSpriteId:AssetRef,battleSpriteId:AssetRef,
 progressionId:Ref<Progression>,initialState:{level:positive Int,exp:nonnegative Int,
 hp:Int,mp:Int,equipment:EquipmentSlots,knownSkills:Ref<Skill>[]},
 learnset:[{level:Int,skillId:Ref<Skill>}],allowedEquipmentTags:Id[],sourceRefs
}
Progression {
 id,characterId:Ref<Character>,minDefinedLevel:Int,maxLevel:Int,
 experienceMode:cumulative|nextLevelRequirement,
 rows:[{level:Int,hp:Int,mp:Int,strength:Int,agility:Int,vitality:Int,spirit:Int,
        attack:Int,defense:Int,experienceThreshold:Int|null}],
 levelUpRuleId:Id,sourceRefs
}
```

定义域内等级唯一/连续，maxLevel不自动写99；参考当前最高80不等于已经证实原版上限。末级experienceThreshold=null，而不是把参考0误判为无限升级循环。经验语义确认后才能设置experienceMode；现有reward_exp/reward_gold先保留raw，因为不知它们是奖励平衡表还是别的用途。

initialState含原版开局/加入时的状态，不从数据库第一行随意生成。角色名称身份、技能roleId与装备role01..04分别建立legacy映射，禁止统一整数强转。升级时HP/MP补满、差额补充或不补的规则由levelUpRuleId表达并测试。

## Enemy与Boss

```text
Enemy {
 id,nameKey:Id,spriteId:AssetRef,tags:Id[],
 stats:{hp:Int,mp:Int?,attack:Int,defense:Int,agility:Int},
 aiId:Id,actions:[{skillId:Ref<Skill>,weight:nonnegative Int,when:Condition}],
 resistances:[{effectId:Id,ruleId:Id,parameters:Object}],
 rewards:{experience:Int,money:Int,drops:[{itemId:Ref<Item>,quantity:Int,chance:Ratio}]},
 sourceRefs
}
Boss {
 id,enemyIds:Ref<Enemy>[],battleId:Ref<Battle>,defeatedFlagId:Ref<Flag>,
 prerequisites:Condition,onVictoryEventId:Ref<Event>?,
 phases:[{when:Condition,eventId:Ref<Event>}],sourceRefs
}
```

Boss不复制hp/attack到第二处；同一敌人有剧情变体时显式引用独立版本。monster.type=0只是导入标签候选，Boss进入原版包必须核对。`skillHurtNum/buffType/buff_pro/object_pro`需要字段语义映射，不默认百分数或直接伤害。

## Item与Equipment

```text
Item {
 id,nameKey:Id,descriptionKey:Id,iconId:AssetRef?,category:consumable|keyItem|equipment,
 stackLimit:positive Int,contexts:(field|battle)[],
 target:TargetSpec,useEffectId:Id?,consumePolicy:never|onSuccess|onAttempt,
 buyPrice:Int?,sellPrice:Int?,sellable:Bool,sourceRefs
}
Equipment {
 id,itemId:Ref<Item>,slot:weapon|armor|accessory|verifiedAdditionalSlot,
 occupiesSlots:Id[],allowedCharacters:Ref<Character>[],
 statModifiers:[{stat:Id,operation:add|multiplyRatio,value:Int|Ratio}],
 passiveEffectIds:Id[],attackTarget:TargetSpec?,sourceRefs
}
```

同一个物品全局只有一个id；Equipment是引用它的能力定义。双手通过占槽表示，避免只写isTwoHand却仍装盾。具体原版槽位、左右手兼容、买卖限制未知项不得靠现代RPG常识决定。货币是Wallet资产，legacy obj129映射为money reward，不能在正式背包里混入一叠“银两道具”。

效果是客户端注册的纯逻辑handler+参数，并声明target/随机/失败/消耗结果；unknown desc不编译成eval。任务物品的使用可触发Event，仍在事务内检查目标/Flag，使用失败是否消耗需实证。

## Skill

```text
Skill {
 id,nameKey:Id,descriptionKey:Id,mpCost:nonnegative Int,
 contexts:(field|battle)[],target:TargetSpec,effectId:Id,parameters:Object,
 accuracyRuleId:Id?,animationId:Id?,soundId:AssetRef?,
 prerequisites:Condition,sourceRefs
}
TargetSpec {side:self|ally|enemy,selection:single|all|random|none,
            alive:living|dead|any,maxTargets:Int?}
```

角色learnset负责学习条件，不用skill.roleId暗藏唯一所属。效果参数按effectId的schema验证：伤害/治疗/解毒/复活/逃脱等各自字段，不能让每个skill随便塞Object。Phase 1起逐个补 `$defs`，未知handler立即拒包。

## Shop与Chest

```text
Shop {
 id,npcId:Ref<NPC>,mapId:Ref<Map>,currencyId:Id,
 offers:[{id:Id,itemId:Ref<Item>,quantity:positive Int,price:Int,
          availableWhen:Condition,stock:Int|null}],
 buybackRuleId:Id?,services:[{id:Id,effectId:Id,priceRuleId:Id,when:Condition}],
 sourceRefs
}
Chest {
 id,mapId:Ref<Map>,pos:GridPos,appearance:box|hidden,
 openedFlagId:Ref<Flag>,condition:Condition,rewards:Reward[],
 fullInventoryPolicy:rejectAll|verifiedAlternatePolicy,
 onOpenEventId:Ref<Event>?,sourceRefs
}
Reward = {kind:"item",itemId:Ref<Item>,quantity:positive Int}
       | {kind:"money",currencyId:Id,amount:positive Int}
```

price=0是真实免费，null是未提供/不出售，不能互换。Shop对象由map_buy按地图/NPC聚合，offer保持原始次序证据；store默认隔离。酒店/医馆按service而非伪造为物品。交易与领取必须对余额、容量、stock和Flag一起提交，重复交互不会再次发奖。

## Event与Flag

```text
Flag {id,valueType:bool|int|enum,defaultValue:Bool|Int|Id,allowedValues?:Id[],sourceRefs}
Event {
 id,trigger:{kind:mapEnter|step|interact|itemUse|battleResult|script,
             mapId?:Ref<Map>,npcId?:Ref<NPC>,cells?:Int[]},
 priority:Int,when:Condition,repeat:always|once|whileCondition,
 onceFlagId:Ref<Flag>?,reentry:reject|queue,
 actions:Action[],onFailure:abortToCheckpoint|branch,
 failureEventId:Ref<Event>?,sourceRefs
}
```

Condition为严格判别联合：

|type|必需字段|语义|
|---|---|---|
|always|value:Bool|显式恒真/假|
|all/any|conditions:Condition[]|组合；空集合是否允许在schema固定，建议禁止|
|not|condition:Condition|取反|
|flag|flagId,op:eq/ne/lt/lte/gt/gte,value|按Flag类型校验，Bool只允许eq/ne|
|hasItem|itemId,quantity|背包检查，不修改|
|partyContains|characterId|在队伍中|
|bossDefeated|bossId|读取战后状态|
|money|currencyId,op,value|余额检查|
|battleResult|value:victory/defeat/escaped|仅战斗续执行上下文有效|

Action也是严格判别联合；每一行都必须生成独立JSON Schema分支，不用任意JSON对象吞掉错别字：

|type|关键参数|完成/失败约定|
|---|---|---|
|dialogue|dialogueId|等用户完成/选择才继续|
|moveNpc|npcId,path[],movementRuleId,blocking|等待或显式非阻塞；越界/不可走给结果|
|hideNpc/showNpc|npcId|写场景/持久覆盖，作用域显式|
|giveItem/removeItem|itemId,quantity|容量/数量校验，失败无部分修改|
|addPartyMember/removePartyMember|characterId,statePolicy|新建/恢复/保留装备等策略显式|
|startBattle|battleId,onVictory/onDefeat/onEscape:EventId?|等待结果，不能共用胜利支路|
|bossBattle|bossId|走Boss战事务，仅胜利设置击败状态|
|teleport|mapId,spawnId|逻辑传送并等待新场景就绪|
|changeMap|mapId,spawnId,transitionStyle|与teleport共用引擎，显式呈现方式|
|setFlag|flagId,value|类型匹配才写；不接受未知key|
|checkFlag|condition,then:Action[],else:Action[]|显式分支；读操作不暗改Flag|
|openShop|shopId|等待退出后继续|
|playMusic|assetId,mode:replace/stop,resumePolicy|stop分支assetId可省；循环用资源元数据|
|playSound|assetId,wait:Bool|必要时等播放完成|
|changeTile|mapId,layerId,pos,tile,collisionOverride?|永久变化写save overlay；明确是否影响碰撞|
|chest|chestId|奖励与开启标记同事务|
|condition|when,then:Action[],else:Action[]|通用条件树分支|
|sequence|actions:Action[]|有序执行；异步动作保留程序计数器|
|callEvent|eventId|受限调用栈与预算，禁止无界递归|
|wait|ticks|单位逻辑tick，不用墙钟延时|

`statePolicy`、作用域、移动规则等新语义需要客户端能力版本。不可把服务端字符串直接映射到反射方法调用。枚举不认识就拒绝激活该包。

仅用于Schema/执行器fixture的事件形状示例：

```json
{
  "id": "test.event.once",
  "trigger": {"kind": "script"},
  "priority": 0,
  "when": {"type": "flag", "flagId": "test.flag.seen", "op": "eq", "value": false},
  "repeat": "once",
  "onceFlagId": "test.flag.seen",
  "reentry": "reject",
  "actions": [
    {"type": "dialogue", "dialogueId": "test.dialogue.hello"},
    {"type": "setFlag", "flagId": "test.flag.seen", "value": true}
  ],
  "onFailure": "abortToCheckpoint",
  "sourceRefs": ["test.source.fixture"]
}
```

onceFlag只在事件成功完成后提交；上例对话后显式setFlag与执行器成功提交合并为同一结果。取消/失败不提前消耗once资格。真实剧情在未取证前不套此示例造事件。

## Battle、Encounter与规则

```text
Battle {
 id,kind:fixed|random,backgroundId:AssetRef,bgmId:AssetRef?,
 slots:[{enemyId:Ref<Enemy>,position:Int}|{poolId:Ref<EncounterPool>,position:Int}],
 canEscape:Bool,victoryConditionId:Id,defeatPolicyId:Id,
 onVictoryEventId:Ref<Event>?,onDefeatEventId:Ref<Event>?,onEscapeEventId:Ref<Event>?,
 specialRules:[{ruleId:Id,parameters:Object}],sourceRefs
}
EncounterPool {id,entries:[{enemyId:Ref<Enemy>,weight:nonnegative Int}],samplingRuleId:Id,sourceRefs}
Encounter {id,battleIds:Ref<Battle>[],selectionRuleId:Id,rateRuleId:Id,parameters:Object,sourceRefs}
BattleRules {
 id,formulaVersion:positive Int,physicalDamageRuleId:Id,skillRules:Id[],
 hitRuleId:Id,criticalRuleId:Id,multiHitRuleId:Id,turnOrderRuleId:Id,
 escapeRuleId:Id,rewardRuleId:Id,rng:{algorithmId:Id,stateFormatVersion:Int},
 arithmetic:{roundingRuleId:Id,overflowRuleId:Id},parameters:Object,sourceRefs
}
```

混合槽位是显式判别联合，不能把某个整数有时当enemy有时当pool。确认参考type语义后才能转换；没有选择权重就保持DraftUnknown，不默认均匀。

ruleId必须在客户端固定注册表中存在；内容包只调经schema约束的参数，不下载6502或通用脚本执行。公式和RNG未确认前BattleRules不得标verified-original。必须用记录了输入属性、状态、随机值、整数中间值、最终伤害的golden trace验证；不能用“常见RPG公式”填空。

## 玩家存档与兼容（非静态内容）

```text
SaveEnvelope {
 saveId:Id,saveSchemaVersion:Int,revision:Int,contentHash:hex64,
 gameDataVersion:String,rulesetId:Id,baseGameId:Id,checksum:hex64,
 state:{mapId:Ref<Map>,pos:GridPos,facing:Direction,
  party:Id[],characters:CharacterState[],inventory:ItemStack[],wallet:Wallet,
  flags:Map<FlagId,typedValue>,openedChests:Id[],bossStates:Map<BossId,State>,
  npcOverrides:Object[],tileOverrides:Object[],unlockedTravelIds:Id[],
  playTicks:Int,rngState:Object,eventCheckpoint:Checkpoint?,debugModified:Bool}
}
```

CharacterState包含level/exp/hp/mp、状态效果、装备、已学技能；player数据不放进服务器发布的game-data。未知旧ID必须显式迁移/保留旧包，不默默丢弃。V1安全点存档不保存半次战斗，后续扩展战斗快照需要提升saveSchemaVersion与测试。

## 校验契约

1. JSON Schema验证类型、required、枚举、范围、oneOf和additionalProperties；草稿UNKNOWN在正式包拒绝。
2. 索引全局ID唯一，所有Ref按正确命名空间解析；检查map/NPC/dialogue/enemy/item/event及其它域。
3. 地图层长度/tileset矩形/坐标/传送落点/素材存在性/碰撞已知；格子视图与原版证据比对。
4. 对话局部节点、事件调用与战后支路闭合；Flag有定义且类型正确，once/事务标记一致。
5. 条件图检查明显永假、同优先级冲突、不可达候选、无终止递归；有条件可达与完整主线还需状态轨迹测试。
6. 成长区间连续、经验模式明确、技能学习ID映射正确、价格/掉落概率/装备槽有效；不猜原版上限。
7. 包的schema/能力/client兼容；存档迁移必须在副本上成功，并保留rollback。
8. 来源与权限检查独立于格式检查。结构通过且referenceExact的内容仍可能不忠于原版。
