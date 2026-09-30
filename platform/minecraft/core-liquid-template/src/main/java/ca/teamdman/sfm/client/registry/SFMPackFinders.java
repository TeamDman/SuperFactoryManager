package ca.teamdman.sfm.client.registry;

{% case minecraft_version %}
{% when '1.19.2' %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Files;
import java.nio.file.Path;
{% when '1.19.4', '1.20', '1.20.1' %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Files;
import java.nio.file.Path;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Files;
import java.nio.file.Path;
{% when '1.21', '1.21.1' %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
{% when '26.1.2' %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
{% endcase %}

/**
 * Registers SFM's optional built-in resource packs, so they appear in the Resource Packs screen.
 */
public class SFMPackFinders {
{% case minecraft_version %}
{% when '1.19.2' %}

    private static final String CLASSIC_PACK_PATH = "pack/classic"; // root contains pack.mcmeta & optional pack.png
    private static final String CLASSIC_PACK_ID = SFM.MOD_ID + ":classic"; // must be unique in repository
    private static final String CLASSIC_PACK_DISPLAY_NAME = "SFM Classic"; // shown in logs; UI uses pack.mcmeta description

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        var modFileInfo = ModList.get().getModFileById(SFM.MOD_ID);
        if (modFileInfo == null) return; // should not happen

        Path classicRoot = modFileInfo.getFile().findResource(CLASSIC_PACK_PATH);
        // Require a valid pack.mcmeta to register
        if (!Files.exists(classicRoot.resolve("pack.mcmeta"))) return;

        event.addRepositorySource((consumer, factory) -> {
            @SuppressWarnings("resource")
            PathPackResources packResources = new PathPackResources(CLASSIC_PACK_DISPLAY_NAME, classicRoot);
            @MCVersionDependentBehaviour Pack pack = Pack.create(
                    CLASSIC_PACK_ID,
                    false, // not required; user can enable/disable
                    () -> packResources,
                    factory,
                    Pack.Position.TOP, // prefer above mod_resources so it overrides
                    PackSource.BUILT_IN
            );
            consumer.accept(pack);
        });
    }
}
{% when '1.19.4', '1.20', '1.20.1' %}

    private static final String CLASSIC_PACK_PATH = "pack/classic"; // root contains pack.mcmeta & optional pack.png
    private static final String CLASSIC_PACK_ID = SFM.MOD_ID + ":classic"; // must be unique in repository
    private static final String CLASSIC_PACK_DISPLAY_NAME = "SFM Classic"; // shown in logs; UI uses pack.mcmeta description

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        var modFileInfo = ModList.get().getModFileById(SFM.MOD_ID);
        if (modFileInfo == null) return; // should not happen

        Path classicRoot = modFileInfo.getFile().findResource(CLASSIC_PACK_PATH);
        // Require a valid pack.mcmeta to register
        if (!Files.exists(classicRoot.resolve("pack.mcmeta"))) return;

        event.addRepositorySource((consumer) -> {
            @SuppressWarnings("resource")
            PathPackResources packResources = new PathPackResources(CLASSIC_PACK_DISPLAY_NAME, true, classicRoot);
            @MCVersionDependentBehaviour Pack pack = Pack.readMetaAndCreate(
                    CLASSIC_PACK_ID,
                    Component.literal(CLASSIC_PACK_DISPLAY_NAME), // TODO: add to LocalizationKeys
                    false, // not required; user can enable/disable
                    (packId) -> packResources,
        PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP, // prefer above mod_resources so it overrides
                    PackSource.BUILT_IN
            );
            consumer.accept(pack);
        });
    }
}
{% when '1.20.2', '1.20.3', '1.20.4' %}

    private static final String CLASSIC_PACK_PATH = "pack/classic"; // root contains pack.mcmeta & optional pack.png
    private static final String CLASSIC_PACK_ID = SFM.MOD_ID + ":classic"; // must be unique in repository
    private static final String CLASSIC_PACK_DISPLAY_NAME = "SFM Classic"; // shown in logs; UI uses pack.mcmeta description

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        var modFileInfo = ModList.get().getModFileById(SFM.MOD_ID);
        if (modFileInfo == null) return; // should not happen

        Path classicRoot = modFileInfo.getFile().findResource(CLASSIC_PACK_PATH);
        // Require a valid pack.mcmeta to register
        if (!Files.exists(classicRoot.resolve("pack.mcmeta"))) return;

        event.addRepositorySource((consumer) -> {
            @SuppressWarnings("resource")
            PathPackResources packResources = new PathPackResources(CLASSIC_PACK_DISPLAY_NAME, classicRoot, true);
            @MCVersionDependentBehaviour Pack pack = Pack.readMetaAndCreate(
                    CLASSIC_PACK_ID,
                    Component.literal(CLASSIC_PACK_DISPLAY_NAME), // TODO: add to LocalizationKeys
                    false, // not required; user can enable/disable
                    new Pack.ResourcesSupplier() {
                        @Override
                        public PackResources openPrimary(String p_294636_) {
                            return packResources;
                        }

                        @Override
                        public PackResources openFull(
                                String p_251717_,
                                Pack.Info p_294956_
                        ) {
                            return packResources;
                        }
                    },
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP, // prefer above mod_resources so it overrides
                    PackSource.BUILT_IN
            );
            consumer.accept(pack);
        });
    }
}
{% when '1.21', '1.21.1' %}

    private static final String CLASSIC_PACK_PATH = "pack/classic"; // root contains pack.mcmeta & optional pack.png
    private static final String CLASSIC_PACK_ID = SFM.MOD_ID + ":classic"; // must be unique in repository
    private static final String CLASSIC_PACK_DISPLAY_NAME = "SFM Classic"; // shown in logs; UI uses pack.mcmeta description

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        var modFileInfo = ModList.get().getModFileById(SFM.MOD_ID);
        if (modFileInfo == null) return; // should not happen

        Path classicRoot = modFileInfo.getFile().findResource(CLASSIC_PACK_PATH);
        // Require a valid pack.mcmeta to register
        if (!Files.exists(classicRoot.resolve("pack.mcmeta"))) return;

        event.addRepositorySource((consumer) -> {
            PackLocationInfo packLocationInfo = new PackLocationInfo(
                    CLASSIC_PACK_ID, Component.literal(CLASSIC_PACK_DISPLAY_NAME), PackSource.BUILT_IN,
                    Optional.empty()
            );
            @SuppressWarnings("resource")
            PathPackResources packResources = new PathPackResources(packLocationInfo, classicRoot);
            Pack pack = Pack.readMetaAndCreate(
                    packLocationInfo,
                    new Pack.ResourcesSupplier() {
                        @Override
                        public PackResources openPrimary(PackLocationInfo pLocation) {
                            return packResources;
                        }

                        @Override
                        public PackResources openFull(
                                PackLocationInfo pLocation,
                                Pack.Metadata pMetadata
                        ) {
                            return packResources;
                        }
                    },
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(false, Pack.Position.TOP, false)
            );
            consumer.accept(pack);
        });
    }
}
{% when '26.1.2' %}

    private static final String CLASSIC_PACK_PATH = "pack/classic"; // root contains pack.mcmeta & optional pack.png
    private static final String CLASSIC_PACK_DISPLAY_NAME = "SFM Classic"; // shown in logs; UI uses pack.mcmeta description

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onRegisterPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        event.addPackFinders(
                SFMResourceLocation.fromSFMPath(CLASSIC_PACK_PATH),
                PackType.CLIENT_RESOURCES,
                Component.literal(CLASSIC_PACK_DISPLAY_NAME),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP
        );
    }
}
{% endcase %}
