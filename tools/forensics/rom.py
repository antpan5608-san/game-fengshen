"""Generic NES file forensics. Header declarations are not game identification."""
import hashlib
import math
from pathlib import Path
from PIL import Image
from .common import ROOT, save

SPEC='https://www.nesdev.org/wiki/NES_2.0'

def nes2_size(low, high, unit):
    return (1 << (low >> 2))*(((low & 3)*2)+1) if high==15 else ((high<<8)|low)*unit

def ram_size(shift):return 0 if shift==0 else 64<<shift

def parse_header(data):
    if len(data)<16:raise ValueError('Truncated NES header')
    h=data[:16]
    if h[:4]!=b'NES\x1a':raise ValueError('Invalid iNES magic')
    ident=h[7]&12
    if ident not in (0,8):raise ValueError('Unsupported/archaic header variant; do not guess NES version')
    nes2=ident==8;warnings=[]
    prg=nes2_size(h[4],h[9]&15,16384) if nes2 else h[4]*16384
    chr_size=nes2_size(h[5],h[9]>>4,8192) if nes2 else h[5]*8192
    mapper=(h[6]>>4)|(h[7]&240)|((h[8]&15)<<8 if nes2 else 0)
    trainer=512 if h[6]&4 else 0;prg_start=16+trainer;chr_start=prg_start+prg;end=chr_start+chr_size
    if not prg:raise ValueError('No PRG ROM declared')
    if end>len(data):raise ValueError(f'Truncated ROM: header requires {end} bytes, got {len(data)}')
    battery=bool(h[6]&2)
    if not nes2 and any(h[12:16]):warnings.append('Dirty iNES padding: mapper upper nibble may be unreliable; retained header declaration.')
    if end<len(data):warnings.append('Trailing bytes present: not silently discarded; may be miscellaneous/PlayChoice/garbage.')
    if nes2:
        prgram={'volatileBytes':ram_size(h[10]&15),'nonvolatileBytes':ram_size(h[10]>>4),'confidence':'HEADER_DECLARED'}
        chrram={'volatileBytes':ram_size(h[11]&15),'nonvolatileBytes':ram_size(h[11]>>4),'confidence':'HEADER_DECLARED'}
    else:
        prgram={'legacyDeclaredBytes':h[8]*8192 if h[8] else None,'nonvolatileBytes':None,
                'confidence':'UNKNOWN','note':'Battery flag is not a measured RAM size; byte8=0 is unspecified.'}
        chrram={'volatileBytes':None,'nonvolatileBytes':None,'confidence':'UNKNOWN'}
    if chr_size==0: warnings.append('No CHR ROM: CHR RAM path indicated; tile uploads/compression require mapper/runtime research.')
    return {'format':'NES_2.0' if nes2 else 'iNES','headerHex':h.hex(),'mapper':mapper,
      'mapperConfidence':'HEADER_DECLARED' if not any(h[12:16]) or nes2 else 'LOW',
      'submapper':h[8]>>4 if nes2 else None,'mirroringDeclared':'FOUR_SCREEN_OR_MAPPER_SPECIFIC' if h[6]&8 else ('VERTICAL' if h[6]&1 else 'HORIZONTAL'),
      'mirroringNote':'Runtime mirroring may be mapper-controlled; declaration only.',
      'batteryBackedPersistentMemoryFlag':battery,'prgRam':prgram,'chrRam':chrram,
      'trainer':bool(trainer),'trainerBytes':trainer,'prgRomBytes':prg,'chrRomBytes':chr_size,
      'chrMode':'CHR_ROM' if chr_size else 'CHR_RAM_INDICATED','consoleType':h[7]&3,
      'timingMode':h[12]&3 if nes2 else None,'miscRomCount':h[14]&3 if nes2 else None,
      'sections':{'header':{'offset':0,'length':16},'trainer':{'offset':16,'length':trainer},
        'prg':{'offset':prg_start,'length':prg},'chr':{'offset':chr_start,'length':chr_size},'trailing':{'offset':end,'length':len(data)-end}},
      'warnings':warnings,'gameIdentity':'UNKNOWN','originalVersionVerified':False}

def analyze(path):
    p=Path(path);data=p.read_bytes();r={'fileName':p.name,'fileSize':len(data),
      'sha256':hashlib.sha256(data).hexdigest(),'sha1':hashlib.sha1(data).hexdigest(),'md5':hashlib.md5(data).hexdigest()}
    r['fingerprint']={'id':'rom.sha256.'+r['sha256'],'fileSha256':r['sha256'],
       'prgSha256':None,'chrSha256':None,'prgChrSha256':None,
       'identityBasis':'file bytes, never filename; payload hashes exclude header, trainer and trailing bytes'}
    try:
        r.update(parse_header(data));r['status']='STRUCTURALLY_VALID'
        chunks={name:data[s['offset']:s['offset']+s['length']] for name,s in r['sections'].items() if name in ('prg','chr')}
        r['fingerprint'].update(prgSha256=hashlib.sha256(chunks['prg']).hexdigest(),
          chrSha256=hashlib.sha256(chunks['chr']).hexdigest() if chunks['chr'] else None,
          prgChrSha256=hashlib.sha256(chunks['prg']+chunks['chr']).hexdigest())
    except ValueError as e:r.update(status='INVALID',error=str(e))
    return r

def scan_roms(directory=None):
    d=Path(directory or ROOT/'reference/rom');files=sorted(p for p in d.rglob('*') if p.is_file() and p.suffix.lower()=='.nes') if d.exists() else []
    result={'status':'WAITING_FOR_ROM' if not files else 'ROM_FILES_ANALYZED','roms':[analyze(p) for p in files],
            'note':'Structural validity does not prove this is an unmodified FengShenBang ROM.'}
    for p,entry in zip(files,result['roms']):entry['relativePath']=p.relative_to(d).as_posix()
    save(ROOT/'reports/rom-analysis.json',result)
    return result

def section(path,name):
    data=Path(path).read_bytes();h=parse_header(data)
    if name=='file':return data,0
    if name not in h['sections']:raise ValueError('Unknown section')
    s=h['sections'][name];return data[s['offset']:s['offset']+s['length']],s['offset']

def dump(path,out):
    path=Path(path).resolve();out=Path(out).resolve()
    if out==path or path.is_relative_to(out):raise ValueError('Output directory must not contain the input ROM')
    data=path.read_bytes();h=parse_header(data);out.mkdir(parents=True,exist_ok=True)
    for name in ('prg','chr','trainer'):
        s=h['sections'][name]
        if s['length']:(out/(name+'.bin')).write_bytes(data[s['offset']:s['offset']+s['length']])
    save(out/'manifest.json',analyze(path));return {'output':str(out),'sections':h['sections']}

def banks(path,name='prg',size=16384):
    if size<=0:raise ValueError('Bank inspection chunk size must be positive')
    data,base=section(path,name);result=[]
    for start in range(0,len(data),size):
        block=data[start:start+size];counts=[block.count(i) for i in set(block)]
        entropy=-sum((n/len(block))*math.log2(n/len(block)) for n in counts)
        result.append({'index':start//size,'sectionOffset':start,'fileOffset':base+start,'length':len(block),
         'sha256':hashlib.sha256(block).hexdigest(),'entropy':round(entropy,4)})
    return {'note':'Fixed file chunks, not asserted mapper banks or CPU addresses.','section':name,'chunks':result}

def search(path,pattern,name='file'):
    tokens=pattern.split()
    if not tokens or all(t=='??' for t in tokens):raise ValueError('Provide at least one concrete byte; use e.g. 4E 45 ?? 1A')
    wanted=[None if t=='??' else int(t,16) for t in tokens]
    if any(v is not None and not 0<=v<=255 for v in wanted):raise ValueError('Invalid byte')
    data,base=section(path,name);result=[]
    first=next(i for i,v in enumerate(wanted) if v is not None);anchor=bytes([wanted[first]])
    pos=data.find(anchor,first)
    while pos!=-1:
        start=pos-first
        if start+len(wanted)<=len(data) and all(v is None or data[start+i]==v for i,v in enumerate(wanted)):
            result.append({'fileOffset':base+start,'sectionOffset':start,'length':len(wanted)})
        pos=data.find(anchor,pos+1)
    return {'pattern':pattern,'section':name,'matches':result,'meaning':'UNKNOWN; byte match is not dialogue or table verification.'}

def tile_image(data,columns=16):
    if len(data)%16 or not data:raise ValueError('Tile bytes must be a nonempty multiple of 16')
    if not 1<=columns<=128:raise ValueError('Tile columns out of range')
    n=len(data)//16;im=Image.new('RGB',(columns*8,((n+columns-1)//columns)*8),(40,40,40))
    shades=[(0,0,0),(85,85,85),(170,170,170),(255,255,255)]
    for t in range(n):
        for y in range(8):
            lo,hi=data[t*16+y],data[t*16+y+8]
            for x in range(8):im.putpixel(((t%columns)*8+x,(t//columns)*8+y),shades[((lo>>(7-x))&1)|(((hi>>(7-x))&1)<<1)])
    return im

def export_tiles(path,out,name='chr',offset=0,count=None):
    data,base=section(path,name)
    if not data:raise ValueError('No CHR ROM data; no fabricated tiles exported')
    length=(count*16) if count is not None else len(data)-offset
    if offset<0 or length<=0 or offset+length>len(data):raise ValueError('Tile range outside section')
    out=Path(out).resolve()
    if out==Path(path).resolve():raise ValueError('Cannot overwrite ROM')
    out.parent.mkdir(parents=True,exist_ok=True);tile_image(data[offset:offset+length]).save(out)
    return {'png':str(out),'fileOffset':base+offset,'length':length,'palette':'synthetic grayscale indices, not NES game palette','meaning':'raw 2bpp tile candidate; not identified as glyph/sprite/map'}
