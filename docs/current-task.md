# 当前任务：PLAYABLE-R1（父任务 WORLD-FULL-01）

task_id: PLAYABLE-R1
parent_task: WORLD-FULL-01
status: PARTIAL
quality: PERSONAL_TEST
manual_acceptance: PENDING

用户2026-10-05正式修订：尽快交付个人测试版；完整长路线/剧情/真机/声音交用户人工验收，保留原长测试。原签名/同提交/同APK/hash/reviewer/环境保护不变。仅更新原两个Fengshen对象，不修改Language/玩家云进度。

当前工作树 /workspace/game-fengshen-world-next，起始main7747010d；上一冻结7302040c/v77/run37248177285。用户授权后取消剩余长测试，build SUCCESS，runtime CANCELLED，world/continuation CANCELLED，不写PASS。取消前原Content17、升级、原生存储、医疗/双人解毒客栈、交易/装备/存档保护及touch-ux/f0正常冷启实际通过；完整新游戏南海路线未完成。签名产物/日志/已生成检查点仍保留，完整旧现场见history/world-full01-runtime-checkpoints.md。

冻结范围不扩大：c51-r1/18张依赖地图/120文件，manifest427ea305b23eb8493d6df34c1af3051bc6905f634b20a49d41e635266e67f805。已实现开局/村服务/南海/西北宫/85洞/东海/小龙女/地府村2/共享医疗；本轮新候选完整正常路线DEFERRED_TO_MANUAL。十殿/重生/女人国等c50/56图源码与资源保留既有独立开发线，不混入本个人包，不称已交付。

正式基线仍v27/c14/SHA5944141d45edb059294e9de066914c611f1e0cc6f334f32be9584a6ec35cc353。最近实际inspect37247975418于00:33:57Z查询27/26=3757事件，普通真机9会话/模拟器0/测试0；仅旧26 ProtocolException1/rootUNCONFIRMED，精确非阻断评估保留，不能证明新候选健康。

本轮最小修改：原scope/回执/两份workflow显式PERSONAL_TEST级别；原完整稳定门禁保留，个人短冒烟不伪造三段成功；原构建保留相关快检/全JVM/严格干净导出，其他后期导出仅稳定门禁。跨内容迁移前一次保留原saveJson到preContentMigration，已有备份不覆盖，失败保护原存档。

下一动作：相关规则/审批正反例、仪器编译→同源签名个人候选→原KVM短冒烟（v76合法东海/中毒小龙女检查点明确CONTROLLED、真实买卖/解毒/住宿/医疗/外部force-stop和冷启）→原审批发布→公网完整字节验证/实际postflight→PERSONAL_TEST_DELIVERY。人工验收PENDING，不等长通关；后续WORLD-FULL仍PARTIAL，ALL_MAPS_USABLE=NO/分母UNKNOWN。

唯一累计欠账：docs/delivery-status.md；已有路线：docs/original-playthrough-roadmap.md。没有自动关闭尚未实现内容或设备/声音验收。

本地实际检查：个人分级6方法、runtime37方法、scope5方法、CI安全16方法、录制边界4方法PASS；原审批32个真实隔离API案例PASS（稳定3job拒绝/个人短runtime拒绝/权限与来源均保留）。仪器最终编译已执行，见/tmp/r1-personal-final-instrument-compile.log；短冒烟/正式发布仍待原runner，不冒称App已通过。下一候选使用0.8.11-playable-r1-personal/versionCode78，目标内容及素材不变，源冻结后仅现场本地记录。

实际v78/run37252174601/source84c60142：签名build SUCCESS（68 Python执行/5快检组；不可变335 JVM/68 suites/0失败错误跳过），SHAee617384cf746971f76741157d1b4da978bf583e4ebef1aceaa6771b48dc896a，122 assets与v77逐字节一致。短冒烟runtime111582844546 FAILURE/NOT_PUBLISHED，后两长job显式SKIPPED。实际升级/首原档备份且不覆盖、Content17、交易装备/地图战斗药草/保护/医疗/有源双人村2全部PASS；外部host force-stop保存字节相等检查通过，但后续GameView测试期望旧normal端点(30,19)/encounter0，实际保存是此前内部Activity重进后(6,14)/encounter43，角色/物品/钱/flag逐项一致。分类TEST_HARNESS重复使用已移动的旧检查点，不声称App丢档。

最小修正：短smoke正常方法只执行有源补给/服务保存，外部record器再唯一执行force-stop/cold/合法继续；原提前内部Activity复现不删除且保留所有断言。下一候选79/0.8.11-playable-r1-personal，同范围/原签名/无游戏规则或素材变化；不再长练级。最新inspect37252198992/01:38:55Z仍27/26=3757事件/9普通真机会话/旧26 ProtocolException1，原Node精确评估ALLOW；正式仍27。
