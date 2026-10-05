local root=assert(os.getenv('FENGSHEN_ROOT'));local out=root..'/private-derived/world-master172-letter-investigation-controlled/'
local function tick(k)joypad.set(1,k or{});emu.frameadvance()end
local function idle(n)for i=1,n do tick()end end
local function w(a,v)memory.writebyte(a,v%256);memory.writebyte(a+1,math.floor(v/256))end
local function cp(n)
 for _,s in ipairs({{'ram',0,0x800},{'sram',0x6800,0x800},{'ppu',0,0x4000},{'cpu',0x8000,0x8000}})do local f=assert(io.open(out..n..'-'..s[1]..'.bin','wb'));f:write(s[1]=='ppu'and ppu.readbyterange(s[2],s[3])or memory.readbyterange(s[2],s[3]));f:close()end
 gui.savescreenshotas(out..n..'.png');local st=savestate.create(out..n..'.fc8');savestate.save(st);savestate.persist(st)
end
local trace=assert(io.open(out..'writes.tsv','w'));local label='';local armed=false
for _,a in ipairs({0x47,0x3a,0x3b,0x7d0,0x9d,0x9e,0x7c9,0x7c6,0x7c8,0x7ac,0x6004,0x6005,0x6006,0x6007,0xa5})do memory.registerwrite(a,1,function(addr,n,v)if armed then trace:write(string.format('%s\t%d\t%04X\t%d\tPC=%04X\n',label,emu.framecount(),addr,v,memory.getregister('pc')));trace:flush()end end)end
emu.speedmode('nothrottle');idle(100)
armed=false;savestate.load(savestate.create(root..'/private-derived/world-master172-letter-controlled/01-real-letter-front.fc8'));assert(memory.readbyte(0x47)==172);label='investigation';armed=true;cp('00-real-letter-front')
for _,key in ipairs({'A','down','down','down','A'})do local k={};k[key]=true;for i=1,8 do tick(k)end;idle(40);cp('menu-'..key..'-'..emu.framecount())end
for i=1,12 do for j=1,8 do tick({A=true})end;idle(120);cp(string.format('investigate-A-%02d',i))end
trace:close();local f=assert(io.open(out..'done.txt','w'));f:write('REAL_ORIGINAL_INVESTIGATION_KEYS_FROM_CONTROLLED_CURE_AND_POSITION_NOT_NORMAL_ROUTE_OR_ANDROID');f:close();emu.exit()
