# 服务器选择性接收 Windows 视觉交接

2026-10-08北京时间。服务器是唯一游戏代码与发布写入者；Windows写入冻结、旧监控暂停，检查点不回Windows。人类确认全部任务迁移37,472文件/7.22GB，原历史/旧树保留于 `/srv/codex-migration/20261008`、`/srv/codex-workspaces/legacy-fengshen`；这是继承的人类迁移事实，本游戏轮未重算全盘集合。

已实际读取 `/srv/fengshen-dev/inbox/windows-handoff-20261008/windows-freeze.md`，核ZIP29,145,116字节/SHA `29b84c7c2127d415c4e9370f7236ce567f09b7f3755368e26dedf93b09f7a0e1`、安全条目，隔离提取48文件于 `.local-ai/windows-visual-handoff-20261008`。未整树覆盖；哈希比较和选择清单另存服务器receipts。

## 合并与保留

- 仅合并已完整阅读的 `ResourceMap.kt`、`ResourceMapTest.kt`，先证明服务器这两文件与4b157ad基线相同，再加入按字节LRU、失败可重试、超大/负权重拒绝，保留三参数ABI和身份索引。现有 `BattleVisualAssets` 复用该缓存，64MiB不变；有界读取方案适配到现有 `AssetSource.readVisual`，清单64KiB/图4MiB，未加第二系统。
- 12张交接图与服务器12张完整SHA相同，仅命名/元数据方案不同，不重复打包。另4张哪吒攻击、小龙女施法、敌1、敌137已核完整字节/PNG尺寸并逐张目视，继续候选，不进本轮12图清单。
- 交接的另一个视觉加载器/异步准备/动作几何/abilityId/绘制/独立验包器和测试全部留隔离快照与原ZIP；保留服务器布局、绘制、正常路线及35门禁。下一姿态批次再按只读快照选择性复用，并处理敌图黑底、脚底/比例、异步过期结果及主线程缓存读问题。

交接16图清单SHA `a1be5d81353d4ab78f7a7caf19483bd895a1313b8ef833a3d9cff258156ba741` 完整有效，不替换服务器12图清单。`action-prompts.json` 实际有五条记录，含哪吒两条，与冻结说明“未取回”存在时点差异；已读文件并保留，不编造提示词，现已从迁入原会话实际核对5份精确提示词、4原imagegen调用/完整masterPNG哈希及哪吒留白v2自制v1引用链；仅私有receipts留索引，未输出其他原历史或凭据。地图主题差异继续作者配置/待核，不升级原版环境证据。

## 实际检查与失败

已读交接原Gradle日志，并从四份原XML汇总21项/0失败错误跳过；只是Windows局部历史检查。冻结文件记述4+16 Python共20项，本轮未复跑或当作服务器App证明。

合并后实际服务器全量441 JVM/94 suites，失败/错误/跳过0；相关Python88项/7.626秒OK；应用/仪器DEBUG构建成功，正确91版/c62/392及视觉13文件完整验包通过。无执行位gradlew和错误参数名命令记录保留，后改用 `bash android/gradlew ... -PfengshenVersionCode=91 -PfengshenVersionName=0.8.21-battle-visual-personal` 并核APK；错误参数包未做App证明。

新原生产preflight 2026-10-07T19:21:58Z，NO_ISSUES_OBSERVED/errors空/cleanup0，3939事件/2实机会话（89:3870，90:69），仅上传样本，不当91手机证明。

首轮source110f8c7/build37669616584正式签名及439 release JVM通过，APK57,538,609字节/SHA `25dc978fecee634761ad0fbd5f8fd7c913e6908977d7ff6b418773f773204d2c`。正常攻击/药草及外部cold、其他部分原片证明可核；整体runtime汇总FAIL：实际Surface1216，旧验收固定1080/936。日志、185图/14原片及原指标留 `.local-ai/visual-actions-37669616584/runtime-failed`；不补造旧Insets，不把整体改PASS。

修正读取实际Android Insets与安全区，只接受原1080或全屏1216已测Surface，保留2640×1216截图/三字体/8原生矩阵/48dp/互不重叠和安全区边界；新候选强制原始测量并绑定scope哈希，历史scope原要求保持。缺测量/伪造/越界反例拒绝。最新代码须新codex_only回执与原Actions重建/App复跑；旧失败候选不替新检查。

## 接续入口

先完成12图候选新同源构建、35门禁、实际原图/原片/完整保存复核；仅原门槛全满足后两对象发布、公网完整字节/postflight。随后从保留的4张姿态/敌图及只读动作标识推进下一有限批次，按实际绘制图框验受控比例，不改攻击/法术/奖励/加入/存档。其他角色、受击/胜败图集、敌人/环境、真机性能/声音、正常结局及三项长期终点继续OPEN/PARTIAL。

## 后续新同源实际结果

sourcec323bfd/build37675332933正式签名和runtime均SUCCESS，release441/94零失败错误跳过，16 Python组报告122；原CLI独立review35门禁/7完整cold/14原片全SHA及偏好恢复通过，实际目视109原PNG/44采样帧。原publish37681167754成功，v91公网57,540,785字节/SHA6304e3ecba1b102a64d6c430241bd0e206e247ce351d8a9deb692ada017bf668与审核产物逐字节一致，c62/392+视觉13及原签名保持。postflight20:22:22Z无上传样本问题，69事件均90；91手机/声音仍未验。见[v91交付](../evidence/v91-battle-visual-personal.md)。当前服务器检查点不回Windows；下一四图姿态/敌图有限适配已CPU定位/读真实源码，未整树合并原未验实现，所有未完成项保持。
