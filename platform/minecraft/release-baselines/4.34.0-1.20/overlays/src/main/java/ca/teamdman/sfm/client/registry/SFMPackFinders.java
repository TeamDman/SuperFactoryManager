package ca.teamdman.sfm.client.registry;

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
