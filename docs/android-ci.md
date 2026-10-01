# Android 云构建与发布

仓库：antpan5608-san/game-fengshen，main。保留游戏内容/技术栈，不迁移为模拟器。已发布基线 v21 / 0.8.1-town-01，首个云候选 v22 / 0.8.2-ci-release；候选不代表原版内容里程碑完成。

## 构建环境与必要输入

Windows 2022 Actions runner，Temurin JDK 17，SDK platform 35/build-tools 35.0.0；Gradle wrapper 8.10.2（发行包及 wrapper JAR 均锁 SHA），AGP 8.7.3，Kotlin 2.0.21。既有依赖 WorkManager 2.9.1、Media3 1.4.1、JUnit 4.13.2不变。发布桥复用 ssh2 1.17.0（npm lock），ossutil 2.4.0 官方二进制校验后安装。官方 Actions 固定到提交，不用浮动标签。

ROM、reference、private-derived、development assets、APK、密钥、本机 SDK/local.properties/.venv 不能入 Git。现有导出器需要本机 ROM/取证资源，因此不在云端重新逆向或假设这些文件已提交。`tools/ci_apk.py restore` 从现有公开发布 APK 恢复其 development 包，先验证 metadata/APK size/SHA/package/signer，再验证 `ci/content-source.json` 锁定的 manifest/version 和全部文件，最后写入原 ContentLoader 目录。包内容必须完全吻合：c11，manifest SHA `3ea11936a0c7e04bddfb3e846a612532c836e9e3461df4da71281cfbd0786147`。不是截图地图，不是第二套 importer。

公网对象更新后，同一内容 hash 仍可复用；如果服务器改成新内容版本，旧 pin 构建会明确失败。下一次内容变更仍用原 `export_development.py` 生成包，核对 provenance，更新 pin，并提供包含新包的可信 APK（本地 build-ci 可用 `-ContentApk`），再沿用这一读取入口；本轮没有把内容服务端版本化下载功能写成完成。构建源公网不可用时不能编造空包继续构建。

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

1. 提交迭代代码至 main，递增 APK versionCode（不改 applicationId/key）。Actions → **Android cloud build** → Run workflow，填写版本号/名称。默认首次 22 / 0.8.2-ci-release；后续必须大于已发布版。
2. 构建运行 `build-ci.ps1`：已有内容恢复、安全测试、`testReleaseUnitTest`、`assembleRelease`、证书/package/非debuggable/version/content 校验。失败不会进入上传。运行页面下载 `fengshen-signed-apk`（30天），其中为 `fengshen-remake-v{code}-release.apk`、receipt、SHA256SUMS；没有 ROM、密钥或完整运行日志。测试 XML 单独保留14天。
3. 审核实际 APK、版本、SHA 与目标。首次必须等用户明确确认。Actions → **Android approved server upload** → Run workflow，填审核过的 build_run_id 与 expected_sha256。不要重新构建审核过的 APK。
4. 在 environment pending deployment 页面由 reviewer 审批。只接受同 main commit 的成功手动构建，下载其不可变 artifact，再次验证签名/版本/内容/hash；本机脚本重复点击不会形成第二套产物。
5. 复用 `check-runtime.ps1` → `publish-apk.ps1` → `register-release.ps1` → postflight。查询不可用则阻止上传；包名/signature/version、旧包兼容与目标都检查后才写 APK；完整公网 SHA/size 通过后写 version.json，再更新原服务最近两版规则。重试保留可信上一版记录，不新建日志平台。

等价 CLI（在已授权 gh 环境）：

```powershell
gh workflow run android-build.yml --repo antpan5608-san/game-fengshen --ref main -f version_code=22 -f version_name=0.8.2-ci-release
# 查看 run 和下载产物（RUN_ID 替换为实际成功运行）
gh run view RUN_ID --repo antpan5608-san/game-fengshen
gh run download RUN_ID --repo antpan5608-san/game-fengshen --name fengshen-signed-apk
# 用户确认后才运行，并在 GitHub environment 页面审批
gh workflow run android-publish.yml --repo antpan5608-san/game-fengshen --ref main -f build_run_id=RUN_ID -f expected_sha256=REVIEWED_SHA256
```

本机等价构建（已从受保护方式配置四个签名环境变量，PATH、JDK17、SDK35完整）：

```powershell
./build-ci.ps1 -VersionCode 22 -VersionName 0.8.2-ci-release
```

## 固定目标与限制

只覆盖 `oss://kubernetes-fleetpilot/artifacts/fengshen-remake/app/fengshen-remake.apk.bin` 与同目录 `version.json`；APK 命名为 .bin 的既有分发契约保持，原 App 自升级仍走该 URL。包名 org.fengshen.dev，不要求卸载/清档。第一版正式上传前停止并等待用户确认。两个对象不是 OSS 原子事务，上传失败/校验失败仍报告失败，metadata 不先发布；不可把部分上传写成成功。服务器上传、安全存档覆盖升级及手机结果以实际验收为准。

云 CI 不自动运行依赖私有 ROM 的全量取证测试，也不宣称手机操作验证完成。本轮新增仅 transport/signing/CI adapters；TOWN、BATTLE、音频、内容服务端化和一加真机累计未完成项继续见 delivery-status，完成前不得移除。

## 云端接续与后续自动审批授权（2026-10-01 后续修订）

用户已授权 v22 正式上传及后续已授权迭代的自动审批；上文首次人工确认已获得，不重复请求。受保护环境和内容/签名/同提交验证保持。现有集成能够触发 Actions，但 review pending deployments 返回 HTTP 403；取得对应 Deployments 写入能力前，不能保证无需 GitHub 页面操作，不以删除 reviewer 绕过。需由用户在安全的凭据/连接设置中补充权限，不在聊天或仓库传递令牌。

运行 `36868615998` 在实际 preflight 成功后因 workflow 报告读取遗漏 -AsHashtable 失败，上传尚未开始；修复两处报告读取，保留判定逻辑。由于生产工作流要求构建与 main 同提交，CI 修复进入 main 后必须通过原构建工作流生成该提交的 v22，再审核实际 hash；不放宽 guard 以复用旧提交的产物，不创建第二套流程。

### 自动审批令牌的实际配置入口

自动审批由同一个 `android-publish.yml` 的 `approve` job 完成。创建入口：https://github.com/antpan5608-san/game-fengshen/settings/secrets/actions/new 。Name 为 `FENGSHEN_DEPLOY_REVIEW_TOKEN`，Secret 为仓库拥有者的 Fine-grained Token，仅选 game-fengshen，Actions read 与 Deployments write。不要将令牌发到聊天、写入代码或普通 Variables。

`approve` job 将该 Secret 注入其进程 GH_TOKEN，先核对成功的手动 main 构建与同提交，再审批当前运行中唯一的 fengshen-production 环境。它与 publish job 并行启动，不声明 needs，避免等待环境创建的死锁。reviewer 与 main 限制保持；缺失令牌、不可信构建、不符合 reviewer 身份或 API 拒绝均不批准。publish job 继续复核实际 APK/hash、签名、版本、内容及巡检后上传。该 Secret 不传给 APK 构建或发布脚本，不代表当前云端任务拥有凭据；配置并实际运行成功前，自动审批保持未验证。
