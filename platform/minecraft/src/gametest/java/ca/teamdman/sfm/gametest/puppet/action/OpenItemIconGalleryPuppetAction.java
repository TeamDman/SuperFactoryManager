package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerEntry;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerWorkspace;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFilePresentation;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFilePresentationRegistry;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMFileIconGalleryFixtureSource;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class OpenItemIconGalleryPuppetAction implements SFMPuppetAction {
    private boolean requested;
    private int ticks;

    @Override
    public String description() {
        return "open ItemStack file icon gallery";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!requested) {
            requested = true;
            Minecraft minecraft = Minecraft.getInstance();
            SFMItemIcon unavailableIcon = new SFMItemIcon(
                    new ResourceLocation("missing_theme", "unavailable_item"),
                    SFMItemIcon.PAPER,
                    "unavailable themed item"
            );
            SFMFilePresentationRegistry presentations = SFMFilePresentationRegistry.createDefault().register(
                    ".missing-item",
                    new SFMFilePresentation(
                            unavailableIcon,
                            "unavailable themed item",
                            0xFFFF7777,
                            SFMFilePresentation.Emphasis.ITALIC
                    )
            );
            assertResolved(
                    presentations,
                    SFMFileExplorerEntry.directory("workspace", "workspace", java.util.List.of()),
                    "minecraft:chest"
            );
            assertResolved(presentations, SFMFileExplorerEntry.file("Factory.sfml", "Factory.sfml"), "sfm:disk");
            assertResolved(presentations, SFMFileExplorerEntry.file("ReviewWorkspace.java", "ReviewWorkspace.java"), "minecraft:book");
            assertResolved(presentations, SFMFileExplorerEntry.file("sources.tar.gz", "sources.tar.gz"), "minecraft:ender_chest");
            assertResolved(presentations, SFMFileExplorerEntry.file("sources.gz", "sources.gz"), "minecraft:barrel");
            assertResolved(presentations, SFMFileExplorerEntry.file("README", "README"), "minecraft:name_tag");
            if (!SFMItemIconResolver.resolve(unavailableIcon).usedFallback()) {
                throw new IllegalStateException("Unavailable themed item did not use its vanilla fallback");
            }
            minecraft.setScreen(SFMFileExplorerWorkspace.create(
                    minecraft.screen,
                    new SFMFileIconGalleryFixtureSource(),
                    presentations
            ));
        }
        if (runtime.isFileExplorerOpen()) return true;
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out opening ItemStack icon gallery");
        }
        return false;
    }

    private static void assertResolved(
            SFMFilePresentationRegistry presentations,
            SFMFileExplorerEntry entry,
            String expectedItemId
    ) {
        var resolved = SFMItemIconResolver.resolve(presentations.presentationFor(entry).itemIcon());
        var presentation = presentations.presentationFor(entry);
        ResourceLocation actualId = SFMWellKnownRegistries.ITEMS.getId(resolved.stack().getItem());
        if (resolved.usedFallback() || !expectedItemId.equals(String.valueOf(actualId))) {
            throw new IllegalStateException(entry.name() + " resolved to " + actualId
                    + " (directory=" + entry.directory()
                    + ", requested=" + presentation.itemIcon().requestedItem()
                    + ", fallback=" + resolved.usedFallback() + "), expected " + expectedItemId);
        }
    }
}
