package de.TeutonStudio.TeutonLib.peripheraltest;

import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import de.TeutonStudio.TeutonLib.integration.computer.*;
import de.TeutonStudio.TeutonLib.integration.peripheral.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.Level;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;

final class ChannelsVerification {
    private static final BlockPos A=new BlockPos(24,200,8), B=new BlockPos(10024,200,8), N=new BlockPos(24,200,8);
    private static ServerComputer reader, writer, otherDimension;
    private static ServerLevel world;
    private static net.minecraft.world.level.block.Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }
    private static void require(boolean c,String message) { if(!c) throw new AssertionError(message); }
    private static ServerComputer make(ServerLevel level,BlockPos pos,String family,String program) throws Exception {
        level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        level.removeBlock(pos,false); level.setBlockAndUpdate(pos,block("computercraft:computer_"+family).defaultBlockState());
        var host=(ComputerBlockEntity)level.getBlockEntity(pos); var computer=host.createServerComputer();
        var mount=computer.createRootMount();
        if(mount.exists("channel-result")) mount.delete("channel-result");
        try(var file=mount.openFile("startup.lua",Set.of(StandardOpenOption.WRITE,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING))) {
            file.write(StandardCharsets.UTF_8.encode(program));
        }
        computer.turnOn(); return computer;
    }
    private static String guarded(String body) {
        return "local function result(s) local f=fs.open('channel-result','w'); f.write(s); f.close() end\n"
            +"local ok,err=pcall(function()\nlocal c=require('teutonlib.channels')\n"+body+"\nend)\nif not ok then result('FAIL '..tostring(err)) end\n";
    }
    private static String result(ServerComputer computer) throws Exception {
        var mount=computer.createRootMount(); if(!mount.exists("channel-result")) return "WAITING";
        try(var file=mount.openForRead("channel-result")) { var buffer=ByteBuffer.allocate(4096); file.read(buffer); buffer.flip(); return StandardCharsets.UTF_8.decode(buffer).toString(); }
    }
    private static void menuSafety(ServerLevel level, ServerComputer first, ServerComputer second) throws Exception {
        var one=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ChannelViewerA"));
        var two=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ChannelViewerB"));
        one.setPos(A.getX()+.5,A.getY(),A.getZ()+.5); two.setPos(B.getX()+.5,B.getY(),B.getZ()+.5);
        one.containerMenu=new dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory(dan200.computercraft.shared.ModRegistry.Menus.COMPUTER.get(),7,one.getInventory(),player -> player.distanceToSqr(A.getCenter())<64,first);
        two.containerMenu=new dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory(dan200.computercraft.shared.ModRegistry.Menus.COMPUTER.get(),7,two.getInventory(),player -> player.distanceToSqr(B.getCenter())<64,second);
        var handle=ComputerPayloads.class.getDeclaredMethod("handle",ComputerPayloads.Request.class,net.minecraft.server.level.ServerPlayer.class); handle.setAccessible(true);
        var session=UUID.randomUUID();
        handle.invoke(null,new ComputerPayloads.Request(7,session,"set","local_signal","",15,false),one);
        require(((ChannelHost)level.getBlockEntity(A)).teutonlib$channels().get("local_signal").value()==15,"valid menu write rejected");
        handle.invoke(null,new ComputerPayloads.Request(7,session,"public","secret_signal","",0,true),two);
        require(((ChannelHost)level.getBlockEntity(B)).teutonlib$channels().get("secret_signal").published(),"second player menu write rejected");
        handle.invoke(null,new ComputerPayloads.Request(7,session,"public","secret_signal","",0,false),one);
        require(((ChannelHost)level.getBlockEntity(B)).teutonlib$channels().get("secret_signal").published(),"request escaped its player's open computer menu");
        handle.invoke(null,new ComputerPayloads.Request(8,session,"set","local_signal","",1,false),one);
        require(((ChannelHost)level.getBlockEntity(A)).teutonlib$channels().get("local_signal").value()==15,"stale menu ID accepted");
        two.setPos(B.getX(),B.getY()+100,B.getZ());
        handle.invoke(null,new ComputerPayloads.Request(7,session,"set","secret_signal","",1,false),two);
        require(((ChannelHost)level.getBlockEntity(B)).teutonlib$channels().get("secret_signal").value()==7,"out of reach menu write accepted");
        ComputerIntegration.runtime(first).setValue("local_signal",9); ComputerIntegration.runtime(second).setPublic("secret_signal",false);
        one.containerMenu.removed(one); two.containerMenu.removed(two);
        de.TeutonStudio.TeutonLib.TeutonLib.LOGGER.info("Verified independent player menus, stale menu IDs, cross-computer isolation and reach permissions");
    }
    static String run(ServerLevel level,String action) throws Exception {
        if(action.equals("normal")||action.equals("advanced")) {
            world=level;
            level.setChunkForced(A.getX()>>4,0,true); level.setChunkForced(B.getX()>>4,0,true);
            for(var pos:List.of(A,B)) for(var d:Direction.values()) level.removeBlock(pos.relative(d),false);
            level.setBlockAndUpdate(B.east(),block("computercraft:wired_modem_full").defaultBlockState());
            level.setBlockAndUpdate(A.east(),block("computercraft:wired_modem_full").defaultBlockState());
            writer=make(level,B,action,guarded("""
                if #c.list()==0 then
                  c.create('public_signal','output'); c.set('public_signal',12); c.setPublic('public_signal',true)
                  c.create('secret_signal','output'); c.set('secret_signal',7)
                end
                result('WRITER_READY')
                while true do os.pullEvent('hold') end
                """));
            reader=make(level,A,action,guarded("""
                c.create('incoming','input','top')
                c.create('local_signal','output'); c.set('local_signal',9)
                assert(not pcall(c.set,'local_signal',1.5), 'fractional signals rejected')
                local all=c.list(); assert(#all==2 and all[1].name=='incoming')
                local id=%d
                local visible,hidden,last
                for i=1,100 do
                  local ok,a,b=pcall(c.list,id); last=tostring(ok)..":"..tostring(a)..":"..tostring(b)
                  if ok and #a==1 and b==1 then visible,hidden=a,b; break end
                  sleep(0.05)
                end
                assert(visible and #visible==1 and hidden==1, 'public/private listing '..tostring(last))
                assert(visible[1].name=='public_signal' and visible[1].value==12)
                assert(c.read(id,'secret_signal')==7, 'known private read')
                assert(not pcall(c.read,id,'missing'), 'unknown read rejected')
                local lines={}; local previous=print
                print=function(...) local words={...}; for i,v in ipairs(words) do words[i]=tostring(v) end; lines[#lines+1]=table.concat(words,' ') end
                local command=assert(loadfile('rom/programs/channels.lua',nil,setmetatable({print=print},{__index=_ENV})))
                local ran=pcall(command,tostring(id)); print=previous
                assert(ran and lines[#lines]=='1 private channels not shown', 'CLI private footer')
                for _,line in ipairs(lines) do assert(not line:find('secret_signal',1,true), 'private name leaked') end
                result('READY')
                while true do
                  local _,phase=os.pullEvent('channel_phase'); sleep(0.25)
                  if phase=='target_modem' or phase=='local_modem' or phase=='offline' then
                    assert(not pcall(c.list,id),'unavailable remote must fail')
                  elseif phase=='reconnected' or phase=='link_modem' then
                    assert(c.read(id,'secret_signal')==7,'reconnected remote read')
                  end
                  result('PASS '..phase)
                end
                """.formatted(writer.getID())));
            var nether=level.getServer().getLevel(Level.NETHER);
            otherDimension=make(nether,N,action,guarded("sleep(.5); assert(peripheral.find('modem')); assert(not pcall(c.list,"+writer.getID()+")); result('DIMENSION_PASS'); while true do os.pullEvent('hold') end"));
            nether.setBlockAndUpdate(N.east(),block("computercraft:wired_modem_full").defaultBlockState());
            return "STARTED";
        }
        if(action.equals("result")) {
            String output=result(reader);
            if(output.startsWith("FAIL")) output+=" writer="+((ChannelHost)level.getBlockEntity(B)).teutonlib$channels().entries()+" reader="+((ChannelHost)level.getBlockEntity(A)).teutonlib$channels().entries()+" writerResult="+result(writer);
            return output;
        }
        if(action.equals("dimension")) return result(otherDimension);
        if(action.equals("target_modem")) { level.removeBlock(B.east(),false); ((ComputerBlockEntity)level.getBlockEntity(B)).updateInputsImmediately(); }
        if(action.equals("local_modem")) { level.removeBlock(A.east(),false); ((ComputerBlockEntity)level.getBlockEntity(A)).updateInputsImmediately(); }
        if(action.equals("reconnected")) {
            level.setBlockAndUpdate(B.east(),block("computercraft:wired_modem_full").defaultBlockState());
            level.setBlockAndUpdate(A.east(),block("computercraft:wired_modem_full").defaultBlockState());
            ((ComputerBlockEntity)level.getBlockEntity(A)).updateInputsImmediately(); ((ComputerBlockEntity)level.getBlockEntity(B)).updateInputsImmediately();
        }
        if(action.equals("link_modem")) {
            var host=level.getBlockEntity(A); UUID id=UUID.randomUUID(); ((LinkIdentity)host).teutonlib$linkTarget(id);
            var pos=A.south(4); level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos,block("computercraft:wireless_modem_normal").defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.UP));
            level.setBlockAndUpdate(pos.above(),PeripheralLinks.BLOCK.get().defaultBlockState());
            ((PeripheralLinkBlockEntity)level.getBlockEntity(pos.above())).target(id); LinkRegistry.validate();
            level.removeBlock(A.east(),false);
        }
        if(action.equals("offline")) {
            writer.shutdown();
            require(((ChannelHost)level.getBlockEntity(B)).teutonlib$channels().get("public_signal").value()==12,"shutdown lost output");
        }
        if(action.equals("inspect")) {
            var runtime=ComputerIntegration.runtime(reader); require(runtime!=null,"missing runtime");
            require(runtime.sourceViews().stream().anyMatch(s -> s.types().contains("modem")),"modem source missing");
            CompoundTag data=ComputerPayloads.snapshot(runtime,""); require(data.getList("channels",Tag.TAG_COMPOUND).size()==2,"channel snapshot");
            var host=level.getBlockEntity(B); var saved=host.saveWithFullMetadata(level.registryAccess());
            var bank=ChannelPersistence.load(saved.getList("TeutonLibChannels",Tag.TAG_COMPOUND));
            require(bank.get("public_signal").value()==12 && bank.get("public_signal").published() && !bank.get("secret_signal").published(),"saved output/privacy");
            if(DbwAccess.available()) {
                var manager=Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager");
                manager.getMethod("createConnection",Level.class,BlockPos.class,BlockPos.class,Direction.class,String.class).invoke(null,level,B,A,Direction.UP,"public_signal");
                require(DbwAccess.read(level.getBlockEntity(A),"top")==12,"DbW input/output");
                DbwAccess.rename(host,"public_signal","renamed_signal",12);
                require(DbwAccess.connections(host,"renamed_signal")==1 && DbwAccess.connections(host,"public_signal")==0,"DbW rename migrates cables");
                DbwAccess.rename(host,"renamed_signal","public_signal",12);
                require(DbwAccess.connections(host,"public_signal")==1,"DbW reverse rename");
                var values=(Map<?,?>)manager.getMethod("getSourceSignals",BlockPos.class).invoke(manager.getMethod("get",Level.class).invoke(null,level),B);
                require(values.get("public_signal").equals(12),"shutdown reset DbW source signal");
            }
            return "PASS inspect";
        }
        if(action.equals("unload")) {
            level.setChunkForced(B.getX()>>4,0,false); return "STARTED unload";
        }
        if(action.equals("unloaded")) {
            if(level.getChunkSource().getChunkNow(B.getX()>>4,0)!=null) return "WAITING";
            if(DbwAccess.available()) require(DbwAccess.read(level.getBlockEntity(A),"top")==12,"unload reset DbW output");
            return "PASS unloaded";
        }
        if(action.equals("reload")) {
            level.setChunkForced(B.getX()>>4,0,true); level.getChunkAt(B); return "STARTED reload";
        }
        if(action.equals("restored")) {
            var host=level.getBlockEntity(B); require(((ChannelHost)host).teutonlib$channels().get("public_signal").value()==12,"reload lost output");
            if(DbwAccess.available() && DbwAccess.read(level.getBlockEntity(A),"top")!=12) return "WAITING";
            return "PASS restored";
        }
        if(action.equals("restart")) {
            level.setChunkForced(B.getX()>>4,0,true); var host=level.getBlockEntity(B);
            require(host!=null,"restart host absent"); var bank=((ChannelHost)host).teutonlib$channels();
            require(bank.get("public_signal").value()==12 && bank.get("public_signal").published() && !bank.get("secret_signal").published(),"restart bank lost state");
            if(DbwAccess.available() && DbwAccess.read(level.getBlockEntity(A),"top")!=12) return "WAITING";
            var first=((ComputerBlockEntity)level.getBlockEntity(A)).getServerComputer();
            var second=((ComputerBlockEntity)host).createServerComputer(); second.turnOn();
            if(first==null || ComputerIntegration.runtime(first)==null || ComputerIntegration.runtime(second)==null) return "WAITING";
            menuSafety(level,first,second);
            return "PASS restart";
        }
        reader.queueEvent("channel_phase",new Object[]{action}); return "REQUESTED";
    }
}
