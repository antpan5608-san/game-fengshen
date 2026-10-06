---
name: fengshen-content-iteration
description: 在Fengshen当前授权的内容迭代或地图批量接入中复用可信APK基底、原巡检与CI验证；不自行扩大全量研究，不用于纯规划或其他项目。
---

# 适用与输入

先读AGENTS.md及docs/current-task.md；当前任务/版本/欠账不保存在skill。
需要本轮范围、已审核基底构建run_id与APK SHA、可信receipt及内容pin。
资源获取按AGENTS.md最新授权；不含凭据，不扩展任务或发布范围。
当前任务若为本地迁移交接，仅保存公开成果与资源索引；不调用下列inspect/构建/发布触发命令，不继续游戏开发。已运行CI保留，私有原始输入通过安全入口另行迁出。

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
默认个人交付使用当前任务授权的PERSONAL_TEST级别：相关快速回归、真实短冒烟（来源明确的隔离fixture可用）、存档/外部冷启/旧档升级和严格产物校验；完整剧情/真机/声音交用户人工验收并标PENDING。无短冒烟只留明确首次启动待检候选，不能冒称运行PASS或无说明覆盖稳定对象；纯文档/skill或工具变化不发布游戏。
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

清单的打包集合必须来自当前target，而不是较小的恢复输入：`inventory_target_from_base`先核原APK/signature/hash，再调用原`export_from_base`核target manifest。此CLI已在Linux既有JDK/ANDROID_SDK_ROOT实际运行，`test_world_hell_scope`验证当前target集合/文件数及错误基底拒绝，`test_world_inventory`仍要求未知分母和App NOT_RUN。`packageSource`标REPRODUCIBLE_TARGET_EXPORT_NOT_RELEASE，不能当成已发布APK；仅ROM服务枚举也不能把后来已接的caller硬编码成NOT_IMPLEMENTED。

服务同样从目标`scene.serviceBindings`与实际shops/inns/clinics交叉引用，原inventory入口已生成caller/室内/定义绑定并验证错引用拒绝。PACKAGED不推断App业务通过或已发布。新场景局部复现可在隔离git archive目录复用匹配ROM缓存/原SDK，原`ci_apk.restore`→wrapper `assembleDebug`→`ci.content`核实际包；已实际成功，debug签名产物只证明干净构建/内容，不替代生产签名或KVM运行。

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

- 原exit表一行不保证当前状态可站立：export_world_from_base在签名前调用validate_world_exit_geometry，镜像原loader地形/已核可移除actor/区域规则。真实实体墙行移至原provenance的unresolvedExitRows，保留hash和未知上下文，不放宽collision。test_world_final_hall_export实际验证拒绝两条墙行、仍能确定性生成；EncounterRect原语义为左/上排除、右/下包含。加载器仍独立复查，导出/本地编译不替代App门禁。

- 具体原版证据类别不能代替项目source.confidence枚举。原ci_apk.restore和receipt现在实际提前核PROVISIONAL_REFERENCE/GAMEPLAY_VERIFIED；originalEvidenceKind保留受控菜单/CPU等验证范围，分类不等于正常游玩。test_ci_apk.py已实际验证错误分类虽hash正确也拒绝，旧assets不变，正确分类不改具体证据；历史golden只用于复现比较，不能作为通过App门禁的基底。


## 已验证的配方检查点复用

- 小批新增定义可用原export_from_base的baseExport递归复现固定父pin/provenance；父配方与新增配方始终用同一已审核原APK，不以未通过候选APK恢复素材。实际test_world_village3_export验证父JSON原字节/hash、中间manifest、不同基底/循环拒绝和旧媒体逐字节不变，ci_apk.restore严格恢复成功。不是第二套导入器或跳过签名。
- 父pin/来源JSON和CPU TSV固定text eol=lf；test_world_evidence_checkout在隔离Git仓库autocrlf=true实际checkout比较blob/文件hash通过。不读取时归一化，不全库强制renormalize用户修改。
- 旧批次负例从该批次固定golden读完整定义；新增量缺旧Boss不代表生产缺Boss。实际洞窟/第一殿定向重跑保留原反向断言并通过，首轮错误仍在任务记录。
- 静态NPC只有原真实步行进入可见区后才提取OAM/PPU/CHR；写未知actor字段或仅改坐标曾无法激活渲染。实际可见部件按原OAM flipX/flipY复建、核RGBA；透明/错误翻转拒绝。受控起始源不能改称正常新游戏。新App流程编译后还须同候选runner实际验证。

## 已验证的医馆局部来源与回归（正常App待验）

- 同名室内不保证同碰撞profile/palette；真实医馆控制按键证明床边class5可以横移，套村庄方向规则曾被原导出guard拒绝。只核当前地图实际tileset/移动/墙/NPC，再形成局部定义；不能移除guard或泛化未知场景。默认palette须逐32项核实际淡入完成结果，不强制所有零色相同。
- 菜单必须执行到真实收费/状态写入阶段；只到价格提示未治疗，第三项实际退出。独立原向量用效果确认时RAM，不能把后续通用菜单清理混作药效。活跃CPU逐bank与匹配ROM比较，不能用对白后的切回bank。world-clinic-rules记录受控范围/原向量SHA，ClinicRevivalTest/ClinicCareTest与test_world_clinic_export已实际通过；不宣称正常死队友复活已运行。
- 递归父配方新增可选schema只在非空时写入，避免空clinics改变已审核历史manifest。真实autocrlf checkout已扩验父链JSON/医馆TSV，严格空目录ci_apk.restore与本地目标逐字节一致。正常临床UI和患者分项尚待原runner；无真实患者记NOT_RUN，不为验收制造病症。

- 新增父配方测试也必须逐处显式UTF-8读取；真实Windows村3setUp曾遗漏导致0方法执行。四处修复后，在隔离Path.open缺省cp1252下实际执行村3/医馆八方法成功，保留原hash/反向断言。Linux全量通过不能代替Windows默认编码检查。


- 大陆桥不能套村庄方向profile：原foot source15限制左右、16限制上下，target通行另核。world-continent-bridges固定真实按键与144原CPU输出，ContinentBridgeTest和test_world_continent_bridge_export已实际验证；同批保留完整原区16十九组和三个原整组图形，不仅开放路而漏遇敌。现有probe-world-enemy只增已核zone/count参数与显式退出，旧路径不变。原actor0406/0408已经是世界坐标，重复加camera曾失败；旧长按回村源实际165而非160，需核源RAM，失败不能提升为路线证据。
- 新局部配方的旧测试读固定父golden，原ci_apk.restore空目录逐字节核195文件成功；140全世界Python/257 JVM/真实autocrlf checkout已执行。新增正常桥/自然遇敌驱动仅编译，App和声音待原同产物runner，不把新内容静态校验写成正常可玩。


- 静态可走格不能替代原actor条件：map16六record的0:A973加载过滤1536原CPU向量已用于ContinentBarrierTest和局部导出；复用SceneBarrier，不设旗标/移除原挡路对象。某对象位于桥类别16，须保留已核foot规则而非只认0/2。test_world_continent_barrier_export首轮旧scene省略空dynamicObjectCells导致读取错误，改合法空默认并保留六对象完整断言；143全世界Python/258 JVM/真实LF checkout/空目录恢复已成功。App条件路线仍待同产物runner。
- 原ci_apk.py verify需显式--output路径；只漏该参数时在写receipt失败，不能称签名不符。实际取回同源不可变签名artifact后，核receipt来源/run/完整SHA，再在对应冻结内容pin树verify成功；不把签名构建或字节验包当正常运行/发布。


## 已验证的森林分派校正

- 原PHA/PHA/RTS分派表存目标地址减一，不能在表值处直接模拟后就宣布所有类别可走。forest101实际树格按键被阻挡，完整源/目标分派144矩阵验证后复用Scene.sourceEdges；world-forest101-terrain保留原跨度、实际键序列和失败入口解释。树墙、source3横向约束与sprite遮挡分别处理，不将遮挡值当遇敌控制。
- 原完整zone/group loader和observed_graphic_recipe已取得两种森林敌人全部图块/实际RGBA；区域/组数按目标ROM验证后才扩probe-world-enemy的有限计数。局部导出4方法、完整世界回归、JVM/仪器编译、真实autocrlf checkout与ci_apk.restore空assets目录逐字节已运行。restore的destination就是assets目录，不能再附android路径；normalApp/外部冷启须等同候选runner实际结果，不能由编译宣称成功。

- 单个地图的静态寻路失败先核连通组件与原独立出口，不能放宽墙。test_world_forest101_export中的补给返回测试已实际验证海域/洞窟六腿及未消漩涡反例；Content.sceneForState按已有合法flag去除物品目标，离线几何测试必须同样应用它，不能将基底dynamicObjectCells的漩涡误认永久墙。正常多地图App驱动仍须原同产物runner执行，静态PASS不等于正常路线PASS。


## 已验证的本次方向与接触核对

原移动分派$97为本次方向，空闲snapshot的0不证明实际调用参数；原代码钩子与正常按键先定位参数，再复用CPU矩阵。森林target3/7横向原阻挡已用144实际方向组合及按键反例核定，旧证据保留为历史，不替换旧parent hash。新增修正独立局部配方，151世界回归、真实LF checkout、空assets恢复均已运行；App仍需同候选验证。

大陆actor不等于一律实体障碍：原A973过滤后C68A对E7/E8接触分派，D6步行进入，移除或其他walker不进入。probe-world-tree-contact.py已对匹配ROM运行2384案例，派生TSV不含ROM；WorldActorContactTest验证失败换图保留、旗标移除后普通走格与接触不扣完成步。未打包的新入口或四层不能写正常App成功。


- 当前授权可接受明确非阻断历史故障时，诊断投影仍保留status/ERROR/计数；原runtime-summary.mjs的独立--release-assessment只核已经精确review的旧版本下载中断与可信两版hash。19 Node/投影方法、22原审批隔离场景和实际脱敏inspect输入评估已运行；新错误、崩溃、丢档或查询不可用仍拒绝。PowerShell/生产路径未实际执行前保持待核，不将ALLOW_WITH_KNOWN_NON_BLOCKING_ISSUES写NO_ISSUES_OBSERVED。动态例外存ci/runtime-nonblocking-issues.json，不复制进skill。


## 已验证的树内局部接续（正常App仍待验）

复用原export_from_base固定父配方、Reader独立出口、scoped_map_atlas与observed_graphic_recipe，接触入口只解除已核接触对象的那一格探测，不删旗标或打开其余墙。树内原同zone在各地图分别绑定完整组；大陆原遇敌矩形在module11而非同址module0，必须核真实指针/跨度，不能抄区域编号。四层局部导出四方法、完整世界回归、JVM与原ci_apk.restore空assets逐字节已实际执行；正常App入口虽已接原runtime/receipt门禁，不能由编译宣称PASS。

NPC首谈action与一次性seen分开；原action17在对白前写见面旗标，未拥有指定物品不加mapflag或队员。原CPU表和OriginalNpcTalkTest核状态，原真实按键核正文；重复对话不能套统一seen规则。后续teacher选择/礼物容量探针已运行，派生表不证明物品使用或入队。菜单取证长按可能跨层，改成单帧按键后逐屏检查实际阶段；原始ROM/PPU/RAM继续忽略目录。

## 已验证的限定房间及原NPC后处理

- 已提交raw NPC记录须先执行真实首谈/复谈selector再执行action后处理；已置NPC mask会跳过后处理，不能直接从action初值推复谈。`probe-world-teacher-talk.py`已实际校对师父8192、容量6及道童1024 CPU案例；碰撞probe另36案例，不将受控CPU当正常App路线。
- 房间未定义遇敌时核原type/zone字节FF，不从邻接森林继承遇敌。counter交互cell/direction、独立返回、特殊物品16行容量沿原导出/统一状态事务；只取得物品时不要捏造worldUse或价格。局部export测试和原ci_apk.restore空目录215文件严格恢复已运行；新增正常Android房间路径已编译，实际运行仍待同候选门禁。

## 已验证的地府正常路线驱动修正

只用原单向地形和独立出口规划后续十殿，不交换出口猜返程。`test_world_hell_route_driver.py`已实际验证十条下一腿、10/13区自然遇敌邻格及旧服务点不可达反例；正常补给训练放在首个不可返回步行节点前，仍通过原买卖/付费客栈和自然战斗，不添加玩法门槛。首次殿前录屏可用原record_app_audio.py的限定7200秒预算（边界4方法通过）；本次修正实际App仍待原KVM运行，不将拓扑测试当正常通关。

## 已验证的信物入队局部接续（正常App另验）

先核物品dispatch、可复用数量标记、event/script阶段与独立NPC context；完成mapflag不等于NPC移除条件。`probe-world-yang-join.py`已在匹配缓存运行5926 CPU案例；原受控真实按键与初始化字段逐址校对发现strength/敏捷不能按写入顺序猜，初始手部贡献也不能直接套商品表。维持明确差异，不新增第二份存档属性或未核双手事务。

原export_from_base的限定existingItemCapabilityUpdates/既存NPC目标只允许公开provenance所列稳定ID/字段，固定父配方和旧定义hash，未变媒体保持逐字节相同。局部4测试、完整世界166方法、LF实际checkout及ci_apk.restore空目录215文件严格校验已运行。入队复用StoryFollowup/统一commit/save rollback，UI仍选物品后明确执行；三人自然遇敌与冷启正常驱动仅编译，须同候选实际运行才能记PASS，不能拿CPU或导出替代。

- 独立出口落点不是可任意离开的空地：真实runtime曾在23(55,91)固定向下撞55,92墙，原拓扑也核55,93墙。test_world_hell_route_driver新增实际落点西侧离开/返门与54,93↔54,94自然zone8邻格反例，4方法已运行；保留失败run，新正常App须重新审核，不放宽地形。

- 新角色成长配方必须输出ContentLoader实际要求的limitEvidence，不能只有原ROM levelCapSource。签名候选曾在真实Content仪器门禁报JSONException；原extend_world_characters增加来源路径早期拒绝，局部导出测试缺字段反例及原restore已运行。更新旧仪器断言时仍核真实目标/只读双手能力，不吞加载错误；修后实际App须新候选重跑。


## 已验证的共享村庄定向恢复（新App另验）

原NPC记录第3字节不是文本组号；先从真实对话RAM/PPU核文本组，再复用extract_text/原字体图块hash转写。目标ROM两段2048字节font匹配、15段文本decode及三张原OAM/RGBArecipe已执行，Reference不能按未经核对的map/NPC序号套用。action50的原首谈/repeat selector与post-action分开，probe-world-village4.py实际运行576桥矩阵/3584对话案例，保留特殊repeat反例，不用通用NPC-seen新增剧情门槛。

caller村庄复用现有17/18/19/20/22室内、stock/InnStay/Clinic命令，导出器只新增本村桥类别10/11边，不改历史父hash。原ci_apk.restore严格恢复220文件与局部4方法/282 JVM及仪器编译已运行；新增正常UI与冷启驱动未执行前须NOT_RUN，不能将原版/CPU/编译写正常Android可玩。

- 静态可站的NPC邻格未必在当前桥的连通分量；本次村4驱动first邻格13,14反例实际失败，改只读probeFrom方向搜索选14,13，不放宽墙。5局部方法与282 JVM/仪器重新执行、真实LF checkout含新父pin及576/3584派生表通过；正常App仍待同产物门禁。

- 原固定交通不得套门出口免费传送：先用现有Reader/ROM movement stream及实际接触按键核每个状态步、扣血/死亡位置、独立返程。跟踪probe-world-ferry.lua在官方FCEUX/Xvfb有界重跑、受控派生TSV一致；原回放仍受控缓存，不要求迁整目录。复用原局部export验证route/既存稳定对象hash/完整敌群，严格restore和4导出/完整世界回归/LFcheckout已运行。动作由统一状态持久、渲染不结算；JSON/真实正常路线/外部冷启须同候选仪器另验，未执行标NOT_RUN。初轮把整份旧地图JSON固定会漏掉合法的新出口格，应只核有证据的exact delta，禁止借此开放其他墙/海面。

## 已验证的原NPC与特殊战斗资源定位（正常App另验）

NPC callback表、world坐标event表和正文赠物selector是独立命名空间，不能按相同数字推成同一效果。局部teacher/森林/箱配方复用原Reader、已核RTS方向矩阵和既存物品定义；目标ROM受控按键/原字体hash与CPU表可复用，不推成正常Android通关。秘宝命令需核原菜单收集、目标、数量/used位及调度执行时机，不能套地图药草或在选择时先置效果。定向JVM/导出/实际LF checkout已执行；新App路径仅编译时继续NOT_RUN。

需要证明干净目标恢复时直接调用现有ci_apk.restore(source,destination=空目录)，核完整manifest与文件集合。CLI restore的--output不会改变assets目的地；曾误用后已通过显式destination重跑纠正，不把同assets覆盖写成空目录验证。


- 新NPC堵路先核完整原加载状态，不能仅用静态base context判原路线不可达。原map79加载按真实party数与胜标切换map163 context219；原NPC指针、位置/文本及1284 CPU边界已执行，受控原版房内按键走到师父成功。公开攻略只提供定位线索，推荐对话不成为虚构前置；同一variant必须同时影响碰撞、绘制和交谈。新App流程仍须同候选另验，原版/CPU不代替Android通过。

## 已验证的后续村庄资源复用

按真实caller一次批次复用库存/价目、共享室内与医生，不按村庄复制地图。仅相同tileset0复用已核576条桥方向矩阵，分别固定当前格网、独立入口/返回记录、原NPC和当前文字组；不要把普通EXIT套成另一村的EDGE。用已观察活跃字体glyph像素hash匹配既有字形，只定向补缺字。原OAM图形须逐不透明像素匹配且唯一；按真实按键步行取得图形，不依赖写camera后未刷新的假截图。

FCEUX gui.savescreenshotas调度下一帧；调用后先推进一帧再切source/save，已在原防具菜单观察到错误图/修后原商品画面。原版/受控资源不算Android正常流程。本轮现有导出器的局部父pin、旧媒体字节、全新空目录严格restore、来源enum和拓扑拒绝测试实际通过；新增商品未核名字时沿用PROVISIONAL_REFERENCE并明确nameConfidence UNKNOWN，不发明名称或关闭来源检查。

## 已验证的局部脚本与离队接续（Android待当前runner）

- 原ROM字面量先区分十六进制与地图十进制ID；仅以实际map dispatcher/coordinate table确认事件。已因$87误读87调查错误事件，保留失败，纠正后只核所需script流/消息与结算。
- 复用probe-world-cave87-state.py运行未改ROM CPU的胜后、保留离队记录/战斗投影/出口恢复和钱箱边界；受控HP1胜利不是正常Boss证据。公开仅受控数值TSV/provenance；ROM/PPU/原存档继续忽略。
- 原export_from_base父pin固定、scoped ROM tile/glyph及ci_apk.restore在空目录严格核验新场景；test_world_cave87_export.py已执行两次一致/旧媒体不变/错误坐标、对白、离队身份、额外奖励、像素与CPU表拒绝。计算新目标hash仅用于初次生成审查，随后必须完整严格重生成/restore，不能关最终目标pin校验。
- 完整Boss矩形须包含实际边缘像素；过小矩形即便图块全匹配也可能裁图。普通敌框不可含玩家红点造成混palette。ROM静态初帧组合明确PROVISIONAL，未OAM观察不得标成截图匹配或正常App画面。
- 无普通对白的钱箱须按已证身份/金额/旗标加载，不能因复用普通NPC校验导致新包全局拒绝。JVM/导出/编译通过后仍必须原runtime验证正常路线、存档及真实画面；本局部Android目前未运行。

- 空目录恢复必须调用原ci_apk.restore(source,destination=...)：当前CLI restore忽略--output（该参数只供verify回执），曾错误认为写入指定临时目录；随后在新TemporaryDirectory/assets实际复核完整目标文件/hash成功。不是更换导入器或关闭pin。仅编译正常驱动不代表App正常胜利/外部冷启已执行。

## 已验证的共享村庄局部批次

- 复用world-village-batch-resources.json及原export_from_base：固定实际几何、caller价目/商品表、桥矩阵、当前font与action selector，只加新增定义；旧媒体逐字节复用。源NPC的四个1KiB CHR银行可能不连续，不以4KiB整段搜索失败推断资源不存在。静态frame未匹配OAM时标PROVISIONAL，不写原版运行画面已验证。
- 原来源enum仍只接受PROVISIONAL_REFERENCE/GAMEPLAY_VERIFIED；实际有id/价格来源但名字未解时保留nameConfidence UNKNOWN/referenceKind，不把UNKNOWN整个对象塞入已存在加载契约，也不放宽校验。局部四方法、6144对白/9容量原CPU、空目录ci_apk.restore及JVM/仪器编译已运行；正常App仍须当前候选原runner。
- 精简current-task必须保留独立task_id行，check-runtime.ps1先解析该字段；历史正文归档而非删除。遗漏曾使inspect查询前失败，修后真实原inspect成功，不能把查询前失败写健康。

## 已验证的局部光照和赠物接续

- 原probe-world-night8.py复用既有py65 call，核当前目标选择器、重复使用数量、before-text赠物和实际forest class2完整分派；probe-world-tree-chests.py的--map-id只增加已取证调用域，仍执行相同原库存/成功flag路径。已在当前Linux缓存匹配ROM执行；各TSV与来源跨度受原export_development严格校验，原ROM/PPU/回放不公开。
- 原validate_world_night8_resources/export_from_base的局部图集使用同一metatile/CHR，仅改变原palette；旧媒体不变。test_world_night8_export.py、NightLightTest和实际空目录ci_apk.restore已通过。低层CPU不能证明菜单持有/目标，须结合真实按键实验；原退出再入洞实验将临时照明与永久ownership/used区分。
- 原结构出口可能落全墙且无可达邻格。先核真实grid/源记录，保留inactive来源、不开放假墙；初轮路径断言失败曾发现此问题。Android JSONObject在普通JVM为stub，真实JSON冷启断言放原ContentTest仪器，不把该编译写运行PASS。
- 新正常光照/赠物/冷启驱动已编译并接原CI，实际App仍以任务记录为准；编译、受控实验和派生atlas不是正常路线或手机证据。

## 已验证的女王局部取证（正常App待验）

- probe-world-queen117.py继续原py65 call：校完整ROM并执行1536目标/既有marker、1024完成位/context、36原tileset6足行。派生数值TSV及当前探针hash受原export_development的局部validator审核；原回放/PPU仍忽略。字段和源码地址实际验证后复用，不以名称猜绑定目标或凭对白猜赠物。
- 原行动中的画面可能有角色OAM覆盖Boss，导致palette/图块匹配失败。先找同敌稳定指令等待帧；本次原observed_graphic_recipe重建全部120图块及RGBA成功，未扩展成另一导入器。失败瞬时帧保留私有来源限制，不当原静态图形。
- CPU/纯业务/仪器编译只证明局部数据与代码；最新实际inspect脱敏计数单独记录，不借旧版日志给新候选健康背书。局部原CPU/纯逻辑、来源拒绝、原export_from_base重复导出/旧媒体逐字节和全新ci_apk.restore已实际通过；真实Android仍须同产物原runtime，不把编译当运行。

- 原受控位置实验须同步0406/0408与8E/90原坐标字段，边界从前一真实格按键接近，不能在未完成transition上猜返程失败。遗漏曾导致假失败；补齐后原宫殿/内城/世界独立返程实际通过。所有位置/HP fixture仍不是正常路线。
- 同一provenance局部事件按实际action→dispatcher→event/script逐段核，action编号不等于event编号。赠物before-text与文本后完成分开纯proposal和pending存档，原容量/已有物/used位/复谈边界用现有py65 probe和JVM核；静态actor F0属性未解时复用同原身份已知姿势且明确PROVISIONAL，不猜动画。

- NPC帧首字节含F1等未解释属性时不猜palette/翻转；复用现有OAM重建方法，在受控附近位置捕获原RAM/PPU/PNG，由四个实际sprite索引及attribute确定flip，比较全部不透明像素且图案表匹配唯一，再用scoped_observed_graphic固定ROM跨度/RGBA。已实际用于当前局部六NPC；位置fixture不是正常路线，完整走动仍欠账。
- 单actor编号须查真实handler表，不套相邻通用分支；原action41实查CD1A，初误套CB84没有deferred action，失败保留，改查actual dispatcher后CPU对照通过。静态拓扑失败时再比较无NPC组件，只能区分原墙与演员阻挡，不删除墙来让测试变绿；未证明世界连续入口的局部数据不得计正常可玩。


## 已核的连续回归接续边界（整段验收以当前回执为准）

- 原ci/run-town02-runtime.sh的阶段分派经隔离Bash函数执行验证，保留全部原normal recorder/cold-test；tools/runtime_handoff.py只验证/原样搬运App-written JSON，部分阶段不能声明全部PASS。Linux执行`python -m unittest discover -s tests -p test_runtime_handoff.py`实际覆盖同候选/正常与冷启边界、不同run/hash/版本/签名、改数据/缺阶段/真机拒绝和完整分派；此传输fixture不是正常App。
- 实际已完成原北海候选的expected-save、最后normal event及recording冷启前状态已校验一致；原运行后来失败时仍只保留PARTIAL，不从通过局部推断整包可发布。原workflow跨runner App-owned导入、85洞→东海胜后→真实双人村2已实际通过同候选；最终阶段不能从前段通过推断PASS。
- 长时正常准备预算以实际normal-index的获胜数/EXP/原成长门槛和耗时定位，修限定驱动预算而不改玩家等级、遇敌/价格/奖励或删断言。曾355正常胜利后仍未到原目标等级，失败不是崩溃证据。新预算是否足够继续以实际App结果为准，未知不写PASS。

- 隔离shell分派fixture在Linux实际通过，Windows默认bash执行曾退出1且未展示stderr。现在源码显式定位已有Git Bash，缺失明确拒绝，不调用WSL或安装平台；定位正反例本地已执行，原Windows runner实际门禁已通过；其成功不等于Android路线通过。失败须保留stdout/stderr摘要，不能跳过fixture或冒充App崩溃。

## 已执行的阶段收敛检查（App结果另核）

将已实现、已打包、同候选App已验证、已发布分开记。阶段候选先冻结真实依赖/终点/测试集合，以原golden配方和局部已有服务复用导出；原ci_apk.restore在全新临时目录严格验120文件和原签名基底已执行，不手删素材或放宽hash。更远配方/源码保留开发线，不能整个后续内容照包却省其验收。

失败先按实际错误分BUILD_ENV/TEST_HARNESS/GAME_BUG/CONTENT_GAP/ENV_LIMIT；测试基底路径使用runner已有FENGSHEN_CONTENT_BASE_APK，硬编码/workspace在Windows实际失败。先执行相关fixture/原审批回归/仪器编译，再跑最终同候选长流程；范围外训练耗时不作为阶段前置，更不成为玩家门槛。

原runtime_handoff只搬真实正常JSON，scope依赖清单由内容pin固定SHA，原三job、同源/同签名/同hash/reviewer保留。全开发29条正常路径仍保留；R1分派和内容局部门禁已本地执行，长路线用于稳定里程碑/核心规则大改/特定故障；个人短冒烟与完整验收分别记录，未执行长测不写PASS。修复交付后同步回既有开发线，避免从头重做。

- 长路线无奖励重进比较前，先定位normal-index中的合法补给：真实用药可改变HP/库存，比较点须在补给后；原两格室内重进使用普通触控，不混入自动补给field helper。半血区可能再次合法用药，不能假设一次恢复就足够。实际索引46HP来源逐值/SHA核对，新增4HP明确隔离边界，保留全部角色/物品/钱/flag断言；编译/相关fixture及原Android两例隔离复现、正常北宫重进已通过；它们不替代新候选整段主线。正常/cold索引与截图分名防覆盖，医疗分名实际路径仍待验。

- 跨runner交接曾仅root推送/hash成功，但App读取实际EACCES；不能把主机读回当App可读。原runtime_handoff的来源/cold/hash验证继续保留。新增App-owned字节写入/读回及64KiB/文件名边界已通过Linux主机35相关方法、5scope方法和仪器编译；原生存储探针、后两runner的App-owned导入已实际通过；85洞/东海/村2正常和cold同候选接续通过，末段仍以当前结果为准。探针只搬隔离历史snapshot原值、不启动或restore GameState，不计正常流程。

- 实际Windows长分派fixture通过Git Bash -c传递时末尾两fi未到达，报unexpected EOF；本地块为8007字符，不能以Linux -c通过推断Windows参数传输可靠。改在隔离临时目录写UTF-8/LF脚本文件，以原Git Bash执行同一完整分派与断言；本地及原Windows门禁已实际通过，不安装另一Bash或删范围外原测试。

- App-owned存储探针及文件分派已经原Windows构建/实际AOSP执行成功；实际跨runner导入及世界中段已通过，最终整体以完整回执为准。正常旅程失败也可能是合法战败：先读真实起点HP/等级/库存与采购索引。本次满药仍在低等级多敌群战败，改用同候选已有正常准备检查点串联可选回归，不再重复练级；不改玩家规则或建立剧情等级锁。原29路径及cold保留，提前隔离北向复现及同候选准备后的北向/村1正常和cold已实际通过，独立分支不合并奖励。历史正常fixture只用于有源局部复现，原字节/SHA/来源另记在test资产；正常主线必须本次新游戏生成同候选状态。

- 共享室内返程会落在原门口trigger；BFS目标等于当前位置时不会发输入，不能当再次进店。实际医疗首店/取消/返程已通过，第二次入店断言失败属于驱动遗漏离门步骤；新增普通合法一步离门再返回，原价格/角色/存档断言保持。此修正与有源正常双人fixture的提前Activity重启回归已在实际AVD通过；Activity重启不冒称外部force-stop冷启。提前局部复现须用原IsolatedGameTestCase还原进入前偏好，不能像外部cold recorder一样保留fixture：实际保留已装备长剑的源污染下一小刀回归，产生测试!! NPE。原run_test仅此局部复现传false，真实升级/外部录屏保留不变；该局部false还原及其后原装备用例已由同一原AVD实际通过；整包仍必须取完整同候选回执。

- 阶段冻结前，用当前任务的真实inspect摘要执行原Node --release-assessment；仅inspect成功不足以证明发布决策可用。曾阶段task_id已改而非阻断issue文件仍绑定父任务，原门禁实际拒绝。修复只同步当前taskId，旧错误范围/时间/hash/count/UNCONFIRMED保持；实际摘要与原13个决策正反例通过，新回归核当前任务字段。冻结后必须新同源候选，不能修改main后发布旧APK。

- 连续双人路线的失败先逐人检查正常source status/HP与补给目标。曾驱动只解毒首人，第二人逛店自然毒损到低HP，原客栈排除该状态；这是已定位的驱动遗漏，不能改客栈为免费解除/复活。复用既有全队补给，提前以已核正常/外部cold的原值复现整段村2；新fixture和正常主线分label且恢复偏好。当前只完成定位/编译，实际新App回归以当前任务回执为准。

## 个人测试交付与反馈闭环

默认以当前任务/原runtime-scope的明确质量等级交付，PERSONAL_TEST不等于稳定版；原三段长测和原签名/来源/hash/reviewer仍保留，不能伪造长测成功。已经实际执行的隔离医馆/双人中毒补给/交易装备及偏好恢复可优先复用；原录屏器已有外部force-stop/实际冷启机制，原PERSONAL_TEST分级薄适配已在真实Windows/KVM/保护发布链路执行成功：相关快检和全JVM、原短smoke、覆盖升级/不覆盖迁移前原档、一次外部force-stop/实际冷启、同源同签名同hash审核及公网完整字节复核；人工/稳定验收仍PENDING/NOT_RUN。

用户反馈先查对应版本和时间的既有日志，再复现/修复/补快回归，不要求其证明技术根因；保留唯一好的迁移前原档和真实云进度。完整游玩、设备、声音及长体验仍标人工PENDING，取消/失败和未实现内容分别保留。

- 外部冷启比较必须绑定刚保存的端点：实际个人smoke在内部Activity重进后已正常移动，却仍读旧normal端点，host字节恢复比较已过而测试坐标/encounter断言失败。保留原状态断言，正常smoke只做一次保存，然后由原录屏器唯一执行外部force-stop/cold/继续；该薄修已编译、快检、真实同候选短smoke与外部cold通过，并经原发布链路/公网字节复核。保留失败原片和源值，不能把之前未通过的cold改PASS。

- 原NPC记录首字节是entityByte，不能当对白组；以原extract_npcs逐值核清单，当前四域正反例已运行。对白组须另查实际调度/活动font，Reference编号和相似拓扑仅是线索。清单CLI仍要求已有JDK/ANDROID_SDK_ROOT，缺SDK会明确拒绝，不关闭APK校验。

- 正常稳定路线遇到胜后存活断言失败，先看同候选txt/索引的实际HP、库存、状态与原奖励；合法部分战死不是自动结算Bug。已实核隔离HallRoutePreparationTest使用原OpeningBattle/固定原规则比较有界准备策略，但必须由真实训练/商店/付费休息在新同候选App实现；隔离比较不算正常路线PASS，不删原存活/各自EXP/一次性flag断言。
- 云artifact重定向CONNECT403时，现有已连接GitHub只读artifact入口曾实际取回小型checkpoint ZIP（<32MiB），可核其hash并读取原txt/index；不打印临时下载URL，不将连接器成功等同当前云GH_TOKEN/网络策略验证通过。超大完整录像仍用原artifact入口，不绕过代理或另造原片。

- 录制预算变更须同时核原run-town02-runtime.sh与record_app_audio.validate_recording_budget；test_record_app_boundary.py实际检查所有显式预算和上限正反例。首殿18000秒仅限该prefix，其他范围不扩；入口校验失败属于TEST_HARNESS，不是App路线失败，也不能复用旧产物PASS。

- 大敌可能使用多palette。原observed_graphic_recipe的per_tile_palette模式按每8×8块找匹配ROM跨度并在scoped_observed_graphic核整体RGBA；已对真实魔礼寿280块运行，四图重建/错palette/span/重叠拒绝/派生fixture回环通过。原c50两次导出字节/hash保持。原片混入其他精灵时旧单palette拒绝，先缩定真实矩形，不能放宽整体RGBA。原图/PPU仍留忽略目录。
- RAM context标签需用原0:D664指针表定位，不依据旧provenance名字猜场景。原event16写7E6对应145，121实际7D0；16个原CPU边界已运行。旧错误key做只读兼容时须由原完成/global flag约束且新明确false优先，不能覆盖真实存档或重复激活演员。

- 原角色再次入队不得用新模板覆盖旧角色。复用原CPU调用核已有最大HP/MP、状态与flag，再用统一StoryCharacterChange提案更新存档原角色；缺角色/未知最大值/重复效果拒绝，取消或重复对白不提交。8/80/25个原CPU案例及相关JVM已运行；NPC零玩家步还需拒绝同图异位和跨图，不能借演出补剧情捷径。原字体按活动CHR与实际编码复用旧已核字形，剩余仅辨认当前对白所需字形，空格控制码另核，非全字库完成；新内容仍须原导出/同产物App验证。

- 场景NPC原OAM需保留非零码对应的实体黑；不能将所有黑像素透明化。原截图顶部裁8与硬件OAM y+1必须一致，当前源OAM的各非零像素码应映射为同一个真实截屏RGB，且全部非透明像素逐个匹配；原RGBA门禁不放宽。旧emulatorRgb与当前FCEUX截屏有微小RGB差异时先定位而非猜帧错位。三个源姿态已重建/像素匹配/回归；静态姿态不等于完整NPC走动，原图与PPU仅留忽略目录。新增RAM/map标签仍按原指针表核，不能由剧情地名猜。

- 当前场景的tileset3两平面矩阵可复用既有probe-world-island-terrain.py的固定字节，仅在scoped wrapper改变地图批次与受控输出目录；原ROM不改，旧probe/hash/配方保留。probe-world-jiameng-terrain.py已实际执行四格网264及胜后室内64 CPU组合，原导出器定向validator/来源拒绝与完整旧c50再导出通过；这些不算Android正常路线。
- 原observed_oam_graphic_recipe已经当前Linux/FCEUX原OAM、匹配ROM、实际截图执行，三actor全部非零像素匹配，并有隔离布局fixture回归。只能透明原pixel code0，非零黑色保持不透明；用实际NPC记录位置绑定身份，不假设14/24字节固定步长。原取证首次用了不存在的工作树缓存路径，须先查现有iteration_reader实际输入位置；禁止重新下载来掩盖路径错误。
- 源码检查显式read_text(encoding="utf-8")，不能以Linux默认编码通过推断Windows中文源码可读；真实原Windows门禁已在薄修后通过，未削弱App验收。

- 新遇敌图形先核当前地图的原CHR上下文，不能套上一个取证存档的银行。原图数个非零码显示同色时，既有observed_graphic_recipe可限定allow_collapsed_palette/per_tile_palette，从匹配ROM原16字节块重建；RGBA/hash继续严格核，不生成伪原图块。该分支已对实际原截图和正反例运行，默认其他配方行为不变。
- 新场景批次作为既有baseExport的子配方接入。原export_from_base严格验证目标pin，ci_apk.restore在空临时目录重建同一输出，旧PNG/音频逐字节比较；新普通敌人完整组表、掉落引用和地图默认遇敌阈值均保留。局部原CPU对白/角色过滤测试不能代替新Android正常路线；版本与具体资源状态仍由当前任务维护。
- 原地图CHR影子变量不一定是对白字体。已在现有FCEUX/Xvfb受控按键中记录mapper寄存器6004..6007实际写入，定位当前对白的两个2KiB字体银行；再用原glyph_pixels与既有字形像素hash定位必要字形。未知Unicode转录保持PROVISIONAL，可用固定Reference文本校对，但不从对白推断未证事件副作用。原回放/PPU不进入公开产物。
- 村民条件对白可复用原selector与action handler的CPU矩阵，仅增加当前实际记录；probe-world-west-village-talk.py已运行并与共享OriginalNpcTalk通过定向JVM对照。原有flag已置位时selector会跳过handler，受控scratch event不能错误地要求清零；CPU通过不代替ContentLoader或Android接入。
- 后续同类村庄增量写入既有world-village-batch-resources与原baseExport子配方，旧caller资源保持语义和历史字节。原NPC hidden类别/ID不能当对白索引；probe-world-west-village-pickups.py已核当前隐藏选择、容量、重复与金钱上限，并与WorldItems的JVM提案逐案对照。隐藏special的取得与使用分别记录，未核使用不补按钮效果；原名未解码保留稳定ID。
- 原动画帧高位标志不能直接当调色板下标。当前静态配方可引用另一真实记录中同entity的普通静止帧，依旧限定原动画指针、四图块、实际当前CHR与palette/像素hash；动态朝向/OAM未证时保持PROVISIONAL。原边界出口要补记录最后旧场景坐标，不把入口落点直接当离开trigger；当前实例已复用原FCEUX入口存档执行真实DOWN并核单独返回。新批次原导出、空目录restore和旧caller回归已实际运行，App仍须另验。

- 原房屋入口不能套用商店class-minus公式；当前已实际读取caller*3+houseIndex表、FCEUX真实入门/FE返程，并用probe-world-west-houses.py执行96入口与64 plain-room碰撞案例。入口落点重合出口时，正常先离开再踏入出口；原存档保留captureCaller/returnToCaller，不交换正反坐标。只检查持有/已用的action55/56不补治疗/航线。原ci_apk.restore省略destination即写加载器实际assets/development；APK内实际前缀/manifest需再核，编译不证明启动。新CPU fixture要保留实际tileset变量，漏设时标取证配置错误后复测，不改原预期。上述局部导出/JVM/编译与空目录复现已执行；新App仍待原runner。

- 原菜单效果与事件结束分别核：现已复用匹配缓存执行probe-world-west-scene-items.py（状态恢复/船位与消耗）与原FCEUX明确菜单/后续对白；共享StoryFollowup负责pending阶段、结束flag及同存档事务，原CPU/JVM/编译不代替App。font38/39本轮与实际使用对白PPU全4096字节匹配；逐字转录和局部未知格保留PROVISIONAL。库存低7位数量与高used标志分开，原新赠物可覆盖零数量的used行；缺旧档多空行顺序时保留当前存档并报精确缺口，不从JSON排序假造原格顺序。新原船水类矩阵不等于已接通落岸/停船/运行，必须另核原码头与独立返程。

- 自由载具与原固定渡船分开接现有World/Scene：先用原CPU水/码头矩阵和真实原按键核登船、落岸、停车与独立返程，状态成本与换图计数分别记录。当前已运行码头112组合、水5400组合、真实中毒/单人死亡/全队失能与四向OAM opaque像素核对；全队失能可停在原半完成换图，须保存为禁止行走的战败续接，不能算出航成功。原渡船的精确pending点与自由船入口都不能开放其他脚行水格。FreeBoatTest和相关导出/篡改拒绝/空目录restore已实跑，Android正常航行仍须原同候选runtime；不以本地或受控原版写App已验。

- 原Gradle wrapper版本属性为fengshenVersionCode/fengshenVersionName；其他同义属性会被忽略并生成旧默认版本。本轮aapt dump badging实际查出本地debug参数错误，纠正构建后仍须逐包核实际versionCode/versionName、内容pin与hash；debug不是正式签名或App验收，不能仅从命令意图填版本。


- 原新入图选择与cold restore分开：先对实际header前分派/当前USED库存行执行有界CPU矩阵，再用World无副作用arrivalResolver接新门；tryRestore显式旧map不重选。地图选择不能从对白推断病旗。共享地图网格/tileset相同且原碰撞矩阵相同可复用，其它出口必须分别核原记录与真实按键，不能交换坐标。已实际执行256 CPU、对应JVM/World失败保留与原局部export/空restore；新的Android加载/正常流程仍要当前产物验收。
- 原可见信件的investigation消息不等于普通talk消息；核真实菜单指令和raw记录字段。无奖励/flag的消息用限定readOnlyDialogue，避免通用NPC-seen标志制造剧情状态；原ROM、PPU和savestate继续仅在忽略目录。已有字体哈希复用后对未知字形目视转录并标PROVISIONAL，不从Reference地图ID推定ROM地图。

- 对当前原action1赠特殊物品，先核selector置旗与实际赠物先后，再复用OriginalNpcTalk既有赠物入口；原满包可能已置旗且复谈不重赠，不自动退款/重试。probe-world-sages89.py已实际运行256/14/128有界CPU矩阵，相关JVM/仪器编译通过；原零数量USED行覆盖使旧当前行见证失效，不能用历史剧情flag代替。新真实出口可增加旧scene的transitionCells/enabledCells，回归应只允许原独立记录映射出的精确增量，其余数据和媒体不变，不能机械要求整份sceneJSON不变。App仍须当前产物实验。

- 原物品目标可能是玩家当前位置而非NPC：已实际运行probe-world-well8.py的472位置/4消耗/1024阶段、受控原菜单和完整对白，复用OriginalSceneItems位置目标与共享StoryFollowup/pending校验，不造一个NPC或面向要求。目标scene开放范围与原谓词分开记录；原“不查map”不得写成“只允许此map”。无NPC的对白恢复用现有nullable openDialogue，结束仍同存档事务；ContentTest codec已编译但尚未App运行。新内容首次目标hash未固定时只用原导出器生成模式计算manifest，明确固定pin后再默认严格重导出/空restore；旧pin与旧媒体保持，不让CI跳过校验。

- 实际DEBUG加载曾在NPC引用require失败，原隔离入口先运行已有ContentTest可获得准确ContentLoader行号，再运行UI冒烟；打包hash成功不等于启动。缺失历史绑定时保留原内容pin，用有界局部export新增明确aliasOf/真实ROM文本group与source，FF/FF对象只声明已核不可交谈，不删除验证或伪造对白。上述诊断、局部导出/严格restore及正反回归已执行；新的App复测仍待runner。

- 实际五分钟前台AUTO测试曾以不变快照等待AUTO而失败：原SaveHistory有已测试的最近快照去重。验证自动保存需先制造明确隔离状态变化，再使用真实时间；去重与计时分别验证，不为测试删除运行规则。此fixture修正已编译，App重跑结果仍待runner。
