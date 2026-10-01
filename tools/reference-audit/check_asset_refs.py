"""Case-sensitive asset path and database naming-convention probes.
Conventions are hypotheses, not evidence of a working resource loader.
"""
import json
import sqlite3
import sys
from pathlib import Path

root=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]);assets=root/'Resources/res'
paths={p.relative_to(assets).as_posix() for p in assets.rglob('*') if p.is_file()}
c=sqlite3.connect((assets/'MainData').as_uri()+'?mode=ro',uri=True);c.row_factory=sqlite3.Row
checks=[]
def add(table,column,row,path,mode):
    checks.append({'table':table,'column':column,'rowId':row['id'],'path':path,'exactCaseExists':path in paths,'interpretation':mode})
for table,column,pattern in [('map','res','{}'),('map_mask','res','resCCB/Object/{}.png'),('monster','res','resCCB/Battle/Monster/{}.png'),('object','res','resCCB/Icon/{}.png'),('skill','skEffect','ccbFiles/Battle/Effect/{}.ccbi'),('battle','bg','resCCB/Battle/Bg/{}.png')]:
    for row in c.execute('select * from '+table):
        if row[column] not in (None,'','0',0):add(table,column,row,pattern.format(row[column]),'literal' if table=='map' else 'naming convention hypothesis')
for row in c.execute('select * from map_npc'):
    for direction in ['Up','Down','Left','Right']:
        add('map_npc','res',row,'ccbFiles/Role/'+row['res']+'_'+direction+'.ccbi','naming convention hypothesis')
for row in c.execute('select * from map_object'):
    if row['res'] not in ('0','',None):add('map_object','res',row,row['res'],'literal')
for row in c.execute('select * from map'):
    if row['bgm']:add('map','bgm',row,f'Sound/bgm_{row["bgm"]:03}.mp3','naming convention hypothesis')
ccbi=json.loads((out/'ccbi-details.json').read_text(encoding='utf-8'))
case_errors=[{'ccbi':x['path'],'reference':r['path']} for x in ccbi for r in x['resources'] if r['path'] not in paths]
result={'databaseChecks':checks,'missingOrDifferentCase':[x for x in checks if not x['exactCaseExists']],'ccbiCaseErrors':case_errors}
(out/'asset-reference-checks.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'checks':len(checks),'missingOrDifferentCase':result['missingOrDifferentCase'],'ccbiCaseErrors':case_errors},ensure_ascii=False))
