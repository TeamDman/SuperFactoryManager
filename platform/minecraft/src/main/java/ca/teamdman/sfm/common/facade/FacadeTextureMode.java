package ca.teamdman.sfm.common.facade;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

public enum FacadeTextureMode implements StringRepresentable {
    STRETCH,
    FILL;

    @SuppressWarnings("deprecation")
    public static final EnumCodec<FacadeTextureMode> CODEC = StringRepresentable.fromEnum(FacadeTextureMode::values);

    @Override
    public String getSerializedName() {
        return name();
    }

    public static @Nullable FacadeTextureMode byName(@Nullable String pName) {
        return CODEC.byName(pName);
    }

}
