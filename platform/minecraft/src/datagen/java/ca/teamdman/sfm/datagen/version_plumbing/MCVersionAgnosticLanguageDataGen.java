package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public abstract class MCVersionAgnosticLanguageDataGen extends LanguageProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticLanguageDataGen(
            GatherDataEvent event,
            String modId,
            String locale
    ) {
        super(event.getGenerator(), modId, locale);
    }
}
