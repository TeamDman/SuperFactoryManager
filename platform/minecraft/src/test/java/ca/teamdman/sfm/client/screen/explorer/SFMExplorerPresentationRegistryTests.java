package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SFMExplorerPresentationRegistryTests {
    @Test
    public void lowerOrderedCustomContributorIsSelectedDeterministically() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register("test:z_later", 20, row -> Optional.of(marker(row, "later", "[L]")))
                .register("test:custom", 10, row -> Optional.of(marker(row, "custom", "[C]")))
                .build();

        SFMExplorerPresentationRegistry.Resolution resolution = registry.resolve(row(
                SFMPath.parse("file:///C:/work/example.txt"),
                "example.txt",
                false,
                Optional.of("file:txt")
        ));

        assertEquals("test:custom", resolution.contributorId());
        assertEquals("custom", resolution.presentation().label());
        SFMExplorerPresentation.MarkerIcon icon = assertInstanceOf(
                SFMExplorerPresentation.MarkerIcon.class,
                resolution.presentation().icon()
        );
        assertEquals("[C]", icon.marker());
    }

    @Test
    public void minecraftDefaultsMapItemRegistryPathsToItemPresentation() {
        SFMExplorerPresentationRegistry.Resolution resolution =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        SFMPath.parse("registry://minecraft/item/minecraft/stone"),
                        "Stone",
                        false,
                        Optional.of("minecraft:stone")
                ));

        assertEquals(SFMMinecraftItemExplorerPresenter.ID, resolution.contributorId());
        SFMExplorerPresentation.ItemIcon icon = assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        );
        assertEquals(new ResourceLocation("minecraft", "stone"), icon.item().requestedItem());
    }

    @Test
    public void ordinaryFilesUseGenericFallbackEvenWhenResolverContributesIconSortMetadata() {
        SFMExplorerPresentationRegistry.Resolution resolution =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        SFMPath.parse("file:///C:/work/example.txt"),
                        "example.txt",
                        false,
                        Optional.of("file:txt")
                ));

        assertEquals(SFMExplorerPresentationRegistry.GENERIC_FALLBACK_ID, resolution.contributorId());
        SFMExplorerPresentation.MarkerIcon icon = assertInstanceOf(
                SFMExplorerPresentation.MarkerIcon.class,
                resolution.presentation().icon()
        );
        assertEquals("[F]", icon.marker());
    }

    private static SFMExplorerPresentation marker(
            SFMExplorerProjection.Row row,
            String label,
            String marker
    ) {
        return new SFMExplorerPresentation(label, new SFMExplorerPresentation.MarkerIcon(marker));
    }

    private static SFMExplorerProjection.Row row(
            SFMPath path,
            String label,
            boolean expandable,
            Optional<String> iconKey
    ) {
        SFMExplorerEntry entry = SFMExplorerEntry.simple(path, label, expandable, iconKey);
        return new SFMExplorerProjection.Row(
                path,
                entry,
                0,
                false,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME)
        );
    }
}
