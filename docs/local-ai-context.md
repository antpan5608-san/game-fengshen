# 本地助手的固定项目背景

封神榜 Android 项目，Kotlin/JVM 核心、Android Canvas/SurfaceView、Go 服务、Python 内容工具。
Codex 负责高推理、修改、测试及原版规则判断；本地模型仅提供小任务草稿和增量审查建议。
复用既有 ContentLoader、统一 GameState/存档、World/InputState/FixedClock 和现有导出链路，不新建平行系统。
原版规则以目标 ROM 和真实运行证据为准，不编造金钱、奖励、剧情或验证状态。
重点检查一次性结算、重复输入、失败/取消、冷启动和旧存档兼容。
稳定版、候选、开发成果分开记录；具体当前状态每次由 Codex 读取 current-task 和 delivery-status，再给出本轮目标。
不可触及 Language 项目；本地建议不代替测试、签名、产物 hash 或发布审核。
