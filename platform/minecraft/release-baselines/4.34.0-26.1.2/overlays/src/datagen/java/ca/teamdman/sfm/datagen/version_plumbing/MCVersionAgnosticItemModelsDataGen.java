package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.data.PackOutput;

public abstract class MCVersionAgnosticItemModelsDataGen extends ModelProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticItemModelsDataGen(
            PackOutput output,
            String modId
    ) {
        super(output, modId);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        populate(itemModels);
    }

    protected abstract void populate(ItemModelGenerators itemModels);

    @MCVersionDependentBehaviour
    @Override
    public String getName() {
        return modId + " Item Models";
    }
}
