"""Materialize strict, versioned Phase 1 contracts, using the frozen raw columns."""
from .common import ROOT, load, save, SOURCES, CONFIDENCES, DOMAINS, DIFFS

def obj(properties, required=None):
    return {'type':'object','properties':properties,'required':list(properties) if required is None else required,'additionalProperties':False}
def arr(item):return {'type':'array','items':item}
S={'type':'string'};I={'type':'integer'};N={'type':'integer','minimum':0};B={'type':'boolean'}
ID={'type':'string','pattern':'^[a-z][a-z0-9_.-]{1,95}$'}
REFS={'type':'array','minItems':1,'uniqueItems':True,'items':ID}
POS=obj({'x':I,'y':I});NULLID={'anyOf':[ID,{'type':'null'}]}

def build():
    folder=ROOT/'game-data/schemas'
    locator=obj({'repository':S,'commit':S,'path':S,'sha256':{'type':'string','pattern':'^[0-9a-f]{64}$'},
       'table':S,'primaryKey':{'type':['integer','string']},'column':S,'url':S,'timestampMs':N,
       'romSha256':{'type':'string','pattern':'^[0-9a-f]{64}$'},'offset':N,'length':N,'bank':N,'meaning':S},[])
    source=obj({'id':ID,'source':{'enum':SOURCES},'confidence':{'enum':CONFIDENCES},'originalVerified':B,
       'licenseStatus':{'enum':['UNKNOWN','RESTRICTED','PERMITTED']},'locator':locator,'evidenceRefs':arr(ID),'note':S})
    source['allOf']=[{'if':{'properties':{'originalVerified':{'const':True}}},'then':{'properties':{'confidence':{'const':'VERIFIED'},'evidenceRefs':{'minItems':1}}}}]
    write(folder,'provenance',obj({'schemaVersion':{'const':1},'records':arr(source)}))
    definitions=load(ROOT/'docs/evidence/database-tables.json');branches=[]
    for table,info in definitions.items():
        props={}
        for c in info['columns']:
            typ=c['type'].lower();types=['integer','null'] if typ in ('integer','int') else ['number','null'] if typ in ('float','real') else ['string','null']
            # SQLite affinity is not a strict storage type (map/22/soundId contains a space).
            # Shape/field completeness is strict; storage-class anomalies are separate warnings.
            props[c['name']]={'type':['integer','number','string','null']}
        branches.append(obj({'id':ID,'table':{'const':table},'legacyId':{'type':['integer','string']},'sourceRefs':REFS,'data':obj(props)}))
    row_schema={'type':'object','required':['table'],'properties':{'table':{'enum':list(definitions)}},
      'allOf':[{'if':{'properties':{'table':b['properties']['table']}},'then':b} for b in branches]}
    attrs={'type':'object','additionalProperties':S}
    image=obj({'source':S,'width':S,'height':S,'path':S})
    tmx=obj({'id':I,'sourceFile':S,'sha256':S,'sourceRefs':REFS,'attributes':attrs,
      'layers':arr(obj({'attributes':attrs,'gids':arr({'type':'integer','minimum':0,'maximum':4294967295})})),
      'tilesets':arr(obj({'attributes':attrs,'image':{'anyOf':[image,{'type':'null'}]},'tileProperties':{'type':'object','additionalProperties':{'type':'object'}}})),
      'xmlTree':{'type':'object'}})
    asset=obj({'id':ID,'path':S,'sha256':S,'bytes':N,'source':{'const':'REFERENCE_PROJECT'},'originalVerified':{'const':False},'sourceRefs':REFS})
    write(folder,'raw-reference',obj({'schemaVersion':{'const':1},'profile':{'const':'reference-research'},'commit':S,
      'sourceDatabaseSha256':S,'tableSchemas':{'type':'object'},'records':arr(row_schema),
      'domains':obj({d:arr(ID) for d in DOMAINS if d!='assets'}),'maps':arr(tmx),'assets':arr(asset)}))
    # Canonical has no generic legacy-data escape hatch. Only admitted, explicit semantic records.
    base={'id':ID,'sourceRefs':REFS}
    def entity(props,required=None):
        keys=list(props) if required is None else required
        return obj({**base,**props},['id','sourceRefs']+keys)
    action={'oneOf':[]}
    for kind,fields in {
       'dialogue':{'dialogueId':ID},'giveItem':{'itemId':ID,'quantity':{'type':'integer','minimum':1}},
       'removeItem':{'itemId':ID,'quantity':{'type':'integer','minimum':1}},
       'openShop':{'shopId':ID},'startBattle':{'enemyId':ID},'useSkill':{'skillId':ID},
       'callEvent':{'eventId':ID},'showNpc':{'npcId':ID},'hideNpc':{'npcId':ID},
       'teleport':{'mapId':ID,'position':POS},'changeMap':{'mapId':ID,'position':POS},
       'chest':{'chestId':ID},'sequence':{'actions':arr({'$ref':'#/$defs/action'})}
    }.items():action['oneOf'].append(obj({'type':{'const':kind},**fields}))
    entities={
     'maps':entity({'name':S,'width':{'type':'integer','minimum':1},'height':{'type':'integer','minimum':1},
        'assetIds':arr(ID),'transitions':arr(obj({'id':ID,'position':POS,'targetMapId':ID,'targetPosition':POS}))}),
     'dialogues':entity({'text':{'type':'string','minLength':1},'nextDialogueId':NULLID,'speakerNpcId':NULLID}),
     'npcs':entity({'mapId':ID,'position':POS,'dialogueId':ID,'eventId':NULLID}),
     'enemies':entity({'name':S,'hp':{'type':'integer','minimum':1},'attack':N,'defense':N,'skillIds':arr(ID)}),
     'progression':entity({'characterId':ID,'level':{'type':'integer','minimum':1},'hp':N,'mp':N,'experienceThreshold':{'type':['integer','null'],'minimum':0}}),
     'items':entity({'name':S}), 'equipment':entity({'itemId':ID,'slot':S}),
     'skills':entity({'name':S,'mpCost':N,'characterId':NULLID,'learnLevel':{'type':['integer','null'],'minimum':1}}),
     'shops':entity({'mapId':ID,'npcId':ID,'itemIds':arr(ID)}),
     'chests':entity({'mapId':ID,'position':POS,'itemId':ID}),
     'events':entity({'mapId':NULLID,'actions':arr({'$ref':'#/$defs/action'})}),
     'assets':entity({'path':S,'sha256':{'type':'string','pattern':'^[0-9a-f]{64}$'}})}
    c=obj({'schemaVersion':{'const':1},'contract':{'const':'forensic-baseline-v1'},
       'profile':{'enum':['verified-baseline','synthetic-test']},'status':{'enum':['BLOCKED_WAITING_FOR_ROM','BLOCKED_UNVERIFIED','READY_FOR_REVIEW','SYNTHETIC']},
       'runtimeReady':{'const':False},'entities':obj({d:arr(entities[d]) for d in DOMAINS})})
    c['$defs']={'action':action};write(folder,'canonical-baseline',c)
    baseline_record=obj({'domain':{'enum':DOMAINS},'id':ID,'referenceId':NULLID,
       'comparable':{'type':'object'},'complete':B,'sourceRefs':REFS})
    write(folder,'original-comparison',obj({'schemaVersion':{'const':1},'baseRomSha256':{'type':['string','null']},
       'completeDomains':arr({'enum':DOMAINS}),'coverageSourceRefs':obj({d:REFS for d in DOMAINS},[]),'records':arr(baseline_record)}))
    offset=obj({'romSha256':{'type':'string','pattern':'^[0-9a-f]{64}$'},'offset':N,'length':{'type':'integer','minimum':1},
      'meaning':{'type':'string','minLength':1},'evidence':REFS,'confidence':{'enum':CONFIDENCES}})
    write(folder,'rom-offsets',obj({'schemaVersion':{'const':1},'status':{'enum':['WAITING_FOR_ROM','RESEARCH_IN_PROGRESS']},'records':arr(offset)}))

def write(folder,name,data):
    data={'$schema':'https://json-schema.org/draft/2020-12/schema',**data}
    save(folder/(name+'.schema.json'),data)
