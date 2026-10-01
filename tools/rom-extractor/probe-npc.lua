local out="F:/apps/fengshen-remake/private-derived/npc-probe/"
local function dump(path,data) local f=assert(io.open(out..path,"wb"));f:write(data);f:close() end
local banks={63,63,63,63};local reads={};local writes=assert(io.open(out.."ppu-writes.tsv","w"));local mapper=assert(io.open(out.."mapper.tsv","w"))
local frame=0
memory.registerwrite(0x6000,8,function(a,s,v)
 if a<0x6004 then banks[a-0x6000+1]=v%64 end
 mapper:write(string.format("%d\t%04X\t%02X\t%04X\n",frame,a,v,memory.getregister('pc')))
end)
memory.registerread(0x8000,0x8000,function(a,s,v)
 if (frame>=362 and frame<435) or (frame>=695 and frame<800) then
  local slot=math.floor((a-0x8000)/8192)+1
  local offset=16+banks[slot]*8192+(a%8192)
  reads[offset]=(reads[offset] or 0)+1
 end
end)
memory.registerwrite(0x2007,function(a,s,v)
 if frame>=695 and frame<800 then writes:write(string.format("%d\t%02X\t%04X\t%d\n",frame,v,memory.getregister('pc'),banks[4])) end
end)
emu.speedmode("nothrottle")
for f=1,1451 do
 frame=f;local keys={}
 if f==180 or f==360 then keys.start=true end
 if f>=500 and f<580 then keys.up=true end
 if f==700 or f==730 or f==1050 or f==1300 then keys.A=true end
 joypad.set(1,keys);emu.frameadvance()
 if f==450 or f==600 or f==750 or f==900 or f==1040 or f==1200 or f==1450 then
  local stem=string.format("frame-%04d",f)
  gui.savescreenshotas(out..stem..".png")
  dump(stem.."-ram.bin",memory.readbyterange(0,0x800));dump(stem.."-sram.bin",memory.readbyterange(0x6800,0x800))
  dump(stem.."-ppu.bin",ppu.readbyterange(0,0x4000))
  dump(stem.."-cpu-rom.bin",memory.readbyterange(0x8000,0x8000))
  savestate.save(savestate.create(out..stem..".fc0"))
 end
end
writes:close();mapper:close()
local f=assert(io.open(out.."rom-reads.tsv","w"));for offset,n in pairs(reads) do f:write(string.format("%06X\t%d\n",offset,n)) end;f:close()
dump("complete.txt","normal inputs; no cheats or emulated memory writes\n");emu.pause()
