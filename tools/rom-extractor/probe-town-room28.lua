-- Scoped original room28 normal new-game route and menu input.
-- No memory.writebyte, cheats or savestate load; raw outputs stay private-derived.
-- Requires a caller-created private-derived/town-room28 output directory.
local root=assert(os.getenv('FENGSHEN_ROOT'),'Set isolated private FENGSHEN_ROOT')
local out=root..'/private-derived/town-room28/'
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
local funds=false -- No inventory/player RAM setup; retain normal new game.
local opening={{8,20},{8,19},{8,18},{8,17},{8,16},{7,16}};local oi=1;local talked=nil;local ready=not funds
local roomRoute={{6,9},{6,8},{6,7},{6,6},{5,6},{4,6}};local roomPhase=1;local ri=1;local talkStarted=nil;local returned=nil
local town={{2,15},{3,15},{4,15},{5,15},{6,15},{7,15},{8,15},{9,15},{9,16},{9,17},{9,18},{9,19},{9,20},{9,21},{9,22},{9,23},{9,24},{10,24},{11,24},{12,24},{12,23}};local wi=1;local ti=1;local enter=nil;local atDoor=nil;local lastMap=-1
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
  k={};local e=t-enter
  if e==120 then snap('room28-stable-entry') end
  if map==28 and e>=180 then
   if roomPhase==1 or roomPhase==3 or roomPhase==5 then
    while roomRoute[ri] and x==120+16*roomRoute[ri][1] and y==120+16*roomRoute[ri][2] do ri=ri+1 end
    if roomRoute[ri] then steer(k,roomRoute[ri],x,y)
    elseif roomPhase~=5 then talkStarted=t;roomPhase=roomPhase+1;snap('actor'..math.floor(roomPhase/2)..'-neighbor') end
   elseif roomPhase==2 or roomPhase==4 then
    local dt=t-talkStarted
    if dt==5 then if roomPhase==2 then k.up=true else k.right=true end end
    if roomPhase==2 then
     if dt==40 or dt==160 then k.A=true end
     if dt==240 then snap('actor1-actual-dialogue') end
     if dt==360 or dt==440 or dt==520 then k.A=true end
     if dt==600 or dt==640 or dt==680 then k.B=true end
     if dt==720 then snap('actor1-after-menu-close') end
    else
     if dt==40 or dt==160 or dt==320 or dt==440 or dt==560 or dt==720 then k.A=true end
     if dt==80 or dt==100 or dt==120 or dt==480 or dt==500 or dt==520 then k.down=true end
     if dt==240 then snap('hidden-first-investigation') end
     if dt==640 then snap('hidden-repeat-investigation') end
     if dt==360 or dt==760 or dt==800 or dt==840 then k.B=true end
    end
    if dt>=900 then
     if roomPhase==2 then roomRoute={{5,6},{6,6},{6,5},{6,4}} else roomRoute={{6,5},{6,6},{6,7},{6,8},{6,9},{6,10}} end
     ri=1;roomPhase=roomPhase+1
    end
   end
  end
  if map==0 then
   if not returned then returned=t;snap('room28-return-transition') end
   if t==returned+140 then snap('room28-return-settled') end
  end
 end
 joypad.set(1,k);emu.frameadvance()
 local newMap=memory.readbyte(0x47)
 if newMap~=lastMap then snap('map');lastMap=newMap end
 if t%10==0 then log:write(string.format('%d\t%d\t%d\t%d\t%d\t%d\n',t,newMap,word(0x406),word(0x408),memory.readbyte(0),memory.readbyte(0x9d)))end
 if returned and t>=returned+180 then snap('room28-return-complete');break end
 if enter and t>=enter+6000 then snap('room28-incomplete-budget');break end
end
log:close();hooks:close();snap('final');dump('complete.txt','Normal controller input from new game; NO RAM writes; inspect actual map trajectory before accepting');emu.pause()
