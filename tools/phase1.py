"""Phase 1 local CLI. Run via phase1.ps1 or the isolated Python environment."""
import argparse
import collections
import json
import sys
from pathlib import Path
from forensics.common import ROOT, load, save
from forensics import schemas, rom
from forensics.importer import import_reference
from forensics.comparison import compare, write_comparison, empty_baseline, publish_canonical
from forensics.validator import validate_raw, validate_canonical, validate_original_artifacts, write_report, Report, schema_errors
from forensics.viewer import build_viewer

RAW=ROOT/'game-data/raw/reference-project/dataset.json'
DEFAULT_REFERENCE=ROOT.parent/'FengShenBang-reference'

def source_manifest(extra=None):
    records=load(ROOT/'game-data/provenance/reference-project.json')['records']
    path=Path(extra) if extra else ROOT/'game-data/provenance/original.json'
    if path.exists():records+=load(path)['records']
    return {'schemaVersion':1,'records':records}

def make_comparison(raw,sources,path=None):
    path=Path(path) if path else ROOT/'game-data/raw/rom/comparison.json'
    result=compare(raw,sources,load(path) if path.exists() else None)
    if result['status']=='WAITING_FOR_ROM' and any(p.suffix.lower()=='.nes' for p in (ROOT/'reference/rom').glob('*')):
        result['status']='WAITING_FOR_ORIGINAL_EVIDENCE'
    write_comparison(result)
    research=ROOT/'reports/rom-research.json'
    if research.exists():
        record=load(research)
        if record.get('romSha256')==result.get('baseRomSha256'):
            from forensics.research_reports import write_reports
            write_reports(record,result)
    return result

def rom_document(result):
    save(ROOT/'reports/rom-analysis.json',result)
    lines=['# ROM Analysis','',f"状态：**{result['status']}**。扫描`reference/rom/`；文件只读，ROM及导出二进制被Git忽略。",'']
    if not result['roms']:
        lines+=['没有提供`.nes`文件。以下均为UNKNOWN：SHA-256/SHA-1/MD5、文件大小、iNES/NES 2.0、PRG、CHR、Mapper、Mirroring、Battery-backed RAM、Trainer、原版发行版本。',
          '','字库、字符编码、对话、指针表、控制字符、地图/敌人/道具/成长offset均未定位。639条参考对话验证数为0；没有输出虚构charset或ROM对话。']
    for x in result['roms']:
        lines+=['## '+x['fileName'],'','```json',json.dumps(x,ensure_ascii=False,indent=2),'```','']
    lines+=['','## 工具与解释边界','',
      '- `./phase1.ps1 rom analyze`：扫描并记录哈希及头；可追加文件路径分析单个ROM。结构合法不等于确为未改版《封神榜》。',
      '- `./phase1.ps1 rom dump reference/rom/game.nes --out private-derived/game`：只读导出PRG/CHR/Trainer及manifest。',
      '- `./phase1.ps1 rom banks reference/rom/game.nes --section prg --size 0x4000`：固定大小文件块、熵和哈希；不是已确认的硬件bank/CPU地址。',
      '- `./phase1.ps1 rom search reference/rom/game.nes "4E 45 ?? 1A" --section file`：带通配符二进制模式搜索；返回绝对/分段offset。匹配不证明语义。',
      '- `./phase1.ps1 rom tiles reference/rom/game.nes --out private-derived/chr.png`：CHR平面2bpp图；灰度仅显示索引，不是游戏调色板。可用`--section prg --offset 0x100 --count 256`检查明确选定的候选字节。',
      '', 'NES 2.0支持扩展mapper/submapper、线性/指数乘数ROM长度、易失/非易失RAM shift。iNES byte8=0不编造RAM容量；battery flag只说明持久化标记，不推断具体存档芯片或容量。',
      'CHR大小为0时记录CHR_RAM_INDICATED并拒绝空CHR图导出；字库可能在PRG/压缩/运行时上传中，不能靠头推断其offset。Mirroring为头声明，运行时可能由mapper控制。',
      'Trainer计入PRG起点；截断输入/非法magic/不支持的头型报错；尾部字节保留为trailing段，不静默丢弃。脏iNES padding降低mapper声明置信度。',
      '', 'offset登记契约：`game-data/provenance/rom-offsets.json`，每项必须有ROM SHA-256、offset、length、meaning、evidence来源ID、confidence。实际研究进展见下方profile报告。',
      '', '## 调查顺序','',
      '先确定文件哈希/版本与结构，再检查CHR或PRG中可见字形；依据实际字符表识别文字编码、指针表与控制码，逐条对照639条文本。保留解码原字节与失败标记，不能修改解码结果凑匹配。',
      '随后用对话、地图出入口与战斗出现位置交叉定位地图/敌人/成长/道具；每个确认的offset单独记录证据。',
      '', '格式依据：[iNES](https://www.nesdev.org/wiki/INES)、[NES 2.0](https://www.nesdev.org/wiki/NES_2.0)、[PPU pattern tables](https://www.nesdev.org/wiki/PPU_pattern_tables)、[Mirroring](https://www.nesdev.org/wiki/Mirroring)。']
    research_path=ROOT/'reports/rom-research.json'
    if research_path.exists():
        research=load(research_path)
        if research.get('romSha256') in {r.get('sha256') for r in result['roms']}:
            from forensics.research_reports import rom_section
            lines.append(rom_section(research))
    (ROOT/'docs/rom-analysis.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')

def combined_validation(raw,sources,reference,canonical):
    results=[validate_raw(raw,reference,sources),validate_canonical(canonical,sources,ROOT/'game-data/canonical'),validate_original_artifacts(sources)]
    offsets=load(ROOT/'game-data/provenance/rom-offsets.json');r=Report('rom-offsets');schema_errors(offsets,'rom-offsets',r)
    source_ids={s['id'] for s in sources['records']}
    for entry in offsets.get('records',[]):
        for sid in entry.get('evidence',[]):
            if sid not in source_ids:r.add('INVALID_PROVENANCE_REFERENCE','rom-offsets','Unknown offset evidence',reference=sid)
    results.append(r.finish());allissues=[dict(i,scope=result['mode']) for result in results for i in result['issues']]
    counts=dict(collections.Counter(i['severity'] for i in allissues));codes=dict(collections.Counter(i['code'] for i in allissues))
    return {'schemaVersion':1,'mode':'phase1-all','status':'FAIL' if counts.get('ERROR') else 'REVIEW_REQUIRED' if allissues else 'PASS',
      'counts':counts,'issuesByCode':codes,'issues':allissues,'checksExecuted':sum(r['checksExecuted'] for r in results),
      'originalityVerified':False,'subreports':[{k:v for k,v in r.items() if k!='issues'} for r in results]}

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='command',required=True)
    imp=sub.add_parser('import-reference');imp.add_argument('--reference',type=Path,default=DEFAULT_REFERENCE)
    val=sub.add_parser('validate');val.add_argument('--reference',type=Path,default=DEFAULT_REFERENCE);val.add_argument('--canonical',type=Path,default=ROOT/'game-data/canonical/baseline.json');val.add_argument('--sources',type=Path)
    cmp=sub.add_parser('compare');cmp.add_argument('--original',type=Path);cmp.add_argument('--sources',type=Path)
    can=sub.add_parser('canonical');can.add_argument('--candidate',type=Path);can.add_argument('--sources',type=Path)
    view=sub.add_parser('viewer');view.add_argument('--reference',type=Path,default=DEFAULT_REFERENCE);view.add_argument('--out',type=Path);view.add_argument('--open',action='store_true')
    sub.add_parser('schemas')
    rp=sub.add_parser('rom').add_subparsers(dest='operation',required=True)
    analyze=rp.add_parser('analyze');analyze.add_argument('path',nargs='?',type=Path)
    research=rp.add_parser('research');research.add_argument('path',type=Path)
    for op in ('dump','banks','search','tiles'):
        parser=rp.add_parser(op);parser.add_argument('path',type=Path)
        if op in ('dump','tiles'):parser.add_argument('--out',type=Path,required=True)
        if op!='dump':parser.add_argument('--section',choices=['file','header','trainer','prg','chr','trailing'],default='chr' if op=='tiles' else 'file' if op=='search' else 'prg')
        if op=='banks':parser.add_argument('--size',type=lambda x:int(x,0),default=16384)
        if op=='search':parser.add_argument('pattern')
        if op=='tiles':parser.add_argument('--offset',type=lambda x:int(x,0),default=0);parser.add_argument('--count',type=int)
    a=p.parse_args(argv);code=0
    if a.command=='schemas':schemas.build();result={'schemas':'generated'}
    elif a.command=='import-reference':
        schemas.build();result=import_reference(a.reference);cmp=make_comparison(load(RAW),source_manifest())
        roms=rom.scan_roms();rom_document(roms)
        if not (ROOT/'game-data/provenance/rom-offsets.json').exists():save(ROOT/'game-data/provenance/rom-offsets.json',{'schemaVersion':1,'status':'WAITING_FOR_ROM' if not roms['roms'] else 'RESEARCH_IN_PROGRESS','records':[]})
        if not (ROOT/'game-data/canonical/baseline.json').exists():publish_canonical(empty_baseline(not roms['roms']),source_manifest(),cmp,ROOT/'game-data/canonical')
    elif a.command=='validate':
        result=combined_validation(load(RAW),source_manifest(a.sources),a.reference,load(a.canonical));write_report(result);code=1 if result['counts'].get('ERROR') else 0
        result={k:v for k,v in result.items() if k not in ('issues','subreports')}
    elif a.command=='compare':
        result=make_comparison(load(RAW),source_manifest(a.sources),a.original);result={'status':result['status'],'summary':result['summary'],'uniqueTaggedRows':result['nonOriginalContent']['uniqueSourceRows']}
    elif a.command=='canonical':
        sources=source_manifest(a.sources);comparison=make_comparison(load(RAW),sources)
        candidate=load(a.candidate) if a.candidate else load(ROOT/'game-data/canonical/baseline.json')
        result=publish_canonical(candidate,sources,comparison,ROOT/'game-data/canonical')
    elif a.command=='viewer':result=build_viewer(load(RAW),a.reference,load(ROOT/'reports/reference-vs-original.json'),a.out,a.open)
    elif a.command=='rom':
        if a.operation=='analyze':
            result={'status':'ROM_FILES_ANALYZED','roms':[rom.analyze(a.path)]} if a.path else rom.scan_roms();rom_document(result)
            code=1 if any(x['status']=='INVALID' for x in result['roms']) else 0
        elif a.operation=='dump':result=rom.dump(a.path,a.out)
        elif a.operation=='banks':result=rom.banks(a.path,a.section,a.size)
        elif a.operation=='search':result=rom.search(a.path,a.pattern,a.section)
        elif a.operation=='tiles':result=rom.export_tiles(a.path,a.out,a.section,a.offset,a.count)
        elif a.operation=='research':
            from forensics.fengshen246 import write_research
            result=write_research(a.path)
            comparison=make_comparison(load(RAW),source_manifest())
            baseline=load(ROOT/'game-data/canonical/baseline.json')
            if baseline['status']=='BLOCKED_WAITING_FOR_ROM':
                baseline['status']='BLOCKED_UNVERIFIED';publish_canonical(baseline,source_manifest(),comparison,ROOT/'game-data/canonical')
            rom_document(rom.scan_roms())
    print(json.dumps(result,ensure_ascii=False,indent=2));return code

if __name__=='__main__':
    try:sys.exit(main())
    except (ValueError,OSError,KeyError) as e:print('ERROR: '+str(e),file=sys.stderr);sys.exit(1)
