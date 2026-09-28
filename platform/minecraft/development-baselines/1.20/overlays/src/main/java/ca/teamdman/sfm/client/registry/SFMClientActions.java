package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public static synchronized SFMClientActionCommandTree commandTree() {
        if (commandTree == null) {
            List<Map.Entry<ResourceLocation, SFMClientAction<?>>> registrations = new ArrayList<>();
            for (ResourceLocation id : registry().keys()) {
                SFMClientAction<?> action = Objects.requireNonNull(registry().get(id));
                registrations.add(Map.entry(id, action));
            }
            commandTree = SFMClientActionDispatcherCompiler.compileCommandTree(registrations);
        }
        return commandTree;
    }
}
