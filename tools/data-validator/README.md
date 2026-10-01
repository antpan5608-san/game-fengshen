# Data Validator

项目根目录：`./phase1.ps1 validate`。退出0表示没有ERROR，仍可能为REVIEW_REQUIRED；退出1表示ERROR/输入失败。不能把退出0解释为已建立原版基线。

输出[JSON报告](../../reports/data-validation.json)及[阅读版](../../docs/data-validation-report.md)。实现[validator.py](../forensics/validator.py)，入口也可用`.venv/Scripts/python.exe tools/data-validator/main.py`。

检查严格实体形状、重复ID、来源链、map/NPC/dialogue/enemy/item/skill/event/shop/chest/asset引用、递归事件参数、坐标、传送目标、缺失对话、成长间断与技能学习等级、资源文件/大小写/哈希。raw的SQLite affinity不等于强类型，存储异常保留并报警；canonical拒绝额外旧字段。

原始场景参数的映射、battle.type引用空间来自Phase 0假设，只做结构检查并附告警。raw超出零基TMX边界报告WARNING；canonical约定左上零基格坐标，越界是ERROR。条件可达性、Flag生命周期、技能角色编号映射、战斗公式都未被此工具证明。

对未来人工制作的研究候选可用`--canonical path.json --sources original-provenance.json`检查；生产输入不允许synthetic-test绕过真实性。`canonical`命令仅发布verified-baseline候选，过滤现代内容并重新检查引用，若形成悬挂引用则整体拒绝写入。

正负例见[test_validation.py](../../tests/test_validation.py)，各必需引用类型均有故障注入。研究Schema不等于Phase 0的完整运行包Schema，详见[数据层说明](../../game-data/README.md)。
