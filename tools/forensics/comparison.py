"""Evidence-gated comparison. Missing ROM is UNKNOWN, never REFERENCE_ONLY."""
import collections
import re
from .common import ROOT, DOMAINS, DIFFS, load, save
from .validator import Report, schema_errors, validate_provenance, validate_canonical

MARKERS=[('工作室','studio branding'),('VIP','VIP commerce'),('购买金币','currency purchase'),
 ('付费','payment'),('商城','modern storefront'),('充值','top-up'),('内购','in-app purchase'),
 ('人民币','modern currency'),('支付宝','payment service'),('微信支付','payment service'),
 ('购买存档','save-slot purchase'),('解锁存档','save-slot commerce'),
 ('游戏制作者','developer self-insert'),('黑白牛','reference studio/author branding'),
 ('程序员','modern developer reference'),('贴吧','modern social platform'),('微博','modern social platform'),
 ('qq群','modern social group'),('扫荡券','modern sweep ticket'),('秒天秒地秒空气','modern promotional meme')]
PATTERNS=[(r'购买.{0,12}金币','currency purchase'),(r'购买.{0,12}存档','save-slot purchase')]

def markers(value,path=''):
    found=[]
    if isinstance(value,dict):
        for k,v in value.items():found.extend(markers(v,path+'/'+str(k)))
    elif isinstance(value,list):
        for i,v in enumerate(value):found.extend(markers(v,path+'/'+str(i)))
    elif isinstance(value,str):
        for token,reason in MARKERS:
            if token.casefold() in value.casefold():found.append({'field':path,'marker':token,'value':value,'reason':reason})
        for pattern,reason in PATTERNS:
            if re.search(pattern,value):found.append({'field':path,'marker':pattern,'value':value,'reason':reason})
    return found

def reference_entries(raw):
    byid={x['id']:x for x in raw['records']};maps={x['id']:x for x in raw['maps']};entries={d:[] for d in DOMAINS}
    for domain,ids in raw['domains'].items():
        for rid in ids:
            rec=byid[rid];payload=rec['data'];refs=list(rec['sourceRefs'])
            if domain=='maps':
                m=maps[rec['legacyId']];payload={'data':payload,'tmxSha256':m['sha256']};refs+=m['sourceRefs']
            entries[domain].append({'id':rid,'legacyId':rec['legacyId'],'payload':payload,'sourceRefs':refs})
    entries['assets']=[{'id':a['id'],'legacyId':a['path'],'payload':{'path':a['path'],'sha256':a['sha256']},'sourceRefs':a['sourceRefs']} for a in raw['assets']]
    return entries

def verified_source(sid,sources,rom_sha):
    s=sources.get(sid,{})
    if not s.get('originalVerified') or s.get('confidence')!='VERIFIED':return False
    for eid in s.get('evidenceRefs',[]):
        e=sources.get(eid,{});loc=e.get('locator',{})
        if e.get('confidence')!='VERIFIED':continue
        if e.get('source')=='ORIGINAL_ROM' and rom_sha and loc.get('romSha256')==rom_sha and loc.get('length',0)>0 and 'offset' in loc and loc.get('meaning'):return True
    return False

def field_differences(reference,original,path='',partial=False):
    if isinstance(reference,dict) and isinstance(original,dict):
        result=[];keys=set(original) if partial else set(reference)|set(original)
        for key in sorted(keys):
            if key not in reference or key not in original:
                result.append({'field':path+'/'+key,'reference':reference.get(key),'original':original.get(key),'presenceMismatch':True})
            else:result+=field_differences(reference[key],original[key],path+'/'+key,partial)
        return result
    return [] if reference==original else [{'field':path or '/','reference':reference,'original':original}]

def compare(raw,sources,original=None):
    original=original or {'schemaVersion':1,'baseRomSha256':None,'completeDomains':[],'coverageSourceRefs':{},'records':[]}
    r=Report('comparison-input');schema_errors(original,'original-comparison',r);idx=validate_provenance(sources,r)
    if r.issues:raise ValueError('Invalid comparison/provenance: '+str(r.finish()['issues'][:5]))
    orig_by_ref={};original_only=[];seen=set()
    for x in original['records']:
        key=(x['domain'],x['referenceId']);identity=(x['domain'],x['id'])
        if identity in seen or (x['referenceId'] and key in orig_by_ref):raise ValueError('Ambiguous original comparison IDs')
        seen.add(identity)
        if x['referenceId']:orig_by_ref[key]=x
        else:original_only.append(x)
    def verified(x):return bool(x['sourceRefs']) and all(verified_source(s,idx,original['baseRomSha256']) for s in x['sourceRefs'])
    entries=reference_entries(raw);records=[];used=set()
    for domain,rows in entries.items():
        covered=domain in original['completeDomains'] and bool(original['coverageSourceRefs'].get(domain)) and all(verified_source(s,idx,original['baseRomSha256']) for s in original['coverageSourceRefs'].get(domain,[]))
        for row in rows:
            o=orig_by_ref.get((domain,row['id']));status='UNKNOWN';diff=[];evidence=[]
            reason='No verified original counterpart; reference existence alone proves nothing.'
            if o:
                used.add((domain,row['id']));evidence=o['sourceRefs']
                if verified(o) and o['comparable']:
                    diff=field_differences(row['payload'],o['comparable'],partial=not o['complete'])
                    status='MODIFIED' if diff else 'MATCH' if o['complete'] else 'LIKELY_MATCH'
                    reason='Compared explicit fields against cited original evidence; partial equivalence is not full verification.'
                else:reason='Original candidate lacks verified evidence; comparison withheld.'
            elif covered:status='REFERENCE_ONLY';reason='Absent from explicitly complete, verified original domain enumeration.'
            flags=markers(row['payload'])
            records.append({'domain':domain,'referenceId':row['id'],'legacyId':row['legacyId'],
              'originalId':o['id'] if o else None,'status':status,'originalVerified':status=='MATCH',
              'sourceRefs':row['sourceRefs'],'originalSourceRefs':evidence,'reason':reason,'differences':diff,
              'tags':['NON_ORIGINAL_REFERENCE_CONTENT'] if flags else [],'markerEvidence':flags})
    if set(orig_by_ref)-used:raise ValueError('Original comparison refers to unknown reference IDs: '+str(set(orig_by_ref)-used))
    for o in original_only:
        records.append({'domain':o['domain'],'referenceId':None,'legacyId':None,'originalId':o['id'],
          'status':'ROM_ONLY' if verified(o) and o['complete'] else 'UNKNOWN','originalVerified':False,
          'sourceRefs':[],'originalSourceRefs':o['sourceRefs'],'reason':'Explicit original-only candidate; no implicit string matching.',
          'differences':[],'tags':[],'markerEvidence':[]})
    summary={}
    for d in DOMAINS:
        subset=[x for x in records if x['domain']==d];c=collections.Counter(x['status'] for x in subset)
        summary[d]={'referenceCount':len(entries[d]),'originalVerified':sum(x['originalVerified'] for x in subset),
          'nonOriginalTagged':sum(bool(x['tags']) for x in subset),'statuses':{s:c[s] for s in DIFFS}}
    # Scan every table, not just the headline domains (e.g. yingyi and LianJi).
    flagged=[]
    for x in raw['records']:
        hits=markers(x['data'])
        if hits:flagged.append({'referenceId':x['id'],'table':x['table'],'legacyId':x['legacyId'],
          'sourceRefs':x['sourceRefs'],'tag':'NON_ORIGINAL_REFERENCE_CONTENT','evidence':hits})
    return {'schemaVersion':1,'status':'WAITING_FOR_ROM' if not original['baseRomSha256'] else 'EVIDENCE_REVIEW',
       'baseRomSha256':original['baseRomSha256'],'summary':summary,'records':records,
       'nonOriginalContent':{'uniqueSourceRows':len(flagged),'records':flagged},
       'note':'Modern-content tags are semantic screening evidence, not a fabricated ROM diff. No automatic canonical promotion.'}

def write_comparison(report):
    save(ROOT/'reports/reference-vs-original.json',report)
    lines=['# Reference vs Original','',f"状态：**{report['status']}**。固定参考提交见 raw manifest。",'',
      '|数据域|Reference|原版已验证|明显改编标签|UNKNOWN|','|---|---:|---:|---:|---:|']
    for d,s in report['summary'].items():lines.append(f"|{d}|{s['referenceCount']}|{s['originalVerified']}|{s['nonOriginalTagged']}|{s['statuses']['UNKNOWN']}|")
    lines+=['','items与equipment有交集；shops为230条库存行+21条store行，不等于251家店。',
      '',f"现代内容：**{report['nonOriginalContent']['uniqueSourceRows']} 个唯一数据库行**。匹配字段/原文/来源详见[JSON报告](../reports/reference-vs-original.json)。",'',
      '|来源行|命中词|','|---|---|']
    for x in report['nonOriginalContent']['records']:
        lines.append('|'+x['referenceId']+'|'+'、'.join(sorted({h['marker'] for h in x['evidence']}))+'|')
    lines+=['','## 分类规则','',
      '- MATCH：有已核原版证据，显式比较的完整研究记录相同；仍须人工转换语义后才可进入canonical。',
      '- LIKELY_MATCH：有证据的部分字段相同，不能提升整行真实性。',
      '- MODIFIED：有证据的字段确有差异，逐字段列出。',
      '- REFERENCE_ONLY：仅在原版整个域有完整、已验证的枚举证据时使用。',
      '- ROM_ONLY：显式原版独有候选，且记录完整、有已核证据。',
      '- UNKNOWN：无原版、证据不足、映射不明；不得把缺ROM视作不存在。',
      '', '现代标记筛查覆盖所有表的字符串：'+ '/'.join(x[0] for x in MARKERS)+'；另匹配购买与金币/存档间的短文本。',
      '没有命中词不代表原版；关联地图/NPC/敌人可能同属改编，只列为人工调查线索，不按ID区间整段删除。',
      '所有原始行保留；排除仅发生在canonical准入。没有导入原版比较输入时，259地图、639对话逐ID均为UNKNOWN。',
      '', '比较输入契约见`game-data/schemas/original-comparison.schema.json`；maps比较对象为`{data:原map行,tmxSha256:参考TMX哈希}`，其他表为完整原行，assets为`{path,sha256}`。',
      'TMX哈希不适合直接与ROM数据比较；只有经证据支持的同义转换才可生成该比较对象。数据库字段也需明确单位/编码，不能把ROM字节硬套参考字段。',
      '`complete`表示整个研究记录已覆盖；`completeDomains`还需`coverageSourceRefs`提供整域枚举证据。原版证据必须绑定ROM哈希/offset/length/meaning；实机记录可补强ROM，不能单独替代ROM证据。']
    (ROOT/'docs/reference-vs-original.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')

def empty_baseline(waiting=True):
    return {'schemaVersion':1,'contract':'forensic-baseline-v1','profile':'verified-baseline',
      'status':'BLOCKED_WAITING_FOR_ROM' if waiting else 'BLOCKED_UNVERIFIED','runtimeReady':False,'entities':{d:[] for d in DOMAINS}}

def publish_canonical(candidate,sources,comparison,asset_root=None):
    """Explicit semantic candidate only. No raw-row conversion or defaults."""
    shape=Report('canonical-input');schema_errors(candidate,'canonical-baseline',shape)
    if shape.issues:raise ValueError('Invalid canonical candidate shape: '+str(shape.issues[:3]))
    if candidate['profile']!='verified-baseline':raise ValueError('Synthetic fixtures may not be published as canonical')
    if candidate['status']=='READY_FOR_REVIEW' and not any(candidate['entities'].values()):raise ValueError('An empty candidate cannot claim READY_FOR_REVIEW')
    blocked_sources={s for x in comparison['nonOriginalContent']['records'] for s in x['sourceRefs']}
    source_index={s['id']:s for s in sources['records']}
    def has_blocked_lineage(ids,seen=None):
        seen=set() if seen is None else seen
        for sid in ids:
            if sid in blocked_sources:return True
            if sid in seen:continue
            seen.add(sid)
            if has_blocked_lineage(source_index.get(sid,{}).get('evidenceRefs',[]),seen):return True
        return False
    rejected=[]
    for d,rows in candidate['entities'].items():
        kept=[]
        for x in rows:
            if markers(x) or has_blocked_lineage(x['sourceRefs']):rejected.append({'domain':d,'id':x['id'],'reason':'NON_ORIGINAL_REFERENCE_CONTENT'})
            else:kept.append(x)
        candidate['entities'][d]=kept
    check=validate_canonical(candidate,sources,asset_root)
    # Excluding modern content must not leave dangling references or silently cascade-delete.
    if check['counts'].get('ERROR'):raise ValueError('Canonical candidate failed validation: '+str(check['issues'][:5]))
    save(ROOT/'game-data/canonical/baseline.json',candidate)
    save(ROOT/'reports/canonical-admission.json',{'admittedCounts':{d:len(x) for d,x in candidate['entities'].items()},
      'rejected':rejected,'runtimeReady':False,
      'note':'No direct reference-to-canonical promotion. Empty baseline is a blocked contract, not a playable dataset.'})
    return check
