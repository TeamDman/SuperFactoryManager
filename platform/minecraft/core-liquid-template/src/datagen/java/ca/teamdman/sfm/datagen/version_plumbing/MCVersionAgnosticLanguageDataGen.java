package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% when '26.1.2' %}
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
{% endcase %}

public abstract class MCVersionAgnosticLanguageDataGen extends LanguageProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticLanguageDataGen(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            GatherDataEvent event,
{% when '26.1.2' %}
            PackOutput output,
{% endcase %}
            String modId,
            String locale
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
        super(event.getGenerator(), modId, locale);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(event.getGenerator().getPackOutput(), modId, locale);
{% when '26.1.2' %}
        super(output, modId, locale);
{% endcase %}
    }
}
