package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SFMExplorerPresentationRegistryTests {
{% if features.theme_preview_rules and features.client_theme %}
    @Test
    public void authoredRuleReplacesOnlyTheIconAndReloadInvalidatesItsDecision() {
        var row=row(SFMPath.parse("file:///C:/work/abc.json"),"abc.json",false,Optional.empty());
        var registry=SFMExplorerPresentationRegistry.minecraftDefaults();
        try {
            String rules=ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRuleCodec.write(List.of(
                    ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules.Rule.user(
                            ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.parse(
                                    "sfm:string/equals sfm:entry/name \"abc.json\"",
                                    ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewOperators.builtins()),
                            ca.teamdman.sfm.client.presentation.SFMItemIcon.vanilla("diamond","diamond"))));
            org.junit.jupiter.api.Assertions.assertTrue(ca.teamdman.sfm.client.theme.SFMClientThemeService.reloadText("schema_version=1\n"+rules).valid());
            var first=registry.resolve(row);
            assertEquals("abc.json",first.presentation().label());
            assertEquals(new ResourceLocation("minecraft:diamond"),assertInstanceOf(SFMExplorerPresentation.ItemIcon.class,first.presentation().icon()).item().requestedItem());
            ca.teamdman.sfm.client.theme.SFMClientThemeService.resetForTests();
{% if features.theme_file_icon_defaults %}
            assertEquals(new ResourceLocation("minecraft:written_book"),assertInstanceOf(SFMExplorerPresentation.ItemIcon.class,registry.resolve(row).presentation().icon()).item().requestedItem());
{% else %}
            assertEquals(new ResourceLocation("minecraft:map"),assertInstanceOf(SFMExplorerPresentation.ItemIcon.class,registry.resolve(row).presentation().icon()).item().requestedItem());
{% endif %}
            assertEquals(SFMPath.parse("file:///C:/work/abc.json"),row.path());
        } finally { ca.teamdman.sfm.client.theme.SFMClientThemeService.resetForTests(); }
    }
{% endif %}
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

{% if features.client_theme or features.command_palette %}
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
{% endif %}

{% if features.client_theme and features.theme_file_icon_matching %}
    @Test
    public void minecraftDefaultsMapOrdinaryFilesToThemeBackedItemPresentation() {
        SFMExplorerPresentationRegistry.Resolution resolution =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        SFMPath.parse("file:///C:/work/example.txt"),
                        "example.txt",
                        false,
                        Optional.of("file:txt")
                ));

        assertEquals("sfm:file_extension", resolution.contributorId());
        SFMExplorerPresentation.ItemIcon icon = assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        );
        assertEquals(new ResourceLocation("minecraft", "paper"), icon.item().requestedItem());
    }
{% endif %}

{% if features.client_theme or features.command_palette %}
    @Test
    public void itemRegistryRowsRemainActualItemStacksInListAndSmallIconProjections() {
        SFMExplorerProjection.Row item = row(
                SFMPath.parse("registry://minecraft/item/minecraft/stone"),
                "Stone",
                false,
                Optional.of("minecraft:stone")
        );
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();

        for (SFMExplorerProjection.View view : SFMExplorerProjection.View.values()) {
            SFMExplorerPanelViewport.Snapshot viewport = SFMExplorerPanelViewport.calculate(
                    new SFMScreenPanelBounds(0, 0, 320, 180),
                    view,
                    List.of(item),
                    0
            );
            SFMExplorerPresentation.ItemIcon icon = assertInstanceOf(
                    SFMExplorerPresentation.ItemIcon.class,
                    registry.resolve(viewport.cells().get(0).row()).presentation().icon()
            );
            assertEquals(new ResourceLocation("minecraft", "stone"), icon.item().requestedItem());
        }
    }
{% endif %}

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
