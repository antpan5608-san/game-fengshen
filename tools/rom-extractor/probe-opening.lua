-- Read-only observation of CPU RAM/PPU/banking. Inputs are recorded controller presses.
-- Run with FCEUX 2.6.6; no cheats and no emulated memory writes.
local out = "F:/apps/fengshen-remake/private-derived/opening-probe/"
local log=assert(io.open(out.."trace.tsv","w"))
local function dump(path,data) local f=assert(io.open(out..path,"wb"));f:write(data);f:close() end
log:write("frame\taddress\tvalue\tpc\n")
memory.registerwrite(0x6000,8,function(a,s,v)
 log:write(string.format("%d\t%04X\t%02X\t%04X\n",emu.framecount(),a,v,memory.getregister('pc')))
end)
emu.speedmode("nothrottle")
for frame=1,1800 do
 local keys={}
 if frame==180 or frame==360 then keys.start=true end
 if frame>=600 and frame%60==0 then keys.A=true end
 joypad.set(1,keys)
 emu.frameadvance()
 if frame%150==0 then
  local stem=string.format("frame-%04d",frame)
  gui.savescreenshotas(out..stem..".png")
  dump(stem.."-ram.bin",memory.readbyterange(0,0x800))
  dump(stem.."-sram.bin",memory.readbyterange(0x6800,0x800))
  dump(stem.."-cpu-rom.bin",memory.readbyterange(0x8000,0x8000))
  dump(stem.."-ppu.bin",ppu.readbyterange(0,0x4000))
  log:flush()
 end
end
log:close();dump("complete.txt","1800 frames; inputs start=180,360; A=600,660,...,1800\n")
emu.pause()
