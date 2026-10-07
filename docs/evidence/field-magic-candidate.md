# FIELD-MAGIC-01：提神术有限候选

IMPLEMENTED_LOCAL_VERIFIED；v89/0.8.19-field-magic-personal 候选。未打包、未进行签名 App 验收、未发布；线上仍 v88，稳定版仍 v82。MAGIC-ORIGINAL-01 的 A/B/C 和长期终点均未完成。

只开放原 field actorIndex1/row0 提神术的地图使用。复用 ContentLoader、现有角色法术页、队员选择、模态输入版本检查和原保存事务。选择法术→选择队员→确认，一次提案同时更新目标 HP 和施法者 MP；取消与重复旧确认不结算，保存失败沿原 commitModal 回滚。哪吒不增加法术；其他治疗、解毒、战斗和旅行效果继续显示未开放。

原生 read hook 实际命中 bank9:A2BE 施法者、A3C6 治疗目标，原指令为 AND F0。新实际 CPU 案例为 256 施法状态、512 治疗目标状态、2052 MP 前缀，连同原 880 共 **3700**；旧三张矩阵逐字节不变。原 MP2 不足、MP3 阳性一次提交和原未封顶 HP5→58/MP44→41 继续作为独立来源。只略过拒绝文字 renderer，MP 前缀在场景分派前停止，效果矩阵从原扣 MP 入口开始；不是正常入队、Android 或全法术验收。

本地第一轮 423 JVM 有 1 项原矩阵失败：Xiao-first 的受控队伍原读取固定 actor0 等级，不能按重排后的 party0 读取 Xiao 等级。已纠正固定 actor 存储转换，第二轮实际 **423 Debug JVM /92 suites，0失败/错误/跳过，仪器编译通过**；不是 release 签名/App。29 Python scope/原字节/边界检查通过。首次 Gradle --tests 误绑定到仪器编译任务、历史 room28 scope 被新门槛误拒绝的两项调用/兼容失败保留，纠正后通过；历史 golden 文件未改。

正式门禁保留旧 29 项，追加法术真实触摸取消/确认/重复保护及三字体、完整外部冷启两项，共 **31**。新 helper 要求本候选全部 raw JSON、三档实际截图、两个原视频全字节和保存前后完整等值；v89 及之后的 c62 候选不能通过重算 scope hash 去掉法术验收，旧 v87 scope 仍可验证其历史版本。覆盖基线更新为实际已发布 v88/source480d/build37595383488/fullSHA6090de31…，同 c62 内容/manifest625a/392文件，未新增存档字段或放宽兼容。

原健康解毒故障单独保留：受控 MP257→510 已实际查看原图，最大 MP600 是隔离 fixture，并非12级合法成长。现有原成长数据则显示正常更高等级 MP 可跨256（Xiao 50级 maxMP290，80级466）；不能据此把该异常当正常退款，或放宽当前 MP<=maxMP 的存档保护。姜原 field 封魔 row2、天罡 row3 与 Reference 顺序不同；名称与行号必须按原证据绑定。

原只读 inspect37604805219 SUCCESS，2026-10-07T10:03:45.2733510Z preflight 为 NO_ISSUES_OBSERVED：103事件、1实机会话，storedVersionCounts88=103，errors{}、cleanup0。它是发布时 NO_DATA 之后的新样本，不能改写当时的 postflight，也不等于完整真机、声音、云恢复或正常通关验收。

下一步：完成最新 4B 建议阅读与真实决策，冻结新来源，原 main/同源同审核签名 APK 执行 21 Content/31门禁、实际 v88 升级及首份备份保护，实际看图与 raw 字节复核后沿原两对象发布、公网完整包和 postflight；继续 battle 初始法术 MP 时点和效果取证。
