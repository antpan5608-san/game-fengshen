import copy,json,sys
from pathlib import Path
root=Path('/workspace/game-fengshen-world-jiameng-next');sys.path.insert(0,str(root/'tools'))
from forensics.fengshen246 import Reader,SHA256,digest,extract_map,extract_npcs,extract_text,decode_tokens,extract_default_map_palette,extract_growth_candidates,extract_world_service_catalog
from forensics.common import save,load
r=Reader(Path('/workspace/game-fengshen/private-inputs/town02/target.nes').read_bytes())
assert digest(r.data)==SHA256
pp='game-data/provenance/world-jiang-invitation.json';p=load(root/pp)
old=load(root/'android/app/src/main/assets/development/scene.json');assert old['version']=='opening-segment-001-c60'
fonts=[];dialogues=[]
for filename,group,messages in [('font17-draft.json',17,range(2,14)),('font131-first-draft.json',131,range(1,16)),('font131-draft.json',131,range(16,19))]:
 d=load(root/'private-derived/world-jiang-resources'/filename)
 assert not d['unknown'];cs={int(k):v for k,v in d['charset'].items()}
 font=dict(group=group,messages=list(messages),chr2kBanks=d['banks'],charset=d['charset'],glyphs=d['glyphs'],sources=[])
 for bank in d['banks']:
  offset=524304+bank*2048
  font['sources'].append(dict(offset=offset,length=2048,sha256=digest(r.data[offset:offset+2048]),meaning='Original active font CHR bank'))
 fonts.append(font)
 for i in messages:
  t=extract_text(r,group,i)
  dialogues.append(dict(id=f'rom.dialogue.{group}.{i}',text=decode_tokens(bytes.fromhex(t['rawHex']),cs)['text'],
   source=dict(confidence='PROVISIONAL_ROM_GLYPH_TRANSCRIPTION',originalVerified=False,evidence=pp,
    record=t['range'],pointerEvidence=t['pointerEvidence'],fontBanks=d['banks'],remainingUnknown=['Normal Android route and exact font rendering'])))
gfx=load(root/'private-derived/world-jiang-resources/oam-recipes.json')
entity={n['spriteId']:n['sprite']for n in old['npcs']if n.get('spriteId')in(140,141,142,143)}
npcs=[]
for mid in (7,121):
 for rec in extract_npcs(r,mid)['records']:
  raw=bytes.fromhex(rec['rawHex']);i=rec['index'];sprite=raw[0];first=raw[1];repeat=raw[2]
  cell=[(rec[k]-120)//16 for k in('xCandidate','yCandidate')]
  n=dict(id=f'rom.npc.{mid}.{i}',mapId=mid,cell=cell,spriteId=sprite,
   sprite=f'npc-jiang-{sprite}.png'if sprite in(150,152,199,247)else entity[sprite],
   firstDialogue=f'rom.dialogue.{mid+10}.{first}',repeatDialogue=None,firstEffects=[],jiangResourceEvidence=pp,
   source=dict(confidence='PROVISIONAL_ROM_STATIC',record=rec['range'],evidence=pp,
    remainingUnknown=['Original dynamic NPC movement and per-frame facing not reproduced','Normal Android route NOT_RUN']))
  if mid==121 and i<3:
   n['repeatDialogue']=f'rom.dialogue.131.{repeat}'
   n['originalTalk']=dict(actionId=44,mapFlagId=f'rom.map.121.flag.{raw[13]}',witnessFlagId='rom.global.7c9.nonzero',itemId='',evidence=pp,
    messageDialogues={'0':n['firstDialogue'],'1':f'rom.dialogue.131.{first+1}','2':n['repeatDialogue']})
  elif mid==121 and i==3:
   n['repeatDialogue']='rom.dialogue.131.17'
   n['originalTalk']=dict(actionId=45,mapFlagId='rom.map.121.flag.16',witnessFlagId='rom.global.7c9.nonzero',itemId='',evidence=pp,
    messageDialogues={str(k):f'rom.dialogue.131.{k}'for k in(14,16,17,18)})
  elif mid==7 and i==4:
   n['firstDialogue']='rom.dialogue.17.13';n['repeatDialogue']='rom.dialogue.17.6'
   n['originalTalk']=dict(actionId=61,mapFlagId='',witnessFlagId='rom.global.7fd.nonzero',itemId='',evidence=pp)
  else:n['readOnlyDialogue']=True
  if mid==7 and i in(4,5):n.update(removedFlagId='rom.npccontext.7.192',automaticStoryEvidence=pp)
  npcs.append(n)
palettes={mid:extract_default_map_palette(r,mid)for mid in(7,121,142)}
maps=[]
for mid,spawn,allowed in [(7,[11,21],[0,*range(2,12)]),(121,[23,44],[0,2]),(142,[15,43],[0,*range(2,11)])]:
 m=extract_map(r,mid)
 recipe=dict(mapId=mid,gridSha256=m['gridSha256'],palette=palettes[mid]['palette'],spawn=spawn,
  walkableClasses=allowed,npcCells=[n['cell'][1]*m['width']+n['cell'][0]for n in npcs if n['mapId']==mid],
  limitations=['Only original mainline entries in this batch; optional city rooms and services not yet connected'])
 if mid==7:recipe.update(directionalCollision=True,jiangTownBridgeEvidence=pp)
 if mid==121:recipe['terrain']=dict(tileset=4,evidence='game-data/provenance/world-terrain.json')
 if mid==142:recipe['directionalProfileEvidence']=pp
 maps.append(recipe)
exits=[]
for mid in (16,7,121,142):
 ptr=r.word(8,0xdc69+mid*2)
 for i in range(128):
  a=ptr+i*5;raw=r.read(8,a,5)
  if raw[0]==254:break
  if raw[2]not in(7,121,142,16)or(mid==16 and raw[2]not in(7,142)):continue
  if raw[0]==255:
   for direction,cell in [('DOWN',[15,43]),('LEFT',[0,43]),('RIGHT',[31,43])]:
    # Edge row is map-wide; scope only observed bottom gate until native side departures are verified.
    if direction!='DOWN':continue
    exits.append(dict(fromMapId=mid,trigger=cell,toMapId=raw[2],spawn=list(raw[3:]),kind='EDGE_RECORD',triggerMode='EDGE',direction=direction,
     confidence='VERIFIED',arrivalDirection='DOWN',source=r.span(8,a,5,'Independent original city edge return'),
     runtimeEvidence='CONTROLLED_NATIVE_REAL_ENTRY_WITH_ORIGINAL_EDGE_DISPATCH'))
  else:exits.append(dict(fromMapId=mid,trigger=list(raw[:2]),toMapId=raw[2],spawn=list(raw[3:]),kind='EXIT_RECORD',
   confidence='VERIFIED',arrivalDirection='DOWN'if mid in(7,121)else'UP',resetEncounterSteps=True,
   source=r.span(8,a,5,'Independent original route exit row')))
p['fonts']=fonts;p['dialogues']=dialogues;p['graphics']=gfx;p['npcs']=npcs;p['maps']=maps;p['exits']=exits;p['palettes']=palettes
p['contextSources']=[r.span(0,0xa664,54,'Original party-count map7 context selector'),r.span(0,0xd672,2,'Map7 context pointer'),
 r.span(8,0xb48f,4,'NPC contexts191/192 pointers')]
p['contexts']={str(mid):extract_npcs(r,mid)['records']for mid in(191,192)}
p['bridgeReuse']=dict(path='game-data/provenance/world-village-batch-resources.json',sha256=digest((root/'game-data/provenance/world-village-batch-resources.json').read_bytes()))
matrix=(root/'private-derived/world-jiang-city142-terrain/matrix.tsv').read_bytes();dest=root/'android/app/src/test/resources/world-city142-terrain-original.tsv';dest.write_bytes(matrix)
p['cityTerrain']=dict(mapId=142,tilesetId=6,mode=0,testCount=484,failures=0,cpuExpectedPath=str(dest.relative_to(root)),cpuExpectedSha256=digest(matrix),
 sourceEdges={'3':['UP','LEFT'],'4':['DOWN','LEFT'],'5':['UP'],'6':['DOWN'],'7':['UP','RIGHT'],'8':['DOWN','RIGHT'],'9':['LEFT'],'10':['RIGHT']},
 targetEdges={'3':['DOWN','RIGHT'],'4':['UP','RIGHT'],'5':['DOWN'],'6':['UP'],'7':['DOWN','LEFT'],'8':['DOWN','LEFT']},
 sources=[r.span(0,0xcde9,75,'Original tileset6 source edges'),r.span(0,0xd1d3,66,'Original tileset6 target edges'),
 r.span(0,0xca98,29,'Original source dispatcher'),r.span(0,0xce35,29,'Original target dispatcher')])
p['sources']=[s for s in p['sources']if s['cpuAddress']!=0xc704]
p['sources'] +=[r.span(11,0xc704,73,'Complete original script26 including message12 and NPC-only movement'),
 r.span(10,0xcd2d,22,'Original guard cure/message/map-bit selector'),r.span(10,0xd6ec,8,'Original action61 second dialogue6'),
 r.span(11,0xd311,68,'Original event21 warp, script26 and completion dispatch'),r.span(11,0xcd9a,45,'Original script completion bit128')]
p['rules']['continuation']['dialogueMessages']=[12,7,8,9,10,11]
init=p['rules']['initializedCharacter'];p['initialCharacter']={k:copy.deepcopy(init[k])for k in('id','level','experience','hp','maxHp','mp','maxMp','strength','stamina','agility','spirit','equipment')}
p['initialCharacter'].update(name='姜子牙',actorIndex=3,statusMask=0,confidence='CONTROLLED_ORIGINAL_CPU_AND_NATIVE_JOIN',
 inheritedEmptySlotFields=['Original status remains prior0; no learned spell list inferred'])
p['initializationSources']=[r.span(10,0xd5c4,158,'Actual fourth-slot original initializer')]
group=extract_growth_candidates(r)['groups'][3];yg=load(root/'game-data/provenance/world-party-yangjian.json')['growthExtension']
p['growthExtension']=copy.deepcopy(yg);p['growthExtension'].update(owner='jiangziya',actorIndex=3,growthRange=group['growthRange'],thresholdRange=group['thresholdRange'],
 initialInterval=dict(level=38,totalExperience=190000,currentThreshold=181822,nextLevel=39,nextThreshold=202109),normalCharacterLevelUp='NOT_RUN')
rootptr=r.word(2,0xef60+6);lists={}
for j,slot in enumerate(('rightHand','leftHand','body','feet')):
 ptr=r.word(2,rootptr+2*j);b=r.read(2,ptr,40);b=b[:b.index(255)+1]
 lists[slot]=dict(ids=list(b[:-1]),source=r.span(2,ptr,len(b),'Actor3 actual permitted slot IDs'))
p['equipmentLists']=lists;p['items']=[];updates=[];catalog={i['id']:i for i in extract_world_service_catalog(r)['items']}
for id,slot in [('rom.weapon.44','rightHand'),('rom.armor.24','body'),('rom.armor.28','feet')]:
 raw=catalog[id];assert raw['originalId']in lists[slot]['ids']
 equipment=dict(originalId=raw['originalId'],slot=slot,allowedCharacters=['jiangziya'],originalActorIndexes=[3],originalPermittedSlots=['rightHand','leftHand']if slot=='rightHand'else[slot],
  attackBonus=raw['contribution']if slot=='rightHand'else 0,defenseBonus=raw['contribution']if slot=='body'else 0,
  evasionValue=raw['contribution']if slot=='feet'else 0,operationEnabled=True,evidence=pp,
  ruleSources=[raw['contributionSource'],r.span(2,0xef66,2,'Actor3 slot list root'),r.span(2,rootptr,8,'Actor3 four permitted-slot pointers'),lists[slot]['source']],crossHandOccupancy=False)
 prior=next((i for i in old['items']if i['id']==id),None)
 if prior:
  if 'equipment'in prior:
   equipment=copy.deepcopy(prior['equipment']);equipment['allowedCharacters']+=['jiangziya'];equipment.setdefault('ruleSources',[]).append(lists[slot]['source']);equipment['ownerExtensionEvidence']=pp
  updates.append(dict(id=id,baseDefinitionSha256=digest((json.dumps(prior,ensure_ascii=False,sort_keys=True,indent=2)+'\n').encode()),fields={'equipment':equipment}))
 else:
  p['items'].append(dict(id=id,category=raw['category'],originalId=raw['originalId'],buyPrice=raw['buyPrice'],sellPrice=raw['sellPrice'],maxCount=raw['maxCount'],
   name='原版armor 24',source=dict(confidence='PROVISIONAL_ROM_STATIC',nameConfidence='UNKNOWN',priceRange=raw['priceSource'],nameRange=raw['nameSource'],
    nameEvidence=dict(kind='ORIGINAL_STABLE_ID_NAME_NOT_DECODED',evidence=pp)),equipment=equipment))
p['itemCapabilityUpdates']=updates
guard=root/'android/app/src/test/resources/world-jiang-guard-selector-original.tsv'
p['localCpu']=dict(path=str(guard.relative_to(root)),caseCount=84,failures=0,sha256=digest(guard.read_bytes()),probePath='tools/rom-extractor/probe-world-jiang-local.py',probeSha256=digest((root/'tools/rom-extractor/probe-world-jiang-local.py').read_bytes()))
p['status']='SCOPED_RESOURCES_READY_ANDROID_NOT_RUN';save(root/pp,p)
parent='ci/golden-world-reference-repair-content.json';pin=load(root/parent)
addition=dict(initialState={k:p['initialCharacter'][k]for k in('id','level','experience','hp','maxHp','mp','maxMp','strength','stamina','agility','spirit','statusMask','equipment')},
 name='姜子牙',originalActorIndex=3,evidence=pp,portraitAsset='npc-jiang-247.png',portraitScope='OBSERVED_ORIGINAL_PANXI_NPC_SPRITE_NOT_NATIVE_STATUS_PORTRAIT',confidence='CONTROLLED_ORIGINAL_INIT_STATIC_GROWTH')
rows=[]
for raw in group['rows'][1:]:
 rows.append(dict(level=raw['index']+1,threshold=raw['cumulativeExpCandidate'],hp=raw['hpDeltaCandidate'],mp=raw['mpDeltaCandidate'],
  strength=raw['strengthDeltaCandidate'],stamina=raw['staminaDeltaCandidate'],agility=raw['agilityDeltaCandidate'],spirit=raw['spiritDeltaCandidate'],
  source=raw['growthRange'],thresholdSource=raw['thresholdRange'],runtimeVerified=False,evidence=pp))
ptr=0x8577+6;a=r.word(9,ptr)
overlay=dict(characterGrowth=[dict(owner='jiangziya',originalActorIndex=3,knownMaxLevel=80,limitEvidence=pp,rows=rows)],
 characterMultiplierThresholds={'jiangziya':list(r.read(9,a,36))},characterMultiplierSources={'jiangziya':dict(pointer=r.span(9,ptr,2,'Actor3 physical multiplier pointer'),source=r.span(9,a,36,'Actor3 physical multiplier thresholds'))})
evidence=dict(taskId='WORLD-FULL-01',scopeRevision='panxi-city-king-original-invitation-batch',romSha256=SHA256,
 baseExport=dict(kind='PINNED_LOCAL_RECIPE_FROM_SAME_REVIEWED_APK_NOT_CANDIDATE_APK',pinPath=parent,pinSha256=digest((root/parent).read_bytes()),provenanceSha256=digest((root/pin['iteration']['provenance']).read_bytes())),
 baseManifestSha256=pin['manifestSha256'],ruleRanges=[],emulatorRgb=load(root/'game-data/provenance/world-well8-content.json')['emulatorRgb'],maps=maps,npcs=npcs,dialogues=dialogues,graphics=gfx,exits=exits,
 additionalCharacters=[addition],combatOverlay=overlay,items=p['items'],existingItemCapabilityUpdates=updates,jiangCapabilityEvidence=pp,originalJiangJoin={'evidence':pp},
 limitations=['Original NPC cutscene movement/frame timing and optional city rooms not completed','Learned spells not inferred','Android new batch NOT_RUN'])
save(root/'game-data/provenance/world-jiang-content.json',evidence)
pin['contentVersion']='opening-segment-001-c61';pin['iteration']['provenance']='game-data/provenance/world-jiang-content.json';pin['manifestSha256']='PENDING_GENERATION'
save(root/'ci/golden-world-jiang-content.json',pin)
print('Scoped batch',len(maps),'maps',len(npcs),'NPCs',len(dialogues),'dialogues','Jiang own growth rows',len(rows))
