# 本地开发迁移：云端安全交接

状态：WAITING_LOCAL_TAKEOVER。公开源码保存与远端核验见本文末尾；私有输入尚未传到用户电脑，不能据此声称整体迁移完成。

最新授权：2026-10-06停止云端新增功能、新发布和WORLD-FULL-01自动续跑。电脑成为唯一开发工作区，云端只保留原GitHub Actions构建验收。未删除任何工作区，未reset/clean、清档、合并main、取消已有CI、触发新workflow/审批/发布。保护规则、reviewer、签名及Language未改。

## 1. 交接入口与分支

仓库：https://github.com/antpan5608-san/game-fengshen

- 接管入口：`work/local-migration-handoff`，含已保存的最新开发源码、当前停止规则与本文。
- 最新未完成开发源码：`work/world-jiameng-next` → `353cf7e8d8d5619d4b9409383a0de22c050948c6`，父提交`72912e3792066a96b9bc7b10c8b78e2a45e7d3b6`。
- 历史训练驱动未提交修改：`work/world-05bfcc05` → `2920d9d9ffcbd0e6c4b86ea3f8a6c5e089eddf0f`，父提交`05bfcc05e1d25de95f5825cc19c29adab7d0b0e9`。
- 冻结R2：`work/world-75ac819c` → `75ac819cad0bc387bafbf35ac3ccb352229aab9f`；远端main也为此SHA，不推送历史本地main、不改远端main。
- 历史本地main另存：`work/local-migration-original-main` → `456892c911a4ff11b1cc5c653af8f2432eeeb486`。
- 原有远端开发备份`work/world-jiameng-batch-continuation` → `06b95cdaabc398c60eecc008eaaea83d93c816c0`保留。这是已运行DEBUG smoke来源，不是353cf7e新批次的App验收。

所有41个工作区及159个本地分支的完整SHA、树SHA、起始未提交状态见[完整清单](handoff/local-migration-inventory.json)。清单记录交接文档提交前的源码检查点；交接文档最终提交以本分支Git HEAD/远端引用为准，不能把开发源码SHA与后续交接文档SHA混为一谈。3个detached工作区HEAD均已有分支可达，未被遗弃。

接管后本地完整分支列表：`git for-each-ref --format="%(refname) %(objectname)" refs/remotes/origin`；交接入口完整SHA：`git rev-parse origin/work/local-migration-handoff`。

## 2. 未提交成果的保存

开工41个工作区只有两处存在未提交修改，均在改动前检查后单独保存：

1. followup工作区的`android/app/src/androidTest/java/org/fengshen/dev/TouchTest.kt`：两个正常训练循环按实际可走相邻格选择输入，不再假定固定切换格。保存为2920d9d9，不把该未完成驱动视为已通过完整App验收。
2. jiameng-next工作区的7个文件保存为353cf7e8：Content.kt、world-jiang-invitation.json、export_development.py、world-city142-terrain-original.tsv、golden-world-jiang-content.json、world-jiang-content.json、probe-world-city142-terrain.py。保留未完成c61批次，未激活生产内容pin、未发版。

该工作区随后切到新的交接分支，原开发分支继续指向353cf7e8。本轮仅追加交接规范、索引和历史归档，没有继续修游戏。一次性未完成元数据生成脚本原字节保存在`tools/handoff/jiang-batch-metadata-archived.py`，SHA-256 `a3bb4c389945ccf875f4e141310ed1f32cea8ba8d36741ff216bed8380842c8f`；它不是新的导入器或CI入口，硬编码云路径、私有输入和待校正EDGE语义，不应直接执行覆盖当前配方。

所有工作区保留原目录。原当前任务完整快照见[历史状态](history/world-full01-before-local-migration-20261006.md)，其中自动继续/发布指令已被本次停止授权替代。累计欠账仍在`docs/delivery-status.md`，未宣称全地图完成。

## 3. 生产、候选、开发的实际区别

| 层次 | 实际状态 | 来源/内容 |
| --- | --- | --- |
| 生产 | v82 / 0.8.12-playable-r1-stable；本轮只读version.json仍为82，STABLE/PASS，人工验收PENDING | APK来源`c461e7c121b6f535d85e2a245b82be1f6e42d786`；c51-r1，18依赖图/120文件 |
| 冻结候选 | v83 / 0.8.13-world-hell-r2，NOT_PUBLISHED；原run仍自行验收，不自动发布 | `75ac819cad0bc387bafbf35ac3ccb352229aab9f`；c50，56依赖图/302文件 |
| 开发c60 | 原资源引用修复及存档历史功能已实现，DEBUG已打包，部分真实App smoke通过；尚有手动回档仪器失败 | 69依赖图/378文件；c60 manifest `8c56f689610cff897c58d5efdac2370f32e0934cd172a3b0b173f0eb6c1b7bdb` |
| 开发c61草案 | 新磻溪/城市/文王邀请批次有源码、配方和局部原CPU/JVM证据；NOT_RESTORED / NOT_BUILT / APP_NOT_RUN / NOT_PUBLISHED | 353cf7e8；生成草案72依赖图/388文件，manifest `bde171f4998f5d88414abe58effee87bcfe3a3c3a51054de4230e7f36704dc9d` |

生产APK SHA-256：`22ca9c1d78ac562789f9b6337089d1f1e2b48b7201705a34ec75746890fce4d9`，16,632,753字节；包名`org.fengshen.dev`，签名指纹`5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6`。生产c51-r1 manifest：`427ea305b23eb8493d6df34c1af3051bc6905f634b20a49d41e635266e67f805`。

生产构建/验收[37261594942](https://github.com/antpan5608-san/game-fengshen/actions/runs/37261594942)，历史发布[37269398974](https://github.com/antpan5608-san/game-fengshen/actions/runs/37269398974)。现有下载对象仍为https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin 。本轮仅查询版本元数据，未重新执行生产APK全字节下载验包；上列生产APK指纹来自已有可信发布证据，不能称本轮新增游戏或真机验收。

## 4. 仍运行的CI与保留的失败

最终提交前只读查询（2026-10-06T05:04:44.269091+00:00）时唯一仍运行的Actions为[37408307126](https://github.com/antpan5608-san/game-fengshen/actions/runs/37408307126)，冻结来源75ac819c。build `112090763026` SUCCESS、runtime `112092610520` SUCCESS、runtime-world `112109867425` IN_PROGRESS，continuation尚无最终结果。后续状态可只读查看；即使完成也不能自动审批或发布。本次没有cancel、rerun、dispatch或approve操作。

此run已有`fengshen-signed-apk` artifact ID `11388012539`，归档digest `sha256:7b5fcda656ad37c32dbfd5c0d6be78aa4141bf839a612e6779c5dc2a13f26f0b`。这是artifact压缩包hash，不是APK hash；新APK完整hash尚未在本交接提取，不能沿用旧候选APK指纹。

[开发smoke37413317670](https://github.com/antpan5608-san/game-fengshen/actions/runs/37413317670)，来源06b95cda，已结束FAILURE：

- 实际ContentTest/井事件codec与ContentLoader通过。
- 实际前台5分钟AUTO通过（306秒），损坏/保留上限/活动与迁移保护相关检查通过。
- 手动回档/Activity重启测试在`TouchTest.kt:55`的`assertTrue(dialog().isShowing)`失败；当前仅有失败栈，尚未区分真实UI故障与仪器时序，不能写已修复或PASS。
- 外部force-stop冷启NOT_RUN；真机、声音、真实云恢复NOT_RUN。
- [失败证据artifact](https://github.com/antpan5608-san/game-fengshen/actions/runs/37413317670/artifacts/11390352218)：`fengshen-development-save-history-smoke`，315,248字节；归档digest `sha256:34b463f81553df5da97a18f48757e19891bca0127ee129e611c19ed05e7d3345`。

旧R2 run37395426221 world因录制器预算校验不一致FAIL、continuationSKIP，属于已定位TEST_HARNESS；75ac有限修复重新构建，旧失败保留。旧smoke37412250645因测试未改变状态而被原AUTO去重，TEST_HARNESS；修正fixture后的真实AUTO结果如上，不更改旧失败记录。

最近既有受保护inspect [37408310009](https://github.com/antpan5608-san/game-fengshen/actions/runs/37408310009) 于2026-10-06 03:18:20 UTC查询可信82/79：8484普通事件、3真机会话，0模拟器/测试，errors空、cleanup0；82=4959、79=3525。观察窗口2026-10-05 02:35:55至2026-10-06 02:18:11 UTC，只证明上传窗口情况，不是整机健康或本轮手机验收。本次迁移没有触发新inspect，不用该历史巡检冒充本次发版结果。

## 5. 私有输入与可恢复内容：迁移缺口

**私有原始输入没有迁到用户电脑，也没有加入Git或公开artifact。** 本仓库中的原始hash/路径索引可以公开审计，但不等于已经取得原文件。当前环境没有授权导出入口可保证这些文件已传到电脑；不要发送Token到聊天、不要把原ROM上传公开Git/Actions。

| 输入 | 云端实际路径/指纹 | 用途与接管情况 |
| --- | --- | --- |
| 匹配ROM | `/workspace/game-fengshen/private-inputs/town02/target.nes`；1,048,592字节，SHA `f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25`，Mapper246 | 本轮实际再次验hash；新地图/原CPU再取证的必要输入。安全迁出或按已授权固定公开来源取得匹配缓存；不是整个Windows目录。 |
| 可信v27 APK基底 | `/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk`；13,371,832字节，SHA `5944141d45edb059294e9de066914c611f1e0cc6f334f32be9584a6ec35cc353` | 本轮实际再次验hash；旧签名artifact build36995827962/source9396d3d3e39db47e13a8cf2a9dd9b2e32bda2d9b，`fengshen-signed-apk`。远端artifact是否未过期需电脑查询；本地缓存保留。 |
| 原取证741文件 | [逐文件路径/大小/hash清单](handoff/local-migration-private-input-manifest.json)，共13,222,243字节 | 最近姜邀请/磻溪/地图边界/房屋等原RAM、PPU、savestate、原截图/日志仍受控目录，未公开原文件。继续复核取证需安全迁出对应子目录。 |
| 用户存档 | 用户手机/电脑及现有App备份机制，云端没有用户真实存档副本 | 不清档、不卸载、不覆盖真实云进度。先保留唯一好的迁移前档和当前可恢复备份，不能以隔离fixture替代真实备份。 |
| 签名/服务器凭据 | 原Actions受保护环境Secrets；当前任务未取得本地keystore或服务器Secret | 不需复制Token或Secrets到源码；电脑使用已有安全连接，Actions保留runner内签名/保护。凭据迁移未完成不能说本地拥有。 |

原始子目录均位于`/workspace/game-fengshen-world-island/private-derived/`：world-panxi7-after-well-talk-valid-pose-controlled、world-king121-join-controlled、world-jiang-king-fonts-controlled、world-jiang-panxi-oam-controlled、world-jiang-exits-controlled、world-jiang-exits-correct-adjacency-controlled、world-house38-39-original-entry-return-controlled。开发工作区的`private-derived/world-jiang-resources`及`private-derived/world-jiang-city142-terrain`另列在hash清单。字体草案/OAM配方/terrain已存在公开派生数据，不必搬回整个历史目录；重审原始采样才需对应原片。

可信APK恢复只取得已发布派生地图/图集/音频/内容定义，不恢复ROM、原始回放、私有日志、许可或凭据。v27基底c14 manifest为`9dc427b5764f67bf41a8ca4275c370202c4788ba4bc8932afbf82f08f2b99495`；复用原`ci_apk.restore/verify`与`export_from_base`按配方链生成c60/c61。当前`ci/content-source.json`仍固定冻结R2 c50，不能直接用默认CONFIG把开发内容写成生产候选，也不能关闭hash校验。

电脑复用c60原恢复入口的精确方式（已在原workflow执行成功，不是现在云端继续运行）：在已有JDK/Android SDK/Pillow环境、仓库根目录，将可信v27文件路径传给以下原函数。Linux/Git Bash示例：

```bash
python - "/安全本地路径/fengshen-remake-v27-release.apk" <<'PYRESTORE'
import json, sys
from pathlib import Path
from tools import ci_apk as ci
ci.CONFIG = json.loads(Path('ci/golden-world-reference-repair-content.json').read_text(encoding='utf-8'))
ci.restore(Path(sys.argv[1]), next_code=84)
PYRESTORE
```

84仅为既有隔离DEBUG测试使用值，不是生产版本分配；电脑后续真实候选需重新查询实际最新发布版本并使用更大versionCode。c61草案不能据此直接启用；先完成所列冲突/验证，保持c60/c50与草案目标hash区分。


生产APK/签名artifact可以恢复其自身c51-r1派生内容，不能替代依赖配方指明的v27基底。公网对象可覆盖，URL的?v参数不是不可变证明。签名APK不包含用于重新签名的私钥。

## 6. 电脑接管的精确下一操作

先在电脑既有工作区检查remote/branch/status，保留未提交源码、私有输入与存档，不reset/clean；不要覆盖原电脑目录。

```bash
git fetch origin
git rev-parse origin/work/local-migration-handoff
git switch --create work/local-migration-handoff --track origin/work/local-migration-handoff
```

如本地已存在同名分支，先检查它的HEAD与未提交修改，再选择新本地接管分支；不要强制切换覆盖。尚无仓库时正常clone后读取此分支。核对本清单完整SHA，电脑是唯一后续开发位置；原两份Actions仅用于已有构建/验收，不把旧连续自动发布授权用于本轮接管。

按顺序继续的最小事项（**当前云任务只记录，不执行**）：

1. 获取匹配ROM/可信v27缓存或artifact并验完整hash；保留现有电脑输入，不要求完整reference/private-derived历史。需要重审的取证仅安全迁出hash清单中相关子目录。配置已有JDK/Android SDK/Gradle wrapper及安全GitHub连接，不复制密钥进Git。
2. 只读查询R2 `gh run view 37408307126 --repo antpan5608-san/game-fengshen --json status,conclusion,jobs`；保持冻结75ac和同审核产物。交接没有授权新的发布，结果通过也停在等待电脑决定。
3. 先读失败artifact11390352218，复现手动回档弹窗失败`TouchTest.kt:55`，分类真实UI/测试时序后最小修复。已有真实5分钟AUTO/损坏/保留上限通过，不重新跑全世界练级；外部cold、旧档升级仍需真实执行。
4. 开发c61未启用：142南边原生探测是从row42走到row43时回world16，草案却写EDGE(row43→44)且有初步VERIFIED字段；此冲突必须校正并保留原证据，不能直接发布或称出口已验。左侧142(3,10)/121(1,29)探测未换图，保留失败，不能猜反向出口。已核7返16(16,65)、121中心返142(15,16)、142右返121(45,29)、121右返142(27,10)都是受控原版证据，不是正常Android路径。
5. 姜加入/成长/装备与c61局部export需要目标pin、空目录restore、相关回归和真实App smoke后才能启用。已有3个姜事务JVM、2个磻溪、84守卫/256selector/36磻溪/32initializer/484terrain原CPU局部通过；正常Android该新批次NOT_RUN，不能把72依赖图写成72图已可玩。后续由电脑按保留主任务范围接管，云端不自动续跑。

原本地云环境仅供参考：JDK `/workspace/scratch/jdk21/usr/lib/jvm/java-21-openjdk-amd64`、SDK `/workspace/scratch/android-sdk`、原Gradle wrapper。电脑不必复制这些Linux工具目录；原Actions KVM仍承担隔离Android运行。本轮未触发新的构建/测试。

## 7. 工作区完整SHA快照

| 工作区 | 分支 | 已保存源码HEAD（完整SHA） | 保存情况 |
| --- | --- | --- | --- |
| /workspace/game-fengshen | main | `456892c911a4ff11b1cc5c653af8f2432eeeb486` | 原样保留 |
| /workspace/game-fengshen-armor-acquisition | test/armor-acquisition | `ead1c5ec32efff8a85f6a63760188c8ca409a098` | 原样保留 |
| /workspace/game-fengshen-cave85-runtime | test/cave85-normal-runtime | `03aace72f7342ccf499192455013d6c586690708` | 原样保留 |
| /workspace/game-fengshen-east-content | data/east-palace-content | `47d16bee311f1427f0d42df4204f3d99fd81dbf2` | 原样保留 |
| /workspace/game-fengshen-north-runtime | test/north-pearl-runtime | `840daa1029ccc3d3585ea27d1b0deb3a225afa66` | 原样保留 |
| /workspace/game-fengshen-party-rules | impl/original-party-rules | `5b00054c7be2577853850160c72bcbabd590ed5b` | 原样保留 |
| /workspace/game-fengshen-runtime-summary | work/runtime-summary-safety | `b473d3b93f9b6e2d7c42ca7cd652761757011359` | 原样保留 |
| /workspace/game-fengshen-status4-test-fix | test/status4-supported-fixture | `d12c02803f146c1656a0b097a1e2a37cda0696b3` | 原样保留 |
| /workspace/game-fengshen-updater-review | review/updater-protocol | `7b9142745d2a79ec83e16931b7e95b820722dac1` | 原样保留 |
| /workspace/game-fengshen-world-after-forest | work/world-after-forest | `addc84c46e890b9d7792f1074c48a234f3b2469d` | 原样保留 |
| /workspace/game-fengshen-world-after-medical | work/world-13e9d9e6 | `13e9d9e67ea65441d3eb34658a9b7d20fa16473d` | 原样保留 |
| /workspace/game-fengshen-world-after-tree | work/world-909a8ec3 | `909a8ec3694ef7376f40f0de1288e29fae90513b` | 原样保留 |
| /workspace/game-fengshen-world-batch | work/world-fbfc8cac | `fbfc8cac21644ecefdc066f364abb87a188b8464` | 原样保留 |
| /workspace/game-fengshen-world-c36-publish-policy | work/world-263e7dbc | `263e7dbce24a3baa26abfb0c267783dfe0ad3a9e` | 原样保留 |
| /workspace/game-fengshen-world-cave-candidate | work/world-9b438f73 | `9b438f73ded6400f0ea130154f71e64b8885f75c` | 原样保留 |
| /workspace/game-fengshen-world-chest-loader-fix | work/world-f2f86ffa | `f2f86ffa47dbf9ec8ea933e9432e868ea928a019` | 原样保留 |
| /workspace/game-fengshen-world-continuation | work/world-75ac819c | `75ac819cad0bc387bafbf35ac3ccb352229aab9f` | 原样保留 |
| /workspace/game-fengshen-world-coverage | work/world-99a036ba | `99a036ba80ecec5fed9d0f5dfb86f947bfc3cb98` | 原样保留 |
| /workspace/game-fengshen-world-east-batch | work/world-91f2708d | `91f2708d0e918097d57e58f3fd3e0263623cdc94` | 原样保留 |
| /workspace/game-fengshen-world-ferry | work/world-87df6cf8 | `87df6cf836ec7d2106f2231f67f13c622272528c` | 原样保留 |
| /workspace/game-fengshen-world-first-hall | work/world-ccefece6 | `ccefece6c5fd66bfe41c353daca17fe945223f77` | 原样保留 |
| /workspace/game-fengshen-world-followup | work/world-05bfcc05 | `2920d9d9ffcbd0e6c4b86ea3f8a6c5e089eddf0f` | 起始修改已保存；见下文 |
| /workspace/game-fengshen-world-ghost-batch | work/world-bb342354 | `bb3423548e3c5ecdee54baa5ba8feaa68815b0cd` | 原样保留 |
| /workspace/game-fengshen-world-hall-npc-test-fix | work/world-82eb31d9 | `82eb31d92dba47edc74e0fb501452c8ff0c03661` | 原样保留 |
| /workspace/game-fengshen-world-hell-batch | work/world-73cc1b90 | `73cc1b901ee3b43a5b5ad216a43bde8e45cad655` | 原样保留 |
| /workspace/game-fengshen-world-hell-rest | work/world-f38b4fa8 | `f38b4fa8f9676a19efeae3133d7515135d0e9cc1` | 原样保留 |
| /workspace/game-fengshen-world-island | work/world-island | `5e94b68f51a7c30de40443a4751af8d3de9d2d24` | 原样保留 |
| /workspace/game-fengshen-world-item-source-fix | work/world-8aa3b6b9 | `8aa3b6b9e9aeb49f44287b35a4f7f9ae3cec95ff` | 原样保留 |
| /workspace/game-fengshen-world-jiameng | work/world-jiameng | `7747010d2cd71ab78c6e1a459440b54530e8ea9d` | 原样保留 |
| /workspace/game-fengshen-world-jiameng-next | work/local-migration-handoff | `353cf7e8d8d5619d4b9409383a0de22c050948c6` | 起始修改已保存；见下文 |
| /workspace/game-fengshen-world-last-halls | work/world-a73241ad | `a73241ade785472d5a105afd33d0a7bbd1cd1069` | 原样保留 |
| /workspace/game-fengshen-world-next | work/world-c461e7c1 | `c461e7c121b6f535d85e2a245b82be1f6e42d786` | 原样保留 |
| /workspace/game-fengshen-world-post-rebirth | work/world-post-rebirth | `b1ef6aad0faf85b171275d71d530a96b541485f4` | 原样保留 |
| /workspace/game-fengshen-world-rebirth | work/world-8e5f4d9d | `8e5f4d9d7a0304ea91ab0606a0725d9340ba51e1` | 原样保留 |
| /workspace/game-fengshen-world-rebirth-exit-fix | work/world-b19de048 | `b19de0481bfa7132bb42b21bcb57ef7b7c573103` | 原样保留 |
| /workspace/game-fengshen-world-village3-clinic | work/world-c552900a | `c552900a4c831bc6451017e93fc3bc21b0e0be4a` | 原样保留 |
| /workspace/game-fengshen-world-village4 | work/world-village4 | `10ddf56b09de0f74ee9e2418685d050a2893e02d` | 原样保留 |
| /workspace/game-fengshen-xiaolongnv-data | data/xiaolongnv-party | `a8625c230e5841ff2c48c45d286a8f2efbd04898` | 原样保留 |
| /workspace/scratch/nanhai-clean-checkpoint | DETACHED | `40db7d2aa5c7b31535c86aaee301391949d6a5ce` | 原样保留 |
| /workspace/scratch/nanhai-clean-final | DETACHED | `71b2f233e06c8393387d8e21eb470151d6128fb6` | 原样保留 |
| /workspace/scratch/nanhai-clean-origin | DETACHED | `142de0e73176283c7358e696a81f5d619c90a95b` | 原样保留 |

## 8. 交接保存与安全核验

公开历史已审计：6390个Git对象、2220个blob；未发现Token/private-key模式。历史跟踪路径未发现ROM/原始回放/密钥/私有日志路径；实际私有文件只保留忽略目录，必要索引仅路径/hash。本检查是有限模式/路径审计，不是宣称所有历史内容绝对无敏感数据。

实际普通Git非force推送命令（显式排除main）在send-pack阶段失败：`RPC failed; HTTP 401 curl 22` / `unexpected disconnect`。公开fetch、GitHub API读写可用；平台代理8080/TLS保留，没有复制placeholder为真实Token、没有改认证或绕过网络限制。采用当前已授权GitHub Git Data API保存公开blob/tree/commit，逐对象校验原SHA，引用创建或更新仅非force；不改工作流/保护/reviewer。迁移过程中没有输出、发送或落盘Token。159个本地分支的45份不同workflow内容已检查，未发现push/pull_request/workflow_run/repository_dispatch自动触发配置，因此保存分支不会启动新CI。

截至2026-10-06T05:04:44.269091+00:00，158个非main本地分支已保存到GitHub，逐分支远端SHA与本地完整SHA一致，0不匹配；包括历史本地main的独立保存分支。263个缺失历史提交均保持原SHA，没有重写作者/父链或force更新。远端共167个分支（含原有备份），完整SHA见[远端分支清单](handoff/local-migration-remote-refs.json)。远端main仍75ac819cad0bc387bafbf35ac3ccb352229aab9f，未合并或更新。交接文档提交将在此源码快照后仅推进work/local-migration-handoff。

交接文档最终提交只追加本文/当前任务/停止规则/公开索引与归档；源代码开发SHA仍为353cf7e8。交接完成后停止云端自动研发，等待用户电脑接管；**私有输入传输与电脑构建尚未完成**，不得将“公开成果已保存”写成“电脑已全部迁移且可构建”。
