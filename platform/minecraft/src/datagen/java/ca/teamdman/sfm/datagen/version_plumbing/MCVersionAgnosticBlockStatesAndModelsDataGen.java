package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticBlockStatesAndModelsDataGen extends ModelProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockStatesAndModelsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput(), modId);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        populate(blockModels);
    }

    protected abstract void populate(BlockModelGenerators blockModels);
}
