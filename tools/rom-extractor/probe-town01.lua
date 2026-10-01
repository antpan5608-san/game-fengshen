-- TOWN-01 extends WORLD-01's normal controller route; reads only, no RAM writes.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local mode=os.getenv('TOWN01_MODE') or 'weapon'
local out=root..'/private-derived/town01-'..mode..'/'
local function dump(n,b)local f=assert(io.open(out..n,'wb'));f:write(b);f:close()end
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local banks={63,63,63,63};local frame=0
local log=assert(io.open(out..'timeline.tsv','w'));local hooks=assert(io.open(out..'writes.tsv','w'))
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
for _,a in ipairs({0x47,0x501,0x502,0x503,0x560,0x568,0x56c})do memory.registerwrite(a,1,function(a,s,v)
 local pc=memory.getregister('pc');hooks:write(string.format('%d\t%04X\t%d\t%04X\t%d\n',frame,a,v,pc,banks[math.floor((pc-0x8000)/8192)+1] or -1))end)end
memory.registerwrite(0x5d0,0xb0,function(a,s,v)
 local pc=memory.getregister('pc');hooks:write(string.format('%d\t%04X\t%d\t%04X\t%d\n',frame,a,v,pc,banks[math.floor((pc-0x8000)/8192)+1] or -1))end)
for _,span in ipairs({{0x6aa,6},{0x6c4,16}})do memory.registerwrite(span[1],span[2],function(a,s,v)
 local pc=memory.getregister('pc');hooks:write(string.format('%d\t%04X\t%d\t%04X\t%d\n',frame,a,v,pc,banks[math.floor((pc-0x8000)/8192)+1] or -1))end)end
local function snap(label)
 local n=string.format('%06d-%s',frame,label)
 dump(n..'-ram.bin',memory.readbyterange(0,0x800));dump(n..'-sram.bin',memory.readbyterange(0x6800,0x800))
 dump(n..'-ppu.bin',ppu.readbyterange(0,0x4000));gui.savescreenshotas(out..n..'.png')
end
local world={{203,141},{202,141},{202,140},{202,139},{201,139},{200,139},{200,138},{200,137},{200,136},{200,135},{200,134},{200,133},{200,132},{200,131},{200,130},{201,130},{202,130}}
local funds=os.getenv('TOWN01_FUNDS')=='1'
local opening={{8,20},{8,19},{8,18},{8,17},{8,16},{7,16}};local oi=1;local talked=nil;local ready=not funds
local town=dofile(out..'path.lua');local wi=1;local ti=1;local enter=nil;local atDoor=nil;local lastMap=-1
local function steer(k,target,x,y)
 local tx,ty=120+16*target[1],120+16*target[2]
 if x<tx then k.right=true elseif x>tx then k.left=true elseif y<ty then k.down=true elseif y>ty then k.up=true end
end
emu.speedmode('nothrottle')
for t=1,14000 do
 frame=t;local k={};local map=memory.readbyte(0x47);local x,y=word(0x406),word(0x408)
 if t==180 or t==360 then k.start=true end
 if t==500 or t==610 then k.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then k.down=true end
 if t==690 or t==720 then k.B=true end
 if not funds and t>=780 and t<850 then k.down=true end
 if funds and t>=780 and map==114 then
  if oi<=#opening then
   while opening[oi] and x==120+16*opening[oi][1] and y==120+16*opening[oi][2] do oi=oi+1 end
   if opening[oi] then steer(k,opening[oi],x,y)elseif not talked then talked=t end
  end
  if talked then local e=t-talked
   if e==5 then k.up=true end
   if e==40 or e==150 or e==350 or e==550 then k.A=true end
   if e==750 or e==820 then k.B=true end
   if e>900 then ready=true;if x<248 then k.right=true elseif y<584 then k.down=true end end
  end
 end
 if ready and t>=980 and map==16 and wi<=#world then
  while world[wi] and x==120+16*world[wi][1] and y==120+16*world[wi][2] do wi=wi+1 end
  if world[wi]then steer(k,world[wi],x,y)end
 end
 if map==0 then wi=#world+1 end
 if map==0 and wi>#world and not enter then
  if not atDoor then atDoor=t+180 end
  if t>=atDoor then
   while town[ti] and x==120+16*town[ti][1] and y==120+16*town[ti][2] do ti=ti+1 end
   if town[ti]then steer(k,town[ti],x,y)elseif t%30==0 then k.up=true end
  end
 elseif map~=0 and map~=16 and map~=114 and t>1200 and not enter then enter=t;snap('entered') end
 if map==16 and memory.readbyte(0)==8 then
  k={}
  if t%60==0 then
   if memory.readbyte(0x110)==4 and memory.readbyte(0xde)==1 and memory.readbyte(0xe0)~=0 then k.down=true else k.A=true end
  end
 end
 if enter then
  local e=t-enter
  -- Discovery snapshot first; then normal upward walk and periodic talk/confirm.
  if e>180 and e<450 then k.up=true end
  if e==500 or e==700 or e==1000 or e==1450 or e==1800 or e==2150 or e==2500 or e==2800 then k.A=true end
  if e==1950 or e==2950 or e==3050 or e==3200 then k.B=true end
  if e==2050 then k.down=true end
  if e>=3400 and e<3650 then k.down=true end
  if e==1300 or e==1700 or e==2400 or e==2700 then snap('transaction')end
  if e==1150 and tonumber(os.getenv('TOWN01_ITEM_INDEX') or '0')>=1 then k.down=true end
  if e==1250 and tonumber(os.getenv('TOWN01_ITEM_INDEX') or '0')>=2 then k.down=true end
 end
 joypad.set(1,k);emu.frameadvance()
 local newMap=memory.readbyte(0x47)
 if newMap~=lastMap then snap('map');lastMap=newMap end
 if t%10==0 then log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n',t,newMap,word(0x406),word(0x408),memory.readbyte(0),memory.readbyte(0xde),memory.readbyte(0xe0),word(0x501),memory.readbyte(0x99),memory.readbyte(0x98)))end
 if t%120==0 and t>1100 then snap('state')end
 if enter and t>=enter+3800 then break end
end
log:close();hooks:close();snap('final');dump('complete.txt','Normal controller discovery, no emulated memory writes');emu.pause()
