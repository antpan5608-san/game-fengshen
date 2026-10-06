# 本地开发与局域网模型协作

电脑开发目录：`F:\apps\game-fengshen`，分支 `codex/local-ai-development`。旧目录 `F:\apps\fengshen-remake` 保留原样，迁移前备份位于 `F:\apps\.migration-backups\fengshen-20261006-125200`。

云端已停止研发；公开交接入口为 `work/local-migration-handoff` / `ed442e8dcd1f7f33a68111532b73f9f29055c622`，最新开发源码检查点 `353cf7e8d8d5619d4b9409383a0de22c050948c6`。历史分支均已 fetch 到本地，完整记录见 `docs/local-migration-cloud-handoff.md` 与 `docs/handoff/`。GitHub Actions 继续承担原构建验收，本轮没有发布或合并 main。

## 首次启用

在 Codex 中打开**上述仓库根目录**。项目配置 `.codex/config.toml` 指定 `gpt-6.1-sol` / `high`，不要用父目录 `F:\apps` 代替仓库根目录。已有聊天若设置了单独模型，需要确认其模型与推理强度。

代码与开发环境在电脑，Codex 的高推理仍使用云端；`local-code` 两个别名的推理在局域网服务器执行。

Codex 要求该目录先成为可信项目，才会加载项目配置。当前已验证配置文件与独立 MCP 连接；是否已自动加载，以 Codex 实际配置和工具列表为准。项目可信记录与 Hook 的信任是两件事。

首次启用结束检查时，在项目内通过 `/hooks` 或 Codex 的 Hooks 管理界面审阅 `.codex/hooks.json`，信任其中这一条 Stop 命令。它只读取代码指纹和本机回执，不调用模型、不改源码。禁止绕过 Hook 信任；定义变化后需要重新审阅。官方规则见 [Codex Hooks](https://learn.chatgpt.com/docs/hooks)。

## 每轮使用

1. Codex 阅读 `AGENTS.md`、`docs/current-task.md` 与累计欠账，负责设计、复杂推理及最终改动。
2. 可先用 `search_project_code(query, limit)` 找已有函数；返回原文件行号、完整函数或明确标注的局部片段、文件指纹。它只做本地文本检索，不建立向量知识库。
3. 小函数或测试可以调用 `draft_code(task, paths, acceptance, ranges)`。大文件应指定 `ranges=[{"path":"相对路径","start_line":起始行,"end_line":结束行}]`，选择完整的小函数。默认 `local-code:latest`；9B 对照需显式传 `local-code-quality:latest`。
4. 一轮改动完成后，调用 `review_changes(task_id, goal)`，同时执行相关测试。已提交、新增和删除代码均按上次被接受的快照计算，不会被一次 Git 提交隐藏。
5. Codex 实际查看每份建议，说明采用或拒绝原因，调用 `record_review_decision(snapshot, checks, decisions)`。此后再改代码必须重新审查。该回执不授予发版权限。

草稿没有自动执行或应用入口。模型回答不等于测试结果；错误语言、编造函数、错误预期、缺少上下文均应拒绝。仓库摘要只是持久上下文，模型不会永久学习项目。

## 服务不可用时

模型离线、503、超时或生成截断时，审查会返回 `partial` 及未覆盖文件。Codex 检查这些代码并完成真实验证后，用 `mode="codex_fallback"` 和具体 `reason` 记录降级；不能写成本地模型审查通过。401 不重试，503 最多三次；跨工具进程只允许一个推理请求，草稿及每个审查片段共享 30 秒的排队、重试与生成预算，完整审查预算 600 秒。9B 冷启动较慢，也受这一默认预算约束；超时后由 Codex 接管。

Stop 只检查回执，一次提醒后仍未完成会明确停止为未完成。计划模式及无代码改动不请求模型。初始导入基线不是全仓库通过审查的声明。

## 文件、凭据与恢复

- API：`http://192.168.1.20:8000/v1`，仅局域网；密钥从当前用户目录下受保护的 `Documents\ChatGPT\local-llm\.secrets\access.json` 读取，不写入 Git。确需改位置时显式配置 `LOCAL_AI_CREDENTIALS_FILE`，并在项目 MCP 的 `env_vars` 中传入。
- `.local-ai/` 保存独立工具环境、SQLite 基线、建议/决定、缓存和实测记录；已忽略且限制为当前用户访问。备份此目录才能保留审查历史。停止该项目工具后再备份数据库及其 WAL 文件。
- ROM、原始资源、玩家数据、凭据、构建产物、忽略目录、工作区外路径均不作为工具输入。常见源码凭据字面量会脱敏；Codex 仍应检查选取的代码内容。
- 缓存同时绑定提示、模型身份、四份权威文档指纹及分词器版本。文档或模型别名更新后不复用旧建议。
- 工具环境依赖固定于 `tools/local-ai/requirements-lock.txt`。分词器只取固定版本的数据文件并校验指纹，不加载远端 Python 或模型权重。
- 项目 Git 设置 `core.autocrlf=false`、`core.eol=lf`。保留原证据脚本字节，不关闭资源指纹校验。

必要时可直接运行以下工具；不要重复初始化现有基线：

```powershell
.local-ai/venv/Scripts/python.exe tools/local-ai/manage.py status
.local-ai/venv/Scripts/python.exe tools/local-ai/manage.py review --task TASK-ID --goal "本轮目标"
.local-ai/venv/Scripts/python.exe -m unittest discover -s tools/local-ai -p "test*.py"
```

新机器安装：先创建 `.local-ai/venv`，安装锁定依赖，运行 `tools/local-ai/setup.py` 获取校验后的分词器，再在干净的导入检查点执行一次 `manage.py init`。API 凭据另行安全配置，不复制到仓库。移到其他路径时需修改项目配置和 Hook 命令，并重新审阅。

## 接管范围

匹配 ROM 与可信 v27 APK 已取得并验完整 SHA；c60 的 378 个开发资源文件已按原恢复链生成，manifest 为 `8c56f689610cff897c58d5efdac2370f32e0934cd172a3b0b173f0eb6c1b7bdb`。c61 草案源码保留，尚未启用。生产仍是 v82；冻结候选、c60 和 c61 不能混写。

云端 741 份原始取证文件仍保留在原工作区，未整包传输；需要重新审阅原采样时，按私有资源清单取得对应目录或重新取证。用户真实存档与签名私钥未迁移，本轮仅使用隔离测试数据。具体实测、失败及尚未执行项见 `docs/local-ai-validation.md`。
