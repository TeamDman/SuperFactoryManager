package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.render.FacadeBlockColor;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDist;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

public class SFMBlockColors {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void registerBlockColor(RegisterColorHandlersEvent.Block event) {
        FacadeBlockColor blockColor = new FacadeBlockColor();
        event.register(blockColor, SFMBlocks.CABLE_FACADE.get());
        event.register(blockColor, SFMBlocks.FANCY_CABLE_FACADE.get());
    }
}
