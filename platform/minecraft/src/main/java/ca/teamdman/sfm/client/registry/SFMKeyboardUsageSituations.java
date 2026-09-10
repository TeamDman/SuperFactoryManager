package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageSituation;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageSituationCatalog;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contributor registry and stable built-in ids for keyboard usage situations. */
public final class SFMKeyboardUsageSituations {
    public static final ResourceLocation GLOBAL = new ResourceLocation(SFM.MOD_ID, "global");
    public static final ResourceLocation WORKSPACE = new ResourceLocation(SFM.MOD_ID, "workspace");
    public static final ResourceLocation DEFAULT = new ResourceLocation(SFM.MOD_ID, "default");
    public static final ResourceLocation TEXT_EDITOR = new ResourceLocation(SFM.MOD_ID, "text_editor");
    public static final ResourceLocation TEMPORAL_DOCUMENT = new ResourceLocation(SFM.MOD_ID, "temporal_document");
    public static final ResourceLocation COMMAND_PALETTE = new ResourceLocation(SFM.MOD_ID, "command_palette");
    public static final ResourceLocation TERMINAL = new ResourceLocation(SFM.MOD_ID, "terminal");
    public static final ResourceLocation EXPLORER = new ResourceLocation(SFM.MOD_ID, "explorer");
    public static final ResourceLocation EXPLORER_SEARCH = new ResourceLocation(SFM.MOD_ID, "explorer_search");
    public static final ResourceLocation EXPLORER_FIND = new ResourceLocation(SFM.MOD_ID, "explorer_find");
    public static final ResourceLocation EXPLORER_FILTER = new ResourceLocation(SFM.MOD_ID, "explorer_filter");

    private SFMKeyboardUsageSituations() {
    }

    public static SFMDeferredRegister<SFMKeyboardUsageSituation> createContributor(String namespace) {
        return new SFMDeferredRegisterBuilder<SFMKeyboardUsageSituation>()
                .namespace(namespace)
                .registry(RegistryHolder.REGISTRY_ID)
                .onlyIf(SFMEnvironmentUtils::isClient)
                .build();
    }

    public static void register(IEventBus bus) {
        RegistryHolder.REGISTRY_CREATOR.register(bus);
    }

    public static SFMRegistryWrapper<SFMKeyboardUsageSituation> registry() {
        return RegistryHolder.REGISTRY_CREATOR.registry();
    }

    public static SFMKeyboardUsageSituationCatalog catalog() {
        Map<ResourceLocation, SFMKeyboardUsageSituation> values = new LinkedHashMap<>(builtIns());
        try {
            for (ResourceLocation id : registry().keys()) {
                SFMKeyboardUsageSituation situation = registry().get(id);
                if (situation != null) values.put(id, situation);
            }
        } catch (RuntimeException ignored) {
            // Pure unit tests and very early client initialization use the
            // exact built-in catalog before Forge has frozen the registry.
        }
        return new SFMKeyboardUsageSituationCatalog(values);
    }

    public static Map<ResourceLocation, SFMKeyboardUsageSituation> builtIns() {
        Map<ResourceLocation, SFMKeyboardUsageSituation> values = new LinkedHashMap<>();
        values.put(GLOBAL, new SFMKeyboardUsageSituation(
                Component.literal("Global"),
                Component.literal("Active across Minecraft and other mods"),
                List.of()));
        values.put(WORKSPACE, new SFMKeyboardUsageSituation(
                Component.literal("SFM workspace"),
                Component.literal("Active while an SFM panel workspace is open"),
                List.of(GLOBAL)));
        values.put(DEFAULT, new SFMKeyboardUsageSituation(
                Component.literal("SFM default control"),
                Component.literal("Active for ordinary controls in an SFM workspace"),
                List.of(WORKSPACE)));
        values.put(TEXT_EDITOR, new SFMKeyboardUsageSituation(
                Component.literal("SFM text editor"),
                Component.literal("Active while an SFM text editor owns keyboard input"),
                List.of(DEFAULT)));
        values.put(TEMPORAL_DOCUMENT, new SFMKeyboardUsageSituation(
                Component.literal("SFM temporal document"),
                Component.literal("Active while an undo-tree-backed SFM document owns keyboard input"),
                List.of(TEXT_EDITOR)));
        values.put(COMMAND_PALETTE, new SFMKeyboardUsageSituation(
                Component.literal("SFM command palette"),
                Component.literal("Active while the SFM command palette owns keyboard input"),
                List.of(TEMPORAL_DOCUMENT)));
        values.put(TERMINAL, new SFMKeyboardUsageSituation(
                Component.literal("SFM terminal"),
                Component.literal("Active while the Rust terminal viewport owns keyboard input"),
                List.of(DEFAULT)));
        values.put(EXPLORER, new SFMKeyboardUsageSituation(Component.literal("Explorer"),
                Component.literal("An Explorer panel owns keyboard input"), List.of(DEFAULT)));
        values.put(EXPLORER_SEARCH, new SFMKeyboardUsageSituation(Component.literal("Explorer search input"),
                Component.literal("Find or Filter owns keyboard input"), List.of(EXPLORER)));
        values.put(EXPLORER_FIND, new SFMKeyboardUsageSituation(Component.literal("Explorer Find"),
                Component.literal("Non-hiding Find input owns keyboard input"), List.of(EXPLORER_SEARCH)));
        values.put(EXPLORER_FILTER, new SFMKeyboardUsageSituation(Component.literal("Explorer Filter"),
                Component.literal("Filter input owns keyboard input"), List.of(EXPLORER_SEARCH)));
        return Map.copyOf(values);
    }

    /** Keeps Forge registry bootstrap out of pure model/unit-test use. */
    private static final class RegistryHolder {
        private static final ResourceKey<Registry<SFMKeyboardUsageSituation>> REGISTRY_ID =
                SFMResourceLocation.createSFMRegistryKey("keyboard_usage_situation");
        private static final SFMDeferredRegister<SFMKeyboardUsageSituation> REGISTRY_CREATOR =
                new SFMDeferredRegisterBuilder<SFMKeyboardUsageSituation>()
                        .namespace(SFM.MOD_ID)
                        .registry(REGISTRY_ID)
                        .onlyIf(SFMEnvironmentUtils::isClient)
                        .createNewRegistry()
                        .build();
    }
}
