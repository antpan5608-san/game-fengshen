-- BATTLE-01 established normal input, recorded for current encounter/attack/victory audio.
local out='F:/apps/fengshen-remake/private-derived/audio-log01/'
local function exists(p)local f=io.open(p,'r');if f then f:close();return true end;return false end
emu.speedmode('normal');local log=assert(io.open(out..'battle-retry-timeline.tsv','w'))
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
for t=1,6001 do
 local k={};if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if t>=780 and t<1700 then k.down=true end
 if t>=1750 and t<2100 then k.left=true end
 if t>=2150 and t<2500 then k.down=true end
 if t>=2640 then
   if memory.readbyte(0x18)==33 then
    if math.floor((t-2640)/180)%2==0 then k.up=true else k.down=true end
   elseif t%60==0 then k.A=true end
 end
 joypad.set(1,k);emu.frameadvance()
 log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,memory.readbyte(0x47),memory.readbyte(0x110),word(0x514),word(0x570),word(0x572),memory.readbyte(0x3c8),memory.readbyte(0x18)));if t%60==0 then log:flush()end
end
log:close();local f=assert(io.open(out..'battle-retry-complete.txt','w'));f:write('normal controller only');f:close();emu.pause()
