# Data Validation Report

结果：**REVIEW_REQUIRED**。原版真实性不会因结构检查通过而升级。

执行命令：`./phase1.ps1 validate`。逐条问题见 [机器报告](../reports/data-validation.json)。

|类别|数量|
|---|---:|
|CANONICAL_BLOCKED|1|
|INFERRED_REFERENCE_NAMESPACES|1|
|ORIGINAL_BASELINE_UNVERIFIED|1|
|RAW_COORDINATE_OUTSIDE_ZERO_BASED_BOUNDS|12|
|SQLITE_STORAGE_CLASS_MISMATCH|1|
|TILESET_DIMENSION_MISMATCH|190|
|UNKNOWN_EVENT_OPCODES|1|
|UNKNOWN_SKILL_ROLE_MAPPING|1|

ERROR为确定结构/引用错误；WARNING为来源内差异或坐标约定待核；UNKNOWN为未解决语义/真实性。没有修改源数据或用默认值消除问题。

raw坐标越界按明确的零基TMX假设报告warning；canonical坐标契约确定，因此同类越界是error。尚不能证明有条件剧情可达或全流程通关。
