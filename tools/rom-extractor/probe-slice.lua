-- Opening NPC, tile collision and world transition observation. Controller only.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/slice-probe/'
local banks={63,63,63,63};local frame=0
local function dump(name,data) local f=assert(io.open(out..name,'wb'));f:write(data);f:close() end
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1) end
local vars=assert(io.open(out..'variables.tsv','w'));local col=assert(io.open(out..'collision.tsv','w'));local trajectory=assert(io.open(out..'trajectory.tsv','w'))
memory.registerwrite(0x6000,4,function(a,s,v) banks[a-0x6000+1]=v%64 end)
local function watch(a,s,v)
 local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
 vars:write(string.format('%d\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
end
memory.registerwrite(0x29,2,watch);memory.registerwrite(0x3a,2,watch)
memory.registerexec(0xca51,function()
 if banks[3]==2 then
  col:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%04X\n',frame,memory.readbyte(0x47),memory.readbyte(0x97),memory.readbyte(0x7a),memory.readbyte(0x7b),memory.readbyte(0x99),memory.readbyte(0x98),memory.readbyte(0x9c),memory.readbyte(0x18),word(0x406),word(0x408),word(0x12)))
 end
end)
local function snap(t)
 local stem=string.format('frame-%04d',t);dump(stem..'-ram.bin',memory.readbyterange(0,0x800));dump(stem..'-sram.bin',memory.readbyterange(0x6800,0x800));dump(stem..'-ppu.bin',ppu.readbyterange(0,0x4000));gui.savescreenshotas(out..stem..'.png')
end
emu.speedmode('nothrottle')
for t=1,4101 do
 frame=t;local k={}
 if t==180 or t==360 then k.start=true end
 if t>=500 and t<580 then k.up=true end
 if t==700 or t==730 or t==1250 then k.A=true end
 if t==1280 or t==1870 or t==1900 then k.B=true end
 if t>=1300 and t<1308 then k.left=true end
 if t>=1320 and t<1328 then k.up=true end
 if t==1400 or t==1430 or t==1800 or t==1840 then k.A=true end
 -- Reach a wall along the hall's row, then reverse to test free movement.
 if t>=2000 and t<2080 then k.left=true end
 if t>=2120 and t<2160 then k.right=true end
 if t>=2200 and t<2280 then k.down=true end
 if t>=2320 and t<2440 then k.right=true end
 if t>=2480 and t<2600 then k.down=true end
 -- All observations, including failed movements and current location, preserved.
 joypad.set(1,k);emu.frameadvance()
 trajectory:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x7a),memory.readbyte(0x7b),memory.readbyte(0x97),memory.readbyte(0x9c),memory.readbyte(0)))
 if t==450 or t==600 or t==900 or t==1040 or t==1500 or t==1700 or t==1950 or t==2080 or t==2160 or t==2280 or t==2440 or t==2600 or t==3000 or t==4000 then snap(t) end
end
vars:close();col:close();trajectory:close();dump('complete.txt','Cold boot, fixed controller timeline; read-only memory hooks.');emu.pause()
