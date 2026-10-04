import sys,importlib.util,itertools,json,hashlib
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));from forensics.fengshen246 import Reader
from py65.devices.mpu6502 import MPU
r=Reader(Path('private-inputs/town02/target.nes').read_bytes())
spec=importlib.util.spec_from_file_location('existing_village4_cpu',Path(__file__).with_name('probe-world-village4.py'));shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)
out=Path('private-derived/world-night8-original-cpu');out.mkdir(parents=True,exist_ok=True)
rows=['map\titem\tpaletteBefore\tpaletteAfter\tapplied'];failures=0
for mid,item,before in itertools.product(range(256),range(24),[0,32,52,63]):
 c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x47]=mid;c.memory[0x6db]=item;c.memory[0x8c]=before;c.memory[0x68a]=91
 shared.call(c,0xe540);expected=(32,32) if (mid,item)==(74,8) else (before,0);got=(c.memory[0x8c],c.memory[0x68a]);failures+=got!=expected
 rows.append('\t'.join(map(str,[mid,item,before,*got])))
assert failures==0
f=out/'night8-map-use-original.tsv';f.write_bytes(('\n'.join(rows)+'\n').encode());print('ORIGINAL_MAP_USE',len(rows)-1,failures,hashlib.sha256(f.read_bytes()).hexdigest(),flush=True)
rows=['quantityBefore\tquantityAfter\tidAfter'];base=r.word(2,0xa194+2)
for q in [1,129]:
 c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x6d6]=1;c.memory[0x6d7]=8;c.memory[base]=8;c.memory[base+64]=q
 shared.call(c,0xa22c);assert(c.memory[base],c.memory[base+64])==(8,129)
 rows.append(f'{q}\t{c.memory[base+64]}\t{c.memory[base]}')
f=out/'night8-bookkeeping-original.tsv';f.write_bytes(('\n'.join(rows)+'\n').encode());print('ORIGINAL_BOOKKEEPING',len(rows)-1,0,hashlib.sha256(f.read_bytes()).hexdigest(),flush=True)
rows=['case\tquantityAfter\titemAfter\tgrantFailed']
for name,initial in {'empty':[],'existing':[(8,1)],'used':[(8,129)],'full':[(i,1)for i in range(16)if i!=8]+[(19,1)]}.items():
 c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x675]=164;c.memory[0x3b]=2
 for j,(i,q)in enumerate(initial):c.memory[base+j]=i;c.memory[base+64+j]=q
 shared.call(c,0xb481)
 matches=[c.memory[base+64+j] for j in range(16) if c.memory[base+j]==8 and c.memory[base+64+j]&127];got=matches[0] if matches else 0
 expected=1 if name in ['empty','existing'] else 129 if name=='used' else 0
 assert got==expected,(name,got);rows.append(f'{name}\t{got}\t8\t{c.memory[0x68e]}')
f=out/'night8-before-text-gift-original.tsv';f.write_bytes(('\n'.join(rows)+'\n').encode());print('ORIGINAL_GIFT',len(rows)-1,0,hashlib.sha256(f.read_bytes()).hexdigest(),flush=True)

# Same original dispatch, including the actual class2 entrance absent from the earlier forest sample.
rows=['source\ttarget\tdirection\tblocked\tocclusion\tmode'];failures=0
for source,target,direction in itertools.product([0,1,2,3,7,8,9],[0,1,2,3,7,8,9],range(1,5)):
 c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768);c.memory[0x71]=5;c.memory[0x99]=source;c.memory[0x98]=target;c.memory[0x6c]=direction
 shared.call(c,0xca98);shared.call(c,0xce35);got=c.memory[0x9c]!=0;expected=target==1 or source==3 and direction>=3
 failures+=got!=expected;rows.append('\t'.join(map(str,[source,target,direction,int(got),c.memory[0x756],c.memory[0x6b]])))
assert failures==0
f=out/'forest100-foot-original.tsv';f.write_bytes(('\n'.join(rows)+'\n').encode());print('ORIGINAL_FOREST100',len(rows)-1,failures,hashlib.sha256(f.read_bytes()).hexdigest(),flush=True)
# Exact teacher164 record and actual shared selector/action1; first flag precedes gift.
from forensics.fengshen246 import extract_npcs
record=bytes.fromhex(extract_npcs(r,164)['records'][1]['rawHex']);assert(record[0],record[1],record[2],record[12],record[13])==(175,2,3,1,2)
fa=r.word(0,0xd493+328);rows=['flagBefore\tmessage\tflagAfter'];failures=0
for flag in range(256):
 c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag;c.memory[0xa2]=2;c.memory[0x400:0x40e]=record;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
 for _ in range(80):
  if c.pc==0xa18a:break
  c.step()
 else:raise RuntimeError('Original teacher164 selector did not finish')
 if c.memory[0xa1]==1:shared.call(c,0xcb84)
 got=(c.memory[0x3b],c.memory[fa]);expected=(3,flag) if flag&2 else (2,flag|2);failures+=got!=expected;rows.append('\t'.join(map(str,[flag,*got])))
assert failures==0
f=out/'teacher164-selector-original.tsv';f.write_bytes(('\n'.join(rows)+'\n').encode());print('ORIGINAL_TEACHER164_SELECTOR',len(rows)-1,failures,hashlib.sha256(f.read_bytes()).hexdigest(),flush=True)
