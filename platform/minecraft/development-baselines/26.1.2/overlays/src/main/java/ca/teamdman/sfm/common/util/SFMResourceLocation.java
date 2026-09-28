package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
import net.minecraft.IdentifierException;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

public class SFMResourceLocation {
    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    public static Identifier fromSFMPath(String path) {
        return fromNamespaceAndPath(SFM.MOD_ID, path);
    }
    public static Identifier fromMinecraftPath(String path) {
        return fromNamespaceAndPath("minecraft", path);
    }
    public static Identifier parse(String expanded) {
        return Identifier.parse(expanded);
    }
    public static @Nullable Identifier tryParse(String expanded) {
        try {
            return parse(expanded);
        } catch (IdentifierException rle) {
            return null;
        }
    }
    public static <T> ResourceKey<Registry<T>> createSFMRegistryKey(String path) {
        return ResourceKey.createRegistryKey(SFMResourceLocation.fromSFMPath(path));
    }
}
