package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
{% if features.client_program_actions %}
import ca.teamdman.sfm.client.action.SFMClientActionDescriptor;
import ca.teamdman.sfm.client.action.SFMClientProgramActionDispatcher;
{% endif %}
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% else %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
{% if features.client_program_actions %}
import java.util.Optional;
{% endif %}

public final class SFMClientActions {
    public static final ResourceKey<Registry<SFMClientAction<?>>> REGISTRY_ID =
            SFMResourceLocation.createSFMRegistryKey("client_action");

    private static final SFMDeferredRegister<SFMClientAction<?>> REGISTRY_CREATOR =
            new SFMDeferredRegisterBuilder<SFMClientAction<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .onlyIf(SFMEnvironmentUtils::isClient)
                    .createNewRegistry()
                    .build();

    private static @Nullable SFMClientActionCommandTree commandTree;

    private SFMClientActions() {
    }

    public static SFMDeferredRegister<SFMClientAction<?>> createContributor(String namespace) {
        return new SFMDeferredRegisterBuilder<SFMClientAction<?>>()
                .namespace(namespace)
                .registry(REGISTRY_ID)
                .onlyIf(SFMEnvironmentUtils::isClient)
                .build();
    }

    public static void register(IEventBus bus) {
        REGISTRY_CREATOR.register(bus);
    }

    public static SFMRegistryWrapper<SFMClientAction<?>> registry() {
        return REGISTRY_CREATOR.registry();
    }

{% if features.client_program_actions %}
    /** Lookup for program adapters; registration alone does not grant machine access. */
    public static Optional<SFMClientActionDescriptor> programmaticDescriptor(ResourceLocation actionId) {
        SFMClientAction<?> action = registry().get(Objects.requireNonNull(actionId));
        return action == null ? Optional.empty()
                : action.programmaticDescriptor().filter(descriptor -> descriptor.actionId().equals(actionId));
    }

    public static Optional<SFMClientProgramActionDispatcher.Binding> programmaticBinding(ResourceLocation actionId) {
        SFMClientAction<?> action = registry().get(Objects.requireNonNull(actionId));
        if (action == null) return Optional.empty();
        return action.programmaticDescriptor().filter(descriptor -> descriptor.actionId().equals(actionId))
                .flatMap(descriptor -> action.programmaticHandler()
                        .map(handler -> new SFMClientProgramActionDispatcher.Binding(descriptor, handler)));
    }

{% endif %}
    public static synchronized SFMClientActionCommandTree commandTree() {
        if (commandTree == null) {
{% case minecraft_version %}
{% when "26.1.2" %}
            List<Map.Entry<Identifier, SFMClientAction<?>>> registrations = new ArrayList<>();
{% else %}
            List<Map.Entry<ResourceLocation, SFMClientAction<?>>> registrations = new ArrayList<>();
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
            for (Identifier id : registry().keys()) {
{% else %}
            for (ResourceLocation id : registry().keys()) {
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
                SFMClientAction<?> action = registry().get(id)
                        .map(reference -> reference.value())
                        .orElseThrow(() -> new IllegalStateException("Missing registered client action " + id));
{% else %}
                SFMClientAction<?> action = Objects.requireNonNull(registry().get(id));
{% endcase %}
                registrations.add(Map.entry(id, action));
            }
            commandTree = SFMClientActionDispatcherCompiler.compileCommandTree(registrations);
        }
        return commandTree;
    }
}
