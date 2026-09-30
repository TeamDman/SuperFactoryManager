package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class ScalarResourceType<STACK, CAP> extends ResourceType<STACK, Class<STACK>, CAP> {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public final ResourceLocation registryKey;
{% when '26.1.2' %}
    public final Identifier registryKey;
{% endcase %}
    public final Class<STACK> item;

    public ScalarResourceType(
            SFMBlockCapabilityKind<CAP> capability,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation registryKey,
{% when '26.1.2' %}
            Identifier registryKey,
{% endcase %}
            Class<STACK> item
    ) {
        super(capability);
        this.registryKey = registryKey;
        this.item = item;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ResourceLocation getRegistryKeyForStack(STACK stack) {
{% when '26.1.2' %}
    public Identifier getRegistryKeyForStack(STACK stack) {
{% endcase %}
        return registryKey;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ResourceLocation getRegistryKeyForItem(Class<STACK> item) {
{% when '26.1.2' %}
    public Identifier getRegistryKeyForItem(Class<STACK> item) {
{% endcase %}
        return registryKey;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public @Nullable Class<STACK> getItemFromRegistryKey(ResourceLocation location) {
        if (location.equals(registryKey)) {
{% when '26.1.2' %}
    public @Nullable Class<STACK> getItemFromRegistryKey(Identifier identifier) {
        if (identifier.equals(registryKey)) {
{% endcase %}
            return item;
        }
        return null;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public Set<ResourceLocation> getRegistryKeys() {
{% when '26.1.2' %}
    public Set<Identifier> getRegistryKeys() {
{% endcase %}
        return Set.of(registryKey);
    }

    @Override
    public Iterable<Class<STACK>> getItems() {
        return List.of(item);
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean registryKeyExists(ResourceLocation location) {
        return location.equals(registryKey);
{% when '26.1.2' %}
    public boolean registryKeyExists(Identifier identifier) {
        return identifier.equals(registryKey);
{% endcase %}
    }

    @Override
    public Class<STACK> getItem(STACK stack) {
        return item;
    }

    @Override
    public boolean matchesStackType(Object o) {
        return item.isInstance(o);
    }
}
