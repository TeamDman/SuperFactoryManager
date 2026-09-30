package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.render.CableFacadeBlockModelWrapper;
import ca.teamdman.sfm.client.render.FancyCableFacadeBlockModelWrapper;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import com.google.common.collect.ImmutableList;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
{% when '1.21', '1.21.1' %}
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
{% when '26.1.2' %}
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
{% endcase %}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
{% when '26.1.2' %}
import net.neoforged.neoforge.client.event.ModelEvent;
{% endcase %}

import java.util.Map;
import java.util.function.Function;

public class SFMBlockModelWrappers {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when '1.19.2' %}
    public static void onModelBakeEvent(@MCVersionDependentBehaviour ModelEvent.BakingCompleted event) {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static void onModelBakeEvent(@MCVersionDependentBehaviour ModelEvent.ModifyBakingResult event) {
{% endcase %}

        record FacadeModelRelationship(
                SFMRegistryObject<Block, ?> facadeBlock,

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                Function<BakedModel, BakedModelWrapper<BakedModel>> modelWrapperConstructor
{% when '26.1.2' %}
                Function<BlockStateModel, BlockStateModel> modelWrapperConstructor
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                };
{% when '26.1.2' %}
        };
{% endcase %}

        // Apply the model redirection for each relationship
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        Map<ResourceLocation, BakedModel> models = event.getModels();
{% when '1.21', '1.21.1' %}
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
{% when '26.1.2' %}
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
{% endcase %}
        for (var relationship : relationships) {
            // Get the possible states for the facaded block
            ImmutableList<BlockState> possibleStates = relationship
                    .facadeBlock()
                    .get()
                    .getStateDefinition()
                    .getPossibleStates();

            // Apply the model redirection for each state
            for (BlockState state : possibleStates) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // Get the default model location for the state
                ModelResourceLocation stateModelLocation = BlockModelShaper.stateToModelLocation(state);

{% when '26.1.2' %}
{% endcase %}
                models.computeIfPresent(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                        stateModelLocation,
                        (_location, model) -> relationship.modelWrapperConstructor().apply(model)
{% when '26.1.2' %}
                        state,
                        (_state, model) -> relationship.modelWrapperConstructor().apply(model)
{% endcase %}
                );
            }
        }
    }

}
