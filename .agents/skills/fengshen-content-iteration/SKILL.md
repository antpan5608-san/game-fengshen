---
name: fengshen-content-iteration
description: 在Fengshen当前授权的内容迭代或地图批量接入中复用可信APK基底、原巡检与CI验证；不自行扩大全量研究，不用于纯规划或其他项目。
---

# 适用与输入

先读AGENTS.md及docs/current-task.md；当前任务/版本/欠账不保存在skill。
需要本轮范围、已审核基底构建run_id与APK SHA、可信receipt及内容pin。
资源获取按AGENTS.md最新授权；不含凭据，不扩展任务或发布范围。

# 已验证方法

1. 定位输入：查具体provenance的verifiedFields/remainingUnknown、已发布定义和实际提取函数。
   买卖已核不能推断使用效果；声明待核时先查公开研究与参考，使用时保持实际置信度；本轮药草已补局部原版证据。
2. 业务修改前调用原发布workflow的只读入口：
   `gh workflow run android-publish.yml --repo antpan5608-san/game-fengshen --ref main -f mode=inspect`
   原check-runtime.ps1 -SummaryOnly查询受保护环境，approve沿用reviewer；publish跳过。
   返回只保留task_id、时间、权威两版、样本/错误计数和覆盖限制，不保存samples或整份旧日志。
3. 当前任务不能下载官方artifact时，先报告实际HTTP/CONNECT错误，不输出签名URL、不绕过代理。
   已验证原build workflow的`mode=verify-base`，传入`base_build_run_id`及`base_sha256`：
   runner下载原fengshen-signed-apk，比较来源提交/run/receipt/完整字节，调用ci_apk.py verify/restore。
   该模式没有签名/服务器Secrets，不生成新APK；其成功运行禁止被当作生产APK构建。
4. 本地公网回退：重新读取version.json，核对实际版本/package/signer及APK完整SHA/size，
   再用原`tools/ci_apk.py verify`验签名/版本/非debuggable/内容pin；`restore --apk`写原assets入口。
   工具要求JDK及ANDROID_SDK_ROOT下的官方build-tools；Linux aapt还需同包lib64/libc++.so。
   来源URL的版本查询参数不保证不可变；恢复发布内容不等于迁移ROM/原始回放。
5. 相关回归：`python -m unittest discover -s tests -p test_ci_apk.py`；
   `python tests/test_environment_review.py`在Linux+bash/jq执行真实workflow脚本的隔离API fixture。
   安全断言覆盖非法来源、不同提交、无build成功job、reviewer/权限拒绝及inspect隔离。
   实际App正常操作、冷启动和覆盖升级必须另验，fixture不算正常游玩。

6. 本地缺目标ROM时，复用game-data/provenance/rom-acquisition.json中固定提交的公开URL，
   以Python urllib普通访问取回一个文件，存private-inputs（忽略），用hashlib核对完整SHA/大小，再以原Reader解析。
   hash匹配只证明字节一致；licenseStatus仍单独记录。已取得的匹配输入缓存复用，不逐轮重复下载。
7. 局部药草原版边界复用tools/rom-extractor/probe-town02-herb.lua：FCEUX Lua正常菜单按键，
   受控RAM fixture明确标记，不能作为正常路线证据。FENGSHEN_ROOT指定仓库；HERB_CASE/HP/FLAG/COUNT控制隔离样本。
   Debian官方FCEUX包加Lua5.1和Xvfb在用户scratch解包运行已成功，许可文件保留；offscreen OpenGL失败，Xvfb成功。
8. 复用原export_development.py的--base-apk/--provenance/--version生成可审核目标manifest；
   ci/content-source.json分别固定iteration.base字节/hash和目标内容pin。
   ci_apk.restore由基底验证→局部导出→目标hash验证→写assets；旧药草局部路径不需ROM或旧captures；新场景按实际必要ROM依赖复用受控缓存，不要求整个历史目录。
   对旧基底单独验包使用ci_apk.py verify --base-only（精确基底APK hash门禁）；默认verify仍审核目标APK。
   两次干净临时目录restore的42文件一致；test_town02_export.py覆盖错规则/ROM/pin和不变素材字节。

9. 原build-ci.ps1恢复目标内容、相关Python门禁、Gradle wrapper的release单元/构建/同签名验包已实际成功；
   APK与instrument APK由原工作流分别保留。业务用HerbUse，触摸用原面板；apply之外的available不结算。
   本地JRE缺编译器时，用Debian官方匹配JDK-headless包在scratch补齐，不改全局权限；JVM网络沿用平台代理系统属性。
   debug单元测试与构建成功，ContentTest及受控药草UI仪器实际成功；不将其写成正常流程/正式覆盖通过。

10. 同候选运行用原workflow的runtime job与ci/run-town02-runtime.sh，临时目录显式ANDROID_AVD_HOME和avdmanager --path；
    默认路径假设曾实际失败，显式路径后runner成功。单个AOSP AVD下载原基底/本次候选和同签名test APK，
    先正常赠刀保存再adb install -r覆盖，验旧档一致；受控边界与Content后由原record_app_audio.py --silent
    执行正常药店购买、受伤使用、外部force-stop、实际GameView冷启动和继续探索。输出运行回执和实际录像；声音NOT_RUN。
    发布workflow复核同main提交/build run/APK hash和成功runtime job及回执，测试job不持生产Secret。

# 输出和停止条件

输出恢复/验证receipt、脱敏巡检摘要、测试结果及固定交付报告；临时输入/产物按.gitignore隔离。
缺直接证据先找已有公开研究与参考；有来源且无冲突可PROVISIONAL接入开发版，关键状态定向测试，不猜药效/条件。缺口只阻塞依赖项，不停止无关可执行工作。
无设备/无App实际验收只留候选；纯文档/skill或工具变化不发布游戏。
权限/查询失败报告实际动作和最小权限；保留main、reviewer、同提交/同APK hash和目标隔离。

# 仍待验证

局部导出和目标内容恢复已实际验证；签名构建已成功；App覆盖以当前任务记录为准。
不能仅改目标pin后要求旧服务器包满足它，也不把任意ZIP改名APK。
原版药草边界、签名候选覆盖、正常闭环及停止进程恢复已由真实runner成功执行；真机和声音仍NOT_RUN。
本地无KVM的软件AVD两次System UI ANR，保留失败，不能作为正常流程PASS；低分辨率不保证解决。
原买卖/装备/INPUT-01/摇杆回归在同候选runner已实际通过；原发布门禁和自动审批已在真实生产runner成功，公网独立下载/验签/内容复核通过。
真机、声音、真实账号多设备恢复仍需实际执行，不将工具存在写成设备已经运行。

详细入口：docs/android-ci.md、tools/ci_apk.py、check-runtime.ps1、原两份Actions工作流。

## 已验证的限定新场景导出

原export_from_base已扩展现有Reader/metatile/ROM-tile recipe路径，可从可信不可变APK基底批次导出当前必要地图/出口/NPC/敌群与本场图形。ROM原始文件仍在忽略的private-inputs/.ci-private，recipe只含偏移/长度/hash/像素组合与来源。先核每条出口实际落点/方向，不交换猜返程；遭遇分区/行为未知只保留受影响区域边界，旧可行区域不能缩成轨迹白名单。

本地已运行test_nanhai_export.py：同输入两次结果一致、不变媒体逐字节一致、错误基底/目标pin/ROMspan/敌数值拒绝、manifest全文件核验。该测试输入为FENGSHEN_CONTENT_BASE_APK指定的已审核APK；源码依赖与生成方法均在原export_development.py及当前provenance。图形PNG编码的透明0和不透明黑底须按实际观察区分。Windows/Linux的Pillow压缩字节曾不同而原bytehash失败；原导出器以已审核RGBA hash核像素，并用固定存储DEFLATE序列输出PNG，目标文件/manifest hash继续严格审核，源PNG hash仅保留溯源。本地Pillow解码/CRC/多block/不调用平台encoder及错像素拒绝测试已通过，Windows runner已严格恢复与Linux同一manifest；随后测试文件的默认cp1252读取失败，中文JSON必须显式read_text(encoding='utf-8')。不能关闭hash门禁或删测试绕过。

限定Boss业务边界已在原JVM门禁运行：攻击等防伤0/小于防伤1、同字节命中与倍伤、敏捷排序、冰与物理同字节选择、Boss逃跑读字节后失败耗行动、掉落数量/分类格数满不丢物品。本地构建/原版正常取证不能替代Android正常主线或runner签名门禁；相应CI/App方法已在原签名候选正常运行门禁执行；实际结果以当前交付记录为准，不把本地或原版证据当Android验收。


## 已验证的连续区段交付

- 原TouchTest.testNormalNanhaiRouteBossAndVictory以真实MotionEvent从新游戏补给/练级/连图/NPC到Boss胜后，再正常离宫/再入；不得调用restoreSnapshot、改HP/等级/物品/flag或固定胜利。边界fixture用独立testControlledNanhaiVictoryFlagAndResumeOnce，不能混作主线。
- 原record_app_audio.py --silent配合--cold-test testNanhaiColdStartMatchesNormalSave和--budget-seconds在原KVM runtime已实跑：未剪辑分段、SHA/Android uptime、每段只读保存world边界、外部force-stop/实际GameView冷启与继续。战斗live HP以原片为准；分段拉取有短间隙，索引时间近似，静音不等于音频验收。
- App外部索引必须先用原pull_evidence收集再挑小型原片；此前收集顺序错使正常App通过但整个run失败，保留门禁。小型原片只是原文件副本，原完整artifact继续保留全段，不生成示意图/假战斗。
- 原check-reviewed-apk.ps1要求同main/来源/签名/审核APK字节和成功runtime receipt，新增区段门槛由实际测试和原workflow承载。审核时冻结来源；修候选必须重新构建验证。发布后文档提交可以不同，明确APK来源。原publish pre/post巡检与公网完整APK独立复核已成功；新版本无样本如实记录。


## 已验证的限定资源与断言诊断

运行升级基底可以与内容导出基底不同，原ci/content-source.json的runtimeBaseline固定实际已发布APK；receipt.buildRunID可能是字符串，仅将编号统一成字符串比较，来源/hash/证书/manifest照常严格检查。实际原校验块通过，错误来源/编号/版本/内容fixture仍拒绝。

完整App录像artifact可能超过当前32MiB传输限制，直接GH下载也曾实际403；不要移除代理或反复下载。原android-publish.yml的inspect可带runtime_evidence_run_id读取同仓库main原build的完成run，只抽取受限大小的隔离仪器txt到fengshen-runtime-assertion-diagnostics并继续真实巡检；已实际取回缺失的失败断言。此入口不上传APK、不登记版本、不清理数据，不将失败artifact作为可信内容基底。之后原录屏器会在正常断言失败时输出对应日志尾部，原小型checkpoint artifact保留txt；原始生产客户端日志不进入这些产物。

原runtime已实际完成单指点怪、信息不耗行动、动作HP快照、正常成长与本场战斗药草，再跑正常新游戏至龙王胜后及外部冷启。相关局部fixture与主线证据分开；重复字体执行次数与独立方法数分开。局部取证复用匹配缓存和probe-mobile-battle-herb.lua，需已有受控存档及FENGSHEN_ROOT/BATTLE_HERB_CASE；不可把受控实验冒充正常原版游玩。

发布成功后才更新runtimeBaseline为实际审核APK，并对该APK执行原run-town02-runtime.sh的PYBASE校验块；iteration.base仍服务原确定性内容导出，不一起盲改。原片可能包含Android metadata数据轨，ffprobe核对实际video与audio流，不能误将数据轨当损坏视频。用原片SHA、uptime事件索引、外部冷启片及GitHub artifact入口支持交付；静音原片依然声音NOT_RUN。

## 已验证的图集故障与共享服务证据定位

- 黑屏先检查实际审核APK图集像素，不仅验证PNG可解码和hash。曾有防具图集全部不透明黑；匹配ROM的正常进店、淡入完成后取得真实palette，复用scoped_map_atlas生成配方。全黑/淡出palette必须拒绝，不能靠换成别店图集消除黑屏。test_world_export.py已本地验证确定性、原媒体字节不变及错价/状态/出口/palette/span拒绝；App流程须另验。
- 住宿原版正常按键与受控边界分开；价格、排除状态和返回出口分别查原Reader span。恢复HP/MP不等于复活或清除所有异常；不可用队员保持原状。原版取消、钱不足、满状态及多队员受控实验已运行，具体案例见world-full01 provenance；不把fixture写成正常路线。
- 村庄室内共享地图ID：caller与辅助NPC上下文各有独立RAM来源；不能按村庄数量复制地图或把NPC overlay当缺失几何。完整有效集合仍须按当前任务核定。
- 更换可信内容基底时，历史Nanhai golden配置独立固定，test_nanhai_export.py用FENGSHEN_GOLDEN_BASE_APK；当前局部导出用FENGSHEN_CONTENT_BASE_APK。两者各自验hash，不把新任务集合写进旧golden。新的runner下载/住宿正常流程仍以当前任务实际结果为准。


## 已验证的地图索引与默认palette批次

`export_development.py --world-inventory <report> --base-apk <reviewed-apk>`复用原Reader枚举物理几何表、NPC overlay域、真实出口/服务门口；解码、打包和实际App运行字段分开。相同grid不合并状态ID，未证明使用的尾槽保留UNKNOWN；Reference TMX扩边数据不替代原格网。原E0C3选择/E3A6背景/E438精灵palette及PPU零色镜像已与正常防具/客栈截图核对；只代表静态默认palette，不宣称脚本光照变体已核。

F0原KVM正常三店→客栈取消/确认/离店→真实伤后药草与冷启已运行，实际发布同源同产物与公网完整字节复核成功。后续全场景按需缓存/换图预检本地单元通过，新增旧档保护App方法待runner，不能把编译当实际运行。完整artifact大于传输上限且blob/文件URL返回403时，原inspect有界服务画面提取已执行成功：旧收集器给town01画面加touch-ux-前缀，按真实文件名筛选后取回7张原App服务PNG并人工检查。先前筛选零图片和未保留F0原片仍是限制；后续必须核artifact实际文件，不能将测试PASS当录像已保留。不要无限重试或修改代理。


## 已验证的共享状态与局部敌群取证

- 原库存读取E685/E68D和各类本地ID，批次生成服务目录；名字、物品使用和装备槽位分别核，不将Reference商品ID直接当ROM ID。旧商品和一次性奖励仍走统一状态。
- 原版敌图形依赖所属遇敌区域的CHR银行。只改敌人source而保留另一地区的环境会产生黑屏/乱码；改用原完整zone/group loader后成功，现probe-world-enemy.lua用限定WORLD_ENEMY_ZONE/GROUP和FENGSHEN_ROOT记录受控输入，不改ROM，不算正常路线。参数范围以脚本为准。
- 原export_development.observed_graphic_recipe从明确矩形回查每个16字节ROM图块；完整图块/RGBA一致后，原scoped_observed_graphic在CI重建。淡出/多palette/部分匹配必须拒绝。原整组加载仅证明已注明静态姿态，不升级全动画或名称。
- 牛黄丸局部实验复用probe-town02-herb.lua的可选HERB_ITEM_ID/OUTPUT_FOLDER；默认药草行为不变。原状态/数量写hook定位成功：确认先扣一次，解毒时原例程再查库存扣一次；仅1份的再次查找没有数量可扣。正常购买/路线与受控异常状态实验分别留记录，不混作正常玩耍证明。
- 历史F0 golden独立固定于ci/golden-world-f0-content.json，避免新基底使旧全黑回归预期失真；本地显式FENGSHEN_WORLD_F0_BASE_APK与当前FENGSHEN_CONTENT_BASE_APK各验来源/hash。新的runner下载与扩展App方法仍以当前任务实际结果为准。

## 已验证的世界输入缓存与成长表边界

- `export_development.py --world-cache private-derived/world-scene-cache`在原Reader/图集配方上恢复物理几何与默认图集，输入固定ROM、生成器及palette hash；第二次实际命中缓存。输出仅允许既有忽略的私有目录，公开artifacts输出实际被拒绝。175几何/38去重图集是静态输入恢复，不是175地图已打包或正常可达；灯光和事件状态另核。
- NPC坐标先按原`cell*16+120`解码，再核当前室内范围。编号23上下文有真实记录，不能当空哨兵删除；额外NPC overlay出现条件仍需原状态分派。`test_world_inventory.py`实际验证已知店员坐标和未决overlay记录保留。
- 同一角色成长表批次用原`extract_growth_candidates`及`extend_world_growth`，保留已核低等级行；等级上限必须有升级例程判断，不只看表长度。`test_world_growth_export.py`已本地核对原表、高等级静态数据、旧行不变及错误角色/上限/span拒绝。其他角色不得借用哪吒表；高等级正常App未执行时保持NOT_RUN。
- 泛化加载器用稳定ID、唯一slot、原字段范围和已实现行为门禁，旧golden断言继续保留；移除样本数量硬限制不等于全部敌人规则已实现。上述新批次当前只有本地验证，原runner/App结果以当前任务记录为准。


## 已验证的共享商店与分层地形局部接续

- `test_world_west_services_export.py`从可信基底两次导出，核未变媒体逐字节一致、caller库存/价格、单手合法槽位/贡献/命中、遇敌组及manifest；同一室内须保留旧caller绑定，不因新增村庄覆盖原商店。原武器选择器的双手列表不能直接当单手候选。
- 原场景tileset4以原阶梯/上下层状态推进；`OriginalTerrain`纯决策、World移动和SaveSnapshot共享mode，原观察路线正反向及失败回滚JVM已执行。不能把该profile套所有tileset，不能把静态图可达当真实动态机关已恢复。
- 原observed_graphic_recipe对齐的是每块8×8图块，原OAM画面起点可为非8倍数；明确起点/尺寸后逐块ROM和RGBA必须全匹配。实际装备预览起点y=49全匹配，强行切到y=48曾失败，不放宽像素校验。
- 默认遇敌表根为EE47，EE60只是某地图的叶项；剧情sourceType经原映射才是enemyID。敌人掉落稳定ID必须在最终物品定义中存在；未知使用效果不因补掉落而编造。上述局部新Android流程以实际runner结果为准，本地编译不是App验收。

## 已验证的原始记录与事件阶段区分

- 原遇敌区条目是4字节（组表指针与图形上下文）；按原count完整批次读，不能用2字节步长。`extract_encounter_groups`保留全部raw行；原某组重复同一slot/source时，实际loader只初始化两实例、调度/奖励也按两slot处理。只有已核的完全相同重复行可生成唯一运行实例；原三条`sourceEntities`及计数仍保留。冲突source重复不能推断后写覆盖。原CPU与`test_world_north_palace_export.py`已验证，正常Android另验。
- 宝箱grant子程序不证明之前没有剧情：应核坐标触发、实际映射bank、战斗返回与对白完成分别写哪些flag。原主循环切8K bank46的事件路径和module10同号helper不能混为同一现场；已有分阶段CPU与正常输入证据分别保留。原独立宝箱/可复用道具由`WorldItems`在统一状态提出一次事务，保存失败回滚；相关JVM和仪器编译已执行，App新流程未执行时不能宣称冷启通过。
- 旧内容golden另存原pin；新目标只修已确认差异，未变媒体逐字节复用。名称依正常原屏修正时不顺带改价格、贡献或物品效果；原Reference名称只能保持PROVISIONAL。相关世界导出方法已实际执行；旧基底环境变量须分别指定，不能因缺历史输入删golden。


## 已验证的加入角色与局部内容恢复

原export_world_from_base按独立initialization/owner成长/倍率指针及装备列表span校验additionalCharacters；事件最终页用原StoryFollowup一次提案加入、换图和落flag。world-east-palace-script.json、world-party-xiaolongnv.json及world-two-party.json分别保存证据范围。不同遇敌矩形不扩成整图默认区；未恢复区域仍按原矩形明确标记。既有物品重复时用固定baseDefinitionSha256复用，不能重复追加或静默覆盖。

原ci_apk.restore的两个干净临时destination已实际生成相同109文件；test_world_east_export.py覆盖原媒体字节、目标manifest和错入队/成长/倍率/剧情/库存定义拒绝。以上是本地内容与纯规则结果，新角色正常App/覆盖升级/冷启必须另运行，不把编译写PASS。

历史缺可选字段 fixture 若刻意移除 combat.json，必须同时移除后续 scoped scriptedActor；真实loader的孤立演员校验保留。否则测试构造不一致的混合版本而报错。该修正已编译，真实ContentTest复验以当前run为准。

当前共享 normalWorldStoryContinuation 触控/BFS驱动的东海、逐页对白、两角色及外部冷启入口已编译，尚待runner正常流程，未列为已成功运行方法。

## 已验证的地狱敌群输入复用

地图23各矩形必须按原区独立保留；新增状态行为需要同字节分派、状态优先级、无合法指令时推进及战后清理证据，不能只放宽enemySupported。world-status-bit8.json保留限定span与受控CPU索引；私有CPU样本依赖正常原版RAM，未声明公开干净环境可重建该实验。

原export_from_base/ci_apk.restore已在两个干净目录重建本批114文件一致；test_world_hell_encounters_export.py实际验证完整原组、原媒体/库存定义、错状态/漏命令推进span/错掉落与价格拒绝。引用同类Reference数值匹配只确定暂名，多个同价候选保留未知，不据此推断药效。数据导出与JVM/仪器编译不等于新分区正常App通过。


## 已验证的后续村庄批次与加载标签

原商店catalog按caller批量取完整库存，已有掉落物品缺商价时用existingItemPriceUpdates固定原baseDefinitionSha256及真实priceSource，仅补原不存在的buy/sell字段；ID、名称、未知使用效果和既有数据保持。test_world_village2_export.py本地验证错价/错指针/错原定义hash/漏库存/错角色槽位拒绝、媒体不变与两次确定性导出；原ci_apk.restore两个干净目录重建一致。所有items（不只equipment）须检查原loader接受的confidence词汇；名字暂定使用PROVISIONAL_REFERENCE词汇，verifiedFields单独记录，不自造复合confidence或放宽loader。

共享室内落点仍按具体caller正常场景观察，不能把其他村庄的12,12机械套为新村庄12,14。原版续跑服务取证与独立门口分支分开索引，不声称单段连续录像。新Android正常服务/地狱/双人冷启方法尚待runner，不列为成功方法。

原前置ContentTest已在runner真实暴露Content.kt:219全物品来源检查；只检查装备曾遗漏未知药品的复合标签导致加载失败。保留校验，错误带稳定物品ID；完整词汇回归与新App实际结果分开记录。

检查累计当前provenance的items与可信基底两处既有定义，不能只查基底：后续村庄曾重复追加西海已有药品12。批次导出拒绝同批重复稳定ID，既有已核名称整条复用，避免associateBy静默覆盖；新增单价补齐不意味着新增物品或效果。

- 成长行的原7字节字段是HP uint16、其他五项uint8；不能对全行统一255上限。实际小龙女等级59行HP256曾使已签名内容在loader失败，修正后的共用纯校验及原行/溢出测试已本地运行；最终App以当前run验收为准。


## 已验证的第一殿局部配方与状态区分

- 原NPC图形用正常淡入稳定帧/OAM实际可见slot及PPU tile raw hash定位；不能用NPC记录下标机械推OAM。FCEUX原图256×224裁去顶部8行，截图坐标须实际搜索校准；原palette RGB按同正常帧核验，透明洞保留背景、不透明黑另验。NPC189/190原图 opaque 像素逐一匹配后由既有ROM recipe导出；失败的ppu.writebyte方法此环境不存在，不重复。
- 原脚本战斗sourceEntity来自执行的事件分派，不是NPC外观ID；胜后原flag写入时机与物理门对象分开。原map70出口扫描无条件、不可见NPC碰撞被flag4移除，采用SceneBarrier只删对应动态cell，不能凭剧情猜MapExit条件。原正常接近/对话与受控BossHP1奖励实验明确分开。
- 原576 CPU地形矩阵覆盖当前地图实际class集合，source23垂直分支优先于目标墙；原TSV作为独立JVM预期，不能把实现再复制为预期。原严格restore两干净目录/世界导出及JVM方法已本地执行；新Android第一殿正常流程尚待runner，不列为已成功方法。
- 原版多角色菜单需分清角色→槽位→动作→候选；用药为类别→物品→目标，第三次A后DOWN才能换小龙女。旧驱动在列表RIGHT实际改药品；修正真实按键及库存/目标观察，不修改游戏药效掩盖驱动问题。

- 新批次测试加入Windows原CI时，也检查测试内每个中文JSON读取。曾有仅一个legacy read_text遗漏UTF-8使门禁失败；改显式编码后在隔离Path.open默认cp1252的原8方法实际通过。不能仅在Linux通过就声称已排除Windows默认编码问题。

- 出口静态校验必须与当前剧情门碰撞分开：原门对象占出口曾使整包在ContentLoader拒绝。先严格解析已核SceneBarrier，用仅移除此类对象的几何视图校验出口；运行sceneForState仍须真实flag，普通NPC和墙不能因此通过。validExitPlacement的合法门/墙/其他对象/越界/不改变原状态JVM测试及仪器编译已实际运行；新App验收以当前CI为准。

- 新行为先独立核实际CPU分派、同字节随机、目标范围与基础伤害，再扩原OpeningBattle与extractor。world-enemy-behavior1.json及444行独立CPU预期已用于5专项JVM/3导出证据方法；直接A956扣血捕获在死亡flag分派前，不能混作整回合状态。私有CPU实验仍依赖隔离原版存档/mapper，不能声称公开干净环境可复现所有原实验；正常Android另验。

- 原StoryBattleDefinition.pendingDialogue返回当前对白ID，不是待执行状态查询；完成胜后检查真实pendingFlag，保留重复完成无副作用及门/存档断言。错误null期望曾使实际ContentTest加载后失败；修正已本地编译，新的正常App结果须另验。

- 后续敌人能力使用原behavior分派批次核证：world-enemy-single-special.json/3148原CPU结果验证2/4单目标、与原8EB2相同字节选择及伤害；world-enemy-status16.json/2575原CPU结果验证6的阈值10、特殊miss不落物理、状态优先级与原全队10败判。对应7个Python证据方法与229全JVM方法、仪器编译实际通过。原始CPU实验需要隔离原版mapper/存档，公开派生TSV不是原始资源；此树正常App/新地图尚未执行，不写可玩PASS。

- 原冰行为普通公式与boss表是两域：用world-enemy-ice-identities.json/1700原CPU派生预期核17身份，再允许后段177表内身份进入原提取器。3个证据测试、230全JVM方法与仪器编译实际通过；来源span可保留旧recipe描述但地址/hash必须相同。正常App/新图需另验，HP-positive死亡mask仅算术fixture不当合法目标。

- 同类殿事件按原NPC末4字节及原inline stage表批次绑定：world-hell-hall-batch-script.json/4096原CPU期望已用于3项Python与纯旗标JVM测试，复用StoryBattleDefinition/SceneBarrier而非复制运行引擎。NPC246 gate原mask随地图不同，失败分支和加载过滤也需核；CPU及受控坐标图形不等于正常路线。后段八Boss原全组104块各全匹配配方成功；读取高地址battleHP须从SRAM6800起始dump取offset186，不读取短RAM空slice。原NPCcamera/screen定位尝试失败并保留；后续按真实+4/+6 world坐标取10种可见单帧，198透明不能提升为完整图。不宣称自动隐式skill匹配。

- 世界NPC坐标与对白字段先对原14字节及22字节actor记录核对：+2/+3不是屏幕坐标；以原+4/+6 world坐标/OAM、PPU每个16字节CHR和opaque像素重建既有recipe，组合部件分别留ID。静态RGB表与FCEUX实录RGB可有差异，沿用实际每个PPU值一致的可见像素，未用颜色不冒称验证；透明全块拒绝为完整图。10个recipe复建/错身份/错像素/透明拒绝3方法已实跑。原9B在874C是遮挡，不能直接转bool免遇敌；6840原碰撞+480原遇敌预期、233JVM及106世界Python实际通过。原私有CPU存档依赖和Android NOT_RUN限制保留。


- 普通宝箱沿用WorldItems与InventoryCapacity，原14字节记录需独立验证类别/ID/flag及grant/容量/开箱动画span；受控CPU数量含已使用位，不能把原字节129当129份。test_world_chest_grants.py与HallChestGrantTest已在本地验证72原CPU预期、失败不落flag、满栏已有栈可增、重复不结算。新地图正常App/冷启另验；箱子取物不证明其物品使用效果。


- 多地图批次复用原export_from_base，地图/独立出口/NPC/完整遇敌组/门flag分别核来源；旧目标pin留golden，基底和目标hash不混用。test_world_hall_batch_export.py、本地113世界方法及两全新ci_apk.restore目录已运行：旧媒体字节不变、错数量/物品/门/交谈/姿态/少组拒绝。受控Original/仪器编译不等于正常Android路线或冷启；必要App门禁另外执行。原ROM当场对白阶段6E1可能已变为UI actor，不能拿它替代进入战斗/原NPC记录身份。


- 原完成步状态先毒后特定地图效果：world-hell-field67-step.json与3840完整CPU输出（含原BE1A请求队列）已用于Field67StepTest/原Status测试，保持step(List) ABI，GameView传CompletedStep.mapId而不是换图后的world.mapId。仅保护RAM边界核实不授权物品用法；正常路线/音频/Android另验。新1 Python及定向10 JVM、全238 JVM与仪器编译实际成功，不据此写新图已可玩。
- 原正常流程准备耗尽时先查实际成长阈值、训练敌群奖励与保存索引。北海前开局弱敌区3000步未达到12级，保留失败，不注入EXP/更改等级目标；改走已支持海域正常训练的驱动已编译，App复测未执行。进度每64步保留实际快照有助诊断，不能只增大预算无限重试。

- 原CPU派生TSV的字节hash不能依赖宿主Git默认换行；Windows core.autocrlf曾使严格恢复拒绝。仓库*.tsv固定text eol=lf，test_world_evidence_checkout.py在隔离Git仓库启用autocrlf真实checkout后逐字节比原表及blob已成功；保持所有SHA门禁，不通过读取时静默忽略差异来放宽。修复后的Windows runner仍须实际执行。

- 新普通箱接入时，Loader能力检查与WorldItems原交易复用同一supportsTreasure；不继续沿用旧“所有宝箱都是special11”的样本假设，也不删除amount/稳定ID/maxCount/flag和categoryGrant来源门禁。旧定海珠无categoryGrant仍走原专用规则；72原CPU grant边界和238全JVM本地通过，仪器编译通过。真实App加载修复需原候选ContentTest及正常开箱另验。

- 同类剧情NPC索引不保证相同：某殿首条是守卫而王在idx2。测试及正常驱动应按当前地图NPC和storyBattles的稳定npcId唯一绑定，不硬编码idx1；原记录、奖励和门旗标断言保留。实际Content失败说明需查绑定，不能改ROM数据迁就测试；新驱动编译/实际runner结果分开记录。

- 新侧室入口/返回应逐条读取原表；一个spawn不等于只有一个入口。完整region/gate仍查EE47/ED87，室内不能推断免遇敌；图形/文本别名和动态NPC状态分别留证。test_world_seventh_side_export.py与864独立原CPU参数及真实LF checkout已本地成功，正常App进出/遭遇另验。


## 已验证的末段局部导出与脚本边界

- 原export_from_base/ci_apk.restore已实际恢复末殿与非战斗场景剧情；world-final-hall-content和world-rebirth-script只扩展原schema/StoryContinuation。新局部导出测试验证重复生成、不变媒体字节、原门/单向出口/对白及拒绝猜测落点、免费中毒步、额外奖励和错误图形。原CPU捕获的PRG映射必须逐8KiB核目标ROM，不能把当前逻辑module一次铺满CPU地址。
- 真实Windows checkout用*.tsv text eol=lf，test_world_evidence_checkout实际比较checkout和Git blob原字节；不归一化或关闭hash。新scene的阶段保存/取消/原正常路线方法仅编译，App未实跑前保持待核；详见当前任务，不把导出/JVM当正常游玩。
