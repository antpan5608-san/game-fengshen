# v92：攻击／施法姿态及两种敌人图

PUBLISHED_AND_VERIFIED / PERSONAL_TEST，manual_acceptance=PENDING；稳定v82保持。WORLD-FULL-01仍IN_PROGRESS/PARTIAL，三项长期终点未完成。

源码 `644ceae14c1697c3fee01171591b43bc77554625`，原Actions build `37688806409` / publish `37694244393` 均SUCCESS。版本92 / `0.8.22-battle-pose-personal`，完整62,588,756字节，SHA256 `6ef2725ff17afeb46c74bd209bb0fca561a5934f53584a0175fdf123d649a91f`。原包名org.fengshen.dev、原证书SHA5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6、c62/392文件/625a314a清单保持；battle-visual-02-r2共16图加清单17文件，清单SHA8dc53b77027055a2b9a2ec37a80e2aba3113e668bd350ab822c14c3d85794f30。

[原下载入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=92)。publishedAt为2026-10-07T22:10:10.2830155Z。仅原两个Fengshen OSS对象更新；签名和Secrets仍在原Actions，服务器公网完整下载与审核签名APK逐字节相同，版本/发布回执也独立核对。

本批按来源/hash选择性接入Windows交接的哪吒攻击、小龙女施法、敌1/137四张原创图，旧12图字节不变。ContentLoader原worker准备固定16图，Canvas只查准备快照，不再调用源文件、同步缓存锁或解码。真实BattleActionStep身份/计时选姿态，统一等比裁框/脚底、绘制/目标框；攻击/法术/HP/MP/RNG/奖励/入队/存档规则未改变。固定16图不是按当前战斗加载；实测保留bitmap39,316,116字节不等于进程峰值或手机60fps。

新实际正式445 release JVM/95 suites零失败错误跳过；原CI16组Python报告122通过；Android21 Content与36个人门禁通过，原runtime_handoff独立raw审查通过。新三字体真实队列准备MP44/HP5→效果44/58→晚扣费41/58→哪吒ATTACK，绘制前后完整状态/RNG/fight.party不变。正常新游戏/NPC/赠物/三店补给、自然战斗真实攻击胜利EXP+1/银两+1、药草HP16→20、保存及外部force-stop冷启实际通过；受控四人/法术/Boss单列，不冒称正常入队或完整主线。

实际查看121张必需原PNG的31页索引及12新姿态原大图，14原MP4完整SHA、7完整cold前后状态及隔离偏好恢复核验通过；实际查看60帧采样（32常规、16正常细节、12正常后段）。正常片45.25秒胜利奖励、49秒药草选择16/20、49.75秒使用后20/20可见。采样用ffprobe实际编码时长，原录像/录制器墙钟时长均保留；不是全程连续播放、声音或手机验收。四人身体重叠、2倍字体战场小、旧SPECIAL全场闪白、其他动作/敌人/环境及按战斗需求加载仍OPEN。

首source58d066b/build37686148098实际App在旧Boss128/112比例断言FAIL，签名包SHA595e3736、原全部日志/图/片保留，未发布。仅仪器三处改成实际选中绘制asset比例，仍保留native本体/安全边界/48dp/奖励/保存断言；新source重新正式构建/App通过，不复用首run部分结果。

postflight实际2026-10-07T22:12:05Z查询成功：NO_DATA，正常事件/会话/实机均0，errors空、cleanupFailures0、权威保留92/91。当前没有两版上传样本，不能据此称运行健康或手机通过。回执 `/srv/fengshen-dev/receipts/visual-pose-r2-{public-full-byte-verified,postflight,actual-image-review,video-byte-cold-verified}.json`，新原证据 `.local-ai/visual-actions-37688806409`；迁入改动、失败及旧历史保留。

最新codex_only快照585b7d92已按真实源码及新正式检查登记，Stopcurrent/continue=true。发布后更新覆盖基底为实际92，历史iteration27保持；下一同任务有限批次：仅原已识别HEAL/ANTIDOTE按真实合法目标显示柔和局部光效，保留unknown/敌special/药草回退，重新审查/测试及原Actions同签名验收。真机帧耗时/内存、声音、完整正常原结局、真实账号多设备恢复以及全部长期欠账保持OPEN；没有长期完成回执。
