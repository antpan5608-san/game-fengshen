# 下一有限视觉批次：敌人附近的名称与短血条

状态：有限绘制／布局与原证据链代码 IMPLEMENTED，本机新454 JVM／96 suites零失败、84相关Python（7 UI／13 visual／48 runtime／16 APK）通过，应用与仪器 DEBUG 编译通过。原 Actions 正式同源签名／Android 实际量字 App／发布仍 NOT_RUN。属于既有 WORLD-FULL-01；v95 已按原同源审核发布并公网／postflight 核验；本设计不作为下一源码的验收。

已用 CPU `prepare_context` 导航并读取真实 `BattleScreen.battleEnemySceneLayout`、`MainActivity.battleEnemyBox`／统一绘制／`battleLine`、`BattleScreenTest`、两项三字体 phone/party 仪器测试及 `battle_ui_evidence.py`。当前单敌标签和血条宽度来自整个敌方 cell；编译布局探针显示原标签与血条有 2–4dp 几何重叠。该探针不是新 App 故障复现。

只复用现有已审敌 1／137 crop 和 native 回退，不新增素材、名称、HP、能力或规则。保留原图像比例和缩放、48dp 命中 cell、多人／六敌 compact 编号、状态卡、系统字号及完整信息层。

最小适配是共享只读反馈布局：根据实际绘制 sprite、cell、Android Paint 的完整显示文字量宽及 fontMetrics 行高，优先放在图框下方；完整文字行、间隔、血条和底部留白放不下时，使用图框侧边空白。宽度受 cell/可用空白限制。仅减少空白，不缩小字号或进一步压缩大字小战场精灵；极端输入无法适配时保留有界回退并明确未解决。

服务器设计入口 `.local-ai/battle-pose-next/enemy-feedback-next-notes.md`，数值原型 `enemy_feedback_geometry_draft.py` 和结果 `enemy-feedback-geometry-draft-result.json`。24 个真实编译单敌 cell（两窗口、三字体、一至四人）接两张真实 crop；合成文本宽度／行高范围共 1440 组，768 下方、672 侧边，框内且无相互重叠。合成范围不是实际 Android 字体测量，也不计作 JVM 或 App 测试。

下一代码批次保留原五参数布局 ABI，由 Canvas 和仪器共用同一反馈结果。新 JVM 核真实裁框、文字边界、精灵／命中不变、标签／血条分离、紧凑多敌原几何及无效数值拒绝。沿原两项三字体测试，将实际 sprite／label／gauge、目标身份、显示文字宽高加入已有 `mobile-phone-F.json`／`mobile-party-phone-F.json` 的 raw 哈希绑定；旧报告缺字段、错目标、NaN／bool、越界／重叠必须拒绝，不另建验收通道。

新 App 须继续正常起始交互、物品、自然战斗攻击、合法药草、奖励、完整保存与外部冷启；四人／Boss／已实现原治疗单列受控。原 36 门禁、旧档覆盖、RNG 与完整保存不变继续实测；实际三字体图片和原录像采样须逐版检查。真机／声音／完整原版结局、完整美术与全部累计欠账仍 OPEN。通过新审查及原同源签名 App 后才原批准发布、公网完整字节与 postflight，再接续。

2026-10-08T04:05Z 新检查点：Canvas 与仪器共用 `battleEnemyFeedback`；量宽采用同绘制字号的 Android Paint，量高采用真实 fontMetrics。纯布局只接原 sprite 与 cell，单敌下方优先／侧边空白回退，多敌沿原 compact 编号与几何。两项新 JVM 检查真实已审 crop／两窗口／三字体／一至四人及移位，另查过高／非有限文本；新 Python raw-proof 要求实际框、身份、同文字量宽／行高、精灵与状态不变，缺字段／错目标／越界／重叠／NaN／bool／伪状态及过宽拒绝。合成测试数据不当真实 App。原 36 门禁与七规则源码不变；c62／素材保持。`enemy-feedback-local-checks.json` 与各测试日志保存于服务器 receipts；首次 gradlew 权限失败原日志保留，使用 bash 原 wrapper 后构建成功。拟96参数仅本地检查，不称冻结／正式验收。下一动作是新 codex_only／Stop，选择性提交冻结同源 main，原 Actions 新签名 App，再按真实门槛发布；v95完整证据不能复用为96验收。

手机同会话连接交接检查点（2026-10-08T04:16:41.306850+00:00）：本批源码已选择性冻结并推送 main `428c67e8f9073fc6087fa9d017564d8a0438e29c`；原 Actions build [37725875054](https://github.com/antpan5608-san/game-fengshen/actions/runs/37725875054) 最新实际查询 IN_PROGRESS，build job `113143786490` 正在 Build, test and verify existing signature/content；App/runtime 尚未创建／未验，正式签名产物尚未独立核验，v96未发布且未触发发布。新454 JVM／96 suites、84 Python、DEBUG两包与409内容／视觉字节同95已真实通过；codex_only `ed72f2c2`、Stop current。源码冻结后本地仅写交接文档与服务器回执，不推无关提交改变当前候选 main 来源。

立即接续顺序：查询上述原run，保留真实失败与原artifact；正式签名成功后独立校验source／run／版本96／包名签名／SHA／非DEBUG／c62-392／视觉17，并按成功作业窗口选择唯一artifact、完整ZIP digest安全下载到 `.local-ai/visual-actions-37725875054`。原runtime全部21 Content／36门禁及新三字体实际量字 raw-proof须真正通过；服务器重算 `tools/runtime_handoff.py review`，目视本批全部原PNG及实际原片采样，核7完整cold／偏好恢复。全部门槛满足并再次实际审查登记后，才同源原reviewer发布，独立公网完整字节与Linuxpostflight，然后登记实际版本／更新baseline及接续。不要借95图片／冷启／App或本机DEBUG替代96验收。

待验范围：本批正式签名／旧档覆盖升级／真实App三字体／正常起始交互-物品-自然攻击-合法治疗-奖励-完整保存与外部cold均待原Actions；受控四人／Boss单列。真机13T／帧耗时内存／声音／真实多账号云恢复／正常完整原结局／完整重制与所有累计欠账仍OPEN。短战场角色比例仅ignored数值设计102组（历史95实测窗口，22组拟调整），未进游戏，不算新测试或完成。保留原Windows ZIP／历史／服务器工具dirty／失败。用户要求本回合正常结束以交接手机同一threadId连接；长期IN_PROGRESS/PARTIAL继续，无暂停／完成标记，不取消Actions，不创建新会话或新任务。

2026-10-08T04:22:37.591035+00:00 同会话接续：正式签名构建成功，原runtime job113146409794仍运行。本机已独立完整ZIP／来源／签名／非DEBUG／版本96／62,610,832字节／SHA23f7a7ac686333cb98b04e2cbe1d7547dbe15ead956459861a9d6521e89fa65b／c62-392／视觉17及409份同95逐字节核验。正式新454 JVM／96 suites零失败／错误／跳过，原build log16组报告132 Python、16 OK；本机84与正式132分开，不冒充新50工具或重复JVM。App尚未独立通过，未触发发布。辅助字节验证过早检查未下载目录，以及误猜buildRunId键失败均另存，按原signed回执真实buildRunID修核；输入与门禁未改。新Linuxpreflight04:21:12Z NO_ISSUES_OBSERVED，65事件2实机、94:26／95:39、errors空／cleanup0，只是上传样本，不当手机验收。

2026-10-08T04:45:47.804025+00:00 原build/runtime首轮SUCCESS；新独立21Content／36raw、87实际量字（15单敌新／72原multi）、121原PNG／31页全目视、74独立视频采样／19页全目视、6原尺寸复查、14原片完整SHA／7完整cold／7原prefs通过。正常36-41s攻击／敌倒／EXP+2银两+1／42s返回及外部cold实际已看；四人原治疗HP5→58／MP44→41、解毒另列受控。非全程播放／声音／13T／完整结局，短战场角色和所有长期欠账保持。source428c67e/run37725875054／SHA23f7a7ac，APP_VERIFIED／NOT_PUBLISHED；新codex_only ed72f2c2与Stop current已记录。辅助checkpoint文件名初猜失败已留receipt，实际路径修正，不影响游戏或验收。

2026-10-08T04:54:28.711328+00:00 本批已原发布37729043457 SUCCESS，公网完整62,610,832／SHA23f7a7ac与审核APK相等，postflight权威96/95、errors空cleanup0、样本均95，新35 baseline回归通过。见[v96实际交付](v96-enemy-feedback-personal.md)；接续[短战场角色](battle-short-arena-next-batch.md)，历史失败/检查点及所有长期欠账保留。
