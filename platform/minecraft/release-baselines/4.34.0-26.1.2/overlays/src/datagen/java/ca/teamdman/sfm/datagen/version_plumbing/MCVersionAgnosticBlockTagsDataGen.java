package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public abstract class MCVersionAgnosticBlockTagsDataGen extends BlockTagsProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockTagsDataGen(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            String modId
    ) {
        super(
                output,
                lookupProvider,
                modId
        );
    }

    protected abstract void addBlockTags();

    @MCVersionDependentBehaviour
    @Override
    public String getName() {
        return modId + " Block Tags";
    }

    @MCVersionDependentBehaviour
    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.addBlockTags();
    }
}
