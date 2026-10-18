package de.TeutonStudio.TeutonLib.mixin.peripheral.console;

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry;
import de.teutonstudio.ccaeroworks.computer.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(value = PeripheralNetworkBuilder.class, remap = false)
public abstract class ConsoleGraphMixin {
    @Inject(method = "build", at = @At("RETURN"), cancellable = true)
    private void teutonlib$links(ConsoleComputer owner, CallbackInfoReturnable<PeripheralNetworkGraph> cir) {
        LinkRegistry.validate();
        PeripheralNetworkGraph graph = cir.getReturnValue();
        List<PeripheralNetworkNode> nodes = new ArrayList<>(graph.getPeripherals());
        for (LinkRegistry.Connection connection : LinkRegistry.connections(owner.getHost())) {
            DeskNetworkNode desk = graph.getDesks().stream().filter(candidate -> candidate.getDesk() == connection.anchor()).findFirst().orElse(null);
            if (desk == null) continue;
            var types = new LinkedHashSet<String>(); types.add(connection.peripheral().getType()); types.addAll(connection.peripheral().getAdditionalTypes());
            nodes.add(new PeripheralNetworkNode(desk, connection.link().devicePosition(),
                    connection.link().getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING).getOpposite(),
                    connection.name(), connection.peripheral(), connection.peripheral().getType(), types,
                    PeripheralTypeNames.INSTANCE.aliases(types)));
        }
        cir.setReturnValue(graph.copy(graph.getNetworkId(), graph.getRevision(), graph.getState(), graph.getDesks(), nodes, graph.getDimension()));
    }
}
