package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.render.FacadeBlockColor;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SFMBlockColors {
    @SubscribeEvent
    public static void registerBlockColor(RegisterColorHandlersEvent.Block event) {
        FacadeBlockColor blockColor = new FacadeBlockColor();
        event.register(blockColor, SFMBlocks.CABLE_FACADE_BLOCK.get());
        event.register(blockColor, SFMBlocks.FANCY_CABLE_FACADE_BLOCK.get());
    }
}
