# 当前任务：PLAYABLE-R1（父任务 WORLD-FULL-01）

task_id: PLAYABLE-R1
parent_task: WORLD-FULL-01
status: PARTIAL

用户本轮授权固定阶段收敛、修复、同候选App验收、原流程发布；R1交付后继续全世界目标。发布前不合入新区域，不reset/clean/清档，不修改Language或放宽签名/reviewer/同产物门槛。

## 当前有效状态（2026-10-04；时间标为UTC，用户展示北京时间+8）

- 执行工作树：/workspace/game-fengshen-world-next，work/world-374cfc36；起始main7747010d2cd71ab78c6e1a459440b54530e8ea9d。当前冻结来源374cfc362fa5cfbc7a84b4c35608cd1ab20b4f59/treec2e379b22d8b0f9c8daebd8929ee130309b7db1b（本地97574d8同树）。v68失败/v69构建前取消的证据与修复见下方；本文件现场状态仅本地更新，不能推动main改变候选来源。
- 独立公网读取：正式仍v27/0.8.7-world-full01-f0/c14，SHA5944141d45edb059294e9de066914c611f1e0cc6f334f32be9584a6ec35cc353，原签名保留。
- v67/run37199961466/source7747010d build于12:08:15Z FAILURE：BUILD_ENV，村5隐藏物导出测试硬编码Linux基底APK路径。真实Windows原Bash/路径相关门禁已过，此失败发生在后续导出；所有App job SKIPPED/NOT_PUBLISHED。修正测试读取已有FENGSHEN_CONTENT_BASE_APK，保留所有断言。
- 冻结R1：正常新游戏→南海/西北龙宫→85洞→东海胜后小龙女实际加入→地府村2买卖/住宿/双人战斗→医疗室实际入口/取消/原返程→保存/外部停止/冷启继续。购物/住店/等级不是剧情门槛。第一殿/十殿/重生/女人国不作为R1终点。
- 内容依赖：opening-segment-001-c51-r1，18张地图/120文件，manifest427ea305b23eb8493d6df34c1af3051bc6905f634b20a49d41e635266e67f805；由原golden村2及现有医生定义局部导出，不依赖新区域。地图0,1,2,16,17,18,19,20,22,23,25,85,95,96,97,98,114,139。18是打包依赖数量，不是正常App已验证数量。
- 冻结测试/端点/依赖/hash：ci/runtime-scope.json，由ci/content-source.json固定文件SHA。原三job仍全部必需；base共享回归/新游戏至北宫，world同候选85洞→东海→村2，continuation同候选村2医疗室/冷启。原29条全开发正常及冷启路径完整保留在原脚本，scope外不能写PASS。
- 已执行：335 JVM/68 suites/0失败/0错误/0跳过，仪器编译18秒PASS；原审批28 PASS；R1局部内容/实际review命令5 PASS；120文件全新目录原ci_apk.restore/签名基底/manifest验证PASS。运行脚本32方法PASS（原全开发29条与R1 11条正常分派均实际隔离执行）、基底路径导出2 PASS、最终仪器编译8秒PASS。新增R1测试首轮方法名误写与分派断言放错位置均失败后原断言保留修正重跑通过，不算App错误。v68实际App前段通过、北宫后续断言失败，总体FAILURE；本轮新候选正常路线仍NOT_RUN，发布前后巡检、一加13T和声音仍NOT_RUN。
- 权限已实际恢复：12:31 gh api user成功，原inspect37202401403完成approve/inspect SUCCESS、publish SKIPPED。本轮实际查询12:32:02.1529430Z：可信27/26=1451/2289，共3740事件，普通真机9会话/模拟器0/测试0/清理0；旧26下载ProtocolException1仍ISSUES_FOUND/rootUNCONFIRMED，按原精确非阻断评估保留，不代表新候选健康。没有删除代理、TLS或reviewer。
- 后续成果完整保留：/workspace/game-fengshen-world-island本地fb99b7d；work/world-room116-c50/dd9a14e8，c50/56地图/302文件。/workspace/game-fengshen-world-jiameng从7747010d隔离，原115事件31→116(13,5)新受控取证仅忽略目录，不合入R1，不冒称正常App。

## 最近实际检查点与下一条动作

- 原build run37202777979于2026-10-04T13:04:09Z（北京时间21:04:09）SUCCESS。实际Windows 48组Python/255方法执行及release JVM335/68 suites/0失败错误跳过通过。Git Bash/基底路径修正本次真实Windows通过；不等于App通过。
- 不可变artifact11304251460/fengshen-signed-apk已下载；v68/0.8.10-playable-r1，16632525字节，SHA553839d32b6c78680fe2e49bc3a5d0d29e18756426f57cf67237c83662705592；原Signer5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6。原ci_apk.verify独立验版本/签名/120文件/c51-r1 manifest全部PASS。
- v68/base job111442185011于14:27:31Z失败，world/continuation SKIPPED，总run FAILURE/NOT_PUBLISHED。实际北宫Boss胜利、珍珠首次领取与立即复查通过；重进触发点时正常补给药草将HP46→96、数量7→6，钱946/EXP2566/剧情位未变，驱动却与补给前状态比较（TouchTest原2231行）。分类TEST_HARNESS，不声称游戏崩溃或重复发奖。
- 最小修复：正常药草补给后再捕获重进比较状态，保留所有角色/库存/钱/flag断言；新增隔离复现使用v68真实normal-index中的原值，明确CONTROLLED，不替代新候选主线。复现放在原base长路线之前。医疗normal/cold索引与截图分名，避免冷启覆盖正常来源。窄补已知c51-r1存档标记，场景/引用/位置校验保留，未来升级仅纯逻辑核对，不能写实际覆盖PASS。
- 修复后本地335 JVM/68 suites/0失败错误跳过与仪器APK编译PASS；全部runtime相关32方法与scope5方法PASS，Bash语法/diff PASS。新增4HP受控边界明确不是正常存档原值；新隔离Android复现与完整新候选正常/冷启仍待原Actions。
- v69/run37210639485/source7b13487d触发14:48:46Z；构建期间静态复查发现一次补给后HP仍低于半血时，step helper会再次合法用药（如4→54→104/max109），仍可能错误比较。未进入App前申请取消原run，不冒称新AppFAILURE。修为明确普通触控DOWN/UP原两格重进，保留地图/落点/钱/物品/角色/flag断言，补4HP隔离边界，与原46HP来源区分。
- 修正已非强制集成main374cfc362fa5cfbc7a84b4c35608cd1ab20b4f59/treec2e379b22d8b0f9c8daebd8929ee130309b7db1b（本地97574d8同树）；原v70/run37211528686于15:02:43Z触发一次。下一动作：跟进实际构建与早期受控复现，通过后继续原三段同APK路线。内容仍c51-r1/同manifest，原三job/原审批不变。新候选须从正常新游戏同APK完成R1全路线，不能借v68录像写新候选PASS。
- 三job实际通过后：原自动审批/publish同来源同SHA→公网完整字节/签名/内容复核→实际postflight→完整R1阶段报告。任一P0/P1最小定位修复、新产物重验，不把旧候选证据给新候选背书。
- R1报告后才安全同步必要修复到现有c50开发线，固定下一终点继续地府必要流程→重生入队→已有山洞/村/女人国接入验证。WORLD-FULL-01总体PARTIAL，ALL_MAPS_USABLE=NO/有效分母UNKNOWN。

累计欠账唯一权威：docs/delivery-status.md原十类清单；当前路线：docs/original-playthrough-roadmap.md。失败及旧快照在既有history保留，不关闭未验项目。

## v70实际构建检查点（2026-10-04T15:28:44Z，未发布）

原build job111463534162 SUCCESS，实际Windows255 Python方法执行/48组，release JVM335/68 suites/0失败错误跳过。不可变artifact11307526410的v70 APK16632573字节/SHAf04bf40300b4067269b96e4f77ff77799009f1805f32de57bfddce7ff1d24683；原ci_apk.verify独立签名/包名/版本/120文件/c51-r1 manifest PASS，内容120文件与v68逐字节相同。runtime/base111468210069从15:28:46Z实际运行中，Android复现/最终正常路线尚待回执，world/continuation未完成。来源继续冻结，正式仍v27。

最近现场状态16:22:55Z：原runtime/base仍IN_PROGRESS，world/continuation未完成，尚无v70正常全路线PASS回执；正式NOT_PUBLISHED/v27不变。继续等待真实base结果并按原链路接续，不另触发重复候选。

## v70实际base检查点（16:50:24Z，整体仍未发布）

原runtime/base111468210069 SUCCESS；25 base门禁PASS、upgrade PASS、runtime receipt仍PARTIAL/completedStages=[base]。七条同候选正常路径及各自外部cold源均PASS，北宫末尾含重新触发不发奖、珍珠不耗数量、实际穿过漩涡、保存；原handoff.verify下一world入口严格验证PASS。新4HP/原46HP隔离方法已实际通过（1方法/2场景，不能算正常路线）。artifact11308864610原handoff、11308699985北宫截图及两个未改原片、11309181692南海胜后原片、11309098578新游戏开头原片已取得并核SHA。

world111483083692于16:50:26Z开始、16:52:11Z FAILURE：85洞段第一步读取world-north-palace-expected-save.json报EACCES（TouchTest2953行），root推送/hash通过不能证明App UID可读；continuation SKIPPED。分类TEST_HARNESS存储权限，不把仪器FileNotFound当游戏崩溃；早停后mp4拉取也失败，不能宣称录像成功。整体v70 FAILURE/NOT_PUBLISHED，正式仍v27。一加13T/声音NOT_RUN。

当前修复：原runtime_handoff保留来源/同APK/normal-cold/hash检查，改由隔离仪器在App自身externalFilesDir写原字节并实际读回，禁止触碰GameState/真实存档；增加原base长路线前的原生存储探针，明确CONTROLLED。本地验证后新来源/新候选重验原三段，旧v70前段不能给新APK背书。

存储修复本地检查：runtime相关35方法与scope5方法PASS；仪器编译25秒PASS，既有JVM任务UP-TO-DATE（不能新增计335执行）。未改游戏数值/内容pin/包名/签名/reviewer，原生App文件探针和新候选三段仍待原Actions。

## 当前冻结候选（2026-10-04T17:25:35Z）

main/sourceff5f3cfa727978738aafbce1d3ccbedb603689e4（tree9d00d40c84d7bc984c5ee7a1147394d49d8bb871，本地dd79a13同树）；work/world-ff5f3cfa。v71/run37220463729已触发原build一次，build111489577406实际IN_PROGRESS。新增App-owned存储探针在base长流程之前；新三段正常路线均待本次候选实际执行，不能借v70结果写PASS。正式仍v27/c14；本文件现场状态仅本地，不推main改变候选来源。

v71 build于17:26:26Z FAILURE，35 runtime相关门禁中2个Windows分派fixture失败：Git Bash -c的长块末尾fi未到达，unexpected EOF；其余33方法通过。无APK、所有App job SKIPPED/NOT_PUBLISHED，分类BUILD_ENV。原8007字符块改为临时UTF-8/LF脚本文件执行；保留29全开发/11 R1正常分派、共享/冷启断言。不能把夹具模拟EACCES文本当第二次Android故障。
