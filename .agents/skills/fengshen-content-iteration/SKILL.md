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
   ci_apk.restore由基底验证→局部导出→目标hash验证→写assets；CI不用ROM或完整旧captures。
   对旧基底单独验包使用ci_apk.py verify --base-only（精确基底APK hash门禁）；默认verify仍审核目标APK。
   两次干净临时目录restore的42文件一致；test_town02_export.py覆盖错规则/ROM/pin和不变素材字节。

9. 原build-ci.ps1恢复目标内容、相关Python门禁、Gradle wrapper的release单元/构建/同签名验包已实际成功；
   APK与instrument APK由原工作流分别保留。业务用HerbUse，触摸用原面板；apply之外的available不结算。
   本地JRE缺编译器时，用Debian官方匹配JDK-headless包在scratch补齐，不改全局权限；JVM网络沿用平台代理系统属性。
   debug单元测试与构建成功，ContentTest及受控药草UI仪器实际成功；不将其写成正常流程/正式覆盖通过。

# 输出和停止条件

输出恢复/验证receipt、脱敏巡检摘要、测试结果及固定交付报告；临时输入/产物按.gitignore隔离。
缺直接证据先找已有公开研究与参考；有来源且无冲突可PROVISIONAL接入开发版，关键状态定向测试，不猜药效/条件。缺口只阻塞依赖项，不停止无关可执行工作。
无设备/无App实际验收只留候选；纯文档/skill或工具变化不发布游戏。
权限/查询失败报告实际动作和最小权限；保留main、reviewer、同提交/同APK hash和目标隔离。

# 仍待验证

局部导出和目标内容恢复已实际验证；签名构建已成功；App覆盖以当前任务记录为准。
不能仅改目标pin后要求旧服务器包满足它，也不把任意ZIP改名APK。
原版药草边界取证、模拟器安装、内容/受控UI仪器已成功；正常闭环、正式候选覆盖及真机仍按当前任务推进。
原workflow新增runtime隔离步骤、现有录屏器的静音/冷启动适配及同APK运行回执门禁仍需真实runner验证；
本地软件AVD出现System UI ANR，保留失败，降低任务AVD分辨率后再验，不将未完成录像计为通过。
现有runner能力只以实际观测为准，不能把工具存在写成设备已经运行。

详细入口：docs/android-ci.md、tools/ci_apk.py、check-runtime.ps1、原两份Actions工作流。
