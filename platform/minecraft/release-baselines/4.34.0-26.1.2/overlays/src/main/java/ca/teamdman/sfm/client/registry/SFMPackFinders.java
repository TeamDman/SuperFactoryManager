package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers SFM's optional built-in resource packs, so they appear in the Resource Packs screen.
 */
public class SFMPackFinders {

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
