# Android 云构建与发布

仓库：antpan5608-san/game-fengshen，main。保留游戏内容/技术栈，不迁移为模拟器。已发布 v23 / 0.8.3-town-02 / c12，在v21/c11已有成果上增加药草地图/菜单使用；云构建、同签名和原服务器发布已验证成功，不代表原版内容里程碑完成。

## 构建环境与必要输入

Windows 2022 Actions runner，Temurin JDK 17，SDK platform 35/build-tools 35.0.0；Gradle wrapper 8.10.2（发行包及 wrapper JAR 均锁 SHA），AGP 8.7.3，Kotlin 2.0.21。既有依赖 WorkManager 2.9.1、Media3 1.4.1、JUnit 4.13.2不变。发布桥复用 ssh2 1.17.0（npm lock），ossutil 2.4.0 官方二进制校验后安装。官方 Actions 固定到提交，不用浮动标签。

ROM、reference、private-derived、development assets、APK、密钥、本机 SDK/local.properties/.venv 不能入 Git。当前局部迭代从可信基底复用不变素材，具体新增定义以已提交provenance为输入；不假设ROM/历史私有目录已经提交。`tools/ci_apk.py restore` 从现有公开发布 APK 恢复其 development 包，先验证 metadata/APK size/SHA/package/signer，再验证 `ci/content-source.json` 锁定的 manifest/version 和全部文件，最后写入原 ContentLoader 目录。实际目标由ci/content-source.json固定；iteration.base单独锁定可信c11 APK字节/manifest，原导出器局部转换后必须完全吻合目标pin。不是截图地图，不是第二套 importer。

公网对象更新后，同一内容 hash 仍可复用；如果服务器改成新内容版本，旧 pin 构建会明确失败。内容变化仍用原 `export_development.py`：当前--base-apk/--provenance/--version先确定可追溯目标hash，ci_apk.restore验基底后调用同一局部导出函数再验目标，build-ci可直接用旧基底-ContentApk，不要求先有新内容APK；本轮没有把内容服务端版本化下载功能写成完成。构建源公网不可用时不能编造空包继续构建。

## Secrets

GitHub 仓库 Settings → Secrets and variables → Actions → Repository secrets：

| 名称 | 内容 |
|---|---|
| FENGSHEN_KEYSTORE_BASE64 | 当前应用原签名 keystore 的 Base64；不是新密钥 |
| FENGSHEN_KEYSTORE_PASSWORD | 原 keystore 密码 |
| FENGSHEN_KEY_ALIAS | 原签名 alias |
| FENGSHEN_KEY_PASSWORD | 原 key 密码 |
| FENGSHEN_DEPLOY_REVIEW_TOKEN | antpan5608-san 的 Fine-grained Token，仅此仓库，Actions read、Deployments write；用于已授权发布的自动环境审批 |

Settings → Environments → fengshen-production → Environment secrets：

| 名称 | 内容 |
|---|---|
| ALIYUN_ACCESS_KEY_ID | 已有 OSS 发布身份 |
| ALIYUN_ACCESS_KEY_SECRET | 对应受保护凭据 |
| REMOTE_HOST | 现有 Fengshen 巡检主机 |
| REMOTE_PORT | 现有 SSH 端口 |
| REMOTE_USER | 巡检/两版保留操作账号 |
| REMOTE_PASS | 对应受保护 SSH 密码 |

不得在聊天、Git、日志中发送这些值。`configure-ci-secrets.ps1 -Signing -Server` 可在已登录 gh 的当前电脑运行：从既有 Gradle debug 签名配置读取，先签出并校验本地 v22 release 与已安装证书一致，再通过 stdin 加密设置四项 Secret；服务器凭据读取现有 Windows 保护文件/只读 Language 凭据 helper，只写本 Fengshen 环境 Secrets，不修改 Language。临时导出文件清理，密钥与密码不作为 artifact 上传。此 helper 是本机首次 bootstrap，不是云构建依赖。自定义签名时需使用相同证书；既有 signer SHA `5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6`。

fengshen-production 只允许 main，required reviewer 为仓库拥有者。构建 job 无服务器 Secrets。生产 Secret 权限应限制到上述 Fengshen 两个对象；不要授予 workflow 去写 Language 路径。保护规则与 Secrets 状态需以 GitHub 实际返回为准。

## 以后如何操作

1. 提交迭代代码至 main，递增 APK versionCode（不改 applicationId/key）。Actions → **Android cloud build** → Run workflow，填写版本号/名称。现有默认22 / 0.8.2-ci-release仅为首次历史值；当前22已发布，后续必须显式填写大于22的新版本。
2. 构建运行 `build-ci.ps1`：已有内容恢复、安全测试、`testReleaseUnitTest`、`assembleRelease`、证书/package/非debuggable/version/content 校验。失败不会进入上传。运行页面下载 `fengshen-signed-apk`（30天），其中为 `fengshen-remake-v{code}-release.apk`、receipt、SHA256SUMS；没有 ROM、密钥或完整运行日志。测试 XML 单独保留14天。
3. 审核实际 APK、版本、SHA 与目标。用户已确认 v22 正式发布并授权后续已授权迭代自动审批；符合当前范围与全部校验后无需重复请求同一授权。Actions → **Android approved server upload** → Run workflow，填审核过的 build_run_id 与 expected_sha256。不要重新构建审核过的 APK。
4. 同一 workflow 的 approve job 通过原 required reviewer 政策批准 fengshen-production；FENGSHEN_DEPLOY_REVIEW_TOKEN 已在 v22 运行实测成功。令牌/身份/权限失败时如实报告，不删除保护或绕过。只接受同 main commit 的成功手动构建，下载其不可变 artifact，再次验证签名/版本/内容/hash；本机脚本重复点击不会形成第二套产物。
5. 复用 `check-runtime.ps1` → `publish-apk.ps1` → `register-release.ps1` → postflight。查询不可用则阻止上传；包名/signature/version、旧包兼容与目标都检查后才写 APK；完整公网 SHA/size 通过后写 version.json，再更新原服务最近两版规则。重试保留可信上一版记录，不新建日志平台。

等价 CLI（在已授权 gh 环境）：

```powershell
gh workflow run android-build.yml --repo antpan5608-san/game-fengshen --ref main -f version_code=22 -f version_name=0.8.2-ci-release
# 查看 run 和下载产物（RUN_ID 替换为实际成功运行）
gh run view RUN_ID --repo antpan5608-san/game-fengshen
gh run download RUN_ID --repo antpan5608-san/game-fengshen --name fengshen-signed-apk
# 已授权且审核通过后运行；现有 approve job 自动完成环境审批
gh workflow run android-publish.yml --repo antpan5608-san/game-fengshen --ref main -f build_run_id=RUN_ID -f expected_sha256=REVIEWED_SHA256
```

本机等价构建（已从受保护方式配置四个签名环境变量，PATH、JDK17、SDK35完整）：

```powershell
./build-ci.ps1 -VersionCode 22 -VersionName 0.8.2-ci-release
```

## 固定目标与限制

只覆盖 `oss://kubernetes-fleetpilot/artifacts/fengshen-remake/app/fengshen-remake.apk.bin` 与同目录 `version.json`；APK 命名为 .bin 的既有分发契约保持，原 App 自升级仍走该 URL。包名 org.fengshen.dev，不要求卸载/清档。v22首次上传确认已取得，后续按AGENTS最新自动审批授权执行。两个对象不是 OSS 原子事务，上传失败/校验失败仍报告失败，metadata 不先发布；不可把部分上传写成成功。服务器上传、安全存档覆盖升级及手机结果以实际验收为准。

云 CI 不自动运行依赖私有 ROM 的全量取证测试，也不宣称手机操作验证完成。当前TOWN-02已新增药草地图/菜单使用，原workflow加入同候选runtime门禁；TOWN、BATTLE、音频、内容服务端化和一加真机累计未完成项继续见 delivery-status，完成前不得移除。

## 云端接续与后续自动审批授权（2026-10-01 历史修订；阻塞已由下文成功运行解除）

用户已授权 v22 正式上传及后续已授权迭代的自动审批；上文首次人工确认已获得，不重复请求。受保护环境和内容/签名/同提交验证保持。现有集成能够触发 Actions，但 review pending deployments 返回 HTTP 403；取得对应 Deployments 写入能力前，不能保证无需 GitHub 页面操作，不以删除 reviewer 绕过。需由用户在安全的凭据/连接设置中补充权限，不在聊天或仓库传递令牌。

运行 `36868615998` 在实际 preflight 成功后因 workflow 报告读取遗漏 -AsHashtable 失败，上传尚未开始；修复两处报告读取，保留判定逻辑。由于生产工作流要求构建与 main 同提交，CI 修复进入 main 后必须通过原构建工作流生成该提交的 v22，再审核实际 hash；不放宽 guard 以复用旧提交的产物，不创建第二套流程。

### 自动审批令牌的实际配置入口

自动审批由同一个 `android-publish.yml` 的 `approve` job 完成。创建入口：https://github.com/antpan5608-san/game-fengshen/settings/secrets/actions/new 。Name 为 `FENGSHEN_DEPLOY_REVIEW_TOKEN`，Secret 为仓库拥有者的 Fine-grained Token，仅选 game-fengshen，Actions read 与 Deployments write。不要将令牌发到聊天、写入代码或普通 Variables。

`approve` job 将该 Secret 注入其进程 GH_TOKEN，先核对成功的手动 main 构建与同提交，再审批当前运行中唯一的 fengshen-production 环境。它与 publish job 并行启动，不声明 needs，避免等待环境创建的死锁。reviewer 与 main 限制保持；缺失令牌、不可信构建、不符合 reviewer 身份或 API 拒绝均不批准。publish job 继续复核实际 APK/hash、签名、版本、内容及巡检后上传。该 Secret 不传给 APK 构建或发布脚本，不代表当前云端任务拥有凭据；运行36882936289已实际自动审批成功，见下文；若后续令牌过期或权限改变，继续按实际API结果报告。

## v22 云端正式交付（2026-10-01 历史交付）

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

## 单项内容迭代的只读接续入口

原android-publish.yml默认mode=publish，产物输入仍由发布job强制核验；mode=inspect仅调用check-runtime.ps1 -SummaryOnly，使用原fengshen-production保护与自动review，publish不运行。摘要artifact保留14天，不含samples/旧原始日志，不登记版本/清理数据。TOWN-02已实际成功运行此入口。

原android-build.yml默认mode=build。mode=verify-base只对成功main手动构建的不可变fengshen-signed-apk核对receipt/来源提交/run/hash，再用ci_apk.py verify/restore；只保留verification receipt，不生成APK、不使用服务器/签名Secrets。同时只读观察现有Linux runner adb/emulator/AVD/KVM；不是启动设备或App验收。

新增只读模式后，生产review与publish均额外要求来源运行实际包含唯一成功build job；verify-base成功不能当作APK构建。后续新内容导出与进入签名构建按下文TOWN-02已验证入口执行，不允许用关闭pin验证或伪APK绕过。稳定已验证步骤见.agents/skills/fengshen-content-iteration/SKILL.md，实际run/版本由当前任务和交付文档记录。

## TOWN-02 局部内容与运行门禁

content-source.json分开固定iteration.base的完整APK/内容hash与目标manifest。原ci_apk.restore验证基底后调用export_development.export_from_base生成药草局部定义，校验目标pin才写assets；无需ROM/历史私有captures。--base-only仅用于原基底验证入口，不能审核目标候选。

原build-ci的RuntimeTests编译同签名release仪器APK；原build workflow的runtime在单个临时AVD验证旧包覆盖、药草边界和正常闭环。录屏继续用record_app_audio.py的--silent适配，静音不证明声音。发布审批须build/runtime实际成功，check-reviewed-apk核对runtime回执与同提交/同产物hash；跳过或失败只留候选。首次真实runner及后续成功结果以交付记录为准；该runtime链路已由下文TOWN-02实测成功。


## TOWN-02 当前交付与已验证迭代入口

正式v23/0.8.3-town-02/c12，构建36902536271及发布36903560942均success，来源77cdc6b7f822628e0fe8b2a4292e6d396d9089f3。APK SHA256 1a5a5e10f2793c1418a83a2da3b218ebdc2ff2274b2f02d3d1a30c2153d63bbf（11322079字节）；18:04:11 UTC独立下载验证package/签名/内容及全部字节。后续文档HEAD不同不是另一APK来源；下一游戏迭代必须读取实际生产版本并显式递增，不能照抄历史默认22/23。

当前原导出器使用已校验c11基底和提交的药草provenance局部生成目标，不要求先有新内容APK。iteration.base与目标pin分开锁定，默认ci_apk verify仍检目标，verify --base-only仅核原基底。干净CI和临时目录重复恢复已成功，不变素材复用。

原android-build的runtime_tests=true使用同run正式APK/test APK及原不可变v22基底，在一台隔离AOSP AVD执行覆盖安装、Content、药草边界、三店/装备/输入及正常闭环；原录屏器--silent验证停止进程/实际GameView恢复并继续探索，保留录像和同产物runtime-receipt。原android-publish在生产凭据步骤前核对成功build/runtime、同main/源提交/run/APK hash/content与运行回执。本次真实自动审批和发布均成功，reviewer未删除；声音/一加13T仍NOT_RUN。完整验证限制及最新10类欠账见delivery-status最后TOWN-02报告。

## TOUCH-UX-01 已交付的原流程接续

v24/0.8.4-touch-ux-01已发布，来源ef29edb9192bb299ed493b767c52b23c450b2094；原构建36917255772、原发布36919194991成功。APK SHA256 def359de888614152768bdb70c4f12a5e09a653124db6eb0650e96a9edb76300，11353567字节。c12内容和42文件hash不变；后续记录提交不是新APK来源。

原android-build.yml的runtime继续使用同run正式签名APK/test APK，覆盖基底更新为不可变v23构建36902536271。同一隔离API30 AVD包含旧UI对照、真实新游戏触屏交易/装备/药草、冷启动、边界/输入及三档字体。原run-town02-runtime.sh配置匹配native屏幕/skin，截图断言2640×1216和实际有效窗口；不能只凭wm请求或测试PASS省略截图审阅。已出现过Actions成功但截图不合格的候选，该候选未发布。

运行回执新增touchUx、phoneSizedLayout和baselineComparison；check-reviewed-apk要求已授权触控任务的运行门禁，同源提交/run/同审核APK hash保持。原受保护环境/自动审批、preflight/两对象上传/完整公网字节校验/postflight均通过。2026-10-01 20:07:55 UTC发布后可信24/23、424事件全来自23，24无上传样本；一加13T、声音与长时性能仍NOT_RUN。旧artifact名fengshen-town02-runtime-evidence继续复用，不另建workflow或平台。

稳定触控方法见.agents/skills/fengshen-touch-ux/SKILL.md；当前任务/交付记录保存版本和run。以后必须读取实际最新发布并递增，历史默认号不可照抄。


## 已验证的连续区段门禁

仍只有原构建/发布两份workflow。原runtime在隔离AOSP KVM运行正常连续路线/Boss/胜后离宫再入与实际force-stop冷启；check-reviewed-apk.ps1要求同来源和同产物的nanhaiNormalRoute/nanhaiBossVictory/nanhaiOnceAndColdRestart通过。原record_app_audio.py保留未剪辑分段及只读world边界；完整原片在原runtime artifact，小型checkpoints/entry/final副本便于核查。静音、手机wm尺寸和历史真机日志不能写成声音/实机验收。当前版本/hash/run_id以current-task和交付记录为准。
