package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public abstract class MCVersionAgnosticLanguageDataGen extends LanguageProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticLanguageDataGen(
            PackOutput output,
            String modId,
            String locale
    ) {
        super(output, modId, locale);
    }
}
