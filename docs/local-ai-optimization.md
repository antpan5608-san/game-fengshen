# 本地模型辅助开发优化

默认采用：当前 4B / 4K / 关闭思考 / temperature 0.2。候选独立验收没有达到至少多正确完成5项的门槛，因此没有更换默认模型。

工作流与环境入口见 [使用说明](local-ai-usage.md)。新增 `search_project` 是 `search_project_code` 的兼容别名；检索只读，不占GPU。

调用草稿前提供明确语言、接口和验收条件，优先传完整小函数的 `ranges`。检查返回的 `complete_declaration`；局部片段需要补足类型和调用关系。每次调用每个片段30秒内失败则由Codex接管。

实测48项留出任务原配置18/48，候选18/48；当前源码20项试用可验证6/20。结果只覆盖隔离夹具及选定函数，不等于完整剧情、真机或存档发布验收。

完整报告与原始证据：`C:/Users/antpan/Documents/ChatGPT/local-llm/artifacts/optimization/report.md`。参数选择记录存于同目录 `decision.json`；项目当前生效请求参数存于忽略目录 `.local-ai/active-profile.json`。

升级模型或别名后应刷新 `.local-ai/model-digests.json`，保持分词器与实际模型一致。当前参数未换权重；既有分词器继续使用。原始回答或未经验证的修改不直接作为训练数据。
