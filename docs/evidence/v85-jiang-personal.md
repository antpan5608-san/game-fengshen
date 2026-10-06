# v85 / c61 个人阶段交付（2026-10-07，北京时间）

状态：IMPLEMENTED / PACKAGED / APP_VERIFIED_PERSONAL / PUBLISHED_AND_VERIFIED。
quality=PERSONAL_TEST，manual_acceptance=PENDING；长期WORLD-FULL-01仍ACTIVE/PARTIAL。

原build [37516065030](https://github.com/antpan5608-san/game-fengshen/actions/runs/37516065030) 和publish [37518266540](https://github.com/antpan5608-san/game-fengshen/actions/runs/37518266540) 均SUCCESS。
同源提交230b999cb5c0269484f4c61b0bf58256d2ae800c，85 / 0.8.15-jiang-personal。

APK32,481,873字节，SHA256 `0fafb10d61fdd7511b8c84f875ea43bdb84db8ba9a8b72258767fb16a5d90baf`。
包名org.fengshen.dev，原签名 `5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6`。
内容opening-segment-001-c61，manifest `37f0f7bb1080f6fe59f3853928c7e5006c2974d6f3ca5698713b2a37f5747557`，388文件/72依赖图；分母未知，不能算72图正常可玩。

下载：[原公网APK入口](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=85)。沿用覆盖安装，保留玩家存档。

## 本轮内容与实际验证

- 姜子牙邀请/四人入队与阶段存档，磻溪两页对白保持模态所有权；按B不把待完成阶段变成可移动状态，完成不重入队。
- 冷启唯一旗标缺失→false差异已复用原map7状态重建修正，before/after整个JSON等值，不删任何字段。
- 原武器7/44命中均从匹配ROM核为51；补原span，加载器拒绝可操作/初始武器缺表，修复真实Key44 AndroidRuntime崩溃。
- 四人引擎收集前三指令不结算，第四人后执行原回合；四人物品目标绘制与命中共享2×2几何，只选目标不耗物品，胜利只结算一次。

411 release JVM /89 suites零失败、错误、跳过；21实际ContentTest全部通过。18同签名个人门禁全部PASS：原覆盖升级、内容、触控交易、双人补给住宿、诊疗进退、药草战斗、不可恢复存档保护、首次迁移备份、外部cold，以及受控版本字段、井codec、历史回档、回档cold、损坏/20档保留；新增姜codec、受控触控邀请入队、外部cold、四人战斗。

真实App保留六段原MP4，各段完整SHA均独立核实，所有cold/prefs恢复和截图/末帧已查看。姜recording SHA `d8c41aa2ccd14d09ca448217092534e16ec929978a651ae41b63b6803555a017`；完整cold boundary SHA `dddd784ae0e0303fc8037ed133c166b688d82a7cb55d5eca62492a1c203c63b3`，equal=true/差异字段空。两摘要均保留在同源runtime receipt。这些是来源明确的隔离受控App冒烟，完整正常剧情、真机及声音仍待验。

原审批、main同源、同审核APK、签名、递增版本和两对象保护全部沿用；无绕过。发布后再次从公网下载全部字节并用原ci_apk.verify核包名/签名/版本/内容。实际postflight 2026-10-06T19:22:04.7418420Z：NO_ISSUES_OBSERVED/errors为空/cleanupFailures=0，权威保留85/84。上报样本仍来自v84，不能证明v85真机健康。

## 失败、限制与接续

原DEBUG冷启差异、原四人Key44崩溃和签名37513917589的兼容样本失败均保留。首次签名411 JVM与20/21 Content通过，合成c1删除combat却保留后期originalJiangJoin，被生产guard正确拒绝。只修合成样本，并补“当前c61保留事件但无战斗必须拒绝”的反例；生产规则、21方法和18门禁未削弱。新同源完整签名验收才用于发布。

本地辅助模型被独立GPU训练占用，相关审查实际0片段/身份不可用或超时；Codex亲自覆盖9文件及后续1测试文件、完成相关验证并记录codex_fallback，不声称4B审查PASS。

原四人胜利摘要有裁切、旧战斗未常驻全队，下一批落实敌左我右/队伍状态卡、结果可读性、默认查看敌人/先选动作再目标及角色/物品摘要，覆盖实际最多6敌/4队员和大字体。当前71物品含14药品是目录，不等于全部效果已实现。房间28、点击寻路、原指令、正常主体剧情至结局及十类累计欠账继续开放；历史STABLE仍v82。长期三终点未达成，继续自主研发，不能因本版发布停止目标。
