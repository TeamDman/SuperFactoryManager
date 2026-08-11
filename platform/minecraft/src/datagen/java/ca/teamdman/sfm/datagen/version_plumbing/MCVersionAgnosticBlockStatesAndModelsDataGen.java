package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticBlockStatesAndModelsDataGen extends BlockStateProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockStatesAndModelsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput(), modId, event.getExistingFileHelper());
    }
}
