# Phase 0只读审计工具

这些脚本是审计证据生成器，**不是**计划中的ROM extractor、正式data-validator或Android/Go实现。所有参考读取均只读，输出只写指定evidence目录。

输入：固定提交`d636453f14f86a096f9d293bf3facfd96cfcb614`的完整参考快照及GitHub递归tree JSON。来源树若truncated会失败；文件缺失/哈希不匹配会非零退出。语义问题保存在报告中，退出0不意味着原版准确或可通关。

依赖：Python 3.12、Pillow 12.3.0；额外资源检查使用mutagen 1.48.1、fonttools 4.66.0。普通脚本无需Cocos/Android SDK。可在隔离venv安装这些版本，不要将依赖目录提交Git。

```powershell
python tools/reference-audit/audit.py --reference F:/apps/FengShenBang-reference --tree docs/evidence/FengShenBang-reference-tree.json --out docs/evidence
python tools/reference-audit/inspect_db.py F:/apps/FengShenBang-reference/Resources/res/MainData
python tools/reference-audit/inspect_ccbi.py F:/apps/FengShenBang-reference docs/evidence/ccbi-details.json
python tools/reference-audit/inspect_assets.py F:/apps/FengShenBang-reference docs/evidence/asset-probes.json
python tools/reference-audit/check_asset_refs.py F:/apps/FengShenBang-reference docs/evidence
python tools/reference-audit/verify_phase0.py
```

本环境Pillow来自Codex捆绑Python；mutagen/fonttools安装在仓库外`F:/apps/fengshen-audit-deps`，运行资源检查时通过PYTHONPATH引用。脚本没有联网下载ROM或发布资源功能。

主要证据：

- file-inventory：全部路径、大小、Git blob SHA-1、SHA-256及匹配结果。
- database-tables/reference-checks：表字段、计数、空值、重复ID、显式/假设引用检查。没有外键错误不等于事件解释正确。
- map-inventory/map-errors/map-catalog：全部图层解码、tileset属性与图名。GID范围检查使用TMX声明图片尺寸，不替代真实原版图块比对。
- map-graph：从map1按warp+jump边构造的简化图；它忽略条件，同时缺少剧情teleport，**不能证明死图或通关**。
- ccbi-details：按[cocos2d-x CCBReader](https://raw.githubusercontent.com/cocos2d/cocos2d-x/v3/cocos/editor-support/cocosbuilder/CCBReader.cpp)格式解析字符串缓存、sequence及音效/回调元数据，未解释节点树。
- asset-probes：全部MP3流元信息、TTF字符覆盖及map PNG精确5倍复制测试。未完整音频解码/听辨。
- map-warnings：190处TMX图片声明尺寸与实际PNG不同，须在转换前复核。
- asset-reference-checks：4,092条数据库资源路径/命名约定检查，以及CCBI含路径资源大小写精确检查。命名约定匹配不证明运行时加载已实现。

数据报告只描述已观察事实。完整图片和音频留在参考快照外部，未随新仓库复制。database-inspection包含各表少量样本用于理解结构，不是生产game-data。
