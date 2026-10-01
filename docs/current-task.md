task_id: TOUCH-UX-01
status: IN_PROGRESS

# 物品、装备和已有商店的手机直接触控适配

仅改当前已实现物品/装备/三店的触屏交互：点列表选择，明确按钮提交；滚动列表与安全dp目标，稳定对象/状态绑定，模态输入隔离。复用TownTrade/OpeningEquipment/HerbUse/统一状态与SaveSnapshot、SurfaceView/Canvas和原CI，不改价格/药效/槽位/剧情，不扩战斗、地图、其他菜单。TOWN-02已发布，其药草逻辑直接复用，十类欠账仍见delivery-status权威清单。

基线main 7e280ed55c66cd59ed149f0a3b1acbad545a1a30，工作区原干净；生产v23/0.8.3-town-02/c12，APK SHA256 1a5a5e10f2793c1418a83a2da3b218ebdc2ff2274b2f02d3d1a30c2153d63bbf，内容manifest 8ef01830b269d58294d6e6830676b4f9c9ac593e079eedf32de76c2fd35080f8。纯UX无数据变化不升级内容包。

开工首次gh查询HTTP401，runtime现观测连接恢复；原inspect运行36905625298已启动（旧源码任务标签TOWN-02），重新以本任务记录巡检后业务修改。不得用旧日志/无样本证明健康；手机、声音和长时验收分别记录。

本轮实现后验证U1—U8并更新精简fengshen-touch-ux skill，职责只限被授权触屏界面，不复制内容/巡检/发布体系。skill显式/隐式匹配按实际Codex能力隔离验证，不以测试提示词冒充成功。只有实际App路径/保存/升级/布局验证通过才发布递增版本，同提交/同产物/同签名和两Fengshen对象边界保持。完成后停止。
