local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/battle-probe/'
local function dump(name,data) local f=assert(io.open(out..name,'wb'));f:write(data);f:close() end
-- Cold boot replay avoids depending on emulator save-state serialization.
local banks={63,63,63,63};local frame=0
local writes=assert(io.open(out..'writes.tsv','w'));local reads={}
memory.registerwrite(0x6000,4,function(a,s,v) banks[a-0x6000+1]=v%64 end)
memory.registerwrite(0x500,0x300,function(a,s,v)
 if frame>=1900 then
  local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
  writes:write(string.format('%d\t%04X\t%02X\t%04X\t%d\n',frame,a,v,pc,banks[slot] or -1))
 end
end)
memory.registerread(0x8000,0x8000,function(a,s,v)
 if frame>=1900 and frame<2400 then local off=16+banks[math.floor((a-0x8000)/8192)+1]*8192+a%8192;reads[off]=(reads[off] or 0)+1 end
end)
emu.speedmode('nothrottle')
for t=1,6001 do
 frame=t;local keys={}
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 then keys.left=true end
 if t>=2150 and t<2500 then keys.down=true end
 if t>=2640 and t%60==0 then keys.A=true end
 joypad.set(1,keys);emu.frameadvance()
 if t>=1980 and t%60==0 then
  local stem=string.format('frame-%04d',t);gui.savescreenshotas(out..stem..'.png')
  dump(stem..'-ram.bin',memory.readbyterange(0,0x800));dump(stem..'-sram.bin',memory.readbyterange(0x6800,0x800))
  dump(stem..'-ppu.bin',ppu.readbyterange(0,0x4000))
 end
end
writes:close();local rf=assert(io.open(out..'reads.tsv','w'));for off,n in pairs(reads) do rf:write(string.format('%06X\t%d\n',off,n)) end;rf:close()
dump('complete.txt','cold boot; status-probe inputs through 2500, then A every 60 frames from 2640 to 6000');emu.pause()
