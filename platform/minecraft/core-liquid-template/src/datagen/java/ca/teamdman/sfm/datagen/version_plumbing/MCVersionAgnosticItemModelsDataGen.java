package ca.teamdman.sfm.datagen.version_plumbing;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.data.event.GatherDataEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% when '26.1.2' %}
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.data.PackOutput;
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public abstract class MCVersionAgnosticItemModelsDataGen extends ItemModelProvider {
{% when '26.1.2' %}
public abstract class MCVersionAgnosticItemModelsDataGen extends ModelProvider {
{% endcase %}
    @MCVersionDependentBehaviour
    public MCVersionAgnosticItemModelsDataGen(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            GatherDataEvent event,
{% when '26.1.2' %}
            PackOutput output,
{% endcase %}
            String modId
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
        super(event.getGenerator(), modId, event.getExistingFileHelper());
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        super(event.getGenerator().getPackOutput(), modId, event.getExistingFileHelper());
{% when '26.1.2' %}
        super(output, modId);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        populate(itemModels);
    }

    protected abstract void populate(ItemModelGenerators itemModels);

    @MCVersionDependentBehaviour
    @Override
    public String getName() {
        return modId + " Item Models";
{% endcase %}
    }
}
