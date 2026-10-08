# v99 紧凑多敌血条：实际验收与接续

2026-10-08，服务器唯一写入者。IMPLEMENTED / PACKAGED / APP_VERIFIED / PUBLISHED_AND_VERIFIED 已实际通过。quality=PERSONAL_TEST，manual_acceptance=PENDING。线上实际99，上一版98，稳定82、c62与WORLD-FULL-01 IN_PROGRESS/PARTIAL保持。

源码 `a5bb7c6bdffee85e62c0349aa7dfdbf855a54174`，原[构建与隔离验收37741740866](https://github.com/antpan5608-san/game-fengshen/actions/runs/37741740866)首次SUCCESS；build113193786466、runtime113197306391。正式版本99 / `0.8.29-compact-gauge-personal`，62,618,584字节，SHA256 `35bd105a2d1e5c73c2ce6b421b923fda78d0b860cae6b2a98836603389df6daf`。原包名 `org.fengshen.dev`、原证书、非DEBUG/c62的392内容与视觉17文件共409游戏资源逐字节同98，没有新素材或规则。

完整原签名证书SHA256：`5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6`。运行scope SHA `8a0e1fbcd6034c6f609b775b33256edadd9b321985c6c6aaf50329f00eadafd5`。

只复用统一battleEnemyFeedback/Canvas，在紧凑多敌原血条框内，按实际sprite宽＋8dp、至少24dp且不超过旧条，适配宽度和x中心。原编号label/sprite/cell/y/h、单敌完整量字、48dp目标、ContentLoader/BattleActionStep、HP/MP/奖励/存档不变。新V2 raw强制baselineGauge/compactGauge与原边界，不降低原门禁或把旧raw冒充新App。

实际新检查：本地466 JVM/97 suites零失败错误跳过、55相关Python及两DEBUG包；正式466 release JVM/97 suites与完成build日志16组134 Python均通过。4个新增JVM含多窗口/字体/队型/六敌、左右边界/不扩张/非法输入及24.03 Float回归；2个新增Python含15类篡改拒绝。首Float失败日志/XML保留，修正后通过。新21 Content与36原raw门禁通过，source/run/hash绑定和成功job唯一artifact完整ZIP摘要、大小、安全路径已独立验证。

实际三字体受控App：48角色body、87敌反馈行与98同测量窗口比较，除24条窄条外原几何保持；72compact中48宽度已适配无需再缩，15单敌条保持。121原PNG/31页＋5正常原PNG、77原录像采样/20页＋追加51正常帧/13页均实际目视；15完整MP4哈希、7完整cold所有字段before=after/差异空、7原UI偏好还原通过。采样并非逐帧全片或真机验收。

正常路线无grants：原起始NPC/小刀/三店与合法物品交互、两次自然遇敌；本次百角海龟攻击胜利EXP+3/银两+2，后一次臭甲虫合法逃跑。最终药草一次13→20HP，EXP3/12、银两7、药草0，完整保存与外部force-stop恢复相等。四人/Boss/六敌/已实现提神术与解毒术为受控证据，不能冒称正常加入或通关。

本次正常首请求queue1/prepare1505/UI13/ready1519/post40/firstPosted1559ms，第二次缓存后prepare0/firstPosted71ms。受控三字体排队355–432ms、首实际post460–503ms；不是可比基准或手机性能结论。原素材回退仍在正常首场7.5–8.5s采样可见，9s已有准备后的角色/背景；没有本批性能改善主张。未知敌仍原素材回退，全部美术不算完成。

新完整代码审查 `codex_only` 快照 `fde6ae0fe22551d6bfccd735a4cb6153d53fdf36541273e1cecb53e6957fc413`，Stop current/continue=true；本轮实际重读完整九项代码/测试/配置diff。新07:53:24Z preflight查询成功、15条98事件/1实机会话/errors空/cleanup0，仅有限上传样本，不是99真机验收。

服务器脱敏回执：`/srv/fengshen-dev/receipts/compact-gauge-formal-build-verified.json`、`compact-gauge-formal-python-verified.json`、`compact-gauge-runtime-raw-verified.json`、`compact-gauge-actual-geometry-verified.json`、`compact-gauge-actual-observations.json`、`compact-gauge-video-cold-verified.json`、`compact-gauge-actual-visual-review.json`。原App/原片只在服务器 `.local-ai/visual-actions-37741740866/` 保留；不提交完整存档/原片或凭据。

真实首次ZIP传输失败、过早读取未完成ZIP被拒绝、GH发布前main查询EOF均保留单独失败回执。未完成ZIP没进入验收；完整下载后重新验证通过。正式Python原先整run未完不能取日志的失败/待验记录保持，新的134计数来自完成build日志。不改变玩家输入、原失败或迁入工具dirty。

接续[地图首帧后的局部图像预备](battle-map-preparation-next-batch.md)：只原单worker/cache，当前map/当前队伍/空enemy，不安装warm bundle到战斗，最多一个待处理任务，battle进入取消旧预备，当前单图解码最多完成该图后停止。此范围尚未实现或新测试。13T/声音/真实账号多设备云恢复、完整原主线、全美术/法术/寻路与权威全部累计欠账保持OPEN；只有三终点与最终公网验明才登记长期完成。

## 原发布及独立公网实核

原[发布37746689697](https://github.com/antpan5608-san/game-fengshen/actions/runs/37746689697)首次SUCCESS，approve113209753627/publish113209753462；07:58:54.0853462Z发布。成功publisher唯一artifact全ZIP摘要与原签名receipt逐字段相等，当前99/上一版98，仍PERSONAL_TEST/manual_acceptance=PENDING。08:03:36Z独立公网62618584完整字节与同审核SHA相等、原签名/非DEBUG/c62及视觉17已验。

08:03:40Z Linux postflight查询成功，NO_ISSUES_OBSERVED，errors空/cleanup0、权威99/98；仅15条旧98上传事件/1实机会话，尚无99实机样本，不能当99手机验收。四项runtimeBaseline改为真实99 build/source/hash/version，新35个ci_apk/runtime_handoff测试实际通过，基线全diff已再次实际审查，新codex_only c3498ade与Stop current通过。

下载：[v99个人测试APK](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=99)。原两个OSS对象之外没有本批写入；原存档、dirty与全部失败保留。继续下一有限地图首帧后局部预备，长期仍IN_PROGRESS/PARTIAL，不设置暂停或完成标记。
