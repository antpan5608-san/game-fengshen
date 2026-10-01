"""Read CCB v5 caches and sequence metadata, not the entire node graph.
Format reference: cocos2d-x v3 CCBReader.cpp readHeader/readInt/readSequences.
"""
import collections
import json
import struct
import sys
from pathlib import Path

class Reader:
    def __init__(self, data): self.data, self.pos, self.bit = data, 4, 0
    def byte(self):
        n=self.data[self.pos];self.pos+=1;return n
    def get_bit(self):
        n=(self.data[self.pos]>>self.bit)&1;self.bit+=1
        if self.bit==8:self.bit=0;self.pos+=1
        return n
    def integer(self, signed=False):
        bits=0
        while not self.get_bit():
            bits+=1
            if bits>32:raise ValueError('invalid CCB integer')
        n=1<<bits
        for i in reversed(range(bits)):n|=self.get_bit()<<i
        if self.bit:self.bit=0;self.pos+=1
        return (n//2 if n%2 else -n//2) if signed else n-1
    def floating(self):
        t=self.byte()
        if t<4:return [0,1,-1,0.5][t]
        if t==4:return float(self.integer(True))
        n=struct.unpack_from('<f',self.data,self.pos)[0];self.pos+=4;return n
    def string(self):
        n=self.byte()*256+self.byte();b=self.data[self.pos:self.pos+n]
        if len(b)!=n:raise ValueError('truncated string')
        self.pos+=n;return b.decode('utf-8')

root=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]);rows=[]
for p in sorted((root/'Resources/res').rglob('*.ccbi')):
    r=Reader(p.read_bytes());d={'path':p.relative_to(root).as_posix()}
    try:
        assert r.data[:4]==b'ibcc'
        d['version']=r.integer();assert d['version']==5
        d['jsControlled']=bool(r.byte());cache=[r.string() for _ in range(r.integer())]
        d['stringCount']=len(cache)
        d['resources']=[{'path':s,'exists':(root/'Resources/res'/s).is_file()} for s in cache if s.endswith(('.png','.plist','.ccbi','.mp3','.ttf')) and '/' in s]
        d['sequences']=[]
        for _ in range(r.integer()):
            seq={'duration':r.floating(),'name':cache[r.integer()],'id':r.integer(),'chainedId':r.integer(True)}
            seq['callbacks']=[{'time':r.floating(),'name':cache[r.integer()],'type':r.integer()} for _ in range(r.integer())]
            seq['sounds']=[{'time':r.floating(),'file':cache[r.integer()],'pitch':r.floating(),'pan':r.floating(),'gain':r.floating()} for _ in range(r.integer())]
            d['sequences'].append(seq)
        d['autoplay']=r.integer(True)
        d['nodeGraphOffset']=r.pos
        d['limitation']='Node property timelines and callbacks are not interpreted or executed.'
    except Exception as e:d['error']=str(e)
    rows.append(d)
out.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'files':len(rows),'errors':[x for x in rows if 'error' in x],'versions':dict(collections.Counter(x.get('version') for x in rows)),'sequences':sum(len(x.get('sequences',[])) for x in rows),'missingResourceReferences':sum(not r['exists'] for x in rows for r in x.get('resources',[]))},ensure_ascii=False))
