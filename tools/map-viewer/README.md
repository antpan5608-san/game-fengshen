# Map Viewer

项目根目录：`./phase1.ps1 viewer --open`。不打开浏览器只生成：`./phase1.ps1 viewer`。

输出`reports/map-viewer/index.html`、本地data.js及129张实际被TMX引用的图片；可直接双击HTML，离线运行，不需要服务端/Node构建/旧游戏。源码为本目录三个静态文件；生成器位于[viewer.py](../forensics/viewer.py)。该目录含参考美术副本，生成物被Git忽略。

左侧查地图ID/名字；中间按层显示、缩放、拖动、网格/原始碰撞属性；右侧列出NPC/出入口/宝箱/auto_move/map_object/传送终点及完整源行。点击NPC显示dialogue ID与参考文本；出口可跳至目标map与原坐标；同一格的多条记录同时列出，不模拟Flag条件。

Y坐标顶部/底部两种假设可切换，屏幕同时显示TMX和raw坐标。默认底部只是便于核查Cocos来源的候选，不视为已确认。Collision叠层是mac/macair等原始tile属性分布，**颜色不代表是否可走**。Teleport用story.nextSceneId=2的解释，标的是终点，未推断入口位置。map.jump_map_id没有明确触发格，只在记录列表展示。地图名/坐标/图块/属性全部来自参考项目。

190处TMX图片声明尺寸差异会显示，未强行修复。默认按声明宽度索引；这不是保证与原版相同的渲染。

浏览器验证脚本[viewer-smoke.cjs](../../tests/viewer-smoke.cjs)使用本地Playwright（本机Node runtime已提供）。若没有Playwright自带浏览器，可以设置`PHASE1_BROWSER`为Chrome/Edge可执行文件路径；不用下载浏览器。验证遍历259图并检查搜索、NPC/出口/事件/Teleport、碰撞开关及画布像素。结果见`reports/viewer-test.json`，截图在忽略的`reports/test-artifacts/`。
