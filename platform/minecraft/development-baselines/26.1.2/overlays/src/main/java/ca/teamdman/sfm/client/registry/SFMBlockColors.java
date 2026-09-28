package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.render.FacadeBlockTintSource;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;

public class SFMBlockColors {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void registerBlockColor(RegisterColorHandlersEvent.BlockTintSources event) {
        FacadeBlockTintSource blockColor = new FacadeBlockTintSource();
        event.register(List.of(blockColor), SFMBlocks.CABLE_FACADE.get(), SFMBlocks.FANCY_CABLE_FACADE.get());
    }
}
