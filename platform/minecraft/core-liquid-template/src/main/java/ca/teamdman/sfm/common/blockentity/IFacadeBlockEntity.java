package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.facade.FacadeData;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.model.data.ModelProperty;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.model.data.ModelProperty;
{% when '26.1.2' %}
import net.neoforged.neoforge.model.data.ModelProperty;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public interface IFacadeBlockEntity {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    ModelProperty<BlockState> FACADE_BLOCK_STATE_MODEL_PROPERTY = new ModelProperty<>();
{% when '26.1.2' %}
    net.neoforged.neoforge.model.data.ModelProperty<BlockState> FACADE_BLOCK_STATE_MODEL_PROPERTY = new ModelProperty<>();
{% endcase %}

    void updateFacadeData(FacadeData newFacadeData);

    @Nullable FacadeData getFacadeData();

}
