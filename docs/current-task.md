# WORLD-FULL-01 本地恢复执行

task_id: WORLD-FULL-01
status: IN_PROGRESS
overall: PARTIAL
ALL_MAPS_USABLE: NO
valid_map_denominator: UNKNOWN
development_location: USER_COMPUTER_ONLY
cloud_role: EXISTING_GITHUB_ACTIONS_BUILD_AND_ACCEPTANCE_ONLY
stable_version: v82 / 0.8.12-playable-r1-stable
current_candidate_version: v84 / 0.8.14-c60-personal，PLANNED_NOT_PACKAGED_NOT_PUBLISHED
frozen_previous_candidate: v83 / 0.8.13-world-hell-r2，NOT_PUBLISHED
frozen_previous_candidate_source: 75ac819cad0bc387bafbf35ac3ccb352229aab9f
content_version: opening-segment-001-c60
manifest_sha256: 8c56f689610cff897c58d5efdac2370f32e0934cd172a3b0b173f0eb6c1b7bdb
first_real_blocker: DEBUG手动回档与外部冷启已通过；c60签名候选覆盖升级和同包最低门禁尚待构建执行，不能发布。

## 当前用户授权（2026-10-06）

已从管理窗口实际用户消息核实原话：“由你管理该窗口，恢复游戏开发，先完成 c60，再推进 后续开发，完成一版发布一版。”本窗口是游戏唯一写入执行者；该指令替代迁移期间暂停研发/发布安排。每轮选可验收范围，完成后沿原保护流程阶段发布并主动接续下一批。已授权必要main更新和原自动审批，不降低同源提交/同审核APK哈希、签名、递增版本、reviewer与存档安全门槛。

本机目录 `F:/apps/game-fengshen`，分支 `codex/local-ai-development`。Codex gpt-6.1-sol/high和本地4B辅助工具已接入、完全访问/无需执行审批/可信目录及当前Stop定义已启用。当前聊天cwd为local-llm，实际操作均指定游戏根目录；同一审查流程可用原manage.py/CLI，不改全局模型。

## 首批：c60与存档回档

- 线上实际查询仍v82，SHA `22ca9c1d78ac562789f9b6337089d1f1e2b48b7201705a34ec75746890fce4d9`。冻结R2 run37408307126：build/runtime成功，runtime-world已失败（09:59 UTC原录屏预算耗尽），保留原冻结来源和失败；不得用旧候选替代本轮c60。
- 原artifact11390352218 / run37413317670 / source06b95cdaabc398c60eecc008eaaea83d93c816c0已实际取回。ContentLoader、真实五分钟前台AUTO（304.649秒）、损坏/20档保留保护通过；手动回档在TouchTest.kt:55失败，外部cold尚未完成。原断言和原视频保留，不从编译或JVM推断App通过。
- c60已恢复378文件；迁移时405 JVM/DEBUG构建及井事件codec通过。控制电脑约8GB RAM，完整AVD界面曾资源不足；优先复用现有KVM Actions定向复测。
- 下一动作：原巡检→审查弹窗切换和真实失败录像→实际复现/分类→最小修复→存档/回档/Activity重启/外部cold/旧档升级定向验收→同候选签名内容与原发布门槛→发布及公网完整字节复核/postflight。

## 后续批次

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
