# ROM Extractor

项目根目录：`./phase1.ps1 rom analyze`。ROM放`reference/rom/*.nes`，保持只读。目录和`.nes`/`.NES`均被Git忽略。

用法和格式边界见[ROM报告](../../docs/rom-analysis.md)，实现[rom.py](../forensics/rom.py)。等价入口：`.venv/Scripts/python.exe tools/rom-extractor/main.py analyze`。

支持iNES/NES2头、SHA-256/SHA-1/MD5、PRG/CHR/Trainer分段导出、固定块bank检查、十六进制/`??`通配模式搜索、2bpp tile灰度图。通配符模式需作为一个加引号的参数。`--offset`/`--size`支持0x十六进制。

PRG/CHR的dump用于研究；导出目录必须位于输入ROM所在目录外的独立子树，例如`private-derived/game`。这是避免覆盖输入的工具约束，不改变ROM。dump不删除已有文件；若处理不同ROM应使用新目录。manifest声明哪些分段存在，CHR RAM ROM不生成chr.bin。

哈希合法/头合法并不能证明游戏版本未改；字节搜索命中也不能直接算作已验证对话。已确认offset登记在`game-data/provenance/rom-offsets.json`并链接来源；合成ROM测试位于临时目录，绝不作为游戏证据。

## 指纹绑定的游戏研究

`./phase1.ps1 rom research reference/rom/candidate-f3596ffda5c1b838.nes`

实现为现有forensics包的`fengshen246.py`，复用generic analyzer、CHR导出、比较与provenance，不另建入口架构。
只接受SHA-256 `f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25`。
输出到被忽略的`game-data/raw/rom/`：64张CHR sheet、54已核及115新增HIGH字形、12段开局文本（2段实测）、14个其余未解码文本流、11地图及离线Viewer、10NPC记录、177敌人候选、320成长候选与bounded disassembly。V1增量仍在同一profile中，由`vertical_slice.py`完成。
每次重建保留原始字节含义；没有通用GB2312/Big5假设。运行时观测缺失时相关核验降级，不把候选自动写入canonical。

## 原始输入重放

Windows官方FCEUX2.6.6：本地`private-derived/tooling/fceux-2.6.6/fceux64.exe`。
来源`https://github.com/TASEmulators/fceux/releases/tag/v2.6.6`；工具与ROM不提交。
从冷启动执行Lua脚本，参数为`-sound 0 -pal 0 -lua <脚本绝对路径> <ROM绝对路径>`，进程工作目录为模拟器目录。
`probe-npc.lua`复现对话；`probe-status.lua`复现状态与出口；`probe-battle.lua`复现完整首战。
在PowerShell启动后台模拟器时使用`Start-Process -WindowStyle Hidden`。运行前创建对应`private-derived/*-probe/`目录。
当前opening/NPC脚本记录了本机路径；status/battle可设置`FENGSHEN_ROOT`。移植时只改输出路径，不改输入时间线。
所有脚本仅注入手柄输入、读取RAM/PPU和登记观察回调，不写游戏RAM、不改ROM。
V0尝试用文件持久化savestate失败，该尝试不计入证据。V1的`probe-level.lua`从冷启动开始，使用FCEUX内存savestate对象保存每次获胜checkpoint；5次死亡后恢复均核对HP/EXP，并登记branch ID。它不是单一路径无死亡通关，也不是生成EXP的作弊试验。
PNG为请求帧的下一帧，RAM/PPU是标注帧。实际文件哈希见`game-data/provenance/gameplay-captures.json`。

V1使用`probe-slice.lua`、`probe-world.lua`、`probe-level.lua`，均支持`FENGSHEN_ROOT`；输出分别为slice-probe/world-probe/level-probe。
每个脚本需独立冷启动，先创建输出目录，保持NTSC/sound off/cheats off；不要把另一脚本的存档当启动状态。完成后生成complete.txt并暂停。
level探针最长120001帧，达到第二次升级并回到世界状态即可提前结束；这次39420帧完成。
level-check-entry在同一帧重复时文件名会覆盖，V1核验使用唯一growth-entry/change二进制及有序events.tsv/writes.tsv；不把覆盖文件伪称第一次检查状态。
新文件哈希和脚本哈希见`game-data/provenance/gameplay-v1.json`。

## 测试

`./phase1.ps1 test`：保留原有36项；增加指纹、两位平面汉字、控制符/边界、16×15地图块、NPC终止、敌人端序、混合银行成长与证据完整性。
ROM golden仅在本地匹配指纹时运行；缺ROM自动skip，不通过网络下载。`validate`另核验已登记ROM范围及原始证据文件哈希。
