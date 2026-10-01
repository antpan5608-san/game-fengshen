"""Validate deliverable links, embedded JSON, and agreement among audit reports."""
import json
import re
from pathlib import Path

root=Path(__file__).resolve().parents[2]
evidence=root/'docs/evidence'
errors=[]
required=['reference-audit','game-data-inventory','architecture','data-schema','migration-plan','implementation-plan']
def require(test,message):
    if not test:errors.append(message)
def read(name):return json.loads((evidence/name).read_text(encoding='utf-8-sig'))
for name in required:require((root/'docs'/f'{name}.md').is_file(),f'Missing {name}')
for p in root.rglob('*.md'):
    text=p.read_text(encoding='utf-8')
    for value in re.findall(r'\]\(([^)]+)\)',text):
        if '://' in value or value.startswith('#'):continue
        target=value.split('#')[0]
        require((p.parent/target).exists(),f'Broken link {p.relative_to(root)} -> {value}')
    for block in re.findall(r'```json\s*\n(.*?)\n```',text,re.S):
        try:json.loads(block)
        except ValueError as exc:errors.append(f'Invalid JSON in {p.name}: {exc}')
s=read('audit-summary.json');inventory=read('file-inventory.json');maps=read('map-inventory.json')
require(len(inventory)==s['filesExpected']==1958,'File inventory mismatch')
require(all(x['matchesPinnedTree'] for x in inventory),'Reference changed')
require(not s['verificationErrors'],'File/format errors')
require(len(maps)==s['tableCounts']['map']==259,'Map count mismatch')
require(s['pngDecoded']==1154 and s['plistParsed']==39,'Asset count mismatch')
require(not read('map-errors.json'),'TMX errors')
require(len(read('map-warnings.json'))==190,'Dimension warning count changed')
require(not s['referenceViolations'],'Unexpected DB reference violations')
require(all(not x['duplicateIds'] for x in read('database-tables.json').values()),'Duplicate source IDs')
ccb=read('ccbi-details.json')
require(len(ccb)==413 and all(x.get('version')==5 and 'error' not in x for x in ccb),'CCBI metadata mismatch')
assets=read('asset-probes.json')
require(len(assets['audio'])==52 and all('error' not in x for x in assets['audio']),'Audio probe mismatch')
require(sum(x['exact5x'] for x in assets['mapPixelReplication'])==28,'Pixel replication mismatch')
refs=read('asset-reference-checks.json')
require(len(refs['databaseChecks'])==4092 and not refs['missingOrDifferentCase'] and not refs['ccbiCaseErrors'],'Asset reference mismatch')
require(not (root/'android').exists() and not (root/'server').exists(),'Phase 0 scope exceeded')
require(not list(root.rglob('*.nes')),'ROM inside deliverable')
result={'phase':'0','result':'PASS' if not errors else 'FAIL','requiredDocuments':len(required),'checks':'documents, local links, JSON examples, evidence consistency, source hashes, Phase 0 scope','errors':errors,'limitations':['No Android/server implementation or build','No gameplay or full playthrough','No ROM analysis','Original fidelity not verified','CCBI node graphs and full audio decoding not executed']}
(evidence/'phase0-validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
if errors:raise SystemExit(1)
