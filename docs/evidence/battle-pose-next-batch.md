# 视觉姿态／敌图下一有限批次

状态：PREREQUISITES_IMPLEMENTED_LOCAL_VERIFIED，正式候选未冻结／App未验／未发布。线上v91与稳定v82保持；WORLD-FULL-01继续IN_PROGRESS/PARTIAL。开发唯一入口仍current-task，本文件是同任务接续证据，不创建另一任务。

发布v91后已再次用prepare_context（CPU／无后台推理）定位，实际读当前ContentLoader、BattleVisualAssets完整源码、MainActivity统一battleScene绘制/敌图框、BattleActionStep/原调度，以及Windows交接纯几何/新视觉源码和测试。基于服务器成果选择性适配，未整树覆盖、未加第二加载器。

## 当前代码和真实检查

- BattleActionStep保留原构造ABI，在body增加internal-set的abilityId；仅从原实际执行法术spell.id或药草HerbUse.ID填入。不提前处理命令、不解析提示文字，不采RNG或改变HP／MP／奖励／保存。施法准备、效果、晚扣费、post-HP状态帧保留原顺序；旧帧默认null、敌special和物理分支不伪装法术。
- BattleVisualGeometry复用交接的纯裁框/脚底函数，MainActivity原待机绘制调用同一公式；没有改变本轮画面选择、缩放或移动距离。纯pose投影仅为下一图片接入口：执行角色ATTACK与已识别合法法术CAST、药草／未知／敌special／旧未识别帧IDLE；当前Canvas仍画旧待机，不能称攻击／施法图已接入。
- 真实全JVM445项／95 suites，失败、错误、跳过均0；应用与仪器DEBUG编译BUILD SUCCESSFUL 9秒。本地参数92／0.8.22-battle-pose-personal仅DEBUG检查，未占用正式候选／发布。新4项纯几何／帧身份检查及原真实magic/herb调度断言覆盖三字体／1至4人／实际候选裁框、等比／脚底、非法几何拒绝、既有HP/MP阶段与RNG计数。不是App／头部尺度连续／手机证明。
- 实际逐字节核SaveState/SaveHistory/CloudSave/WorldItems/OriginalBattleMagic仍同v91。对Battle完整源码移除仅新增展示身份声明／参数／赋值后，与前一源码逐字节相同；新增行真实审查，不能用测试通过声称读过diff。
- 本批preflight2026-10-07T20:30:02Z NO_ISSUES_OBSERVED/errors空/cleanup0，69事件全v90，v91样本0。服务器回执`/srv/fengshen-dev/receipts/visual-pose-prerequisites-reviewed.json`、构建日志`visual-pose-prerequisites-build.log`；全部差异/新增测试实际阅读后登记本批新codex_only快照，不复用v91审查。

## 具体后续实现入口与缺项

1. 素材在`.local-ai/windows-visual-handoff-20261008/android/app/src/main/assets/visual/`：nezha-attack.png（SHA093a030d…a7bc5）、xiaolongnv-cast.png（e71a03a5…a625b）、enemy-1.png（e5f789e8…dbc8a7）、enemy-137.png（9f60df61…8c5b8）。四master原字节、原调用／5份精确提示词及哪吒自制v1→留白v2链已核，完整SHA／字节／裁框入口在服务器receipts/visual-next-pose-geometry-inputs.json和visual-image-prompt-provenance.json，原ZIP／失败／商业参考仍私有保留。
2. 在现有BattleVisualAssets中添加四个明确ID引用及严格hash／真实解码，不复制交接第二同名类；c62/392规则包不变，独立视觉清单与原ci_apk验包、scope/proof要一起绑定新真实快照。当前12图清单448091仍v91资产，不能将新四图/16图清单冒称已打包。
3. MainActivity从battleVisualPose与既有BattlePresentation时间读取姿态。真实图片alpha裁框比例与脚底已有输入；头部尺度、idle↔attack／cast切换、移动身体与目标轮廓连续尚无新App证据，需真实ready的1/1.3/2截图／录像。旧先验native敌图比例断言需绑定实际选中绘制asset，同时保留48dp／互不重叠／安全边界。
4. 复用原Content加载worker或受控图片准备：按同一battle对象／battleID／token拒绝过期结果；Canvas只读已准备快照、缺素材旧图回退；不要在Canvas调用worker正在持有的同步ResourceMap缓存锁。缓存保留上限不等于解码峰值／真机60fps。当前未实现此准备改造，不声称已解决首帧解码风险。
5. 继续同一正常新游戏NPC／物品／真实攻击胜利／依法药草／完整保存／外部cold验收；合法小龙女法术、四人／Boss单列受控。旧敌图黑底／两倍字四人战场、其他动作／敌人／环境仍欠账。完整主线／声音／一加13T帧耗时及内存／真实账号多设备恢复没有本轮输入，精确保持PENDING，不阻塞此本地适配。

完成有限图片接入后，重新完整审查／相关检查／record_review_decision，原Actions签名／同源App／审核产物全部通过才递增版发布、公网完整字节与postflight；该前置代码检查不替这一门槛。所有长期终点持续开放，服务器独自接续，不回Windows执行。
