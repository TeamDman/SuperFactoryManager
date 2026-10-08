package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraftforge.data.event.GatherDataEvent;
{% when '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.core.HolderLookup;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.data.event.GatherDataEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% when '26.1.2' %}
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import java.util.concurrent.CompletableFuture;
{% endcase %}

public abstract class MCVersionAgnosticBlockTagsDataGen extends BlockTagsProvider {
    @MCVersionDependentBehaviour
    public MCVersionAgnosticBlockTagsDataGen(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            GatherDataEvent event,
{% when '26.1.2' %}
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
{% endcase %}
            String modId
    ) {
        super(
{% case minecraft_version %}
{% when '1.19.2' %}
                event.getGenerator(),
                modId,
                event.getExistingFileHelper()
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                modId,
                event.getExistingFileHelper()
{% when '26.1.2' %}
                output,
                lookupProvider,
                modId
{% endcase %}
        );
    }

    protected abstract void addBlockTags();

    @MCVersionDependentBehaviour
    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    protected void addTags() {
        this.addBlockTags();
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public String getName() {
        return modId + " Block Tags";
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    @MCVersionDependentBehaviour
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    public String getName() {
        return modId + " Block Tags";
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    protected void addTags(HolderLookup.Provider pProvider) {
        this.addBlockTags();
{% endcase %}
    }
}
