package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.model.data.ModelData;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.client.model.data.ModelData;
{% when '26.1.2' %}
import net.neoforged.neoforge.model.data.ModelData;
{% endcase %}

import static ca.teamdman.sfm.common.blockentity.FancyCableFacadeBlockEntity.FACADE_DIRECTION;

public class ToughFancyCableFacadeBlockEntity extends CommonFacadeBlockEntity {
    public ToughFancyCableFacadeBlockEntity(
            BlockPos pos,
            BlockState state
    ) {

        super(SFMBlockEntities.TOUGH_FANCY_CABLE_FACADE.get(), pos, state);
    }

    @Override
    public ModelData getModelData() {

        if (getFacadeData() != null) {

            return ModelData.builder()
                    .with(IFacadeBlockEntity.FACADE_BLOCK_STATE_MODEL_PROPERTY, getFacadeData().facadeBlockState())
                    .with(FACADE_DIRECTION, getFacadeData().facadeDirection())
                    .build();
        }
        return ModelData.EMPTY;
    }

}
