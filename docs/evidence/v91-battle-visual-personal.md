# v91：首批视觉正常可玩片段

PUBLISHED_AND_VERIFIED，2026-10-08北京时间；quality=PERSONAL_TEST，manual_acceptance=PENDING，历史稳定v82保持。WORLD-FULL-01仍IN_PROGRESS/PARTIAL，ALL_MAPS_USABLE=NO，分母UNKNOWN；只验收本批有限片段，完整视觉、正常原结局和三项长期终点仍OPEN。

| 项目 | 实际值 |
|---|---|
| 版本 | 91 / 0.8.21-battle-visual-personal |
| APK源码 | c323bfdff60bfd621428e5d0bb879c52c6035c66 |
| 原构建 / 发布 | 37675332933 / 37681167754，均SUCCESS |
| 完整APK | 57,540,785字节 |
| SHA256 | 6304e3ecba1b102a64d6c430241bd0e206e247ce351d8a9deb692ada017bf668 |
| 包名 / 原签名 | org.fengshen.dev / 5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6 |
| c62内容 | 392文件 / 625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef |
| 独立视觉包 | battle-visual-02-r1 / 12图加清单共13文件 |
| 视觉清单SHA | 4480914b805e4b5c99feeaf9d207227a60bca4accf3379fd151c13ccbaac571c |
| publishedAt | 2026-10-07T20:21:00.6281250Z |
| 下载 | [原Fengshen入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=91) |

## 实现和实际检查

四主角头像/完整待机、四环境自行生成候选按完整hash接入唯一ContentLoader、BattleVisualAssets、统一battleSceneLayout/绘制与BattleActionStep时间投影；图像等比裁框/脚底、攻击移动/治疗光环/伤害闪烁/死亡淡出只读表现。Windows新增交接选择性接入ResourceMap按字节缓存、有界read；保留服务器绘制/门禁。未改变玩家攻击、法术、RNG、奖励、加入或存档规则。具体源码阅读与素材来源见[候选过程](battle-visual-candidate.md)、[服务器交接](../handoffs/server-windows-visual-20261008.md)。

服务器新441 JVM/94 suites零失败错误跳过、88相关Python及DEBUG编译/验包已执行；正式同源Actions重新执行release441/94与16组Python报告122，通过原签名/c62/全部视觉字节核验。原21 Content与35个人门禁全PASS，稳定长段/续接任务按PERSONAL_TEST跳过，不能称稳定验收通过。

原runtime_handoff review独立重算全部新rawproof：7组完整保存外部force-stop冷启前后等值、14原MP4完整SHA和隔离偏好恢复通过。正常新游戏NPC/赠物/补给、自然战斗真实攻击胜利EXP增加、合法药草、完整保存和外部冷启继续由新非fixture方法真实验证；room28正常谈话/隐藏调查/一次物品/返程/cold是另一独立路线。四人/六敌/Boss/合法提神术与解毒術三字体受控证据单列，不称正常四人入队或完整通关。

实际目视109原PNG（29种三字体布局/物品/信息/奖励共87，加22法术图），另14原片末帧及正常/法术间隔采样30帧、正常攻击等定向14帧，共44视频采样帧；不是全部录像连续播放、声音或手机性能证明。正常42/43/44秒可见攻击、死亡淡出、EXP+1；86至91秒第二场伤害/胜利EXP+3，外部cold末帧EXP4/HP20与完整状态证明相符。部分正常测试结束恢复偏好回Launcher；7份cold末帧App可见。

本次实际GameView窗口1080/安全高936、实际bottomInsets144，三字体原矩阵均有测量。首次真实1216窗口触发旧固定要求失败，原数据保留；新协议1216安全边界有合成防伪测试，不将本次1080通过冒称新1216App或一加13T验收。所有触点仍48dp以上、矩阵不重叠。

原Actions审核及两对象发布成功后，服务器又完整下载公网version.json及57,540,785字节APK，与审核文件逐字节一致；原ci_apk复核版本、签名、392内容和13视觉文件。受保护Secrets/签名始终由原Actions处理，没有上传私有ROM/素材/存档或输出凭据。

## 失败、巡检与接续

迁入ignored c61导致首次DEBUG验包拒绝，以及首run37669616584/source110f8c7整体runtime窗口汇总FAIL均保留原输入/日志/185图/14片；未补造Insets、倒改通过或发布失败APK。Windows21游戏/20Python是交接历史，本轮新检查单列。新的Codex完整实际审查已以codex_only按最新快照登记，无本地模型推理要求。

服务器postflight于2026-10-07T20:22:22Z查询：NO_ISSUES_OBSERVED/errors空/cleanup0，权威保留91/90。69事件/1实机会话全部来自v90，v91样本0；仅原上传样本健康，不是91手机无故障证明。服务器检查点和脱敏回执在`/srv/fengshen-dev/receipts/visual-v91-*`，新原片/截图在`.local-ai/visual-actions-37675332933`。

下一有限批次已prepare_context并读真实源码：选择性接入已迁入且恢复原imagegen精确来源的哪吒攻击、小龙女施法、敌1/137四图，复用唯一加载器/纯几何/只读动作标识；验真实裁框比例/脚底与动作切换，避免worker同步缓存读阻塞Canvas、拒绝过期异步结果。旧像素敌图黑底、2倍字体三/四人战场收缩、其他敌/角色动作、环境/探索统一美术、胜敗完整演出仍OPEN。手机/60fps/内存、声音、真实账号多设备云恢复和正常原结局均待外部实际验收，继续不依赖这些输入的研发。
