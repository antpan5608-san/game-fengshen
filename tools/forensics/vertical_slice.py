"""Bounded V1 research on the pinned ROM; reference data never drives extraction.

Runtime checks use read-only captures from the recorded controller experiments.
Absent captures lower coverage; static candidates are never substituted for replay.
"""
import collections
import json
from pathlib import Path
from .common import ROOT, load, save
from .fengshen246 import (SHA256, CHARS, digest, decode_tokens, extract_text,
    extract_map, render_map, extract_npcs, extract_enemy, verify_enemy_runtime,
    extract_growth_candidates, glyph_pixels)

FONT0_LOW='呀擔保裏兩著照夫往走陳那店東多交聽息上怪出必倒們練加驗把刀給它來器嗎藥体及完大身態康心己用哪少會是到打備體的恢力可你'
FONT0_HIGH='娘要重百留我顧放北就塘商買西和談消路都物沒須牠磨增經機小送將起兵了服草員法最值狀健兒自有吧吒爺人村些裝全這以復好一'
FONT0={**dict(enumerate(FONT0_LOW)),**{128+i:c for i,c in enumerate(FONT0_HIGH)}}
DELTA={1:(7,6),2:(7,8),3:(6,7),4:(8,7)}

def opening_font(message):
    if not 0<=message<=11:raise ValueError('Only opening map messages 0..11 established')
    return 0 if message<10 else 78

def opening_dialogues(reader):
    result=[]
    for index in range(12):
        d=extract_text(reader,124,index);bank=opening_font(index)
        d.update(decode_tokens(bytes.fromhex(d['rawHex']),{**(FONT0 if bank==0 else CHARS),0x44:' '}))
        d.update(fontContext={'chr2kBanks':[bank,bank+1],'mapId':114},confidence='HIGH',
                 semanticScope='Text/font context only; NPC event sequence and input waits separate')
        result.append(d)
    return result

def npc_fields(record):
    b=bytes.fromhex(record['rawHex']);x=int.from_bytes(b[4:6],'little');y=int.from_bytes(b[6:8],'little')
    return {**record,'mapId':114,'runtimeSlot':record['index']+1,'spriteId':b[0]&127,
        'rawPixel':[x,y],'mapPixelAnchor':[x-112,y-112],
        'metatile':[(x-120)//16,(y-120)//16],'direction':['UP','DOWN','LEFT','RIGHT'][b[10]&3],
        'directionFlagsRaw':b[10],'dialogueFirst':b[1],'dialogueRepeat':b[2],
        'dialogueGroup':124,'movementProgram':b[3],'movementTimer':b[11],
        'animationProgramPointer':int.from_bytes(b[8:10],'little'),'animationModule':0,
        'eventSelector':b[12],'eventFlagMask':b[13],
        'eventSemantics':'PARTIAL: selector/mask and first/repeat branch known; effect opcode not fully decoded',
        'evidenceScope':'Source fields/coordinate transform; moving paths and all event effects not verified'}

def collision_kind(tileset,code):
    """Traced normal on-foot branches only; no inference from tile appearance."""
    if tileset==4 and code in (0,1,2):
        return {0:'WALKABLE',1:'BLOCKED',2:'DOOR_OCCLUSION'}[code]
    if tileset==1 and 0<=code<=26:return 'BLOCKED' if code in (1,3,4,5,7) else 'WALKABLE'
    return 'UNKNOWN'

def collision_check(reader,maps,text):
    checks=[];differences=[]
    for line in text.splitlines():
        a=line.split('\t');frame,mid,d,cx,cy,current,target,blocked,mt,px,py=map(int,a[:11])
        if mid not in maps or d not in DELTA:continue
        m=maps[mid];dx,dy=DELTA[d];x,y=cx+dx,cy+dy
        if not (0<=x<m['width'] and 0<=y<m['height']):continue
        tile=m['grid'][y][x];c=m['collisionCandidate'];code=reader.read(c['module'],c['cpuAddress']+tile)[0]
        kind=collision_kind(m['tilesetId'],code)
        check={'frame':frame,'mapId':mid,'tile':[x,y],'metatile':tile,'class':code,'kind':kind,
               'runtimeBlocked':blocked,'runtimeClass':target,'runtimeMetatile':mt}
        checks.append(check)
        if tile!=mt or code!=target or kind=='UNKNOWN' or bool(blocked)!=(kind=='BLOCKED'):differences.append(check)
    return {'passed':bool(checks) and not differences,'checks':len(checks),'differences':differences,
        'observedClasses':dict(collections.Counter(f'{c["mapId"]}:{c["class"]}:{c["kind"]}' for c in checks)),
        'scope':'Normal foot movement with boat/flying flags zero; excludes NPC contact and unvisited event handlers'}

def verify_world_viewport(m,ppu):
    if len(ppu)!=16384:raise ValueError('Expected full PPU capture')
    differences=[]
    # Fixed frame960 patch, not a best-fit comparison. Nametable phase was reset
    # on entering the world; this is NOT a universal nametable parity formula.
    for y in range(145,160):
        for x in range(197,212):
            for q,expected in enumerate(m['metatiles'][m['grid'][y][x]]):
                addr=0x2000+((y//15+1)%2)*0x800+(y*2%30+q//2)*32+x*2%32+q%2
                if ppu[addr]!=expected:differences.append({'mapX':x,'mapY':y,'ppuAddress':addr,'expected':expected,'actual':ppu[addr]})
    return {'passed':not differences,'checkedTileIndices':900,'differences':differences,'ppuSha256':digest(ppu),
        'coverage':'Fixed frame960: x197..211 y145..159; crosses PRG module4/module15 at y150. Full map gameplay remains partial.'}

def character_state(ram,sram):
    if len(ram)!=2048 or len(sram)!=2048:raise ValueError('Expected 2KiB CPU and mapper RAM snapshots')
    def u(b,p,n=2):return int.from_bytes(b[p:p+n],'little')
    return {'level':ram[0x504]+1,'xp':u(ram,0x508,3),'hp':u(ram,0x514),'maxHp':u(ram,0x51c),
        'mp':u(ram,0x524),'maxMp':u(ram,0x52c),'strength':u(sram,0x164),
        'stamina':u(ram,0x538),'agility':ram[0x534],'spirit':ram[0x540],
        'remainingExp':u(sram,0x130,3),'money':u(ram,0x501,3)}

def verify_growth_pair(before,after,row,next_threshold):
    fields={'maxHp':'hpDeltaCandidate','maxMp':'mpDeltaCandidate','strength':'strengthDeltaCandidate',
        'stamina':'staminaDeltaCandidate','agility':'agilityDeltaCandidate','spirit':'spiritDeltaCandidate'}
    differences=[k for k,delta in fields.items() if after[k]-before[k]!=row[delta]]
    if after['hp']-before['hp']!=row['hpDeltaCandidate']:differences.append('currentHpDelta')
    if after['xp']!=before['xp']:differences.append('cumulativeXpPreserved')
    if after['xp']<row['cumulativeExpCandidate']:differences.append('threshold')
    if after['remainingExp']!=next_threshold-after['xp']:differences.append('remainingExp')
    if after['level']!=row['index']+1 or before['level']!=after['level']:differences.append('levelIndex')
    return {'passed':not differences,'differences':differences,'beforeGrowth':before,'afterGrowth':after,
        'threshold':row['cumulativeExpCandidate'],'note':'growth-entry occurs AFTER level increment, before additive stats'}

def transition_check(reader,trajectory):
    ptr=reader.word(8,0xdc69+2*114);b=reader.read(8,ptr,5)
    rows=[list(map(int,line.split('\t'))) for line in trajectory.splitlines()]
    before=next((r for r in rows if r[0]==840),None)
    changed=next((r for r in rows if r[0]==843),None)
    spawn=next((r for r in rows if r[1]==b[2] and r[4:6]==[b[3]-7,b[4]-7]
                and r[2:4]==[b[3]*16+120,b[4]*16+120]),None)
    passed=bool(before and before[1]==114 and changed and changed[1]==b[2] and spawn)
    return {'fromMap':114,'toMap':b[2],'returnEntryCandidate':list(b[:2]),'destinationPlayerMetatile':list(b[3:]),
        'destinationCameraMetatile':[b[3]-7,b[4]-7],'rawHex':b.hex(),'range':reader.span(8,ptr,5,'Opening entry/exit row'),
        'runtimePassed':passed,'spawnObservation':spawn,'confidence':'VERIFIED' if passed else 'HIGH',
        'scope':'Outbound boundary transition and arrival camera verified; return entry path not replayed',
        'trigger':'Map boundary routine, not collision class2. Door/roof occlusion is separate.'}

def enemy_classification(enemies,reference):
    fields=['hp','attack','defense','experienceReward','moneyReward'];ref_fields=['hp','atk','def','exp','money']
    index=collections.defaultdict(list)
    for e in enemies:index[tuple(e[k] for k in fields)].append(e['romEnemyId'])
    refs=[]
    for r in reference['records']:
        if r['table']!='monster':continue
        values=tuple(r['data'][k] for k in ref_fields);ids=index[values]
        refs.append({'referenceId':r['id'],'nameHint':r['data'].get('name'),
            'candidateRomIds':ids,'status':'NUMERIC_SIGNATURE_CANDIDATE' if ids else 'UNPAIRED',
            'confidence':'HIGH' if len(ids)==1 else 'UNKNOWN','originalVerified':False,
            'scope':'Five numeric fields only; shared stats do not verify identity, name, skills or flags'})
    duplicates=[];by_signature=collections.defaultdict(list)
    for r in reference['records']:
        if r['table']=='monster':by_signature[tuple(r['data'][k] for k in ref_fields)].append(r['id'])
    for values,ids in by_signature.items():
        if len(ids)>1:duplicates.append({'referenceIds':ids,'fiveFieldSignature':list(values)})
    return {'romCount':len(enemies),'referenceCount':len(refs),'romIds':list(range(len(enemies))),
        'records':refs,'duplicateReferenceSignatures':duplicates,
        'counts':dict(collections.Counter(x['status'] for x in refs)),
        'classification':{'0':'Dummy-like all-one record; unused/sentinel runtime role UNKNOWN',
            '1..136':'Ordinary-enemy candidates, only IDs1/2/3 runtime checked',
            '137..176':'Boss-like candidates by stats and reference hints; identities NOT VERIFIED'},
        'conclusion':'Not a simple removal of three enemies. Reference reorders bosses, duplicates some numeric records and changes some stats; preserve both complete enumerations.'}

def augment(reader,rom_path,out,result,records,offsets):
    captures=[]
    def relative(p):return Path(p).resolve().relative_to(ROOT).as_posix()
    def evidence(key,span):
        sid='source.rom.v1.'+key.lower()
        records.append({'id':sid,'source':'ORIGINAL_ROM','confidence':'VERIFIED','originalVerified':False,'licenseStatus':'UNKNOWN',
            'locator':{'path':relative(rom_path),'romSha256':SHA256,**{k:span[k] for k in ('offset','length','sha256','meaning')}},'evidenceRefs':[],
            'note':'Verified bytes of pinned ROM; only stated semantic scope is supported.'})
        offsets.append({'romSha256':SHA256,**{k:span[k] for k in ('offset','length','meaning')},'confidence':'VERIFIED','evidence':[sid]})
        return sid
    def capture(path):
        path=Path(path)
        if not path.exists():return None
        item={'path':relative(path),'sha256':digest(path.read_bytes()),'bytes':path.stat().st_size};captures.append(item)
        sid='source.gameplay.v1.'+str(len(captures))
        records.append({'id':sid,'source':'GAMEPLAY_VERIFIED','confidence':'VERIFIED','originalVerified':False,'licenseStatus':'UNKNOWN',
            'locator':{'path':item['path'],'sha256':item['sha256'],'romSha256':SHA256,
                'meaning':'Exact labeled binary/log capture or next-frame queued PNG from V1 normal-input replay; branch/timing in gameplay-v1.json'},'evidenceRefs':[],
            'note':'Read-only capture from recorded normal-input experiment. See gameplay-v1.json for branch restores and timing.'})
        return sid
    def claim(key,data,refs,verified,meaning):
        path=out/(key+'.json');save(path,data);sid='source.original.v1.'+key
        records.append({'id':sid,'source':'MANUAL','confidence':'VERIFIED' if verified else 'HIGH','originalVerified':bool(verified),
            'licenseStatus':'UNKNOWN','locator':{'path':relative(path),'sha256':digest(path.read_bytes()),'meaning':meaning},
            'evidenceRefs':[r for r in refs if r],'note':'Scoped research claim; does not promote a full reference record or enable runtime consumers.'})
        return sid
    routines=[('font-switch',2,0xb329,0x155,'Initial map chooses font0 for messages0..9, font78 for10..11; group map+10'),
        ('npc-talk',10,0xa155,0x36,'NPC first/repeat dialogue selection by event selector and mask'),
        ('npc-coordinate',0,0x9fc3,0x2e,'Subtract120 then divide16 for NPC collision metatile'),
        ('npc-camera',0,0xa6d6,0x31,'Camera pixel origin=(camera metatile+7)*16'),
        ('npc-render',0,0x9cb3,0x27,'Sprite anchor=raw coordinates-camera pixel origin'),
        ('npc-movement',0,0x9d64,0xde,'Movement program and timer interpreter; full paths not decoded'),
        ('npc-sprite',0,0x9f88,0x3b,'Object low7 bits select sprite/direction animation pointers'),
        ('collision-target',0,0xca04,0x75,'Read current and directional target metatile collision classes'),
        ('collision-town',0,0xd132,0x65,'Town foot/boat movement branch and collision dispatch'),
        ('collision-occlusion',0,0xcd59,0x67,'Town class2 calls CC87 to set sprite occlusion flag9b'),
        ('sprite-occlusion',0,0x874c,0x35,'Negative9b sets sprite background-priority bit20'),
        ('collision-world',0,0xcf44,0x134,'World foot/flying/boat branches, normal foot D031 table'),
        ('world-extension',0,0xb50b,0x96,'World rows150+ use module15 EC59/EC79 pointer arrays'),
        ('boundary-exit',0,0xc66f,0x43,'Map boundary exit dispatch; not door collision class2')]
    routine_refs={}
    from py65.devices.mpu6502 import MPU
    from py65.disassembler import Disassembler
    listing=[]
    for key,module,addr,length,meaning in routines:
        routine_refs[key]=evidence(key,reader.span(module,addr,length,meaning))
        cpu=MPU();cpu.memory[0x8000:]=reader.read(module,0x8000,32768);d=Disassembler(cpu);pc=addr
        listing.append(f'; {key}; module{module}; {meaning}')
        while pc<addr+length:
            size,text=d.instruction_at(pc);listing.append(f'{16+module*32768+pc-0x8000:06X} {pc:04X} {text}');pc+=size
    (out/'vertical-slice-routines.asm').write_text('\n'.join(listing)+'\n',encoding='utf8')
    ds=opening_dialogues(reader);fontbase=0x80010
    fontref=evidence('font0',{'offset':fontbase,'length':4096,'sha256':digest(reader.data[fontbase:fontbase+4096]),'meaning':'Opening font0/1, manually transcribed glyphs; contextual code table'})
    charset=[]
    for code,char in sorted(FONT0.items()):
        off=fontbase+(10+4*(code&63))*16
        charset.append({'code':code,'character':char,'offset':off,'length':64,'plane':code>>7,'encoding':'contextual glyph code',
            'sha256':digest(reader.data[off:off+64]),'confidence':'HIGH','evidence':[fontref,routine_refs['font-switch']]})
    save(out/'charset-opening.json',{'romSha256':SHA256,'fontBanks':[0,1],'records':charset,'globalCharsetComplete':False,
        'transcription':'Manual isolated glyph review; preserve 体 vs 體, 百 vs 有, 牠. Runtime captures independently show mother/father contexts.'})
    variablepath=ROOT/'private-derived/slice-probe/variables.tsv';varref=capture(variablepath)
    variables=variablepath.read_text() if variablepath.exists() else ''
    verified_dialogues=0;dialogrefs=[]
    for d in ds:
        idx=d['messageIndex'];refs=[evidence(f'dialogue.{idx}',d['range']),routine_refs['font-switch'],fontref if idx<10 else 'source.rom.font78']
        refs.extend(evidence(f'dialogue.{idx}.pointer.{n}',s) for n,s in enumerate(d['pointerEvidence']))
        # Fixed captures correspond to the controller replay, not inferred from similarity.
        frames={1:[1500,1700],10:[900,1040]}.get(idx,[])
        gp=[capture(ROOT/f'private-derived/slice-probe/frame-{f:04d}.png') for f in frames]
        bank=opening_font(idx);needle=f'0029\t{bank:02X}\t'
        checked=bool(frames and all(gp) and needle in variables and not d['unknownCodes'])
        d['runtimeObserved']=checked;d['confidence']='VERIFIED' if checked else 'HIGH';verified_dialogues+=checked
        d['evidence']=refs+[r for r in [varref,*gp] if r]
        dialogrefs.append(claim(f'dialogue-opening-{idx}',d,d['evidence'],checked,'Decoded text/font context only; all event effects not promoted'))
    claim('dialogues-opening',{'records':ds,'allOpeningSequenceVerified':False},dialogrefs,False,'Twelve independently decoded opening-map streams, not all opening storyline events')
    save(out/'dialogues.json',{'romSha256':SHA256,'records':ds,'complete':False,'scope':'Opening map text contexts only; entire storyline not verified'})
    pending=load(out/'dialogue-pointer-candidates.json')
    pending['records']=[d for d in pending['records'] if d['messageIndex']>=12]
    save(out/'dialogue-pointer-candidates.json',pending)
    npcs=extract_npcs(reader);npcs['records']=[npc_fields(r) for r in npcs['records']]
    npcpath=ROOT/'private-derived/slice-probe/frame-0450-ram.bin';npcref=capture(npcpath)
    ram=npcpath.read_bytes() if npcpath.exists() else None
    for n in npcs['records']:
        start=0x402+22*n['runtimeSlot'];b=bytes.fromhex(n['rawHex'])
        n['runtimeCopyMatches']=bool(ram and ram[start:start+14]==b)
        n['confidence']='VERIFIED' if n['runtimeCopyMatches'] else 'HIGH'
    npcs['coordinateFormula']={'metatile':'floor((rawPixel-120)/16)','mapPixelAnchor':'rawPixel-112',
        'viewportAnchor':'rawPixel-(cameraTile+7)*16','metaspriteTopLeft':'viewportAnchor-8 (observed 2x2 sprite)'}
    npcclaim=claim('npc-opening',npcs,[evidence('npc-list',npcs['range']),npcref,*[routine_refs[k] for k in ('npc-talk','npc-coordinate','npc-camera','npc-render','npc-movement','npc-sprite')]],bool(ram),'NPC source bytes, coordinates, sprite/direction and dialogue selectors; event/movement semantics PARTIAL')
    maps={114:extract_map(reader,114),16:extract_map(reader,16)};world=maps[16]
    worldrefs=[]
    for key in ('header','headerPointer','chunkTablePointer','moduleEvidence','chrEvidence','metatileRange','attributeRange'):
        worldrefs.append(evidence('world.'+key,world[key]))
    for i,c in enumerate(world['chunks']):
        worldrefs.extend([evidence(f'world.chunk.{i}',c),evidence(f'world.pointer.{i}',c['pointer'])])
    wp=ROOT/'private-derived/world-probe/frame-0960-ppu.bin';wref=capture(wp)
    world['viewportVerification']=verify_world_viewport(world,wp.read_bytes()) if wp.exists() else {'passed':False,'reason':'Capture missing'}
    world['gameplayVerified']=world['viewportVerification']['passed'];world['gameplayScope']='Fixed viewport only; world dynamic actors/events and return path incomplete'
    save(out/'maps/16.json',world);render_map(reader,world,out/'maps/16.png')
    worldclaim=claim('world-map-evidence',{'romMapId':16,'gridSha256':world['gridSha256'],'viewportVerification':world['viewportVerification']},[*worldrefs,wref,routine_refs['world-extension']],world['gameplayVerified'],'World geometry/data and observed viewport only; final boundary row180 is structurally extracted, not gameplay checked')
    ml=load(out/'maps.json');ml['records'].append(world);ml['coverage']='11 structural maps; 2 fixed viewports; no full-world gameplay claim';save(out/'maps.json',ml)
    from .fengshen246 import make_viewer as make_map_index
    make_map_index(out,ml['records'])
    collisions=[]
    for probe in ('slice-probe','world-probe'):
        cp=ROOT/f'private-derived/{probe}/collision.tsv';gp=capture(cp)
        checked=collision_check(reader,maps,cp.read_text()) if cp.exists() else {'passed':False,'checks':0}
        checked['probe']=probe;checked['evidence']=[gp] if gp else [];collisions.append(checked)
    colrefs=[routine_refs[k] for k in ('collision-target','collision-town','collision-world','collision-occlusion','sprite-occlusion')]
    for mid,m in maps.items():
        c=m['collisionCandidate'];colrefs.append(evidence(f'collision.map{mid}',reader.span(c['module'],c['cpuAddress'],256,'Metatile collision class lookup')))
        table=reader.read(c['module'],c['cpuAddress'],256)
        m['collisionClasses']=list(table);m['collisionGrid']=[[collision_kind(m['tilesetId'],table[t]) for t in row] for row in m['grid']]
    colclaim=claim('collision-opening',{'normalFootOnly':True,'checks':collisions,
        'door':'Class2 is passable and sets behind-background sprite priority; NOT transition',
        'eventWaterOther':'Special event and water names remain UNKNOWN outside traced foot/boat/flying branches'},colrefs+[x for c in collisions for x in c['evidence']],all(c['passed'] for c in collisions),'Observed tile passability and traced on-foot branches only')
    tp=ROOT/'private-derived/world-probe/trajectory.tsv';tref=capture(tp)
    transition=transition_check(reader,tp.read_text()) if tp.exists() else {'runtimePassed':False,'confidence':'HIGH'}
    transclaim=claim('transitions-v1',transition,[tref,'source.rom.map114.exit','source.rom.routine.map.transition',routine_refs['boundary-exit']],transition['runtimePassed'],'Outbound map114 ->16 and camera arrival; reverse travel not verified')
    save(out/'map-overlays.json',{'maps':{str(k):{'collisionClasses':m['collisionClasses'],'collisionGrid':m['collisionGrid']} for k,m in maps.items()},
        'evidence':[colclaim,transclaim,npcclaim],'normalFootOnly':True,'completeGameplay':False})
    growth=extract_growth_candidates(reader);upgrades=[]
    for frame,branch,index in [(13121,1,1),(39282,5,2)]:
        states=[];refs=[]
        for label in ('growth-entry','change'):
            base=ROOT/f'private-derived/level-probe/{frame:06d}-b{branch}-{label}'
            rp=Path(str(base)+'-ram.bin');sp=Path(str(base)+'-sram.bin');refs.extend([capture(rp),capture(sp)])
            if rp.exists() and sp.exists():states.append(character_state(rp.read_bytes(),sp.read_bytes()))
        row=growth['groups'][0]['rows'][index]
        check=verify_growth_pair(*states,row,growth['groups'][0]['rows'][index+1]['cumulativeExpCandidate']) if len(states)==2 else {'passed':False,'reason':'Capture missing'}
        check.update(frame=frame,branch=branch,level=index+1)
        for key in ('growthRange','thresholdRange'):refs.append(evidence(f'growth.{index}.{key}',row[key]))
        check['evidence']=[r for r in refs if r];upgrades.append(check)
    eventpath=ROOT/'private-derived/level-probe/events.tsv';eventref=capture(eventpath);writeref=capture(ROOT/'private-derived/level-probe/writes.tsv')
    events=[line.split('\t') for line in eventpath.read_text().splitlines()] if eventpath.exists() else []
    crossed7=any(int(a[4])==0 and int(a[5])==8 for a in events)
    for u in upgrades:
        seq=[a for a in events if int(a[0])==u['frame'] and int(a[1])==u['branch']]
        u['levelIncrementObserved']=bool(seq and seq[0][2]=='level-check-entry' and int(seq[0][4])==u['level']-2
            and any(a[2]=='growth-entry' and int(a[4])==u['level']-1 for a in seq))
        u['passed']=u['passed'] and u['levelIncrementObserved']
    progress={'confidence':'VERIFIED' if all(u['passed'] for u in upgrades) and crossed7 else 'HIGH',
        'expSemantics':'uint24 little-endian cumulative EXP at0508; zero-based level0504',
        'initialDisplay':7,'initialCumulativeExp':0,'firstThreshold':12,'secondThreshold':27,
        'resolution':'New game literal7 at module0 B814 writes remaining-EXP cache6930. Bank45 D208 comparison uses next cumulative threshold; D268..D295 recomputes cache=nextThreshold-currentEXP.',
        'crossed7WithoutUpgrade':crossed7,'upgrades':upgrades,'rawRows':320,'actorTables':4,'rowsPerActor':80,
        'attributeSemantics':'Separate per-actor seven-byte additive growth rows; initial constants are independent. Index0 is not reapplied to initial spirit.',
        'unverified':['Actors1..3 runtime','special/high levels and caps','agility overflow/carry effect on spirit','all multiplayer reward branches'],
        'replay':'Cold boot, normal controller inputs; five verified in-memory checkpoint restores after death. No cheats or writes to emulated RAM. Branch IDs are part of the evidence.',
        'captureCaveat':'Repeated level-check-entry labels in one frame overwrite snapshot files; those files are excluded. Unique growth-entry/change snapshots plus ordered event/write logs are used.'}
    progclaim=claim('progression-verified',progress,['source.rom.routine.character.init','source.rom.routine.growth.bank-switch',eventref,writeref,
        *[r for u in upgrades for r in u['evidence']],*[evidence(f'growth.routine.{i}',s) for i,s in enumerate(growth['routineRanges'])]],progress['confidence']=='VERIFIED','Initial cache and first two Nezha upgrades only; 320 rows not all gameplay verified')
    gc=load(out/'progression-reference-candidates.json');gc['thresholdDiscrepancy']={'initialDisplay':7,'firstNextLevelTableValue':12,'status':'RESOLVED','evidence':[progclaim],'resolution':progress['resolution']}
    for row in gc['records']:row['note']='Static per-level aggregate candidate; first two Nezha deltas observed separately. High-level overflow and other actors remain unverified.'
    save(out/'progression-reference-candidates.json',gc)
    enemy1=extract_enemy(reader,1);ep=ROOT/'private-derived/level-probe/007500-b0-periodic-sram.bin';eref=capture(ep)
    enemy1['runtimeVerification']=verify_enemy_runtime(enemy1,ep.read_bytes(),5) if ep.exists() else {'passed':False}
    enemy1['confidence']='VERIFIED' if enemy1['runtimeVerification']['passed'] else 'HIGH'
    enemyclaim=claim('enemy-opening-1',enemy1,[evidence('enemy1',enemy1['range']),evidence('enemy1.pointer',enemy1['pointer']),eref,'source.rom.routine.enemy.loader'],enemy1['runtimeVerification']['passed'],'Enemy1 five numeric stats/rewards only; name and remaining bytes unverified')
    all_enemies=[extract_enemy(reader,i) for i in range(177)];rawref=load(ROOT/'game-data/raw/reference-project/dataset.json')
    classification=enemy_classification(all_enemies,rawref);save(out/'enemy-classification.json',classification)
    compare=load(out/'comparison.json');compare['records'].append({'domain':'enemies','id':enemy1['id'],'referenceId':'reference.monster.1','complete':False,
        'comparable':dict(zip(['hp','atk','def','exp','money'],[enemy1[k] for k in ('hp','attack','defense','experienceReward','moneyReward')])), 'sourceRefs':[enemyclaim]})
    # Explicit opening dialogue associations are research hints, never fuzzy VERIFIED links.
    associations={0:10,1:9,2:2,3:6,4:5,5:4,6:11,7:12,8:3,10:7,11:8}
    dialogue_comparison=[]
    refstories={r['data']['id']:r for r in rawref['records'] if r['table']=='story'}
    for idx,rid in associations.items():
        d=ds[idx];ref=refstories[rid]
        dialogue_comparison.append({'romId':d['id'],'referenceId':ref['id'],'associationConfidence':'HIGH',
            'romText':d['text'],'referenceText':ref['data']['speak_words'],
            'status':'MODIFIED' if d['runtimeObserved'] and d['text']!=ref['data']['speak_words'] else 'UNKNOWN',
            'candidateTextEqual':d['text']==ref['data']['speak_words'],'evidence':[dialogrefs[idx],*ref['sourceRefs']],
            'note':'Verbatim comparison; quotation/traditional characters are retained. HIGH decoded candidates do not become verified differences.'})
        if idx!=10:compare['records'].append({'domain':'dialogues','id':d['id'],'referenceId':ref['id'],
            'comparable':{'speak_words':d['text']},'complete':False,'sourceRefs':[dialogrefs[idx]]})
    save(out/'dialogue-opening-comparison.json',{'records':dialogue_comparison,'referenceCount':639,'completeDomain':False})
    save(out/'comparison.json',compare)
    battle=load(out/'battle.json');battle['unverified']=[s for s in battle['unverified'] if s!='Level-up table'];battle['levelCheckEvidence']=progclaim
    battle['rng']='Observed ordinary multiplier1; critical selection/RNG distribution remain UNKNOWN. Restored branches are not independent random trials.';save(out/'battle.json',battle)
    ledger=[{'id':'DIFF-DIALOGUE-007','category':'DIALOGUE','romValue':'都是你做的好事','referenceValue':'都是你惹的好事','evidence':[dialogrefs[10],'reference.story.7'],'decision':'Preserve ROM 做; never normalize to reference','status':'MODIFIED'},
        {'id':'DIFF-MAP-001','category':'MAP_STRUCTURE','romValue':[32,30],'referenceValue':[35,35],'evidence':['source.original.map114.geometry','reference.map.1'],'decision':'Keep separate coordinate systems; association HIGH','status':'MODIFIED'},
        {'id':'DIFF-DIALOGUE-009','category':'VERBATIM_TEXT','romValue':ds[1]['text'],'referenceValue':refstories[9]['data']['speak_words'],
            'evidence':[dialogrefs[1],*refstories[9]['sourceRefs']],'decision':'Preserve source punctuation and script; this is a verbatim difference, not evidence of changed plot','status':'MODIFIED'},
        {'id':'HINT-DIALOGUE-011','category':'WORDING_CANDIDATE','romValue':'這把小刀送給你','referenceValue':'这把小刀送个你',
            'evidence':[dialogrefs[6],*refstories[11]['sourceRefs']],'decision':'HIGH decoded candidate; wait for this NPC runtime sequence before verified semantic difference','status':'UNKNOWN'},
        {'id':'DIFF-EXP-INIT','category':'INTERNAL_ROM_SEMANTICS','romValue':{'initialCache':7,'cumulativeThreshold':12},'referenceValue':'Not used to resolve','evidence':[progclaim],'decision':'Separate cached remaining display from cumulative threshold; preserve both','status':'RESOLVED'},
        {'id':'DIFF-ENEMY-COUNT','category':'ENUMERATION','romValue':177,'referenceValue':174,'evidence':['source.rom.enemy.0.data' if any(r['id']=='source.rom.enemy.0.data' for r in records) else 'source.rom.routine.enemy.loader'],'decision':'Preserve dummy and all records; see numeric-signature classifications, not a one-to-one ID mapping','status':'UNPAIRED'},
        {'id':'CORRECTION-NPC-POINTER','category':'RESEARCH_CORRECTION','romValue':'bytes8..9 animation program in module0','referenceValue':'V0 eventRoutineCandidate interpretation','evidence':[routine_refs['npc-render'],routine_refs['npc-movement']],'decision':'Rename field; never execute animation bytecode as 6502 event code','status':'RESOLVED'},
        {'id':'DIFF-GROWTH-L50','category':'PROGRESSION_CANDIDATE','romValue':4114,'referenceValue':4144,'evidence':['source.rom.growth.actor.0.growthrange'],'decision':'Keep UNKNOWN until high-level routine/carry checked; not verified MODIFIED','status':'UNKNOWN'}]
    save(ROOT/'game-data/provenance/difference-ledger.json',{'romSha256':SHA256,'records':ledger})
    save(ROOT/'game-data/provenance/gameplay-v1.json',{'romSha256':SHA256,'settings':{'cheats':False,'emulatedMemoryWrites':False},'snapshots':captures,
        'scripts':[{ 'path':relative(ROOT/f'tools/rom-extractor/probe-{s}.lua'),'sha256':digest((ROOT/f'tools/rom-extractor/probe-{s}.lua').read_bytes())} for s in ('slice','world','level')],
        'saveStates':progress['replay'],'screenshotTiming':'PNG queued for following frame; binary observations at labeled frame'})
    candidate={'schemaVersion':1,'status':'RESEARCH_ONLY','runtimeConsumable':False,'canonicalPromotion':'BLOCKED_UNVERIFIED',
        'romSha256':SHA256,'records':[{'domain':'progression','sourceRefs':[progclaim],'data':progress}] if progress['confidence']=='VERIFIED' else []}
    save(ROOT/'game-data/canonical-candidate/vertical-slice.json',candidate)
    summary={'version':1,'initialMap':114,'secondMap':16,'mapsStructurallyExtracted':11,
        'mapsWithVerifiedViewport':int(result['mapsWithObservedViewport'])+int(world['gameplayVerified']),
        'mapsFullyGameplayVerified':0,'npcSourceRecords':len(npcs['records']),'npcExactRuntimeCopies':sum(n['runtimeCopyMatches'] for n in npcs['records']),
        'dialoguesDecoded':len(ds),'dialoguesRuntimeVerified':verified_dialogues,'additionalFontGlyphsHigh':len(charset),
        'enemiesExtracted':177,'enemiesFiveFieldsVerified':result['enemyRecordsWithRuntimeCheck']+int(enemy1['runtimeVerification']['passed']),
        'progressionExtracted':320,'progressionUpgradeRowsVerified':sum(u['passed'] for u in upgrades),'initialCharacterVerifiedSeparately':1,
        'collisionChecks':sum(c['checks'] for c in collisions),'collisionPassed':all(c['passed'] for c in collisions),
        'mapTransitionVerified':transition['runtimePassed'],'firstLevelUpVerified':upgrades[0]['passed'],'secondLevelUpVerified':upgrades[1]['passed'],
        'scale50':{'status':'NOT_RUN','reason':'Second-map dynamic actors/events and full initial dialogue sequence not yet verified; user prerequisite not met'},
        'onePlus13TRequirementsRecorded':(ROOT/'docs/android-ui-design.md').exists(),'readyForPhase2':False,
        'minimalBlockers':['Opening dialogue/event sequence beyond two observed NPC conversations: waits, gifts, flags, repeat paths',
            'NPC movement programs and event effects; world dynamic actor placement/identity',
            'Second-map event/entrance semantics and reverse transition; unvisited collision conditions'],
        'candidate':relative(ROOT/'game-data/canonical-candidate/vertical-slice.json')}
    summary['confidenceCounts']={'VERIFIED':54+verified_dialogues+summary['mapsWithVerifiedViewport']+summary['npcExactRuntimeCopies']+summary['enemiesFiveFieldsVerified']+summary['progressionUpgradeRowsVerified']+1,
        'HIGH':len(charset)+(12-verified_dialogues)+(11-summary['mapsWithVerifiedViewport'])+(10-summary['npcExactRuntimeCopies'])+(177-summary['enemiesFiveFieldsVerified'])+318,
        'MEDIUM':0,'LOW':0,'UNKNOWN':14}
    summary['confidenceCountUnit']='54 font78 glyphs +115 font0 glyphs +26 streams (12 decoded/14 unresolved) +11 maps(viewport scope) +10 NPC source records +177 enemy stat records +320 progression rows +1 initial character; scoped confidence, never full reference equivalence'
    claim('vertical-slice',summary,[npcclaim,worldclaim,colclaim,transclaim,progclaim,enemyclaim,*dialogrefs],False,'Partial V1 vertical slice with explicit completion blockers')
    make_viewer(out,maps,npcs,transition)
    result.update(verticalSlice=summary,battle=battle,mapsExtracted=11,mapsWithObservedViewport=summary['mapsWithVerifiedViewport'],
        enemyRecordsWithRuntimeCheck=summary['enemiesFiveFieldsVerified'],dialoguesExtractedVerified=verified_dialogues,
        dialoguePointersUndecoded=len(pending['records']))
    # Refresh bindings only for files intentionally regenerated in this invocation.
    for record in records:
        if record['source']=='MANUAL' and record.get('locator',{}).get('path'):
            p=ROOT/record['locator']['path']
            if p.exists():record['locator']['sha256']=digest(p.read_bytes())
    result['confidenceCounts']=dict(collections.Counter(r['confidence'] for r in records))
    save(ROOT/'game-data/provenance/original.json',{'schemaVersion':1,'records':records})
    save(ROOT/'game-data/provenance/rom-offsets.json',{'schemaVersion':1,'status':'RESEARCH_IN_PROGRESS','records':offsets})

def make_viewer(out,maps,npcs,transition):
    data={'maps':{str(k):v for k,v in maps.items()},'npcs':npcs['records'],'transition':transition}
    document='''<!doctype html><html lang="zh"><meta charset="utf-8"><title>Original Vertical Slice V1</title>
<style>body{background:#171c24;color:#edf2f7;font:16px system-ui;margin:24px}canvas{image-rendering:pixelated;max-width:100%;cursor:crosshair}label,select{margin-right:20px}pre{white-space:pre-wrap}#wrap{overflow:auto;max-height:75vh}select{font:inherit;padding:5px}</style>
<h1>Original Vertical Slice V1 · 证据 Viewer</h1><p>ROM 灰度图块；不是游戏 UI。仅 on-foot 碰撞分支。NPC 数字为源记录索引，坐标为初始配置。</p>
<select id="map"><option value="114">114 · 开局 · 32×30</option><option value="16">16 · 野外 · 256×181</option></select>
<label><input id="collision" type="checkbox" checked>碰撞</label><label><input id="npc" type="checkbox" checked>NPC / EVENT</label>
<p>绿色 WALKABLE　红色 BLOCKED　黄色 DOOR（遮挡）　紫色 TRANSITION　蓝色 EVENT（部分）　灰色 UNKNOWN</p>
<div id="wrap"><canvas id="view"></canvas></div><pre id="detail">点击地图查看原始 metatile、碰撞类别及 NPC 字段。第二张地图动态 NPC 尚未验证，不能解释为空。</pre>
<script>const D=DATA;const c=document.getElementById('view'),ctx=c.getContext('2d');let active='114',img=new Image();
function draw(){let m=D.maps[active];c.width=m.width*16;c.height=m.height*16;ctx.drawImage(img,0,0);if(document.getElementById('collision').checked){for(let y=0;y<m.height;y++)for(let x=0;x<m.width;x++){let k=m.collisionGrid[y][x];ctx.fillStyle={WALKABLE:'#1cc58044',BLOCKED:'#fa415a88',DOOR_OCCLUSION:'#ffd447aa',UNKNOWN:'#88888888'}[k];ctx.fillRect(x*16,y*16,16,16)}}
if(active==='114'){ctx.strokeStyle='#db6eff';ctx.lineWidth=3;ctx.strokeRect(8*16,29*16,16,16);if(document.getElementById('npc').checked)for(let n of D.npcs){let[x,y]=n.metatile;ctx.fillStyle='#52c5ff';ctx.fillRect(x*16+2,y*16+2,12,12);ctx.fillStyle='#fff';ctx.font='12px monospace';ctx.fillText(n.index,x*16,y*16+12)}}else{ctx.strokeStyle='#db6eff';ctx.lineWidth=3;ctx.strokeRect(203*16,142*16,16,16)}}
function select(){active=document.getElementById('map').value;document.getElementById('detail').textContent='Map '+active+' · 点击图块查看证据；未知事件/NPC未自动验证。';img.onload=draw;img.src='maps/'+active+'.png'}
document.getElementById('map').onchange=select;document.getElementById('collision').onchange=draw;document.getElementById('npc').onchange=draw;
c.onclick=e=>{let b=c.getBoundingClientRect(),x=Math.floor((e.clientX-b.left)*c.width/b.width/16),y=Math.floor((e.clientY-b.top)*c.height/b.height/16),m=D.maps[active],t=m.grid[y][x];document.getElementById('detail').textContent=JSON.stringify({map:active,x,y,metatile:t,collisionClass:m.collisionClasses[t],kind:m.collisionGrid[y][x],npc:active==='114'?D.npcs.filter(n=>n.metatile[0]===x&&n.metatile[1]===y):'World dynamic NPC UNKNOWN',transition:D.transition},null,2)};select();</script></html>'''
    (out/'vertical-slice-viewer.html').write_text(document.replace('DATA',json.dumps(data,ensure_ascii=False).replace('</','<\\/')),encoding='utf8')
