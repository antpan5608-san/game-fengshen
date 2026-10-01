"""Lossless SQLite rows + TMX XML tree/cell export, never canonical promotion."""
import base64
import collections
import gzip
import hashlib
import json
import sqlite3
import struct
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path
from .common import ROOT, COMMIT, save, load, sha, provenance, contained

DOMAIN_TABLES={'maps':['map'],'dialogues':['story'],'enemies':['monster'],
 'progression':['role_lv'],'items':['object'],'equipment':['object'],'skills':['skill'],
 'npcs':['map_npc'],'shops':['map_buy','store'],'chests':['map_box'],
 'events':['auto_move','map_object','battle']}

def xml_tree(e):
    return {'tag':e.tag,'attributes':dict(e.attrib),'text':e.text,'tail':e.tail,'children':[xml_tree(x) for x in e]}

def decode_layer(data):
    encoding,compression=data.get('encoding'),data.get('compression')
    if encoding=='base64':
        b=base64.b64decode(''.join((data.text or '').split()),validate=True)
        if compression=='zlib':b=zlib.decompress(b)
        elif compression=='gzip':b=gzip.decompress(b)
        elif compression:raise ValueError('Unsupported compression: '+compression)
        if len(b)%4:raise ValueError('TMX uint32 byte length mismatch')
        return list(struct.unpack('<'+'I'*(len(b)//4),b))
    if encoding=='csv':return [int(x) for x in (data.text or '').strip().split(',')]
    if encoding is None:return [int(x.get('gid')) for x in data.findall('tile')]
    raise ValueError('Unsupported encoding: '+str(encoding))

def parse_map(path, base):
    m=ET.parse(path).getroot();layers=[];tilesets=[]
    for e in m.findall('layer'):
        layers.append({'attributes':dict(e.attrib),'gids':decode_layer(e.find('data'))})
    for ts in m.findall('tileset'):
        if ts.get('source'):raise ValueError('External TSX unsupported by this importer; retained source needs extension')
        im=ts.find('image');image=None
        if im is not None:
            image=dict(im.attrib);image['path']=(path.parent/im.get('source')).resolve().relative_to(base).as_posix()
        props={}
        for tile in ts.findall('tile'):
            props[tile.get('id')]={x.get('name'):x.get('value',x.text) for x in tile.findall('./properties/property')}
        tilesets.append({'attributes':dict(ts.attrib),'image':image,'tileProperties':props})
    return {'sourceFile':path.relative_to(base).as_posix(),'sha256':sha(path),
      'attributes':dict(m.attrib),'layers':layers,'tilesets':tilesets,'xmlTree':xml_tree(m)}

def import_reference(reference, output=None):
    reference=Path(reference).resolve();output=Path(output or ROOT/'game-data/raw/reference-project')
    if output.resolve().is_relative_to(reference):raise ValueError('Output cannot be inside read-only reference')
    dbfile=reference/'Resources/res/MainData'
    if not dbfile.is_file():raise ValueError('MainData not found: '+str(dbfile))
    known=load(ROOT/'docs/evidence/file-inventory.json');index={x['path']:x for x in known}
    # Check only import inputs against the frozen Phase 0 baseline, not re-audit the repository.
    for p in [dbfile,*sorted((reference/'Resources/res/map').glob('*.tmx'))]:
        rel=p.relative_to(reference).as_posix()
        if rel not in index or sha(p)!=index[rel]['sha256']:raise ValueError('Input differs from Phase 0: '+rel)
    before=sha(dbfile);db=sqlite3.connect(dbfile.as_uri()+'?mode=ro',uri=True);db.row_factory=sqlite3.Row
    tables={};records=[];sources=[];schema={};warnings=[]
    for name,sql in db.execute("SELECT name,sql FROM sqlite_master WHERE type='table' ORDER BY name"):
        quoted='"'+name.replace('"','""')+'"'
        rows=[dict(x) for x in db.execute('SELECT * FROM '+quoted+' ORDER BY rowid')]
        tables[name]=rows
        schema[name]={'sql':sql,'columns':[dict(x) for x in db.execute('PRAGMA table_info('+quoted+')')]}
        for i,row in enumerate(rows):
            key=row.get('id',row.get('saveId',i));src=provenance(name,key,before);sources.append(src)
            records.append({'id':f'reference.{name.lower()}.{key}','table':name,'legacyId':key,
              'sourceRefs':[src['id']],'data':row})
        save(output/'tables'/f'{name}.json',{'table':name,'schema':schema[name],'rows':rows})
    db.close()
    maps=[]
    def file_source(path,file_hash):
        sid='source.reference.file.'+hashlib.sha256(path.encode('utf-8')).hexdigest()[:24]
        sources.append({'id':sid,'source':'REFERENCE_PROJECT','confidence':'LOW','originalVerified':False,
          'licenseStatus':'UNKNOWN','locator':{'repository':'https://github.com/v5100v5100/FengShenBang',
          'commit':COMMIT,'path':path,'sha256':file_hash},'evidenceRefs':[],
          'note':'Exact file in frozen reference snapshot; originality unverified.'})
        return sid
    for row in tables['map']:
        path=contained(reference/'Resources/res',row['res'])
        m=parse_map(path,reference);m['id']=row['id'];m['sourceRefs']=[file_source(m['sourceFile'],m['sha256'])];maps.append(m)
        save(output/'maps'/f"map_{row['id']:03}.json",m)
    assets=[{'id':'asset.reference.'+str(i),'path':x['path'],'sha256':x['sha256'],'bytes':x['bytes'],
             'source':'REFERENCE_PROJECT','originalVerified':False,'sourceRefs':[file_source(x['path'],x['sha256'])]} for i,x in enumerate(known)
            if Path(x['path']).suffix in ('.png','.mp3','.plist','.ccbi','.csb','.ttf')]
    domains={}
    for domain,tabs in DOMAIN_TABLES.items():
        domains[domain]=[r['id'] for r in records if r['table'] in tabs and (domain!='equipment' or r['data'].get('type') in (2,3))]
    for table,fields in {'story':['type','nextSceneId','sceneParam'], 'map_npc':['dir','type','zero_id','switch_on','switch_off'],
      'auto_move':['type','delay','switch_on','switch_off'],'map_object':['type','connect_id'],
      'battle':['type','monster_01','switch_on','switch_off'],'skill':['roleId','targetType','isBattleUse'],
      'role_lv':['roleId','exp','reward_exp','reward_gold'],'object':['type','mini_type','number1']}.items():
        warnings.append({'code':'UNRESOLVED_LEGACY_SEMANTICS','table':table,'fields':fields,
                         'action':'All fields retained raw; not promoted to canonical.'})
    warnings.append({'code':'EQUIPMENT_CLASSIFICATION_INFERRED','detail':'object.type=2/3 is only a research index; rows are not removed from items.'})
    data={'schemaVersion':1,'profile':'reference-research','commit':COMMIT,'sourceDatabaseSha256':before,
          'tableSchemas':schema,'records':records,'domains':domains,'maps':maps,'assets':assets}
    save(output/'dataset.json',data)
    save(output/'manifest.json',{'commit':COMMIT,'counts':{k:len(v) for k,v in tables.items()},
       'recordCount':len(records),'databaseSha256':before,'datasetSha256':sha(output/'dataset.json'),
       'sourceRoot':str(reference),'profile':'REFERENCE_UNVERIFIED','schemaVersion':1})
    save(ROOT/'game-data/provenance/reference-project.json',{'schemaVersion':1,'records':sources})
    save(ROOT/'reports/migration-warnings.json',{'warnings':warnings,'droppedFields':[]})
    if sha(dbfile)!=before:raise RuntimeError('Read-only input changed during import')
    return {'tables':len(tables),'records':len(records),'maps':len(maps),'domains':{k:len(v) for k,v in domains.items()},'output':str(output)}
