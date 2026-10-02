-- CONTROLLED ORIGINAL STATE: isolated boundary/RNG observations, NOT normal Android evidence.
local root=assert(os.getenv('FENGSHEN_ROOT'))
local case=os.getenv('BATTLE_HERB_CASE') or 'injured'
local out=root..'/private-derived/mobile-play01/'..case..'/'
local f=0
local function word(a)return memory.readbyte(a)+256*memory.readbyte(a+1)end
local function setword(a,v)memory.writebyte(a,v%256);memory.writebyte(a+1,math.floor(v/256))end
local function dump(n,b)local x=assert(io.open(out..n,'wb'));x:write(b);x:close()end
local function snap(n)dump(n..'-ram.bin',memory.readbyterange(0,0x800));dump(n..'-sram.bin',memory.readbyterange(0x6800,0x800));gui.savescreenshotas(out..n..'.png')end
local log=assert(io.open(out..'menu-trace.tsv','w'))
local function tick(k)joypad.set(1,k or{});emu.frameadvance();f=f+1 end
local function press(key,wait)local k={};k[key]=true;tick(k);for _=1,(wait or 120)do tick({})end end
emu.speedmode('nothrottle');for _=1,100 do tick()end;savestate.load(savestate.create(8));snap('controlled-loaded')
setword(0x514,1000);setword(0x51c,1000);memory.writebyte(0x544,0);memory.writebyte(0x570,0);memory.writebyte(0x5b0,case=='no-inventory' and 0 or 3)
press('up',20);press('A',200);snap('controlled-dialogue-1');press('A',300);snap('controlled-dialogue-2');press('A',600);snap('controlled-battle')
for t=1,5000 do
 if memory.readbyte(0)==8 and memory.readbyte(0x110)==4 and memory.readbyte(0xde)==1 then break end
 if t%120==0 then press('A',60)else tick()end
end
snap('controlled-command');log:write('command '..f..' state='..memory.readbyte(0)..' phase='..memory.readbyte(0x110)..' de='..memory.readbyte(0xde)..' e0='..memory.readbyte(0xe0)..'\n')
press('down',60);press('down',60);press('A',200);snap('controlled-medicine-list');press('A',180);snap('controlled-medicine-list-actual')
if case=='player-first' then memory.writebyte(0x534,30)end
local hp=case=='near-full' and 95 or case=='full' and 100 or case=='dead' and 0 or case=='death-before-action' and 5 or 30
setword(0x514,hp);setword(0x51c,100);memory.writebyte(0x544,case=='dead' and 32 or 0)
press('A',180);snap('controlled-medicine-target')
local trace=assert(io.open(out..'use-trace.tsv','w'))
for _,a in ipairs({0x43,0x514,0x515,0x5b0})do
 memory.registerwrite(a,1,function(addr,n,v)
  if addr~=0x43 then trace:write(string.format('%d\tWRITE\t%04X\t%d\tPC=%04X HP=%d count=%d\n',f,addr,v,memory.getregister('pc'),word(0x514),memory.readbyte(0x5b0)%128));trace:flush()end
 end)
end
for _,pc in ipairs({0x855e,0x8a49,0x8e05,0x8e42,0x8e5f,0x8e6c,0x8e73,0x8eb7,0x9133,0x99c2,0x9b33,0xa6c2,0xac78,0xac89,0xac95})do
 memory.registerexec(pc,function()
  if memory.readbyte(pc)==0xa5 and memory.readbyte(pc+1)==0x43 then
   trace:write(string.format('%d\tRNG\tPC=%04X value=%d HP=%d count=%d\n',f,pc,memory.readbyte(0x43),word(0x514),memory.readbyte(0x5b0)%128));trace:flush()
  end
 end)
end
press(case=='cancel' and 'B' or 'A',700);snap('controlled-use-result');log:write('result HP='..word(0x514)..' qty='..(memory.readbyte(0x5b0)%128)..'\n');log:close();trace:close();dump('done.txt','controlled battle herb probe done');emu.pause()
