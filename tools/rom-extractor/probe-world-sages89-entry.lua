local root=assert(os.getenv('FENGSHEN_ROOT'));local out=root..'/private-derived/world-sages89-entry-talk-controlled/'
local function tick(k)joypad.set(1,k or{});emu.frameadvance()end
local function idle(n)for i=1,n do tick()end end
local function w(a,v)memory.writebyte(a,v%256);memory.writebyte(a+1,math.floor(v/256))end
local function cp(n)
 for _,s in ipairs({{'ram',0,0x800},{'sram',0x6800,0x800},{'ppu',0,0x4000},{'cpu',0x8000,0x8000}})do local f=assert(io.open(out..n..'-'..s[1]..'.bin','wb'));f:write(s[1]=='ppu'and ppu.readbyterange(s[2],s[3])or memory.readbyterange(s[2],s[3]));f:close()end
 gui.savescreenshotas(out..n..'.png');local st=savestate.create(out..n..'.fc8');savestate.save(st);savestate.persist(st)
end
local trace=assert(io.open(out..'writes.tsv','w'));local label='';local armed=false
for _,a in ipairs({0x47,0x3a,0x3b,0x7d0,0x9d,0x9e,0x7c9,0x7c6,0xa5})do memory.registerwrite(a,1,function(addr,n,v)if armed then trace:write(string.format('%s\t%d\t%04X\t%d\tPC=%04X\n',label,emu.framecount(),addr,v,memory.getregister('pc')));trace:flush()end end)end
emu.speedmode('nothrottle');idle(100)
armed=false;savestate.load(savestate.create(root..'/private-derived/world-after-rebirth-route/bridge-03-after-left.fc8'));assert(memory.readbyte(0x47)==16)
local f=assert(io.open(root..'/private-derived/world-west-snow-use-valid-source-controlled/followup-01-ram.bin','rb'));local data=f:read('*all');f:close()
for a=0x500,0x5ff do memory.writebyte(a,string.byte(data,a+1))end
for a=0x700,0x7ff do memory.writebyte(a,string.byte(data,a+1))end
w(0x406,120+219*16);w(0x408,120+145*16);memory.writebyte(0x7a,212);memory.writebyte(0x7b,138);idle(80)
label='sage89';cp('00-world-before');armed=true
for i=1,1200 do if memory.readbyte(0x47)~=16 then break end;tick({up=true})end;idle(400);cp('01-original-entry')
assert(memory.readbyte(0x47)==89)
for y=12,7,-1 do
 for i=1,160 do if(memory.readbyte(0x408)+256*memory.readbyte(0x409)-120)/16==y then break end;tick({up=true})end
 idle(20)
 if(memory.readbyte(0x408)+256*memory.readbyte(0x409)-120)/16~=y then cp('stopped-'..y);break end
end
cp('02-original-center-front')
for i=1,8 do tick({A=true})end;idle(40);cp('03-open-menu')
for i=1,8 do tick({A=true})end;idle(100);cp('04-normal-talk')
for i=1,6 do for j=1,8 do tick({A=true})end;idle(100);cp(string.format('talk-A-%02d',i))end
trace:close();local f=assert(io.open(out..'done.txt','w'));f:write('CONTROLLED_CURE_RAM_BLOCK_POSITION_FIXTURE_REAL_SAGES_ENTRY_TALK_NOT_NORMAL_ROUTE_OR_ANDROID');f:close();emu.exit()
