"""Fingerprint-bound, bounded FengShenBang investigation; no reference-driven decoding.

All offsets are absolute file offsets. CPU addresses require a 32 KiB module.
The base investigation decodes group124/message10; vertical_slice augments it
with separately evidenced opening contexts. Other fonts remain event dependent.
"""
import collections
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw
from .common import ROOT, load, save
from . import rom

SHA256='f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25'
PROFILE='fengshen-m246-'+SHA256[:16]
# Manually transcribed from isolated CHR glyphs, then checked against two live pages.
# Deliberately scoped to characters used in the observed message; not GB2312/Big5.
LOW={0x00:'都',0x01:'好',0x02:'惹',0x03:'連',0x04:'月',0x05:'害',0x06:'陳',0x07:'關',
 0x08:'近',0x09:'患',0x0a:'尺',0x0b:'總',0x0c:'官',0x0d:'何',0x0e:'交',0x0f:'當',
 0x10:'兒',0x1f:'個',0x20:'要',0x21:'吧',0x24:'哪',0x25:'的',0x26:'龍',0x27:'降',0x28:'我',0x2a:'村'}
HIGH={0x80:'做',0x81:'事',0x82:'火',0x83:'三',0x84:'豪',0x85:'得',0x86:'塘',0x87:'附',
 0x88:'水',0x89:'二',0x8a:'身',0x8b:'兵',0x8c:'如',0x8d:'向',0x8e:'代',0x8f:'沒',0x90:'子',
 0x9e:'是',0x9f:'為',0xa0:'走',0xa1:'這',0xa4:'吒',0xa5:'四',0xa6:'王',0xa7:'雨',0xa8:'全',0xa9:'海',0xaa:'你'}
CHARS={**LOW,**HIGH}
PUNCT={0x40:'『',0x41:'』',0x42:'，',0x43:'。',0xc0:'！',0xc1:'？'}

def digest(data):return hashlib.sha256(data).hexdigest()

def extract_town_shops(reader):
    """Only town 0's three witnessed merchants. IDs are category-local ROM IDs."""
    shops=[]
    for map_id,kind,door,spawn,type_id in [(17,'weapon',[13,18],[7,12],2),
            (18,'armor',[6,19],[7,12],3),(19,'medicine',[24,25],[6,12],0)]:
        root=reader.word(2,0xe685+2*type_id);stock=reader.word(2,root)
        ids=[]
        for i in range(16):
            value=reader.read(2,stock+i)[0]
            if value==255:break
            ids.append(value)
        else:raise ValueError('Unterminated town stock')
        prices=reader.word(2,0xe68d+2*type_id)
        names=reader.word(2,0xe60e+2*type_id)
        rows=[]
        for item_id in ids:
            name=reader.word(2,names+2*item_id)
            end=reader.read(2,name,16).index(255)+1
            rows.append({'originalId':item_id,'price':reader.word(2,prices+2*item_id),
                'priceRange':reader.span(2,prices+2*item_id,2,'Town buy price'),
                'nameHex':reader.read(2,name,end).hex(),
                'nameRange':reader.span(2,name,end,'Original menu name stream')})
        shops.append({'mapId':map_id,'kind':kind,'typeId':type_id,'door':door,'spawn':spawn,
            'stockRange':reader.span(2,stock,len(ids)+1,'Town 0 merchant stock'),
            'rows':rows,'confidence':'GAMEPLAY_VERIFIED'})
    if [[x['originalId'] for x in s['rows']] for s in shops]!=[[0,1,2],[0,1,28],[0,6]]:
        raise ValueError('Pinned town stock changed')
    return {'romSha256':SHA256,'shops':shops,
        'sourceEdges':{2:['LEFT'],3:['RIGHT'],4:['UP','LEFT'],5:['DOWN','LEFT'],
            6:['UP'],7:['DOWN'],8:['UP','RIGHT'],9:['DOWN','RIGHT']},
        'targetEdges':{2:['RIGHT'],3:['LEFT'],4:['DOWN','RIGHT'],5:['UP','RIGHT'],
            6:['DOWN'],7:['UP'],8:['DOWN','LEFT'],9:['UP','LEFT']},
        'routines':[reader.span(0,0xcb7d,20,'Town source collision dispatch'),
            reader.span(0,0xcee4,20,'Town target collision dispatch'),
            reader.span(0,0xd214,115,'Directional physical barriers'),
            reader.span(0,0xcbe3,70,'Class 21/22/23 original indoor entry'),
            reader.span(2,0xc02f,92,'Merchant stock selection'),
            reader.span(2,0xc186,57,'Buy price lookup'),
            reader.span(2,0xbeb8,50,'Sell: divide price by two, zero quotient becomes one'),
            reader.span(2,0xee8b,25,'Unsigned 24-bit by 8-bit division used by sell price'),
            reader.span(2,0xa0eb,173,'Inventory capacity and insertion'),
            reader.span(2,0xce88,124,'Equipment preview composition')],
        'limits':['Inn/residential services not enabled.',
            'Herb sell price 7 has a witnessed transaction; other sell prices follow the same scoped ROM routine.',
            'Selling cross-category goods at a different merchant remains a development restriction, not an original rule.',
            'Equipment replacement requires an explicit remove then equip; automatic replacement side effects remain unverified.']}

def extract_world_service_catalog(reader):
    """Original category-local stocks/prices/slot membership, without guessing effects or text."""
    if digest(reader.data)!=SHA256:raise ValueError('Service catalog requires target ROM fingerprint')
    categories={0:'medicine',2:'weapon',3:'armor'};stocks=[];items={}
    cross_hand=set(reader.read(2,0xaa7a,17))-{255}
    slots={slot:set(reader.read(2,address,length))-{255} for slot,address,length in
        [('rightHand',0xef88,19),('body',0xef9b,11),('feet',0xefa6,10)]}
    for type_id,category in categories.items():
        root=reader.word(2,0xe685+2*type_id);prices=reader.word(2,0xe68d+2*type_id)
        names=reader.word(2,0xe60e+2*type_id)
        for context in range(18):
            stock=reader.word(2,root+context*2);ids=[]
            for offset in range(17):
                value=reader.read(2,stock+offset)[0]
                if value==255:break
                ids.append(value)
            else:raise ValueError('Stock exceeds original sixteen slots')
            if len(ids)>16 or len(set(ids))!=len(ids):raise ValueError('Invalid original stock list')
            stocks.append({'category':category,'contextIndex':context,'originalIds':ids,
                'stockPointerSource':reader.span(2,root+context*2,2,'Original stock context pointer'),
                'stockSource':reader.span(2,stock,len(ids)+1,'Original terminated stock')})
            for item in ids:
                key=(category,item)
                if key in items:continue
                pointer=reader.word(2,names+2*item)
                raw=reader.read(2,pointer,min(32,0x10000-pointer))
                if 255 not in raw:raise ValueError('Unterminated item name stream')
                raw=raw[:raw.index(255)+1];price=reader.word(2,prices+2*item)
                row={'id':'rom.item.0' if key==('weapon',0) else f'rom.{category}.{item}',
                    'category':category,'originalId':item,'buyPrice':price,'sellPrice':max(1,price//2),
                    'maxCount':10,'nameHex':raw.hex(),'displayNameStatus':'REQUIRES_EXISTING_TEXT_OR_SOURCED_PROVISIONAL_MAPPING',
                    'priceSource':reader.span(2,prices+2*item,2,'Original unsigned purchase price'),
                    'nameSource':reader.span(2,pointer,len(raw),'Original terminated menu text'),
                    'useEffect':'NOT_INFERRED'}
                if category in ('weapon','armor'):
                    typ=0 if category=='weapon' else 1;contribution=reader.word(2,0xe95e+2*typ)+item*2
                    row['contribution']=reader.word(2,contribution)
                    row['contributionSource']=reader.span(2,contribution,2,'Original equipment contribution')
                    legal=[slot for slot,values in slots.items() if item in values and
                        (slot=='rightHand')==(category=='weapon')]
                    row['nezhaPermittedByCategoryList']=bool(legal)
                    row['slotStatus']='NEEDS_CROSS_HAND_TRANSACTION' if category=='weapon' and item in cross_hand else 'ORIGINAL_SLOT_FILTER_VERIFIED'
                    row['nezhaPermittedSlots']=legal if row['slotStatus']=='ORIGINAL_SLOT_FILTER_VERIFIED' else []
                    row['crossHandOccupancy']=category=='weapon' and item in cross_hand
                    row['listMembershipCandidates']=legal
                    row['equipmentOperation']='REQUIRES_ORIGINAL_SLOT_AND_TRANSACTION_RULES'
                items[key]=row
    return {'romSha256':SHA256,'stocks':stocks,'items':list(items.values()),
        'stockDispatch':{'normalCallerRange':[0,15],'map81Context':16,'map21Context':17,
            'source':reader.span(2,0xc02f,92,'Original normal/special merchant selection')},
        'innPrices':[reader.word(2,0xc6bb+2*i) for i in range(16)],
        'innPriceSource':reader.span(2,0xc6bb,32,'Original sixteen normal lodging prices'),
        'equipmentSelectorSource':reader.span(2,0xaa8b,124,'Actual owner, slot and inventory candidate filter'),
        'crossHandSource':reader.span(2,0xaa7a,17,'Original cross-hand occupying weapon IDs and FF'),
        'limitations':['Stocks/prices do not establish all NPC appearance or route conditions',
            'No effect, slot type, reward, or script condition inferred from a display name']}

def cpu_offset(module,address,length=1):
    if not 0<=module<16 or not 0x8000<=address or address+length>0x10000:
        raise ValueError('CPU range escapes selected PRG module')
    return 16+module*0x8000+address-0x8000

class Reader:
    def __init__(self,data,verify=True):
        self.data=data;self.header=rom.parse_header(data)
        if verify and digest(data)!=SHA256:raise ValueError('Unsupported ROM fingerprint: never apply a profile by filename')
    def read(self,module,address,length=1):
        p=cpu_offset(module,address,length);v=self.data[p:p+length]
        if len(v)!=length:raise ValueError('Truncated PRG range')
        return v
    def word(self,module,address):return int.from_bytes(self.read(module,address,2),'little')
    def span(self,module,address,length,meaning):
        b=self.read(module,address,length)
        return {'offset':cpu_offset(module,address,length),'length':length,'sha256':digest(b),'module':module,'cpuAddress':address,'meaning':meaning}

def glyph_pixels(data,base,code):
    """A character is four consecutive 8x8 tiles; bit7 selects one bitplane."""
    if code&0x40 or (code&0x3f)>60:raise ValueError('Not an ordinary glyph code')
    tile=10+4*(code&63);plane=1 if code&128 else 0
    if base<0 or base+(tile+4)*16>len(data):raise ValueError('Glyph exceeds CHR range')
    pixels=[]
    for y in range(16):
        pixels.append([((data[base+(tile+(y//8)*2+x//8)*16+(y%8)+plane*8]>>(7-x%8))&1) for x in range(16)])
    return pixels

def decode_tokens(data,charset=None):
    charset=CHARS if charset is None else charset;text=[];tokens=[];complete=False
    for index,code in enumerate(data):
        if code==0xc3:tokens.append({'index':index,'code':code,'control':'END'});complete=True;break
        if code==0xc2:tokens.append({'index':index,'code':code,'control':'NEWLINE'});text.append('\n');continue
        char=charset.get(code,PUNCT.get(code))
        tokens.append({'index':index,'code':code,'character':char,'status':'DECODED' if char else 'UNKNOWN'})
        text.append(char if char is not None else f'<{code:02X}>')
    if not complete:raise ValueError('No terminator inside bounded text range')
    return {'text':''.join(text),'tokens':tokens,'consumed':len(tokens),'unknownCodes':sorted({t['code'] for t in tokens if t.get('status')=='UNKNOWN'})}

def text_pointer(reader,group,message):
    if not 0<=group<256 or not 0<=message<128:raise ValueError('Text index outside 8-bit dispatch')
    root=0x8a62+2*group;table=reader.word(3,root);entry=table+2*message;pointer=reader.word(3,entry)
    reader.read(3,pointer)
    return pointer,[reader.span(3,root,2,'Text group pointer'),reader.span(3,entry,2,'Message pointer')]

def extract_text(reader,group,message,decode=False,max_bytes=512):
    ptr,spans=text_pointer(reader,group,message);raw=reader.read(3,ptr,min(max_bytes,0x10000-ptr))
    end=raw.find(b'\xc3')
    if end<0:raise ValueError('No bounded text terminator')
    raw=raw[:end+1];result={'id':f'rom.dialogue.{group}.{message}','group':group,'messageIndex':message,
        'rawHex':raw.hex(),'range':reader.span(3,ptr,len(raw),'C3-terminated text byte stream'),
        'pointerEvidence':spans,'confidence':'UNKNOWN','fontContext':None,'text':None}
    if decode:
        result.update(decode_tokens(raw));result['fontContext']={'chr2kBanks':[78,79],'glyphBaseTile':10,'bit7':'bitplane'}
        result['confidence']='VERIFIED' if not result['unknownCodes'] else 'LOW'
    return result

def extract_map(reader,map_id):
    """16x15 chunks, including the traced map16 module15 extension."""
    if not 0<=map_id<256:raise ValueError('Invalid map ID')
    hdr=reader.word(0,0xdcfc+2*map_id);header=reader.read(0,hdr,5)
    tileset=header[0];max_x=int.from_bytes(header[1:3],'little');max_y=int.from_bytes(header[3:5],'little')
    if max_x%16 or max_y%16:raise ValueError('Unproven map dimension encoding')
    width=max_x//16+1;height=max_y//16+1
    if width>256 or (height>=150 and map_id!=16) or tileset>7:raise ValueError('Special/unsupported map layout: investigate separately')
    module=reader.read(0,0xe22b+map_id)[0];table=reader.word(0,0xdb9e+map_id*2)
    # The game uses the high byte of maxX + 1 as chunk row stride (B547-B57D).
    stride=header[2]+1
    if stride!=(width+15)//16:raise ValueError('Unproven chunk stride')
    chunks=[];grid=[[0]*width for _ in range(height)]
    for cy in range((height+14)//15):
        for cx in range(stride):
            pointer_address=table+2*(cy*stride+cx);chunk_module=module;row_bias=0
            if map_id==16 and cy>=10:
                pointer_address=(0xec59 if cy==10 else 0xec79)+2*cx
                chunk_module=15;row_bias=0 if cy<=11 else (cy-11)*15
            p=reader.word(0,pointer_address)+row_bias*16;rows=min(15,height-cy*15)
            data=reader.read(chunk_module,p,16*rows)
            chunks.append({'chunkX':cx,'chunkY':cy,'rows':rows,'rowBias':row_bias,'pointer':reader.span(0,pointer_address,2,'Metatile chunk pointer'),
                           **reader.span(chunk_module,p,16*rows,'Row-major chunk; world y>=150 uses PRG module15, y180 follows the actual address calculation')})
            for yy in range(rows):
                for xx in range(16):
                    x=cx*16+xx;y=cy*15+yy
                    if x<width and y<height:grid[y][x]=data[yy*16+xx]
    mt=reader.word(0,0xdb6e+tileset*2);attrs=reader.word(0,0xdb7e+tileset*2);coll=reader.word(0,0xdb8e+tileset*2)
    metatiles=reader.read(module,mt,1024);attributes=reader.read(module,attrs,256)
    bank=reader.read(0,0xa296+map_id)[0]
    if map_id==16:
        for address,length in [(mt,1024),(attrs,256),(coll,256)]:
            if reader.read(module,address,length)!=reader.read(15,address,length):raise ValueError('World extension has different metatile semantics')
    return {'id':f'rom.map.{map_id}','romMapId':map_id,'width':width,'height':height,'tileWidth':16,'tileHeight':16,
      'grid':grid,'gridSha256':digest(bytes(v for row in grid for v in row)),'tilesetId':tileset,'module':module,
      'chr2kBanks':[bank,bank+1],'header':reader.span(0,hdr,5,'Tileset and maximum pixel coordinates'),
      'headerPointer':reader.span(0,0xdcfc+2*map_id,2,'Map header pointer'),
      'chunkTablePointer':reader.span(0,0xdb9e+map_id*2,2,'Map chunk table pointer'),
      'moduleEvidence':reader.span(0,0xe22b+map_id,1,'PRG module used by renderer'),
      'chrEvidence':reader.span(0,0xa296+map_id,1,'First background CHR 2 KiB bank'),
      'metatileRange':reader.span(module,mt,1024,'Four PPU tile indices per metatile, TL TR BL BR'),
      'attributeRange':reader.span(module,attrs,256,'Renderer attribute lookup; palette meaning only'),
      'collisionCandidate':{'cpuAddress':coll,'module':module,'confidence':'UNKNOWN','note':'Pointer in $4C/$4D; collision bit semantics unproven'},
      'metatiles':[list(metatiles[i:i+4]) for i in range(0,1024,4)],'attributes':list(attributes),
      'chunks':chunks,'confidence':'HIGH','gameplayVerified':False,'transitions':None}

def extract_default_map_palette(reader,map_id):
    """Original default selector/write path, including the PPU's universal-color aliases.

    This does not claim scripted palette changes or every state is verified.
    """
    if not 0<=map_id<175:raise ValueError('Palette ID outside geometry table')
    selector=0xe0c3+2*map_id
    background,sprites=reader.read(0,selector,2)
    bp=reader.word(0,0xe3a6+2*background);sp=reader.word(0,0xe438+2*sprites)
    palette=list(reader.read(0,bp,16))+list(reader.read(0,sp,16))
    if any(c not in range(64) for c in palette):raise ValueError('Default scene palette has unsupported color codes')
    # The original queues background first, sprites second. $3F10/14/18/1C mirror $3F00/04/08/0C.
    for index in (0,4,8,12):palette[index]=palette[index+16]
    return {'mapId':map_id,'palette':palette,'backgroundSelector':background,'spriteSelector':sprites,
        'confidence':'STATIC_ROM_DEFAULT_ONLY','remainingUnknown':['Scripted palette/lighting transitions and non-default event states'],
        'source':[reader.span(0,selector,2,'Map background/sprite palette selector pair'),
            reader.span(0,0xe3a6+2*background,2,'Background palette pointer'),
            reader.span(0,0xe438+2*sprites,2,'Sprite palette pointer'),
            reader.span(0,bp,16,'Original default background colors'),reader.span(0,sp,16,'Original sprite colors')],
        'routineEvidence':[reader.span(0,0xa7f9,44,'Signed map ID selector dispatch'),
            reader.span(0,0xa390,110,'Original queued background then sprite palette writes')]}

def extract_world_inventory(reader,packaged_ids=(),runtime_evidence=None):
    """Enumerate physical tables and shared contexts; parsing is not gameplay verification.

    Keep the extra header slot unresolved rather than treating Reference's count or
    a successful decoder as the effective-world denominator.
    """
    if digest(reader.data)!=SHA256:raise ValueError('World inventory requires the target ROM fingerprint')
    chunk_start,header_start,npc_start,exit_start=0xdb9e,0xdcfc,0xb311,0xdc69
    geometry_slots=(header_start-chunk_start)//2
    if geometry_slots!=175:raise ValueError('Original map table boundary changed')
    # Initial base/indoor overlay references locate the first list; extend through
    # that physical boundary, including later event-only NPC contexts.
    first_list=min(reader.word(8,npc_start+2*i) for i in range(190))
    npc_slots=(first_list-npc_start)//2
    if first_list<npc_start or (first_list-npc_start)%2:raise ValueError('NPC/context table is misaligned')
    npc_ptrs=[reader.word(8,npc_start+2*i) for i in range(npc_slots)]
    if min(npc_ptrs)!=npc_start+npc_slots*2:raise ValueError('NPC/context table boundary changed')
    runtime_evidence=runtime_evidence or {};packed=set(packaged_ids);maps=[];incoming=collections.defaultdict(list)
    for mid in range(geometry_slots):
        original=extract_map(reader,mid);classes=reader.read(original['module'],original['collisionCandidate']['cpuAddress'],256)
        doors=collections.defaultdict(list)
        for y,row in enumerate(original['grid']):
            for x,t in enumerate(row):
                if classes[t] in (21,22,23,24,25):doors[classes[t]].append([x,y])
        pointer=reader.word(8,exit_start+2*mid);exits=[]
        for i in range(128):
            address=pointer+5*i
            if reader.read(8,address)[0]==254:break
            raw=reader.read(8,address,5);destination=raw[2]
            if destination!=254 and destination>=geometry_slots:raise ValueError('Exit refers outside enumerated geometry')
            row={'trigger':list(raw[:2]),'targetMapId':None if destination==254 else destination,
                'kind':'RETURN_TO_CALLER' if destination==254 else 'DIRECT_OR_EDGE',
                'targetCell':list(raw[3:]),'source':reader.span(8,address,5,'Original exit dispatch row'),
                'conditionStatus':'NEEDS_DISPATCH_AND_EVENT_CONDITIONS'}
            exits.append(row)
            if destination!=254:incoming[destination].append({'fromMapId':mid,'trigger':row['trigger']})
        else:raise ValueError('Unterminated map exit table')
        maps.append({'mapId':mid,'identity':'ORIGINAL_GEOMETRY_SLOT','width':original['width'],'height':original['height'],
            'tilesetId':original['tilesetId'],'gridSha256':original['gridSha256'],'headerSource':original['header'],
            'defaultPalette':extract_default_map_palette(reader,mid),
            'decode':'PASS','packaged':mid in packed,'appRender':runtime_evidence.get(str(mid),'NOT_RUN'),
            'normalReachability':'NOT_VERIFIED_IN_THIS_INVENTORY','doorCandidates':dict(doors),'exits':exits,
            'remaining':['NPC/event/terrain/encounter conditions require scoped conversion and runtime proof']})
    contexts=[]
    for mid in range(npc_slots):
        pointer=npc_ptrs[mid];records=[]
        for index in range(128):
            address=pointer+index*14
            if reader.read(8,address)[0]==255:break
            b=reader.read(8,address,14)
            position=[int.from_bytes(b[4:6],'little'),int.from_bytes(b[6:8],'little')]
            # A8F5/A90E/A92E encodes an NPC cell as cell*16 + $78, not screen pixels.
            cell=[(v-0x78)//16 for v in position] if all(v>=0x78 and (v-0x78)%16==0 for v in position) else None
            records.append({'index':index,'textGroup':b[0],'firstMessage':b[1],'repeatMessage':b[2],
                'pixelPosition':position,'positionEncoding':'CELL_TIMES_16_PLUS_120','cell':cell,
                'appearanceAndBehavior':'NEEDS_NPC_DISPATCH','source':reader.span(8,address,14,'Original NPC record')})
        else:raise ValueError('Unterminated NPC/context list')
        contexts.append({'contextId':mid,'kind':'BASE_MAP' if mid<geometry_slots else 'NPC_OVERLAY_ONLY',
            'records':records,'runtime':'NOT_RUN'})
    catalog=extract_world_service_catalog(reader)
    stocks={(x["category"],x["contextIndex"]):x for x in catalog["stocks"]}
    services=[];kinds={21:('weapon',17),22:('armor',18),23:('medicine',19),24:('other-clinic-candidate',20),25:('inn',22)}
    village_bases=list(reader.read(0,0xd2b7,16));overlay=list(reader.read(0,0xd2c7,96))
    for mid in range(16):
        for klass,cells in maps[mid]['doorCandidates'].items():
            kind,interior=kinds[klass]
            context_index=village_bases[mid]+interior-17
            additional=context_index
            overlay_id=overlay[additional]
            room=maps[interior]
            overlay_rows=contexts[overlay_id]['records']
            services.append({'id':f'rom.service.{mid}.{kind}','kind':kind,'callerMapId':mid,
                'interiorMapId':interior,'entryCells':cells,'npcOverlayId':overlay[context_index],
                'baseNpcContextId':interior,
                'additionalNpcCandidates':[{'contextId':overlay_id,'npcIndex':n['index'],'cell':n['cell'],
                    'positionInRoom':n['cell'] is not None and 0<=n['cell'][0]<room['width'] and 0<=n['cell'][1]<room['height'],
                    'appearance':'NEEDS_NPC_STATE_DISPATCH'} for n in overlay_rows],
                'contextSource':reader.span(0,0xd2c7+context_index,1,'Original additional indoor NPC context'),
                'conditions':'Original village access and NPC/state dispatch retained; not a supply prerequisite',
                'operation':'NOT_IMPLEMENTED' if (mid!=0 or kind not in ('weapon','armor','medicine','inn')) else 'CANDIDATE_PENDING_APP',
                'verification':'STRUCTURAL_ROM_DISPATCH'})
    for service in services:
        kind=service['kind'];caller=service['callerMapId']
        if kind in ('weapon','armor','medicine'):service['stock']=stocks[kind,caller]
        elif kind=='inn':service['price']=catalog['innPrices'][caller]
    # The merchant/inn text groups match witnessed services, but their special appearance
    # conditions and stock dispatch must still be verified independently.
    for context in contexts[:geometry_slots]:
        mid=context['contextId']
        if mid in (17,18,19,20,22):continue
        for npc in context['records']:
            if npc['textGroup'] not in (158,159):continue
            services.append({'id':f'rom.service.map{mid}.npc{npc["index"]}',
                'kind':'special-merchant-candidate' if npc['textGroup']==159 else 'special-inn-candidate',
                'interiorMapId':mid,'npcIndex':npc['index'],'source':npc['source'],
                'conditions':'NEEDS_ORIGINAL_SPECIAL_SERVICE_AND_APPEARANCE_DISPATCH',
                'operation':'NOT_IMPLEMENTED','verification':'PROVISIONAL_SERVICE_GROUP_CORRELATION'})
    unresolved=[{'index':175,'kind':'EXTRA_HEADER_AND_EMPTY_NPC_SLOT','status':'UNKNOWN_EFFECTIVE_USAGE',
        'headerPointerSource':reader.span(0,header_start+350,2,'Extra header pointer beyond 175 chunk slots'),
        'reason':'No corresponding chunk pointer; must not be silently counted as a parsed map or excluded as unused'}]
    for m in maps:m['incomingDirectReferences']=incoming[m['mapId']]
    return {'schemaVersion':1,'taskId':'WORLD-FULL-01','romSha256':SHA256,'effectiveMapCount':None,
        'effectiveMapCountStatus':'UNKNOWN_PENDING_EXTRA_SLOT_AND_DYNAMIC_CONTEXT_REVIEW',
        'structuralGeometryCount':len(maps),'npcContextCount':len(contexts),'packagedCount':len(packed),
        'serviceCatalog':catalog,
        'maps':maps,'npcContexts':contexts,'services':services,'unresolved':unresolved,
        'tableEvidence':[reader.span(0,chunk_start,350,'175 chunk pointer slots ending at header table'),
            reader.span(0,header_start,352,'176 header pointer slots; final slot unresolved'),
            reader.span(8,npc_start,npc_slots*2,'NPC/overlay pointer slots ending at first list'),
            reader.span(8,exit_start,352,'Geometry exit pointers plus extra slot'),
            reader.span(0,0xcbe3,79,'Shared indoor/caller/context dispatch'),
            reader.span(0,0xa8f5,68,'NPC cell encoding: x/y times sixteen plus 120'),
            reader.span(0,0xa73f,56,'Temporarily substitute NPC overlay index then restore actual map')],
        'referenceMapCount':259,'referenceIsDenominator':False,
        'summary':{'serviceCandidateCounts':dict(collections.Counter(s['kind'] for s in services)),
            'exitRecords':sum(len(m['exits']) for m in maps),'appRenderPassed':sum(m['appRender']=='PASS' for m in maps),
            'allMapsUsable':'NO','limitation':'Only table inventory; no gameplay, service, event, or all-world promotion'}}

def render_map(reader,m,path):
    # Index-only grayscale: no invented NES palette or inferred sprite placement.
    image=Image.new('L',(m['width']*16,m['height']*16));chr_base=reader.header['sections']['chr']['offset']+m['chr2kBanks'][0]*2048
    chrdata=reader.data[chr_base:chr_base+4096]
    if len(chrdata)!=4096:raise ValueError('Background CHR banks out of range')
    tiles={}
    for t in {v for row in m['grid'] for v in row}:
        tile=Image.new('L',(16,16))
        for q,ti in enumerate(m['metatiles'][t]):
                for yy in range(8):
                    a,b=chrdata[ti*16+yy],chrdata[ti*16+yy+8]
                    for xx in range(8):tile.putpixel(((q%2)*8+xx,(q//2)*8+yy),85*(((a>>(7-xx))&1)+2*((b>>(7-xx))&1)))
        tiles[t]=tile
    for y,row in enumerate(m['grid']):
        for x,t in enumerate(row):image.paste(tiles[t],(x*16,y*16))
    image.save(path)

def verify_opening_viewport(m,ppu):
    """Compare a fixed, documented PPU patch, not a best-fit search.

    $2800 is the second physical nametable. The left ring-buffer column is
    overwritten by the next column; exclude it explicitly, not by mismatch.
    Map x=1..15,y=15..29 -> NT x=2..31,y=0..29, 900 tile indices.
    """
    if len(ppu)!=16384:raise ValueError('Expected full 16 KiB PPU observation')
    differences=[]
    for y in range(30):
        for x in range(2,32):
            expected=m['metatiles'][m['grid'][15+y//2][x//2]][2*(y%2)+x%2]
            actual=ppu[0x2800+y*32+x]
            if expected!=actual:differences.append({'x':x,'y':y,'expected':expected,'actual':actual})
    return {'checkedTileIndices':900,'differences':differences,'passed':not differences,'ppuSha256':digest(ppu),
            'coverage':'30x30 PPU tiles, excludes ring-buffer column; does not verify collision/whole-map gameplay'}

def extract_npcs(reader,map_id=114):
    pointer=reader.word(8,0xb311+map_id*2);records=[]
    for i in range(64):
        address=pointer+i*14
        if reader.read(8,address)[0]==255:break
        data=reader.read(8,address,14)
        records.append({'index':i,'rawHex':data.hex(),'range':reader.span(8,address,14,'Fixed 14-byte NPC source record'),
          'entityByte':data[0],'xCandidate':int.from_bytes(data[4:6],'little'),'yCandidate':int.from_bytes(data[6:8],'little'),
          'animationProgramPointer':int.from_bytes(data[8:10],'little'),'animationModule':0,
          'fieldSemantics':'PARTIAL; bytes8..9 are animation data, not an event code routine','confidence':'HIGH'})
    else:raise ValueError('NPC terminator not found within bounded record count')
    return {'mapId':map_id,'pointer':reader.span(8,0xb311+2*map_id,2,'NPC list pointer'),
      'range':reader.span(8,pointer,len(records)*14+1,'14-byte records terminated by FF'),'records':records}

def extract_enemy(reader,enemy_id):
    if not 0<=enemy_id<177:raise ValueError('Enemy pointer extent not established beyond 177 entries')
    pointer=reader.word(1,0xea58+2*enemy_id);data=reader.read(1,pointer,16)
    values=[int.from_bytes(data[i:i+2],'little') for i in range(0,10,2)]
    return {'id':f'rom.enemy.{enemy_id}','romEnemyId':enemy_id,'hp':values[0],'attack':values[1],
      'defense':values[2],'experienceReward':values[3],'moneyReward':values[4],
      'remainingBytes':list(data[10:]),'remainingFieldSemantics':'UNKNOWN',
      'pointer':reader.span(1,0xea58+2*enemy_id,2,'Enemy stat pointer'),
      'range':reader.span(1,pointer,16,'Enemy record: five uint16le stats/rewards, six unresolved bytes'),
      'confidence':'HIGH','rawHex':data.hex()}

def extract_opening_encounter(reader):
    """Bounded map-16 zone 0 and its complete 19-entry group table.

    The bank selection, zone match, counter gate and group pointer path were
    observed in the cold-boot normal-input battle01 trace. Other map zones
    and unresolved combat behavior are deliberately outside this extraction.
    """
    def bank_bytes(bank,address,length):
        offset=16+bank*8192+(address&0x1fff)
        return reader.data[offset:offset+length]
    def span(bank,address,length,meaning):
        raw=bank_bytes(bank,address,length)
        return {'offset':16+bank*8192+(address&0x1fff),'length':length,
            'sha256':digest(raw),'prg8kBank':bank,'cpuAddress':address,'meaning':meaning}
    def word(bank,address):return int.from_bytes(bank_bytes(bank,address,2),'little')
    if word(46,0xc117)!=0xc11d or word(46,0xc11d)!=0xc14a:
        raise ValueError('Map 16 encounter pointer layout changed')
    zone=bank_bytes(46,0xc14a,10)
    if zone[0]!=0 or zone[-1]!=0:
        raise ValueError('Opening encounter zone changed')
    rectangles=[list(zone[i:i+4]) for i in (1,5)]
    group_count=bank_bytes(5,0xb12d,1)[0]
    root=word(5,0xa93c)
    if group_count!=19 or root!=0xab57 or bank_bytes(4,0x9ea3,5)!=bytes([0,1,1,2,3]):
        raise ValueError('Opening encounter group layout changed')
    remap=bank_bytes(4,0x9ea3,5)
    groups=[]
    for index in range(group_count):
        address=word(5,root+2*index)
        raw=bytearray();entities=[]
        for cursor in range(0,16,2):
            pair=bank_bytes(5,address+cursor,2)
            raw.extend(pair[:1])
            if pair[0]==0:break
            raw.extend(pair[1:2])
            slot=pair[0]-1;source_type=pair[1]
            if slot not in range(7) or source_type>=len(remap) or remap[source_type] not in (1,2,3):
                raise ValueError('Unresolved enemy or slot in opening encounter group')
            entities.append({'slot':slot,'sourceType':source_type,'enemyId':remap[source_type]})
        else:raise ValueError('Encounter group terminator missing')
        if not entities or any(e['slot']==other['slot'] for n,e in enumerate(entities) for other in entities[n+1:]):
            raise ValueError('Empty or overlapping encounter group')
        groups.append({'id':index,'entities':entities,'rawHex':bytes(raw).hex(),
            'pointer':span(5,root+2*index,2,'Encounter group pointer'),
            'range':span(5,address,len(raw),'Slot/source-type pairs, 00 terminated')})
    return {'mapId':16,'zoneId':0,'rectangles':rectangles,
        'rectangleSemantics':'lower bound exclusive; upper bound inclusive; player coordinate is RAM $7A/$7B + 7',
        'groups':groups,'enemies':[extract_enemy(reader,i) for i in (1,2,3)],
        'encounterGate':{'stepCounterMin':6,'stepCounterForced':50,'randomByteThreshold':16,
            'mapType':16,'randomSource':'ROM RAM $43 free-running byte; Android generation not yet equivalent',
            'counter':'RAM $5B increments on a completed tile movement at bank 2 C17B',
            'condition':'At aligned tile: if 6<=counter<50, byte $43<16; counter 50 forces encounter; no repeat at same counter',
            'routine':span(46,0xc000,0x117,'Map encounter selection and gate'),
            'stepRoutine':span(2,0xc17b,0x12,'Movement counter increment')},
        'zonePointers':[span(46,0xc117,2,'Map 16 zone root'),span(46,0xc11d,2,'Zone 0 pointer')],
        'zoneRange':span(46,0xc14a,10,'Zone 0 two rectangles and terminator'),
        'groupCountRange':span(5,0xb12d,1,'Group 0 entry count'),
        'groupRootRange':span(5,0xa93c,4,'Group 0 pointer/tiles'),
        'enemyRemapRange':span(4,0x9ea3,5,'Source type to ROM enemy ID'),
        'groupSelectionRoutine':span(4,0x8ce5,0x54,'Random byte low five bits modulo group count, then pointer load'),
        'romSha256':SHA256,
        'runtimeEvidence':'private-derived/battle01-trigger-left/ and battle01-state/ normal-controller cold-boot traces',
        'remainingUnknown':['Android RNG byte sequence vs NES free-running $43',
            'Player miss/critical and action-order branches outside the observed physical sample',
            'Other escape/defeat branches outside scoped battle02 evidence; $06E2 is not set by observed ordinary escape']}

def verify_enemy_runtime(enemy,sram,slot):
    if len(sram)!=2048 or not 0<=slot<7:raise ValueError('Invalid mapper RAM snapshot or enemy slot')
    actual={'romEnemyId':sram[0x177+slot]}
    for field,base in [('hp',0x180),('attack',0x18e),('defense',0x19c),('experienceReward',0x1b8),('moneyReward',0x1c6)]:
        actual[field]=int.from_bytes(sram[base+2*slot:base+2*slot+2],'little')
    return {'passed':all(enemy[k]==v for k,v in actual.items()),'slot':slot,'observed':actual,'sramSha256':digest(sram)}

def extract_growth_candidates(reader):
    # AED3/AEF5 in battle module 9 map PRG 8 KiB bank 45 into $C000-$DFFF.
    # Do not confuse this mixed mapping with the ordinary four-bank modules.
    def offset(address,length):
        if not 0xc000<=address or address+length>0xe000:raise ValueError('Growth range escapes PRG bank 45')
        return 16+45*8192+address-0xc000
    def span(address,length,meaning):
        p=offset(address,length);data=reader.data[p:p+length]
        return {'offset':p,'length':length,'sha256':digest(data),'cpuAddress':address,'prg8kBank':45,'meaning':meaning}
    def data(address,length):p=offset(address,length);return reader.data[p:p+length]
    def word(address):return int.from_bytes(data(address,2),'little')
    groups=[]
    for actor in range(4):
        growth=word(0xd296+actor*2);threshold=word(0xd200+actor*2)
        rows=[]
        for index in range(80):
            b=data(growth+index*7,7);xp=data(threshold+index*3,3)
            rows.append({'index':index,'growthBytes':list(b),'hpDeltaCandidate':int.from_bytes(b[:2],'little'),
              'mpDeltaCandidate':b[2],'strengthDeltaCandidate':b[3],'staminaDeltaCandidate':b[4],
              'agilityDeltaCandidate':b[5],'spiritDeltaCandidate':b[6],
              'cumulativeExpCandidate':int.from_bytes(xp,'little'),
              'growthRange':span(growth+index*7,7,'Growth row; index 0 is not assumed to override new-game initialization'),
              'thresholdRange':span(threshold+index*3,3,'uint24le cumulative experience candidate')})
        groups.append({'actorIndex':actor,'confidence':'HIGH','rows':rows,
          'growthRange':span(growth,80*7,'80 contiguous seven-byte growth rows'),
          'thresholdRange':span(threshold,80*3,'80 contiguous uint24le experience values')})
    return {'groups':groups,'confidence':'HIGH','mapping':'PRG bank 45 at CPU C000-DFFF',
      'tablePointerRanges':[span(0xd296,8,'Four growth table pointers'),span(0xd200,8,'Four experience table pointers')],
      'routineRanges':[span(0xd208,0x8e,'Experience comparison and level index increment'),span(0xd29e,0xc2,'Apply seven-byte growth deltas')],
      'blocker':'Raw 4x80 tables; runtime coverage and initial display cache semantics are recorded separately in progression-verified.json.'}

def write_research(path,out=None):
    out=Path(out or ROOT/'game-data/raw/rom');out.mkdir(parents=True,exist_ok=True)
    data=Path(path).read_bytes();reader=Reader(data);fingerprint=rom.analyze(path)
    records=[];offsets=[]
    def evidence(key,span,confidence='VERIFIED'):
        sid='source.rom.'+key
        record={'id':sid,'source':'ORIGINAL_ROM','confidence':confidence,'originalVerified':False,'licenseStatus':'UNKNOWN',
          'locator':{'path':Path(path).resolve().relative_to(ROOT).as_posix(),'romSha256':SHA256,'offset':span['offset'],
            'length':span['length'],'sha256':span['sha256'],'meaning':span['meaning']},'evidenceRefs':[],
          'note':'Byte range verified against pinned ROM; semantic scope is limited to the stated meaning.'}
        records.append(record);offsets.append({'romSha256':SHA256,'offset':span['offset'],'length':span['length'],
          'meaning':span['meaning'],'confidence':confidence,'evidence':[sid]});return sid
    def claim(key,refs,meaning,raw_path):
        sid='source.original.'+key
        records.append({'id':sid,'source':'MANUAL','confidence':'VERIFIED','originalVerified':True,'licenseStatus':'UNKNOWN',
          'locator':{'path':raw_path,'sha256':digest((ROOT/raw_path).read_bytes()),'meaning':meaning},'evidenceRefs':refs,
          'note':'Scoped semantic claim; does not verify all fields of a reference entity or the cartridge release.'})
        return sid
    routines=[('text.pointer',3,0x80d3,0x2f,'Two-level text pointer lookup'),
      ('text.decoder',3,0x8158,0xfc,'Token decoding, C2 newline, C3 end and four-tile glyph emission'),
      ('text.special',3,0x826c,0xb6,'Special glyph/control dispatch; unrecognized codes remain UNKNOWN'),
      ('text.plane',3,0x83f0,0x24,'Token sign bit selects glyph bitplane through palette attributes'),
      ('map.header',0,0xb9f7,0x39,'Map header fields loaded into $71,$75..$78'),
      ('map.chunks',0,0xb547,0x57,'16x15 chunk addressing for ordinary maps'),
      ('map.render',7,0x804b,0x99,'Metatile source and four PPU tile queue writes'),
      ('npc.loader',8,0x8057,0x35,'NPC 14-byte record copy and map pointer lookup'),
      ('character.init',0,0xb7c2,0x93,'New-game initialization: party size, HP, MP, strength, speed, stamina, spirit, level threshold'),
      ('enemy.loader',1,0x8ea2,0x97,'Enemy ID -> pointer -> 16-byte stats copied to mapper RAM'),
      ('battle.enemyattack',9,0xaa82,0x10c,'Observed normal enemy physical attack branch, defenses, damage floor and HP subtraction'),
      ('battle.playerattack',9,0xacec,0xeb,'Player physical branch: strength/weapon/defense, multiplier and HP subtraction'),
      ('battle.rewards',9,0xadd7,0xce,'Money sum and experience sum/distribution after victory'),
      ('growth.bank-switch',9,0xaecb,0x42,'Map PRG bank 45 to C000-DFFF, call D208/D29E, restore previous bank'),
      ('map.transition',8,0x808c,0x53,'Five-byte entry/exit row and destination map assignment'),
      ('map.chr',0,0xa0a1,0x29,'Background and sprite CHR bank selection')]
    from py65.devices.mpu6502 import MPU
    from py65.disassembler import Disassembler
    listing=[]
    for key,module,address,length,meaning in routines:
        evidence('routine.'+key,reader.span(module,address,length,meaning))
        machine=MPU();machine.memory[0x8000:]=reader.read(module,0x8000,32768);dis=Disassembler(machine)
        listing.extend(['',f'; {key}: {meaning}; module {module}; bounded investigation, not a whole-ROM code map'])
        pc=address
        while pc<address+length:
            size,instruction=dis.instruction_at(pc)
            listing.append(f'{cpu_offset(module,pc):06X}  {pc:04X}  {instruction}');pc+=size
    (out/'routines.asm').write_text('\n'.join(listing)+'\n',encoding='utf-8')
    # Export actual CHR banks through the existing generic analyzer, not a second NES parser.
    tile_dir=out/'chr';tile_dir.mkdir(exist_ok=True)
    sheets=[]
    for bank in range(64):
        target=tile_dir/f'bank-{bank:02d}.png';rom.export_tiles(path,target,'chr',bank*8192,512);sheets.append(target.name)
    base=reader.header['sections']['chr']['offset']+78*2048
    fontspan={'offset':base,'length':4096,'sha256':digest(data[base:base+4096]),'meaning':'Observed dialogue font: two CHR 2 KiB banks, two independent glyph planes'}
    fontid=evidence('font78',fontspan);charset=[];atlas=Image.new('L',(16*40,4*44))
    draw=ImageDraw.Draw(atlas)
    for i,(code,char) in enumerate(sorted(CHARS.items())):
        pixels=glyph_pixels(data,base,code);glyph=Image.new('L',(16,16));glyph.putdata([255*v for row in pixels for v in row])
        x=(i%16)*40;y=(i//16)*44;atlas.paste(glyph.resize((32,32),Image.Resampling.NEAREST),(x,y+10));draw.text((x,y),f'{code:02X}',fill=255)
        offset=base+(10+4*(code&63))*16
        charset.append({'code':code,'codeHex':f'{code:02X}','character':char,'offset':offset,'length':64,
          'sha256':digest(data[offset:offset+64]),'encoding':'contextual custom glyph code; bit7=bitplane, bits0..5=quad index',
          'plane':code>>7,'confidence':'VERIFIED','evidence':[fontid,'source.rom.routine.text.decoder','source.rom.routine.text.plane'],
          'scope':{'group':124,'message':10,'chr2kBanks':[78,79]}})
    atlas.save(out/'charset.png');save(out/'charset.json',{'romSha256':SHA256,'records':charset,'punctuation':PUNCT,'globalCharsetComplete':False})
    dialogue=extract_text(reader,124,10,True);textid=evidence('dialogue.124.10',dialogue['range'])
    ptrids=[evidence(f'dialogue.124.10.pointer.{i}',s) for i,s in enumerate(dialogue['pointerEvidence'])]
    save(out/'dialogues.json',{'romSha256':SHA256,'records':[dialogue],'complete':False})
    dialogueclaim=claim('dialogue.124.10',[textid,fontid,*ptrids,'source.rom.routine.text.decoder','source.rom.routine.text.plane'],
      'Only byte stream, text and observed font context verified; speaker/next-event reference fields not promoted.',(out/'dialogues.json').relative_to(ROOT).as_posix())
    candidates=[]
    for index in range(26):
        if index!=10:candidates.append(extract_text(reader,124,index,False))
    save(out/'dialogue-pointer-candidates.json',{'records':candidates,'confidence':'UNKNOWN',
      'note':'Bounded pointers found, but shared pointer tables do not imply a shared font. No guessed Chinese decoding.'})
    maps=[];mapdir=out/'maps';mapdir.mkdir(exist_ok=True)
    for map_id in range(114,124):
        m=extract_map(reader,map_id);render_map(reader,m,mapdir/f'{map_id}.png')
        if map_id==114:
            observation=ROOT/'private-derived/npc-probe/frame-0450-ppu.bin'
            if observation.exists():
                m['viewportVerification']=verify_opening_viewport(m,observation.read_bytes())
                m['gameplayVerified']=m['viewportVerification']['passed'];m['confidence']='VERIFIED' if m['gameplayVerified'] else 'HIGH'
        maps.append(m);save(mapdir/f'{map_id}.json',m)
    opening=maps[0];maprefs=[]
    for key in ('header','headerPointer','chunkTablePointer','moduleEvidence','chrEvidence','metatileRange','attributeRange'):
        maprefs.append(evidence('map114.'+key.lower(),opening[key]))
    for i,ch in enumerate(opening['chunks']):
        maprefs.append(evidence(f'map114.chunk.{i}',ch));maprefs.append(evidence(f'map114.chunkptr.{i}',ch['pointer']))
    save(out/'maps.json',{'romSha256':SHA256,'records':maps,'coverage':'1 observed opening map, then 9 structural candidates; no batch promotion'})
    mapclaim=claim('map114.geometry',maprefs+['source.rom.routine.map.header','source.rom.routine.map.chunks'],
      'Opening map geometry and metatile grid only; transitions/collision/NPC semantics remain separate.',(mapdir/'114.json').relative_to(ROOT).as_posix())
    npcs=extract_npcs(reader);evidence('map114.npcs',npcs['range']);save(out/'npcs.json',npcs)
    exitptr=reader.word(8,0xdc69+2*114);exitbytes=reader.read(8,exitptr,5)
    exitspan=reader.span(8,exitptr,5,'Opening map entry x/y, exit destination map and destination x/y')
    exitid=evidence('map114.exit',exitspan)
    save(out/'transitions.json',{'records':[{'fromMap':114,'toMap':exitbytes[2],
      'rawHex':exitbytes.hex(),'range':exitspan,'confidence':'HIGH',
      'observedTransitionFrames':[840,843],'captureDirectory':'private-derived/status-probe',
      'coordinateSemantics':'Byte2 destination ID verified; coordinate offsets and trigger coverage remain partial',
      'evidence':[exitid,'source.rom.routine.map.transition']}]})
    # Known initialization operands, verified by executable stores and status-screen observation.
    initial={'nameObserved':'哪吒','levelObserved':1,'experience':0,'initialRemainingExpDisplay':reader.read(0,0xb815)[0],
      'hp':reader.read(0,0xb7cb)[0],'maxHp':reader.read(0,0xb7cb)[0],'mp':reader.read(0,0xb7e3)[0],
      'strength':reader.read(0,0xb7f4)[0],'agility':reader.read(0,0xb80b)[0],
      'stamina':reader.read(0,0xb801)[0],'spirit':reader.read(0,0xb810)[0],
      'levelEncoding':'RAM0504 zero-based: 0 displays level1. Per-actor tables and first two Nezha upgrades are scoped in progression-verified.json.',
      'confidence':'HIGH','evidence':['source.rom.routine.character.init'],'screenshot':'private-derived/status-probe/frame-0650.png'}
    save(out/'characters.json',{'records':[initial],'complete':False})
    initialclaim=claim('character.initial',['source.rom.routine.character.init'],
      'Only stated initialization constants plus matching status-screen observation; full progression and reward columns unverified.',
      (out/'characters.json').relative_to(ROOT).as_posix())
    enemies=[];enemyclaims={}
    observation=ROOT/'private-derived/battle-probe/frame-1980-sram.bin'
    for enemy_id,slot in [(2,2),(3,4)]:
        enemy=extract_enemy(reader,enemy_id)
        if observation.exists():
            enemy['runtimeVerification']=verify_enemy_runtime(enemy,observation.read_bytes(),slot)
            if enemy['runtimeVerification']['passed']:enemy['confidence']='VERIFIED'
        enemy['nameObserved']={2:'臭甲蟲',3:'百角海膽'}[enemy_id]
        refs=[evidence(f'enemy.{enemy_id}.data',enemy['range']),evidence(f'enemy.{enemy_id}.pointer',enemy['pointer']),
          'source.rom.routine.enemy.loader','source.rom.routine.battle.enemyattack','source.rom.routine.battle.playerattack','source.rom.routine.battle.rewards']
        enemies.append(enemy);save(out/f'enemy-{enemy_id}.json',enemy)
        enemyclaims[enemy_id]=claim(f'enemy.{enemy_id}.stats',refs,'Only HP/attack/defense/EXP/money; names visually observed, remaining flags/skills not verified.',
          (out/f'enemy-{enemy_id}.json').relative_to(ROOT).as_posix())
    save(out/'enemies.json',{'records':enemies,'complete':False})
    all_enemies=[extract_enemy(reader,i) for i in range(177)]
    save(out/'enemy-table-candidates.json',{'records':all_enemies,'confidence':'HIGH','completeDomain':False,
      'extentEvidence':'177 sequential pointers from EA58 to EBBA; records EBBA..F6C9 in PRG module 1. Includes dummy/unidentified entries.',
      'warning':'ROM enemy ID and Reference ID are not assumed equivalent outside observed IDs 2 and 3.'})
    growth=extract_growth_candidates(reader)
    for i,span in enumerate(growth['tablePointerRanges']+growth['routineRanges']):evidence(f'growth.structure.{i}',span)
    for group in growth['groups']:
        for kind in ('growthRange','thresholdRange'):evidence(f'growth.actor.{group["actorIndex"]}.{kind.lower()}',group[kind])
    save(out/'progression.json',growth)
    # Compare identified fields explicitly. Never infer equivalence from edit distance.
    comparison={'schemaVersion':1,'baseRomSha256':SHA256,'completeDomains':[],'coverageSourceRefs':{},'records':[
      {'domain':'dialogues','id':dialogue['id'],'referenceId':'reference.story.7','comparable':{'speak_words':dialogue['text']},'complete':False,'sourceRefs':[dialogueclaim]},
      {'domain':'progression','id':'rom.progression.nezha.initial','referenceId':'reference.role_lv.1',
       'comparable':{'hp':initial['hp'],'mp':initial['mp'],'li_liang':initial['strength'],
         'min_jie':initial['agility'],'ti_neng':initial['stamina'],'jin_shen':initial['spirit']},'complete':False,'sourceRefs':[initialclaim]}]}
    for enemy in enemies:
        enemy_id=enemy['romEnemyId']
        comparison['records'].append({'domain':'enemies','id':enemy['id'],'referenceId':f'reference.monster.{enemy_id}',
          'comparable':{'hp':enemy['hp'],'atk':enemy['attack'],'def':enemy['defense'],'exp':enemy['experienceReward'],'money':enemy['moneyReward']},
          'complete':False,'sourceRefs':[enemyclaims[enemy_id]]})
    save(out/'comparison.json',comparison)
    raw=load(ROOT/'game-data/raw/reference-project/dataset.json');refmap=next(m for m in raw['maps'] if m['id']==1)
    # A diagnostic hypothesis, not an admitted canonical row or a VERIFIED match.
    stats={'hp':initial['hp'],'mp':initial['mp'],'li_liang':initial['strength'],'ti_neng':initial['stamina'],
           'min_jie':initial['agility'],'jin_shen':initial['spirit']};hypotheses=[]
    refgrowth={r['data']['roleLv']:r for r in raw['records'] if r['table']=='role_lv' and r['data']['roleId']==1}
    for row in growth['groups'][0]['rows']:
        level=row['index']+1
        if row['index']:
            for field,delta in [('hp','hpDeltaCandidate'),('mp','mpDeltaCandidate'),('li_liang','strengthDeltaCandidate'),
              ('ti_neng','staminaDeltaCandidate'),('min_jie','agilityDeltaCandidate'),('jin_shen','spiritDeltaCandidate')]:stats[field]+=row[delta]
            stats['min_jie']=min(255,stats['min_jie']);stats['jin_shen']=min(255,stats['jin_shen'])
        if level in refgrowth:
            ref=refgrowth[level];diff=[{'field':k,'reference':ref['data'][k],'romDerivedCandidate':v} for k,v in stats.items() if ref['data'][k]!=v]
            hypotheses.append({'referenceId':ref['id'],'level':level,'candidate':dict(stats),'differences':diff,
              'status':'LIKELY_MATCH' if not diff else 'UNKNOWN','originalVerified':False,'confidence':'HIGH',
              'note':'Conditional on zero-based level indexing and ordinary delta path; runtime level-up still required. EXP intentionally separate.'})
    save(out/'progression-reference-candidates.json',{'records':hypotheses,'originalVerified':False,
      'counts':dict(collections.Counter(x['status'] for x in hypotheses)),
      'thresholdDiscrepancy':{'initialDisplay':7,'firstNextLevelTableValue':12,'status':'UNKNOWN','nextInvestigation':'Observe crossing 7 and 12 accumulated EXP with instruction trace.'}})
    structural={'originalId':'rom.map.114','referenceId':'reference.map.1','associationConfidence':'HIGH',
      'associationEvidence':'Opening location, visible layout, same NPC conversation; independent IDs not equated',
      'romDimensions':[opening['width'],opening['height']],'referenceDimensions':[int(refmap['attributes']['width']),int(refmap['attributes']['height'])],
      'status':'MODIFIED','scope':'dimensions; tiles/GIDs/collision have different schemas and remain UNKNOWN',
      'romSourceRefs':[mapclaim],'referenceSourceRefs':refmap['sourceRefs']}
    save(out/'map-reference-comparison.json',structural)
    # Locatable emulator observations complement ROM evidence, never replace it.
    runtime_files=[];gameplay_refs=[]
    selected={
      'npc-probe':['frame-0450-ppu.bin','frame-0450-ram.bin','frame-0450.png','frame-0600.png','frame-0900.png','frame-1040.png','mapper.tsv'],
      'status-probe':['frame-0650.png','frame-0650-ram.bin','frame-0840-ram.bin','frame-0843-ram.bin','frame-0960.png','ram-writes.tsv'],
      'battle-probe':['frame-1980-sram.bin','frame-1980-ram.bin','frame-2520.png','frame-3000.png','frame-3600.png','frame-4200.png','frame-4980.png',
        'frame-5100.png','frame-5220.png','frame-5280.png','frame-5400-ram.bin','writes.tsv']}
    for probe,names in selected.items():
        for name in names:
            file=ROOT/'private-derived'/probe/name
            if not file.exists():continue
            relative=file.relative_to(ROOT).as_posix();filehash=digest(file.read_bytes())
            runtime_files.append({'path':relative,'sha256':filehash,'bytes':file.stat().st_size})
            if name.endswith('.png'):
                sid='source.gameplay.'+probe.replace('-','.')+'.'+name[:-4].replace('-','.')
                records.append({'id':sid,'source':'GAMEPLAY_VERIFIED','confidence':'VERIFIED','originalVerified':False,
                  'licenseStatus':'UNKNOWN','locator':{'path':relative,'sha256':filehash,'romSha256':SHA256,
                    'meaning':'FCEUX 2.6.6 normal-input observation; screenshot queued at named frame and saved on the following frame'},
                  'evidenceRefs':[],'note':'NTSC, sound off, no cheats, no writes to emulated memory; read-only hooks plus controller input.'})
                gameplay_refs.append(sid)
    for record in records:
        if record['id']==dialogueclaim:record['evidenceRefs'] += [s for s in gameplay_refs if 'npc.probe.frame.0900' in s or 'npc.probe.frame.1040' in s]
        if record['id']==initialclaim:record['evidenceRefs'] += [s for s in gameplay_refs if 'status.probe.frame.0650' in s]
        if record['id'] in enemyclaims.values():record['evidenceRefs'] += [s for s in gameplay_refs if 'battle.probe.frame.2520' in s]
    emulator=ROOT/'private-derived/tooling/fceux-2.6.6/fceux64.exe'
    runtime_manifest={'romSha256':SHA256,'emulator':'FCEUX 2.6.6 Windows x64','emulatorSha256':digest(emulator.read_bytes()) if emulator.exists() else None,
      'settings':{'region':'NTSC','sound':False,'cheats':False,'emulatedMemoryWritesByProbe':False},
      'snapshots':runtime_files,'screenshotTiming':'Requested frame label; PNG captures the following frame. RAM/PPU files are exact labeled frame.',
      'replayScripts':['tools/rom-extractor/probe-npc.lua','tools/rom-extractor/probe-status.lua','tools/rom-extractor/probe-battle.lua'],
      'saveStates':'No persisted save states are used; cold boot controller timelines reproduce observations.',
      'scope':'Frame files below 1980 in battle-probe belong to a rejected save-state attempt; excluded from this manifest.'}
    save(ROOT/'game-data/provenance/gameplay-captures.json',runtime_manifest)
    battle={'confidence':'HIGH','romSha256':SHA256,'observation':'One normal-input cold boot battle, enemy IDs 2 and 3; victory observed',
      'enemySlots':{'2':2,'4':3},'initialHp':20,'finalHp':8,'experienceGained':5,'moneyGained':3,
      'observedPlayerDamage':[8,2,7,4],'damageNote':'Capped damage/HP removal observed; not proof of a universal formula or random distribution.',
      'enemyDamageObserved':[3,3,3,3],
      'normalPhysicalBranch':[
        {'direction':'player to enemy','expression':'strength + equipped weapon contribution - enemy defense, then multiplier and condition branches',
         'example':'8 + 2 - 2 = 8; 8 + 2 - 3 = 7; multiplier observed 1','confidence':'HIGH','routine':'source.rom.routine.battle.playerattack'},
        {'direction':'enemy to player','expression':'enemy attack - armor contribution - stamina, floor at 1, then guard/status branches',
         'example':'9 - 2 - 4 = 3','confidence':'HIGH','routine':'source.rom.routine.battle.enemyattack'}],
      'rewards':{'expSum':5,'moneySum':3,'routine':'source.rom.routine.battle.rewards','multiplayerSplit':'UNKNOWN; only one living party member tested'},
      'unverified':['Encounter rate','RNG distribution','Critical multiplier selection','Level-up table','Magic','Item effects','Boss AI'],
      'captureManifest':'game-data/provenance/gameplay-captures.json'}
    save(out/'battle.json',battle)
    initial_capture=ROOT/'private-derived/battle-probe/frame-1980-ram.bin'
    final_capture=ROOT/'private-derived/battle-probe/frame-5400-ram.bin'
    if initial_capture.exists() and final_capture.exists():
        before=initial_capture.read_bytes();after=final_capture.read_bytes()
        if len(before)!=2048 or len(after)!=2048:raise ValueError('Invalid battle CPU RAM observation')
        battle['initialHp']=int.from_bytes(before[0x514:0x516],'little');battle['finalHp']=int.from_bytes(after[0x514:0x516],'little')
        battle['experienceGained']=int.from_bytes(after[0x508:0x50b],'little')-int.from_bytes(before[0x508:0x50b],'little')
        battle['moneyGained']=int.from_bytes(after[0x501:0x504],'little')-int.from_bytes(before[0x501:0x504],'little')
        battle['captureCheckPassed']=[battle[k] for k in ('initialHp','finalHp','experienceGained','moneyGained')]==[20,8,5,3]
        if not battle['captureCheckPassed']:battle['confidence']='UNKNOWN'
    else:
        battle['confidence']='UNKNOWN';battle['captureCheckPassed']=False
        battle['observation']='Recorded research profile has an opening battle, but local captures are unavailable for this run.'
        for key in ('initialHp','finalHp','experienceGained','moneyGained'):battle[key]=None
    save(out/'battle.json',battle)
    save(ROOT/'game-data/provenance/original.json',{'schemaVersion':1,'records':records})
    save(ROOT/'game-data/provenance/rom-offsets.json',{'schemaVersion':1,'status':'RESEARCH_IN_PROGRESS','records':offsets})
    make_viewer(out,maps)
    result={'schemaVersion':1,'profile':PROFILE,'romSha256':SHA256,'status':'PARTIAL_BASELINE','readyForPhase2':False,
      'charsetGlyphsVerified':len(charset),'dialoguesExtractedVerified':1,'dialoguePointersUndecoded':len(candidates),
      'mapsExtracted':len(maps),'mapsWithObservedViewport':sum(m['gameplayVerified'] for m in maps),'npcRecordsExtracted':len(npcs['records']),
      'enemyStatRecordsExtracted':len(enemies),'enemyRecordsWithRuntimeCheck':sum(e.get('runtimeVerification',{}).get('passed',False) for e in enemies),
      'enemyStatCandidates':len(all_enemies),'progressionRawRows':320,
      'progressionCandidateComparison':dict(collections.Counter(x['status'] for x in hypotheses)),
      'battle':battle,
      'referenceFullyVerified':{'maps':0,'dialogues':0,'enemies':0,'progression':0},
      'referenceFieldComparisons':{'dialogues':{'modified':1,'referenceId':7},'maps':structural},
      'confidenceCounts':{c:sum(r['confidence']==c for r in records) for c in ['VERIFIED','HIGH','MEDIUM','LOW','UNKNOWN']},
      'confidenceCountUnit':'Original evidence records, not reference entities; do not add status and confidence counts.',
      'fingerprint':fingerprint,'initialCharacter':initial,'outputs':str(out)}
    from .vertical_slice import augment
    augment(reader,path,out,result,records,offsets)
    save(ROOT/'reports/rom-research.json',result);return result

def make_viewer(out,maps):
    # Self-contained local HTML; a viewer, not Android/server development.
    options=''.join(f'<option value="{m["romMapId"]}">{m["romMapId"]} — {m["width"]}×{m["height"]} — {m["confidence"]}</option>' for m in maps)
    document='''<!doctype html><html lang="zh"><meta charset="utf-8"><title>ROM Map Evidence Viewer</title>
<style>body{background:#111;color:#ddd;font:16px system-ui;margin:24px}img{image-rendering:pixelated;max-width:100%}pre{white-space:pre-wrap}select{font:inherit;padding:8px}</style>
<h1>ROM Map Evidence Viewer</h1><p>真实 ROM metatile 重建，灰度只表示像素索引。114/16各有固定视口验证；完整事件语义仍部分未知。<a href="vertical-slice-viewer.html">V1 NPC / Collision / Transition 证据查看器</a></p>
<select id="map">OPTIONS</select> <a id="data" href="maps/114.json">原始网格与证据</a><p><img id="picture" src="maps/114.png"></p>
<script>document.getElementById('map').onchange=e=>{let id=e.target.value;document.getElementById('picture').src='maps/'+id+'.png';document.getElementById('data').href='maps/'+id+'.json'}</script></html>'''.replace('OPTIONS',options)
    (out/'map-viewer.html').write_text(document,encoding='utf-8')
