-- Controlled normal-input battles. No ROM/RAM writes, cheats or artificial EXP.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/level-probe/'
local function dump(name,data) local f=assert(io.open(out..name,'wb'));f:write(data);f:close() end
local log=assert(io.open(out..'events.tsv','w'));local writes=assert(io.open(out..'writes.tsv','w'))
local banks={63,63,63,63};local frame=0;local attempt=0;local branch=0
local function word(a) return memory.readbyte(a)+256*memory.readbyte(a+1) end
local function snapshot(label)
 local name=string.format('%06d-b%d-%s',frame,branch,label)
 dump(name..'-ram.bin',memory.readbyterange(0,0x800));dump(name..'-sram.bin',memory.readbyterange(0x6800,0x800));dump(name..'-ppu.bin',ppu.readbyterange(0,0x4000))
 gui.savescreenshotas(out..name..'.png')
 log:write(string.format('%d\t%d\t%s\t%d\t%d\t%d\t%d\t%d\t%d\n',frame,branch,label,memory.readbyte(0),memory.readbyte(0x504),word(0x508),word(0x514),word(0x6930),word(0x501)));log:flush()
end
memory.registerwrite(0x6000,4,function(a,s,v) banks[a-0x6000+1]=v%64 end)
local function observe(a,s,v)
 if frame>=350 then
  local pc=memory.getregister('pc');local slot=math.floor((pc-0x8000)/8192)+1
  writes:write(string.format('%d\t%d\t%04X\t%02X\t%04X\t%d\n',frame,branch,a,v,pc,banks[slot] or -1))
 end
end
memory.registerwrite(0x501,0x5f,observe);memory.registerwrite(0x6930,12,observe)
memory.registerexec(0xd208,function() if banks[3]==45 then snapshot('level-check-entry') end end)
memory.registerexec(0xd29e,function() if banks[3]==45 then snapshot('growth-entry') end end)
emu.speedmode('nothrottle')
local laststate=-1;local lastlevel=-1;local lastxp=-1;local walking=0;local saved=nil;local savedxp=nil;local savedhp=nil;local wait=0
for t=1,120001 do
 frame=t;local keys={};local state=memory.readbyte(0);local level=memory.readbyte(0x504);local xp=word(0x508);local hp=word(0x514)
 if t<=5400 then
  if t==180 or t==360 then keys.start=true end
  if t==500 or t==610 then keys.A=true end
  if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
  if t==690 or t==720 then keys.B=true end
  if t>=780 and t<1700 then keys.down=true end
  if t>=1750 and t<2100 then keys.left=true end
  if t>=2150 and t<2500 then keys.down=true end
  if t>=2640 and t%60==0 then keys.A=true end
 else
  if state==0x88 then
   if t%60==1 then keys.B=true end -- Close a menu opened by a final confirmation press.
   if saved==nil or xp>savedxp then
    saved=savestate.create();savestate.save(saved);savedxp=xp;savedhp=hp;attempt=0;walking=0;snapshot('world-checkpoint')
   end
   if wait>0 then wait=wait-1
   else
    walking=walking+1;local phase=math.floor(walking/180)%2
    if phase==0 then keys.left=true else keys.right=true end
   end
  elseif state==8 then
   if t%20==0 then keys.A=true end
  elseif hp==0 and saved~=nil then
   attempt=attempt+1;branch=branch+1;savestate.load(saved);wait=attempt*7;walking=0
   assert(word(0x508)==savedxp and word(0x514)==savedhp,'State restoration must be verified')
   snapshot('restored-after-defeat')
  end
  if hp==0 and saved~=nil and t%60==0 then
   attempt=attempt+1;branch=branch+1;savestate.load(saved);wait=attempt*7;walking=0
   assert(word(0x508)==savedxp and word(0x514)==savedhp,'State restoration must be verified')
   snapshot('restored-after-zero-hp')
  end
 end
 joypad.set(1,keys);emu.frameadvance()
 state=memory.readbyte(0);level=memory.readbyte(0x504);xp=word(0x508)
 if t>350 and (state~=laststate or level~=lastlevel or xp~=lastxp) then snapshot('change') end
 if t>5400 and t%1500==0 then snapshot('periodic') end
 laststate=state;lastlevel=level;lastxp=xp
 if level>=2 and state==0x88 then snapshot('second-level-complete');break end
end
writes:close();log:close();dump('complete.txt','Finished bounded normal-input experiment. Branch IDs record verified in-memory save-state restores.');emu.pause()
