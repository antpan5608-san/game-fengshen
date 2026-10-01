# Create Game / game-fengshen 云端接续记录

记录日期：2026-10-01（Asia/Shanghai）。用户指定后续在当前窗口继续开发。本记录保存已接入的上下文与实际权限状态，不宣称完整复制了电脑端会话或凭据。

本文件含接入和失败过程的历史记录；当前状态以末尾「v22 云端正式交付」为准：v22 已发布，runner 自动审批已实测成功。此前待确认、BLOCKED、等待审批及未验证状态均保留为历史，不代表当前阻塞。

## 接续入口与来源

- 本地仓库：`/workspace/game-fengshen`。
- origin：`https://github.com/antpan5608-san/game-fengshen.git`。
- 接入时分支：`main`；本地 HEAD 与 `git ls-remote ... refs/heads/main` 均为 `6ed46292fab36f480c1cb1e6958ae0d9202ce7e3`。
- 接入完成时工作区干净；本次只新增接续记录及 AGENTS 接续入口，未修改游戏源码或工作流。
- 执行规则：[AGENTS.md](../AGENTS.md)。当前任务：[current-task.md](current-task.md)。交付与累计欠账：[delivery-status.md](delivery-status.md)。云构建/签名/发布：[android-ci.md](android-ci.md)。原版路线：[original-playthrough-roadmap.md](original-playthrough-roadmap.md)。
- 历史文档保留原样。旧限制、旧未完成描述与后续已交付记录冲突时，按有效授权和最新交付记录解释，不将旧报告当作当前进度。

## 已接入的项目目标与成果

忠实还原 FC《封神榜：伏魔三太子》为 Android 游戏，主要设备 OnePlus 13T；当前连续内容目标为新游戏推进至南海龙王阶段。该里程碑仍未完成。

保留 Kotlin/Android Canvas/SurfaceView、统一 ContentLoader、World/InputState/FixedClock、统一游戏状态与 Local First 存档、既有 Go/PostgreSQL 云存档服务、原导出/取证/校验工具及独立 APK 更新发布机制。现有全屏地图、摇杆/HUD、NPC 对话、赠刀/装备、地图 114/16/0 往返、当前普通战斗与已核单哪吒逃跑/新游戏战败分支、地图 0 三店室内/买卖/返回均作为基线，不重复开发。

原版证据优先；Reference 暂定字段保留来源与未核标注，未知只限制依赖该事实的功能。不得编造规则、事件、商品效果，或把开发边界伪装原版墙体。探索 UI 冻结要求及既有存档兼容规则继续按 AGENTS 执行。

## 构建与发布接续状态

- 仓库记录的已发布基线：v21 / `0.8.1-town-01`，内容 `opening-segment-001-c11`。本次未重新下载正式对象核验其当前状态。
- 用户提供的首个云候选版本：v22 / `0.8.2-ci-release`。
- 已实际查询公开 GitHub API：[运行 36855448982](https://github.com/antpan5608-san/game-fengshen/actions/runs/36855448982)，`Android cloud build` / `workflow_dispatch` / `main`，head SHA 与接入提交一致，`completed` / `success`。
- build job 的构建、测试、既有签名/内容验证及保留 APK 步骤均为 success；`fengshen-signed-apk` 与 `fengshen-unit-test-results` artifact 在核验时未过期。
- 尚未下载候选 APK、读取其 receipt 或独立核验 APK SHA、签名与安装效果；不能仅由 workflow 默认输入推断 APK 内实际版本。
- 复用 `.github/workflows/android-build.yml`、`.github/workflows/android-publish.yml`、`build-ci.ps1`、`tools/ci_apk.py`、原巡检/发布/最近两版保留脚本；不创建第二套流程。
- production workflow 要求审核构建与发布来自同一 main commit。后续接续文档或代码一旦提交并推送到 main，旧 v22 构建不能直接用于新提交的发布；应按现有规则重新构建并审核，不放宽 commit 检查。
- 首次正式上传仍待用户明确确认。本次接入、权限排查和上下文接续未上传 APK、未触发发布。
- 仅允许既有 Fengshen 两目标：`oss://kubernetes-fleetpilot/artifacts/fengshen-remake/app/fengshen-remake.apk.bin` 和同目录 `version.json`。保留 `fengshen-production` 保护环境及 reviewer 审批，不触碰 Language。

## 用户授权与技术权限

用户授权在此窗口继续接手项目、按既有有效任务范围执行开发与验证；这不等于新增具体玩法任务，也不撤销首次正式上传确认门槛。后续明确任务与修订优先于历史授权。

当前执行环境允许本地文件操作和网络访问；已成功读取公开仓库与公开 Actions 元数据。GitHub CLI 身份查询本次返回失败，尚未确认仓库写入、push、Actions 触发或 environment 审批权限。项目授权与外部账户的实际访问能力分别记录；不假设电脑端登录会话已迁移。

运行环境状态工具未列出 secret bindings 或 outbound identities。GitHub Actions Secrets 属于相应 runner/job 的受保护输入，不代表当前云端任务拥有签名或服务器凭据。不得在聊天、Git、日志或 artifact 输出密钥/密码；不从未确认身份发起上传。

## 当前资源与环境缺口

- `reference/`、`private-derived/`、`android/app/src/main/assets/development/`、`android/local.properties`、`artifacts/` 在本次检查时不存在；电脑端未提交资料、私有原版证据和 ROM 尚未迁移。
- 云构建使用现有可信 APK 恢复锁定 c11 内容，并验证全部文件；不将 APK 恢复内容当作 ROM/私有取证迁移。内容变化继续使用原导出器与 provenance。
- 本环境有 git、gh、Java、Python、Go、Node；本次检查 PATH 未发现 `pwsh`、`adb`、`gradle`。独立 gradle 命令缺失不代表仓库 wrapper 或 Actions 不可用；本地完整 Android 构建环境尚未验证。
- 固定巡检：本次因 `pwsh` 不可用，`./check-runtime.ps1` 未执行，状态 `NOT_AVAILABLE`；没有伪造标准巡检报告或服务器健康结论。后续源码修改按原规则处理巡检及实际凭据缺口。
- 手机、模拟器未在本次运行；一加 13T 验收保持 `NOT_RUN`，不以历史模拟器结果替代。
- 电脑端 Create Game 聊天中未写入仓库的决定、待办、未提交 diff、私有资料和账户登录状态不可自动读取；接收到补充后再合并，保留来源和差异。
- 网络故障已排除：平台策略文件 `/etc/codex/network-policy.json` 与继承 HTTP(S) 代理均指向 `proxy:8080`，ALL_PROXY 未设置，Git 无显式代理覆盖。原默认沙箱连接返回 `Operation not permitted`；批准网络访问后 TCP、ls-remote、clone、公开 API 均成功。保持平台代理和 TLS 校验，不绕过网络限制。

## 累计未完成清单

以下保留最新 TOWN-01 交付的十类欠账；完整描述与测试边界以 delivery-status 为准，只有实际完成才移除。

1. 商店/装备：跨类别出售、已穿装备直接替换与满包副作用、左手及其余商品；药草/牛黃丸使用效果与合法场景/目标。
2. 地图 0：客栈收费/恢复/离店、住宅/特殊建筑、其余 NPC/宝箱/事件条件、室内原版 BGM。
3. 后续路线：地图 16 其余遭遇区/特殊格/事件、南海真实连接、龙宫/Boss/胜后状态；已完成三图往返不再列为缺口。
4. 战斗规则：玩家命中/暴击、完整顺序、法术/物品/防御、Boss、原版手动存档后的战败加载分支；已核单哪吒逃跑/新游戏战败保留为已实现。
5. 战斗展示：敌人 1 原名、完整动作、全字库、时序/框位/逐项奖励/调色、原版随机序列差异。
6. 音频：历史超时根因 `UNCONFIRMED`、四类短音效、精确循环、长时真机稳定性。
7. 开局与剧情：旁白时机、NPC 移动、部分首次/复谈 flag、赠金整事件、后续入队/法术/剧情。
8. 更新器/设备：一加 13T 原误报、实际覆盖升级、触控、声音、性能验收。
9. 云端/内容：真实账号本地丢失/多设备恢复未验收；Go 内容版本化与 Android 下载/校验/缓存/离线回退未接通。
10. 完整发布：全量地图/剧情/规则与 canonical、南海龙王里程碑仍未完成。

距离下一原版剧情节点最近的三项阻塞保持：地图 0 必要 NPC/服务事件；通往南海的真实连接/条件；剧情 Boss 与胜后状态。不因此重启全量研究。

## 后续工作入口

每轮先读取四份指定文档及本记录，核对工作区、分支和 HEAD，再依据用户本轮具体需求推进。未经用户指定不自动展开新玩法区段；需要 ROM 或电脑端未提交资料的任务明确标记缺口。构建、测试、APK 审核和受保护上传沿用 android-ci 文档；缺少凭据、巡检或真机证据时报告真实覆盖范围，不将缺失当作通过。

## v22 发布授权与实际阻塞（2026-10-01 更新）

用户明确指示「现在完成一次v22版本的发布」，已授权本次 v22 正式上传，取代上文该版本“待用户确认”的状态。无需重复请求同一次发布授权；仍须核验实际 APK、SHA、签名和目标，并遵守已有 workflow 的环境保护。

本次已实际核验远端 main 与 build run 36855448982 均为 `6ed46292fab36f480c1cb1e6958ae0d9202ce7e3`，构建 `completed/success`；公开服务器 version.json 仍为 v21 / `0.8.1-town-01`，SHA `ed700b85c79bd3abfdc64dbf35cfa517b68d4ad9b4eaa7158c3ad20aa3dd30d1`。查询现有 `android-publish.yml` 运行记录返回空列表。

当前状态：`BLOCKED_AUTH / NOT_PUBLISHED`。实际命令 `gh api user` 返回 `HTTP 401: Bad credentials`；`gh run download 36855448982 --repo antpan5608-san/game-fengshen --name fengshen-signed-apk --dir artifacts/ci` 也返回 `error fetching artifacts: HTTP 401: Bad credentials`。网络与公开 GitHub 访问正常，此次阻塞属于 GitHub 身份认证，不能称为平台代理故障。

签名 APK artifact ID 为 `11158791928`，查询时未过期。API 返回的 `sha256:1c82ac40f2b4f6925fddae00c4f226599b0077abba090ee8a21ef22f8a315a6d` 是 artifact 归档 digest，**不是 APK SHA-256**，不得用于 expected_sha256。实际候选 APK/receipt 尚未下载，不能编造其 hash 或宣称已完成独立签名/版本验证。

恢复有效 GitHub 身份后，下载同一不可变产物并核验 receipt/APK/main 提交，再触发现有 `android-publish.yml`，使用实际 APK hash；随后跟踪 `fengshen-production` 保护环境和原巡检/上传/公网字节校验/postflight。不会重新构建被审核的 v22 或另建发布流程。GitHub 插件已发现但尚未确认安装/连接；不能假定建议已生效。当前服务器凭据未接入本任务，不能本地绕过既有生产工作流。

本次未触发正式发布、未修改服务器对象。全部累计未完成项保留。

## v22 正式发布已触发，等待环境审批（后续更新）

用户完成 GitHub 接入后，`gh api user --jq .login` 已返回 `antpan5608-san`；仓库 API 返回 admin/push 权限。远端 main 仍为 `6ed46292fab36f480c1cb1e6958ae0d9202ce7e3`；本地接续文档未推送，未改变待发布源提交。上文 `BLOCKED_AUTH` 已解除。

本环境下载 artifact 时，官方 `productionresultssa14.blob.core.windows.net` 的代理 CONNECT 返回 `403 Forbidden`。未绕过平台代理或禁用 TLS。通过官方 GitHub 构建日志 API 的正常 `results-receiver.actions.githubusercontent.com` 路径取得成功 build 的验证 receipt；这不代表本地已下载 APK。

实际日志 receipt：`org.fengshen.dev`，v22 / `0.8.2-ci-release`，APK SHA-256 `270a7a035adec342cdfcb03e249f9130e35b0610db94ed0695f010d075ad7aa7`，11,316,827 字节，既有 signer `5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6`；c11 内容 hash `3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147`，receipt 报告 42 个内容文件。该文件数来自实际云端日志，不将历史报告 41 文件数改写为本次独立核验结果。

已向用户展示实际版本、APK hash 和 Fengshen 两个目标，并复用既有 `android-publish.yml` 手动触发 [发布运行 36868615998](https://github.com/antpan5608-san/game-fengshen/actions/runs/36868615998)，输入 build_run_id `36855448982` 与上述 APK hash，不重新构建。运行提交匹配，当前 `waiting`，尚未上传。

`pending_deployments` 返回 `fengshen-production` / environment ID `23198917172` / `current_user_can_approve=true`，required reviewer 为 `antpan5608-san`，`prevent_self_review=false`。按用户明确发布授权调用 review API，实际返回 `HTTP 403: Resource not accessible by integration`。不能把 API 拒绝写成审批成功；未取消保护规则或绕过审批。

状态：`WAITING_ENVIRONMENT_APPROVAL / NOT_PUBLISHED`。用户需在该运行页面执行 Review deployments → fengshen-production → Approve and deploy；已通知用户。这是现有 GitHub 环境技术控制，原 v22 发布授权继续有效，不重复索要授权。审批后继续跟踪 runner 的同产物核验、preflight、两对象上传、公网 SHA/大小校验、两版保留与 postflight；实际成功前不写已交付。一加 13T、本地独立 APK 校验和累计内容欠账状态不变。

## 审批已完成，报告读取修复（后续更新）

用户已在 GitHub 批准运行 `36868615998`，并明确要求后续自动代为审批。授权已写入 AGENTS，限定已授权且校验通过的 Fengshen 发布；保留生产环境和目标边界。

本次运行完成产物/签名/版本/内容复核，实际 runner preflight `NO_ISSUES_OBSERVED`（仅已上传样本窗口；不是全设备健康证明）。随后在 workflow 读取报告时失败：`The provided JSON includes a property whose name is an empty string ... -AsHashTable switch`。错误位于 workflow 消费 JSON，不是巡检脚本、APK 或服务器故障；上传未开始。修复只为两处 ConvertFrom-Json 补 -AsHashtable，保持其他校验；既有内容 transport 12 项单元测试通过。本地 PowerShell 尚不可用，真实 parser 回归由后续原生产 workflow 验证，不将 Python 通过写成 PowerShell 通过。

源提交改变后需要按原同提交规则在新提交重新构建并审核 v22。原失败运行/原构建记录保留，不降低校验以复用旧提交、不另建工作流。自动审批 API 的技术权限仍未解决，不能承诺已配置成功。

## 自动审批由既有生产 workflow 执行（后续更新）

用户表示已在仓库配置 `FENGSHEN_DEPLOY_REVIEW_TOKEN`。当前集成的 Secret 列表 API 返回 403，不能由该错误断言 Secret 缺失；实际有效性须由 runner 运行验证。当前云环境 Secret 列表为空不代表仓库 Secret 不存在。

已在原 `android-publish.yml` 增加并行 approve job，使用上述仓库 Secret 注入其 GH_TOKEN；仅审查同提交的成功 main 手动构建，批准当前运行唯一的 fengshen-production 环境，保留 required reviewer 与主分支规则。未新建 workflow，未删除环境保护，不把令牌交给构建/上传脚本。生产 job 继续执行原签名/内容/hash/巡检/上传/公网校验。真实身份/权限错误保持失败，不自动绕过。

本地实际运行该 job 的 Bash 代码，14 个 fixture 检查通过：可信构建批准确切目标；缺令牌/非法标识/失败或未完成构建/非手动事件/异提交/异分支/异workflow/不合格 reviewer/多目标/其他环境/API 拒绝均不成功审批。既有 transport 12 项通过。结果存于忽略的 artifacts/ci/auto-approval-test-results.json；这些是隔离 fixture 验证，不代表真实 Token 或生产审批已通过。

解析修复提交 f8fc3a4 的 v22 构建 `36870720449` 成功；其发布 `36871347819` 尚未执行上传，需取消等待中的旧提交运行再构建新审批配置提交，防止两份不同字节的 v22 竞相发布。实际新构建/审批/正式发布结果后续追加；累计游戏欠账与真机 NOT_RUN 保持。

## v22 云端正式交付（2026-10-01 最新状态）

状态：`PUBLISHED_AND_VERIFIED`；ANDROID-CI-01 本轮完成。v22 / `0.8.2-ci-release` 于北京时间 2026-10-01 23:17:27（UTC 15:17:27）发布；生产 workflow 于 23:21:47 全部成功。游戏仍为 c11 开发内容，不代表南海龙王或完整原版里程碑完成。

- APK 来源提交：`f7dfea747999f6b1ee497179f98eb6cc7ed94f6a`；[构建 36882142423](https://github.com/antpan5608-san/game-fengshen/actions/runs/36882142423)、[发布 36882936289](https://github.com/antpan5608-san/game-fengshen/actions/runs/36882936289) 均 completed/success，构建与发布同一 main 提交。后续文档提交不改变已审核 APK 来源。
- 下载：[v22 APK](https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=22)；11,316,827 字节，SHA-256 `5cecfe1c3a4208ea077ef8da7fb338e46ad9419dc2551dfb7a61952b230bf72f`。包名 `org.fengshen.dev`，既有签名证书保持；release 非 debuggable、版本、签名、内容及旧包兼容已由原 runner 复核。
- c11 / `opening-segment-001-c11`，manifest SHA `3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147`。本任务于北京时间 23:23:22 独立下载正式服务器 APK，完整 SHA/大小与 version.json/构建 receipt 一致，原 `tools/ci_apk.py::content` 验证 42 文件（包含 manifest；41 内容文件）及全部文件 hash。
- 自动审批实际成功：原 `android-publish.yml` 的 approve job 使用仓库 Secret `FENGSHEN_DEPLOY_REVIEW_TOKEN`，通过原 reviewer 政策批准 fengshen-production。随后 publish job 全部成功，无需再次人工点击。现有集成本身的 Deployments API 403 不影响这个已验证的 runner 途径；不代表当前云任务拿到了该 Secret 值。令牌到期/撤销或保护规则变化仍可能明确失败。
- 实际 `./check-runtime.ps1` preflight：UTC 15:17:21，可信21/20，418事件/41测试事件，普通11模拟器+1真机会话，窗口08:13:38—14:11:04，NO_ISSUES_OBSERVED。
- 实际 postflight：UTC 15:21:41，可信22/21，363事件/14测试事件，普通6模拟器+1真机会话，窗口08:27:54—14:11:04，NO_ISSUES_OBSERVED；清理失败0。当前保留样本来自v21，v22尚无上传样本；历史设备日志不是本轮手机验收，不证明v22运行或历史音频根因修复。
- 保留既有两版规则及每版20MiB容量；本次上传只覆盖 Fengshen APK/version.json 两对象。ROM、密钥和电脑未提交资料没有由本次迁移取得；无新游戏源码、UI或功能开发。
- 验证：实际云端 testReleaseUnitTest/assembleRelease/签名/内容与旧包兼容检查通过；本地自动审批实际 Bash fixture14项、既有 transport12项通过。没有本轮 adb/模拟器/App录屏或真实账号云恢复测试；一加13T覆盖升级、触控、声音、长时性能均 NOT_RUN。

下一迭代继续复用现有构建与发布，versionCode 必须大于22；既有默认22是首次构建的历史值，必须显式覆盖。累计十类未完成项与下一原版节点三个阻塞全部保持，详见最新交付报告。
