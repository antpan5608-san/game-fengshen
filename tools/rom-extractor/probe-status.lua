-- FCEUX 2.6.6, normal controller inputs only. Generated snapshots are private.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/status-probe/'
local function dump(name,data) local f=assert(io.open(out..name,'wb'));f:write(data);f:close() end
local banks={63,63,63,63};local frame=0
local log=assert(io.open(out..'ram-writes.tsv','w'))
memory.registerwrite(0x6000,4,function(a,s,v) banks[a-0x6000+1]=v%64 end)
memory.registerwrite(0x500,0xc0,function(a,s,v)
 if frame<450 then
  local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
  log:write(string.format('%d\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
 end
end)
local function snap(f)
 local stem=string.format('frame-%04d',f)
 gui.savescreenshotas(out..stem..'.png')
 dump(stem..'-ram.bin',memory.readbyterange(0,0x800))
 dump(stem..'-ppu.bin',ppu.readbyterange(0,0x4000))
 savestate.save(savestate.create(out..stem..'.fc0'))
end
emu.speedmode('nothrottle')
local lastmap=-1
for f=1,2601 do
 frame=f;local keys={}
 if f==180 or f==360 then keys.start=true end
 if f==500 or f==610 then keys.A=true end
 if f==520 or f==535 or f==550 or f==565 or f==580 then keys.down=true end
 if f==690 or f==720 then keys.B=true end
 if f>=780 and f<1700 then keys.down=true end
 if f>=1750 and f<2100 then keys.left=true end
 if f>=2150 and f<2500 then keys.down=true end
 joypad.set(1,keys);emu.frameadvance()
 local m=memory.readbyte(0x47)
 if f==650 or (f>=780 and f%120==0) or (f>440 and m~=lastmap) then snap(f) end
 lastmap=m
end
log:close();dump('complete.txt','2601 frames; normal controller inputs, no RAM writes or cheats');emu.pause()
