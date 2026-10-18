package de.TeutonStudio.TeutonLib.mixin.computer.console;

import de.teutonstudio.ccaeroworks.computer.*;
import de.teutonstudio.ccaeroworks.computer.source.*;
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral;
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Compatibility projection only. Existing display/GPS/script owners remain in Aeroworks. */
@Mixin(value=InformationSourceSnapshotBuilder.class,remap=false)
public abstract class ConsoleSourcesMixin {
    @Inject(method="build",at=@At("RETURN"),cancellable=true)
    private void teutonlib$sources(ConsoleComputer owner,CallbackInfoReturnable<InformationSourceSnapshot> cir) {
        var before=cir.getReturnValue(); var sources=new ArrayList<>(before.getSources());
        var kind=new InformationSourceKind("teutonlib:peripherals","PERIPHERALS",25);
        for(var node:PeripheralNetworkBuilder.INSTANCE.build(owner).getPeripherals()) {
            sources.removeIf(v -> v.getId().equals("storage:"+node.getAddress()));
            var link=LinkRegistry.connection(node.getAddress());
            String details=(link==null?"local":"wireless · link "+link.link().getBlockPos().toShortString())+" · "+String.join(", ",node.getTypes());
            var pos=node.getPos();
            sources.add(new InformationSourceView("peripheral:"+node.getAddress(),kind,node.getAddress(),"connected",
                pos.getX(),pos.getY(),pos.getZ(),node.getSide().getName(),details.substring(0,Math.min(details.length(),160))));
            if(node.getTarget() instanceof WiredModemPeripheral modem && modem.getNode().getElement() instanceof WiredModemElement element)
                element.getRemotePeripherals().forEach((name,p) -> {
                    var target = p.getTarget() instanceof net.minecraft.world.level.block.entity.BlockEntity entity ? entity.getBlockPos() : null;
                    var displayPos = target == null ? pos : target;
                    String d="wired · "+p.getType()+(target == null ? " · via modem; device position unavailable" : "");
                    sources.add(new InformationSourceView("peripheral:"+name,kind,name,"connected",displayPos.getX(),displayPos.getY(),displayPos.getZ(),"",d.substring(0,Math.min(d.length(),160))));
                });
        }
        var unique=new LinkedHashMap<String,InformationSourceView>(); sources.forEach(v -> unique.putIfAbsent(v.getId(),v));
        cir.setReturnValue(new InformationSourceSnapshot(List.copyOf(unique.values()),before.getDisplayScripts()));
    }
}
