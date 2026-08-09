package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticItemModelsDataGen extends ItemModelProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticItemModelsDataGen(
            GatherDataEvent event,
            String modId
    ) {
        super(event.getGenerator(), modId, event.getExistingFileHelper());
    }
}
