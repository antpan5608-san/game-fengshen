"""Checkpoint A adapter: existing extracted map -> bounded local development package.
No new ROM importer. Android receives PNG atlases and a grid, never a ROM.
"""
import json
import shutil
import sys
from pathlib import Path
from PIL import Image
from forensics.common import ROOT,load,save
from forensics.fengshen246 import Reader,SHA256,digest,extract_map,extract_opening_encounter,extract_town_shops
from forensics.rom import tile_image

OUT=ROOT/'game-data/packages/development/opening-segment-001-c11'

def export_from_base(payload, provenance_path, target_pin, verify_target=True):
    """TOWN-02 only: reuse checked content bytes and export one evidenced definition.

    The caller validates the immutable base APK with ci_apk.content. No original
    ROM, historical captures or unchanged image/audio regeneration are needed in CI.
    """
    evidence_path=(ROOT/provenance_path).resolve()
    if not evidence_path.is_relative_to(ROOT) or evidence_path.suffix!='.json':
        raise ValueError('Invalid iteration provenance path')
    evidence=load(evidence_path)
    if evidence['taskId']!='TOWN-02' or evidence['romSha256']!=SHA256:
        raise ValueError('Unexpected iteration evidence')
    rule=evidence['herbUse']
    if rule!={'healHp':50,'mapMenu':True,'target':'living-party-member',
              'consumeAtFullHp':True,'evidence':provenance_path}:
        raise ValueError('Unsupported scoped herb policy')
    def encoded(value):return (json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf-8')
    result=dict(payload)
    scene=json.loads(result['scene.json'])
    matches=[i for i in scene['items'] if i['id']=='rom.medicine.0']
    if len(matches)!=1 or matches[0]['originalId']!=0 or matches[0]['category']!='medicine':
        raise ValueError('Base herb ID/category changed')
    herb=matches[0]
    herb['herbUse']=rule
    herb['description']='恢复50HP；满HP仍消耗'
    herb['source']['verifiedFields']=sorted(set(herb['source']['verifiedFields'])|{'mapMenuUse','healHp','target','consumption'})
    herb['source']['remainingUnknown']=[x for x in herb['source']['remainingUnknown'] if x!='medicine effects/use conditions']
    herb['source']['useEvidence']=provenance_path
    result['scene.json']=encoded(scene)
    for name,raw in list(result.items()):
        if name=='manifest.json' or not name.endswith('.json'):continue
        value=json.loads(raw)
        if 'version' in value:
            value['version']=target_pin['contentVersion'];result[name]=encoded(value)
    manifest=json.loads(result['manifest.json'])
    manifest['version']=target_pin['contentVersion']
    manifest['files']={name:digest(raw) for name,raw in result.items() if name!='manifest.json'}
    result['manifest.json']=encoded(manifest)
    if verify_target and digest(result['manifest.json'])!=target_pin['manifestSha256']:
        raise ValueError('Local herb export differs from reviewed target pin')
    return result

def export():
    candidates=[p for p in (ROOT/'reference/rom').rglob('*') if p.suffix.lower()=='.nes' and digest(p.read_bytes())==SHA256]
    if not candidates:raise ValueError('Pinned local ROM required to export assets; Android does not read ROM')
    r=Reader(candidates[0].read_bytes());m=load(ROOT/'game-data/raw/rom/maps/114.json')
    world=load(ROOT/'game-data/raw/rom/maps/16.json')
    village=extract_map(r,0)
    save(ROOT/'game-data/raw/rom/maps/0.json',village)
    if m['gridSha256']!=digest(bytes(t for row in m['grid'] for t in row)):raise ValueError('Map grid hash mismatch')
    if world['gridSha256']!=digest(bytes(t for row in world['grid'] for t in row)):raise ValueError('World grid hash mismatch')
    manifest=load(ROOT/'game-data/provenance/original.json')
    from forensics.validator import validate_original_artifacts
    audit=validate_original_artifacts(manifest)
    if audit['status']!='PASS':raise ValueError('Local ROM evidence audit must pass before development export')
    encounter=extract_opening_encounter(r)
    if encounter['groups'][11]['entities']!=[
        {'slot':2,'sourceType':3,'enemyId':2},{'slot':4,'sourceType':4,'enemyId':3}]:
        raise ValueError('Observed first battle group does not match ROM group 11')
    save(ROOT/'game-data/raw/rom/encounter-opening.json',encounter)
    capture_paths=['private-derived/battle01-trigger-left/timeline.tsv',
        'private-derived/battle01-trigger-idle/timeline.tsv',
        'private-derived/battle01-trigger-right/timeline.tsv',
        'private-derived/battle01-state/frame-1820-sram.bin',
        'private-derived/battle01-actions-2/actions.tsv']
    script_paths=['tools/rom-extractor/probe-battle01-trigger.lua',
        'tools/rom-extractor/probe-battle01-state.lua',
        'tools/rom-extractor/probe-battle01-actions.lua']
    if not all((ROOT/p).is_file() for p in capture_paths+script_paths):
        raise ValueError('BATTLE-01 normal-input evidence missing')
    provenance={'romSha256':SHA256,'scope':'Map 16 zone 0 and group 0 only',
        'captures':{p:digest((ROOT/p).read_bytes()) for p in capture_paths},
        'scripts':{p:digest((ROOT/p).read_bytes()) for p in script_paths},
        'romRanges':{k:encounter[k] for k in ('zoneRange','groupCountRange','groupRootRange','enemyRemapRange')},
        'limitations':encounter['remainingUnknown']}
    save(ROOT/'game-data/provenance/battle01.json',provenance)
    battle02=load(ROOT/'game-data/provenance/battle02.json')
    if battle02['romSha256']!=SHA256:raise ValueError('BATTLE-02 ROM fingerprint mismatch')
    for path,expected in battle02['captures'].items():
        if digest((ROOT/path).read_bytes())!=expected:raise ValueError(f'BATTLE-02 capture changed: {path}')
    if digest((ROOT/battle02['probe']['path']).read_bytes())!=battle02['probe']['sha256']:
        raise ValueError('BATTLE-02 normal controller probe changed')
    for span in [battle02['escape']['routine'],battle02['escape']['order'],
                 battle02['defeat']['clearRoutine'],battle02['defeat']['openingInit']]:
        if digest(r.data[span['offset']:span['offset']+span['length']])!=span['sha256']:
            raise ValueError('BATTLE-02 enabled rule ROM evidence changed')
    world01=load(ROOT/'game-data/provenance/world01.json')
    if world01['romSha256']!=SHA256 or digest((ROOT/world01['probeScript']['path']).read_bytes())!=world01['probeScript']['sha256']:
        raise ValueError('WORLD-01 replay script/ROM provenance changed')
    for path,expected in world01['captures'].items():
        if digest((ROOT/path).read_bytes())!=expected:raise ValueError(f'WORLD-01 capture changed: {path}')
    for row in world01['exits']:
        if r.data[row['rom']['offset']:row['rom']['offset']+5].hex()!=row['rawHex']:
            raise ValueError('WORLD-01 ROM exit row changed')
    facing=world01['arrivalFacing']
    if facing['rawValue']!=0 or facing['decodedDirection']!='DOWN':raise ValueError('WORLD-01 arrival facing changed')
    for sample in facing['runtimeFrames']:
        rows=[line.split('\t') for line in (ROOT/sample['capture']).read_text().splitlines()]
        matches=[a for a in rows if int(a[0])==sample['frame']]
        if len(matches)!=1 or int(matches[0][1])!=sample['mapId'] or int(matches[0][5])!=facing['rawValue']:
            raise ValueError('WORLD-01 arrival-facing runtime sample changed')
    equipment_evidence=load(ROOT/'game-data/provenance/equipment-opening.json')
    if equipment_evidence['romSha256']!=SHA256:raise ValueError('Opening equipment evidence ROM mismatch')
    for name,sha in equipment_evidence['captures'].items():
        path=ROOT/'private-derived/equipment-probe-final'/name
        if not path.exists() or digest(path.read_bytes())!=sha:raise ValueError(f'Opening equipment capture mismatch: {name}')
    script=equipment_evidence['script']
    if digest((ROOT/script['path']).read_bytes())!=script['sha256']:raise ValueError('Opening equipment replay script changed')
    snapshots=[(ROOT/f'private-derived/equipment-probe-final/frame-{f}-ram.bin').read_bytes() for f in ('0590','0710','1350','2000')]
    if [(b[0x5d0],b[0x560],b[0x548],int.from_bytes(b[0x52c:0x52e],'little')) for b in snapshots]!=[
        (0,0,2,0),(1,0,2,0),(2,255,0,0),(1,0,2,0)]:raise ValueError('Knife gift/equipment RAM cycle changed')
    writes=(ROOT/'private-derived/equipment-probe-final/writes.tsv').read_text()
    if not all(s in writes for s in ('649\t05D0\t01\tA183\t9','1232\t05D0\t02\tA144\t9',
        '1980\t05D0\t01\tA1ED\t9','1981\t0560\t00\tA7B1\t9')):raise ValueError('Opening equipment write trace changed')
    OUT.mkdir(parents=True,exist_ok=True)
    ppu=(ROOT/'private-derived/slice-probe/frame-0450-ppu.bin').read_bytes()
    palette_path=ROOT/'private-derived/tooling/fceux-2.6.6/palettes/FCEUX.pal'
    rgb=palette_path.read_bytes();palette=ppu[0x3f00:0x3f20]
    if len(rgb)<192:raise ValueError('64-color palette missing')
    def color(index):return tuple(rgb[(index&63)*3:(index&63)*3+3])+(255,)
    def tile(image,x,y,pattern,t,pal,sprite=False,flip=0):
        # Existing decoder supplies grayscale indices; only adapt palette/OAM here.
        for yy in range(8):
            for xx in range(8):
                v=pattern.getpixel((t%16*8+xx,t//16*8+yy))[0]//85
                c=(0,0,0,0) if sprite and v==0 else color(palette[0 if v==0 else pal*4+v])
                image.putpixel((x+(7-xx if flip&64 else xx),y+(7-yy if flip&128 else yy)),c)
    atlas=Image.new('RGBA',(256,256));base=0x80010+m['chr2kBanks'][0]*2048;pattern=tile_image(r.data[base:base+4096])
    for t in range(256):
        for q,idx in enumerate(m['metatiles'][t]):tile(atlas,t%16*16+q%2*8,t//16*16+q//2*8,pattern,idx,m['attributes'][t]&3)
    atlas.save(OUT/'tiles.png')
    sprite_refs=[]
    # Exact observed OAM poses; no invented walking animation or sprite bytecode interpreter.
    for direction,frame in [('down',2280),('up',600),('left',2080),('right',2440)]:
        stem=ROOT/f'private-derived/slice-probe/frame-{frame:04d}'
        ram=Path(str(stem)+'-ram.bin').read_bytes();p=Path(str(stem)+'-ppu.bin').read_bytes()
        oam=ram[0x204:0x214];coords=[(oam[i+3],oam[i]) for i in range(0,16,4)]
        x0=min(v[0] for v in coords);y0=min(v[1] for v in coords);sprite=Image.new('RGBA',(16,16))
        pattern=p[0x1000:0x2000];off=r.data.find(pattern[:2048],0x80010)
        if off<0:raise ValueError('Observed sprite CHR cannot be traced to ROM')
        pattern=tile_image(pattern)
        for i in range(0,16,4):
            y,t,a,x=oam[i:i+4];tile(sprite,x-x0,y-y0,pattern,t,4+(a&3),True,a)
        sprite.save(OUT/f'player-{direction}.png')
        sprite_refs.append({'direction':direction,'frame':frame,'ramSha256':digest(ram),'ppuSha256':digest(p),
            'romOffset':off,'length':2048,'romRangeSha256':digest(r.data[off:off+2048]),'oamHex':oam.hex()})
    world_ppu=(ROOT/'private-derived/world-probe/frame-0960-ppu.bin').read_bytes()
    palette=world_ppu[0x3f00:0x3f20]
    world_atlas=Image.new('RGBA',(256,256));world_base=0x80010+world['chr2kBanks'][0]*2048
    world_pattern=tile_image(r.data[world_base:world_base+4096])
    for t in range(256):
        for q,idx in enumerate(world['metatiles'][t]):
            tile(world_atlas,t%16*16+q%2*8,t//16*16+q//2*8,world_pattern,idx,world['attributes'][t]&3)
    world_atlas.save(OUT/'tiles16.png')
    collision=m['collisionCandidate'];classes=list(r.read(collision['module'],collision['cpuAddress'],256))
    world_collision=world['collisionCandidate'];world_classes=list(r.read(world_collision['module'],world_collision['cpuAddress'],256))
    village_ppu=(ROOT/'private-derived/world01-next-80/settled-ppu.bin').read_bytes()
    palette=village_ppu[0x3f00:0x3f20]
    village_atlas=Image.new('RGBA',(256,256));village_base=0x80010+village['chr2kBanks'][0]*2048
    village_pattern=tile_image(r.data[village_base:village_base+4096])
    for t in range(256):
        for q,idx in enumerate(village['metatiles'][t]):
            tile(village_atlas,t%16*16+q%2*8,t//16*16+q//2*8,village_pattern,idx,village['attributes'][t]&3)
    village_atlas.save(OUT/'tiles0.png')
    palette=world_ppu[0x3f00:0x3f20]
    trajectory_refs=[]
    for probe in ('slice-probe','world-probe'):
        path=ROOT/f'private-derived/{probe}/trajectory.tsv';trajectory_refs.append({'path':path.relative_to(ROOT).as_posix(),'sha256':digest(path.read_bytes())})
    opening_walkable={i for i,t in enumerate(t for row in m['grid'] for t in row) if classes[t] in (0,2)}
    world_walkable={i for i,t in enumerate(t for row in world['grid'] for t in row) if world_classes[t] in (0,2)}
    village_collision=village['collisionCandidate'];village_classes=list(r.read(village_collision['module'],village_collision['cpuAddress'],256))
    village_walkable={i for i,t in enumerate(t for row in village['grid'] for t in row) if village_classes[t] in (0,2)}
    world_transition_cells={142*world['width']+203,130*world['width']+202,130*world['width']+203}
    def exit_row(map_id,index):
        address=r.word(8,0xdc69+2*map_id)+5*index
        data=r.read(8,address,5)
        return {'romOffset':r.span(8,address,5,'ROM exit row')['offset'],'rawHex':data.hex(),
            'confidence':'VERIFIED','romSha256':SHA256}
    return_capture=ROOT/'private-derived/world01-return/trajectory.tsv'
    village_capture=ROOT/'private-derived/world01-roundtrip-80/trajectory.tsv'
    if '915\t114\t' not in return_capture.read_text() or '1303\t16\t' not in village_capture.read_text():
        raise ValueError('Normal-controller return evidence missing')
    npcs=load(ROOT/'game-data/raw/rom/npc-opening.json')['records']
    # The opening capture contains real OAM poses for these NPC slots. Other
    # opening servants share the same ROM sprite ID; their motion remains open.
    npc_pose={0:133,1:137,2:141,3:145,7:149}
    pose_for={0:0,1:1,2:2,3:3,4:3,5:3,6:3,7:7}
    npc_ram=(ROOT/'private-derived/npc-probe/frame-0450-ram.bin').read_bytes()
    npc_ppu=(ROOT/'private-derived/npc-probe/frame-0450-ppu.bin').read_bytes()
    initial_money=int.from_bytes(npc_ram[0x501:0x504],'little')
    npc_chr_refs=[]
    for bank in (0x1000,0x1800):
        pattern_bytes=npc_ppu[bank:bank+2048]
        rom_offset=r.data.find(pattern_bytes,0x80010)
        if rom_offset<0:raise ValueError('NPC sprite CHR cannot be traced to ROM')
        npc_chr_refs.append({'ppuOffset':bank,'romOffset':rom_offset,'length':2048,
            'romRangeSha256':digest(r.data[rom_offset:rom_offset+2048])})
    palette=npc_ppu[0x3f00:0x3f20]
    npc_pattern=tile_image(npc_ppu[0x1000:0x2000])
    npc_sprite_refs=[]
    for index,slot in npc_pose.items():
        oam=npc_ram[slot*4:slot*4+16]
        if len(oam)!=16 or any(oam[j]>=240 for j in (0,4,8,12)):
            raise ValueError(f'NPC {index} runtime OAM missing')
        coords=[(oam[j+3],oam[j]) for j in (0,4,8,12)]
        x0=min(x for x,y in coords);y0=min(y for x,y in coords)
        sprite=Image.new('RGBA',(16,16))
        for j in (0,4,8,12):
            y,t,a,x=oam[j:j+4]
            tile(sprite,x-x0,y-y0,npc_pattern,t,4+(a&3),True,a)
        sprite.save(OUT/f'npc-{index}.png')
        npc_sprite_refs.append({'npcIndex':index,'oamSlot':slot,'oamHex':oam.hex(),
            'ramSha256':digest(npc_ram),'ppuSha256':digest(npc_ppu)})
    dialogue_records=load(ROOT/'game-data/raw/rom/dialogues-opening.json')['records']
    dialogue_by_index={d['messageIndex']:d for d in dialogue_records}
    opening_npcs=[]
    for n in npcs[:8]:
        if not n['runtimeCopyMatches']:raise ValueError('NPC runtime copy mismatch')
        for field in ('dialogueFirst','dialogueRepeat'):
            if n[field]!=255 and n[field] not in dialogue_by_index:raise ValueError('NPC dialogue missing')
        index=n['index']
        effect=[]
        if index==1:effect=[{'type':'money','amount':100,'source':'reference.story.9','confidence':'PROVISIONAL_REFERENCE','originalVerified':False}]
        if index==2:effect=[{'type':'item','id':'rom.item.0','amount':1,
            'source':'game-data/provenance/equipment-opening.json','confidence':'GAMEPLAY_VERIFIED',
            'originalVerified':True,'evidence':'frame649 CPU RAM 05D0: 0→1 at module9 A183'}]
        opening_npcs.append({'id':f'rom.npc.114.{index}','originalIndex':index,'mapId':114,'cell':n['metatile'],
            'sprite':f'npc-{pose_for[index]}.png','spriteId':n['spriteId'],
            'firstDialogue':f"rom.dialogue.124.{n['dialogueFirst']}",
            'repeatDialogue':None if n['dialogueRepeat']==255 else f"rom.dialogue.124.{n['dialogueRepeat']}",
            'firstEffects':effect,'source':{'romOffset':n['range']['offset'],'romSha256':SHA256,
                'confidence':n['confidence'],'originalVerified':True,'movementVerified':n['movementProgram']==0}})
    npc_cells=[n['cell'][1]*m['width']+n['cell'][0] for n in opening_npcs]
    dialogues=[{'id':d['id'],'text':d['text'],'source':{'romOffset':d['range']['offset'],
        'romSha256':SHA256,'confidence':d['confidence'],'originalVerified':d['confidence']=='VERIFIED'}} for d in dialogue_records]
    reference_story=load(ROOT/'game-data/raw/reference-project/tables/story.json')['rows'][0]
    if reference_story['id']!=1 or reference_story['getItemId']!=0:raise ValueError('Opening Reference intro changed')
    if '小刀' not in dialogue_by_index[6]['text'] or '兵器' not in dialogue_by_index[7]['text']:
        raise ValueError('ROM knife dialogue changed')
    initial=load(ROOT/'game-data/raw/rom/characters.json')['records'][0]
    if initial['nameObserved']!='哪吒' or initial['levelObserved']!=1 or initial['experience']!=0:
        raise ValueError('Opening player record changed; recheck ROM evidence')
    initial_player={'id':'nezha','name':initial['nameObserved'],'portraitAsset':'player-down.png',
        'portraitSource':{'kind':'ROM_OAM_POSE','frame':2280,'romOffset':sprite_refs[0]['romOffset'],
            'romRangeSha256':sprite_refs[0]['romRangeSha256'],'oamHex':sprite_refs[0]['oamHex']},
        'level':initial['levelObserved'],'experience':initial['experience'],
        'hp':initial['hp'],'maxHp':initial['maxHp'],'mp':initial['mp'],'maxMp':0,
        'maxMpSource':{'memoryDomain':'CPU RAM','address':'052C-052D','capture':'private-derived/equipment-probe-final/frame-0590-ram.bin',
            'confidence':'GAMEPLAY_VERIFIED'},
        'equipment':{'rightHand':0,'leftHand':-1,'body':0,'feet':28},
        'equipmentSource':{'memoryDomain':'CPU RAM','addresses':['0560','0564','0568','056C'],
            'capture':'private-derived/equipment-probe-final/frame-0590-ram.bin','confidence':'GAMEPLAY_VERIFIED'},
        'strength':initial['strength'],'stamina':initial['stamina'],'agility':initial['agility'],
        'spirit':initial['spirit'],'source':{'record':'characters.json#0','confidence':initial['confidence'],
            'evidence':initial['evidence']}}
    exit_data=load(ROOT/'game-data/raw/rom/transitions-v1.json')
    if not exit_data['runtimePassed'] or exit_data['rawHex']!='081d10cb8e':raise ValueError('Opening boundary exit evidence missing')
    progression=load(ROOT/'game-data/raw/rom/progression.json')['groups'][0]['rows']
    observed_enemy_names={i:load(ROOT/f'game-data/raw/rom/enemy-{i}.json')['nameObserved'] for i in (2,3)}
    combat={'schemaVersion':1,'version':'opening-segment-001-c11','romSha256':SHA256,
        'zone':{'mapId':16,'id':0,'rectangles':encounter['rectangles'],'source':encounter['zoneRange']},
        'gate':encounter['encounterGate'],
        'groups':[{'id':g['id'],'entities':g['entities'],'source':g['range']} for g in encounter['groups']],
        'enemies':[{'id':e['romEnemyId'],'name':observed_enemy_names.get(e['romEnemyId'],f"原版敌人 {e['romEnemyId']}"),
            'nameConfidence':'GAMEPLAY_VERIFIED' if e['romEnemyId'] in observed_enemy_names else 'UNKNOWN',
            'hp':e['hp'],'attack':e['attack'],'defense':e['defense'],
            'experienceReward':e['experienceReward'],'moneyReward':e['moneyReward'],
            'hitByte':e['remainingBytes'][2],'hitByteSemantics':'ROM battle AI compares $43 with $69AA',
            'behaviorByte':e['remainingBytes'][1],
            'source':e['range']} for e in encounter['enemies']],
        'nezhaGrowth':[{'level':i+1,'threshold':row['cumulativeExpCandidate'],
            'hp':row['hpDeltaCandidate'],'mp':row['mpDeltaCandidate'],
            'strength':row['strengthDeltaCandidate'],'stamina':row['staminaDeltaCandidate'],
            'agility':row['agilityDeltaCandidate'],'spirit':row['spiritDeltaCandidate'],
            'runtimeVerified':i<=2,'source':row['growthRange']} for i,row in enumerate(progression[:10]) if i>0],
        'initialArmorContribution':2,
        'initialArmorSource':r.span(0,0xb82a,5,'Initial body armor contribution 2'),
        'limitations':['RNG byte generation differs from NES free-running $43.',
            'Player miss/critical, escape and original defeat branch are not yet verified.']}
    # Reconstruct each enemy with the existing tile/palette decoder. No screenshot assets.
    import itertools
    def observed_rom_tiles(stem,x0,y0,w,h):
        # Raster palette changes during a frame. The capture supplies only a palette/index hint;
        # every output 8x8 tile must match bytes in the pinned CHR ROM and is decoded from those bytes.
        observed=Image.open(ROOT/(stem+'.png')).convert('RGB').crop((x0,y0,x0+w,y0+h))
        colors=sorted(set(observed.getdata())-{(0,0,0)})
        if len(colors)>3:raise ValueError('Battle asset has more than one NES tile palette')
        best=None
        for order in itertools.permutations([1,2,3],len(colors)):
            codes={(0,0,0):0,**dict(zip(colors,order))};tiles=[]
            for yy in range(0,h,8):
                for xx in range(0,w,8):
                    rows=[[codes[observed.getpixel((xx+x,yy+y))] for x in range(8)] for y in range(8)]
                    raw=bytes([sum((row[x]&1)<<(7-x) for x in range(8)) for row in rows]+
                        [sum(((row[x]>>1)&1)<<(7-x) for x in range(8)) for row in rows])
                    offset=r.data.find(raw,0x80010)
                    tiles.append((xx,yy,offset))
            score=sum(off>=0 for xx,yy,off in tiles)
            if best is None or score>best[0]:best=(score,codes,tiles)
        score,codes,tiles=best
        if score!=len(tiles):raise ValueError('Battle graphic cannot be reconstructed entirely from ROM tiles')
        image=Image.new('RGBA',(w,h));inverse={v:k for k,v in codes.items()};evidence=[]
        for xx,yy,offset in tiles:
            raw=r.data[offset:offset+16];decoded=tile_image(raw)
            for y in range(8):
                for x in range(8):
                    value=decoded.getpixel((x,y))[0]//85
                    image.putpixel((xx+x,yy+y),inverse[value]+(255,))
            evidence.append({'xy':[xx,yy],'offset':offset,'length':16,'sha256':digest(raw)})
        if image.convert('RGB').tobytes()!=observed.tobytes():raise ValueError('Battle ROM tile reconstruction mismatch')
        return image,{'capture':stem+'.png','captureSha256':digest((ROOT/(stem+'.png')).read_bytes()),
            'observedRect':[x0,y0,w,h],'paletteCodes':{str(v):list(k) for k,v in codes.items()},
            'tiles':evidence,'pixelMatch':True,'romSha256':SHA256,'confidence':'GAMEPLAY_VERIFIED',
            'limits':'One observed pose and emulator palette; no complete original animation claim.'}
    graphic_samples={1:('private-derived/level-probe/007500-b0-periodic',5,32,48),
        2:('private-derived/battle02-run/002250-state',2,32,32),
        3:('private-derived/battle02-run/002250-state',4,40,40)}
    graphics=[]
    for enemy_id,(stem,slot,w,h) in graphic_samples.items():
        sr=(ROOT/(stem+'-sram.bin')).read_bytes()
        if sr[0x177+slot]!=enemy_id:raise ValueError('Enemy graphic runtime ID mismatch')
        image,evidence=observed_rom_tiles(stem,16+32*slot,72,w,h)
        image.save(OUT/f'enemy-{enemy_id}.png')
        evidence.update({'sramSha256':digest(sr),'slot':slot})
        graphics.append({'enemyId':enemy_id,'asset':f'enemy-{enemy_id}.png','width':w,'height':h,'source':evidence})
    horizon,horizon_evidence=observed_rom_tiles('private-derived/battle02-run/002250-state',0,0,256,32)
    horizon.save(OUT/'battle-horizon.png')
    hero_pose,hero_pose_evidence=observed_rom_tiles('private-derived/battle02-run/002400-state',120,147,16,16)
    hero_pose.save(OUT/'battle-hero.png')
    captured=(ROOT/'private-derived/battle02-run/002250-state-ppu.bin').read_bytes()
    combat['presentation']={'graphics':graphics,'horizon':'battle-horizon.png','hero':'battle-hero.png',
        'heroSource':hero_pose_evidence,'logicalSize':[256,240],
        'enemySlotLayout':{'xOrigin':16,'xStride':32,'y':72},
        'source':{'capture':'private-derived/battle02-run/002250-state','ppuSha256':digest(captured),
            'originalHeroBattlePortrait':False,'horizon':horizon_evidence},'timingConfidence':'MOBILE_PRESENTATION_ADAPTATION'}
    combat['escape']={'enabled':True,'enemyAgility':{str(e['romEnemyId']):e['remainingBytes'][0] for e in encounter['enemies']},
        'randomTransform':'ROL twice with entry carry=1, ADC original with carry from bit6; uint8',
        'threshold':'uint8(127 + hero agility - first enemy agility in stable descending agility order)',
        'successComparison':'transformedByte < threshold','failureConsumesTurn':True,'graceSteps':0,
        'source':'game-data/provenance/battle02.json#escape'}
    combat['defeat']={'enabled':True,'kind':'RESET_OPENING_STATE','mapId':114,'cell':[8,21],
        'message':'不幸！全員陣亡了！','source':'game-data/provenance/battle02.json#defeat'}
    combat['limitations']=['RNG byte generation differs from NES free-running $43.',
        'Player miss/critical and full action ordering remain scoped gaps.']
    save(OUT/'combat.json',combat)
    package={'schemaVersion':1,'version':'opening-segment-001-c11','channel':'development','runtimeScope':'BATTLE_01_PARTIAL',
        'originalMapId':114,'width':m['width'],'height':m['height'],'tileSize':16,'logicalWidth':256,'logicalHeight':240,
        'grid':[t for row in m['grid'] for t in row],'collision':[classes[t] for row in m['grid'] for t in row],
        'walkableClasses':[0,2],'enabledCells':sorted(opening_walkable),'dynamicObjectCells':npc_cells,'spawn':[8,21],
        'initialPlayer':initial_player,'initialMoney':initial_money,
        'initialMoneySource':{'capture':'private-derived/npc-probe/frame-0450-ram.bin','ramOffset':0x501,
            'length':3,'captureSha256':digest(npc_ram),'confidence':'GAMEPLAY_VERIFIED'},
        'intro':{'id':'reference.story.1','text':reference_story['speak_words'],
            'source':{'confidence':'PROVISIONAL_REFERENCE','originalVerified':False,'needsRomVerification':True}},
        'npcs':opening_npcs,'dialogues':dialogues,
        'items':[{'id':'rom.item.0','name':'小刀',
            'equipment':{'originalId':0,'slot':'rightHand','attackBonus':2,'allowedCharacters':['nezha'],
                'evidence':'game-data/provenance/equipment-opening.json'},
            'source':{'romInventoryAddress':'05D0','romEquippedAddress':'0560','confidence':'GAMEPLAY_VERIFIED',
                'originalVerified':True,'evidence':'game-data/provenance/equipment-opening.json',
                'referenceComparisonId':1}}],
        'maps':[{'id':114,'scene':'scene.json','atlas':'tiles.png'},
            {'id':16,'scene':'scene16.json','atlas':'tiles16.png'},
            {'id':0,'scene':'scene0.json','atlas':'tiles0.png'}],
        'exits':[{'fromMapId':114,'trigger':[8,29],'toMapId':16,'spawn':[203,142],
            'romOffset':exit_data['range']['offset'],'rawHex':exit_data['rawHex'],'confidence':exit_data['confidence']},
            {'fromMapId':16,'trigger':[203,142],'toMapId':114,'spawn':[8,29],**exit_row(16,0),
                'runtimeEvidence':{'path':return_capture.relative_to(ROOT).as_posix(),'sha256':digest(return_capture.read_bytes())}},
            {'fromMapId':16,'trigger':[202,130],'toMapId':0,'spawn':[0,15],**exit_row(16,1),
                'runtimeEvidence':{'path':village_capture.relative_to(ROOT).as_posix(),'sha256':digest(village_capture.read_bytes())}},
            {'fromMapId':16,'trigger':[203,130],'toMapId':0,'spawn':[0,15],**exit_row(16,2)},
            {'fromMapId':0,'trigger':[0,14],'direction':'LEFT','triggerMode':'EDGE','toMapId':16,'spawn':[202,130],
                **exit_row(0,0),'runtimeEvidence':{'path':village_capture.relative_to(ROOT).as_posix(),
                    'sha256':digest(village_capture.read_bytes())}}],
        'source':{'romSha256':SHA256,'mapGridSha256':m['gridSha256'],'header':m['header'],
            'evidence':['source.original.map114.geometry','source.original.v1.collision-opening','source.original.v1.npc-opening','source.original.v1.transitions-v1','source.rom.routine.character.init'],
            'palette':'FCEUX RGB palette; emulator color approximation, not physical-TV color verification',
            'paletteFileSha256':digest(rgb),'ppuPaletteSha256':digest(ppu[0x3f00:0x3f20]),'spritePoses':sprite_refs,
            'npcSpritePoses':npc_sprite_refs,'npcChrBanks':npc_chr_refs,'movementArea':trajectory_refs},
        'limitations':['Normal-foot collision classes 0/1/2 drive map114 movement; NPC positions block walking.',
            'Moving NPC trajectories, opening intro context, mother gift and some first/repeat event timing need ROM verification.',
            'Map 16 opening zone and 19 original groups are exported; battle runtime still has scoped rule gaps.',
            'Map16 and map0 normal-foot classes 0/2 are enabled; special entrances only at verified exit cells.',
            'Development fixed movement cadence; not a claim of complete original timing.'],
        'sprites':{d:f'player-{d}.png' for d in ('up','down','left','right')}}
    for link in package['exits']:
        link['arrivalDirection']='DOWN'
        link['arrivalDirectionEvidence']='game-data/provenance/world01.json#arrivalFacing'
        link['arrivalDirectionConfidence']='INFERRED_SAME_ROUTINE' if link['fromMapId']==16 and link['trigger']==[203,130] else 'GAMEPLAY_VERIFIED'
    save(OUT/'scene.json',package)
    world_package={'schemaVersion':1,'version':package['version'],'channel':'development','runtimeScope':'BATTLE_01_PARTIAL',
        'originalMapId':16,'width':world['width'],'height':world['height'],'tileSize':16,'logicalWidth':256,'logicalHeight':240,
        'grid':[t for row in world['grid'] for t in row],
        'collision':[world_classes[t] for row in world['grid'] for t in row],
        'walkableClasses':[0,2], 'transitionCells':sorted(world_transition_cells),
        'enabledCells':sorted(world_walkable|world_transition_cells),'spawn':[203,142],
        'source':{'romSha256':SHA256,'mapGridSha256':world['gridSha256'],
            'evidence':['source.original.v1.world-map-evidence','source.original.v1.collision-opening','source.original.v1.transitions-v1'],
            'ppuPaletteSha256':digest(palette),'movementArea':trajectory_refs},
        'limitations':['Normal-foot classes 0/2 and four verified exit cells enabled; other collision classes remain closed.',
            'World NPCs and other events not enabled; only opening encounter zone 0 is in combat.json.']}
    save(OUT/'scene16.json',world_package)
    village_package={'schemaVersion':1,'version':package['version'],'channel':'development','runtimeScope':'WORLD_01',
        'originalMapId':0,'width':village['width'],'height':village['height'],'tileSize':16,
        'logicalWidth':256,'logicalHeight':240,'grid':[t for row in village['grid'] for t in row],
        'collision':[village_classes[t] for row in village['grid'] for t in row],
        'walkableClasses':[0,2],'enabledCells':sorted(village_walkable),'spawn':[0,15],
        'source':{'romSha256':SHA256,'mapGridSha256':village['gridSha256'],
            'ppuPaletteSha256':digest(village_ppu[0x3f00:0x3f20]),
            'returnTrajectorySha256':digest(village_capture.read_bytes())},
        'limitations':['NPCs and building interiors are not enabled; collision classes other than normal-foot 0/2 are closed.']}
    save(OUT/'scene0.json',village_package)
    # TOWN-01: extend this exporter, not a parallel importer or Android ROM decoder.
    town=extract_town_shops(r)
    captures={}
    for folder in ('town01-weapon-trade','town01-armor-trade','town01-items-edges',
                   'town01-weapon-name1','town01-weapon-name2','town01-armor-name1','town01-armor-name2'):
        for p in (ROOT/'private-derived'/folder).glob('*'):
            if p.suffix in ('.tsv','.png','.bin'):captures[p.relative_to(ROOT).as_posix()]=digest(p.read_bytes())
    town['captures']=captures
    save(ROOT/'game-data/provenance/town01.json',town)
    names={('weapon',0):'小刀',('weapon',1):'手刀',('weapon',2):'長劍',
        ('armor',0):'肚兜',('armor',1):'布衣',('armor',28):'麻鞋',
        ('medicine',0):'藥草',('medicine',6):'牛黃丸'}
    items=[];merchants=[]
    for shop in town['shops']:
        mid=shop['mapId'];room=extract_map(r,mid);save(ROOT/f'game-data/raw/rom/maps/{mid}.json',room)
        folder='town01-'+{'weapon':'weapon-trade','armor':'armor-trade','medicine':'items-edges'}[shop['kind']]
        capture=next(p for p in sorted((ROOT/'private-derived'/folder).glob('*state-ppu.bin'))
            if Path(str(p).replace('-ppu.bin','-ram.bin')).read_bytes()[0x47]==mid
            and 120<=int.from_bytes(Path(str(p).replace('-ppu.bin','-ram.bin')).read_bytes()[0x406:0x408],'little')<=376)
        ram=Path(str(capture).replace('-ppu.bin','-ram.bin')).read_bytes()
        pp=capture.read_bytes();palette=pp[0x3f00:0x3f20]
        pattern=tile_image(r.data[0x80010+room['chr2kBanks'][0]*2048:0x80010+room['chr2kBanks'][0]*2048+4096])
        a=Image.new('RGBA',(256,256))
        for t in range(256):
            for q,idx in enumerate(room['metatiles'][t]):tile(a,t%16*16+q%2*8,t//16*16+q//2*8,pattern,idx,room['attributes'][t]&3)
        a.save(OUT/f'tiles{mid}.png')
        col=room['collisionCandidate'];cc=list(r.read(col['module'],col['cpuAddress'],256))
        grid=[t for row in room['grid'] for t in row];spawn=shop['spawn'];keeper=[spawn[0],5]
        room_data={'schemaVersion':1,'version':package['version'],'channel':'development','originalMapId':mid,
            'width':room['width'],'height':room['height'],'tileSize':16,'logicalWidth':256,'logicalHeight':240,
            'grid':grid,'collision':[cc[t] for t in grid],'walkableClasses':[0,2],
            'enabledCells':[i for i,t in enumerate(grid) if cc[t] in (0,2)],
            'dynamicObjectCells':[keeper[1]*room['width']+keeper[0]],'spawn':spawn,
            'source':{'romSha256':SHA256,'mapGridSha256':room['gridSha256'],'runtimeCapture':capture.relative_to(ROOT).as_posix()}}
        save(OUT/f'scene{mid}.json',room_data)
        # CHR bytes must occur in the pinned ROM, even for observed OAM and preview poses.
        raw_pattern=bytearray()
        for start in (0x1000,0x1800):
            chunk=pp[start:start+2048];offset=r.data.find(chunk,0x80010)
            if offset<0:raise ValueError('Town sprite CHR does not match pinned ROM')
            raw_pattern.extend(r.data[offset:offset+2048])
        sprite_pattern=tile_image(bytes(raw_pattern));sprite=Image.new('RGBA',(16,16));oam=ram[0x214:0x224]
        x0=min(oam[i+3] for i in range(0,16,4));y0=min(oam[i] for i in range(0,16,4))
        for i in range(0,16,4):
            y,t,attr,x=oam[i:i+4];tile(sprite,x-x0,y-y0,sprite_pattern,t,4+(attr&3),True,attr)
        sprite.save(OUT/f'merchant-{mid}.png')
        preview_capture=sorted((ROOT/'private-derived'/folder).glob('*transaction-ppu.bin'))[0]
        preview_ppu=preview_capture.read_bytes();palette=preview_ppu[0x3f00:0x3f20]
        preview_bytes=bytearray()
        for start in (0,0x800):
            chunk=preview_ppu[start:start+2048];offset=r.data.find(chunk,0x80010)
            if offset<0:raise ValueError('Equipment preview CHR not found in pinned ROM')
            preview_bytes.extend(r.data[offset:offset+2048])
        preview_pattern=tile_image(bytes(preview_bytes))
        merchant_id=f'rom.npc.{mid}.0';dialogue_id=f'rom.shop.{mid}.prompt'
        prompt={'weapon':'你想買些什麼？','armor':'好，公子要買什麼嗎？','medicine':'要買寶貝嗎？'}[shop['kind']]
        package['dialogues'].append({'id':dialogue_id,'text':prompt,
            'source':{'confidence':'GAMEPLAY_VERIFIED','capture':folder,'originalVerified':True}})
        package['npcs'].append({'id':merchant_id,'cell':keeper,'mapId':mid,'sprite':f'merchant-{mid}.png',
            'firstDialogue':dialogue_id,'repeatDialogue':None,'firstEffects':[],
            'interactionCell':[spawn[0],7],'shopId':f'rom.shop.{mid}',
            'source':{'confidence':'GAMEPLAY_VERIFIED','romSha256':SHA256,'capture':folder}})
        stock=[]
        for row in shop['rows']:
            kind=shop['kind'];item_id=row['originalId'];stable='rom.item.0' if (kind,item_id)==('weapon',0) else f'rom.{kind}.{item_id}'
            record={'id':stable,'name':names[kind,item_id],'category':kind,'originalId':item_id,
                'buyPrice':row['price'],'sellPrice':max(1,row['price']//2),'maxCount':10,
                'source':{'confidence':'GAMEPLAY_VERIFIED','originalVerified':False,
                    'verifiedFields':['name','originalId','category','buyPrice','sellPrice']+(['preview'] if kind in ('weapon','armor') else []),
                    'nameRange':row['nameRange'],'priceRange':row['priceRange'],
                    'evidence':'game-data/provenance/town01.json','remainingUnknown':['medicine effects/use conditions','automatic equipment replacement']}}
            if (kind,item_id)==('medicine',0):record['sellPrice']=7
            if kind in ('weapon','armor'):
                typ=0 if kind=='weapon' else 1;ptr=r.word(2,r.word(2,0xf002+typ*2)+item_id*2)
                w,h,pal=r.read(2,ptr,3);tiles=r.read(2,ptr+3,w*h)
                chr_bank=r.read(2,r.word(2,0xcf24+typ*2)+item_id)[0]
                chr_offset=0x80010+chr_bank*2048
                preview_pattern=tile_image(r.data[chr_offset:chr_offset+4096])
                preview=Image.new('RGBA',(w*8,h*8))
                for j,t in enumerate(tiles):tile(preview,j%w*8,j//w*8,preview_pattern,t,4+pal,True)
                record['preview']={'asset':f'item-{kind}-{item_id}.png','width':w*8,'height':h*8,
                    'source':r.span(2,ptr,3+w*h,'Original equipment preview composition'),
                    'chrOffset':chr_offset,'chrLength':4096,'chrSha256':digest(r.data[chr_offset:chr_offset+4096]),
                    'bankSelection':r.span(2,r.word(2,0xcf24+typ*2)+item_id,1,'Preview sprite CHR register 6006/6007'),
                    'palette':list(palette[16+pal*4:20+pal*4]),'capture':preview_capture.relative_to(ROOT).as_posix()}
                preview.save(OUT/record['preview']['asset'])
                bonus=r.word(2,r.word(2,0xe95e+typ*2)+item_id*2)
                slot='rightHand' if kind=='weapon' else 'feet' if item_id==28 else 'body'
                record['equipment']={'originalId':item_id,'slot':slot,'attackBonus':bonus if kind=='weapon' else 0,
                    'defenseBonus':bonus if slot=='body' else 0,'evasionValue':bonus if slot=='feet' else 0,
                    'allowedCharacters':['nezha'],'evidence':'game-data/provenance/town01.json',
                    'operationEnabled':True,
                    'attributeRange':r.span(2,r.word(2,0xe95e+typ*2)+item_id*2,2,'Equipment contribution'),
                    'characterSlotList':r.span(2,0xef88 if kind=='weapon' else 0xefa6 if item_id==28 else 0xef9b,
                        19 if kind=='weapon' else 10 if item_id==28 else 11,'Nezha permitted equipment IDs')}
            items.append(record);stock.append(stable)
        merchants.append({'id':f'rom.shop.{mid}','mapId':mid,'npcId':merchant_id,
            'name':{'weapon':'兵器店','armor':'防具店','medicine':'百貨店'}[shop['kind']],'buyPrompt':prompt,
            'items':stock,'sellItems':stock,
            'source':{'confidence':'GAMEPLAY_VERIFIED','stockRange':shop['stockRange']}})
        package['maps'].append({'id':mid,'scene':f'scene{mid}.json','atlas':f'tiles{mid}.png'})
        package['exits'].extend([
            {'fromMapId':0,'trigger':shop['door'],'toMapId':mid,'spawn':spawn,'confidence':'VERIFIED','arrivalDirection':'DOWN'},
            {'fromMapId':mid,'trigger':spawn,'toMapId':0,
                'spawn':shop['door'],'confidence':'VERIFIED','arrivalDirection':'DOWN',
                'originalRow':r.span(8,r.word(8,0xdc69+mid*2),5,'Original FE return-to-caller exit'),
                'returnOrigin':'Town 0 saved entry origin; one scoped entrance per exported shop'}])
    package['items']=items;package['shops']=merchants;package['runtimeScope']='TOWN_01_SCOPED'
    package['limitations']=[s for s in package['limitations'] if not s.startswith('Map16 and map0')]
    package['limitations'].append('Town 0 three shops and directional edges enabled; inn/residential/NPC events remain closed. Sale restricted to exported stock categories; explicit remove/equip only.')
    town['items']=items
    town['nameComparisons']=[{'romName':'手刀','referenceName':'棍棒','buyPrice':50,'status':'MODIFIED',
        'capture':'private-derived/town01-weapon-name1/006218-transaction.png'},
        {'romName':'長劍','referenceName':'鐵劍','buyPrice':120,'status':'MODIFIED',
        'capture':'private-derived/town01-weapon-name2/006218-transaction.png'}]
    town['probe']={'path':'tools/rom-extractor/probe-town01.lua','sha256':digest((ROOT/'tools/rom-extractor/probe-town01.lua').read_bytes()),
        'inputOnly':True,'paths':{p.relative_to(ROOT).as_posix():digest(p.read_bytes()) for p in (ROOT/'private-derived').glob('town01-*/path.lua')}}
    save(ROOT/'game-data/provenance/town01.json',town)
    save(ROOT/'game-data/raw/rom/shops-town0.json',town)
    village_package['walkableClasses']=[0,*range(2,10)]
    village_package['transitionCells']=[s['door'][1]*village['width']+s['door'][0] for s in town['shops']]
    village_package['enabledCells']=[i for i,c in enumerate(village_package['collision']) if c in village_package['walkableClasses'] or i in village_package['transitionCells']]
    village_package['sourceEdges']={str(k):v for k,v in town['sourceEdges'].items()}
    village_package['targetEdges']={str(k):v for k,v in town['targetEdges'].items()}
    village_package['limitations']=['Inn/residential doors and town NPC events remain development boundaries.']
    save(OUT/'scene0.json',village_package);save(OUT/'scene.json',package)
    # Thin audio adapter using the existing development package and hash validator.
    audio=load(ROOT/'game-data/provenance/audio-log01.json')
    for asset in audio['assets']:
        source=Path(asset['source']['path'])
        if digest(source.read_bytes())!=asset['source']['sha256']:raise ValueError('Audio source changed')
        recording=ROOT/asset['source']['recording']
        if digest(recording.read_bytes())!=asset['source']['recordingSha256']:raise ValueError('ROM audio evidence changed')
        shutil.copyfile(source,OUT/asset['file'])
    save(OUT/'audio.json',audio)
    files={p.name:digest(p.read_bytes()) for p in OUT.iterdir() if p.is_file() and p.name!='manifest.json'}
    save(OUT/'manifest.json',{'schemaVersion':1,'version':package['version'],'channel':'development','files':files})
    target=ROOT/'android/app/src/main/assets/development';target.mkdir(parents=True,exist_ok=True)
    for name in [*files,'manifest.json']:shutil.copyfile(OUT/name,target/name)
    report={'status':'PASS','version':package['version'],'mapIds':[114,16,0,17,18,19],
        'openingWalkableCells':len(opening_walkable),'worldWalkableCells':len(world_walkable),
        'villageWalkableCells':len(village_walkable),'files':files,
        'output':OUT.relative_to(ROOT).as_posix(),'canonicalModified':False,'uploaded':False}
    save(ROOT/'reports/development-export.json',report);return report

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument('--base-apk',type=Path)
    parser.add_argument('--provenance')
    parser.add_argument('--version')
    args=parser.parse_args()
    if args.base_apk:
        import ci_apk as ci
        ci.verify_apk(args.base_apk,release=True)
        base=ci.CONFIG.get('iteration',{}).get('base',ci.CONFIG)
        if base.get('apkSha256') and digest(args.base_apk.read_bytes())!=base['apkSha256']:
            raise ValueError('Wrong reviewed base APK bytes')
        if not args.provenance or not args.version:parser.error('Provide provenance and target version')
        target=dict(ci.CONFIG,contentVersion=args.version)
        # Authoring computes a reviewable pin; CI always verifies the committed target pin.
        payload=export_from_base(ci.content(args.base_apk,base),args.provenance,target,verify_target=False)
        out=ROOT/'game-data/packages/development'/args.version
        out.mkdir(parents=True,exist_ok=True)
        for name,raw in payload.items():(out/name).write_bytes(raw)
        print(json.dumps({'contentVersion':args.version,'manifestSha256':digest(payload['manifest.json']),
            'files':len(payload),'output':str(out),'uploaded':False}))
    else:print(json.dumps(export(),ensure_ascii=False,indent=2))
