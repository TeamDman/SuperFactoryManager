package ca.teamdman.sfm.common.facade;

import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.util.IStringSerializable;
import org.jetbrains.annotations.Nullable;

public enum FacadeTransparency implements IStringSerializable {
    OPAQUE, TRANSLUCENT;

    public static final PropertyEnum<FacadeTransparency> FACADE_TRANSPARENCY_PROPERTY = PropertyEnum.create("facade_transparency", FacadeTransparency.class);


    FacadeTransparency() {
    }

    public String getSerializedName() {
        return name();
    }

    public static @Nullable FacadeTextureMode byName(@Nullable String pName) {
        if (pName == null) {
            return null;
        }
        return FacadeTextureMode.valueOf(pName);
    }

    @Override
    public String getName() {
        return name();
    }
}