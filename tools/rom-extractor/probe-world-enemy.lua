-- CONTROLLED BOSS FIXTURE; encounter zone/group injected, not normal play.
local root=assert(os.getenv('FENGSHEN_ROOT'));local zone=tonumber(assert(os.getenv('WORLD_ENEMY_ZONE')));local group=tonumber(assert(os.getenv('WORLD_ENEMY_GROUP')))
local verifiedCounts={[4]=14,[16]=19}
assert(verifiedCounts[zone] and group>=0 and group<verifiedCounts[zone])
local out=root..'/private-derived/world-enemy-zone-'..zone..'-group-'..group..'/'
local frame=0;local banks={63,63,63,63};local injected=false;local lastphase=-1;local ticks=0
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local log=assert(io.open(out..'actions.tsv','w'));local mapper=assert(io.open(out..'mapper.tsv','w'))
local function snap(n)
 local f=assert(io.open(out..n..'-sram.bin','wb'));f:write(memory.readbyterange(0x6800,0x800));f:close()
 local f=assert(io.open(out..n..'-ram.bin','wb'));f:write(memory.readbyterange(0,0x800));f:close()
 local f=assert(io.open(out..n..'-ppu.bin','wb'));f:write(ppu.readbyterange(0,0x4000));f:close()
 gui.savescreenshotas(out..n..'.png')
end
memory.registerwrite(0x6000,8,function(a,n,v)
 if a<0x6004 then banks[a-0x6000+1]=v%64 end
 if frame>1700 and frame<2600 then mapper:write(string.format('%d\t%04X\t%d\t%04X\n',frame,a,v,memory.getregister('pc')))end
end)
memory.registerexec(0x8ce5,function() if banks[1]==4 and not injected then memory.writebyte(0x112,zone);memory.writebyte(0x43,group);injected=true end end)
for _,pc in ipairs({0x8de9,0x8e41,0x8e5a,0x8e6c,0x8f41,0xa956,0xaa08,0xaa82,0xab2a,0xab52,0xb094})do
 memory.registerexec(pc,function()
  if banks[math.floor((pc-0x8000)/8192)+1]==(pc<0xa000 and 36 or 37) and injected then
   log:write(string.format('%d\t%04X\trng=%d slot=%d behavior=%d physical=%d special=%d target=%d damage=%d HP=%d enemy=%d enemyHP=%d\n',frame,pc,memory.readbyte(0x43),memory.readbyte(0x367),memory.readbyte(0x69db+memory.readbyte(0x367)),memory.readbyte(0x6951),memory.readbyte(0x693e),memory.readbyte(0x368),word(0x137),word(0x514),memory.readbyte(0x6977+memory.readbyte(0x367)),word(0x6980+2*memory.readbyte(0x367))));log:flush()
   if pc==0xab52 then ticks=ticks+1;snap('damage-'..ticks)end
  end
 end)
end
emu.speedmode('nothrottle')
for t=1,3000 do
 frame=t;local k={};local state=memory.readbyte(0)
 if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if t>=780 and t<1700 then k.down=true end
 if t>=1750 and t<2100 and state~=8 then k.left=true end
 if t==2200 or t==2201 then k.B=true end
 if t>4000 and t%12==0 then
  if memory.readbyte(0x110)==4 and memory.readbyte(0xde)==1 then
   if memory.readbyte(0xe0)~=0 then k.down=true else k.A=true end
  else k.A=true end
 end
 joypad.set(1,k);emu.frameadvance()
 if t==2400 or t==2700 then snap('frame-'..t)end
 if ticks>=14 then break end
end
snap('final');log:close();mapper:close();local f=assert(io.open(out..'done.txt','w'));f:write('CONTROLLED zone/group fixture, original full-group loader; no normal-play claim');f:close()
if os.getenv('WORLD_ENEMY_EXIT')=='1' then emu.exit() else emu.pause() end
