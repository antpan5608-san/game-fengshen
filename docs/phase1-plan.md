# Phase 1 实施计划（执行前记录）

已完整阅读Phase 0六份文档；不重做审计。输入为固定提交d636453f14f86a096f9d293bf3facfd96cfcb614及现有evidence。

顺序：来源与Schema → 无损Reference Importer → Validator/差异报告 → PC Map Viewer → ROM基础工具 → 测试、报告、停止。

既有事实：259地图、639剧情、174敌人、249成长；没有完整游戏运行实现；参考库混入改编；190处TMX图片声明尺寸差异；碰撞/Flag/角色编号等语义UNKNOWN。

与Phase 0的兼容：

- 沿用只读raw、候选、canonical分层；未验证候选不进入canonical。
- 采用本次明确指定的`tools/reference-importer`名称。
- provenance扩展为用户要求的八类source和五级confidence；保留originalVerified、sourceRefs及字段位置。
- 本阶段Schema落实来源、raw、比较输入、canonical的研究/验收边界；不创建Kotlin/Android/Go实现。
- 碰撞显示原始标记，不默认through的值代表可走；坐标显示原值并提供Y轴对照。
- 明显现代内容加NON_ORIGINAL_REFERENCE_CONTENT标签；标签依据与ROM对照状态分开，不将未提供ROM误报为REFERENCE_ONLY。
- reference/rom当前无ROM：WAITING_FOR_ROM。头/分段/搜索/CHR工具用合成fixture验证，游戏offset表保持空。
- Phase 2门槛保留：需要原版版本、剧情/碰撞语义及有证据的切片；工具完成不等于原版基准完成。

验收：四个主操作各一条命令；全量导入不丢字段；正负例验证；地图查看器实际浏览器检查；ROM合成测试；原版已验证数量与UNKNOWN逐ID报告。完成后停止，不进入Phase 2。
