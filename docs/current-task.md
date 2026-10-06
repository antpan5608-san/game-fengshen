# WORLD-FULL-01 本地恢复执行

task_id: WORLD-FULL-01
status: IN_PROGRESS
overall: PARTIAL
ALL_MAPS_USABLE: NO
valid_map_denominator: UNKNOWN
development_location: USER_COMPUTER_ONLY
cloud_role: EXISTING_GITHUB_ACTIONS_BUILD_AND_ACCEPTANCE_ONLY
stable_version: v82 / 0.8.12-playable-r1-stable
published_personal_version: v85 / 0.8.15-jiang-personal，PUBLISHED_AND_VERIFIED
current_candidate_version: v86 battle UI batch，LOCAL_IMPLEMENTED_NOT_APP_VERIFIED_NOT_PUBLISHED
frozen_previous_candidate: v83 / 0.8.13-world-hell-r2，NOT_PUBLISHED
frozen_previous_candidate_source: 75ac819cad0bc387bafbf35ac3ccb352229aab9f
content_version: opening-segment-001-c61
manifest_sha256: 37f0f7bb1080f6fe59f3853928c7e5006c2974d6f3ca5698713b2a37f5747557
first_real_blocker: c61已完成原签名验收/发布/公网全字节/postflight；正常主线至结局、四人界面与全部方案/累计欠账仍未完成，先接续已确认战斗/角色/物品UI。

## 下一批战斗界面（开发中，尚未App验收）

按长期授权自主拆分为可验收版本：下一版先完成敌左我右、当前队员状态卡、明确动作后选目标、敌人/队员只读信息和四人奖励摘要/独立详情。角色装备、物品分类搜索与能力说明、可展开行动记录及更小窗口的降级适配继续后续UI批次；不能据此关闭整个界面方案或累计欠账。内容仍为c61，原manifest和388文件/72依赖图保持；v86 / 0.8.16-battle-ui-personal是待验证版本，不是已发布包。

共享几何/只读投影已提交d33570e；实际216个安全窗口/字体/队员/敌数组合通过。已接入原GameView：默认点敌人看信息；选择攻击绑定battleID、角色ID、呈现revision和inputRevision，提交后下一角色重新选指令。原攻击、排序、随机、药草、奖励、存档算法不改；渲染不结算。原素材/黑底/行动快照复用。奖励详情暂停自动离场，B返回摘要、A或明确继续按钮离场。

414 debug JVM/90 suites实际零失败、错误、跳过；最终debug和instrument APK编译3m5s通过。19原交接/分派fixture（含全08失败停止）、4c61精确范围保护、13发布决策检查通过；先前误写不存在的test_world_scope模块造成的导入失败保留，已改用实际c61检查，不能称该失败批次整体PASS。此前4B完整改动轮实际返回8片段后超时，全部建议已读，Codex覆盖未审代码并登记codex_fallback，未冒称该轮完整通过；后续物理键/未提交按钮细节已重新审查，snapshot b675dc27cba303b2e1d0002e0cace55da9b8e784e926eba234f232d845a46d23，6个全新4B片段/无未覆盖，全部读完并登记reviewed决定。编译和本地审查不代替App验收。

原只读inspect37524966943实际SUCCESS：2026-10-06T20:15:13Z，NO_ISSUES_OBSERVED/errors为空/cleanupFailures=0，权威版本85/84；样本仍来自此前v84，不证明v85或新UI真机健康。DEBUG battle-ui入口已经接入原工作流：保持c61严格pin和原姜入队/外部cold，追加真实触摸、一次奖励、战败、秘宝、全08自动推进，以及实际2640×1216硬件/skin、1/1.3/2字体的一至四人/六敌/原Boss和四人结果检查。每个字体保留独立日志、尺寸JSON和真实截图。该新App运行仍NOT_RUN；必须实际读结果并目视原图后才能写App通过，再启用同源签名候选范围、基底85及原发布流程。

本机当前未检测到adb设备，真机触控/音频/真实云账号与正常新游戏至结局均未完成；长期目标ACTIVE，继续独立开发，不重复索取执行/发布授权。

DEBUG37528802859/source640a3c8已实际执行并失败，未签名/未发布。2 Content、姜受控邀请与外部cold（含原四人输入/RNG/过期回调/一次奖励/详情/继续检查）、手势/药草/战败/秘宝/全08自动推进以及旧手机字体1×方法通过；不能把通过部分当整批PASS。新一至四人矩阵在第一个单人source恢复断言失败，1.3×/2×未运行。实际异常为仪器runOnMainSync中的junit断言；原OriginalYangJoinDefinition.validPending要求context与杨戬实际在队一一对应，一/两人fixture错误写context=true，原保存保护正确拒绝。生产校验、世界碰撞及状态恢复不改，只让fixture的context/used与count>=3一致并预先验证；恢复失败在测试线程报告，保留原失败证据。

原artifact11443882054已实际取得：两段原MP4完整SHA通过，cold完整状态equal且无差异，边界SHA dddd784ae0e0303fc8037ed133c166b688d82a7cb55d5eca62492a1c203c63b3。已目视实际四人战场/结果与2640×1216字体1×Boss图，以及原片末帧；正常录制末帧为测试结束后的Android桌面，不冒称剧情画面。实际phone GameView为2640×1080/density3，该事实与截图分辨率分开记录。四人结果图确见24dp头像与首行EXP重叠4dp，正把EXP基线放在头像底部以下；银两文案同时派生实际settlement money差额，原奖励算法/诊断语义/一次保存不变。新修正需重新审查、编译和实际App/三字体复测；新的完整界面、真机与签名发布仍待验。

## 最新长期授权与终点（2026-10-07，北京时间）

已直接核实来源窗口01a11191-f8b7-7db1-8c6c-b39fd4634e01的人类长期要求及最新“Implement the proposed plan.”。完整批准计划、界面详细子计划与确认ID保存于 [fengshen-long-running-completion.md](plans/fengshen-long-running-completion.md)。实际长期目标已建立且ACTIVE，不设用户未要求的token预算。旧点击寻路文件保留为详细子计划及历史，固定版本先后由本轮自主安排取代。

最终三条件同时满足：正常新游戏至原版结局主体剧情及必要保存恢复完整可用；已确认UI/寻路/交互/v84反馈全完成；累计全部欠账有对应实际验收、最终版已发布且公网完整包校验及postflight完成。届时报告完整清单/证据，停止新增开发等待新指令。单版发布、代码/CPU/JVM或受控fixture通过不关闭长期目标。有效分母未知，整体PARTIAL，不公布虚假百分比。

本窗口继续唯一写入和原发布执行；按存档/重复结算安全、路线阻断、能力依赖、体验和验收范围自主选下一版。每版先固定目标/依赖/验收，取证→实现→4B真实审查及决定→测试→实际App→原发布/公网完整字节/postflight→主动接续。每个完成验收的规划版本必须发布，纯文档不发布。已发布源码/签名/main/同APK/hash/reviewer/pin/版本与仅两Fengshen对象保护保持。外部真机/声音/账号需要具体提出，未验不关闭，同时继续独立开发。

当前版c61优先完成实际cold及四人引擎/布局验收；其后可以在UI、房间/统一交互/全部物品清单、点击寻路、主线增量和其他欠账之间按依赖重排。最终正常主线与外部欠账验收尚未完成，各项分别记录IMPLEMENTED/PACKAGED/APP_VERIFIED/PUBLISHED。

## 此前c60恢复授权（2026-10-06，仍保留历史）

已从管理窗口实际用户消息核实原话：“由你管理该窗口，恢复游戏开发，先完成 c60，再推进 后续开发，完成一版发布一版。”本窗口是游戏唯一写入执行者；该指令替代迁移期间暂停研发/发布安排。每轮选可验收范围，完成后沿原保护流程阶段发布并主动接续下一批。已授权必要main更新和原自动审批，不降低同源提交/同审核APK哈希、签名、递增版本、reviewer与存档安全门槛。

本机目录 `F:/apps/game-fengshen`，分支 `codex/local-ai-development`。Codex gpt-6.1-sol/high和本地4B辅助工具已接入、完全访问/无需执行审批/可信目录及当前Stop定义已启用。当前聊天cwd为local-llm，实际操作均指定游戏根目录；同一审查流程可用原manage.py/CLI，不改全局模型。

## c60当前交付（2026-10-06 13:49 UTC）

IMPLEMENTED/PACKAGED/APP_VERIFIED_PERSONAL/PUBLISHED均完成。v84 / 0.8.14-c60-personal，quality=PERSONAL_TEST，manual_acceptance=PENDING；不是新的STABLE里程碑，历史稳定版仍v82。原build37469698895及publish37471974396全部SUCCESS，source704fc991b515f1c453f7413108ad795d350b3451，APK SHA fad6f4d962c7faec836a0d3d5bb50def0be95c4912b51d984eb88e418a8b5e91，31,659,184字节。实际公网下载全部字节、原package/signer/版本、c60 manifest/378文件均独立复核；69是依赖图数，不是69图正常可玩。

406 release JVM零失败/错误/跳过；20 ContentTest及全部14同签名候选个人门禁PASS：覆盖升级、内容加载、触控交易、双人补给住宿、诊疗进退、药草战斗、不可恢复存档保护、迁移前备份、外部cold、受控版本字段边界、井codec、历史回滚、回滚外部cold、损坏与20档保留。实际短冒烟使用已核正常存档的隔离受控回放，四段原MP4逐段SHA验证、cold完整状态一致及原偏好恢复；实际截图与录像末帧已人工查看。不得把受控回放当新候选正常完整主线或真机/音频通过；长流程、声音、一加13T人工仍待验。

原postflight 2026-10-06T13:49:11Z：NO_ISSUES_OBSERVED，errors为空、cleanupFailures=0，原两版保留为84/82；样本来自此前已上报会话，不构成v84新真机验收。下载继续原两对象入口和覆盖升级，勿卸载清档。

下一批c61：已从旧实际命令恢复原Lua两份与TSV两份，全部size+SHA与741清单匹配，本机私有保留。修正脚本46a94a0c4d642eefe425d181b3c5b5687d8ee013e9590aa589c0df7742ff9d3f明确x13..17,y42,DOWN→16(42,78)/steps0；旧脚本97901a097e337f671ee5a509a6d572e4eb5577d151e3b744e03ceb2193849c78及295字节日志f8c6adaa5999c5a75a4776e154a68b03133489fdbc8de62f4ae5ce31b5085af9确认15,43向下不换图；403字节修正日志13c3aea9700f29fcae82781f56491d6713f31d88db20d1f5a02cb41243725885确认两个left仍失败。原RAM/PNG/FC8未恢复，不伪称已复跑原路线。以原5字节记录为source，另行固定实际departure derivation，不制造全宽出口，不启用left。原84guard CPU重新执行同29ed0ebd3150c448e9045bf59ba8152961d6a99311f375079a96d9be25b7e940、零差异；原inactive c61配方bde171/388文件/72依赖图已复现，保留冲突待修，尚未App或发布。

## c61当前真实验收状态（2026-10-07，北京时间）

v84仍为已发布个人版。c61尚未签名或发布；本轮DEBUG实际通过后已启用精确c61个人签名验收scope，manifest为37f0f7bb1080f6fe59f3853928c7e5006c2974d6f3ca5698713b2a37f5747557（旧34de87仅历史候选），388文件/72依赖图，不是72图完整可玩。

DEBUG37482355271在环境阶段exit1，未留具体原因；不得猜测SDK版本故障。DEBUG37484779330实际进入AVD后ContentLoader.kt1054因成长表缺evidence失败，已补原来源字段、缺失/错来源拒绝并严格恢复，5项导出测试PASS159.602s，source0655f81。DEBUG37489153639真实Content2及首段受控入队PASS，录像/四人中段对白截图/expected-save已保留，但原录制器完整saved-before/after等值失败，外部cold方法和四人战斗未执行。OK(1 test)只证明第一段，不覆盖冷启失败。

source2eae8c5仅增加只读冷启before/after JSON及失败原片保留，完整等值断言、备份恢复和生产代码不变；8项录制边界测试通过，4B三片段已实际读并登记真实决定。DEBUG37494010240已实际保留只读before/after、失败cold原片和截图，逐字段唯一差异为rom.npccontext.7.191缺失→false；队伍/数值/物品/钱/位置/对白阶段不变。正在入队目标map7复用原flagsAfterMapLoad让首次保存与cold重建一致，完整等值不放宽。新审查/相关测试与实际cold/四人战斗复测仍须完成。DEBUG实际通过后才启用精确c61个人scope/baseline84，并要求同签名v85全部旧14及姜新增门禁、原审核/两对象发布、公网完整字节/postflight。

## 首批历史过程：c60与存档回档

- 线上实际查询仍v82，SHA `22ca9c1d78ac562789f9b6337089d1f1e2b48b7201705a34ec75746890fce4d9`。冻结R2 run37408307126：build/runtime成功，runtime-world已失败（09:59 UTC原录屏预算耗尽），保留原冻结来源和失败；不得用旧候选替代本轮c60。
- 原artifact11390352218 / run37413317670 / source06b95cdaabc398c60eecc008eaaea83d93c816c0已实际取回。ContentLoader、真实五分钟前台AUTO（304.649秒）、损坏/20档保留保护通过；手动回档在TouchTest.kt:55失败，外部cold尚未完成。原断言和原视频保留，不从编译或JVM推断App通过。
- c60已恢复378文件；迁移时405 JVM/DEBUG构建及井事件codec通过。控制电脑约8GB RAM，完整AVD界面曾资源不足；优先复用现有KVM Actions定向复测。
- 下一动作：原巡检→审查弹窗切换和真实失败录像→实际复现/分类→最小修复→存档/回档/Activity重启/外部cold/旧档升级定向验收→同候选签名内容与原发布门槛→发布及公网完整字节复核/postflight。

## 后续批次

### 用户已授权开发队列（2026-10-06）

已实际核实来源窗口01a11191-f8b7-7db1-8c6c-b39fd4634e01的用户入队、点击寻路和“Implement the proposed plan.”指令。完整原计划及交接实施规格保存于 [v84-feedback-click-navigation.md](plans/v84-feedback-click-navigation.md)，状态AUTHORIZED_QUEUED_NOT_IMPLEMENTED_NOT_APP_VERIFIED。本窗口继续游戏唯一写入者；保留c61当前工作，完成必要验证后主动接续，不再等待相同继续授权。

此前固定顺序作为历史：当前c61验收 → 房间map28/统一交互基础/c60全部70物品能力清单 → 点击寻路与到达选项 → 牛黄丸战斗与剩余物品分批。本轮长期授权已改为自主按安全和依赖选择版本，全部范围与偏好继续有效。既有四方向移动、碰撞/模式/遭遇/每步结算、存档与原发布保护复用；每批完成真实验收后发布个人测试版。牛黄丸地图原版二次尝试扣除不擅自改成bug，未核战斗不能套地图规则。

已定偏好：原指令保留触控快捷；对象到达只显示选项，确认才执行；默认隐藏摇杆、设置可开、实体方向保留；遇敌终止、战后重新点击。完整规格和验收细节以计划文件为准，尚未实现或验证项不得关闭。

c61草案尚未启用。先纠正142南出口原证据row42到43与草案EDGE43到44/VERIFIED冲突；保留左侧未换图失败，不猜出口。姜邀请/加入/成长/装备与7/142/121配方须正确pin、空目录恢复/hash核验、相关测试和真实App短冒烟。完整累计欠账继续 `docs/delivery-status.md`，不得把依赖图数写成已可玩图数。

每轮完整代码改动后本地审查与相关测试，Codex读全部建议并记录真实决定；代码变化重新审查。服务失败亲自覆盖未审代码并记录codex_fallback。开发/打包/App验证/发布分别记；PERSONAL_TEST与manual_acceptance=PENDING如实列示，真机/声音/完整正常主线不能由fixture代替。

## 历史入口与资源

迁移完成状态保留 [local-ai-integration-before-game-resume-20261006.md](history/local-ai-integration-before-game-resume-20261006.md)；云交接及私有缺口继续 [local-migration-cloud-handoff.md](local-migration-cloud-handoff.md)。此前WORLD状态保留 [world-full01-before-local-migration-20261006.md](history/world-full01-before-local-migration-20261006.md)。这些记录中的暂停/下一动作由本轮新授权替代，历史成果和失败保留。

匹配ROM和可信v27基底已在本机验hash；741份云端原始取证未整包迁出，按必要范围取得，不公开ROM/密钥/原始私有材料。旧电脑目录和备份保留，不reset/clean/清档，不改Language或其他项目；仅写原游戏OSS两个发布对象。

## c60定向修复与签名候选准备（2026-10-06）

原手动回档失败已分类为TEST_HARNESS：native弹窗关闭后窗口真实焦点恢复晚于idle，游戏失焦保护正确忽略仪器过早点击。保留生产保护；仪器使用5秒有界只读active/focused/hasWindowFocus/旧弹窗空等待，异常在测试线程报告，原完整状态/存档断言保留。

source e069d26 / development-smoke run37446746338实际SUCCESS：井codec、手动保存/取消/确认回档/Activity重启和外部force-stop冷启动通过。原五分钟AUTO与损坏/20档保护在此前实际DEBUG运行通过；不把DEBUG结果写成签名候选或正常全世界验收。原失败证据仍保留。

当前选择WORLD-C60-PERSONAL：exact c60 manifest、378文件、69依赖图，严格空目录恢复通过。9项原个人最低门禁加4项井codec/手动回档/回档外部冷启/损坏保留门禁将在同一签名候选上真实执行；完整主线/真机/声音保持NOT_RUN或人工待检。发布质量PERSONAL_TEST、manual_acceptance=PENDING。原main/来源/run/APK hash/package/signer/环境审核与两对象发布保护保持。

本地406 JVM测试全部通过；本地release任务因未迁入签名密钥明确未执行，release签名/JVM/同包App验收交原受保护Actions。本地65项相关Python初跑仅新增shell fixture未设第二参数失败，已修复并单独复测通过；其他64项通过。候选打包/签名App/发布状态仍未完成，下一步冻结同源提交、原构建、审核后原发布和公网完整字节复核/postflight。

签名候选run37448200402/source58c8c0d在scope预校验阶段被拒绝：工作树CRLF与Git/CI的LF字节不同，未进入签名/App验收，未发布。已固定ci/*scope.json为LF并重算实际提交格式hash；保留原拒绝门禁。下一候选须重新构建。

第二候选run37448833000/sourcec434f81通过scope pin后，导出火云洞时被executed probe hash门禁拒绝：Windows autocrlf将未固定的Lua从Git LF转换CRLF。实际Git/本地字节均与原执行hash一致；只固定Lua checkout为LF，不改原取证hash、规则或内容。新增真实Git autocrlf/smudge回归，签名/App仍未执行，不发布。

run37449645306/source62fe4f6正式签名build通过（406 release JVM/零失败，APK490203a902d3b31877049606c1dc6a320ab3efac93dbd58ace0f0a42dd813e87），但runtime20项ContentTest有2失败，未发布。旧R1精确18图断言与c60不匹配，新增c60精确69图/2角色/18诊疗定义测试并复用原诊疗存档断言，旧R1保持原精确范围。佳梦关fixture加入杨戬却缺少已有原入队context/used/item19，原SaveSnapshot校验正确拒绝；修复fixture的持久入队状态并新增三种缺失拒绝断言，生产规则保留。定向DEBUG重测后须新来源签名重测全部最低门禁。

新签名run37452828584/source022b651：build/406 release JVM通过，20 ContentTest、覆盖升级与迁移备份通过，随后touch装备仪器固定60次滑动预算在c60完整70物品目录耗尽，目标weapon2仍只露28px（要求至少48dp）。实际记录表明目标排序index51，保持原UI/48dp/选择与交易完整状态断言，只按现有modalLayout实际maxScroll和手势距离计算有界预算；失败时保留隔离截图与焦点/层级诊断。DEBUG定向加入原装备触控测试，随后同来源重新签名验证；未发布BF1B旧候选。

run37456878420/source11fc：build成功，20 Content/覆盖升级/备份、滚动装备、交易、药草、战斗、不可恢复保护均通过；诊疗旧R1 fixture的c51-r1标记与当前c60被仪器错误要求相同，未发布。仅明确controlled首次回放允许已核51-r1→60版本字段变化，先用当前scene/actor/state校验原档；normal同候选及cold保持精确一致，其他旧/未来标记拒绝。比较全SaveSnapshot只改contentVersion，原fixture字节/hash不动，index如实记marker变化及gameplay未变。新增边界拒绝仪器，DEBUG提前检查诊疗与personal完整cold录制并保留触控/诊疗证据，生产迁移/存档规则不变。

本轮将controlledReplayVersionMarker作为第14个个人必需门禁（原13项保留）；旧真实fixture输入字节/hash不变，普通同候选与cold禁止跨标记回放。新增字段后大续跑仪器触发JVM单方法大小上限，已仅抽取原源状态断言及元数据写入帮助函数，未删正常路径/资源/取消/冷启动断言；编译与App需再次真实执行。

## c61最新真实运行（2026-10-07，北京时间）

source8101fee / DEBUG37499759518失败：真实冷启before/after完整JSON equal=true/差异字段空；实际pending及完成无重复入队截图已核，随后四人battle-ready后instrument报告Process crashed。不能由cold通过写整轮App PASS；签名85/发布仍未完成。原before/after、两段MP4、失败日志/截图均保留。a3da978修正3/4队员物品目标为共享2x2绘制/命中几何，保留旧两人精确位置；411 JVM零失败/错误/跳过及仪器编译通过，4B全部4建议片段已读并登记决定，实际UI仍待同来源验收。418cbab在原jiang DEBUG失败分支收集隔离AndroidRuntime堆栈并exit1，8项录制检查/Bash语法通过；原发布与完整等值门槛不变，接续真实新DEBUG。代码检查发现原初始武器44不在已导出hit表中，暂作待堆栈核实线索，不猜阈值或放宽拒绝。

## c61 DEBUG实际通过及签名候选接续（2026-10-07，北京时间）

原DEBUG37506904472保留实际fatal：PhysicalRules.hits缺武器44。匹配ROM核武器7/44均51，已补精确span与所有可操作/初始武器表完整性拒绝；6项导出/篡改/空restore、411 JVM零失败/错误/跳过及仪器编译通过。source3aa7e97035dd97fd6ec6e6b72c2e94efe01d1be1 / DEBUG37511781139真实SUCCESS：2 Content、潘溪两页/B保护完整资源状态、原邀请触控/四人入队、中途external force-stop完整JSON equal=true/差异空、完成不重入队、四目标只选择、前三指令不结算、第四人实际原武器44攻击及一次胜利结算均通过。原两段MP4逐字节SHA、截图与实际末帧已检查；这是受控局部App冒烟，不是正常完整主线/真机/音频。

正式候选规划v85/0.8.15-jiang-personal：保留内容导出基底27，实际升级基底84/704fc991/37469698895/fad6f4d；c61 manifest37f0f7bb/388文件/72依赖图，旧14个人门禁加姜codec/触控入队/外部cold/四人战斗4项，21 Content测试。两个原录制与cold-boundary SHA必须留在签名runtime receipt并复核；不能用DEBUG替代。同源main/原审核与签名/仅两对象/公网完整字节/postflight保持，尚未签名或发布。

本地4B本轮0片段/服务身份不可用，管理窗口已实际只读核Ollama停止、GPU训练服务占用5212MiB并持锁；未抢占训练。Codex亲自覆盖所有未审代码并登记codex_fallback，不能写4B审查通过。实际旧胜利摘要四人内容有裁切，已列入下一界面批次；当前只关闭此次崩溃/局部布局能力，不关闭全界面或累计欠账。长期目标ACTIVE，签名发布后继续。

## c61首次签名真实失败与兼容样本修正（2026-10-07，北京时间）

source95607f417eeef0ade79288df11525aa5d31c81fb / run37513917589：build SUCCESS，411 release JVM/89 suites零失败错误跳过；正式APK85/0.8.15-jiang-personal，32,481,873字节/SHA58aff3da918b095e1e148054ee738b4166e5f83ac9df67604465baa2516a029a，原签名/包名/精确c61内容独立verify通过。runtime真实校验升级基底84、导出旧存档、同签名覆盖安装完成；Content21有20通过、1 ERROR于testPreviousContentWithoutCharacterNameStillLoads：合成c1 fixture删除combat却残留originalJiangJoin，Content.kt1074正确拒绝“Jiang physical rules missing”。后续upgrade一致/18个人门禁未运行，不得写签名App PASS或发布。

仅让c1 fixture去掉其不存在的后期事件，并断言无Jiang能力；新增当前c61启用事件而缺combat必须原guard拒绝的反例。生产guard/旧档迁移/资源pin和21原方法均保留。4B新请求0片段/超时，Codex亲自核此一个文件；修正后仍须新冻结源码和同签名完整21+18门禁，不复用失败APK验收，v85尚未发布。下一UI只读检查发现当前全部区域上下文最多6敌、4队员；536组/20Boss是静态依赖，不是正常通关。

## c61已发布，接续界面批次（2026-10-07，北京时间）

v85/0.8.15-jiang-personal PUBLISHED_AND_VERIFIED，完整报告见[evidence/v85-jiang-personal.md](evidence/v85-jiang-personal.md)。原build37516065030/source230b999cb5c0269484f4c61b0bf58256d2ae800c与publish37518266540 SUCCESS，411 release JVM、21 Content及18同签名个人门禁PASS；32,481,873字节/SHA0fafb10d61fdd7511b8c84f875ea43bdb84db8ba9a8b72258767fb16a5d90baf，原包名签名/c61 manifest37f0/388文件严格核实。六段原App片SHA与cold/prefs/截图/末帧已核，公网全字节及原verify独立PASS。实际postflight2026-10-06T19:22:04.7418420Z NO_ISSUES_OBSERVED/errors{}/cleanup0，保留85/84；上报仍v84历史会话，v85真机/声音不推定通过。

长期目标ACTIVE，PARTIAL、分母UNKNOWN、ALL_MAPS_USABLE NO；不因本版停止。下一界面批次复用现有Canvas/状态/交易及共享绘制命中，敌左我右、常驻一至四队员、独立状态/指令/详情和完整结果，默认点敌查看/先选攻击再目标，取消或浏览不耗行动；角色头像全名与真实经验/装备差值，物品能力/条件分类查找。当前71目录/14药品、536区域上下文组/最多6敌/20Boss定义仅静态范围。实际1/1.3/2字体与safeInset/四人/六敌/Boss画面、原手势/升级/cold必须另验，不标已实现。必要新增门禁及升级基底85随下一冻结版本一起审核，内容导出基底27不变。房间28/导航/原交互/全主线与十类欠账仍开放。
