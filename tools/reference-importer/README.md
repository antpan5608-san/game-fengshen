# Reference Importer

项目根目录：`./phase1.ps1 import-reference`。迁移参考位置可用`--reference F:/apps/FengShenBang-reference`。

实现位于[importer.py](../forensics/importer.py)，也可用`.venv/Scripts/python.exe tools/reference-importer/main.py`。

读取固定Phase 0快照，导入20表/7,105行、259个TMX、1,661项图形/音频/字体资源引用。数据库以SQLite只读模式打开；输入DB/TMX与Phase 0哈希核对。所有字段、字段声明、SQL、原ID、文件、表/行定位均保留；TMX同时保留XML树（属性、text/tail）和解压后的uint32 GID。

输出`game-data/raw/reference-project/`的dataset、逐表、逐图、manifest；来源输出`game-data/provenance/reference-project.json`。字段语义未核实见`reports/migration-warnings.json`，不把字段丢弃或替换为默认值。字节原文仍在原文件，TMX文件哈希可检查；XML树不是字节完全相同的XML再序列化。

equipment仅是object.type=2/3的103行研究索引，items仍保留147行。shops为230库存行+21 store行，230行对应51个(mapId,npcId)组。不能把这些数量直接当作原版装备/商店数。

每次导入都会刷新比较报告，原始标记词命中也保留。只在首次缺少canonical文件时建立空的BLOCKED基准，不自动覆盖已有canonical。逐值一致性测试见[测试](../../tests/test_import_integrity.py)。
