-- AUDIO-LOG-01: replay the established normal controller route; no RAM writes.
local root='F:/apps/fengshen-remake'
local out=root..'/private-derived/audio-log01/'
local function exists(p)local f=io.open(p,'r');if f then f:close();return true end;return false end
emu.speedmode('normal')
while not exists(out..'begin.txt')do emu.frameadvance()end
emu.poweron()
local log=assert(io.open(out..'timeline.tsv','w'))
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local targets={{203,141},{202,141},{202,140},{202,139},{201,139},{200,139},{200,138},{200,137},{200,136},{200,135},{200,134},{200,133},{200,132},{200,131},{200,130},{201,130},{202,130}}
local index=1;local worldStart=nil;local townStart=nil
for t=1,16000 do
 local k={};local map=memory.readbyte(0x47);local x,y=word(0x406),word(0x408)
 if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 -- Capture idle home before leaving through the already proven exit.
 if t>=4500 and t<4600 then k.down=true end
 if map==16 and not worldStart then worldStart=t end
 if worldStart and t>worldStart+4000 and map==16 and index<=#targets then
  local p=targets[index];local tx,ty=120+16*p[1],120+16*p[2]
  if x==tx and y==ty then index=index+1;p=targets[index]end
  if p then tx,ty=120+16*p[1],120+16*p[2];if x<tx then k.right=true elseif x>tx then k.left=true elseif y<ty then k.down=true elseif y>ty then k.up=true end end
 end
 if map==0 and not townStart then townStart=t end
 joypad.set(1,k);emu.frameadvance()
 if t%60==0 or memory.readbyte(0x47)~=map then
  log:write(string.format('%d\t%d\t%d\t%d\t%d\n',t,memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x110)));log:flush()
 end
 if townStart and t>townStart+4000 then break end
end
log:close();local f=assert(io.open(out..'complete.txt','w'));f:write('normal controller only');f:close();emu.pause()
