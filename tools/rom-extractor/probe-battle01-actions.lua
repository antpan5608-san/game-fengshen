-- Bounded action trace from the established normal-input first battle.
local root=os.getenv('FENGSHEN_ROOT') or 'F:/apps/fengshen-remake'
local out=root..'/private-derived/battle01-actions-2/'
local frame=0;local banks={63,63,63,63}
local log=assert(io.open(out..'actions.tsv','w'))
memory.registerwrite(0x6000,4,function(a,s,v)banks[a-0x6000+1]=v%64 end)
for _,address in ipairs({0xaa82,0xacec,0xadd7,0xae12,0x8df1,0x8e85,0x8e7a,0x8ea9,0xb0e6})do
 memory.registerexec(address,function(pc)
  local bank=banks[math.floor((pc-0x8000)/8192)+1]
  if frame>=1900 and frame<=6000 and bank==(pc<0xa000 and 36 or 37) then
   log:write(string.format('%d\t%04X\t0367=%d\t0368=%d\t03C8=%d\tHP=%d\tphase=%d\t43=%d\n',
    frame,pc,memory.readbyte(0x0367),memory.readbyte(0x0368),memory.readbyte(0x03c8),
    memory.readbyte(0x0514)+256*memory.readbyte(0x0515),memory.readbyte(0x0110),memory.readbyte(0x43)))
  end
 end)
end
emu.speedmode('nothrottle')
for t=1,6001 do
 frame=t;local keys={}
 if t==180 or t==360 then keys.start=true end
 if t==500 or t==610 then keys.A=true end
 if t==520 or t==535 or t==550 or t==565 or t==580 then keys.down=true end
 if t==690 or t==720 then keys.B=true end
 if t>=780 and t<1700 then keys.down=true end
 if t>=1750 and t<2100 then keys.left=true end
 if t>=2150 and t<2500 then keys.down=true end
 if t>=2640 and t%60==0 then keys.A=true end
 joypad.set(1,keys);emu.frameadvance()
end
log:close()
local done=assert(io.open(out..'complete.txt','w'));done:write('normal controller input only');done:close()
emu.pause()
