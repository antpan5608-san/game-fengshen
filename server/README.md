# 封神个人云存档

当前 HTTPS 入口：`https://fleetpilots.com/fengshen-api/`。Go 服务只监听服务器本机 `127.0.0.1:8092`；PostgreSQL 独立数据库、角色、systemd 服务与每日备份均使用 `fengshen` 命名。Language 的账号、数据库、服务和 APK 对象不参与。

Android 在设置中登录独立封神账号，保存内容版本、地图/位置/方向、角色成长字段、背包、银两及事件标志。Segment 001 的母亲首次赠金与 NPC 首谈状态已经接入；小刀赠物来自 Reference 暂定字段，均随同一快照上传。登录时读取云端快照；本地与云端不同时要求选择。每次本地安全点保存后异步上传，服务端以修订号拒绝旧副本覆盖；离线继续保留本地进度。只有显示“已同步云端”的进度才保证换机或清除本地数据后可取回。旧 `opening-to-world-b1` 快照可在新 APK 验证地图和坐标后迁移，其他不兼容版本不覆盖本地。

初始账号为 `antpan`，云端初始为空；首次登录会上传手机当前本地进度，测试快照已清除。密码仅保存在被 Git 忽略的 `private-inputs/cloud-account-password.txt`，不得提交或发到聊天。首次在手机输入后，30 天会话令牌经 Android Keystore 加密保存；密码本身不存入 App。清除 App 数据后重新使用账号密码登录。用户应将密码另存于自己的密码管理器；若丢失，可在服务器用 `server/reset-account.sh` 重置，重置会撤销旧会话但保留存档。

本地验证：`cd server; go test ./...`，公网 API 验证：`./server/test-cloud-live.ps1`（读取本地私密密码文件）。v11 的角色 `maxMp` 和 `equipment` 是云存档中的可选字段，旧存档继续可读；服务端验证范围与 Android 本地一致，装备/最大 MP 不再在云端解码后丢失。部署：交叉编译 `GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -o ../artifacts/cloud-server/fengshen-server-linux-amd64 ./cmd/fengshen-server`，把二进制、`schema.sql`、`deploy-cloud.sh` 上传至服务器 `/root/fengshen-stage/`，首次还需上传账户密码文件，然后运行 `bash /root/fengshen-stage/deploy-cloud.sh`。脚本可重复执行，不会重置已有账户/进度；只给默认 HTTPS 站点添加 `/fengshen-api/` 路由。部署前后检查 `https://fleetpilots.com/language-api/health`。

服务器每天 03:17 UTC 用 `pg_dump -Fc` 备份到 `/var/backups/fengshen-remake/` 并保留 14 天。服务端只接收已校验的个人快照，不接收 ROM、地图素材或完整内容包。当前没有公开注册、密码找回界面或跨内容版本自动迁移；这些能力不能用客户端缓存代替。

## AUDIO-LOG-01 私有运行诊断

复用本服务的 HTTPS 与账户会话；未登录客户端经 `POST /fengshen-api/v1/diagnostics/installations` 获取仅写诊断的安装凭据，再向 `POST /fengshen-api/v1/diagnostics/batches` 上传 gzip 批次。管理员独立鉴权读取 `GET /fengshen-api/v1/admin/diagnostics/summary`（可选 `eventID`）与 `/retention`；管理员凭据不进 APK。仓库唯一巡检入口是 `./check-runtime.ps1` / `-Stage postflight`，经受保护 SSH 调用本机管理员接口，不改存档、日志或版本。查询失败和无样本不得解释为健康。

诊断与 PostgreSQL 玩家数据分离，原子持久化于 `/var/lib/fengshen-remake-diagnostics/diagnostics.json`，目录 700、文件 600，仅 `fengshen-cloud` 访问。解压批次≤256 KiB/128 事件，单事件≤4 KiB；每发布版本20 MiB（事件16 MiB、去重索引4 MiB），旧低优先级记录优先淘汰并统计。客户端安装凭据/IP 分别限流，管理员读取不能触发清理。匿名写入/查询实测401，匿名私有文件路径404。

`publish-apk.ps1` 公网 APK/元数据验证后调用 `server/register-release.ps1`，依据可信 current/previous 发布记录登记版本并立即清理过期诊断；不等待新客户端。启动补偿清理；清理失败可查询并阻止成功声明。旧版迟到批次410 `expired_apk_version`，客户端删除该批，不改发生版本。仅清理诊断记录/索引，保留 APK、发布历史、ROM 和玩家存档。部署更新沿用 `server/deploy-update.mjs`，不重复运行首次数据库部署。隔离测试：`go test ./...`；线上只读巡检与实际 App 读回证据见 `docs/delivery-status.md`。
