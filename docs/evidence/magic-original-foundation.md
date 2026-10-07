# 原版法术基础取证：尚未接入游戏

2026-10-07，MAGIC-ORIGINAL-01 / WORLD-FULL-01 的同一执行队列。线上仍是已验证 v87/c62；本批只增加原取证工具与派生证据，不开放玩家法术，不生成新的游戏版本。

原 [probe-original-magic.py](../../tools/rom-extractor/probe-original-magic.py) 复用 Reader 和既有 probe-world-yang-join.py 的有界 CPU harness。精确目标 ROM 校验保持；RAM 和原截图只在私有忽略目录，不进入公共仓库。输出见 [原证据报告](../../game-data/provenance/expected/original-magic/original-magic-report.json)。

实际执行 320 个 battle 等级案例、320 个 field-prefix 等级案例和 240 个治疗 effect-prefix 案例，全部通过；三个 TSV 与独立私有旧探针逐字节相等。只提取 26 条 field / 22 条 battle 有界记录，名称和效果未推断，targetKindByte / sceneKindByte 不直接映射游戏操作。场景码不是地图 ID。

- Battle ActiveActorId 1 在等级 1..80 的法术数为零；field selector0 别名不能给哪吒添加法术。
- 小龙女初始 12 级 battle/field 数均 2；杨戬 24 级为 1/2；姜子牙 38 级为 4/5。Battle 和 field 数不同，不统一把每项列为两种场景都能用。
- 原 field 菜单小龙女初始显示提神术/解毒术，各 3 MP；受控高等级 10 项三页均实际查看。其余 Unicode 转录、battle 对应与实际效果仍逐项核。
- 受控取消及重新选目标保持 HP/MP；MP2 不足时原提示且不改 HP/MP。MP3 首次提交扣到0已由 raw 观察，后期截图为不足提示，未据此推断完整按键时序。
- 未封顶受控目标实际 HP5→58、MP44→41，对应53点治疗；独立 CPU 前缀矩阵匹配原执行。原 party 搜索只递增一次，没有完整循环，不能套 Reference 公式或把角色编号混用。
- 初始受控解毒，目标状态2→0、HP5不变、MP44→41。健康目标状态仍0、HP5不变、MP44→41；原失败分支缺低字节写回的高字节角落尚待核，不假设全退费。

上述 native 菜单、取消和治疗均明确使用隔离 RAM fixture，不是正常入队、升级或 Android 运行。CPU 治疗从已验证菜单后的918B开始，只跳过 message JSR804F，并在924E的UI阶段前停止；尚未验证的目标合法性、状态、溢出、其他法术和战斗效果继续开放。

最终生产脚本重新实跑880案例，原 span 与脚本哈希验证；JSON 固定 LF，错误 ROM、错误 RAM 和覆盖已有结果均在输出前拒绝。4B 首轮6全新片段91.125s、LF修正后2全新片段15.64s，全部实际阅读并登记 reviewed；输出仅建议。拒绝削弱精确来源/断言/步数上限，以及把未知字节、缺名称或非Android证据判作游戏已失败的建议。

下一步用相同原码核 field 目标及施法者限制、battle 名称/MP时点与初始攻击，再最小接入现有 Content、角色页、World 与原战斗调度。A/B/C、完整主线、真机/声音及累计欠账仍未完成。
