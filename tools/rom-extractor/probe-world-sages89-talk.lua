local root=assert(os.getenv('FENGSHEN_ROOT'));local out=root..'/private-derived/world-sages89-adjacent-talk-controlled/'
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
armed=false;savestate.load(savestate.create(root..'/private-derived/world-sages89-entry-talk-controlled/02-original-center-front.fc8'));assert(memory.readbyte(0x47)==89)
label='sages-adjacent';armed=true
for i=1,160 do if(memory.readbyte(0x408)+256*memory.readbyte(0x409)-120)/16==6 then break end;tick({up=true})end;idle(30);cp('00-real-center-adjacent')
assert((memory.readbyte(0x408)+256*memory.readbyte(0x409)-120)/16==6)
for i=1,8 do tick({A=true})end;idle(40);cp('01-menu')
for i=1,8 do tick({A=true})end;idle(100);cp('02-first-talk')
for i=1,8 do for j=1,8 do tick({A=true})end;idle(100);cp(string.format('talk-A-%02d',i))end
trace:close();local f=assert(io.open(out..'done.txt','w'));f:write('CONTROLLED_CURE_RAM_BLOCK_POSITION_FIXTURE_REAL_SAGES_ENTRY_TALK_NOT_NORMAL_ROUTE_OR_ANDROID');f:close();emu.exit()
