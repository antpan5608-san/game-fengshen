-- Matched target ROM; isolated original contact/status fixtures, not Android/new-game acceptance.
local root=assert(os.getenv('FENGSHEN_ROOT'));local out=root..'/private-derived/world-village4-ferry-cost/'
local function tick(k)joypad.set(1,k or{})emu.frameadvance()end
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local function w(a,v)memory.writebyte(a,v%256);memory.writebyte(a+1,math.floor(v/256))end
local function cp(n)
 for _,s in ipairs({{'ram',0,0x800},{'sram',0x6800,0x800},{'ppu',0,0x4000},{'cpu',0x8000,0x8000}})do local f=assert(io.open(out..n..'-'..s[1]..'.bin','wb'));f:write(s[1]=='ppu'and ppu.readbyterange(s[2],s[3])or memory.readbyterange(s[2],s[3]));f:close()end
 gui.savescreenshotas(out..n..'.png');local st=savestate.create(out..n..'.fc8');savestate.save(st);savestate.persist(st)
end
emu.speedmode('nothrottle');for _=1,100 do tick({})end
local log=assert(io.open(out..'cases.tsv','w'));log:write('case\tframe\tmap\tx\ty\tevent\tstage\tstep5b\thp0\tstatus0\thp2\tstatus2\n')
local function row(name)log:write(name..'\t'..emu.framecount()..'\t'..memory.readbyte(0x47)..'\t'..((word(0x406)-120)/16)..'\t'..((word(0x408)-120)/16)..'\t'..memory.readbyte(0xa5)..'\t'..memory.readbyte(0xa6)..'\t'..memory.readbyte(0x5b)..'\t'..word(0x514)..'\t'..memory.readbyte(0x544)..'\t'..word(0x518)..'\t'..memory.readbyte(0x546)..'\n');log:flush()end
for _,case in ipairs({{'forward','world-village4-ferry-trace-controller/01-event45-contact.fc8',16,false},{'reverse','world-village4-ferry-next/03-reverse-contact.fc8',4,false},{'all-poison-low','world-village4-ferry-trace-controller/01-event45-contact.fc8',16,true}})do
 savestate.load(savestate.create(root..'/private-derived/'..case[2]));w(0x514,case[4]and 1 or 100);w(0x51c,100);memory.writebyte(0x544,2)
 if case[4]then w(0x518,1);memory.writebyte(0x546,2)end
 row(case[1]..'-before');local last=-1;local steps=0
 for t=1,3200 do
  if word(0x514)~=last then last=word(0x514);row(case[1]..'-hp-change')end
  tick({});if memory.readbyte(0xa5)==0 and memory.readbyte(0x47)==case[3]then for _=1,160 do tick({})end;break end
 end
 cp(case[1]..'-after');row(case[1]..'-after')
end
log:close();emu.exit()
