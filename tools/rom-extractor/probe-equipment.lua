-- Bounded cold-boot controller probe for the opening servant's knife and menu.
-- No emulated RAM writes, cheats, or save-state injection.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/equipment-probe-final/'
local function dump(name,data) local f=assert(io.open(out..name,'wb'));f:write(data);f:close() end
local log=assert(io.open(out..'state.tsv','w'))
local writes=assert(io.open(out..'writes.tsv','w'))
local banks={63,63,63,63};local frame=0
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
local function trace(a,s,v)
 local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
 writes:write(string.format('%d\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
end
memory.registerwrite(0x6818,2,trace);memory.registerwrite(0x560,1,trace);memory.registerwrite(0x548,2,trace)
memory.registerwrite(0x5d0,1,trace)
log:write('frame\tmap\tx\ty\tfacing\tright\tleft\tbody\tfeet\tweaponBonus\tarmorBonus\tmp\tmaxMp\n')
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1) end
local function snap(f)
 local stem=string.format('frame-%04d',f)
 dump(stem..'-ram.bin',memory.readbyterange(0,0x800))
 dump(stem..'-sram.bin',memory.readbyterange(0x6800,0x800))
 gui.savescreenshotas(out..stem..'.png')
end
emu.speedmode('nothrottle')
for f=1,2200 do
 frame=f
 local k={}
 if f==180 or f==360 then k.start=true end
 if f>=500 and f<532 then k.right=true end
 if f>=540 and f<548 then k.down=true end
 if f==600 or f==630 or f==660 or f==690 or f==720 or f==750 then k.A=true end
 if f==830 or f==860 or f==890 or f==920 then k.down=true end
 if f==960 then k.A=true end
 if f==1150 then k.A=true end
 if f==1200 then k.down=true end
 if f==1230 then k.A=true end
 if f==1400 then k.B=true end
 if f==1450 or f==1660 or f==1740 or f==1810 or f==1900 or f==1980 then k.A=true end
 if f==1500 or f==1540 or f==1580 or f==1620 then k.down=true end
 joypad.set(1,k);emu.frameadvance()
 if f%10==0 then log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',
   f,memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x97),
   memory.readbyte(0x560),memory.readbyte(0x564),memory.readbyte(0x568),memory.readbyte(0x56c),
   word(0x548),word(0x550),word(0x524),word(0x52c))) end
 if f==450 or f==590 or f==610 or f==710 or f==810 or f==930 or f==970 or f==1010 or f==1100 or f==1170 or f==1220 or f==1250 or f==1350 or f==1420 or f==1470 or f==1630 or f==1680 or f==1760 or f==1830 or f==1920 or f==2000 or f==2100 then snap(f) end
end
log:close();writes:close();dump('complete.txt','Cold boot with fixed normal controller inputs; no RAM writes or cheats.')
emu.pause()
