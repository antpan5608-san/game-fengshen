"""Checkpoint A adapter: existing extracted map -> bounded local development package.
No new ROM importer. Android receives PNG atlases and a grid, never a ROM.
"""
import json
import shutil
import sys
import io
import struct
import zlib
import itertools
import urllib.request
from pathlib import Path
from PIL import Image
from forensics.common import ROOT,load,save
from forensics.fengshen246 import Reader,SHA256,digest,extract_map,extract_opening_encounter,extract_town_shops,extract_enemy
from forensics.rom import tile_image

OUT=ROOT/'game-data/packages/development/opening-segment-001-c11'

def iteration_reader():
    """Reuse the pinned public input in a private cache; never include it in exports."""
    acquisition=load(ROOT/'game-data/provenance/rom-acquisition.json')['acquisitions'][0]
    if acquisition['sha256']!=SHA256 or acquisition['size']!=1048592:
        raise ValueError('Unexpected iteration ROM acquisition pin')
    cache=ROOT/'.ci-private/nanhai-target.nes'
    for path in (ROOT/'private-inputs/town02/target.nes',cache):
        if path.is_file():
            raw=path.read_bytes()
            if len(raw)!=acquisition['size'] or digest(raw)!=SHA256:
                raise ValueError('Cached iteration ROM differs; do not silently replace it')
            return Reader(raw)
    url=acquisition['url']
    if not url.startswith('https://raw.githubusercontent.com/') or acquisition['commit'] not in url:
        raise ValueError('ROM input must use the existing immutable public source')
    with urllib.request.urlopen(url,timeout=60) as response:
        if not response.url.startswith('https://'):raise ValueError('Insecure ROM redirect')
        raw=response.read(acquisition['size']+1)
    if len(raw)!=acquisition['size'] or digest(raw)!=SHA256:
        raise ValueError('Downloaded iteration ROM fingerprint differs')
    cache.parent.mkdir(parents=True,exist_ok=True);cache.write_bytes(raw);cache.chmod(0o600)
    return Reader(raw)

def checked_span(reader,span):
    offset=span['offset'];length=span['length']
    if offset<16 or length<1 or offset+length>len(reader.data):
        raise ValueError('Iteration evidence range outside pinned ROM')
    raw=reader.data[offset:offset+length]
    if digest(raw)!=span['sha256']:raise ValueError('Iteration evidence ROM range changed')
    return raw

def deterministic_rgba_png(image):
    """Fixed PNG filter and stored DEFLATE blocks; independent of Pillow/zlib encoders.
    Only new scoped graphics use this encoding. Reviewed base media stay byte-identical.
    """
    if image.mode!='RGBA':raise ValueError('Scoped PNG must be RGBA')
    width,height=image.size
    if not 0<width<=256 or not 0<height<=256:raise ValueError('Scoped PNG exceeds bounds')
    pixels=image.tobytes();stride=width*4
    scan=b''.join(b'\x00'+pixels[y*stride:(y+1)*stride] for y in range(height))
    packed=bytearray(b'\x78\x01')
    for start in range(0,len(scan),65535):
        block=scan[start:start+65535];last=start+len(block)==len(scan)
        packed.extend(bytes([int(last)])+struct.pack('<HH',len(block),len(block)^0xffff)+block)
    packed.extend(struct.pack('>I',zlib.adler32(scan)&0xffffffff))
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return (b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+
            chunk(b'IDAT',bytes(packed))+chunk(b'IEND',b''))

def scoped_map_atlas(reader,map_data,palette,rgb):
    """Existing metatile/CHR decoder, with this scene's observed NES palette."""
    if len(palette)!=32 or any(x not in range(64) for x in palette) or len(set(palette))<2:
        raise ValueError('Missing or faded scene palette')
    if len(rgb)!=64 or any(len(c)!=3 or any(x not in range(256) for x in c) for c in rgb):
        raise ValueError('Invalid emulator RGB palette')
    offset=reader.header['sections']['chr']['offset']+map_data['chr2kBanks'][0]*2048
    pattern=tile_image(reader.data[offset:offset+4096]);image=Image.new('RGBA',(256,256))
    for t in range(256):
        pal=map_data['attributes'][t]&3
        for q,index in enumerate(map_data['metatiles'][t]):
            for y in range(8):
                for x in range(8):
                    value=pattern.getpixel((index%16*8+x,index//16*8+y))[0]//85
                    color=palette[0 if value==0 else pal*4+value]
                    image.putpixel((t%16*16+q%2*8+x,t//16*16+q//2*8+y),tuple(rgb[color])+(255,))
    return deterministic_rgba_png(image)

def cache_world_scenes(reader, destination, rgb):
    """Recover original geometry/default atlases in the existing private resource cache.

    This is input recovery, not a playable package: no spawn, flags, collision
    permissions, NPCs or encounter suppression are invented here.
    """
    from forensics.fengshen246 import extract_default_map_palette
    destination=Path(destination).resolve()
    if not any(destination.is_relative_to((ROOT/p).resolve()) for p in ('private-derived','.ci-private')):
        raise ValueError('World cache must remain in an existing ignored private directory')
    if digest(reader.data)!=SHA256:raise ValueError('World cache requires matching target ROM')
    count=(0xdcfc-0xdb9e)//2
    signature=digest((Path(__file__).read_bytes()+
        (ROOT/'tools/forensics/fengshen246.py').read_bytes()+json.dumps(rgb).encode()))
    pin={'schemaVersion':1,'romSha256':SHA256,'generatorSha256':signature,'geometrySlots':count}
    destination.mkdir(parents=True,exist_ok=True);index=destination/'index.json'
    def child(name):
        p=(destination/name).resolve()
        if not p.is_relative_to(destination):raise ValueError('Escaped cache path')
        return p
    if index.is_file():
        prior=load(index)
        if prior.get('input')==pin and [m.get('mapId') for m in prior.get('maps',[])]==list(range(count)):
            if all(child(m[k]).is_file() and digest(child(m[k]).read_bytes())==m[k+'Sha256']
                    for m in prior['maps'] for k in ('geometry','atlas')):
                return dict(prior,cacheReused=True)
    rows=[];atlas_assets=set()
    for mid in range(count):
        data=extract_map(reader,mid);palette=extract_default_map_palette(reader,mid)
        raw=(json.dumps(data,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode()
        png=scoped_map_atlas(reader,data,palette['palette'],rgb);asset=digest(png)
        geometry=f'map{mid}.json';atlas=f'atlases/{asset}.png'
        gp=child(geometry);ap=child(atlas);ap.parent.mkdir(parents=True,exist_ok=True)
        gp.write_bytes(raw)
        if not ap.is_file() or digest(ap.read_bytes())!=asset:ap.write_bytes(png)
        image=Image.open(io.BytesIO(png));colors=set(image.getdata())
        black=all(c[:3]==(0,0,0) for c in colors)
        rows.append({'mapId':mid,'geometry':geometry,'geometrySha256':digest(raw),
            'atlas':atlas,'atlasSha256':asset,'atlasRgbaSha256':digest(image.tobytes()),
            'paletteSource':palette,'gridSha256':data['gridSha256'],
            'visualInput':'BLACK_REQUIRES_STATE_PALETTE' if black else 'STATIC_DEFAULT_RECOVERED',
            'runtime':'NOT_RUN','normalReachability':'NOT_VERIFIED'})
        atlas_assets.add(asset)
    report={'input':pin,'maps':rows,'uniqueAtlases':len(atlas_assets),'cacheReused':False,
        'effectiveMapCount':None,'effectiveMapCountStatus':'UNKNOWN_EXTRA_HEADER_SLOT_175',
        'limitations':['Static input cache only; does not enable maps, services, events or encounters',
            'Scripted lighting, dynamic map/NPC states and extra header slot remain unverified']}
    save(index,report)
    return report

def scoped_observed_graphic(reader,recipe):
    """Rebuild the existing observed-ROM-tile recipe without uploading private PPU dumps."""
    width,height=recipe['width'],recipe['height']
    if width not in range(8,257,8) or height not in range(8,241,8):
        raise ValueError('Invalid bounded graphic dimensions')
    colors=recipe['paletteCodes'];image=Image.new('RGBA',(width,height));occupied=set()
    for tile in recipe['tiles']:
        raw=checked_span(reader,tile)
        if len(raw)!=16:raise ValueError('Graphic tile must be 16 bytes')
        xx,yy=tile['xy']
        if xx%8 or yy%8 or not 0<=xx<=width-8 or not 0<=yy<=height-8 or (xx,yy) in occupied:
            raise ValueError('Overlapping or escaped graphic tile')
        occupied.add((xx,yy))
        for flag in ('flipX','flipY'):
            if flag in tile and not isinstance(tile[flag],bool):raise ValueError('Graphic flip must be boolean')
        if 'attribute' in tile and (bool(tile['attribute']&64)!=tile.get('flipX',False) or bool(tile['attribute']&128)!=tile.get('flipY',False)):
            raise ValueError('Graphic OAM flip differs from the observed attribute')
        for y in range(8):
            for x in range(8):
                sx=7-x if tile.get('flipX',False)else x
                sy=7-y if tile.get('flipY',False)else y
                value=((raw[sy]>>(7-sx))&1)+2*((raw[sy+8]>>(7-sx))&1)
                color=tuple(colors[str(value)])+(0 if value==0 and recipe.get('transparentZero',True) else 255,)
                image.putpixel((xx+x,yy+y),color)
    if len(occupied)!=width*height//64:raise ValueError('Incomplete graphic recipe')
    if not recipe.get('rgbaSha256') or digest(image.tobytes())!=recipe['rgbaSha256']:
        raise ValueError('Reconstructed graphic differs from reviewed RGBA pixels')
    return deterministic_rgba_png(image)

def observed_graphic_recipe(reader,capture_path,rect,transparent_zero=False):
    """Bounded evidence helper for the existing ROM-tile recipe, never an image importer.

    Call only with a settled original capture and an explicitly reviewed rectangle.
    A partial match, faded frame, or extra palette colors is rejected.
    """
    if digest(reader.data)!=SHA256:raise ValueError('Graphic evidence needs target ROM')
    x,y,width,height=rect
    # Sprite OAM origins are pixel positions (NES stores y-1); only the
    # composition dimensions, not its screen origin, are tile multiples.
    if any(not isinstance(v,int) for v in rect) or x<0 or y<0 or width%8 or height%8 or not 8<=width<=256 or not 8<=height<=240:
        raise ValueError('Graphic evidence dimensions must align to tiles')
    with Image.open(capture_path) as capture:
        if x<0 or y<0 or x+width>capture.width or y+height>capture.height:raise ValueError('Capture rectangle escapes image')
        observed=capture.convert('RGB').crop((x,y,x+width,y+height))
    colors=sorted(set(observed.getdata())-{(0,0,0)})
    if not 1<=len(colors)<=3:raise ValueError('Faded or mixed graphic palette')
    chr_start=reader.header['sections']['chr']['offset'];best=None
    for order in itertools.permutations((1,2,3),len(colors)):
        codes={(0,0,0):0,**dict(zip(colors,order))};tiles=[]
        for yy in range(0,height,8):
            for xx in range(0,width,8):
                rows=[[codes[observed.getpixel((xx+dx,yy+dy))] for dx in range(8)] for dy in range(8)]
                raw=bytes([sum((row[dx]&1)<<(7-dx) for dx in range(8)) for row in rows]+
                    [sum(((row[dx]>>1)&1)<<(7-dx) for dx in range(8)) for row in rows])
                offset=reader.data.find(raw,chr_start)
                tiles.append({'xy':[xx,yy],'offset':offset,'length':16,'sha256':digest(raw)})
        matches=sum(t['offset']>=chr_start for t in tiles)
        if best is None or matches>best[0]:best=(matches,codes,tiles)
    matches,codes,tiles=best
    if matches!=len(tiles):raise ValueError(f'Incomplete original tile match: {matches}/{len(tiles)}')
    rgba=observed.convert('RGBA')
    if transparent_zero:
        rgba.putdata([p[:3]+(0 if p[:3]==(0,0,0) else 255,) for p in rgba.getdata()])
    recipe={'width':width,'height':height,'observedRect':rect,'transparentZero':transparent_zero,
        'paletteCodes':{str(code):list(color) for color,code in codes.items()},'tiles':tiles,
        'rgbaSha256':digest(rgba.tobytes()),'captureSha256':digest(Path(capture_path).read_bytes()),
        'captureKind':'CONTROLLED_ORIGINAL_FULL_GROUP_LOADER','normalPlayEvidence':False}
    scoped_observed_graphic(reader,recipe) # Same CI reconstruction and RGBA gate.
    return recipe

def export_nanhai_from_base(payload,evidence,provenance_path,target_pin):
    """Extend the current exporter for this bounded route, retaining base media bytes."""
    if evidence.get('romSha256')!=SHA256 or evidence.get('taskId')!='NANHAI-01':
        raise ValueError('Unexpected Nanhai iteration evidence')
    if digest(payload['manifest.json'])!=evidence['baseManifestSha256']:
        raise ValueError('Nanhai export requires the reviewed base content')
    reader=iteration_reader();result=dict(payload)
    for enemy in evidence['combatOverlay']['enemies']:
        original=extract_enemy(reader,enemy['id'])
        for field in ('hp','attack','defense','experienceReward','moneyReward'):
            if enemy[field]!=original[field]:raise ValueError('Enemy values differ from pinned ROM')
        remaining=original['remainingBytes']
        if (enemy['behaviorByte'],enemy['hitByte'])!=(remaining[1],remaining[2]):
            raise ValueError('Enemy behavior differs from pinned ROM')
        checked_span(reader,enemy['source'])
        if enemy.get('loot') and enemy['loot']['threshold']!=remaining[3]:
            raise ValueError('Loot threshold differs from pinned ROM')
    for zone in evidence['combatOverlay']['zones']:
        for group in zone['groups']:
            raw=checked_span(reader,group['range'])
            expected=bytes([v for entity in group['entities'] for v in (entity['slot']+1,entity['sourceType'])]+[0])
            if raw!=expected:raise ValueError('Encounter slots differ from pinned ROM')
    def verify_ranges(value):
        if isinstance(value,dict):
            if all(k in value for k in ('offset','length','sha256')):checked_span(reader,value)
            for child in value.values():verify_ranges(child)
        elif isinstance(value,list):
            for child in value:verify_ranges(child)
    verify_ranges(evidence['evidence']['boss'])
    verify_ranges(evidence['evidence']['routeEncounterReset']['staticEvidence'])
    encoded=lambda value:(json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf-8')
    scene=json.loads(result['scene.json']);maps={m['mapId']:m for m in evidence['maps']}
    if set(maps)!={25,97}:raise ValueError('Nanhai map scope changed without evidence review')
    if set(maps)&{m['id'] for m in scene['maps']}:raise ValueError('New maps overlap base scenes')
    exits=evidence['exits']
    if any(e['fromMapId'] not in (16,25,97) or e['toMapId'] not in (16,25,97) for e in exits):
        raise ValueError('Exit escapes current route scope')
    for exit in exits:
        raw=checked_span(reader,exit['rom'])
        if list(raw)!=exit['trigger']+[exit['toMapId']]+exit['spawn']:
            raise ValueError('Nanhai exit does not match original dispatch record')
        if exit['confidence']!='GAMEPLAY_VERIFIED_SCOPED':
            raise ValueError('Route entry/return lacks normal original-game evidence')
        reset_row=next((row for row in evidence['evidence']['routeEncounterReset']['rows']
            if all(row[k]==exit[k] for k in ('fromMapId','toMapId','trigger','spawn'))),None)
        if (exit.get('resetEncounterSteps') is not True or not reset_row or
            reset_row.get('verification')!='NORMAL_CONTROLLER_ONLY_SCOPED' or
            reset_row['completedArrivalSnapshot']['encounterSteps005B']!=0 or
            reset_row['completedArrivalSnapshot']['cell']!=exit['spawn'] or
            reset_row['completedArrivalSnapshot']['mapId']!=exit['toMapId']):
            raise ValueError('Route encounter reset lacks scoped completed-arrival evidence')
        scene['exits'].append({k:exit[k] for k in ['fromMapId','trigger','toMapId','spawn','resetEncounterSteps']}|
            {'triggerMode':'CELL','confidence':'VERIFIED','arrivalDirection':'DOWN',
             'source':exit['rom'],'evidence':provenance_path})
    for mid,recipe in maps.items():
        original=extract_map(reader,mid)
        if original['gridSha256']!=recipe['gridSha256']:raise ValueError('Scoped map grid changed')
        c=original['collisionCandidate'];classes=list(reader.read(c['module'],c['cpuAddress'],256))
        grid=[t for row in original['grid'] for t in row];collision=[classes[t] for t in grid]
        transition={e['trigger'][1]*original['width']+e['trigger'][0] for e in exits if e['fromMapId']==mid}
        transition|={e['spawn'][1]*original['width']+e['spawn'][0] for e in exits if e['toMapId']==mid}
        # Full normal-foot areas, not a coordinate/trajectory whitelist.
        enabled=[i for i,c in enumerate(collision) if c in (0,2) or i in transition]
        data={'schemaVersion':1,'version':target_pin['contentVersion'],'channel':'development',
            'originalMapId':mid,'width':original['width'],'height':original['height'],
            'tileSize':16,'logicalWidth':256,'logicalHeight':240,'grid':grid,'collision':collision,
            'walkableClasses':[0,2],'transitionCells':sorted(transition),'enabledCells':enabled,
            'spawn':recipe['spawn'],'dynamicObjectCells':recipe.get('npcCells',[]),
            'source':{'romSha256':SHA256,'mapGridSha256':original['gridSha256'],
                'evidence':provenance_path,'paletteCapture':recipe['paletteCapture']},
            'limitations':recipe.get('limitations',[]),'unavailableRegions':recipe.get('unavailableRegions',[])}
        result[f'scene{mid}.json']=encoded(data)
        result[f'tiles{mid}.png']=scoped_map_atlas(reader,original,recipe['palette'],evidence['emulatorRgb'])
        scene['maps'].append({'id':mid,'scene':f'scene{mid}.json','atlas':f'tiles{mid}.png'})
    world=json.loads(result['scene16.json']);transition=set(world.get('transitionCells',[]))
    for exit in exits:
        for field,mid in [('trigger',exit['fromMapId']),('spawn',exit['toMapId'])]:
            if mid==16:transition.add(exit[field][1]*world['width']+exit[field][0])
    world['transitionCells']=sorted(transition)
    world['enabledCells']=sorted(set(world['enabledCells'])|transition)
    result['scene16.json']=encoded(world)
    if evidence.get('mapObjects'):scene['mapObjects']=evidence['mapObjects']
    if evidence.get('items'):
        if any(i['id'] in {old['id'] for old in scene['items']} for i in evidence['items']):raise ValueError('New item overlaps base definition')
        scene['items'].extend(evidence['items'])
    if evidence.get('npcs'):
        scene['npcs'].extend(evidence['npcs']);scene['dialogues'].extend(evidence['dialogues'])
    for name,recipe in evidence.get('graphics',{}).items():
        if '/' in name or '\\' in name or not name.endswith('.png'):raise ValueError('Unsafe scoped image name')
        result[name]=scoped_observed_graphic(reader,recipe)
    if evidence.get('combatOverlay'):
        combat=json.loads(result['combat.json']);overlay=evidence['combatOverlay']
        for name in ('enemies','zones'):
            if name in overlay:combat[name]=combat.get(name,[])+overlay[name]
        if 'enemyAgility' in overlay:combat['escape']['enemyAgility'].update(overlay['enemyAgility'])
        for graphic in overlay.get('graphics',[]):
            if 'origin' in graphic:
                recipe=evidence['graphics'][graphic['asset']]
                if graphic['origin']+[graphic['width'],graphic['height']]!=recipe.get('observedRect'):
                    raise ValueError('Enemy origin differs from observed original placement')
        combat['presentation']['graphics'].extend(overlay.get('graphics',[]))
        for name in ('horizons','blackBackgroundEnemyIds'):
            if name in overlay:combat['presentation'][name]=overlay[name]
        for name in ('physicalRules','bosses'):
            if name in overlay:combat[name]=overlay[name]
        observed=evidence['evidence'].get('normalGrowth',{})
        if overlay.get('growthVerifiedLevels'):
            if observed.get('normalReadOnlyNoRAMwrites')!=True:raise ValueError('Growth lacks normal original-game evidence')
            for sample in observed['observed']:
                state=sample['state'];level=state['level'];rows=[r for r in combat['nezhaGrowth'] if r['level']<=level]
                for field,initial in [('maxHp','maxHp'),('strength','strength'),('stamina','stamina'),('agility','agility'),('spirit','spirit')]:
                    increment='hp' if field=='maxHp' else field
                    if state[field]!=scene['initialPlayer'][initial]+sum(r[increment] for r in rows):raise ValueError('Observed growth conflicts with base rows')
                threshold=next(r['threshold'] for r in combat['nezhaGrowth'] if r['level']==level+1)
                if state['xp']+state['remainingExp']!=threshold:raise ValueError('Observed level threshold conflicts')
        for row in combat['nezhaGrowth']:
            if row['level'] in overlay.get('growthVerifiedLevels',[]):
                row['runtimeVerified']=True;row['runtimeEvidence']=provenance_path
        result['combat.json']=encoded(combat)
    if evidence.get('audio'):
        audio=json.loads(result['audio.json'])
        for candidate in evidence['audio']:
            name=candidate['file'];checked_name=name.startswith('bgm-') and name.endswith('.mp3') and '/' not in name and '\\' not in name
            if not checked_name or name in result:raise ValueError('Audio addition overlaps base or escapes scope')
            cache=ROOT/'.ci-private'/name
            url=candidate['source']
            prefix='https://raw.githubusercontent.com/v5100v5100/FengShenBang/'+evidence['sourceReference']['commit']+'/Resources/res/Sound/'
            if not url.startswith(prefix):raise ValueError('Unpinned audio source')
            if cache.is_file():raw=cache.read_bytes()
            else:
                with urllib.request.urlopen(url,timeout=60) as response:
                    if not response.url.startswith('https://'):raise ValueError('Insecure audio redirect')
                    raw=response.read(candidate['bytes']+1)
            if len(raw)!=candidate['bytes'] or digest(raw)!=candidate['sha256']:raise ValueError('Reference audio hash differs')
            cache.parent.mkdir(parents=True,exist_ok=True);cache.write_bytes(raw)
            result[name]=raw;track='reference.bgm.'+str(candidate['id']).zfill(3)
            audio['assets'].append({'id':track,'file':name,'kind':'BGM','loop':candidate['loop'],
                'loopStartMs':candidate['loopStartMs'],'loopEndMs':candidate['loopEndMs'],'source':candidate})
            audio['maps'][str(candidate['mapId'])]=track
        result['audio.json']=encoded(audio)
    scene['runtimeScope']=evidence['runtimeScope'];scene['nanhai']=evidence.get('story',{})
    scene['limitations']=[x for x in scene.get('limitations',[]) if not x.startswith('Nanhai')]
    scene['limitations'].extend(evidence.get('limitations',[]))
    result['scene.json']=encoded(scene)
    return result

def extend_world_growth(reader, combat, proof, provenance_path):
    """Batch the original owner's table; preserve witnessed rows and do not borrow another actor."""
    from forensics.fengshen246 import extract_growth_candidates
    original=extract_growth_candidates(reader);owner=original['groups'][0]
    if proof.get('actorIndex')!=0 or proof.get('confidence')!='ORIGINAL_ROM_STATIC':
        raise ValueError('Growth owner or evidence differs')
    for key in ('growthRange','thresholdRange'):
        if proof[key]!=owner[key]:raise ValueError('Growth table range differs')
        checked_span(reader,proof[key])
    cap=proof['levelCapSource']
    # Absolute LDA $0504,X / CMP #$4F / BCS $AF04; the stored level is zero based.
    if checked_span(reader,cap)!=bytes.fromhex('bd0405c94fb036'):
        raise ValueError('Original level-cap dispatch differs')
    if proof['maxLevel']!=80 or proof['levels']!=[2,80]:raise ValueError('Growth domain differs from original limit')
    previous={r['level']:r for r in combat['nezhaGrowth']};rows=[]
    fields=('level','threshold','hp','mp','strength','stamina','agility','spirit')
    for raw in owner['rows'][1:]:
        row=dict(level=raw['index']+1,threshold=raw['cumulativeExpCandidate'],hp=raw['hpDeltaCandidate'],
            mp=raw['mpDeltaCandidate'],strength=raw['strengthDeltaCandidate'],stamina=raw['staminaDeltaCandidate'],
            agility=raw['agilityDeltaCandidate'],spirit=raw['spiritDeltaCandidate'],runtimeVerified=False,
            source=raw['growthRange'],thresholdSource=raw['thresholdRange'],evidence=provenance_path)
        if row['level'] in previous:
            old=previous.pop(row['level'])
            if any(old[k]!=row[k] for k in fields):raise ValueError('Existing witnessed growth row differs')
            row=old
        rows.append(row)
    if previous or any(a['threshold']>=b['threshold'] for a,b in zip(rows,rows[1:])):
        raise ValueError('Growth lost old rows or cumulative ordering')
    combat['nezhaGrowth']=rows
    combat['growthLimit']={'owner':'nezha','level':proof['maxLevel'],'confidence':proof['confidence'],
        'evidence':provenance_path,'source':cap}

def extend_world_characters(reader, scene, combat, additions, overlay):
    """Validate a joined actor against its scoped initialization, own growth and ROM pointers."""
    from forensics.fengshen246 import extract_growth_candidates
    known={scene['initialPlayer']['id']}|{x['initialState']['id'] for x in scene.get('additionalCharacters',[])}
    for actor in additions:
        proof=load(ROOT/actor['evidence']);state=actor['initialState'];original=proof['initialCharacter']
        if proof['romSha256']!=SHA256 or state['id'] in known or actor['originalActorIndex']!=original['actorIndex']:
            raise ValueError('Joined actor identity or original initialization differs')
        if state!={k:original[k] for k in ('id','level','experience','hp','maxHp','mp','maxMp','strength','stamina','agility','spirit','statusMask','equipment')}:
            raise ValueError('Joined actor stats or equipment differ from scoped original initialization')
        for span in proof['initializationSources']:checked_span(reader,span)
        if actor['name']!=original['name'] or actor.get('skillRefs'):
            raise ValueError('Joined actor cannot borrow names or unverified learned spells')
        known.add(state['id'])
    growth=overlay.get('characterGrowth',[])
    if {x['owner'] for x in growth}!={x['initialState']['id'] for x in additions}:
        raise ValueError('Each joined actor requires its own complete growth table')
    original_groups=extract_growth_candidates(reader)['groups'] if growth else []
    for table in growth:
        actor=next(a for a in additions if a['initialState']['id']==table['owner'])
        proof=load(ROOT/actor['evidence'])['growthExtension'];index=actor['originalActorIndex']
        original=original_groups[index]
        if table['originalActorIndex']!=index or proof['actorIndex']!=index or table['knownMaxLevel']!=proof['maxLevel']:
            raise ValueError('Growth owner or max level differs')
        for field in ('growthRange','thresholdRange'):
            if proof[field]!=original[field]:raise ValueError('Joined growth range differs')
            checked_span(reader,proof[field])
        if checked_span(reader,proof['levelCapSource'])!=bytes.fromhex('bd0405c94fb036'):
            raise ValueError('Joined growth cap branch differs')
        if len(table['rows'])!=79:raise ValueError('Incomplete joined growth table')
        for row,raw in zip(table['rows'],original['rows'][1:]):
            expected=dict(level=raw['index']+1,threshold=raw['cumulativeExpCandidate'],hp=raw['hpDeltaCandidate'],
                mp=raw['mpDeltaCandidate'],strength=raw['strengthDeltaCandidate'],stamina=raw['staminaDeltaCandidate'],
                agility=raw['agilityDeltaCandidate'],spirit=raw['spiritDeltaCandidate'])
            if any(row[k]!=v for k,v in expected.items()):raise ValueError('Joined growth row differs from ROM')
            checked_span(reader,row['source']);checked_span(reader,row['thresholdSource'])
    multipliers=overlay.get('characterMultiplierThresholds',{})
    if set(multipliers)!={x['initialState']['id'] for x in additions}:
        raise ValueError('Joined physical multiplier table is missing')
    for owner,values in multipliers.items():
        actor=next(a for a in additions if a['initialState']['id']==owner)
        source=overlay['characterMultiplierSources'][owner]
        pointer=checked_span(reader,source['pointer'])
        if source['pointer']['cpuAddress']!=0x8577+2*actor['originalActorIndex'] or \
                int.from_bytes(pointer,'little')!=source['source']['cpuAddress'] or \
                list(checked_span(reader,source['source']))!=values or len(values)!=36:
            raise ValueError('Joined physical multiplier pointer or table differs')
    if additions:scene['additionalCharacters']=scene.get('additionalCharacters',[])+additions
    if growth:combat['characterGrowth']=combat.get('characterGrowth',[])+growth
    if multipliers:combat['physicalRules'].setdefault('characterMultiplierThresholds',{}).update(multipliers)


def extend_world_mechanisms(reader, scene, mechanisms, provenance_path):
    """Compile only evidenced dynamic chunks through the existing scene exporter."""
    for definition in mechanisms:
        proof=load(ROOT/definition['evidence'])
        if proof.get('romSha256')!=SHA256 or proof.get('scopeRevision')!='east95-dynamic-chunk':
            raise ValueError('Dynamic scene lacks current original evidence')
        for span in proof['sources']:checked_span(reader,span)
        rule=proof['mechanism']
        if (definition['id'],definition['mapId'],definition['x'],definition['y'],definition['sessionFlag'])!=(
                'rom.mechanism.95.0',95,12,21,'runtime.session.map95.mechanism0') or \
                (rule['mapId'],rule['triggerCell'],rule['requiresMovingState97'],rule['requiresFacing'])!=(95,[12,21],0,False):
            raise ValueError('Dynamic trigger differs from original dispatch')
        original=extract_map(reader,95)
        if 95 not in {m['id'] for m in scene['maps']} or original['tilesetId']!=4:
            raise ValueError('Dynamic scene not in reviewed map batch')
        chunk=original['chunks'][2]
        if (chunk['module'],chunk['cpuAddress'],chunk['length'],chunk['chunkX'],chunk['chunkY'])!=(7,0x9c90,240,0,1) or \
                (rule['replacementPointer'],rule['dataModule'])!=(0xcf30,7):
            raise ValueError('Dynamic original chunk source differs')
        old=checked_span(reader,chunk);new=reader.read(7,0xcf30,240)
        table=original['collisionCandidate'];classes=reader.read(table['module'],table['cpuAddress'],256)
        changes=[dict(x=i%16,y=15+i//16,fromTile=a,toTile=b,fromCollision=classes[a],toCollision=classes[b])
            for i,(a,b) in enumerate(zip(old,new)) if a!=b]
        evidenced=[dict(x=c['cell'][0],y=c['cell'][1],fromTile=c['metatileBefore'],toTile=c['metatileAfter'],
            fromCollision=c['collisionBefore'],toCollision=c['collisionAfter']) for c in proof['changedCells']]
        if changes!=evidenced or len(changes)!=9:
            raise ValueError('Dynamic cells differ from original chunk comparison')
        if any(m['id']==definition['id'] or m['sessionFlag']==definition['sessionFlag'] for m in scene.get('mechanisms',[])):
            raise ValueError('Duplicate scene mechanism')
        scene.setdefault('mechanisms',[]).append({k:definition[k] for k in ('id','mapId','x','y','sessionFlag','evidence')}|
            {'changes':changes,'resumePolicy':'ANDROID_SESSION_NOT_ORIGINAL_MANUAL_SAVE','source':provenance_path})

def validate_world_behavior1(reader,enemy):
    """Accept only the scoped original AI/damage proof, not a name-based guess."""
    from forensics.fengshen246 import extract_enemy_special_base
    path='game-data/provenance/world-enemy-behavior1.json'
    if enemy.get('specialDamageEvidence')!=path or 'iceBaseDamage' in enemy:
        raise ValueError('Behavior1 requires its independent original damage evidence')
    proof=load(ROOT/path);rules=proof['rules']
    if (proof['romSha256'],proof['scopeRevision'],proof['behavior']) != \
            (SHA256,'behavior1-all-living-targets-and-original-damage',1) or \
            rules['specialChoice']!='(random &127)<41' or \
            rules['specialHit']!='((random>>1)&63)<57, always true in actual choice branch' or \
            rules['allTarget'] is not True or rules['baseDamageIgnoresArmorAndStamina'] is not True:
        raise ValueError('Behavior1 rules differ from original dispatch')
    if proof['verifiedEnemyIds']!=[24,88,143,162,166,172,176] or enemy['id'] not in proof['verifiedEnemyIds']:
        raise ValueError('Behavior1 identity outside verified CPU scope')
    required={(9,0x8dc4,12),(9,0x8de9,201),(9,0xa956,117),(9,0xa912,2),(9,0xab6d,41),(9,0xab0a,79)}
    if {(s['module'],s['cpuAddress'],s['length']) for s in proof['sources']}!=required:
        raise ValueError('Behavior1 lacks choice, target, damage or HP evidence')
    for span in proof['sources']:checked_span(reader,span)
    actual=extract_enemy_special_base(reader,enemy['id'])
    if enemy.get('specialBaseDamage')!=actual['specialBaseDamage'] or enemy.get('specialSource')!=actual['specialSource']:
        raise ValueError('Behavior1 damage differs from original dispatch')
    checked_span(reader,enemy['specialSource'])

def validate_world_single_special(reader,enemy):
    """Only the evidenced behavior2/4 single-target path, never an ice alias."""
    from forensics.fengshen246 import extract_enemy_single_special_base,extract_enemy
    path='game-data/provenance/world-enemy-single-special.json'
    if enemy.get('specialDamageEvidence')!=path or 'iceBaseDamage' in enemy:
        raise ValueError('Single special attack requires its independent original evidence')
    proof=load(ROOT/path);rules=proof['rules'];behavior=enemy['behaviorByte']
    identities={'2':[67,121,128,147,159,164],'4':[33,46,115,163,165]}
    if proof['romSha256']!=SHA256 or proof['scopeRevision']!='behavior2-and4-single-target-original-damage' or \
            proof['verifiedEnemyIds']!=identities or behavior not in (2,4) or enemy['id'] not in identities[str(behavior)] or \
            extract_enemy(reader,enemy['id'])['remainingBytes'][1]!=behavior:
        raise ValueError('Single special attack identity or scope differs')
    if rules['specialChoice']!='(random &127)<41' or rules['secondaryThresholds']!={'2':64,'4':57} or \
            rules['specialHit']!='((random>>1)&63)<threshold, always true in actual choice branch' or \
            rules['allTarget'] is not False or rules['baseDamageIgnoresArmorAndStamina'] is not True or \
            rules['rawDamageDispatchCapturesDeathFlag'] is not False:
        raise ValueError('Single special attack choice, target or damage rule differs')
    required={(9,0x8dc4,12),(9,0x8de9,0xc9),(9,0xa956,0x12c),(9,0xab6d,0x29),(9,0xab0a,0x4f)}
    if {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
        raise ValueError('Single special attack lacks target or damage source')
    for span in proof['sources']:checked_span(reader,span)
    actual=extract_enemy_single_special_base(reader,enemy['id'])
    if proof['identityBases'][str(enemy['id'])]!=actual or any(enemy.get(k)!=v for k,v in actual.items()):
        raise ValueError('Single special attack base differs from original CPU scope')
    checked_span(reader,enemy['specialSource'])

def validate_world_hall_batch_terrain(reader,map_id,evidence_path='game-data/provenance/world-hell-hall-batch-terrain.json'):
    """Actual two-plane matrices for this batch; never a transport or field-damage rule."""
    scopes={'game-data/provenance/world-hell-hall-batch-terrain.json':
        ('hell-halls61-through68-ground-and-upper-plane-bridge-zero',6840,set(range(61,69))),
        'game-data/provenance/world-seventh-side-terrain.json':
        ('seventh-hall-side-rooms-ground-and-upper-plane-bridge-zero',864,{69,158,159}),
        'game-data/provenance/world-rebirth-terrain.json':
        ('map86-rebirth-ground-and-upper-plane-bridge-zero',128,{86})}
    if evidence_path not in scopes:raise ValueError('Unreviewed terrain evidence path')
    scope,count,maps=scopes[evidence_path];proof=load(ROOT/evidence_path)
    if proof['romSha256']!=SHA256 or proof['scopeRevision']!=scope or map_id not in maps or \
            proof['kind']!='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_ANDROID' or str(map_id) not in proof['maps'] or \
            proof['inputModes']!=[0,1] or proof['initialBridgeState']!=0 or proof['testCount']!=count or proof['failures']!=0 or \
            set(map(int,proof['maps']))!=maps:
        raise ValueError('Hell terrain batch outside original CPU scope')
    rule=proof['maps'][str(map_id)];original=extract_map(reader,map_id)
    cc=original['collisionCandidate'];lookup=reader.read(cc['module'],cc['cpuAddress'],256)
    import collections
    counts=dict(collections.Counter(str(lookup[t])for row in original['grid']for t in row))
    if original['tilesetId']!=3 or rule['mapId']!=map_id or rule['gridSha256']!=original['gridSha256'] or \
            rule['classCounts']!=counts or rule['testCount']!=len(counts)**2*8:
        raise ValueError('Hell terrain class matrix or grid differs')
    expected={'source23VerticalSelectsUpperOnlyForTarget14':True,'upperStandingClasses':[10,11,13,14,17,18,23],
        'upperBridge20Through22':'NOT_IMPLEMENTED','9b':'ORIGINAL_SPRITE_OCCLUSION_NOT_ENCOUNTER_CONTROL'}
    if proof['rules']!=expected or proof['encounterGate']['suppressesOn9b'] is not False or \
            proof['encounterGate']['testCount']!=480 or proof['encounterGate']['failures']!=0:
        raise ValueError('Terrain/encounter semantics differ from original CPU scope')
    required={(0,0xca98,29),(0,0xcc87,215),(0,0xce35,29),(0,0xd099,153),(0,0xd214,115),
        (0,0x874c,34),(11,0xc0b3,100),(11,0xed87+23,46)}
    if {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
        raise ValueError('Hell terrain lacks original plane, direction, occlusion or encounter code')
    for span in proof['sources']:checked_span(reader,span)
    for data in [proof,proof['encounterGate']]:
        if digest((ROOT/data['cpuExpectedPath']).read_bytes())!=data['cpuExpectedSha256']:
            raise ValueError('Original terrain/gate CPU expectations differ')
    return rule

def validate_world_seventh_side_npc_graphic(reader,sprite_id):
    """Only the genuinely visible sprite191 pose; no NPC movement rule inferred."""
    from forensics.fengshen246 import extract_npcs
    path='game-data/provenance/world-seventh-side-rooms.json';p=load(ROOT/path)
    recipe=p['sprite191']['recipe'];bindings=p['sprite191']['bindings']
    if sprite_id!=191 or p['romSha256']!=SHA256 or p['scopeRevision']!='seventh-hall-three-side-rooms' or \
            {(b['mapId'],b['npcIndex'])for b in bindings}!={(69,1),(158,1),(159,1)} or \
            recipe['captureKind']!='CONTROLLED_POSITION_REAL_SIDE_ROOM_ENTRY_VISIBLE_ORIGINAL_OAM' or \
            recipe['normalPlayEvidence'] is not False or recipe['opaquePixelMatch'] is not True or \
            (recipe['width'],recipe['height'],recipe['transparentZero'],recipe['opaquePixelCount'])!=(16,16,True,198):
        raise ValueError('Unreviewed side room sprite pose or identity')
    for b in bindings:
        n=extract_npcs(reader,b['mapId'])['records'][b['npcIndex']]
        if b['record']!=n['range'] or checked_span(reader,b['record'])[0]!=191:
            raise ValueError('Side room sprite does not bind the original NPC')
    raw=scoped_observed_graphic(reader,recipe)
    if sum(p[3]!=0 for p in Image.open(io.BytesIO(raw)).convert('RGBA').getdata())!=198:
        raise ValueError('Side room pose is incomplete or transparent')
    return recipe

def world_rebirth_definition(rule):
    path='game-data/provenance/world-rebirth-script.json'
    def movement(cell,direction,steps,encounter):
        return {'completedSteps':steps,'destination':{'mapId':86,'x':cell[0],'y':cell[1],
            'direction':direction,'terrainMode':0,'encounterSteps':encounter}}
    return {'id':rule['id'],'npcId':rule['npcId'],'flagId':rule['flagId'],
        'entryTrigger':{'mapId':86,'x':rule['trigger'][0],'y':rule['trigger'][1]},
        'openingMovement':movement(rule['openingPosition'],rule['openingDirection'],rule['openingSteps'],rule['openingEncounterSteps']),
        'movementsBeforeDialogue':[{'dialogueIndex':rule['laterMovementBeforeMessage']-2,
            **movement(rule['laterPosition'],rule['laterDirection'],rule['laterSteps'],rule['laterEncounterSteps'])}],
        'continuation':{'dialogueIds':rule['dialogueIds'],'destination':rule['destination'],
            'completionFlags':[rule['flagId']]},'evidence':path}

def validate_world_rebirth_script(reader,definition):
    from forensics.fengshen246 import extract_npcs
    p=load(ROOT/'game-data/provenance/world-rebirth-script.json');rule=p['rule']
    required={(11,0xd825,0x4e),(11,0xda4a,0x2e),(11,0xda81,3),(11,0xcb42,0x27),
        (11,0xccf9,10),(11,0xcd03,7),(11,0xcbee,11),(11,0xcbf9,10),(11,0xcd0a,0x19),
        (11,0xcc8a,0x6f),(11,0xc2ac,2),(11,0xc348,59),(0,0xd493+86*2,2),(0,0xba30,0xcf),(11,0xc000,5)}
    if p['romSha256']!=SHA256 or p['scopeRevision']!='final-hall-exit-map86-rebirth-original-scene-script' or \
            p['kind']!='CONTROLLED_ORIGINAL_CPU_AND_INPUT_NOT_NORMAL_ROUTE' or p['cpuCaseCount']!=56 or \
            p['mappedBanks']!=[0,1,46,3] or {(s['module'],s['cpuAddress'],s['length'])for s in p['sources']}!=required:
        raise ValueError('Scene script lacks its original captured CPU scope')
    for span in p['sources']:checked_span(reader,span)
    if digest((ROOT/p['cpuExpectedPath']).read_bytes())!=p['cpuExpectedSha256']:
        raise ValueError('Scene script CPU expectations differ')
    expected={'id':'rom.scene-story.86.rebirth','npcId':'rom.npc.86.0','mapId':86,'trigger':[12,5],
        'flagId':'rom.map.86.flag.128','eventId':2,'scriptId':3,'openingPosition':[7,4],
        'openingDirection':'UP','openingSteps':6,'openingEncounterSteps':6,'laterMovementBeforeMessage':10,
        'laterPosition':[7,5],'laterDirection':'LEFT','laterSteps':1,'laterEncounterSteps':7,
        'dialogueIds':[f'rom.dialogue.96.{i}'for i in range(2,13)],
        'destination':{'mapId':16,'x':238,'y':160,'direction':'UP','terrainMode':0,'encounterSteps':0},
        'reward':'NO_ADDITIONAL_STATS_ITEMS_OR_MONEY','reentryCompleted':'NO_SCRIPT_RESTART',
        'mapType':255,'defaultZone':255,'initialTerrainMode':0}
    if rule!=expected or definition!=world_rebirth_definition(rule):
        raise ValueError('Scene script movement, completion, text or destination differs')
    if reader.read(11,0xda81,3)!=bytes([12,5,0]) or reader.word(11,0xcb62)!=0xccf9 or \
            reader.word(11,0xc2ac)!=0xc348 or reader.word(0,0xd493+86*2)!=0x756 or \
            reader.read(0,0xed87+86)[0]!=255 or reader.read(0,0xee47+86)[0]!=255 or \
            reader.read(11,0xc000,5)!=bytes.fromhex('a55a100160'):
        raise ValueError('Scene script trigger, map flag, stream or encounter identity differs')
    npc=extract_npcs(reader,86)['records'][0]
    if npc['range']!=p['npcSource'] or checked_span(reader,p['npcSource'])!=bytes.fromhex('84ffff00e800a8001e8d01020000'):
        raise ValueError('Scene actor differs from original map86 record')
    if [x['partyHp']for x in p['poisonBoundaries']]!=[[35,29],[28,22],[27,21],[27,21]] or \
            any(x['status']!=[2,2]for x in p['poisonBoundaries']):
        raise ValueError('Scene automatic movement must preserve original poison costs')
    recipe=p['npcGraphic']
    if recipe['captureKind']!='CONTROLLED_COMPLETED_REBIRTH_FLAG_REAL_EXIT_VISIBLE_POSE_NO_DIALOGUE_CHR' or \
            recipe['normalPlayEvidence'] is not False or recipe['opaquePixelMatch'] is not True or \
            (recipe['width'],recipe['height'],recipe['transparentZero'],recipe['opaquePixelCount'])!=(16,16,True,204):
        raise ValueError('Scene actor has no complete visible original pose')
    raw=scoped_observed_graphic(reader,recipe)
    if sum(c[3]!=0 for c in Image.open(io.BytesIO(raw)).convert('RGBA').getdata())!=204:
        raise ValueError('Scene actor pose is incomplete')
    return p

def validate_world_field_protection_item(reader,item):
    path='game-data/provenance/world-field67-item12.json'
    proof=load(ROOT/path)
    identity={'id':'rom.special.12','categoryId':1,'originalId':12,'name':'定神珠',
        'nameGlyphHex':'02707478ff','maxCount':1,'consumption':0,'usedMarker':128,
        'targetMapId':67,'target':'scene-no-actor-no-object','price':'NOT_INFERRED'}
    required={(2,0xe52a,0x30),(2,0x9c7e,0x29),(2,0xa22c,0x6c),(2,0xe7de,1),
        (2,0xe610,2),(2,0xe7c0,2),(2,0xe847,5),(0,0x858a,7),(0,0xba7f,0x19),
        (0,0x8130,0x1e),(10,0x808b,0x21)}
    rules={'selectionOnly':True,'onConfirmSetsPending6814':True,
        'firstCompletedSourceStep67SetsActive686f':True,'mapReconstructionClearsPendingOnly':True,
        'activePersistsAcrossObserved67To23To67':True,'poisonNotPrevented':True}
    if proof['romSha256']!=SHA256 or proof['item']!=identity or proof['rules']!=rules or \
            proof['cpuCaseCount']!=50 or {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
        raise ValueError('Unverified map67 protection rule or identity')
    for span in proof['sources']:checked_span(reader,span)
    if digest((ROOT/proof['cpuExpectedPath']).read_bytes())!=proof['cpuExpectedSha256']:
        raise ValueError('Original item12 CPU expectations differ')
    if (item['id'],item['category'],item['originalId'],item['maxCount'],item['name'])!=('rom.special.12','special',12,1,'定神珠') or \
            any(k in item for k in ['buyPrice','sellPrice','worldUse','herbUse','antidoteUse','equipment']) or \
            item.get('fieldProtectionUse')!={'mapId':67,'reusable':True,'evidence':path}:
        raise ValueError('Map67 item definition differs from original scoped use')
    pointer=reader.word(2,reader.word(2,0xe610)+24)
    source=item['source']['nameRange']
    if source['module']!=2 or source['cpuAddress']!=pointer or checked_span(reader,source)!=bytes.fromhex(identity['nameGlyphHex']):
        raise ValueError('Item12 name source differs from original pointer')

def validate_world_hall_batch_npc_graphic(reader,sprite_id):
    """One genuinely visible original actor pose, including independent companion records."""
    from forensics.fengshen246 import extract_npcs
    proof=load(ROOT/'game-data/provenance/world-hell-hall-batch-resources.json')
    if proof['romSha256']!=SHA256 or sprite_id not in [192,193,194,195,196,197,208,209,210,254]:
        raise ValueError('NPC graphic outside completed original pose scope')
    entry=proof['npcSprites'][str(sprite_id)];recipe=entry['recipe']
    if entry['spriteId']!=sprite_id or entry['confidence']!='VERIFIED_ONE_CONTROLLED_ORIGINAL_POSE' or \
            recipe['captureKind']!='CONTROLLED_ORIGINAL_NPC_WORLD_POSITION_AND_COMPANION_ONLY' or \
            recipe['normalPlayEvidence'] is not False or recipe['opaquePixelMatch'] is not True or \
            recipe['width']!=16 or recipe['height']!=16 or recipe['transparentZero'] is not True:
        raise ValueError('NPC pose evidence does not justify this bounded graphic')
    if not entry['bindings']:raise ValueError('NPC pose has no original record identity')
    for binding in entry['bindings']:
        if binding['mapId'] not in range(61,69):raise ValueError('NPC binding outside scoped original maps')
        npc=extract_npcs(reader,binding['mapId'])['records'][binding['npcIndex']]
        if npc['range']!=binding['source'] or checked_span(reader,binding['source'])[0]!=sprite_id:
            raise ValueError('NPC graphic binding differs from actual original record')
    raw=scoped_observed_graphic(reader,recipe)
    image=Image.open(io.BytesIO(raw)).convert('RGBA');opaque=sum(p[3]!=0 for p in image.getdata())
    if not opaque or opaque!=recipe['opaquePixelCount']:raise ValueError('Transparent or incomplete NPC pose')
    return recipe

def validate_world_chest_grant(reader,npc):
    """Grant only from the actual chest record; item effect or price is not inferred."""
    path=npc['treasure']['evidence'];proof=load(ROOT/path)
    scopes={'game-data/provenance/world-hell-chest-grants.json':('hell-halls61-through68-actual-ordinary-chest-grant',72),
            'game-data/provenance/world-tree107-chests.json':('tree107-108-three-actual-ordinary-chest-grants',21)}
    if path not in scopes or proof['romSha256']!=SHA256 or (proof['scopeRevision'],proof['testCount'])!=scopes[path] or \
            proof['kind']!='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_ANDROID' or proof['failures']!=0:
        raise ValueError('Chest grant lacks original scoped evidence')
    if path=='game-data/provenance/world-tree107-chests.json':
        reuse=proof['ruleReuse'];raw=(ROOT/proof['cpuExpectedPath']).read_bytes()
        if reuse['path']!='game-data/provenance/world-hell-chest-grants.json' or \
                digest((ROOT/reuse['path']).read_bytes())!=reuse['sha256'] or \
                digest(raw)!=proof['cpuExpectedSha256'] or len(raw.splitlines())!=22 or \
                proof['activeCpuSha256']!=digest(reader.read(2,0x8000,32768)) or \
                [(b['mapId'],b['npcIndex'],b['categoryId'],b['originalId'])for b in proof['bindings']]!=[(107,0,3,30),(107,1,2,7),(108,0,0,14)]:
            raise ValueError('Tree chest source, original CPU or reused capacity rules differ')
    from forensics.fengshen246 import extract_npcs
    matches=[b for b in proof['bindings']if npc['id']==f'rom.npc.{b["mapId"]}.{b["npcIndex"]}']
    if len(matches)!=1:raise ValueError('Chest outside actual raw-record scope')
    rule=matches[0];original=extract_npcs(reader,rule['mapId'])['records'][rule['npcIndex']]
    raw=checked_span(reader,npc['source']['record']);category=rule['categoryId'];item_id=rule['originalId']
    categories={0:'medicine',1:'special',2:'weapon',3:'armor'}
    stable='rom.item.0' if (category,item_id)==(2,0) else f'rom.{categories[category]}.{item_id}'
    treasure=npc['treasure'];expected={'itemId':stable,'flagId':f'rom.map.{rule["mapId"]}.flag.{rule["flagMask"]}',
        'amount':1,'categoryGrant':category,'evidence':path}
    if npc['mapId']!=rule['mapId'] or original['range']!=npc['source']['record'] or rule['source']!=original['range'] or \
            list(raw[:3])!=[144,category,item_id] or raw[12]!=0 or raw[13]!=rule['flagMask'] or \
            rule['flagMask'] not in [1,2,4,8,16,32,64,128] or treasure!=expected or \
            rule['maxCount']!=reader.read(2,0xa190+category)[0] or not npc.get('openedSprite'):
        raise ValueError('Chest category, count, flag or original record differs')
    if proof['rules']!={'grantAmount':1,'categoryLimits':[10,1,10,10],'categorySlotCount':16,
            'fullExistingStackCanIncrease':True,'failureDoesNotSetOpenedFlag':True,'repeatDoesNotGrant':True,
            'quantityMask':127,'worldUseRuleInferred':False}:
        raise ValueError('Chest inventory rules differ')
    required={(10,0xa740,92),(2,0x9ec0,249),(2,0xa0df,12),(2,0xa0eb,173),(2,0xa190,12)}
    if {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
        raise ValueError('Chest lacks grant, flag or capacity source')
    for span in proof['sources']:checked_span(reader,span)
    animation=rule['animationSource']
    if animation['cpuAddress']!=int.from_bytes(raw[8:10],'little') or animation['cpuAddress']!=0x8e3d:
        raise ValueError('Chest opened graphic cannot reuse a different original animation')
    checked_span(reader,animation)
    if digest((ROOT/proof['cpuExpectedPath']).read_bytes())!=proof['cpuExpectedSha256']:
        raise ValueError('Original chest CPU expectations differ')
    return rule

def validate_world_hall_batch_script(reader,map_id):
    """Same original finalization, independently checked per event/gate, never a universal flag4."""
    from forensics.fengshen246 import extract_npcs
    p=load(ROOT/'game-data/provenance/world-hell-hall-batch-script.json')
    if p['romSha256']!=SHA256 or p['scopeRevision']!='hell-halls61-through68-original-finalization-and-gate-filter' or \
            str(map_id) not in p['maps'] or p['kind']!='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_BOSS_ANDROID':
        raise ValueError('Hell batch script outside its actual original scope')
    rule=p['maps'][str(map_id)];records=extract_npcs(reader,map_id)['records']
    king=records[rule['npcIndex']];gate=records[0];raw=checked_span(reader,rule['npcSource']);graw=checked_span(reader,rule['barrierSource'])
    if king['range']!=rule['npcSource'] or gate['range']!=rule['barrierSource'] or raw[0]!=189 or \
            list(raw[10:14])!=[1,2,rule['eventId'],rule['eventArgument']] or graw[0]!=246 or graw[13]!=rule['gateMask'] or \
            rule['mapId']!=map_id or rule['npcId']!=f'rom.npc.{map_id}.{king["index"]}' or \
            rule['bossVictoryFlag']!=f'rom.map.{map_id}.flag.{raw[13]}' or \
            rule['barrierRemovedFlag']!=f'rom.map.{map_id}.flag.{graw[13]}' or rule['victoryFlags']!=[rule['barrierRemovedFlag']]:
        raise ValueError('Hell batch NPC, gate or independent map flags differ')
    if rule['barrierCell']!=[(gate['xCandidate']-120)//16,(gate['yCandidate']-120)//16] or \
            rule['npcCell']!=[(king['xCandidate']-120)//16,(king['yCandidate']-120)//16] or rule['firstMessage']!=raw[1] or rule['repeatMessage']!=raw[2]:
        raise ValueError('Hell batch cell or message differs from original record')
    if rule.get('normalTalkCell')!=[rule['npcCell'][0],rule['npcCell'][1]+2] or rule.get('normalTalkDirection')!='UP' or \
            rule.get('interactionEvidenceKind')!='CONTROLLED_POSITION_DISPATCH_NOT_NORMAL_ROUTE' or len(rule.get('interactionRamSha256',''))!=64:
        raise ValueError('Hell batch lacks its controlled original interaction boundary')
    event=reader.word(10,0xd1e3+2*rule['eventId']);start=reader.word(10,event+6);win=reader.word(10,event+8)
    expected_source=bytes([0xa9,rule['sourceType']])+bytes.fromhex('2030d360')
    expected_win=bytes.fromhex('adc107d00420cfd1602072d32059d3a9')+bytes([rule['repeatMessage']])+bytes.fromhex('2078d360')
    if rule['sourceTypeInstruction']['cpuAddress']!=start or checked_span(reader,rule['sourceTypeInstruction'])!=expected_source or \
            rule['winInstruction']['cpuAddress']!=win or checked_span(reader,rule['winInstruction'])!=expected_win or \
            reader.read(1,0x9ea3+rule['sourceType'])[0]!=rule['enemyId']:
        raise ValueError('Hell batch source or success/failure finalization differs')
    for key in ('eventPointer','eventStages','mapFlagPointer'):checked_span(reader,rule[key])
    required={(10,0xd359,37),(10,0xcf29,9),(10,0xd6f4,0x3d),(0,0xa973,169)}
    if {(s['module'],s['cpuAddress'],s['length'])for s in p['sources']}!=required:
        raise ValueError('Hell batch lacks gate/event/reload sources')
    for span in p['sources']:checked_span(reader,span)
    return rule

def validate_world_ice_identities(reader,enemy):
    """Later ice identities require the actual all-identity CPU proof, not a dragon assumption."""
    from forensics.fengshen246 import extract_enemy_ice_base,extract_enemy
    path='game-data/provenance/world-enemy-ice-identities.json'
    if enemy.get('iceDamageEvidence')!=path or any(k in enemy for k in ('specialBaseDamage','specialSource')):
        raise ValueError('Later ice requires its all-identity damage evidence')
    p=load(ROOT/path);rules=p['rules'];ids=[12,93,99,110,117,120,132,136,137,138,139,141,150,155,161,169,175]
    if p['romSha256']!=SHA256 or p['scopeRevision']!='behavior3-all-decoded-identities-original-damage' or \
            p['behavior']!=3 or p['verifiedEnemyIds']!=ids or enemy['id'] not in ids or \
            enemy['behaviorByte']!=3 or extract_enemy(reader,enemy['id'])['remainingBytes'][1]!=3:
        raise ValueError('Ice identity or CPU scope differs')
    if rules['ordinaryFormula']!='(enemyId-12)*3+13' or \
            rules['bossTable']!='A906+2*(enemyId-137), original decoded domain0..176' or \
            rules['baseDamageIgnoresArmorAndStamina'] is not True or \
            rules['status04DoublesBeforeUint16Truncation'] is not True or rules['hpFloor']!=0 or \
            rules['directDamageBeforeDeathFlag'] is not True:
        raise ValueError('Ice formula, HP or modifier rule differs')
    required={(9,0xaa08,0x3a),(9,0xa906,80),(9,0xab0a,0x4f),(9,0xab6d,0x29)}
    if {(s['module'],s['cpuAddress'],s['length'])for s in p['sources']}!=required:
        raise ValueError('Ice lacks dispatch/table/status/HP sources')
    for span in p['sources']:checked_span(reader,span)
    actual=extract_enemy_ice_base(reader,enemy['id'])
    if p['identityBases'][str(enemy['id'])]!=actual or any(enemy.get(k)!=v for k,v in actual.items()):
        raise ValueError('Ice base differs from original CPU scope')

def validate_world_status16(reader,enemy):
    """Original behavior6's hit/miss and priority, with no guessed cure/effect name."""
    from forensics.fengshen246 import extract_enemy
    path='game-data/provenance/world-enemy-status16.json'
    if enemy.get('behaviorEvidence')!=path or any(k in enemy for k in ('iceBaseDamage','specialBaseDamage')):
        raise ValueError('Behavior6 requires its status10 evidence, not damage data')
    p=load(ROOT/path);r=p['rules'];ids=[39,51,63,78,80,95,104,129,168,174]
    if p['romSha256']!=SHA256 or p['scopeRevision']!='behavior6-state10-hit-miss-priority-and-no-input' or \
            p['behavior']!=6 or p['verifiedEnemyIds']!=ids or enemy['id'] not in ids or enemy['behaviorByte']!=6 or \
            extract_enemy(reader,enemy['id'])['remainingBytes'][1]!=6:
        raise ValueError('Behavior6 status10 scope or identity differs')
    if r['specialChoice']!='(random &127)<41' or r['secondaryHit']!='((random>>1)&63)<10' or \
            r['singleTarget'] is not True or r['selectedSpecialMissIsNotPhysical'] is not True or r['statusMask']!=16 or \
            r['legalReplaceMasks']!=[0,2,4,8,16] or r['hpChangedByStatus'] is not False or r['rawDamage']!=0 or \
            r['inputSkipMasks']!=[8,16,32] or r['defeatCondition']!='all present HP zero OR all present status bit10; dead+10 is not all bit10':
        raise ValueError('Behavior6 status10 choice, priority or command rule differs')
    required={(9,0x8dc4,12),(9,0x8de9,0xc9),(9,0xa956,0x2e),(9,0xa0d2,0x58),(9,0xa63a,0x65),(9,0xb68e,0x77)}
    if {(s['module'],s['cpuAddress'],s['length'])for s in p['sources']}!=required:
        raise ValueError('Behavior6 lacks hit/miss/priority/defeat/no-input sources')
    for span in p['sources']:checked_span(reader,span)

def validate_world_clinic_definition(reader,clinic):
    path='game-data/provenance/world-clinic-rules.json'
    p=load(ROOT/path)
    required={(2,0xc86f,153),(2,0x86cd,41),(2,0xc550,32),(2,0xcabb,230),
        (2,0xca61,41),(2,0xc7c6,54),(2,0xee8b,98)}
    if clinic['evidence']!=path or p['romSha256']!=SHA256 or \
            p['kind']!='CONTROLLED_ORIGINAL_MENU_AND_ACTIVE_CPU_NOT_NORMAL_ANDROID' or \
            p['activePrgBanks']!=[8,9,10,11] or p['activeCpuSha256']!=digest(reader.read(2,0x8000,32768)) or \
            not p['doctorActiveCpuSha256s'] or any(h!=p['activeCpuSha256']for h in p['doctorActiveCpuSha256s']) or \
            {(s['module'],s['cpuAddress'],s['length'])for s in p['sources']}!=required:
        raise ValueError('Medical service lacks actual original menu/active CPU evidence')
    for span in p['sources']:checked_span(reader,span)
    for kind,count in [('revival',15),('care',27)]:
        raw=(ROOT/p[kind+'ExpectedPath']).read_bytes()
        if digest(raw)!=p[kind+'ExpectedSha256'] or len(raw.splitlines())-1!=count or p[kind+'CaseCount']!=count:
            raise ValueError('Medical original menu expectations differ')
    constants={'deadMask':32,'recoveredHp':1,'recoveredStatus':1,'feeDenominator':100,
        'minimumFee':1,'moneyLimit':999999}
    treatments=[{'id':'poison','name':'中毒','statusMask':2,'price':2},
        {'id':'confusion','name':'錯亂','statusMask':4,'price':3}]
    if any(clinic[k]!=v for k,v in constants.items()) or \
            any(p['rules']['revival'][k]!=v for k,v in constants.items()) or \
            p['rules']['treatment']['options']!=treatments or p['rules']['treatment']['thirdMenuOption']!='CANCEL_NOT_ANOTHER_CONDITION':
        raise ValueError('Medical rule differs from original target/price/status policy')
    role={'REVIVAL':('revival',1,[]),'TREATMENT':('care',0,treatments)}.get(clinic['kind'])
    if role is None or clinic['mapId']!=20 or clinic['npcId']!=f'rom.npc.20.{role[1]}' or \
            clinic['id']!=f'rom.clinic.{clinic["callerMapId"]}.{role[0]}' or clinic['treatments']!=role[2]:
        raise ValueError('Medical definition, actor or available operations differ')
    return p

def validate_continent_foot_bridges(reader,proof_path):
    proof=load(ROOT/proof_path)
    original=extract_map(reader,16)
    required={(0,0xcab5,117),(0,0xcf44,22),(0,0xd031,71),(0,0xd257,18),
              (0,0xcaa5,16),(0,0xce42,16)}
    if proof_path!='game-data/provenance/world-continent-bridges.json' or \
            (proof['romSha256'],proof['mapId'],proof['tilesetId'],proof['scopeRevision'])!= \
            (SHA256,16,1,'continent16-foot-bridges-no-transport-state') or \
            original['tilesetId']!=1 or proof['mapGridSha256']!=original['gridSha256'] or \
            proof['activeCpuSha256']!=digest(reader.read(0,0x8000,32768)) or \
            proof['standingClasses']!=[0,2,15,16] or \
            proof['sourceEdges']!={'15':['LEFT','RIGHT'],'16':['UP','DOWN']} or proof['targetEdges'] or \
            proof['transportFlags']!={'6812':0,'6813':0,'6815':0} or \
            proof['cpuCaseCount']!=144 or proof['cpuFailures']!=0 or \
            {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
        raise ValueError('Continent bridges lack original foot/direction evidence')
    for span in proof['sources']:checked_span(reader,span)
    raw=(ROOT/proof['cpuExpectedPath']).read_bytes()
    if digest(raw)!=proof['cpuExpectedSha256'] or len(raw.splitlines())!=145:
        raise ValueError('Continent bridge CPU expectations differ')
    return proof

def validate_world_exit_geometry(scene,result):
    """Match the existing loader's gate-open placement check before signing.

    A ROM exit-table row is not permission to stand on a physical wall. Only
    independently reviewed removable actors are cleared for this static check.
    """
    maps={m['id']:(scene if m['scene']=='scene.json' else json.loads(result[m['scene']]))for m in scene['maps']}
    cleared={mid:{b['cell'][1]*maps[mid]['width']+b['cell'][0]for b in scene.get('sceneBarriers',[])if b['mapId']==mid}for mid in maps}
    def valid(mid,cell):
        if mid not in maps:return False
        m=maps[mid];x,y=cell
        if not(0<=x<m['width']and 0<=y<m['height']):return False
        i=y*m['width']+x;c=m['collision'][i];profile=m.get('terrain',{}).get('tileset')
        if profile==3 and c not in {0,2,3,4,5,6,7,8,9,10,11,13,17,18,19,20,21,23}:return False
        if profile==4 and c not in {0,2,4,5,6,7,8}:return False
        if c not in m['walkableClasses']and i not in m.get('transitionCells',[]):return False
        if i not in m['enabledCells']or i in set(m.get('dynamicObjectCells',[]))-cleared[mid]:return False
        if any(a<x<=c and b<y<=d for a,b,c,d in m.get('unavailableRegions',[])):return False
        return True
    for row in scene['exits']:
        if not valid(row['fromMapId'],row['trigger'])or not valid(row['toMapId'],row['spawn']):
            raise ValueError(f"Invalid exit geometry {row['fromMapId']}:{row['trigger']} -> {row['toMapId']}:{row['spawn']}; original state evidence is required")

def validate_world_tree_contact(reader,path):
    if path!='game-data/provenance/world-tree-contact.json':raise ValueError('Unknown actor contact evidence')
    proof=load(ROOT/path)
    required={(0,0xa973,77),(0,0xc68a,258),(0,0xc78c,26),(0,0xc894,100),(0,0xc9ea,35),(0,0xaa1f,41)}
    if proof['romSha256']!=SHA256 or proof['scopeRevision']!='continent-E7-E8-original-contact-foot-D6-tree107' or \
            proof['cpuCaseCount']!=2384 or proof['cpuFailures']!=0 or \
            proof['activeCpuSha256']!=digest(reader.read(0,0x8000,32768)) or \
            {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required or \
            proof['rules']!={'walkerId':214,'toMapId':107,'spawn':[7,14],'preserveArrivalDirection':True,
                'resetEncounterSteps':True,'return':[16,169,149],'whenRemoved':'ordinary_floor_no_contact_entry',
                'triggerBeforeCompletedStep':True}:
        raise ValueError('Tree contact destination, timing or actor rule differs')
    for span in proof['sources']:checked_span(reader,span)
    raw=(ROOT/proof['cpuExpectedPath']).read_bytes()
    if digest(raw)!=proof['cpuExpectedSha256'] or len(raw.splitlines())!=2385:
        raise ValueError('Original actor contact CPU table differs')
    from forensics.fengshen246 import extract_npcs
    records={n['entityByte']:n for n in extract_npcs(reader,16)['records']}
    if [b['actorId']for b in proof['bindings']]!=[231,232]:raise ValueError('Unreviewed actor contact')
    for b in proof['bindings']:
        original=records[b['actorId']];record=checked_span(reader,b['recordSource'])
        cell=[(original[k]-120)//16 for k in ('xCandidate','yCandidate')]
        if b['recordSource']!=original['range'] or b['mapId']!=16 or b['cell']!=cell or \
                b['removedFlagId']!=f'rom.map.16.flag.{record[13]}':
            raise ValueError('Original actor contact presence or cell differs')
    return proof

def export_world_from_base(payload,evidence,provenance_path,target_pin):
    """Batch scene/service overlays on reviewed media; no raw captures in CI inputs."""
    if digest(payload['manifest.json'])!=evidence['baseManifestSha256']:
        raise ValueError('World export requires reviewed base content')
    reader=iteration_reader();result=dict(payload)
    encoded=lambda value:(json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf-8')
    scene=json.loads(result['scene.json']);known={m['id'] for m in scene['maps']}
    for span in evidence['ruleRanges']:checked_span(reader,span)
    for recipe in evidence.get('atlasCorrections',[]):
        mid=recipe['mapId'];original=extract_map(reader,mid)
        if mid not in known or original['gridSha256']!=recipe['gridSha256']:
            raise ValueError('Atlas correction lacks existing map/grid evidence')
        name=next(m['atlas'] for m in scene['maps'] if m['id']==mid)
        if digest(result[name])!=recipe['previousAssetSha256']:
            raise ValueError('Atlas correction source differs')
        result[name]=scoped_map_atlas(reader,original,recipe['palette'],evidence['emulatorRgb'])
    for recipe in evidence.get('maps',[]):
        mid=recipe['mapId'];original=extract_map(reader,mid)
        if mid in known or original['gridSha256']!=recipe['gridSha256']:
            raise ValueError('World map overlaps or differs from pinned ROM')
        c=original['collisionCandidate'];classes=reader.read(c['module'],c['cpuAddress'],256)
        grid=[t for row in original['grid'] for t in row];collision=[classes[t] for t in grid]
        if mid==20 and evidence.get('clinics'):
            resource=load(ROOT/evidence['evidence']['clinicResources']['path'])
            scope=resource['collisionScope']
            if digest((ROOT/evidence['evidence']['clinicResources']['path']).read_bytes())!=evidence['evidence']['clinicResources']['sha256'] or \
                    resource['romSha256']!=SHA256 or original['tilesetId']!=2 or set(collision)!={0,1,2,5} or \
                    recipe['walkableClasses']!=[0,2,5] or recipe.get('directionalCollision') or \
                    scope['kind']!='ORIGINAL_CONTROLLER_REAL_ROOM_EDGES_NOT_TOWN_DIRECTIONAL_PROFILE' or \
                    scope['sampleCount']!=14 or len(scope['samples'])!=14:
                raise ValueError('Medical room must preserve its actual plain-room collision scope')
            previous=[13,5]
            for sample in scope['samples']:
                x,y=sample['before'];dx,dy={'UP':(0,-1),'DOWN':(0,1),'LEFT':(-1,0),'RIGHT':(1,0)}[sample['key']]
                target=[x+dx,y+dy];blocked=target in [[13,4],[3,6]] or collision[target[1]*original['width']+target[0]]==1
                if sample['mapId']!=20 or sample['before']!=previous or sample['after']!=(previous if blocked else target):
                    raise ValueError('Medical room movement differs from actual original controller samples')
                previous=sample['after']
        transitions=set()
        for exit in evidence['exits']:
            for field,idfield in [('trigger','fromMapId'),('spawn','toMapId')]:
                if exit[idfield]==mid:transitions.add(exit[field][1]*original['width']+exit[field][0])
        allowed=recipe['walkableClasses']
        forest=recipe.get('forestCollisionEvidence')
        if forest:
            proof=load(ROOT/forest)
            required={(0,0xca98,29),(0,0xcdc0,41),(0,0xce35,29),(0,0xd197,60),(0,0xd257,18)}
            tree=forest=='game-data/provenance/world-tree107-terrain.json'
            if tree:
                binding=next((b for b in proof['maps']if b['mapId']==mid),None)
                if mid not in (107,108,109,110) or binding is None or binding['gridSha256']!=original['gridSha256'] or \
                        original['tilesetId']!=5 or set(collision)!={0,1} or allowed!=[0] or \
                        proof['scopeRevision']!='tree107-110-foot0-wall1-requested-direction' or \
                        proof['romSha256']!=SHA256 or proof['cpuCaseCount']!=144 or proof['cpuFailures']!=0 or \
                        proof['sourceEdges']!={} or proof['targetEdges']!={} or \
                        recipe['palette']!=binding['palette']['palette'] or \
                        {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
                    raise ValueError('Tree floor lacks actual forest dispatcher, grid or palette')
            elif forest!='game-data/provenance/world-forest101-terrain.json' or mid!=101 or original['tilesetId']!=5 or \
                    proof['romSha256']!=SHA256 or proof['gridSha256']!=original['gridSha256'] or \
                    proof['scopeRevision']!='map101-foot-mode-zero-full-rts-dispatch' or \
                    proof['cpuCaseCount']!=144 or proof['cpuFailures']!=0 or proof['mode']!=0 or \
                    proof['sourceEntry']!=0xcdc0 or proof['targetEntry']!=0xd197 or \
                    set(collision)!={0,1,3,7,8,9} or allowed!=[0,3,7,8,9] or \
                    proof['sourceEdges']!={'3':['LEFT','RIGHT']} or proof['targetEdges']!={} or \
                    {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
                raise ValueError('Forest foot movement lacks its complete original RTS-dispatch scope')
            for span in proof['sources']:checked_span(reader,span)
            if proof['activeCpuSha256']!=digest(reader.read(0,0x8000,0x8000)) or \
                    digest((ROOT/proof['cpuExpectedPath']).read_bytes())!=proof['cpuExpectedSha256']:
                raise ValueError('Forest original CPU expectations or active bank differ')
        data={'schemaVersion':1,'version':target_pin['contentVersion'],'channel':'development',
            'originalMapId':mid,'width':original['width'],'height':original['height'],
            'tileSize':16,'logicalWidth':256,'logicalHeight':240,'grid':grid,'collision':collision,
            'walkableClasses':allowed,'transitionCells':sorted(transitions),
            'enabledCells':[i for i,c in enumerate(collision) if c in allowed or i in transitions],
            'spawn':recipe['spawn'],'dynamicObjectCells':recipe.get('npcCells',[]),
            'source':{'romSha256':SHA256,'mapGridSha256':original['gridSha256'],'evidence':provenance_path},
            'limitations':recipe.get('limitations',[])}
        if forest:
            data['sourceEdges']=proof['sourceEdges'];data['targetEdges']=proof['targetEdges']
        if recipe.get('unavailableRegions'):
            proof=load(ROOT/recipe['unavailableRegionEvidence'])
            if proof['romSha256']!=SHA256 or proof['zone8']['mapId']!=mid:
                raise ValueError('Unavailable encounter regions lack original map evidence')
            for span in proof['zone8']['sources']:checked_span(reader,span)
            if recipe['unavailableRegions']!=proof['zone8']['unavailableRegions']:
                raise ValueError('Unavailable regions must retain original unimplemented partitions')
            data['unavailableRegions']=recipe['unavailableRegions']
        if recipe.get('directionalCollision'):
            # Shared tileset-0 town edges have one original dispatch, not one
            # new collision implementation per village.
            if original['tilesetId']!=0:raise ValueError('Town directional profile on another tileset')
            town=extract_town_shops(reader)
            for field in ('sourceEdges','targetEdges'):data[field]=town[field]
        if recipe.get('terrain'):
            terrain=recipe['terrain'];proof=load(ROOT/terrain['evidence'])
            if original['tilesetId']!=terrain['tileset'] or proof['romSha256']!=SHA256:
                raise ValueError('Terrain profile differs from original tileset')
            if terrain['tileset']==4:
                for field in ('sourceDispatch','targetDispatch','selector','targetSelector'):
                    checked_span(reader,proof['profile'][field])
            elif terrain['tileset']==3 and proof.get('scopeRevision')=='map23-ground-mode':
                if original['gridSha256']!=proof['gridSha256'] or proof['mode']!=0 or proof['bridgeState']!=0:
                    raise ValueError('Ground profile lacks current map/plane evidence')
                for span in proof['sources']:checked_span(reader,span)
                if set(collision)!={int(c) for c in proof['classCounts']} or set(allowed)!=set(collision)-{1,14}:
                    raise ValueError('Ground profile classes differ from original map')
            elif terrain['tileset']==3 and proof.get('scopeRevision') in ('map70-first-hall-ground-mode','map60-second-hall-ground-mode'):
                if mid!=proof['mapId'] or original['gridSha256']!=proof['gridSha256'] or proof['mode']!=0 or proof['bridgeState']!=0 or proof['testCount']!=576 or proof['failures']!=0:
                    raise ValueError('Hell hall lacks its original ground matrix')
                for span in proof['sources']:checked_span(reader,span)
                if set(collision)!={int(c) for c in proof['classCounts']} or set(allowed)!=set(collision)-{1}:
                    raise ValueError('Hell hall ground classes differ')
            elif terrain['tileset']==3 and proof.get('scopeRevision') in ('hell-halls61-through68-ground-and-upper-plane-bridge-zero',
                    'seventh-hall-side-rooms-ground-and-upper-plane-bridge-zero','map86-rebirth-ground-and-upper-plane-bridge-zero'):
                validate_world_hall_batch_terrain(reader,mid,terrain['evidence'])
                if set(allowed)!=set(collision)-{1}:
                    raise ValueError('Hell batch must retain both observed planes, never unknown wall classes')
            else:raise ValueError('Terrain execution profile not implemented')
            data['terrain']=terrain
        result[f'scene{mid}.json']=encoded(data)
        result[f'tiles{mid}.png']=scoped_map_atlas(reader,original,recipe['palette'],evidence['emulatorRgb'])
        scene['maps'].append({'id':mid,'scene':f'scene{mid}.json','atlas':f'tiles{mid}.png'});known.add(mid)
    for exit in evidence.get('exits',[]):
        if exit['fromMapId'] not in known or exit['toMapId'] not in known or exit['confidence']!='VERIFIED':
            raise ValueError('World transition lacks scoped source/target evidence')
        raw=checked_span(reader,exit['source'])
        if exit['kind']=='RETURN_TO_CALLER':
            if list(raw)!=exit['trigger']+[254,0,0]:raise ValueError('Original return record differs')
        elif exit['kind']=='COLLISION_ENTRY':
            origin=extract_map(reader,exit['fromMapId']);c=origin['collisionCandidate']
            x,y=exit['trigger'];tile=origin['grid'][y][x]
            klass=reader.read(c['module'],c['cpuAddress']+tile)[0]
            if klass!=exit['collisionClass'] or exit['toMapId']!=klass-exit['dispatchSubtract']:
                raise ValueError('Original indoor collision dispatch differs')
        elif exit['kind']=='ACTOR_CONTACT':
            proof=validate_world_tree_contact(reader,exit['contactEvidence'])
            binding=next((b for b in proof['bindings']if b['actorId']==exit['contactActorId']),None)
            if binding is None or exit['fromMapId']!=16 or exit['toMapId']!=107 or \
                    exit['source']!=binding['recordSource'] or exit['trigger']!=binding['cell'] or \
                    exit['spawn']!=[7,14] or exit.get('triggerMode')!='ACTOR_CONTACT' or \
                    exit.get('preserveArrivalDirection') is not True or exit.get('resetEncounterSteps') is not True or \
                    not any(b['id']==f'rom.barrier.16.{exit["contactActorId"]}' and b['cell']==exit['trigger'] and
                        b['removedFlagId']==binding['removedFlagId']for b in scene.get('sceneBarriers',[])):
                raise ValueError('Actor contact must retain original filtered barrier and destination')
        elif exit['kind']=='EXIT_RECORD':
            if list(raw)!=exit['trigger']+[exit['toMapId']]+exit['spawn']:
                raise ValueError('Original exit record differs')
        elif exit['kind']=='EDGE_RECORD':
            original=extract_map(reader,exit['fromMapId']);x,y=exit['trigger'];direction=exit.get('direction')
            boundary=(direction=='LEFT' and x==0 or direction=='RIGHT' and x==original['width']-1 or
                direction=='UP' and y==0 or direction=='DOWN' and y==original['height']-1)
            if raw[0]!=255 or list(raw[2:])!=[exit['toMapId']]+exit['spawn'] or not boundary:
                raise ValueError('Original edge return or observed boundary differs')
            if exit.get('triggerMode')!='EDGE' or not exit.get('runtimeEvidence'):
                raise ValueError('Edge requires observed departure, not swapped coordinates')
        else:raise ValueError('Unsupported transition kind needs original evidence')
        scene['exits'].append({k:exit[k] for k in ['fromMapId','trigger','toMapId','spawn','confidence','arrivalDirection']}|
            {'source':exit['source'],'evidence':exit['contactEvidence']if exit['kind']=='ACTOR_CONTACT'else provenance_path}|
            {k:exit[k] for k in ('resetEncounterSteps','captureCaller','returnToCaller','triggerMode','direction','preserveArrivalDirection','contactActorId') if k in exit})
        if exit.get('preserveArrivalDirection'):
            source=exit['arrivalDirectionSource']
            checked_span(reader,source)
            if source.get('module')!=0 or source.get('cpuAddress')!=0xaa1f or source.get('length',0)<41:
                raise ValueError('Preserved facing requires the original map reconstruction path')
        for field,idfield in [('trigger','fromMapId'),('spawn','toMapId')]:
            mid=exit[idfield]
            if mid==114:raise ValueError('Opening scene overlay requires explicit review')
            name=next(m['scene'] for m in scene['maps'] if m['id']==mid);data=json.loads(result[name])
            x,y=exit[field];index=y*data['width']+x
            if not 0<=x<data['width'] or not 0<=y<data['height']:raise ValueError('Exit outside map')
            data['transitionCells']=sorted(set(data.get('transitionCells',[]))|{index})
            data['enabledCells']=sorted(set(data['enabledCells'])|{index});result[name]=encoded(data)
    extend_world_mechanisms(reader,scene,evidence.get('mechanisms',[]),provenance_path)
    for patch in evidence.get('exitContextUpdates',[]):
        matches=[e for e in scene['exits'] if all(e[k]==patch[k] for k in ('fromMapId','trigger','toMapId','spawn'))]
        if len(matches)!=1:raise ValueError('Caller patch must identify one existing transition')
        checked_span(reader,patch['source'])
        if patch['fromMapId'] in (17,18,19,22):
            if list(checked_span(reader,patch['source']))!=patch['trigger']+[254,0,0] or patch.get('returnToCaller') is not True:
                raise ValueError('Caller return lacks original FE record')
        elif patch['fromMapId'] not in range(16) or patch.get('captureCaller') is not True:
            raise ValueError('Caller entry outside original villages')
        matches[0].update({k:patch[k] for k in ('captureCaller','returnToCaller') if k in patch})
    for update in evidence.get('itemUpdates',[]):
        matches=[i for i in scene['items'] if i['id']==update['id']]
        if len(matches)!=1 or update['id']!='rom.medicine.6':raise ValueError('Unreviewed item update')
        rule=update['antidoteUse']
        if rule!={'mapMenu':True,'cureStatusMask':2,'confirmationConsumption':1,'extraConsumptionWhenCured':1,
                'evidence':'game-data/provenance/world-status.json'}:raise ValueError('Antidote rule differs from checked dispatch')
        status=load(ROOT/rule['evidence'])
        if status['romSha256']!=SHA256:raise ValueError('Wrong status evidence fingerprint')
        for span in status['ranges']:checked_span(reader,span)
        matches[0]['antidoteUse']=rule;matches[0]['description']=update['description']
        source=matches[0]['source'];source['useEvidence']=rule['evidence']
        source['verifiedFields']=source.get('verifiedFields',[])+['mapUseConsumption','mapUsePoisonRemoval']
        source['remainingUnknown']=['Battle antidote use/order not implemented']
    overlay=evidence.get('combatOverlay')
    if overlay:
        combat=json.loads(result['combat.json'])
        for enemy in overlay.get('enemies',[]):
            original=extract_enemy(reader,enemy['id'])
            if enemy['id'] in {x['id'] for x in combat['enemies']}:raise ValueError('Enemy overlay overlaps existing ID')
            if any(enemy[k]!=original[k] for k in ('hp','attack','defense','experienceReward','moneyReward')):
                raise ValueError('Enemy overlay stats differ from ROM')
            tail=original['remainingBytes']
            if (enemy['behaviorByte'],enemy['hitByte'],overlay['enemyAgility'][str(enemy['id'])])!=(tail[1],tail[2],tail[0]):
                raise ValueError('Enemy overlay behavior/agility differs')
            category={0:'medicine',2:'weapon',3:'armor'}.get(tail[4])
            item_id='rom.item.0' if category=='weapon' and tail[5]==0 else f'rom.{category}.{tail[5]}'
            if category is None or enemy.get('loot')!={'category':category,'itemId':item_id,'threshold':tail[3]}:
                raise ValueError('Enemy overlay loot differs')
            checked_span(reader,enemy['source'])
            if enemy['behaviorByte'] not in (1,2,4) and any(k in enemy for k in ('specialBaseDamage','specialSource','specialDamageEvidence')):
                raise ValueError('Special damage cannot be assigned to an unevidenced behavior')
            if enemy['behaviorByte']==3:
                from forensics.fengshen246 import extract_enemy_ice_base
                if enemy['id']>144 or 'iceDamageEvidence' in enemy:
                    validate_world_ice_identities(reader,enemy)
                original_ice=extract_enemy_ice_base(reader,enemy['id'])
                if enemy.get('iceBaseDamage')!=original_ice['iceBaseDamage']:
                    raise ValueError('Ice damage differs from original behavior dispatch')
                if enemy['iceSource']!=original_ice['iceSource']:
                    # Historical boss recipes use equivalent spans with their own
                    # description; address/length/hash remain exact, not wording.
                    for key in ('offset','length','sha256','module','cpuAddress'):
                        if enemy['iceSource'].get(key)!=original_ice['iceSource'][key]:
                            raise ValueError('Ice evidence span differs from original dispatch')
                checked_span(reader,enemy['iceSource'])
            elif enemy['behaviorByte']==1:
                validate_world_behavior1(reader,enemy)
            elif enemy['behaviorByte'] in (2,4):
                validate_world_single_special(reader,enemy)
            elif enemy['behaviorByte']==6:
                validate_world_status16(reader,enemy)
            elif enemy['behaviorByte']==8:
                if enemy.get('behaviorEvidence')!='game-data/provenance/world-status-bit8.json' or 'iceBaseDamage' in enemy:
                    raise ValueError('Behavior8 requires its scoped status evidence')
                proof=load(ROOT/enemy['behaviorEvidence'])
                rules=proof['rules']
                if (proof['romSha256'],proof['scopeRevision'],proof['enemyId'],proof['behavior'],proof['statusMask']) != \
                        (SHA256,'behavior8-state08-and-no-eligible-command-progress',29,8,8) or \
                        rules['legalReplaceMasks']!=[0,4,8] or rules['hpChangedByStatus'] is not False or \
                        rules['commandSkipMask']!=56 or rules['battleExitClearMask']!=8 or \
                        rules['battleExitKeeps']!=[2,4,16,32]:
                    raise ValueError('Behavior8 status rules differ from original dispatch')
                required={(9,0x8dc4,12),(9,0x8de9,201),(9,0xa0d2,88),(9,0xb68e,119),
                          (9,0xa69f,73),(9,0x818e,126),(9,0x9282,46)}
                if {(s['module'],s['cpuAddress'],s['length']) for s in proof['sources']}!=required:
                    raise ValueError('Behavior8 lacks command progression and exit evidence')
                for span in proof['sources']:checked_span(reader,span)
            elif enemy['behaviorByte'] not in (0,7,9) or 'iceBaseDamage' in enemy:
                raise ValueError('Enemy behavior requires implementation and evidence')
        for zone in overlay.get('zones',[]):
            from forensics.fengshen246 import extract_encounter_groups
            original_groups=extract_encounter_groups(reader,zone['id'])['groups']
            if [(g['id'],g.get('sourceEntities',g['entities'])) for g in zone['groups']]!=[(g['id'],g['entities']) for g in original_groups]:
                raise ValueError('Encounter overlay must retain the complete original zone group table')
            for group,original_group in zip(zone['groups'],original_groups):
                if not original_group['duplicateSlots']:
                    if group['entities']!=original_group['entities']:raise ValueError('Unexpected group normalization')
                    continue
                proof=load(ROOT/zone['duplicateSlotEvidence'])
                if proof['romSha256']!=SHA256 or proof['zoneId']!=zone['id']:
                    raise ValueError('Repeated-slot evidence belongs to another zone')
                for span in proof['sources']:checked_span(reader,span)
                instances={}
                for row in original_group['entities']:
                    if row['slot'] in instances and instances[row['slot']]!=row:
                        raise ValueError('Conflicting repeated slots need their actual loader semantics')
                    instances.setdefault(row['slot'],row)
                if group['entities']!=list(instances.values()):
                    raise ValueError('Repeated-slot runtime instances differ from original loader')
            if zone['rectangles']:
                raw=checked_span(reader,zone['rectangleSource'])
                if raw!=bytes([zone['id']]+[v for rect in zone['rectangles'] for v in rect]+[0]):raise ValueError('Encounter rectangles differ')
                if zone['mapId'] not in (16,23,25) or zone['randomThreshold']!=16 or zone['randomGate']!='LOW':
                    raise ValueError('Unreviewed rectangular encounter dispatcher')
            else:
                mid=zone['mapId']
                if mid not in known or reader.read(0,0xee47+mid)[0]!=zone['id'] or zone['id']==255:
                    raise ValueError('Default encounter region differs from original map table')
                checked_span(reader,zone['defaultSource']);checked_span(reader,zone['mapTypeSource'])
                map_type=reader.read(0,0xed87+mid)[0]
                expected_gate={10:(245,'HIGH'),16:(16,'LOW')}.get(map_type)
                if expected_gate!=(zone['randomThreshold'],zone['randomGate']):
                    raise ValueError('Unreviewed default encounter probability branch')
            expected_count=reader.read(1,0xb12d+zone['id'])[0]
            if len(zone['groups'])!=expected_count or [g['id'] for g in zone['groups']]!=list(range(expected_count)):
                raise ValueError('Encounter groups incomplete')
            for group in zone['groups']:
                actual=checked_span(reader,group['range'])
                pairs=[v for entity in group.get('sourceEntities',group['entities']) for v in (entity['slot']+1,entity['sourceType'])]
                if actual!=bytes(pairs+[0]) or any(reader.read(1,0x9ea3+entity['sourceType'])[0]!=entity['enemyId'] for entity in group['entities']):
                    raise ValueError('Encounter members or source mapping differ')
        combat['enemies']+=overlay.get('enemies',[]);combat['zones']+=overlay.get('zones',[])
        for fallback in overlay.get('fallbackZones',[]):
            if fallback['mapId']!=25 or fallback['id']!=1 or fallback['rectangles'] or \
                    checked_span(reader,fallback['defaultSource'])!=b'\x01':raise ValueError('Unreviewed default encounter fallback')
            existing=next(z for z in combat['zones'] if z['mapId']==25 and z['id']==1)
            if any(fallback[k]!=existing[k] for k in ('groups','randomThreshold','randomGate')):
                raise ValueError('Default encounter fallback must reuse full original zone1')
            combat.setdefault('fallbackZones',[]).append(fallback)
        combat['escape']['enemyAgility'].update(overlay.get('enemyAgility',{}))
        for graphic in overlay.get('graphics',[]):
            recipe=evidence['graphics'][graphic['asset']]
            if graphic['origin']+[graphic['width'],graphic['height']]!=recipe['observedRect']:
                raise ValueError('Enemy graphic placement differs from observed rectangle')
        combat['presentation']['graphics']+=overlay.get('graphics',[])
        for boss in overlay.get('bosses',[]):
            if boss['id'] in {b['id'] for b in combat.get('bosses',[])}:raise ValueError('Duplicate story battle')
            if boss.get('entryTrigger'):
                trigger=boss['entryTrigger'];proof=load(ROOT/trigger['evidence'])
                if proof['romSha256']!=SHA256:raise ValueError('Coordinate story ROM differs')
                for span in proof.get('spans',proof.get('sources',[])):checked_span(reader,span)
                if proof.get('guard'):
                    original=proof['guard']['trigger']
                    if (boss['mapId'],trigger['mapId'],trigger['x'],trigger['y'],boss['eventId'])!=(
                            original['mapId'],original['mapId'],*original['cell'],original['eventId']):
                        raise ValueError('Coordinate story trigger differs')
                    if not original['automatic'] or boss['sourceType']!=154:
                        raise ValueError('Guarded chest story source differs')
                    raw=checked_span(reader,boss['npcSource'])
                    if list(raw[0:3])!=[144,1,11]:raise ValueError('Guarded treasure record differs')
                elif proof.get('scopeRevision')=='cave85-script5-little-dragon':
                    rule=proof['rules']
                    if (boss['mapId'],trigger['mapId'],trigger['x'],trigger['y'],boss['eventId'],boss['sourceType'],boss['enemyId'])!=(85,85,2,6,5,158,140) or \
                            (rule['mapId'],rule['triggerCell'],rule['sourceType'],rule['dynamicActorId'])!=(85,[2,6],158,129):
                        raise ValueError('Cave story source/trigger differs')
                    raw=checked_span(reader,boss['actorScriptSource'])
                    if raw!=reader.read(11,0xc4b6,9):raise ValueError('Original cave actor script differs')
                else:raise ValueError('Coordinate story requires verified phase semantics')
                if not boss.get('commitAfterDialogue') or boss['flagId']!=f'rom.map.{boss["mapId"]}.flag.128':
                    raise ValueError('Coordinate story phase or completion differs')
            else:
                raw=checked_span(reader,boss['npcSource'])
                if list(raw[10:14])!=[1,2,boss['eventId'],boss['eventArgument']]:
                    raise ValueError('Original story battle dispatch differs')
                if boss.get('continuation'):
                    proof=load(ROOT/boss['continuation']['evidence']);rule=proof['rules']
                    if proof['romSha256']!=SHA256:raise ValueError('Story continuation ROM differs')
                    for span in proof['sources']:checked_span(reader,span)
                    if any(boss[k]!=rule[k] for k in ('mapId','npcId','eventId','eventArgument','sourceType','enemyId')) or \
                            boss['flagId']!=rule['bossVictoryFlag']:
                        raise ValueError('Story continuation event identity differs')
                    expected={k:rule[k] for k in ('dialogueIds','joinCharacterId','destination','completionFlags')}
                    expected['evidence']=boss['continuation']['evidence']
                    if boss['continuation']!=expected or boss.get('commitAfterDialogue',False):
                        raise ValueError('Story continuation order or state differs')
                elif boss.get('victoryFlags') and boss.get('victoryFlagEvidence')=='game-data/provenance/world-hell-hall-batch-script.json':
                    rule=validate_world_hall_batch_script(reader,boss['mapId'])
                    if any(boss[k]!=rule[k] for k in ('mapId','npcId','eventId','eventArgument','sourceType','enemyId')) or \
                            boss['flagId']!=rule['bossVictoryFlag'] or boss['victoryFlags']!=rule['victoryFlags'] or \
                            boss.get('commitAfterDialogue',False):
                        raise ValueError('Hell batch finalization fields differ from original event')
                elif boss.get('victoryFlags'):
                    proof=load(ROOT/boss['victoryFlagEvidence']);rule=proof['rules']
                    if proof['romSha256']!=SHA256 or proof['scopeRevision'] not in ('first-hall-event20-qin-victory-removes-246','second-hall-event21-chu-victory-removes-246'):
                        raise ValueError('Unknown battle map flags')
                    for span in proof['sources']:checked_span(reader,span)
                    if any(boss[k]!=rule[k] for k in ('mapId','npcId','eventId','eventArgument','sourceType','enemyId')) or \
                            boss['flagId']!=rule['bossVictoryFlag'] or boss['victoryFlags']!=rule['victoryFlags'] or boss.get('commitAfterDialogue',False):
                        raise ValueError('Hell hall battle flags or timing differ')
                    if rule['eventArgument']!=2 or checked_span(reader,rule['sourceTypeInstruction'])!=bytes([0xa9,rule['sourceType']]) or \
                            rule['sourceTypeInstruction']['cpuAddress']!=reader.word(10,0xd1e3+2*rule['eventId'])+12 or \
                            checked_span(reader,rule['setDialogueAndEventFlag'])!=bytes([0xa9,rule['repeatMessage'],0x20,0x78,0xd3]):
                        raise ValueError('Hell hall source or message/event instruction differs')
                elif boss['flagId']!=f'rom.event.{boss["mapId"]}.{boss["eventId"]}.{boss["eventArgument"]}':
                    raise ValueError('Story flag must retain original event identity')
            if boss['group']['entities']!=[{'slot':3,'enemyId':boss['enemyId']}] or reader.read(1,0x9ea3+boss['sourceType'])[0]!=boss['enemyId']:
                raise ValueError('Original story enemy source differs')
            for span in boss['ruleSources']:checked_span(reader,span)
            combat.setdefault('bosses',[]).append(boss)
        combat['presentation'].setdefault('blackBackgroundEnemyIds',[]).extend(overlay.get('blackBackgroundEnemyIds',[]))
        combat['presentation'].setdefault('horizons',[]).extend(overlay.get('horizons',[]))
        for hit in overlay.get('weaponHits',[]):
            if checked_span(reader,hit['source'])[0]!=hit['threshold'] or reader.read(9,0x9b41+hit['originalId'])[0]!=hit['threshold']:
                raise ValueError('Original weapon hit threshold differs')
            combat['physicalRules']['weaponHitThreshold'][str(hit['originalId'])]=hit['threshold']
        extend_world_characters(reader,scene,combat,evidence.get('additionalCharacters',[]),overlay)
        result['combat.json']=encoded(combat)
    for patch in evidence.get('sceneCapabilityUpdates',[]):
        if patch.get('kind')=='FOREST_REQUEST_DIRECTION':
            proof=load(ROOT/patch['evidence']);required={(0,0xca98,29),(0,0xcdc0,41),(0,0xce35,29),(0,0xd197,60),(0,0xd257,18)}
            if patch['evidence']!='game-data/provenance/world-forest101-direction.json' or patch['mapId']!=101 or \
                    proof['romSha256']!=SHA256 or proof['mapId']!=101 or \
                    proof['scopeRevision']!='forest101-actual-requested-direction-97' or \
                    proof['cpuCaseCount']!=144 or proof['cpuFailures']!=0 or \
                    proof['sourceEdges']!={'3':['LEFT','RIGHT']} or \
                    proof['targetEdges']!={'3':['LEFT','RIGHT'],'7':['LEFT','RIGHT']} or \
                    {(s['module'],s['cpuAddress'],s['length'])for s in proof['sources']}!=required:
                raise ValueError('Forest request direction requires its scoped original CPU evidence')
            for span in proof['sources']:checked_span(reader,span)
            raw=(ROOT/proof['cpuExpectedPath']).read_bytes()
            if digest(raw)!=proof['cpuExpectedSha256'] or len(raw.splitlines())!=145 or \
                    proof['activeCpuSha256']!=digest(reader.read(0,0x8000,32768)):
                raise ValueError('Forest direction CPU expectations differ')
            name=next(m['scene']for m in scene['maps']if m['id']==101);data=json.loads(result[name])
            if data['source']['mapGridSha256']!=proof['gridSha256'] or data['walkableClasses']!=[0,3,7,8,9] or \
                    data['sourceEdges']!=proof['sourceEdges'] or data['targetEdges']!={}:
                raise ValueError('Forest correction parent differs from fixed original grid/edges')
            data['targetEdges']=proof['targetEdges'];data['source']['directionEvidence']=patch['evidence']
            result[name]=encoded(data);continue
        if patch.get('kind')=='CONTINENT_FOOT_BRIDGES':
            proof=validate_continent_foot_bridges(reader,patch['evidence'])
            if patch['mapId']!=16 or patch['implementedCapabilities']!=['FOOT_BRIDGE15','FOOT_BRIDGE16','ORIGINAL_ZONE16'] or \
                    not overlay or not any(z['mapId']==16 and z['id']==16 for z in overlay['zones']):
                raise ValueError('Continent bridge change must retain its actual full encounter zone')
            name=next(m['scene']for m in scene['maps']if m['id']==16);data=json.loads(result[name])
            if data['walkableClasses']!=[0,2] or data.get('sourceEdges') or data.get('targetEdges') or data.get('terrain'):
                raise ValueError('Continent bridge parent differs from reviewed foot state')
            data['walkableClasses']=proof['standingClasses']
            data['sourceEdges']=proof['sourceEdges'];data['targetEdges']=proof['targetEdges']
            data['enabledCells']=sorted(set(data['enabledCells'])|{i for i,c in enumerate(data['collision'])if c in (15,16)})
            data.setdefault('source',{})['footBridgeEvidence']=patch['evidence']
            result[name]=encoded(data)
            continue
        if patch['mapId']!=25 or not overlay:raise ValueError('Scene capability update lacks implemented encounters')
        zone=next(z for z in overlay['zones'] if z['id']==4)
        name=next(m['scene'] for m in scene['maps'] if m['id']==patch['mapId']);data=json.loads(result[name])
        if data.get('unavailableRegions')!=zone['rectangles'] or patch['implementedCapabilities']!=['ENEMY10_POISON','FIELD_POISON','MAP_ANTIDOTE','FIELD_DEFEAT']:
            raise ValueError('Scene capability source/implementation differs')
        data['unavailableRegions']=[]
        data['limitations']=[s for s in data.get('limitations',[]) if not s.startswith('Nanhai sea northern encounter region4')]
        result[name]=encoded(data)
    for name,recipe in evidence.get('graphics',{}).items():
        if name in result or '/' in name or '\\' in name or not name.endswith('.png'):
            raise ValueError('Unsafe/overlapping world graphic')
        result[name]=scoped_observed_graphic(reader,recipe)
    if evidence.get('growthExtension'):
        combat=json.loads(result['combat.json'])
        extend_world_growth(reader,combat,evidence['growthExtension'],provenance_path)
        result['combat.json']=encoded(combat)
    for inn in evidence.get('inns',[]):
        if int.from_bytes(checked_span(reader,inn['priceSource']),'little')!=inn['price']:
            raise ValueError('Lodging price differs from original table')
        flags=[checked_span(reader,span)[0] for span in inn['statusFlagSources']]
        if inn['blockedStatusMask']!=sum(set(flags)) or any(x not in (2,16,32,64) for x in flags):
            raise ValueError('Lodging status policy differs from original routine')
    for clinic in evidence.get('clinics',[]):
        validate_world_clinic_definition(reader,clinic)
        if clinic['callerMapId']not in known or not any(b['callerMapId']==clinic['callerMapId']and
                b['interiorMapId']==20 and b['npcId']==clinic['npcId']and b.get('clinicId')==clinic['id']
                for b in evidence.get('serviceBindings',[])):
            raise ValueError('Medical service needs its original caller binding')
    if evidence.get('shops') or evidence.get('items') or evidence.get('existingItemPriceUpdates'):
        from forensics.fengshen246 import extract_world_service_catalog
        catalog=extract_world_service_catalog(reader)
        new_item_ids=[i['id'] for i in evidence.get('items',[])]
        if len(set(new_item_ids))!=len(new_item_ids) or set(new_item_ids)&{i['id'] for i in scene['items']}:
            raise ValueError('Duplicate stable item ID; use existing definition rather than silently replacing it')
        items={i['id']:i for i in catalog['items']}
        stocks={(s['category'],s['contextIndex']):s for s in catalog['stocks']}
        for item in evidence.get('items',[])+evidence.get('existingItemReuse',[]):
            if 'baseDefinitionSha256' in item:
                matches=[i for i in scene['items']if i['id']==item['id']]
                if len(matches)!=1 or digest(encoded(matches[0]))!=item['baseDefinitionSha256']:
                    raise ValueError('Existing item reuse differs from reviewed base definition')
            if item['category']=='special':
                if 'fieldProtectionUse'in item:
                    validate_world_field_protection_item(reader,item)
                    continue
                proof=load(ROOT/item['worldUse']['evidence'])
                for span in proof['spans']:checked_span(reader,span)
                if proof['romSha256']!=SHA256 or (item['id'],item['originalId'],item['maxCount'])!=('rom.special.11',11,1) or \
                        'buyPrice' in item or 'sellPrice' in item:
                    raise ValueError('Unreviewed special item or invented price')
                expected={'targetSpriteId':226,'reusable':True,'usedFlagId':'rom.inventory.special.11.used',
                    'evidence':item['worldUse']['evidence']}
                if item['worldUse']!=expected:raise ValueError('World item use differs from verified dispatch')
                names=reader.word(2,0xe610);pointer=reader.word(2,names+item['originalId']*2)
                if checked_span(reader,item['source']['nameRange'])!=reader.read(2,pointer,5) or \
                        item['source']['nameRange']['cpuAddress']!=pointer or not item['source'].get('nameEvidence'):
                    raise ValueError('Special item name lacks original source')
                continue
            original=items.get(item['id'])
            if original is None or any(item[k]!=original[k] for k in ('category','originalId','buyPrice','sellPrice','maxCount')):
                raise ValueError('Service item differs from original catalog')
            checked_span(reader,item['source']['priceRange']);checked_span(reader,item['source']['nameRange'])
            if not item['source'].get('nameEvidence'):raise ValueError('Service name needs a source')
            equipment=item.get('equipment')
            if equipment:
                slot=equipment['slot'];bonus=original['contribution']
                if equipment['allowedCharacters']==['nezha']:
                    if slot not in original['listMembershipCandidates']:
                        raise ValueError('Equipment category list differs')
                else:
                    proof=load(ROOT/equipment['evidence'])
                    expected=next((x for x in proof['items'] if x['id']==item['id']),None)
                    if proof['romSha256']!=SHA256 or expected is None or equipment!=(expected['equipment']|{'evidence':equipment['evidence']}) or \
                            not set(equipment['allowedCharacters']).issubset({x['initialState']['id'] for x in scene.get('additionalCharacters',[])+evidence.get('additionalCharacters',[])}):
                        raise ValueError('Equipment owner or original slot evidence differs')
                if original['crossHandOccupancy'] and equipment.get('operationEnabled',True):
                    raise ValueError('Cross-hand equipment requires its original paired transaction')
                expected=(bonus if slot=='rightHand' else 0,bonus if slot=='body' else 0,bonus if slot=='feet' else 0)
                if tuple(equipment[k] for k in ('attackBonus','defenseBonus','evasionValue'))!=expected:
                    raise ValueError('Equipment contribution differs')
                for span in equipment['ruleSources']:checked_span(reader,span)
        for shop in evidence.get('shops',[]):
            stock=stocks.get((shop['source']['category'],shop['source']['callerMapId']))
            if stock is None:raise ValueError('Unknown original service stock context')
            expected=[items['rom.item.0' if stock['category']=='weapon' and i==0 else f'rom.{stock["category"]}.{i}']['id'] for i in stock['originalIds']]
            if shop['items']!=expected or shop['sellItems']!=expected:
                raise ValueError('Service stock differs from original category/context')
            checked_span(reader,shop['source']['stockRange'])
        # An old loot definition can predate a usable merchant price. Preserve
        # its stable ID/name/effects and only add the actual catalog price,
        # bound to the complete reviewed definition and original price pointer.
        for update in evidence.get('existingItemPriceUpdates',[]):
            matches=[i for i in scene['items'] if i['id']==update['id']]
            original=items.get(update['id'])
            if len(matches)!=1 or original is None or digest(encoded(matches[0]))!=update['baseDefinitionSha256']:
                raise ValueError('Existing item price update differs from reviewed definition')
            old=matches[0]
            if any(old[k]!=original[k] for k in ('category','originalId','maxCount')) or \
                    any(update[k]!=original[k] for k in ('buyPrice','sellPrice')) or \
                    any(k in old for k in ('buyPrice','sellPrice')):
                raise ValueError('Existing item price update must fill absent actual catalog prices')
            for key in ('offset','module','cpuAddress','length','sha256'):
                if update['priceSource'].get(key)!=original['priceSource'][key]:
                    raise ValueError('Existing item price source differs from its original pointer')
            checked_span(reader,update['priceSource'])
            old.update({k:update[k]for k in ('buyPrice','sellPrice')})
            old['source']=dict(old['source'],merchantPriceEvidence=provenance_path,
                               merchantPriceRange=update['priceSource'])
    for definition in evidence.get('sceneStories',[]):
        validate_world_rebirth_script(reader,definition)
        if any(s['id']==definition['id']or s['npcId']==definition['npcId']for s in scene.get('sceneStories',[])):
            raise ValueError('Duplicate scene script identity')
        if definition['entryTrigger']['mapId'] not in known or definition['continuation']['destination']['mapId'] not in known:
            raise ValueError('Scene story destination not packaged')
        scene.setdefault('sceneStories',[]).append(definition)
    for name in ('npcs','dialogues','inns','clinics','shops','items'):
        old=scene.get(name,[]);added=evidence.get(name,[])
        if name=='clinics' and not(old or added):continue # Keep reviewed historical recipe bytes unchanged.
        if {r['id'] for r in old}&{r['id'] for r in added}:raise ValueError('Overlapping world object ID')
        scene[name]=old+added
    for npc in evidence.get('npcs',[]):
        if npc.get('originalTalk'):
            from forensics.fengshen246 import extract_npcs
            path='game-data/provenance/world-tree107-talk.json';proof=load(ROOT/path)
            record=extract_npcs(reader,110)['records'][0]
            expected={k:proof['rules'][k]for k in ('actionId','mapFlagId','witnessFlagId','itemId')}|{'evidence':path}
            raw=(ROOT/proof['cpuExpectedPath']).read_bytes()
            if proof['romSha256']!=SHA256 or proof['scopeRevision']!='tree110-yang-actor-action17-witness-and-item19' or \
                    proof['cpuCaseCount']!=1024 or proof['cpuFailures']!=0 or \
                    proof['activeCpuSha256']!=digest(reader.read(10,0x8000,32768)) or \
                    digest(raw)!=proof['cpuExpectedSha256'] or len(raw.splitlines())!=1025 or \
                    npc['id']!='rom.npc.110.0' or npc['mapId']!=110 or npc['cell']!=[6,6] or npc['spriteId']!=130 or \
                    npc['source']['record']!=record['range'] or proof['npcSource']!=record['range'] or \
                    npc['originalTalk']!=expected or npc['firstEffects'] or \
                    npc['firstDialogue']!=proof['rules']['firstDialogue'] or npc['repeatDialogue']!=proof['rules']['repeatDialogue'] or \
                    evidence['graphics'].get(npc['sprite'])!=proof['graphic']:
                raise ValueError('Original NPC action17 selection, flags or actor differs')
            for span in proof['sources']:checked_span(reader,span)
            for dialogue in proof['dialogues']:
                checked_span(reader,dialogue['source']['range'])
                if dialogue not in evidence['dialogues']:raise ValueError('Original actor dialogue source differs')
        if npc.get('clinicId'):
            from forensics.fengshen246 import extract_npcs
            records=extract_npcs(reader,20)['records']
            index=next((i for i in range(2)if npc['id']==f'rom.npc.20.{i}'),None)
            if index is None:raise ValueError('Unknown original medical actor')
            record=records[index];cell=[(record[k]-120)//16 for k in ('xCandidate','yCandidate')]
            if npc['mapId']!=20 or npc['cell']!=cell or npc['interactionCell']!=[cell[0],cell[1]+1] or \
                    npc['spriteId']!=record['entityByte'] or npc['source']['record']!=record['range'] or npc['firstEffects'] or \
                    not any(c['id']==npc['clinicId']and c['npcId']==npc['id']for c in evidence.get('clinics',[])):
                raise ValueError('Medical actor/target/effects differs from original record')
        if npc.get('spriteEvidence')=='game-data/provenance/world-hell-hall-batch-resources.json':
            recipe=validate_world_hall_batch_npc_graphic(reader,npc['spriteId'])
            if evidence['graphics'].get(npc['sprite'])!=recipe:
                raise ValueError('NPC sprite differs from reviewed original actor pose')
        elif npc.get('spriteEvidence')=='game-data/provenance/world-seventh-side-rooms.json':
            recipe=validate_world_seventh_side_npc_graphic(reader,npc['spriteId'])
            if evidence['graphics'].get(npc['sprite'])!=recipe:
                raise ValueError('Side room sprite differs from its reviewed pose')
        if npc.get('interactionDirection'):
            proof=load(ROOT/npc['interactionEvidence'])
            rule=validate_world_hall_batch_script(reader,npc['mapId']) if npc['interactionEvidence']=='game-data/provenance/world-hell-hall-batch-script.json' else proof['rules']
            if proof['romSha256']!=SHA256 or (npc['id'],npc['mapId'],npc['cell'],npc['interactionCell'],npc['interactionDirection'])!= \
                    (rule['npcId'],rule['mapId'],rule['npcCell'],rule['normalTalkCell'],rule['normalTalkDirection']):
                raise ValueError('Original nonadjacent interaction point differs')
            checked_span(reader,npc['source']['record'])
        if npc.get('scriptedActor'):
            if npc.get('sceneStoryActor'):
                definition=next((s for s in evidence.get('sceneStories',[])if s['npcId']==npc['id']),None)
                if definition is None:raise ValueError('Scene actor lacks its scoped script')
                proof=validate_world_rebirth_script(reader,definition)
                if npc['id']!='rom.npc.86.0' or npc['mapId']!=86 or npc['cell']!=[7,3] or \
                        npc['source']['record']!=proof['npcSource'] or npc['firstEffects'] or \
                        npc['firstDialogue']!='rom.dialogue.96.2' or npc.get('repeatDialogue') or \
                        evidence['graphics'].get(npc['sprite'])!=proof['npcGraphic']:
                    raise ValueError('Scene actor pose, interaction or side effects differ')
                continue
            story=next((b for b in overlay.get('bosses',[]) if b['npcId']==npc['id']),None) if overlay else None
            if story is None or not story.get('actorScriptSource') or npc.get('firstEffects'):
                raise ValueError('Scripted actor lacks its original event')
            proof=load(ROOT/story['entryTrigger']['evidence']);rule=proof['rules']
            if npc['mapId']!=rule['mapId'] or npc['cell']!=rule['introTalkCell']:
                raise ValueError('Scripted actor pose differs from original interception')
            if npc['source']['script']!=story['actorScriptSource']:
                raise ValueError('Script actor and boss evidence differ')
        if not npc.get('treasure'):continue
        if npc['treasure'].get('categoryGrant') is not None:
            validate_world_chest_grant(reader,npc)
            continue
        raw=checked_span(reader,npc['source']['record']);t=npc['treasure']
        if list(raw[:3])!=[144,1,11] or raw[13]!=2 or t['itemId']!='rom.special.11' or t['amount']!=1 or \
                t['flagId']!=f'rom.map.{npc["mapId"]}.flag.2' or not npc.get('openedSprite'):
            raise ValueError('Treasure differs from original category, item or grant flag')
        if not overlay or not any(b['npcId']==npc['id'] and b.get('entryTrigger') for b in overlay.get('bosses',[])):
            raise ValueError('Guarded treasure requires its original coordinate story')
    for obj in evidence.get('mapObjects',[]):
        raw=checked_span(reader,obj['recordSource']);mid=obj['mapId']
        cell=[(int.from_bytes(raw[i:i+2],'little')-120)//16 for i in (4,6)]
        if obj['interaction']=='NOT_IMPLEMENTED':
            if mid not in known or obj['cell']!=cell or any(o['id']==obj['id'] for o in scene.get('mapObjects',[])):
                raise ValueError('Unimplemented actor must preserve its original record and cell')
            if obj.get('initialHiddenEvidence'):
                path='game-data/provenance/world-hell-hall-batch-resources.json'
                proof=load(ROOT/path);initial=proof['initialHiddenObjects']['198']
                if obj['initialHiddenEvidence']!=path or (mid,raw[0])!=(64,198) or \
                        initial['source']!=obj['recordSource'] or initial['confidence']!='VERIFIED_INITIAL_ZERO_ALPHA_ONLY' or \
                        initial['recipe']['completeGraphic'] is not False or initial['recipe']['opaquePixelCount']!=0 or \
                        evidence['graphics'].get(obj['sprite'])!=initial['recipe']:
                    raise ValueError('Hidden object only justifies its original initial transparent pose')
            elif obj.get('spriteEvidence')=='game-data/provenance/world-hell-hall-batch-resources.json':
                recipe=validate_world_hall_batch_npc_graphic(reader,obj['spriteId'])
                if raw[0]!=obj['spriteId'] or raw[1]!=255 or evidence['graphics'].get(obj['sprite'])!=recipe:
                    raise ValueError('Non-dialogue actor must retain its original independent pose')
            scene.setdefault('mapObjects',[]).append(obj)
            continue
        target=obj['itemTarget']
        if mid not in known or raw[0]!=226 or raw[13]!=1 or obj['cell']!=cell or \
                obj['interaction']!='WORLD_ITEM_TARGET' or target['spriteId']!=226 or \
                target['removedFlagId']!=f'rom.map.{mid}.flag.1' or target['completionFlagId']!=f'rom.map.{mid}.flag.128':
            raise ValueError('World item object differs from original record')
        if any(o['id']==obj['id'] for o in scene.get('mapObjects',[])):raise ValueError('Duplicate map object')
        if not any(i.get('worldUse',{}).get('evidence')==target['evidence'] for i in scene['items']):
            raise ValueError('World item object has no reviewed use rule')
        scene.setdefault('mapObjects',[]).append(obj)
        name=next(m['scene'] for m in scene['maps'] if m['id']==mid);data=json.loads(result[name])
        if not 0<=cell[0]<data['width'] or not 0<=cell[1]<data['height']:raise ValueError('Map object outside grid')
        data['dynamicObjectCells']=sorted(set(data.get('dynamicObjectCells',[]))|{cell[1]*data['width']+cell[0]})
        result[name]=encoded(data)
    for barrier in evidence.get('sceneBarriers',[]):
        if barrier.get('kind')=='CONTINENT_ACTOR_FILTER':
            from forensics.fengshen246 import extract_npcs
            proof=load(ROOT/barrier['evidence']);original=extract_npcs(reader,16)['records']
            if barrier['evidence']!='game-data/provenance/world-continent-actor-barriers.json' or \
                    (proof['romSha256'],proof['scopeRevision'],proof['mapId'],proof['flagAddress'],proof['cpuCaseCount'],proof['cpuFailures'])!= \
                    (SHA256,'continent16-six-original-flag-filtered-collision-actors',16,0x710,1536,0) or \
                    reader.word(0,0xd493+32)!=0x710 or len(original)!=6:
                raise ValueError('World collision actor filter lacks original scoped evidence')
            if {(v['module'],v['cpuAddress'],v['length'])for v in proof['sources']}!={(0,0xa973,75)}:
                raise ValueError('Original actor-filter routine differs')
            for span in proof['sources']+[proof['flagPointer'],proof['npcListSource']]:checked_span(reader,span)
            table=(ROOT/proof['cpuExpectedPath']).read_bytes()
            if digest(table)!=proof['cpuExpectedSha256'] or len(table.splitlines())!=1537:
                raise ValueError('Original world collision-actor CPU table differs')
            index=next((n['index']for n in original if n['range']==barrier['recordSource']),None)
            if index is None:raise ValueError('World blocker is not an original map16 actor')
            raw=checked_span(reader,barrier['recordSource']);cell=[(int.from_bytes(raw[i:i+2],'little')-120)//16 for i in (4,6)]
            if (barrier['mapId'],barrier['cell'],raw[0],raw[13],barrier['removedFlagId'])!= \
                    (16,cell,[244,231,232,241,242,243][index],1<<index,f'rom.map.16.flag.{1<<index}') or \
                    barrier['id']!=f'rom.barrier.16.{raw[0]}' or raw[1:3]!=b'\xff\xff':
                raise ValueError('World blocker identity/cell/flag differs')
            name=next(m['scene']for m in scene['maps']if m['id']==16);data=json.loads(result[name])
            i=cell[1]*data['width']+cell[0]
            if not(0<=cell[0]<data['width'] and 0<=cell[1]<data['height']) or data['collision'][i]not in (0,2,15,16):
                raise ValueError('Original world blocker has invalid foot geometry')
            data['dynamicObjectCells']=sorted(set(data.get('dynamicObjectCells',[]))|{i});result[name]=encoded(data)
            if any(b['id']==barrier['id']for b in scene.get('sceneBarriers',[])):raise ValueError('Duplicate original world blocker')
            scene.setdefault('sceneBarriers',[]).append(barrier)
            continue
        batch=barrier['evidence']=='game-data/provenance/world-hell-hall-batch-script.json'
        proof=load(ROOT/barrier['evidence'])
        rule=validate_world_hall_batch_script(reader,barrier['mapId']) if batch else proof['rules']
        raw=checked_span(reader,barrier['recordSource'])
        cell=[(int.from_bytes(raw[i:i+2],'little')-120)//16 for i in (4,6)]
        if proof['romSha256']!=SHA256 or not(batch or proof['scopeRevision'] in ('first-hall-event20-qin-victory-removes-246','second-hall-event21-chu-victory-removes-246')) or \
                barrier['mapId']!=rule['mapId'] or barrier['cell']!=cell or raw[0]!=246 or raw[13]!=(rule['gateMask'] if batch else 4) or \
                barrier['removedFlagId']!=rule['barrierRemovedFlag'] or barrier['cell']!=rule['barrierCell']:
            raise ValueError('Original hell hall collision actor differs')
        for span in proof['sources']:checked_span(reader,span)
        name=next(m['scene'] for m in scene['maps']if m['id']==barrier['mapId']);data=json.loads(result[name])
        if cell[1]*data['width']+cell[0] not in data['dynamicObjectCells']:
            raise ValueError('Original barrier must start as a collision actor')
        if any(b['id']==barrier['id']for b in scene.get('sceneBarriers',[])):raise ValueError('Duplicate original barrier')
        scene.setdefault('sceneBarriers',[]).append(barrier)
    bindings=scene.get('serviceBindings',[])+evidence.get('serviceBindings',[])
    if len({(b['callerMapId'],b['interiorMapId'],b['npcId']) for b in bindings})!=len(bindings):
        raise ValueError('Duplicate service caller binding')
    if bindings:scene['serviceBindings']=bindings
    if overlay:
        # A legitimate random drop must be loadable even when no current shop
        # stocks it. Reject missing definitions before signing/building an APK.
        item_categories={i['id']:i['category'] for i in scene['items']}
        for enemy in combat['enemies']:
            loot=enemy.get('loot')
            if loot and item_categories.get(loot['itemId'])!=loot['category']:
                raise ValueError('Encounter loot has no matching item definition')
    scene['limitations']=scene.get('limitations',[])+evidence.get('limitations',[])
    validate_world_exit_geometry(scene,result)
    result['scene.json']=encoded(scene)
    return result

def export_from_base(payload, provenance_path, target_pin, verify_target=True, _visited=frozenset()):
    """Reuse checked base bytes; dispatch the current bounded, evidenced iteration."""
    evidence_path=(ROOT/provenance_path).resolve()
    if not evidence_path.is_relative_to(ROOT) or evidence_path.suffix!='.json':
        raise ValueError('Invalid iteration provenance path')
    if evidence_path in _visited:raise ValueError('Cyclic scoped base export')
    visited=_visited|{evidence_path}
    evidence=load(evidence_path)
    if evidence['romSha256']!=SHA256 or evidence['taskId'] not in ('TOWN-02','NANHAI-01','WORLD-FULL-01'):
        raise ValueError('Unexpected iteration evidence')
    if evidence.get('baseExport'):
        # Restore a pinned local checkpoint recipe from the same reviewed APK;
        # no failed candidate APK, second importer or hand-edited assets are used.
        base=evidence['baseExport'];pin_path=(ROOT/base['pinPath']).resolve()
        if not pin_path.is_relative_to(ROOT) or pin_path.suffix!='.json' or digest(pin_path.read_bytes())!=base['pinSha256']:
            raise ValueError('Scoped base pin differs')
        parent=load(pin_path);proof_path=(ROOT/parent['iteration']['provenance']).resolve()
        if not proof_path.is_relative_to(ROOT) or proof_path.suffix!='.json' or digest(proof_path.read_bytes())!=base['provenanceSha256']:
            raise ValueError('Scoped base provenance differs')
        if parent['iteration']['base']!=target_pin['iteration']['base'] or parent['manifestSha256']!=evidence['baseManifestSha256']:
            raise ValueError('Scoped checkpoint must retain the same reviewed APK base')
        payload=export_from_base(payload,parent['iteration']['provenance'],parent,_visited=visited)
    if evidence['taskId'] in ('NANHAI-01','WORLD-FULL-01'):
        result=(export_nanhai_from_base if evidence['taskId']=='NANHAI-01' else export_world_from_base)(payload,evidence,provenance_path,target_pin)
        encoded=lambda value:(json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode('utf-8')
        for name,raw in list(result.items()):
            if name=='manifest.json' or not name.endswith('.json'):continue
            value=json.loads(raw)
            if 'version' in value:value['version']=target_pin['contentVersion'];result[name]=encoded(value)
        manifest=json.loads(result['manifest.json']);manifest['version']=target_pin['contentVersion']
        manifest['files']={name:digest(raw) for name,raw in result.items() if name!='manifest.json'}
        result['manifest.json']=encoded(manifest)
        if verify_target and digest(result['manifest.json'])!=target_pin['manifestSha256']:
            raise ValueError('Scoped export differs from reviewed target pin')
        return result
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
    parser.add_argument('--world-inventory',type=Path,help='Write table/context coverage without generating an APK')
    parser.add_argument('--world-cache',type=Path,help='Recover geometry/default atlases into ignored private cache only')
    args=parser.parse_args()
    if args.world_cache:
        rgb=load(ROOT/'game-data/provenance/world-full01.json')['emulatorRgb']
        report=cache_world_scenes(iteration_reader(),args.world_cache,rgb)
        print(json.dumps({'geometrySlots':len(report['maps']),'uniqueAtlases':report['uniqueAtlases'],
            'cacheReused':report['cacheReused'],'blackDefaultIds':[m['mapId'] for m in report['maps']
                if m['visualInput']=='BLACK_REQUIRES_STATE_PALETTE'],'appRuntime':'NOT_RUN','uploaded':False}))
    elif args.world_inventory:
        from forensics.fengshen246 import extract_world_inventory
        pin=load(ROOT/'ci/content-source.json')
        packaged=load(ROOT/pin['iteration']['provenance'])
        # Package status is populated by the reviewed manifest, not by successful ROM parsing.
        ids=[]
        if args.base_apk:
            import ci_apk as ci
            base=ci.CONFIG['iteration']['base'];ci.verify_apk(args.base_apk,release=True)
            if digest(args.base_apk.read_bytes())!=base['apkSha256']:raise ValueError('Wrong reviewed inventory APK')
            payload=ci.content(args.base_apk,base);ids=[m['id'] for m in json.loads(payload['scene.json'])['maps']]
        report=extract_world_inventory(iteration_reader(),ids)
        args.world_inventory.parent.mkdir(parents=True,exist_ok=True);save(args.world_inventory,report)
        print(json.dumps({k:report[k] for k in ['structuralGeometryCount','npcContextCount','effectiveMapCount','summary']}))
    elif args.base_apk:
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
