package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticBlockTagsDataGen extends BlockTagsProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockTagsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(
                event.getGenerator(),
                modId,
                event.getExistingFileHelper()
        );
    }

    protected abstract void addBlockTags();

    @MCVersionDependentBehaviour
    @Override
    protected void addTags() {
        this.addBlockTags();
    }

    @Override
    public String getName() {
        return modId + " Block Tags";
    }
}
