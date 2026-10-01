-- Controlled cold-boot input comparison for the opening encounter; no memory writes.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local variant=os.getenv('FENGSHEN_VARIANT') or 'left'
assert(variant=='left' or variant=='idle' or variant=='right')
local out=root..'/private-derived/battle01-trigger-'..variant..'/'
local log=assert(io.open(out..'timeline.tsv','w'))
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local frame=0;local banks={63,63,63,63}
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
local writes=assert(io.open(out..'writes.tsv','w'))
memory.registerwrite(0x18,1,function(a,s,v)
 if frame>=1700 and frame<=1810 then
  local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
  writes:write(string.format('%d\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
 end
end)
memory.registerwrite(0x29,1,function(a,s,v)
 if frame>=1700 and frame<=1810 then
  local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
  writes:write(string.format('%d\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
 end
end)
emu.speedmode('nothrottle')
for t=1,2200 do
 frame=t;local keys={}
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 and variant~='idle' then keys[variant]=true end
 joypad.set(1,keys);emu.frameadvance()
 if t>=1680 then
  log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',
   t,memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x29),
   memory.readbyte(0x18),memory.readbyte(0x7a),memory.readbyte(0x7b),
   memory.readbyte(0x6977),memory.readbyte(0x6978),memory.readbyte(0x6979)))
 end
 if t==1690 or t==1740 or t==1765 or t==1785 or t==1800 or t==1900 or t==2100 then
  gui.savescreenshotas(out..string.format('frame-%04d.png',t))
 end
end
log:close();writes:close()
local done=assert(io.open(out..'complete.txt','w'));done:write('cold boot; normal controller only; variant '..variant);done:close()
emu.pause()
