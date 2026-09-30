package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public abstract class RegistryBackedResourceType<STACK,ITEM,CAP> extends ResourceType<STACK,ITEM,CAP> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final Map<ITEM, ResourceLocation> registryKeyCache = new Object2ObjectOpenHashMap<>();
{% when '26.1.2' %}
    private final Map<ITEM, Identifier> registryKeyCache = new Object2ObjectOpenHashMap<>();
{% endcase %}
    public RegistryBackedResourceType(SFMBlockCapabilityKind<CAP> CAPABILITY_KIND) {
        super(CAPABILITY_KIND);
    }


    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ResourceLocation getRegistryKeyForStack(STACK stack) {
{% when '26.1.2' %}
    public Identifier getRegistryKeyForStack(STACK stack) {
{% endcase %}
        ITEM item = getItem(stack);
        return getRegistryKeyForItem(item);
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ResourceLocation getRegistryKeyForItem(ITEM item) {
{% when '26.1.2' %}
    public Identifier getRegistryKeyForItem(ITEM item) {
{% endcase %}
        var found = registryKeyCache.get(item);
        if (found != null) return found;
        found = getRegistry().getId(item);
        if (found == null) {
            throw new NullPointerException("Registry key not found for item: " + item);
        }
        registryKeyCache.put(item, found);
        return found;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public Set<ResourceLocation> getRegistryKeys() {
{% when '26.1.2' %}
    public Set<Identifier> getRegistryKeys() {
{% endcase %}
        return getRegistry().keys();
    }

    @Override
    public Iterable<ITEM> getItems() {
        return getRegistry().values();
    }

    public abstract SFMRegistryWrapper<ITEM> getRegistry();

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public @Nullable ITEM getItemFromRegistryKey(ResourceLocation location) {
        return getRegistry().get(location);
{% when '26.1.2' %}
    public @Nullable ITEM getItemFromRegistryKey(Identifier identifier) {
        return getRegistry().get(identifier).get().value();
{% endcase %}
    }

    @Override
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean registryKeyExists(ResourceLocation location) {
        return getRegistry().contains(location);
{% when '26.1.2' %}
    public boolean registryKeyExists(Identifier identifier) {
        return getRegistry().contains(identifier);
{% endcase %}
    }

}
