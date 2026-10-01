task_id: TOWN-02
scope_revision: herb-use-and-supply

# TOWN-02：开局药草使用与补给闭环

用户授权实施任务：已有药店买药草→正常离店→原版允许的地图/菜单场景选合法目标使用→HP/数量/HUD/存档一致→停止进程/重启→继续探索。这是可选补给，禁止新增出村剧情前置。

仅药草 `rom.medicine.0`；不展开牛黃丸、全物品、客栈、NPC、新地图、南海Boss或战斗物品。保留v21/c11游戏成果、已发布v22及既有UI/输入/战斗/音频/云存档。

开始提交：1f9a72f12cbb4dc651a49f8fd952ffeb8f10b2bf。业务修改前本地缺pwsh，巡检初始NOT_AVAILABLE；按用户授权只给原发布workflow增加inspect模式，继续使用原check-runtime.ps1、受保护环境/reviewer与脱敏摘要，无上传/登记/清理调用。实际本轮巡检结果后续填写，不引用旧结果代替。

原版门槛：现有provenance/c11仅确认药草ID/买卖，medicine effects/use conditions未核。必须核定效果计算、目标、地图可用性、满HP/取消/失败与消耗时机；无证据不猜药效。缺私有输入只恢复必要文件，未变化地图/音频/图集复用可信c11。

交付门槛：正常购买/离店/受伤角色使用/保存冷启动与旧档升级实际App验收；边界fixture单独标记。一加13T未执行为NOT_RUN，无Android运行验收只保留候选，不覆盖生产。无新功能或有效修复不发布游戏。

历史授权/上一任务见docs/history/authorization-through-android-ci-01.md；十类累计欠账仍以docs/delivery-status.md现有清单为准。本轮固定报告execution_kind、各项实际覆盖、skill可靠方法及一个最小资源请求。完成后停止。
