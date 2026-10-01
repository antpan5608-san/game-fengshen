task_id: TOWN-02
scope_revision: herb-use-and-supply

# TOWN-02：开局药草使用与补给闭环

用户授权实施任务：已有药店买药草→正常离店→原版允许的地图/菜单场景选合法目标使用→HP/数量/HUD/存档一致→停止进程/重启→继续探索。这是可选补给，禁止新增出村剧情前置。

仅药草 `rom.medicine.0`；不展开牛黃丸、全物品、客栈、NPC、新地图、南海Boss或战斗物品。保留v21/c11游戏成果、已发布v22及既有UI/输入/战斗/音频/云存档。

开始提交：1f9a72f12cbb4dc651a49f8fd952ffeb8f10b2bf。业务修改前本地缺pwsh，巡检初始NOT_AVAILABLE；按用户授权只给原发布workflow增加inspect模式，继续使用原check-runtime.ps1、受保护环境/reviewer与脱敏摘要，无上传/登记/清理调用。实际本轮巡检结果后续填写，不引用旧结果代替。

原版门槛：现有provenance/c11仅确认药草ID/买卖，medicine effects/use conditions未核。必须核定效果计算、目标、地图可用性、满HP/取消/失败与消耗时机；无证据不猜药效。缺私有输入只恢复必要文件，未变化地图/音频/图集复用可信c11。

交付门槛：正常购买/离店/受伤角色使用/保存冷启动与旧档升级实际App验收；边界fixture单独标记。一加13T未执行为NOT_RUN，无Android运行验收只保留候选，不覆盖生产。无新功能或有效修复不发布游戏。

历史授权/上一任务见docs/history/authorization-through-android-ci-01.md；十类累计欠账仍以docs/delivery-status.md现有清单为准。本轮固定报告execution_kind、各项实际覆盖、skill可靠方法及一个最小资源请求。完成后停止。

## 本轮实际接续结果（持续更新）

- 只读巡检运行36888836939（d0ab511），approve/inspect成功、publish跳过。UTC2026-10-01T16:02:05查询可信22/21：452事件，14测试；普通6模拟器+2真机会话，窗口08:27:54—15:48:08；无上传ERROR，清理失败0。不替代本轮App/手机验收。
- 本地尝试下载不可变构建36882142423的fengshen-signed-apk：API可读，下载代理CONNECT403，未绕过或打印签名URL。转为再次下载正式对象，实际元数据/完整字节与v22可信receipt一致；使用官方SDK35.0.0 aapt/apksigner与原ci_apk.py验包名、签名、非debuggable、版本/hash/大小和42文件后restore成功。
- 用户确认目前没有接入私有资源。药草ID rom.medicine.0、originalId0、购买15/卖出7/数量上限10可复用；效果/使用条件仍未核，不启用虚构回血规则。已定位一个既有固定ROM来源，但旧准入规则禁止公共补ROM，正在请求仅取回该一文件的明确授权；批准前不下载。
- 本轮原build workflow新增verify-base，使用现有不可变artifact、receipt与ci_apk验证恢复，不使用签名/服务器Secrets、不构建新APK、不上传生产；观察原runner Android/KVM能力。实际运行36889913121（e445ca2）成功：runner取得构建36882142423不可变APK，receipt/签名/内容验证及原restore通过，transport12项通过。Linux runner实际adb=true、emulator=false、AVD空、KVM不可读写；不声明模拟器已运行。

## 当前交付边界

药草使用功能仍BLOCKED：未取得效果/场景/目标/满血与消耗规则证据，未修改游戏业务代码或启用药草。用户确认无私有资源；单个固定来源ROM的取回许可尚未收到，不把问卷预选或等待当成授权。原game-data-inventory.md历史准入要求“不得从公共下载补ROM”，因此仅定位来源，没有擅自下载。最小输入是一个iNES/Mapper246、1048592字节、指定SHA的ROM，私有落点private-inputs/town02/target.nes；后续只补药草局部行为，不恢复全部历史目录。

本轮已实施并验证巡检/基底接续工具。未生成新候选APK，未发布游戏；正式对象仍为v22/c11。正常App使用、边界原版对照、冷启动/旧档升级及一加13T均NOT_RUN。累计十类游戏欠账未关闭，权威清单保持delivery-status现有十项。本轮可靠方法已沉淀到唯一内容迭代skill，新内容导出/仪器流程仍明确待核。
