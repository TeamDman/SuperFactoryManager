package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticBlockTagsDataGen extends BlockTagsProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockTagsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                modId,
                event.getExistingFileHelper()
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
