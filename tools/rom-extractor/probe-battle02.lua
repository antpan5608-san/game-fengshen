-- Extend the existing cold-boot battle probe. Controller only, no ROM/RAM writes.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local mode=os.getenv('FENGSHEN_BATTLE02_MODE') or 'menu'
local out=root..'/private-derived/battle02-'..mode..'/'
local frame=0;local banks={63,63,63,63}
local log=assert(io.open(out..'events.tsv','w'));local hooks=assert(io.open(out..'hooks.tsv','w'))
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local function dump(name,data)local f=assert(io.open(out..name,'wb'));f:write(data);f:close()end
local function snapshot(label)
 local name=string.format('%06d-%s',frame,label)
 dump(name..'-ram.bin',memory.readbyterange(0,0x800));dump(name..'-sram.bin',memory.readbyterange(0x6800,0x800));dump(name..'-ppu.bin',ppu.readbyterange(0,0x4000))
 gui.savescreenshotas(out..name..'.png')
 log:write(string.format('%d\t%s\tstate=%02X phase=%d map=%d x=%d y=%d hp=%d mp=%d xp=%d money=%d steps=%d grace=%d\n',frame,label,memory.readbyte(0),memory.readbyte(0x110),memory.readbyte(0x47),word(0x406),word(0x408),word(0x514),word(0x520),word(0x508),word(0x501),memory.readbyte(0x5b),memory.readbyte(0x6e2)));log:flush()
end
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
for _,a in ipairs({0x47,0x5b,0x6e2,0x501,0x502,0x503,0x514,0x515,0x520,0x521})do
 memory.registerwrite(a,1,function(addr,s,v)
  if frame>1780 then local pc=memory.getregister('pc');hooks:write(string.format('%d\tW\t%04X\t%d\t%04X\t%d\n',frame,addr,v,pc,banks[math.floor((pc-0x8000)/8192)+1] or -1))end
 end)
end
memory.registerread(0x43,1,function(a,s,v)
 local pc=memory.getregister('pc');local bank=banks[math.floor((pc-0x8000)/8192)+1]
 if frame>2000 and bank and (bank==36 or bank==37) then hooks:write(string.format('%d\tR43\t%04X\t%d\tbank%d\n',frame,pc,v,bank))end
end)
emu.speedmode('nothrottle')
local chrseq=0
memory.registerwrite(0x6004,0x7fc,function(a,sz,v)
 if frame>=2250 and frame<=2252 and a%8>=4 then
  chrseq=chrseq+1;dump(string.format('chr-%d-%04X-%d-ppu.bin',chrseq,a,v),ppu.readbyterange(0,0x4000))
 end
end)
memory.registerexec(0x8a49,function()
 if banks[1]==36 then hooks:write(string.format('%d\tESCAPE_ENTRY\tcarry=%d rng=%d\n',frame,memory.getregister('p')%2,memory.readbyte(0x43)))end
end)
memory.registerexec(0x8aa9,function()
 if banks[1]==36 then hooks:write(string.format('%d\tESCAPE\trng=%d transformed=%d threshold=%d heroAgility=%d enemyAgility=%d enemy=%d\n',frame,memory.readbyte(0x43),memory.readbyte(0x692f),memory.readbyte(0x692e),memory.readbyte(0x534),memory.readbyte(0x69d4+memory.readbyte(0x367)),memory.readbyte(0x367)));hooks:flush()end
end)
local last=-1;local returnedFrame=nil
for t=1,((mode=='menu' or mode=='assets') and 3201 or 16001) do
 frame=t;local keys={};local state=memory.readbyte(0)
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 and state~=8 then keys.left=true end
 if t==2200 or t==2201 then keys.B=true end
 if mode:match('run') or mode=='lose' or mode:match('win') then
  if t>2250 and t%12==0 then
   if memory.readbyte(0x110)==4 and memory.readbyte(0xde)==1 then
    local target=mode:match('run') and 4 or (mode:match('win') and 0 or 3)
    if memory.readbyte(0xe0)~=target then keys.down=true else keys.A=true end
   else keys.A=true end
  end
 else
  if t==2320 then keys.down=true end
  if t==2360 then keys.right=true end
  if t==2400 then keys.A=true end
 end
 if returnedFrame then keys={} end
 if mode~='assets' and t>=1780 and t<8000 and t%4==0 then gui.savescreenshotas(out..string.format('video-%06d.png',t))end
 joypad.set(1,keys);emu.frameadvance()
 if t==2100 or t==2250 or t==2330 or t==2370 or t==2410 or t==2450 or t==2700 or t==3200 or (t>1780 and t%120==0) or memory.readbyte(0)~=last then snapshot('state');last=memory.readbyte(0) end
 if (mode:match('run') or mode:match('win')) and t>2700 and (memory.readbyte(0)==0x88 or memory.readbyte(0)==0x0b) and not returnedFrame then
  snapshot('returned');returnedFrame=t
 end
 if returnedFrame and t>=returnedFrame+180 then snapshot('returned-visible');emu.frameadvance();break end
end
log:close();hooks:close();dump('complete.txt','cold boot normal controller; bounded menu discovery');emu.pause()
