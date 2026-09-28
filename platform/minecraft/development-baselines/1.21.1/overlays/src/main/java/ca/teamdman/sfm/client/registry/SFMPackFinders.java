package ca.teamdman.sfm.client.registry;

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

/**
 * Registers SFM's optional built-in resource packs, so they appear in the Resource Packs screen.
 */
public class SFMPackFinders {

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
