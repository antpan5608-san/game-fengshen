-- Bounded normal-input CPU trace immediately before the first observed battle.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/battle01-entry-2/'
local frame=0;local banks={63,63,63,63};local ring={};local at=0;local dumped=false
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
memory.registerexec(0x8000,0x8000,function(pc)
 if frame>=1764 and frame<=1786 and not dumped then
  at=at+1;ring[(at-1)%400+1]=string.format('%d\t%04X\t%d',frame,pc,banks[math.floor((pc-0x8000)/8192)+1] or -1)
 end
end)
memory.registerwrite(0x29,1,function(a,s,v)
 if v==0x5a and frame>=1764 and frame<=1786 and not dumped then
  dumped=true
  local f=assert(io.open(out..'entry-trace.tsv','w'))
  local first=math.max(1,at-399)
  for i=first,at do f:write(ring[(i-1)%400+1],'\n') end
  f:write('STACK\n')
  local sp=memory.getregister('s');f:write(string.format('S=%02X\n',sp))
  for i=sp+1,0xff do f:write(string.format('%02X:%02X\n',i,memory.readbyte(0x100+i)))end
  f:close()
 end
end)
memory.registerwrite(0x18,1,function(a,s,v)
 if v==0x21 and frame>=1750 and frame<1780 then
  local f=assert(io.open(out..'dispatch-trace.tsv','w'))
  local first=math.max(1,at-399)
  for i=first,at do f:write(ring[(i-1)%400+1],'\n') end
  f:write('STACK\n')
  local sp=memory.getregister('s');f:write(string.format('S=%02X\n',sp))
  for i=sp+1,0xff do f:write(string.format('%02X:%02X\n',i,memory.readbyte(0x100+i)))end
  f:close()
 end
end)
emu.speedmode('nothrottle')
for t=1,1790 do
 frame=t;local keys={}
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 then keys.left=true end
 joypad.set(1,keys);emu.frameadvance()
end
local f=assert(io.open(out..'complete.txt','w'));f:write(dumped and 'entry recorded' or 'entry not recorded');f:close()
emu.pause()
