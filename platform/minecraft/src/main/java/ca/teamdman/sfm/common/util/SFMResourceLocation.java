package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.SFM;
{% if targets.mc_26_1_2 %}
import net.minecraft.IdentifierException;
{% else %}
import net.minecraft.ResourceLocationException;
{% endif %}
import net.minecraft.core.Registry;
{% if targets.mc_26_1_2 %}
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
{% else %}
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
{% endif %}
import org.jetbrains.annotations.Nullable;

public class SFMResourceLocation {
{% if targets.mc_26_1_2 %}
    public static Identifier fromNamespaceAndPath(String namespace, String path) {
{% else %}
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
{% endif %}
{% if targets.mc_26_1_2 %}
        return Identifier.fromNamespaceAndPath(namespace, path);
{% elsif targets.mc_1_21_0 %}
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
{% elsif targets.mc_1_21_1 %}
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
{% else %}
        return new ResourceLocation(namespace, path);
{% endif %}
    }
{% if targets.mc_26_1_2 %}
    public static Identifier fromSFMPath(String path) {
{% else %}
    public static ResourceLocation fromSFMPath(String path) {
{% endif %}
        return fromNamespaceAndPath(SFM.MOD_ID, path);
    }
{% if targets.mc_26_1_2 %}
    public static Identifier fromMinecraftPath(String path) {
{% else %}
    public static ResourceLocation fromMinecraftPath(String path) {
{% endif %}
        return fromNamespaceAndPath("minecraft", path);
    }
{% if targets.mc_26_1_2 %}
    public static Identifier parse(String expanded) {
{% else %}
    public static ResourceLocation parse(String expanded) {
{% endif %}
{% if targets.mc_26_1_2 %}
        return Identifier.parse(expanded);
{% elsif targets.mc_1_21_0 %}
        return ResourceLocation.parse(expanded);
{% elsif targets.mc_1_21_1 %}
        return ResourceLocation.parse(expanded);
{% else %}
        return new ResourceLocation(expanded);
{% endif %}
    }
{% if targets.mc_26_1_2 %}
    public static @Nullable Identifier tryParse(String expanded) {
{% else %}
    public static @Nullable ResourceLocation tryParse(String expanded) {
{% endif %}
        try {
            return parse(expanded);
{% if targets.mc_26_1_2 %}
        } catch (IdentifierException rle) {
{% else %}
        } catch (ResourceLocationException rle) {
{% endif %}
            return null;
        }
    }
    public static <T> ResourceKey<Registry<T>> createSFMRegistryKey(String path) {
        return ResourceKey.createRegistryKey(SFMResourceLocation.fromSFMPath(path));
    }
}
