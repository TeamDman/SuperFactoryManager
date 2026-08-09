package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.render.CableFacadeBlockModelWrapper;
import ca.teamdman.sfm.client.render.FancyCableFacadeBlockModelWrapper;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;

import java.util.Map;
import java.util.function.Function;

public class SFMBlockModelWrappers {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onModelBakeEvent(@MCVersionDependentBehaviour ModelEvent.BakingCompleted event) {

        record FacadeModelRelationship(
                SFMRegistryObject<Block, ?> facadeBlock,

                Function<BakedModel, BakedModelWrapper<BakedModel>> modelWrapperConstructor
        ) {
        }

        // Define the known relationships
        var relationships = new FacadeModelRelationship[]{
                new FacadeModelRelationship(
                        SFMBlocks.CABLE_FACADE,
                        CableFacadeBlockModelWrapper::new
                ),
                new FacadeModelRelationship(
                        SFMBlocks.FANCY_CABLE_FACADE,
                        FancyCableFacadeBlockModelWrapper::new
                ),
                new FacadeModelRelationship(
                        SFMBlocks.TUNNELLED_CABLE_FACADE,
                        CableFacadeBlockModelWrapper::new
                ),
                new FacadeModelRelationship(
                        SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE,
                        FancyCableFacadeBlockModelWrapper::new
                ),
                new FacadeModelRelationship(
                        SFMBlocks.TOUGH_CABLE_FACADE,
                        CableFacadeBlockModelWrapper::new
                ),
                new FacadeModelRelationship(
                        SFMBlocks.TOUGH_FANCY_CABLE_FACADE,
                        FancyCableFacadeBlockModelWrapper::new
                ),
                };

        // Apply the model redirection for each relationship
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (var relationship : relationships) {
            // Get the possible states for the facaded block
            ImmutableList<BlockState> possibleStates = relationship
                    .facadeBlock()
                    .get()
                    .getStateDefinition()
                    .getPossibleStates();

            // Apply the model redirection for each state
            for (BlockState state : possibleStates) {
                // Get the default model location for the state
                ModelResourceLocation stateModelLocation = BlockModelShaper.stateToModelLocation(state);

                models.computeIfPresent(
                        stateModelLocation,
                        (_location, model) -> relationship.modelWrapperConstructor().apply(model)
                );
            }
        }
    }

}
