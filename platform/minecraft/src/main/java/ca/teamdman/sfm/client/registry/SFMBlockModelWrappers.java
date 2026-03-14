package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.render.CableFacadeBlockModelWrapper;
import ca.teamdman.sfm.client.render.FancyCableFacadeBlockModelWrapper;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SFMBlockModelWrappers {
    @SubscribeEvent
    public static void onModelBakeEvent(@MCVersionDependentBehaviour ModelEvent.BakingCompleted event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (BlockState possibleState : SFMBlocks.CABLE_FACADE_BLOCK.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(
                    BlockModelShaper.stateToModelLocation(possibleState),
                    (location, model) -> new CableFacadeBlockModelWrapper(model)
            );
        }
        for (BlockState possibleState : SFMBlocks.FANCY_CABLE_FACADE_BLOCK
                .get()
                .getStateDefinition()
                .getPossibleStates()
        ) {
            models.computeIfPresent(
                    BlockModelShaper.stateToModelLocation(possibleState),
                    (location, model) -> new FancyCableFacadeBlockModelWrapper(model)
            );
        }
    }
}
