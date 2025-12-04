package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class SFMResourceLocation {
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
    public static ResourceLocation fromSFMPath(String path) {
        return fromNamespaceAndPath(SFM.MOD_ID, path);
    }
    public static ResourceLocation fromMinecraftPath(String path) {
        return fromNamespaceAndPath("minecraft", path);
    }
    public static ResourceLocation parse(String expanded) {
        return new ResourceLocation(expanded);
    }
    public static @Nullable ResourceLocation tryParse(String expanded) {
        try {
            return parse(expanded);
        } catch (NullPointerException rle) {
            return null;
        }
    }
    public static ResourceLocation createSFMRegistryKey(String path) {
        return SFMResourceLocation.fromSFMPath(path);
    }
}
