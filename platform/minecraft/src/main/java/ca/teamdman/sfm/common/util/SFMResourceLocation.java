package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
        } catch (ResourceLocationException rle) {
            return null;
        }
    }
    public static <T> ResourceKey<Registry<T>> createSFMRegistryKey(String path) {
        return ResourceKey.createRegistryKey(SFMResourceLocation.fromSFMPath(path));
    }
}
