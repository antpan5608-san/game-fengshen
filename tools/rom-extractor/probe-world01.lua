-- WORLD-01 bounded cold-boot controller replay. Reads RAM, never writes it.
-- Modes: return enters the observed world house; next walks to the nearby town.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local mode=os.getenv('WORLD01_MODE') or 'return'
local delay=tonumber(os.getenv('WORLD01_DELAY') or '0') or 0
local out=root..'/private-derived/world01-'..mode..(delay>0 and '-'..delay or '')..'/'
local function dump(name,data)local f=assert(io.open(out..name,'wb'));f:write(data);f:close()end
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local log=assert(io.open(out..'trajectory.tsv','w'))
local changes=assert(io.open(out..'transitions.tsv','w'))
local collisions=assert(io.open(out..'collision.tsv','w'))
local banks={63,63,63,63}
local frame=0
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
memory.registerexec(0xca51,function()
 if banks[3]==2 and frame>=900 then
  collisions:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',frame,
   memory.readbyte(0x47),word(0x406),word(0x408),memory.readbyte(0x99),
   memory.readbyte(0x98),memory.readbyte(0x9c),memory.readbyte(0x18)))
 end
end)
memory.registerwrite(0x47,function(a,s,v)
 changes:write(string.format('%d\t%d\t%04X\t%d\t%d\n',frame,v,memory.getregister('pc'),word(0x406),word(0x408)))
end)
local paths={
 ['return']={{203,141},{203,142}},
 ['next']={{203,141},{202,141},{202,140},{202,139},{201,139},{200,139},
  {200,138},{200,137},{200,136},{200,135},{200,134},{200,133},
  {200,132},{200,131},{200,130},{201,130},{202,130}},
}
paths['roundtrip']=paths['next']
local points=assert(paths[mode],'Unknown WORLD01_MODE')
local index=1
local begun=false
local changedAt=nil
local townReady=false
local returnAt=nil
emu.speedmode('nothrottle')
for t=1,4000 do
 frame=t
 local k={}
 -- Existing cold-boot title/menu and observed no-NPC route out of map 114.
 if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if t>=780 and t<850 then k.down=true end
 local map=memory.readbyte(0x47)
 local x,y=word(0x406),word(0x408)
 if t>=900+delay and map==16 and x==3368 and y==2392 then begun=true end
 if begun and map==16 and index<=#points then
  local target=points[index]
  local tx,ty=120+16*target[1],120+16*target[2]
  if x==tx and y==ty then index=index+1;target=points[index] end
  if target then
   tx,ty=120+16*target[1],120+16*target[2]
   if x<tx then k.right=true elseif x>tx then k.left=true
   elseif y<ty then k.down=true elseif y>ty then k.up=true end
  end
 end
 if mode=='roundtrip' and changedAt and t>=changedAt+180 and map==0 and x==120 and y==360 then townReady=true end
 if mode=='roundtrip' and townReady and map==0 then
  if y>344 then k.up=true elseif t%12<8 then k.left=true end
 end
 joypad.set(1,k);emu.frameadvance()
 map=memory.readbyte(0x47);x=word(0x406);y=word(0x408)
 log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,map,x,y,index,memory.readbyte(0x97),memory.readbyte(0x9c)))
 if begun and map==(mode=='return' and 114 or 0) and not changedAt then
  changedAt=t
  dump('transition-ram.bin',memory.readbyterange(0,0x800))
  dump('transition-ppu.bin',ppu.readbyterange(0,0x4000))
 end
 if mode=='roundtrip' and townReady and map==16 and not returnAt then returnAt=t end
 if changedAt and (mode~='roundtrip' and t>=changedAt+120 or mode=='roundtrip' and returnAt and t>=returnAt+120) then
  dump('settled-ram.bin',memory.readbyterange(0,0x800))
  dump('settled-ppu.bin',ppu.readbyterange(0,0x4000))
  gui.savescreenshotas(out..'settled.png')
  break
 end
end
dump('final-ram.bin',memory.readbyterange(0,0x800))
dump('final-ppu.bin',ppu.readbyterange(0,0x4000))
gui.savescreenshotas(out..'final.png')
log:close();changes:close();collisions:close()
dump('result.txt',string.format('mode=%s begun=%s index=%d target=%d transitionFrame=%s returnFrame=%s townReady=%s finalMap=%d; controller only',
 mode,tostring(begun),index,#points,tostring(changedAt),tostring(returnAt),tostring(townReady),memory.readbyte(0x47)))
emu.pause()
