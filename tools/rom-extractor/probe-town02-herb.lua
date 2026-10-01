-- Isolated original-game boundary fixture: RAM setup is NOT normal play evidence.
local root=assert(os.getenv('FENGSHEN_ROOT'),'Set FENGSHEN_ROOT')
local out=root..'/private-derived/town02/'
local case=os.getenv('HERB_CASE') or 'injured'
local hp=tonumber(os.getenv('HERB_HP') or '5')
local flag=tonumber(os.getenv('HERB_FLAG') or '0')
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
emu.speedmode('nothrottle')
local frame=0;local banks={63,63,63,63}
local writes=assert(io.open(out..case..'-writes.tsv','w'))
memory.registerwrite(0x6000,4,function(a,n,v)banks[a-0x6000+1]=v%64 end)
for _,addr in ipairs({0x514,0x515,0x5b0})do memory.registerwrite(addr,1,function(a,n,v)
 if frame>1000 then local pc=memory.getregister('pc');writes:write(string.format('%d\t%04X\t%d\t%04X\t%d\n',frame,a,v,pc,banks[math.floor((pc-0x8000)/8192)+1] or -1)) end
end)end
local log=assert(io.open(out..case..'-timeline.tsv','w'))
for t=1,2400 do
 frame=t;local k={}
 if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if t==1000 then
  memory.writebyte(0x570,0);memory.writebyte(0x5b0,tonumber(os.getenv('HERB_COUNT') or '3'))
  memory.writebyte(0x514,hp);memory.writebyte(0x51c,100);memory.writebyte(0x544,flag)
 end
 if t==1040 or t==1220 or t==1380 or t==1540 or t==1700 or (t==1860 and case~='cancel') then k.A=true end
 if t==1140 then k.down=true end
 if t==1860 and case=='cancel' then k.B=true end
 joypad.set(1,k);emu.frameadvance()
 if t==1100 or t==1300 or t==1460 or t==1620 or t==1780 or t==2020 then gui.savescreenshotas(out..case..'-menu-'..t..'.png') end
 if t%10==0 and t>=1000 then log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,memory.readbyte(0),memory.readbyte(0x060a),memory.readbyte(0x0678),memory.readbyte(0x0605),memory.readbyte(0x06d7),word(0x514),memory.readbyte(0x5b0))) end
end
log:close();writes:close();local f=assert(io.open(out..case..'-result.txt','w'));f:write('hp='..word(0x514)..' qty='..(memory.readbyte(0x5b0)%128)..' flags='..memory.readbyte(0x544));f:close(); local f=assert(io.open(out..'done.txt','w'));f:write(case..' complete');f:close();emu.pause()
