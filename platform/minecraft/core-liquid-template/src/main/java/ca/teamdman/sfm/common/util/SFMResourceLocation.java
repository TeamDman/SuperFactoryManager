package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.IdentifierException;
{% else %}
import net.minecraft.ResourceLocationException;
{% endcase %}
import net.minecraft.core.Registry;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
{% else %}
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class SFMResourceLocation {
{% case minecraft_version %}
{% when "26.1.2" %}
    public static Identifier fromNamespaceAndPath(String namespace, String path) {
{% else %}
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
        return Identifier.fromNamespaceAndPath(namespace, path);
{% when "1.21", "1.21.0", "1.21.1" %}
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
{% else %}
        return new ResourceLocation(namespace, path);
{% endcase %}
    }
{% case minecraft_version %}
{% when "26.1.2" %}
    public static Identifier fromSFMPath(String path) {
{% else %}
    public static ResourceLocation fromSFMPath(String path) {
{% endcase %}
        return fromNamespaceAndPath(SFM.MOD_ID, path);
    }
{% case minecraft_version %}
{% when "26.1.2" %}
    public static Identifier fromMinecraftPath(String path) {
{% else %}
    public static ResourceLocation fromMinecraftPath(String path) {
{% endcase %}
        return fromNamespaceAndPath("minecraft", path);
    }
{% case minecraft_version %}
{% when "26.1.2" %}
    public static Identifier parse(String expanded) {
{% else %}
    public static ResourceLocation parse(String expanded) {
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
        return Identifier.parse(expanded);
{% when "1.21", "1.21.0", "1.21.1" %}
        return ResourceLocation.parse(expanded);
{% else %}
        return new ResourceLocation(expanded);
{% endcase %}
    }
{% case minecraft_version %}
{% when "26.1.2" %}
    public static @Nullable Identifier tryParse(String expanded) {
{% else %}
    public static @Nullable ResourceLocation tryParse(String expanded) {
{% endcase %}
        try {
            return parse(expanded);
{% case minecraft_version %}
{% when "26.1.2" %}
        } catch (IdentifierException rle) {
{% else %}
        } catch (ResourceLocationException rle) {
{% endcase %}
            return null;
        }
    }
    public static <T> ResourceKey<Registry<T>> createSFMRegistryKey(String path) {
        return ResourceKey.createRegistryKey(SFMResourceLocation.fromSFMPath(path));
    }
}
