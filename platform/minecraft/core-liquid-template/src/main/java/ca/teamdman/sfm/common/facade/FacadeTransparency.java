package ca.teamdman.sfm.common.facade;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum FacadeTransparency implements StringRepresentable {
    OPAQUE, TRANSLUCENT;
    public static final EnumProperty<FacadeTransparency> FACADE_TRANSPARENCY_PROPERTY = EnumProperty.create("facade_transparency", FacadeTransparency.class);

    FacadeTransparency() {
    }

    @Override
    public String getSerializedName() {
        return switch (this) {
            case OPAQUE -> "opaque";
            case TRANSLUCENT -> "translucent";
        };
    }
}