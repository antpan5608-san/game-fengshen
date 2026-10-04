# 当前任务：PLAYABLE-R1（父任务 WORLD-FULL-01）

task_id: PLAYABLE-R1
parent_task: WORLD-FULL-01
status: PARTIAL

用户本轮授权固定阶段收敛、修复、同候选App验收、原流程发布；R1交付后继续全世界目标。发布前不合入新区域，不reset/clean/清档，不修改Language或放宽签名/reviewer/同产物门槛。

## 当前有效状态（2026-10-04T12:29:56Z）

- 执行工作树：/workspace/game-fengshen-world-next，work/world-7747010d；起始main7747010d2cd71ab78c6e1a459440b54530e8ea9d。R1修改先保存本地检查点，最终提交/远端来源以实际提交结果记；无进行中的App候选。
- 独立公网读取：正式仍v27/0.8.7-world-full01-f0/c14，SHA5944141d45edb059294e9de066914c611f1e0cc6f334f32be9584a6ec35cc353，原签名保留。
- v67/run37199961466/source7747010d build于12:08:15Z FAILURE：BUILD_ENV，村5隐藏物导出测试硬编码Linux基底APK路径。真实Windows原Bash/路径相关门禁已过，此失败发生在后续导出；所有App job SKIPPED/NOT_PUBLISHED。修正测试读取已有FENGSHEN_CONTENT_BASE_APK，保留所有断言。
- 冻结R1：正常新游戏→南海/西北龙宫→85洞→东海胜后小龙女实际加入→地府村2买卖/住宿/双人战斗→医疗室实际入口/取消/原返程→保存/外部停止/冷启继续。购物/住店/等级不是剧情门槛。第一殿/十殿/重生/女人国不作为R1终点。
- 内容依赖：opening-segment-001-c51-r1，18张地图/120文件，manifest427ea305b23eb8493d6df34c1af3051bc6905f634b20a49d41e635266e67f805；由原golden村2及现有医生定义局部导出，不依赖新区域。地图0,1,2,16,17,18,19,20,22,23,25,85,95,96,97,98,114,139。非本轮已验证地图数量，实际App尚NOT_RUN。
- 冻结测试/端点/依赖/hash：ci/runtime-scope.json，由ci/content-source.json固定文件SHA。原三job仍全部必需；base共享回归/新游戏至北宫，world同候选85洞→东海→村2，continuation同候选村2医疗室/冷启。原29条全开发正常及冷启路径完整保留在原脚本，scope外不能写PASS。
- 已执行：335 JVM/68 suites/0失败/0错误/0跳过，仪器编译18秒PASS；原审批28 PASS；R1局部内容/实际review命令5 PASS；120文件全新目录原ci_apk.restore/签名基底/manifest验证PASS。运行脚本32方法PASS（原全开发29条与R1 11条正常分派均实际隔离执行）、基底路径导出2 PASS、最终仪器编译8秒PASS。新增R1测试首轮方法名误写与分派断言放错位置均失败后原断言保留修正重跑通过，不算App错误。实际新App、覆盖升级、发布前后巡检、一加13T和声音仍NOT_RUN。
- 权限已实际恢复：12:31 gh api user成功，原inspect37202401403完成approve/inspect SUCCESS、publish SKIPPED。本轮实际查询12:32:02.1529430Z：可信27/26=1451/2289，共3740事件，普通真机9会话/模拟器0/测试0/清理0；旧26下载ProtocolException1仍ISSUES_FOUND/rootUNCONFIRMED，按原精确非阻断评估保留，不代表新候选健康。没有删除代理、TLS或reviewer。
- 后续成果完整保留：/workspace/game-fengshen-world-island本地fb99b7d；work/world-room116-c50/dd9a14e8，c50/56地图/302文件。/workspace/game-fengshen-world-jiameng从7747010d隔离，原115事件31→116(13,5)新受控取证仅忽略目录，不合入R1，不冒称正常App。

## 下一条可执行动作

1. 完成R1局部测试与原审批/内容门禁，保存非强制检查点；只将这份冻结依赖与必要兼容/测试修正整合main，不带后续地图或Boss。
2. 原inspect已实际成功；随后原android-build.yml mode=build、runtime_tests=true，版本取实际分配下一号，当前下一候选68/0.8.10-playable-r1。不得在聊天发送Token，不绕保护。
3. 三段同候选实际通过→原自动审批及publish→公网全字节/签名/内容独立核验→实际postflight；冻结来源期间不推无关文档。任一P0/P1先最小修复，新产物重新验证。
4. R1报告后将修复同步开发线，按原顺序继续地府必要流程→重生入队→已有村/山洞/女人国验证。WORLD-FULL-01总体PARTIAL，ALL_MAPS_USABLE=NO/有效分母UNKNOWN。

累计欠账唯一权威：docs/delivery-status.md原十类清单；当前路线：docs/original-playthrough-roadmap.md。失败及旧快照在既有history保留，不关闭未验项目。
