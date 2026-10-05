local root=assert(os.getenv('FENGSHEN_ROOT'));local out=root..'/private-derived/world-lotus136-early-flag-script30-controlled/'
local function tick(k)joypad.set(1,k or{});emu.frameadvance()end
local function idle(n)for i=1,n do tick()end end
local f=assert(io.open(out..'flag-lifecycle.tsv','w'));f:write('flagBefore\tmapBefore\tflagAfter\tmapAfter\tyangStatusAfter\tmap37Context\n')
emu.speedmode('nothrottle');idle(100)
for _,flag in ipairs({0,1})do
 savestate.load(savestate.create(root..'/private-derived/world-jiameng-valid-field-script-controlled/validfield148-controlled-post-battle-stage3.fc8'))
 assert(memory.readbyte(0x47)==148);memory.writebyte(0x788,flag)
 for i=1,6000 do
  if i%80==0 then tick({A=true})else tick()end
  if memory.readbyte(0x47)==37 then idle(400);break end
 end
 assert(memory.readbyte(0x47)==37)
 f:write(string.format('%d\t148\t%d\t%d\t%d\t%d\n',flag,memory.readbyte(0x788),memory.readbyte(0x47),memory.readbyte(0x546),memory.readbyte(0x7d6)));f:flush()
 gui.savescreenshotas(out..'flag'..flag..'-map37.png');local st=savestate.create(out..'flag'..flag..'-map37.fc8');savestate.save(st);savestate.persist(st)
end
f:close();local done=assert(io.open(out..'done.txt','w'));done:write('CONTROLLED_ORIGINAL_PREEXISTING_FAIRY_FLAG_EVENT_BOUNDARY_NOT_NORMAL_VICTORY');done:close();emu.exit()
