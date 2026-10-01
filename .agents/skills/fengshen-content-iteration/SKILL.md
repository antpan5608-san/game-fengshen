---
name: fengshen-content-iteration
description: 在Fengshen单项内容迭代中复用可信APK基底、原巡检与CI验证；不用于全量研究、纯规划或其他项目。
---

# 适用与输入

先读AGENTS.md及docs/current-task.md；当前任务/版本/欠账不保存在skill。
需要本轮范围、已审核基底构建run_id与APK SHA、可信receipt及内容pin。
本skill不授权额外发布或ROM取得方式，不含凭据。

# 已验证方法

1. 定位输入：查具体provenance的verifiedFields/remainingUnknown、已发布定义和实际提取函数。
   买卖已核不能推断使用效果；只有声明待核时必须补原版证据。
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

# 输出和停止条件

输出恢复/验证receipt、脱敏巡检摘要、测试结果及固定交付报告；临时输入/产物按.gitignore隔离。
未取得必要ROM行为证据，不猜药效/条件，不启用未经验证功能。
无设备/无App实际验收只留候选；纯文档/skill或工具变化不发布游戏。
权限/查询失败报告实际动作和最小权限；保留main、reviewer、同提交/同APK hash和目标隔离。

# 仍待验证

新定义经原export_development.py局部复用基底并进入签名构建的链路尚未实施；
不能仅改目标pin后要求旧服务器包满足它，也不把任意ZIP改名APK。
原版药草行为取证、使用闭环、模拟器安装/仪器和真机验收仍按当前任务记录推进。
现有runner能力只以实际观测为准，不能把工具存在写成设备已经运行。

详细入口：docs/android-ci.md、tools/ci_apk.py、check-runtime.ps1、原两份Actions工作流。
