# 当前任务：WORLD-FULL-01

task_id: WORLD-FULL-01

继续全部有效原版地图、商店与住宿的连续恢复。真实墙、地形、单向出口、剧情与道具条件保留；不改数值、不赠资源、不清档。原两份Actions、签名、reviewer、同提交/同产物审核与两个Fengshen对象不变，Language不变。重大可玩进展通过实际App运行、存档与原门禁即阶段发布，不等待非阻断欠账清零。

## 当前有效状态（2026-10-04T07:18Z）

- 生产仍v27/0.8.7-world-full01-f0/c14，用户已安装；APK SHA5944141d45edb059294e9de066914c611f1e0cc6f334f32be9584a6ec35cc353。
- WORLD总体PARTIAL；ALL_MAPS_USABLE=NO，有效原版分母UNKNOWN。175几何缓存/259索引不是正常可用地图总数。
- v62/run37180967103/source a2b3ca386125b2f2ef4d6170b469c36ffb510e8e：build PASS，runtime FAILURE。北海宫廷正常续跑在TouchTest进入训练路线时报25(29,43)无合法路径；实际审核APK证明该格class1真实墙。是测试规划错误，尚无该断言证明App崩溃；失败候选NOT_PUBLISHED。
- v62实际源8级/EXP858/57HP/346两/药草1/牛黄丸0；采购已通过真实药店路径。不得混用v61的314两/2药草报告。
- 最小驱动修复：直接只读BFS走25真实入口39,42→西海门5,24及独立返程；两方向均53节点、仅zone1、不穿其他出口/毒区。删除错误中间墙格，不开墙、不改敌人/价格/存档/原5000步或战败断言。7北海导出方法、2采购守卫、原wrapper仪器编译7秒PASS；修后正常App NOT_RUN。
- 内容仍c41/41图/225文件/manifest7c9a4d5d9796df06515bd093b8861569141f85ba183135b507166a7bb2c9b94f。v62独立验包hash3d130195dbc17165210055b9834e0be8919dff0f63c6e0588577113792944c91，不等于运行通过。
- GitHub API/原Actions/审批实际恢复；Git HTTPS写入失败已记录，不改全局认证、不泄露凭据。使用已验证严格树/hash/非force Git API提交；来源冻结期间不混入独立内容。
- 独立work/world-island保存c46/48图/255文件及202世界Python回归PASS，备份work/world-island-c46/a8fe6932afb3adbb0b23a662ea6cdeb8da1d45f4；继续c47女儿村真实连接/共享服务/原对白/隐藏參須。该分支新内容正常Android仍NOT_RUN，不拿本c41候选背书。

- 本次inspect37185327792在查询前因任务文档缺task_id失败，UNAVAILABLE，非服务器健康结论；已恢复原必需字段并用脚本实际正则核对。v63/run37185326471构建未验收即主动取消，防止浪费runner与旧来源审核。修后新来源重新构建，不用旧产物背书。

## 巡检与限制

最近实际inspect37180842747/2026-10-04T05:46:55.3577520Z：可信27/26=1431/2289，共3720事件；普通真机8会话、模拟器0、测试0、清理0。精确旧v26 apk_update ProtocolException1保持ISSUES_FOUND/根因UNCONFIRMED；不拿旧日志证明新候选健康。发布前后须新巡检。声音、一加13T NOT_RUN。

## 下一条实际动作

提交上述驱动与证据检查，原workflow构建新的v64候选；同产物正常流程/原审批门禁通过才阶段发布。等待期间继续隔离c47实现与局部回归，不改候选来源。新失败读取真实断言后修受影响部分，不无限重试、不主动在单节点结案。

累计十类权威清单：docs/delivery-status.md。路线：docs/original-playthrough-roadmap.md。历史状态完整保留docs/history/world-full01-runtime-checkpoints.md；旧“当前有效”标题均为历史，不与本任务入口竞争。
