local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/world-probe/'
local function dump(name,data)local f=assert(io.open(out..name,'wb'));f:write(data);f:close()end
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local banks={63,63,63,63};local frame=0
local col=assert(io.open(out..'collision.tsv','w'));local trajectory=assert(io.open(out..'trajectory.tsv','w'));local transitions=assert(io.open(out..'transition.tsv','w'))
memory.registerwrite(0x6000,4,function(a,s,v) banks[a-0x6000+1]=v%64 end)
memory.registerexec(0xca51,function()
 if banks[3]==2 then col:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%04X\n',frame,memory.readbyte(0x47),memory.readbyte(0x97),memory.readbyte(0x7a),memory.readbyte(0x7b),memory.readbyte(0x99),memory.readbyte(0x98),memory.readbyte(0x9c),memory.readbyte(0x18),word(0x406),word(0x408),word(0x12))) end
end)
memory.registerwrite(0x47,function(a,s,v)transitions:write(string.format('%d\tmap\t%d\t%04X\t%d\t%d\n',frame,v,memory.getregister('pc'),memory.readbyte(0x7a),memory.readbyte(0x7b)))end)
local function snap(t)
 local s=string.format('frame-%04d',t);dump(s..'-ram.bin',memory.readbyterange(0,0x800));dump(s..'-sram.bin',memory.readbyterange(0x6800,0x800));dump(s..'-ppu.bin',ppu.readbyterange(0,0x4000));gui.savescreenshotas(out..s..'.png')
end
emu.speedmode('nothrottle')
for t=1,1601 do
 frame=t;local k={}
 if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if t>=780 and t<1100 then k.down=true end
 if t>=1150 and t<1182 then k.left=true end
 if t>=1250 and t<1282 then k.right=true end
 joypad.set(1,k);emu.frameadvance()
 trajectory:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x7a),memory.readbyte(0x7b),memory.readbyte(0x97),memory.readbyte(0x9c)))
 if t==450 or t==840 or t==843 or t==887 or t==960 or t==1100 or t==1182 or t==1282 or t==1500 then snap(t) end
end
col:close();trajectory:close();transitions:close();dump('complete.txt','Normal-input replay; no cheats or emulated RAM writes.');emu.pause()
