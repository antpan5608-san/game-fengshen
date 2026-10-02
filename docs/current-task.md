# 当前任务

task_id: WORLD-FULL-01
status: IN_PROGRESS
execution_kind: IMPLEMENTED
scope: 全部有效目标地图与原版交易/住宿服务可运行，保留真实剧情、地形和能力条件

- 开始：2026-10-02 17:46:52 北京时间（09:46:52 UTC）；无固定小时产品终点，不承诺平台无限运行。
- 起始main 93d048fad7d8026c0f730754b57f99ee6c12ea02，工作区干净；正式v26/0.8.6-mobile-play-01/c13，APK来源f9d9ba888e078cc32334bf64ad57ae1822b9402b。
- 当前F0：用户明确报告v26陈塘村客栈开发边界、防具店全黑、海底西北房屋不能进入；逐项修复，不预设房屋可无条件进入。
- F1枚举ROM索引/调度/引用确认有效集合及UNKNOWN；参考259不是已核原版分母。
- F2分类原版规则/安全校验/样本限制/缺运行能力；F3按类型批量接入；F4静态/逐场景服务/剧情/正常路线/升级验证；F5稳定交付。
- 内部检查点自动继续；有验证通过的阶段可发布，但总目标未通过仍PARTIAL。不能以完成F0或新区域主动结案。
- 保留8地图、南海剧情、v26经验/敌人信息/直接点怪/反馈、三店/地图和战斗药草、装备/INPUT-01/存档/音频/更新器。
- 不reset/clean/清档/改Language/真实云进度；原两workflow、签名、同审核APK和reviewer/巡检门槛保持。
- 显式读取两个现有skill；ROM缓存指纹已核匹配，私有原始资源继续受控，不进入公开Git/artifact。
- 十类欠账权威docs/delivery-status.md最新累计清单。历史前任务状态已归档docs/history/mobile-play01-completed-task.md；本次完整授权docs/history/world-full01-authorization.md。
- 总判定均待核：MAP_DATA_COMPLETE / MAP_RUNTIME_COMPLETE / SERVICE_COMPLETE / NORMAL_ROUTE_COMPLETE / ALL_MAPS_USABLE。

## 检查点

- 开工inspect 36991887234成功：2026-10-02 17:48:55北京时间，可信v26/v25，445/4117事件，正常真机2/模拟器0、测试0、错误0、清理失败0。没有发现上传错误不等于所有过程正常，真机历史样本不算本轮验收。
- 用户故障已定位：v26 tiles18.png的65536像素全部为不透明黑；原版正常进入防具店并等淡入完成后取得非黑调色板，待通过原scoped_map_atlas恢复。没有把场景可解析当作可见。
- 客栈map22：入口0(6,25)、落点(12,12)、店员(12,5)/交互(12,7)、出口(12,14)回0(6,25)。原版正常续玩/离店无RAM修改；住宿边界10个受控案例与正常证据分开。固定4两、状态排除mask0x72、有效队员HP/MP恢复上限并清状态；取消/不足不扣，全满仍扣。
- 已实现未提交：Content/SaveState/MainActivity增加原住宿定义、统一纯命令、旧档可选statusMask及既有模态直接触屏；InnStayTest五个方法。本地Gradle wrapper离线单元及instrument APK构建成功；Android实际住宿/升级验证NOT_RUN，不发布。
- F1发现不能按16村×6室内直接新增96地图：原版17–22共享室内ID，$9d保存村庄、$9e选择上下文；商店有另外特殊库存分派。有效图集合/状态变体继续核定，175个解析成功暂不等于最终分母，175及尾索引待分类。
- 下一条动作：用原图形配方/ROMspan形成F0局部导出，修防具图集并纳入客栈；接入原CI正常流程门禁，再继续全量上下文/出口/能力，不以F0作为终点。
- F0局部导出已形成：可信v26/c13直接基底→c14，62文件，manifest 9dc427b5764f67bf41a8ca4275c370202c4788ba4bc8932afbf82f08f2b99495；两次干净目录字节相同。除防具图集外旧媒体不变，战斗/药草/店价/装备规则不变。Python世界5、历史南海7、CI安全12、药草导出5方法通过；JVM87通过，instrument APK可编译。原CI增加同一workflow的历史golden输入和住宿运行门禁，没有新workflow。
- 当前候选需要原KVM正常三店→客栈→取消/确认/离店→用药/冷启和覆盖升级；未运行保持NOT_RUN。提交F0后冻结候选来源，等待CI期间继续F1取证与机器清单，不推新来源影响审核。
