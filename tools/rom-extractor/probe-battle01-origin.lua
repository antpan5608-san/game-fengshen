-- Narrow, read-only extension of probe-battle.lua's normal-input timeline.
-- Determine where its observed enemy 2/3 battle actually starts.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/battle01-origin/'
local log=assert(io.open(out..'timeline.tsv','w'))
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local function snap(t)
 local stem=string.format('frame-%04d',t)
 gui.savescreenshotas(out..stem..'.png')
 local f=assert(io.open(out..stem..'-ram.bin','wb'));f:write(memory.readbyterange(0,0x800));f:close()
end
emu.speedmode('nothrottle')
for t=1,2250 do
 local keys={}
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 then keys.left=true end
 if t>=2150 and t<2500 then keys.down=true end
 joypad.set(1,keys);emu.frameadvance()
 if t>=1600 then
  log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',
   t,memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x29),memory.readbyte(0x3a),
   memory.readbyte(0x18),memory.readbyte(0x7a),memory.readbyte(0x7b),memory.readbyte(0x97),
   memory.readbyte(0x9c),memory.readbyte(0x504),word(0x514),word(0x177)))
 end
 if t==1800 or t==1850 or t==1900 or t==1950 or t==1980 or t==2050 or t==2200 then snap(t) end
end
log:close()
local done=assert(io.open(out..'complete.txt','w'));done:write('normal controller input; no emulated memory writes');done:close()
emu.pause()
