package ca.teamdman.sfm.common.facade;

import org.jetbrains.annotations.Nullable;

public enum FacadeTextureMode {
    STRETCH,
    FILL;


    public String getSerializedName() {
        return name();
    }

    public static @Nullable FacadeTextureMode byName(@Nullable String pName) {
        if (pName == null) {
            return null;
        }
        return FacadeTextureMode.valueOf(pName);
    }

}
