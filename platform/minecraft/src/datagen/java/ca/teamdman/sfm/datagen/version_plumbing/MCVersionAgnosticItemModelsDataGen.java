package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticItemModelsDataGen extends ItemModelProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticItemModelsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator().getPackOutput(), modId, event.getExistingFileHelper());
    }
}
