# 封神榜 Android 复刻与原版取证

**当前已发布基线：v22 `0.8.2-ci-release`，保留开发内容 c11。** 已有全屏地图、摇杆、HUD、NPC、赠刀/装备、114/16/0往返、当前普通战斗及地图0三个商店的室内/交易。完整原版内容和南海龙王里程碑仍未完成，具体范围与累计欠账见[交付记录](docs/delivery-status.md)。GitHub Actions 同签名 release 构建、原服务器上传和自动环境审批已实测成功；[云构建与发布说明](docs/android-ci.md)。旧报告保留为历史，当前有效规则见[AGENTS.md](AGENTS.md)。

以下为保留的原版研究基线状态，不能解释为禁止已授权的检查点A。

手机下载：[封神 APK v22](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=22)。手动下载需去掉文件名末尾 `.bin` 再安装。已有旧 APK 可用“设置 → 检查应用更新”在应用内下载、校验并交给 Android 确认安装。当前服务器 SHA-256 `5cecfe1c3a4208ea077ef8da7fb338e46ad9419dc2551dfb7a61952b230bf72f`。云端先构建并审核实际 APK，随后单独触发受保护上传，现有 approve job 自动完成已授权环境审批；仅覆盖 Fengshen 两对象，不影响 Language。`./build-android.ps1 -LocalOnly` 保留本地开发验证。

当前状态：**PARTIAL_V1 / PHASE_1_INCOMPLETE**。已解疑初始升级7与累计阈值12，实测哪吒前两次升级；提取11张地图（两张各900个PPU索引核对）、12段开局文本（2段实测）、10条NPC记录、177条敌人候选（3种各5字段实测）与320条成长候选。381次正常步行碰撞检查通过；完整Reference行等价仍为0，canonical保持阻断。`READY_FOR_PHASE_2 = NO`。

在本目录PowerShell执行（首次依赖安装：`./phase1.ps1 setup`，需要Python 3.12+）：

```powershell
./phase1.ps1 import-reference
./phase1.ps1 validate
./phase1.ps1 viewer --open
./phase1.ps1 rom analyze
./phase1.ps1 rom research reference/rom/candidate-f3596ffda5c1b838.nes
```

Reference地图查看器位于`reports/map-viewer/index.html`；独立ROM列表位于`game-data/raw/rom/map-viewer.html`；含NPC和Collision的V1查看器位于`game-data/raw/rom/vertical-slice-viewer.html`。均可离线打开。ROM放`reference/rom/`；工具不会修改它，Git会忽略ROM及派生图像/原始数据。一加 13T UI 要求见`docs/android-ui-design.md`；当前 Android 已实现全屏场景和悬浮操作层，但云存档尚未在一加 13T 真机验收。

[Phase 1报告](docs/phase1-report.md) · [原版基线](docs/original-baseline.md) · [ROM报告](docs/rom-analysis.md) · [数据校验](docs/data-validation-report.md) · [差异报告](docs/reference-vs-original.md) · [数据层说明](game-data/README.md)

其他命令：`./phase1.ps1 compare`、`./phase1.ps1 canonical`、`./phase1.ps1 test`。Validator退出0只代表无ERROR，不等于原版验证通过。完整命令与限制见各tools目录README。

参考：`v5100v5100/FengShenBang`，固定提交 `d636453f14f86a096f9d293bf3facfd96cfcb614`（2020-05-28）。本地参考快照位于 `F:/apps/FengShenBang-reference`，与本仓库分开保存。

Phase 0原始文档（保留为Phase 1输入）：

1. [参考项目审计](docs/reference-audit.md)
2. [游戏数据清单](docs/game-data-inventory.md)
3. [整体架构](docs/architecture.md)
4. [数据 Schema 设计](docs/data-schema.md)
5. [迁移策略](docs/migration-plan.md)
6. [分阶段实施计划](docs/implementation-plan.md)

关键结论：内容候选很多，但运行程序是原型；存在改编内容，不能把数据库直接标为原版。原版覆盖比例、完整对话和准确战斗公式均未得到验证。

Phase 0证据与复现命令见[审计工具说明](tools/reference-audit/README.md)。历史审计结果仍保留；当前 Android 与独立云存档服务的实际范围以上述交付为准。
