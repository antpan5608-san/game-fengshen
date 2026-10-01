-- BATTLE-01 established normal input, recorded for current encounter/attack/victory audio.
local out='F:/apps/fengshen-remake/private-derived/audio-log01/'
local function exists(p)local f=io.open(p,'r');if f then f:close();return true end;return false end
emu.speedmode('normal');while not exists(out..'battle-begin.txt')do emu.frameadvance()end
emu.poweron();local log=assert(io.open(out..'battle-timeline.tsv','w'))
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
for t=1,6001 do
 local k={};if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if t>=780 and t<1700 then k.down=true end
 if t>=1750 and t<2100 then k.left=true end
 if t>=2150 and t<2500 then k.down=true end
 if t>=2640 and t%60==0 then k.A=true end
 joypad.set(1,k);emu.frameadvance()
 log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,memory.readbyte(0x47),memory.readbyte(0x110),word(0x514),word(0x570),word(0x572),memory.readbyte(0x3c8)));if t%60==0 then log:flush()end
end
log:close();local f=assert(io.open(out..'battle-complete.txt','w'));f:write('normal controller only');f:close();emu.pause()
