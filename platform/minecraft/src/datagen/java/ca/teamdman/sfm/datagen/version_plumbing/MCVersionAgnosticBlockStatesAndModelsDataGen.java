package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticBlockStatesAndModelsDataGen extends BlockStateProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockStatesAndModelsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator(), modId, event.getExistingFileHelper());
    }
}
