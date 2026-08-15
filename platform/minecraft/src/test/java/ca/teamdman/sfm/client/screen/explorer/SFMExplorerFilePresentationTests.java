package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SFMExplorerFilePresentationTests {
    private static final ResourceLocation CHEST = new ResourceLocation("minecraft", "chest");
    private static final ResourceLocation PAPER = new ResourceLocation("minecraft", "paper");

    @Test
    public void fileDirectoriesUseTheThemeChestForRootChildCollapsedAndExpandedRows() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();

        assertFileIcon(
                registry.resolve(row("file:///C:/project", "project", true, 0, false)),
                "project",
                CHEST
        );
        assertFileIcon(
                registry.resolve(row("file:///C:/project/src", "src", true, 1, false)),
                "src",
                CHEST
        );
        assertFileIcon(
                registry.resolve(row("file:///C:/project/src", "src", true, 1, true)),
                "src",
                CHEST
        );
    }

    @Test
    public void fileLeavesUseTheThemePaperWithoutChangingTheirLabel() {
        SFMExplorerPresentationRegistry.Resolution resolution =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        "file:///C:/project/src/SFM.java",
                        "SFM.java",
                        false,
                        2,
                        false
                ));

        assertFileIcon(resolution, "SFM.java", PAPER);
    }

    @Test
    public void filePresenterDoesNotClaimRegistryOrOtherSchemesBasedOnExpandability() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();

        SFMExplorerPresentationRegistry.Resolution item = registry.resolve(row(
                "registry://minecraft/item/minecraft/stone",
                "Stone",
                false,
                0,
                false,
                Optional.of("minecraft:stone")
        ));
        assertEquals(SFMMinecraftItemExplorerPresenter.ID, item.contributorId());
        assertEquals(
                new ResourceLocation("minecraft", "stone"),
                assertInstanceOf(SFMExplorerPresentation.ItemIcon.class, item.presentation().icon())
                        .item()
                        .requestedItem()
        );

        SFMExplorerPresentationRegistry.Resolution contributedDirectory = registry.resolve(row(
                "example://root/directory",
                "directory",
                true,
                0,
                false
        ));
        assertEquals(SFMExplorerPresentationRegistry.GENERIC_FALLBACK_ID, contributedDirectory.contributorId());
        assertEquals(
                "[D]",
                assertInstanceOf(
                        SFMExplorerPresentation.MarkerIcon.class,
                        contributedDirectory.presentation().icon()
                ).marker()
        );

        SFMExplorerPresentationRegistry.Resolution contributedLeaf = registry.resolve(row(
                "example://root/file",
                "file",
                false,
                0,
                false
        ));
        assertEquals(SFMExplorerPresentationRegistry.GENERIC_FALLBACK_ID, contributedLeaf.contributorId());
        assertEquals(
                "[F]",
                assertInstanceOf(
                        SFMExplorerPresentation.MarkerIcon.class,
                        contributedLeaf.presentation().icon()
                ).marker()
        );
    }

    @Test
    public void orderedCustomPresentersRetainPrecedenceAroundTheFileDefault() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register("test:later", SFMFilePathExplorerPresenter.ORDER + 1,
                        row -> Optional.of(marker("later", "[L]")))
                .register(SFMFilePathExplorerPresenter.ID, SFMFilePathExplorerPresenter.ORDER,
                        new SFMFilePathExplorerPresenter())
                .register("test:earlier", SFMFilePathExplorerPresenter.ORDER - 1,
                        row -> row.path().extension().equals("special")
                                ? Optional.of(marker("special", "[S]"))
                                : Optional.empty())
                .build();

        SFMExplorerPresentationRegistry.Resolution richer = registry.resolve(row(
                "file:///C:/project/example.special",
                "example.special",
                false,
                0,
                false
        ));
        assertEquals("test:earlier", richer.contributorId());
        assertEquals("[S]", assertInstanceOf(
                SFMExplorerPresentation.MarkerIcon.class,
                richer.presentation().icon()
        ).marker());

        SFMExplorerPresentationRegistry.Resolution ordinary = registry.resolve(row(
                "file:///C:/project/example.txt",
                "example.txt",
                false,
                0,
                false
        ));
        assertEquals(SFMFilePathExplorerPresenter.ID, ordinary.contributorId());
        assertEquals(PAPER, assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                ordinary.presentation().icon()
        ).item().requestedItem());
    }

    @Test
    public void unavailableThemeItemsUseTheirDeclaredFallbackAndUltimatelyPaper() {
        ResourceLocation unavailable = new ResourceLocation("test", "missing_directory_icon");
        SFMClientTheme defaults = SFMClientTheme.defaults();
        Map<String, SFMItemIcon> fileIcons = new LinkedHashMap<>(defaults.fileIcons());
        fileIcons.put("directory", new SFMItemIcon(unavailable, CHEST, "directory"));
        SFMClientTheme theme = new SFMClientTheme(
                defaults.colours(),
                defaults.sfmlSyntax(),
                fileIcons,
                defaults.actionIcons()
        );
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register(SFMFilePathExplorerPresenter.ID, SFMFilePathExplorerPresenter.ORDER,
                        new SFMFilePathExplorerPresenter(() -> theme))
                .build();

        SFMItemIcon icon = assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                registry.resolve(row("file:///C:/project", "project", true, 0, false))
                        .presentation()
                        .icon()
        ).item();
        assertEquals(unavailable, icon.requestedItem());
        assertEquals(CHEST, icon.fallbackItem());
        assertEquals(CHEST, SFMItemIconResolver.selectAvailableId(icon, CHEST::equals));
        assertEquals(PAPER, SFMItemIconResolver.selectAvailableId(icon, ignored -> false));
    }

    private static void assertFileIcon(
            SFMExplorerPresentationRegistry.Resolution resolution,
            String expectedLabel,
            ResourceLocation expectedItem
    ) {
        assertEquals(SFMFilePathExplorerPresenter.ID, resolution.contributorId());
        assertEquals(expectedLabel, resolution.presentation().label());
        SFMItemIcon icon = assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        ).item();
        assertEquals(expectedItem, icon.requestedItem());
        assertEquals(expectedItem, SFMItemIconResolver.selectAvailableId(icon, expectedItem::equals));
    }

    private static SFMExplorerPresentation marker(String label, String marker) {
        return new SFMExplorerPresentation(label, new SFMExplorerPresentation.MarkerIcon(marker));
    }

    private static SFMExplorerProjection.Row row(
            String path,
            String label,
            boolean expandable,
            int depth,
            boolean expanded
    ) {
        return row(path, label, expandable, depth, expanded, Optional.empty());
    }

    private static SFMExplorerProjection.Row row(
            String path,
            String label,
            boolean expandable,
            int depth,
            boolean expanded,
            Optional<String> iconKey
    ) {
        SFMPath parsed = SFMPath.parse(path);
        SFMExplorerEntry entry = SFMExplorerEntry.simple(parsed, label, expandable, iconKey);
        return new SFMExplorerProjection.Row(
                parsed,
                entry,
                depth,
                expanded,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME)
        );
    }
}
