package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticLanguageDataGen extends LanguageProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticLanguageDataGen(
            GatherDataEvent event,
            String modId,
            String locale
    ) {
        super(event.getGenerator().getPackOutput(), modId, locale);
    }
}
