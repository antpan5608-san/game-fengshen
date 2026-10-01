---
name: fengshen-content-iteration
description: 在Fengshen单项内容迭代中复用可信APK基底、原巡检与CI验证；不用于全量研究、纯规划或其他项目。
---

# 适用与输入

先读AGENTS.md及docs/current-task.md；当前任务/版本/欠账不保存在skill。
需要本轮范围、已审核基底构建run_id与APK SHA、可信receipt及内容pin。
资源获取按AGENTS.md最新授权；不含凭据，不扩展任务或发布范围。

# 已验证方法

1. 定位输入：查具体provenance的verifiedFields/remainingUnknown、已发布定义和实际提取函数。
   买卖已核不能推断使用效果；声明待核时先查公开研究与参考，使用时保持实际置信度；本轮药草已补局部原版证据。
2. 业务修改前调用原发布workflow的只读入口：
   `gh workflow run android-publish.yml --repo antpan5608-san/game-fengshen --ref main -f mode=inspect`
   原check-runtime.ps1 -SummaryOnly查询受保护环境，approve沿用reviewer；publish跳过。
   返回只保留task_id、时间、权威两版、样本/错误计数和覆盖限制，不保存samples或整份旧日志。
3. 当前任务不能下载官方artifact时，先报告实际HTTP/CONNECT错误，不输出签名URL、不绕过代理。
   已验证原build workflow的`mode=verify-base`，传入`base_build_run_id`及`base_sha256`：
   runner下载原fengshen-signed-apk，比较来源提交/run/receipt/完整字节，调用ci_apk.py verify/restore。
   该模式没有签名/服务器Secrets，不生成新APK；其成功运行禁止被当作生产APK构建。
4. 本地公网回退：重新读取version.json，核对实际版本/package/signer及APK完整SHA/size，
   再用原`tools/ci_apk.py verify`验签名/版本/非debuggable/内容pin；`restore --apk`写原assets入口。
   工具要求JDK及ANDROID_SDK_ROOT下的官方build-tools；Linux aapt还需同包lib64/libc++.so。
   来源URL的版本查询参数不保证不可变；恢复发布内容不等于迁移ROM/原始回放。
5. 相关回归：`python -m unittest discover -s tests -p test_ci_apk.py`；
   `python tests/test_environment_review.py`在Linux+bash/jq执行真实workflow脚本的隔离API fixture。
   安全断言覆盖非法来源、不同提交、无build成功job、reviewer/权限拒绝及inspect隔离。
   实际App正常操作、冷启动和覆盖升级必须另验，fixture不算正常游玩。

6. 本地缺目标ROM时，复用game-data/provenance/rom-acquisition.json中固定提交的公开URL，
   以Python urllib普通访问取回一个文件，存private-inputs（忽略），用hashlib核对完整SHA/大小，再以原Reader解析。
   hash匹配只证明字节一致；licenseStatus仍单独记录。已取得的匹配输入缓存复用，不逐轮重复下载。
7. 局部药草原版边界复用tools/rom-extractor/probe-town02-herb.lua：FCEUX Lua正常菜单按键，
   受控RAM fixture明确标记，不能作为正常路线证据。FENGSHEN_ROOT指定仓库；HERB_CASE/HP/FLAG/COUNT控制隔离样本。
   Debian官方FCEUX包加Lua5.1和Xvfb在用户scratch解包运行已成功，许可文件保留；offscreen OpenGL失败，Xvfb成功。
8. 复用原export_development.py的--base-apk/--provenance/--version生成可审核目标manifest；
   ci/content-source.json分别固定iteration.base字节/hash和目标内容pin。
   ci_apk.restore由基底验证→局部导出→目标hash验证→写assets；旧药草局部路径不需ROM或旧captures；新场景按实际必要ROM依赖复用受控缓存，不要求整个历史目录。
   对旧基底单独验包使用ci_apk.py verify --base-only（精确基底APK hash门禁）；默认verify仍审核目标APK。
   两次干净临时目录restore的42文件一致；test_town02_export.py覆盖错规则/ROM/pin和不变素材字节。

9. 原build-ci.ps1恢复目标内容、相关Python门禁、Gradle wrapper的release单元/构建/同签名验包已实际成功；
   APK与instrument APK由原工作流分别保留。业务用HerbUse，触摸用原面板；apply之外的available不结算。
   本地JRE缺编译器时，用Debian官方匹配JDK-headless包在scratch补齐，不改全局权限；JVM网络沿用平台代理系统属性。
   debug单元测试与构建成功，ContentTest及受控药草UI仪器实际成功；不将其写成正常流程/正式覆盖通过。

10. 同候选运行用原workflow的runtime job与ci/run-town02-runtime.sh，临时目录显式ANDROID_AVD_HOME和avdmanager --path；
    默认路径假设曾实际失败，显式路径后runner成功。单个AOSP AVD下载原基底/本次候选和同签名test APK，
    先正常赠刀保存再adb install -r覆盖，验旧档一致；受控边界与Content后由原record_app_audio.py --silent
    执行正常药店购买、受伤使用、外部force-stop、实际GameView冷启动和继续探索。输出运行回执和实际录像；声音NOT_RUN。
    发布workflow复核同main提交/build run/APK hash和成功runtime job及回执，测试job不持生产Secret。

# 输出和停止条件

输出恢复/验证receipt、脱敏巡检摘要、测试结果及固定交付报告；临时输入/产物按.gitignore隔离。
缺直接证据先找已有公开研究与参考；有来源且无冲突可PROVISIONAL接入开发版，关键状态定向测试，不猜药效/条件。缺口只阻塞依赖项，不停止无关可执行工作。
无设备/无App实际验收只留候选；纯文档/skill或工具变化不发布游戏。
权限/查询失败报告实际动作和最小权限；保留main、reviewer、同提交/同APK hash和目标隔离。

# 仍待验证

局部导出和目标内容恢复已实际验证；签名构建已成功；App覆盖以当前任务记录为准。
不能仅改目标pin后要求旧服务器包满足它，也不把任意ZIP改名APK。
原版药草边界、签名候选覆盖、正常闭环及停止进程恢复已由真实runner成功执行；真机和声音仍NOT_RUN。
本地无KVM的软件AVD两次System UI ANR，保留失败，不能作为正常流程PASS；低分辨率不保证解决。
原买卖/装备/INPUT-01/摇杆回归在同候选runner已实际通过；原发布门禁和自动审批已在真实生产runner成功，公网独立下载/验签/内容复核通过。
真机、声音、真实账号多设备恢复仍需实际执行，不将工具存在写成设备已经运行。

详细入口：docs/android-ci.md、tools/ci_apk.py、check-runtime.ps1、原两份Actions工作流。

## 已验证的限定新场景导出

原export_from_base已扩展现有Reader/metatile/ROM-tile recipe路径，可从可信不可变APK基底批次导出当前必要地图/出口/NPC/敌群与本场图形。ROM原始文件仍在忽略的private-inputs/.ci-private，recipe只含偏移/长度/hash/像素组合与来源。先核每条出口实际落点/方向，不交换猜返程；遭遇分区/行为未知只保留受影响区域边界，旧可行区域不能缩成轨迹白名单。

本地已运行test_nanhai_export.py：同输入两次结果一致、不变媒体逐字节一致、错误基底/目标pin/ROMspan/敌数值拒绝、manifest全文件核验。该测试输入为FENGSHEN_CONTENT_BASE_APK指定的已审核APK；源码依赖与生成方法均在原export_development.py及当前provenance。图形PNG编码的透明0和不透明黑底须按实际观察区分。Windows/Linux的Pillow压缩字节曾不同而原bytehash失败；原导出器以已审核RGBA hash核像素，并用固定存储DEFLATE序列输出PNG，目标文件/manifest hash继续严格审核，源PNG hash仅保留溯源。本地Pillow解码/CRC/多block/不调用平台encoder及错像素拒绝测试已通过，Windows runner已严格恢复与Linux同一manifest；随后测试文件的默认cp1252读取失败，中文JSON必须显式read_text(encoding='utf-8')。不能关闭hash门禁或删测试绕过。

限定Boss业务边界已在原JVM门禁运行：攻击等防伤0/小于防伤1、同字节命中与倍伤、敏捷排序、冰与物理同字节选择、Boss逃跑读字节后失败耗行动、掉落数量/分类格数满不丢物品。本地构建/原版正常取证不能替代Android正常主线或runner签名门禁；相应CI/App方法待实际运行后才能记录成功。
