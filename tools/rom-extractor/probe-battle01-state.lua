-- Opening encounter state and group loader, controlled inputs only.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/battle01-state/'
local frame=0;local banks={63,63,63,63}
local log=assert(io.open(out..'state.tsv','w'))
local hooks=assert(io.open(out..'hooks.tsv','w'))
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
for _,address in ipairs({0x43,0x5b,0x59,0x0112,0x0126})do
 memory.registerwrite(address,1,function(a,s,v)
  if frame>=700 and frame<=1820 then
   local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
   hooks:write(string.format('%d\tW\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
  end
 end)
end
for _,address in ipairs({0xc000,0xc068,0xc0b3,0xc0f8,0x8ce5,0x8d00,0x8d39})do
 memory.registerexec(address,function(pc)
  if frame>=700 and frame<=1820 then
   hooks:write(string.format('%d\tX\t%04X\tmap=%02X x=%d y=%d 43=%02X 5B=%02X 59=%02X 112=%02X 05=%02X banks=%d,%d,%d,%d\n',
    frame,pc,memory.readbyte(0x47),memory.readbyte(0x406)+256*memory.readbyte(0x407),
    memory.readbyte(0x408)+256*memory.readbyte(0x409),memory.readbyte(0x43),memory.readbyte(0x5b),
    memory.readbyte(0x59),memory.readbyte(0x0112),memory.readbyte(0x05),banks[1],banks[2],banks[3],banks[4]))
  end
 end)
end
emu.speedmode('nothrottle')
for t=1,1820 do
 frame=t;local keys={}
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 then keys.left=true end
 joypad.set(1,keys);emu.frameadvance()
 if t>=700 and t%8==0 then
  log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,
   memory.readbyte(0x47),memory.readbyte(0x406)+256*memory.readbyte(0x407),
   memory.readbyte(0x408)+256*memory.readbyte(0x409),memory.readbyte(0x43),memory.readbyte(0x5b),
   memory.readbyte(0x59),memory.readbyte(0x0112),memory.readbyte(0x6881)))
 end
end
local f=assert(io.open(out..'frame-1820-sram.bin','wb'));f:write(memory.readbyterange(0x6800,0x800));f:close()
log:close();hooks:close()
local done=assert(io.open(out..'complete.txt','w'));done:write('cold boot, controller only');done:close()
emu.pause()
