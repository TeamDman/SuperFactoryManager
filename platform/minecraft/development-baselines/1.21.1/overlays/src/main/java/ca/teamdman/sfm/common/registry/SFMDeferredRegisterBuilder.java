package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public class SFMDeferredRegisterBuilder<T> {
    public @Nullable ResourceKey<? extends Registry<T>> registryKey = null;
    public boolean createNewRegistry;
    public BooleanSupplier onlyIf = () -> true;
    public @Nullable String namespace;

    public SFMDeferredRegisterBuilder() {
    }

    /// The ID for the registry.
    public SFMDeferredRegisterBuilder<T> registry(ResourceKey<? extends Registry<T>> registryKey) {
        this.registryKey = registryKey;
        return this;
    }

    /// The namespace (mod id)
    public SFMDeferredRegisterBuilder<T> namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    /// For when a new custom registry should be created.
    public SFMDeferredRegisterBuilder<T> createNewRegistry() {
        this.createNewRegistry = true;
        return this;
    }

    /// For when the registry only exists under certain conditions.
    /// The {@link SFMDeferredRegister} and {@link SFMRegistryObject} will still be created,
    /// but they will never finish manifesting into a registry.
    public SFMDeferredRegisterBuilder<T> onlyIf(BooleanSupplier condition) {
        this.onlyIf = condition;
        return this;
    }

    @MCVersionDependentBehaviour
    public SFMDeferredRegister<T> build() {
        if (registryKey == null) {
            throw new IllegalStateException("Registry key must be set!");
        }
        if (namespace == null) {
            throw new IllegalStateException("Namespace must be set!");
        }
        boolean enabled = this.onlyIf.getAsBoolean();

        if (SFMEnvironmentUtils.isInIDE()) {
            SFM.LOGGER.debug("Creating registry {}, enabled={}", registryKey, enabled);
        }
        @Nullable DeferredRegister<T> inner;
        if (enabled) {
            inner = DeferredRegister.create(registryKey, SFM.MOD_ID);
            if (createNewRegistry) {
                inner.makeRegistry((builder) -> {});
            }
        } else {
            inner = null;
        }

        return new SFMDeferredRegister<>(registryKey, namespace, inner);
    }
}
