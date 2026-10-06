local root=assert(os.getenv('FENGSHEN_ROOT'));local out=root..'/private-derived/world-west-potion-well-controlled/'
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
armed=false;savestate.load(savestate.create(root..'/private-derived/world-sages89-independent-return-controlled/01-real-independent-return.fc8'));assert(memory.readbyte(0x47)==16);assert(memory.readbyte(0x38)==0)
label='well-entry';armed=true;cp('00-original-world-potion')
w(0x406,120+44*16);w(0x408,120+82*16);memory.writebyte(0x7a,37);memory.writebyte(0x7b,75);idle(80);cp('01-controlled-world-before-entry')
for i=1,1200 do if memory.readbyte(0x47)~=16 then break end;tick({up=true})end
idle(400);cp('02-real-village-entry');assert(memory.readbyte(0x47)==8)
w(0x406,120+13*16);w(0x408,120+26*16);memory.writebyte(0x7a,6);memory.writebyte(0x7b,19);idle(80);cp('03-controlled-at-well')
label='well-menu'
for i,key in ipairs({'A','down','A','down','A','A','A','A','A','A','A','A','A','A','A','A','A','A','B','B'})do
 local k={};k[key]=true;for j=1,8 do tick(k)end;idle(120);cp(string.format('menu-%02d-%s',i,key))
end
trace:close();local f=assert(io.open(out..'done.txt','w'));f:write('CONTROLLED_POTION_VALID_ORIGINAL_SOURCE_POSITION_FIXTURE_REAL_VILLAGE_ENTRY_AND_WELL_MENU_NOT_NORMAL_ROUTE_OR_ANDROID');f:close();emu.exit()
