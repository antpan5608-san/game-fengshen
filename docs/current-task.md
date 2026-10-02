task_id: NANHAI-01
status: IN_PROGRESS
execution_kind: IMPLEMENTED

# 南海龙王区段连续恢复

当前唯一任务：从正常新游戏连续推进真实南海路线→龙宫剧情/Boss触发→本场真实战斗/胜后→下一可操作状态→保存/外部停止/重启继续。可选补给不变为出发前置。内部M1→M2→M3检查点通过后自动继续，不按旧单阶段停止规则等待用户；整个任务结束才停止，不扩下一大区。

开始2026-10-01 20:33:25 UTC；预算上限2026-10-02 06:33:25 UTC；约75%预算04:03:25 UTC停止扩大范围并收敛验收。平台连续性不保证；每实质检查点或45–60分钟保存本文件/提交与复现动作。

起始main 3e799fb50594fee63b8fd7f8dbba659da1daae84，干净，无待整合修改。实际生产v24/0.8.4-touch-ux-01/c12，来源ef29edb9192bb299ed493b767c52b23c450b2094，构建36917255772；APK SHA def359de888614152768bdb70c4f12a5e09a653124db6eb0650e96a9edb76300，c12 manifest 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8。实际服务器metadata已确认24。保留全部已交付三图/三店、战斗/逃跑/战败、药草、装备、TOUCH-UX/INPUT/HUD/音频/更新/存档。

内容与触控skill已直接读取；不重试自动CLI匹配认证。目标ROM缓存private-inputs/town02/target.nes完整SHA和1048592字节匹配；不重复下载或全量研究。当前环境HTTP策略enforced/unrestricted，平台代理保留；无本地生产Secret，由原受保护Actions执行巡检/发布。

## 续跑状态

- 当前M1/M2/M3已完成首次源码集成，正常Android连续流程尚NOT_RUN；总体IN_PROGRESS，不按资源/单元测试成功关闭里程碑。
- 最近本地可构建检查点：首次Loader IntArray/List类型错误已修正；debug/仪器APK构建成功，JVM69/69（含本段18业务边界）；Python局部导出5/5、原CI安全12/12、药草导出5/5。随后新增真实地平线/宝箱显示与正常/受控验收方法并再次编译；authoring新增说明后旧target pin被门禁拒绝，已按实际导出manifest重新固定hash，未关闭校验。最新同pin构建/测试完成后启动原CI。
- 本轮开工inspect36922834320实际成功，2026-10-01T20:38:20.259042505Z：可信24/23，事件720、测试0、普通真机会话3/模拟器0，错误{}，NO_ISSUES_OBSERVED；v24事件290/v23事件430。范围仅上传样本，非本轮手机验收，旧音频超时UNCONFIRMED。
- 原版限定证据已取得：16(199,130)↔25(39,42)，25(29,44)↔97(15,29)，各落点方向DOWN；group1海底12群、group2宫内13群，宫内HIGH>=245门槛；必要NPC、首/胜后原文；BossROM137/source156/HP120/攻16/防13/敏8/EXP60/钱100/冰8/逃跑必败耗行动/随机長劍50÷128；正常原版新游戏→练7→购長劍→路线→Boss获胜→复谈不重奖全部已由controller-only验证，0761&1胜后置位，0727错误猜测已拒绝。
- 原版地图97手动存档明确拒绝，未有效手动保存的poweron回开局不算恢复成功；Android沿用原有自动SaveSnapshot便利存档，另验强制停止/重启。原版手动存档战败加载分支继续欠账。
- 新增内容通过原export_from_base接续：两必经地图/四连接/三NPC/两可选宝箱原图但交互未开放/四正常敌人及Boss/地图遇敌/本场规则/掉落/本场背景。未变化c12媒体逐字节复用。北部海底实际group4未接入区域明确开发边界，旧地图不缩回坐标白名单。新增BGM来自固定Reference，PROVISIONAL，许可/目标旋律/精确loop仍未知。
- 两辅助任务仅scratch隔离证据/测试草稿，已完成停止；主任务唯一集成打包发布。ROM/rawPPU/RAM/原始截图/回放仍私有，公开provenance仅范围、hash、派生recipe与来源。
- 最新签名候选检查点：来源3aaf4fcdf0afb9caf91e3afbd064bedfaa1cc9a0，原build run36932952930的Windows build已成功，release JVM69/69；候选v25/0.8.5-nanhai-01，APK SHA1483472f2ea2636714ff031742680cf649af5b97f25960d090c27ff5cb063262，12788840字节，目标c13 manifest0b614b749bb430d4f5a06c255cf4926703e234beab976abfb539ec3de3841eb6，59文件。已独立下载/验原签名与source/run/内容，33个旧媒体逐字节一致。Android runtime尚运行，未验收/未发布；main冻结此来源，本地此任务状态先保存，发布后才提交文档。
- 检查点巡检inspect36933053909于2026-10-01T22:07:29.805433995Z成功：24/23，720事件，0测试，普通真机会话3/模拟器0，v24 290/v23 430，错误{}，清理失败0。上传区间止20:28:44，未增加样本，不能冒称当前APK真机验收。
- 下一动作：等原runtime实际覆盖/回归/正常新游戏路线→Boss→胜后/force-stop结果；失败读取其artifact，按具体原因修复后新来源重建，不重复相同CI。当前候选正式APK可本地下载artifacts/nanhai01/candidate，生产仍24。
- 当前具体阻塞：无不可替代资源/权限阻塞；Android正常M1—M3及候选同源/签名/巡检门槛未执行，当前签名/服务器Secrets只在runner内。
- 时间检查点：2026-10-01约22:05 UTC，已用约1.5小时，剩余约8.5小时。首源码检查点40db7d2；原CI36931479402在Windows导出时失败：Pillow PNG压缩字节与Linux不同，非ROM/像素差异。原导出器现使用固定无压缩DEFLATE PNG，新图先验证已审核RGBA hash再验证目标文件/manifest hash；旧素材字节不变，原源PNG hash保留provenance。本地局部导出7/7通过；第二CI36932538963已在Windows严格恢复同manifest成功，证明PNG差异修复，但三项测试读取中文provenance时依赖系统cp1252而失败。只修复测试显式UTF-8读取，不删断言。第三CI36932722843严格恢复及像素解码已成功，Windows反例mock的字符串斜杠匹配未命中输入，出现2个反例不拒绝；已改按Path.resolve相等匹配，保留所有错误输入拒绝断言。
- 独立干净worktree40db首次恢复59文件成功，使用原入口固定公开输入并核完整ROM/MP3 hash；未迁完整旧目录。全量Python历史ImportIntegrityTests仅缺game-data/raw/reference-project/dataset.json而setUpClass失败，8项跳过、68方法通过；这是未恢复全量历史Reference依赖，不伪报全量通过，不要求全部迁回。另22自动审批安全fixture断言通过。
- 原版现有RAM/static范围补核：Boss初始化遇敌计数32不清，胜后恢复世界清0。仅完成胜后待对话后清零，不在开战初始化伪造清零；受控App验收新增此断言。
- 04:03:25 UTC按75%收敛，截止06:33:25 UTC。下一条动作：提交修复后原工作流重建并实际运行normal新游戏路线。

相关原版路线以docs/original-playthrough-roadmap.md的“当前有效状态”段为准，旧表明确历史；累计十类清单权威仍docs/delivery-status.md。最终只有正常App M1—M3与同产物/升级/巡检通过才READY_FOR_REVIEW，手机和声音不能运行记NOT_RUN。原则上本轮最终一次发布，只写原两个Fengshen对象，不改Language。

- 2026-10-01 22:23 UTC图形审查补充：3aaf4fc签名候选虽然构建/独立验包已通过，但新128×112 Boss图沿用小怪位置(112,72)会被原指令框遮挡，不得发布。本地最小适配从已核observedRect导出可选origin(64,0)，Loader限制图形不越界/不压指令，绘制和触摸共享battleEnemyBox；旧怪位置不动。新目标manifest45e0da6808694b961d809c727abb05727d821939253c1c66f3ce48f4c0741f62，局部7/7与JVM69/69及debug仪器构建再通过，新增受控原位置截图/几何断言。旧run仍可作为诊断证据，不能给修正后的候选背书；修复来源需重新原CI与正常录像，未发布。

- 2026-10-01 22:43 UTC实际检查点：原run36932952930 Windows签名构建/release69项以及受控Boss/旧界面正常流程通过；正常新游戏实际123场战斗、17次地图药草、Lv8、長劍，已走到海底25→龙宫97并正常返回25。正常脚本错误选择29,43墙格导致FAIL，未到Boss、不宣称正常M3通过。已按真实collision改为门口29,44向南29,45再原门进入，不改地图；添加墙/可走断言。原图形修复142de0e之外，四出口现依据原版controller-only settled RAM与静态加载器证据显式清遇敌计数0，完成换图后清旧手势，本段之外旧出口默认不变。新目标manifest badb0194e1342b66732cb2258fed7da2e80910f46fa74cccaac2a339cc4fcbc4，局部7/7与69JVM/debug/仪器构建成功。曾排队的origin-only run36934926038已取消（未执行），合并新证据/测试修复后只启动一次新CI。生产仍v24；剩余约7小时50分，下一动作是相关测试、提交合并修复、原CI正常M1—M3录像/冷启动/覆盖验收。

- 2026-10-01 22:55 UTC：71b2f233原run36936863541签名构建、release69/69、独立完整APK/包/签名/59文件/基底33媒体逐字节核验通过；但受控HP1战败脚本误假定首回合必命中，实际TARGET而非DEFEAT，runtime FAIL，正常主线尚未运行。保留全部战败断言，改以有时限的真实攻击输入等待战败；逃跑反击也容许原版真实miss并有限重试，无强制结果/随机数注入。失败早于原录屏adb root，shell无权读取App外部目录；仅原AOSP ranchu隔离AVD复用既有root读取取证，不碰真机/服务器保护。此候选未发布，下个来源继续同内容pin，重跑原CI，不把旧APK通过项给新候选背书。

- 2026-10-01 23:04 UTC取证门槛补齐：8d8c0b45原run36937973030在运行验收前取消，不作为任何正常流程结果。发现既有录像只覆盖保存前，冷启动仅日志/截图；现原record_app_audio.py增加实际外部force-stop/启动/继续的独立静音MP4与完整SHA/uptime索引，原CI额外保留两个小型原片副本artifact（入口、结尾+重启），完整未剪录像继续原artifact。不是新发布系统/新生产对象；此步骤编译/结构检查后须原runner实跑才可沉淀为成功方法。下一条动作：提交取证薄适配，冻结新来源，原CI正常M1—M3/冷启/覆盖/回归；生产24不变。

- 2026-10-01 23:15 UTC：8001c838原run36938801288签名构建/release69/独立验字节签名内容均PASS，受控Boss逃跑反击/胜后once恢复/HP1实际输入战败已PASS。随后三店normal脚本在药店预期19实际0失败，未开始最终南海normal。World完成一步时同一UI callback先remaining=0再dispatch exit；旧测试后台轮询remaining会在落门和换图之间观察中间状态。现只改仪器helper：通过UI线程同步观察完成序列/remaining及最终完成，仍仅正常MotionEvent，不调用world.tick/finish/restore，不改碰撞或业务；保留进店断言并加实际地图/格/seq诊断。原失败取证已成功由既有AOSP隔离读取取回真实全幅Boss截图，权限薄适配实际验证。候选未发布，下一次同源CI必须再跑正常主线。

- 2026-10-01 23:55 UTC真实可运行检查点：ec552114 / build36940098040 / APK fedb5921a87d74b31a55cbe327cabe22fcfaa606e513c5f043bccb7487435ad3 的正常新游戏M1—M3 PASS（1335.572秒），外部force-stop冷启实际GameView继续PASS（3.785秒）；原完整回归/覆盖升级/3字体手机尺寸均PASS。真实截图包含guard/龙王/冰/胜后/继续/冷恢复，实际静音原片及重启片段/index已保留原artifact。全run仍FAIL，因为小型片段选择器在EXIT trap pull之前读取模拟器文件nanhai-normal-index.json导致FileNotFoundError；不是正常玩法失败，门禁保留，未发布。仅在选择器前显式调用原pull_evidence，不新建取证系统，不改变Java/Kotlin/内容。检查点inspect36941528938于23:35:10Z实际成功，24/23 720事件/0测试/普通真机3会话/模拟器0，错误{}清理0，上传区间仍止20:28；不是25健康/本轮真机验收。下一动作：薄适配提交后同源原CI再跑，取得小型原片并实看，再原保护链发布一次。

- 2026-10-02 00:00 UTC：33d345b的run36943468633在最终运行验收前取消，合并尚缺的逐原片边界状态取证。原录屏索引只读增加每段savedWorldBefore/After（地图、角色、钱、库存、flags）与冷启动前后快照；首段明确pre-launch隔离基线，正常新游戏从索引marker开始；战斗快照是既有稳定世界存档，live HP以原片画面为准，不假装中途存档。本场Kotlin与内容完全不变，目标仍c13/badb0194…。只读快照与收集顺序修复合并一次原CI完整验证；不把ec552正常PASS赋予新的APK字节。本轮仍未正式发布。

- 2026-10-02 00:21 UTC：最终冻结来源bd02f1de249e82c3edcdf8032d8c568c0090f185，原build36944647517 Windows签名构建/release69/69/独立验包PASS，v25 APK cd2542deee282fba7617c898f2ac9c23d4cd853fd4853258360a86de39d577c6，c13/badb0194…59文件，33旧媒体逐字节一致。当前KVM runtime正在跑此同产物正常主线与回归；不以ec552的normal PASS代替此候选。公网实际仍v24。此状态先保存未提交，不改变冻结main；完整runtime通过后原发布保护链同来源一次发布，再提交最终文档。剩余约6小时12分，下一动作是下载小型最终checkpoint/原片，实际查看并核索引，再执行原publish入口；未通过则按具体错误修复新来源重建。

- 2026-10-02 00:33 UTC验收补齐：源码审查发现normal只覆盖胜后复谈/重启，没有实际离宫→再进入。已只在TouchTest补正常步行97→25→97、复谈保持当前snapshot/胜标、不重开战/领奖；途中自然战斗奖励按真实流程保留。游戏逻辑/内容不变，c13 pin不变。36944647517继续完成并保留其结果，但不作为最终完整N5；当前修改经编译后再冻结一次最终来源/原CI，不提前发布。下一动作：编译此新增正常路径，等待当前run结束，再提交并原CI重跑完整同产物门禁。
