import collections
import hashlib
from pathlib import Path
from jsonschema import Draft202012Validator
from PIL import Image
from .common import ROOT, load, save, sha, contained, DOMAINS

class Report:
    def __init__(self,mode):self.mode=mode;self.issues=[];self.checked=0
    def add(self,code,path,message,severity='ERROR',**detail):
        self.issues.append({'code':code,'path':path,'severity':severity,'message':message,**detail})
    def finish(self):
        counts=dict(collections.Counter(i['severity'] for i in self.issues))
        return {'schemaVersion':1,'mode':self.mode,'status':'FAIL' if counts.get('ERROR') else 'REVIEW_REQUIRED' if self.issues else 'PASS',
          'counts':counts,'issuesByCode':dict(collections.Counter(i['code'] for i in self.issues)),
          'checksExecuted':self.checked,'issues':self.issues,'originalityVerified':False}

def schema_errors(data,name,report):
    schema=load(ROOT/'game-data/schemas'/f'{name}.schema.json');Draft202012Validator.check_schema(schema)
    for e in Draft202012Validator(schema).iter_errors(data):report.add('SCHEMA_VALIDATION','/'.join(map(str,e.absolute_path)),e.message)
    report.checked+=1

def validate_provenance(document,r):
    schema_errors(document,'provenance',r);index={}
    for s in document.get('records',[]):
        if s.get('id') in index:r.add('DUPLICATE_ID',s['id'],'Duplicate provenance ID')
        index[s.get('id')]=s
    for s in index.values():
        for ref in s.get('evidenceRefs',[]):
            if ref not in index:r.add('INVALID_PROVENANCE_REFERENCE',s['id'],'Evidence source missing',reference=ref)
        if s.get('originalVerified'):
            evidence=[index.get(x,{}) for x in s.get('evidenceRefs',[])]
            if not any(x.get('source')=='ORIGINAL_ROM' and x.get('confidence')=='VERIFIED' for x in evidence):
                r.add('UNSUPPORTED_ORIGINAL_VERIFICATION',s['id'],'Requires verified ROM evidence; gameplay may corroborate ROM but cannot replace it')
        if s.get('source')=='ORIGINAL_ROM' and s.get('confidence')=='VERIFIED':
            loc=s.get('locator',{})
            if not loc.get('romSha256') or 'offset' not in loc or loc.get('length',0)<=0 or not loc.get('meaning'):
                r.add('INSUFFICIENT_ROM_EVIDENCE',s['id'],'Verified ROM evidence requires hash, offset, positive length and meaning')
        if s.get('source')=='GAMEPLAY_VERIFIED' and s.get('confidence')=='VERIFIED':
            loc=s.get('locator',{})
            if not (loc.get('path') or loc.get('url')) or not loc.get('meaning'):
                r.add('INSUFFICIENT_GAMEPLAY_EVIDENCE',s['id'],'Verified gameplay evidence requires a locatable record and meaning')
    return index

def validate_original_artifacts(sources):
    """Audit local evidence bytes in addition to the portable provenance schema."""
    r=Report('original-artifact-integrity');cache={}
    for source in sources.get('records',[]):
        if source.get('source') not in ('ORIGINAL_ROM','GAMEPLAY_VERIFIED','MANUAL') or source.get('confidence')!='VERIFIED':continue
        loc=source.get('locator',{});relative=loc.get('path')
        if not relative:continue
        r.checked+=1
        try:path=contained(ROOT,relative)
        except ValueError:r.add('EVIDENCE_PATH_ESCAPE',source['id'],'Evidence file escapes workspace');continue
        if not path.is_file():
            r.add('LOCAL_EVIDENCE_UNAVAILABLE',source['id'],'Recorded evidence is not locally available; cannot recheck bytes','UNKNOWN');continue
        if path not in cache:cache[path]=path.read_bytes()
        payload=cache[path]
        if source['source']=='ORIGINAL_ROM':
            if hashlib.sha256(payload).hexdigest()!=loc.get('romSha256'):
                r.add('ROM_FINGERPRINT_MISMATCH',source['id'],'Current local ROM differs from evidence fingerprint');continue
            start=loc.get('offset',-1);length=loc.get('length',0)
            if start<0 or length<=0 or start+length>len(payload):
                r.add('ROM_EVIDENCE_RANGE_INVALID',source['id'],'ROM evidence range exceeds file');continue
            payload=payload[start:start+length]
        if loc.get('sha256') and hashlib.sha256(payload).hexdigest()!=loc['sha256']:
            r.add('EVIDENCE_HASH_MISMATCH',source['id'],'Local evidence bytes differ from recorded SHA-256')
    return r.finish()

def validate_raw(data,reference,sources):
    r=Report('reference-research');reference=Path(reference).resolve()
    schema_errors(data,'raw-reference',r);src=validate_provenance(sources,r)
    if any(i['code']=='SCHEMA_VALIDATION' for i in r.issues):return r.finish()
    tables=collections.defaultdict(dict);seen=set()
    for rec in data['records']:
        rid=rec['id'];r.checked+=1
        if rid in seen or rec['legacyId'] in tables[rec['table']]:r.add('DUPLICATE_ID',rid,'Duplicate source/entity ID')
        seen.add(rid);tables[rec['table']][rec['legacyId']]=rec['data']
        for col in data['tableSchemas'][rec['table']]['columns']:
            val=rec['data'][col['name']];decl=col['type'].lower()
            if val is not None and decl in ('integer','int','float','real') and not isinstance(val,(int,float)):
                r.add('SQLITE_STORAGE_CLASS_MISMATCH',rid+'/'+col['name'],'SQLite stored a nonnumeric value in numeric-affinity column; preserved unchanged','WARNING',value=val,declaredType=decl)
        for source in rec['sourceRefs']:
            if source not in src:r.add('INVALID_PROVENANCE_REFERENCE',rid,'Source manifest entry missing',reference=source)
    target_code={'map':'MAP','map_npc':'NPC','story':'DIALOGUE','monster':'ENEMY','object':'ITEM','skill':'SKILL','auto_move':'EVENT','battle':'EVENT','map_monster':'ENEMY'}
    def ref(table,row,key,target,skip=(0,None)):
        r.checked+=1;value=row.get(key)
        if value not in skip and value not in tables[target]:r.add('INVALID_'+target_code.get(target,target.upper())+'_REFERENCE',f'{table}/{row.get("id")}/{key}',f'Reference missing in {target}',value=value)
    links={'map':{'jump_map_id':'map'},'map_npc':{'mapId':'map','storyId':'story','zero_id':'map_npc'},
     'map_box':{'mapId':'map','objId':'object'},'map_buy':{'mapId':'map','npcId':'map_npc','objId':'object'},
     'map_mask':{'map_id':'map'},'map_object':{'map_id':'map'},
     'auto_move':{'mapId':'map','npcId':'map_npc','storyId':'story','autoMoveId':'auto_move'},
     'map_warp_point':{'cur_map_id':'map','tar_map_id':'map'},
     'story':{'speakerId':'map_npc','next_story_id':'story','getItemId':'object','autoMoveId':'auto_move'},
     'monster':{'skillId':'skill','object':'object'},'object':{'sell_storyId':'story'},
     'battle':{'map_id':'map','nextStoryId':'story','autoMoveId':'auto_move','mibaoid':'object'},
     'yingyi':{'mapId':'map','unlock_mapId':'map'},'store':{'connect_id':'object'}}
    for table,fields in links.items():
        for row in tables[table].values():
            for col,target in fields.items():ref(table,row,col,target)
    for table in ('map_monster','battle'):
        for row in tables[table].values():
            target='map_monster' if table=='battle' and row['type']==1 else 'monster'
            for n in range(1,7):ref(table,row,f'monster_{n:02}',target)
    for row in tables['story'].values():
        if not isinstance(row['speak_words'],str) or not row['speak_words'].strip():r.add('MISSING_DIALOGUE',f"story/{row['id']}",'Empty dialogue')
        if row['nextSceneId'] in ('2','3'):ref('story',row,'sceneParam','map' if row['nextSceneId']=='2' else 'battle')
    maps={m['id']:m for m in data['maps']}
    if len(maps)!=len(data['maps']):r.add('DUPLICATE_ID','maps','Duplicate TMX map ID')
    for row in tables['map'].values():
        if row['id'] not in maps:r.add('MISSING_MAP',str(row['id']),'Missing decoded TMX')
    asset_paths={x['path'] for x in data['assets']}
    actual_paths={p.relative_to(reference).as_posix() for p in reference.rglob('*') if p.is_file()}
    # Reuse the Phase 0 locations, but recheck the current files and current source values.
    patterns=[('map','res','{}'),('map_mask','res','resCCB/Object/{}.png'),('monster','res','resCCB/Battle/Monster/{}.png'),
      ('object','res','resCCB/Icon/{}.png'),('skill','skEffect','ccbFiles/Battle/Effect/{}.ccbi'),('battle','bg','resCCB/Battle/Bg/{}.png'),('map_object','res','{}')]
    for table,column,pattern in patterns:
        for row in tables[table].values():
            if row[column] in (None,'','0',0):continue
            path='Resources/res/'+pattern.format(row[column]);r.checked+=1
            if path not in actual_paths:r.add('MISSING_ASSET',f'{table}/{row["id"]}/{column}','Referenced asset missing under explicit/reference naming convention',asset=path)
    for row in tables['map_npc'].values():
        for direction in ('Up','Down','Left','Right'):
            path=f'Resources/res/ccbFiles/Role/{row["res"]}_{direction}.ccbi';r.checked+=1
            if path not in actual_paths:r.add('MISSING_ASSET',f'map_npc/{row["id"]}/res','Reference naming convention cannot locate NPC animation',asset=path)
    for row in tables['map'].values():
        if row['bgm']:
            path=f'Resources/res/Sound/bgm_{row["bgm"]:03}.mp3';r.checked+=1
            if path not in actual_paths:r.add('MISSING_ASSET',f'map/{row["id"]}/bgm','Reference BGM file missing',asset=path)
    for asset in data['assets']:
        r.checked+=1
        for sid in asset.get('sourceRefs',[]):
            if sid not in src:r.add('INVALID_PROVENANCE_REFERENCE',asset['id'],'Asset source missing',reference=sid)
        try:path=contained(reference,asset['path'])
        except ValueError as e:r.add('MISSING_ASSET',asset['id'],str(e));continue
        if asset['path'] not in actual_paths:r.add('MISSING_ASSET',asset['id'],'Missing asset or wrong case',asset=asset['path'])
        elif sha(path)!=asset['sha256']:r.add('ASSET_HASH_MISMATCH',asset['id'],'Source asset changed')
    for m in maps.values():
        for sid in m.get('sourceRefs',[]):
            if sid not in src:r.add('INVALID_PROVENANCE_REFERENCE',str(m['id']),'TMX source missing',reference=sid)
        width,height=int(m['attributes']['width']),int(m['attributes']['height']);ranges=[]
        for t in m['tilesets']:
            im=t['image'];a=t['attributes']
            if not im or im['path'] not in actual_paths:r.add('MISSING_ASSET',f"map/{m['id']}",'Missing tileset image');continue
            with Image.open(contained(reference,im['path'])) as image:actual=image.size
            declared=(int(im['width']),int(im['height']))
            if actual!=declared:r.add('TILESET_DIMENSION_MISMATCH',f"map/{m['id']}",'Declaration differs; no GID repair applied','WARNING',image=im['path'],declared=declared,actual=actual)
            capacity=(declared[0]//int(a['tilewidth']))*(declared[1]//int(a['tileheight']))
            ranges.append((int(a['firstgid']),int(a['firstgid'])+capacity))
        for layer in m['layers']:
            r.checked+=1
            if len(layer['gids'])!=width*height:r.add('INVALID_LAYER_SIZE',f"map/{m['id']}/{layer['attributes']['name']}",'Grid cell count mismatch')
            bad={g&0x0fffffff for g in layer['gids'] if g and not any(lo<=(g&0x0fffffff)<hi for lo,hi in ranges)}
            if bad:r.add('INVALID_TILE_REFERENCE',f"map/{m['id']}",'Out-of-range GID',gids=sorted(bad))
    def coord(table,row,mapkey,xkey,ykey):
        if row.get(mapkey) in (0,None):return
        m=maps.get(row.get(mapkey))
        if not m:return
        x,y=row.get(xkey),row.get(ykey);w,h=int(m['attributes']['width']),int(m['attributes']['height']);r.checked+=1
        if not isinstance(x,int) or not isinstance(y,int):r.add('INVALID_COORDINATES',f'{table}/{row["id"]}','Noninteger grid coordinates');return
        if not (0<=x<w and 0<=y<h):r.add('RAW_COORDINATE_OUTSIDE_ZERO_BASED_BOUNDS',f'{table}/{row["id"]}',
           'Outside zero-based TMX bounds; origin/world-offset/cutscene semantics unresolved; raw values retained','WARNING',mapId=row[mapkey],x=x,y=y,width=w,height=h,fields=[xkey,ykey])
    for table,keys in {'map_npc':('mapId','zeroPosX','zeroPosY'),'map_box':('mapId','posX','posY'),
      'map_object':('map_id','posX','posY'),'auto_move':('mapId','hero_posX','hero_posY')}.items():
        for row in tables[table].values():coord(table,row,*keys)
    for row in tables['map_warp_point'].values():
        coord('map_warp_point',row,'cur_map_id','point_x','point_y');coord('map_warp_point',row,'tar_map_id','tar_hero_pos_x','tar_hero_pos_y')
        if row['tar_map_id'] not in maps:r.add('INVALID_MAP_TRANSITION',f"warp/{row['id']}",'Destination map missing')
    growth=collections.defaultdict(set)
    for row in tables['role_lv'].values():
        key=(row['roleId'],row['roleLv'])
        if row['roleLv'] in growth[row['roleId']]:r.add('IMPOSSIBLE_PROGRESSION_REFERENCE',f"role_lv/{row['id']}",'Repeated character level')
        growth[row['roleId']].add(row['roleLv'])
    for role,levels in growth.items():
        missing=sorted(set(range(min(levels),max(levels)+1))-levels)
        if missing:r.add('IMPOSSIBLE_PROGRESSION_REFERENCE',f'role/{role}','Missing levels inside defined interval',levels=missing)
    r.add('UNKNOWN_SKILL_ROLE_MAPPING','skill/roleId','Skill roles are not role_lv IDs; no implicit join performed','UNKNOWN')
    r.add('UNKNOWN_EVENT_OPCODES','events','Flag, type, connect_id, collision and asynchronous semantics are not verified','UNKNOWN')
    r.add('INFERRED_REFERENCE_NAMESPACES','battle/story','type-dependent battle slots and nextSceneId mappings are hypotheses, not original verification','WARNING')
    r.add('ORIGINAL_BASELINE_UNVERIFIED','all','No imported record is admitted as original merely because structure passes','UNKNOWN')
    return r.finish()

def validate_canonical(data,sources,asset_root=None):
    r=Report('canonical-baseline');schema_errors(data,'canonical-baseline',r);src=validate_provenance(sources,r)
    # A malformed shape is reported safely, not interpreted by downstream checks.
    if any(i['code']=='SCHEMA_VALIDATION' for i in r.issues):return r.finish()
    entities=data['entities'];indices={d:{x['id']:x for x in entities[d]} for d in DOMAINS};seen=set()
    for domain,rows in entities.items():
        for x in rows:
            r.checked+=1
            if x['id'] in seen:r.add('DUPLICATE_ID',x['id'],'ID reused across entities')
            seen.add(x['id'])
            for sid in x['sourceRefs']:
                if sid not in src:r.add('INVALID_PROVENANCE_REFERENCE',x['id'],'Missing source',reference=sid)
                elif data['profile']!='synthetic-test' and not src[sid].get('originalVerified'):r.add('UNVERIFIED_CANONICAL_CONTENT',x['id'],'Source has not been verified against original')
    def ref(entity,key,value,domain):
        r.checked+=1
        if value is not None and value not in indices[domain]:
            names={'enemies':'ENEMY','npcs':'NPC','dialogues':'DIALOGUE','items':'ITEM','skills':'SKILL','events':'EVENT','shops':'SHOP','maps':'MAP','assets':'ASSET','chests':'CHEST'}
            r.add('INVALID_'+names.get(domain,domain.upper())+'_REFERENCE',entity+'/'+key,'Unknown '+domain+' ID',reference=value)
    def pos(entity,mid,p):
        m=indices['maps'].get(mid)
        if m and not (0<=p['x']<m['width'] and 0<=p['y']<m['height']):r.add('INVALID_COORDINATES',entity,'Position outside canonical map',mapId=mid,position=p)
    def actions(owner,acts):
        for a in acts:
            for key,domain in {'mapId':'maps','npcId':'npcs','dialogueId':'dialogues','enemyId':'enemies','itemId':'items','skillId':'skills','eventId':'events','shopId':'shops','chestId':'chests'}.items():
                if key in a:ref(owner,key,a[key],domain)
            if a['type'] in ('teleport','changeMap'):pos(owner,a['mapId'],a['position'])
            if 'actions' in a:actions(owner,a['actions'])
    for domain,rows in entities.items():
        for x in rows:
            for key,target in {'mapId':'maps','npcId':'npcs','dialogueId':'dialogues','nextDialogueId':'dialogues','speakerNpcId':'npcs','itemId':'items','eventId':'events'}.items():
                if key in x:ref(x['id'],key,x[key],target)
            for key,target in {'assetIds':'assets','itemIds':'items','skillIds':'skills'}.items():
                for value in x.get(key,[]):ref(x['id'],key,value,target)
            if 'position' in x:pos(x['id'],x['mapId'],x['position'])
            if domain=='dialogues' and not x['text'].strip():r.add('MISSING_DIALOGUE',x['id'],'Whitespace-only text')
            if domain=='events':actions(x['id'],x['actions'])
            if domain=='maps':
                for t in x['transitions']:
                    ref(x['id'],'targetMapId',t['targetMapId'],'maps');pos(t['id'],x['id'],t['position']);pos(t['id'],t['targetMapId'],t['targetPosition'])
                    if t['targetMapId'] not in indices['maps']:r.add('INVALID_MAP_TRANSITION',t['id'],'Missing destination')
            if domain=='assets' and asset_root:
                try:path=contained(asset_root,x['path'])
                except ValueError as e:r.add('MISSING_ASSET',x['id'],str(e));continue
                if not path.is_file():r.add('MISSING_ASSET',x['id'],'Asset does not exist')
                elif sha(path)!=x['sha256']:r.add('ASSET_HASH_MISMATCH',x['id'],'Asset hash differs')
    levels=collections.defaultdict(set)
    for x in entities['progression']:
        if x['level'] in levels[x['characterId']]:r.add('IMPOSSIBLE_PROGRESSION_REFERENCE',x['id'],'Duplicate character/level')
        levels[x['characterId']].add(x['level'])
    for char,lvs in levels.items():
        if set(range(min(lvs),max(lvs)+1))-lvs:r.add('IMPOSSIBLE_PROGRESSION_REFERENCE',char,'Gap in defined progression')
    for x in entities['skills']:
        if x['characterId'] is not None and x['learnLevel'] not in levels[x['characterId']]:r.add('IMPOSSIBLE_PROGRESSION_REFERENCE',x['id'],'Skill learning level has no progression row')
    if data['status'].startswith('BLOCKED'):r.add('CANONICAL_BLOCKED',data['status'],'No verified game baseline ready for use','UNKNOWN')
    return r.finish()

def write_report(report):
    save(ROOT/'reports/data-validation.json',report)
    lines=['# Data Validation Report','',f"结果：**{report['status']}**。原版真实性不会因结构检查通过而升级。",'',
      '执行命令：`./phase1.ps1 validate`。逐条问题见 [机器报告](../reports/data-validation.json)。','',
      '|类别|数量|','|---|---:|']
    lines += [f'|{k}|{v}|' for k,v in sorted(report['issuesByCode'].items())]
    lines += ['','ERROR为确定结构/引用错误；WARNING为来源内差异或坐标约定待核；UNKNOWN为未解决语义/真实性。没有修改源数据或用默认值消除问题。',
      '','raw坐标越界按明确的零基TMX假设报告warning；canonical坐标契约确定，因此同类越界是error。尚不能证明有条件剧情可达或全流程通关。']
    (ROOT/'docs/data-validation-report.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
