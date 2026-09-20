package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import ca.teamdman.sfm.client.symbol.SFMSymbolReferenceResultRepository;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeLoader;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SFMExplorerFilePresentationTests {
    private static final ResourceLocation CHEST = new ResourceLocation("minecraft", "chest");
    private static final ResourceLocation BARREL = new ResourceLocation("minecraft", "barrel");
    private static final ResourceLocation PAPER = new ResourceLocation("minecraft", "paper");
    private static final ResourceLocation COCOA_BEANS = new ResourceLocation("minecraft", "cocoa_beans");
    private static final ResourceLocation WRITTEN_BOOK = new ResourceLocation("minecraft", "written_book");
    private static final ResourceLocation BELL = new ResourceLocation("minecraft", "bell");

    @Test
    public void fileDirectoriesUseSpecificNameRulesOrChestForRootChildCollapsedAndExpandedRows() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();

        assertFileIcon(
                registry.resolve(row("file:///C:/project", "project", true, 0, false)),
                "project",
                CHEST
        );
        assertFileIcon(
                registry.resolve(row("file:///C:/project/src", "src", true, 1, false)),
                "src",
                new ResourceLocation("minecraft", "crafting_table")
        );
        assertFileIcon(
                registry.resolve(row("file:///C:/project/src", "src", true, 1, true)),
                "src",
                new ResourceLocation("minecraft", "crafting_table")
        );
        assertEquals(BARREL, itemIcon(registry.resolve(
                row("file:///C:/project/src", "src", true, 1, false)
        )).fallbackItem(), "title-screen safety must retain a container-shaped fallback");
    }

    @Test
    public void javaLeavesUseCocoaBeansCaseInsensitivelyWithoutChangingTheirLabel() {
        SFMExplorerPresentationRegistry.Resolution resolution =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        "file:///C:/project/src/SFM.java",
                        "SFM.java",
                        false,
                        2,
                        false
                ));

        assertEquals(SFMFileExtensionExplorerPresenter.ID, resolution.contributorId());
        assertFileIcon(resolution, "SFM.java", COCOA_BEANS);
        assertFileIcon(
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        "file:///C:/project/src/LOUD.JAVA", "LOUD.JAVA", false, 0, false
                )),
                "LOUD.JAVA",
                COCOA_BEANS
        );
    }

    @Test
    public void persistedDefaultThemeAndInMemoryDefaultAgreeOnTheJavaCocoaIcon() {
        SFMClientTheme persistedDefault = SFMClientThemeLoader.load(
                SFMClientThemeService.DEFAULT_TOML,
                SFMClientTheme.defaults()
        ).theme().orElseThrow();

        assertEquals(COCOA_BEANS, SFMClientTheme.defaults().fileIcon(".java").requestedItem());
        assertEquals(COCOA_BEANS, persistedDefault.fileIcon(".java").requestedItem());
        assertEquals(WRITTEN_BOOK, persistedDefault.fileIconForName("settings.json").requestedItem());
        assertEquals(BELL, persistedDefault.fileIconForName("candidate.sfm-review.json").requestedItem());
    }

    @Test
    public void jsonAndReleaseReviewCompoundSuffixesUseDistinctIconsWithLongestMatchWinning() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();

        SFMExplorerPresentationRegistry.Resolution json = registry.resolve(row(
                "file:///C:/project/settings.json", "settings.json", false, 0, false
        ));
        assertEquals(SFMFileExtensionExplorerPresenter.ID, json.contributorId());
        assertFileIcon(json, "settings.json", WRITTEN_BOOK);

        SFMExplorerPresentationRegistry.Resolution review = registry.resolve(row(
                "file:///C:/project/candidate.sfm-review.json",
                "candidate.sfm-review.json",
                false,
                0,
                false
        ));
        assertEquals(SFMFileExtensionExplorerPresenter.ID, review.contributorId());
        assertFileIcon(review, "candidate.sfm-review.json", BELL);

        assertFileIcon(
                registry.resolve(row(
                        "file:///C:/project/LOUD.SFM-REVIEW.JSON",
                        "LOUD.SFM-REVIEW.JSON",
                        false,
                        0,
                        false
                )),
                "LOUD.SFM-REVIEW.JSON",
                BELL
        );

        SFMClientTheme defaults = SFMClientTheme.defaults();
        Map<String, SFMItemIcon> reversedSpecificity = new LinkedHashMap<>(defaults.fileIcons());
        SFMItemIcon jsonIcon = reversedSpecificity.remove(".json");
        SFMItemIcon reviewIcon = reversedSpecificity.remove(".sfm-review.json");
        reversedSpecificity.put(".json", jsonIcon);
        reversedSpecificity.put(".sfm-review.json", reviewIcon);
        SFMClientTheme reordered = new SFMClientTheme(
                defaults.colours(), defaults.sfmlSyntax(), reversedSpecificity, defaults.actionIcons()
        );
        assertEquals(BELL, reordered.fileIconForName("candidate.sfm-review.json").requestedItem(),
                "compound suffix specificity must not depend on theme-map insertion order");
    }

    @Test
    public void ordinaryAndUnknownExtensionsFallThroughToPaper() {
        assertFileIcon(
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        "file:///C:/project/readme.txt", "readme.txt", false, 0, false
                )),
                "readme.txt",
                PAPER
        );
        assertFileIcon(
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        "file:///C:/project/archive.unknown", "archive.unknown", false, 0, false
                )),
                "archive.unknown",
                PAPER
        );
        assertFileIcon(
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row(
                        "file:///C:/project/README", "README", false, 0, false
                )),
                "README",
                PAPER
        );
    }

    @Test
    public void longestCompoundThemeExtensionWinsBeforeTheOrdinaryExtension() {
        SFMClientTheme defaults = SFMClientTheme.defaults();
        Map<String, SFMItemIcon> icons = new LinkedHashMap<>(defaults.fileIcons());
        ResourceLocation compound = new ResourceLocation("minecraft", "diamond");
        icons.put(".generated.java", new SFMItemIcon(compound, PAPER, "generated Java source"));
        SFMClientTheme theme = new SFMClientTheme(
                defaults.colours(), defaults.sfmlSyntax(), icons, defaults.actionIcons()
        );
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register(SFMFileExtensionExplorerPresenter.ID, SFMFileExtensionExplorerPresenter.ORDER,
                        new SFMFileExtensionExplorerPresenter(() -> theme))
                .register(SFMFilePathExplorerPresenter.ID, SFMFilePathExplorerPresenter.ORDER,
                        new SFMFilePathExplorerPresenter(() -> theme))
                .build();

        SFMExplorerPresentationRegistry.Resolution resolution = registry.resolve(row(
                "file:///C:/project/Output.generated.java",
                "Output.generated.java",
                false,
                0,
                false
        ));
        assertEquals(SFMFileExtensionExplorerPresenter.ID, resolution.contributorId());
        assertEquals(compound, assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        ).item().requestedItem());
        assertEquals("generated Java source", assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        ).item().accessibleLabel());
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
    public void symbolReferenceRowsUseTypedThemeBackedIconsWithoutChangingLabels() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();

        SFMExplorerPresentationRegistry.Resolution hierarchy = registry.resolve(referenceRow(
                "symbol-references://result-1/invocation/",
                "Invocation (2)",
                true,
                SFMSymbolReferenceResultRepository.PresentationKind.HIERARCHY,
                Optional.empty()
        ));
        assertEquals(SFMSymbolReferenceExplorerPresenter.ID, hierarchy.contributorId());
        assertFileIcon(hierarchy, "Invocation (2)", CHEST);
        assertEquals("directory", itemIcon(hierarchy).accessibleLabel());

        SFMExplorerPresentationRegistry.Resolution javaFile = registry.resolve(referenceRow(
                "symbol-references://result-1/invocation/q%2FUse.java/",
                "q/Use.java (2)",
                true,
                SFMSymbolReferenceResultRepository.PresentationKind.JAVA_SOURCE,
                Optional.of("workspace://main/q/Use.java")
        ));
        assertEquals(SFMSymbolReferenceExplorerPresenter.ID, javaFile.contributorId());
        assertFileIcon(javaFile, "q/Use.java (2)", COCOA_BEANS);
        assertEquals("Java source", itemIcon(javaFile).accessibleLabel());

        SFMExplorerPresentationRegistry.Resolution javaSpan = registry.resolve(referenceRow(
                "symbol-references://result-1/invocation/q%2FUse.java/span-000001",
                "line 4:9–4:15  q.Use  [resolved]",
                false,
                SFMSymbolReferenceResultRepository.PresentationKind.JAVA_SOURCE,
                Optional.of("workspace://main/q/Use.java")
        ));
        assertFileIcon(javaSpan, "line 4:9–4:15  q.Use  [resolved]", COCOA_BEANS);

        SFMExplorerPresentationRegistry.Resolution ordinarySpan = registry.resolve(referenceRow(
                "symbol-references://result-1/invocation/generated.txt/span-000001",
                "line 1:1–1:4  q.Use  [resolved]",
                false,
                SFMSymbolReferenceResultRepository.PresentationKind.FILE_SOURCE,
                Optional.of("workspace://generated/generated.txt")
        ));
        assertFileIcon(ordinarySpan, "line 1:1–1:4  q.Use  [resolved]", PAPER);
        assertEquals("unknown file", itemIcon(ordinarySpan).accessibleLabel());

        SFMExplorerPresentationRegistry.Resolution information = registry.resolve(referenceRow(
                "symbol-references://result-1/analysis/outcome",
                "Outcome: success",
                false,
                SFMSymbolReferenceResultRepository.PresentationKind.INFORMATION,
                Optional.empty()
        ));
        assertFileIcon(information, "Outcome: success", PAPER);
    }

    @Test
    public void symbolReferencePresenterRequiresRepositoryOwnedMetadataAndRetainsPrecedence() {
        SFMExplorerProjection.Row withoutMetadata = row(
                "symbol-references://result-1/invocation/",
                "Invocation (1)",
                true,
                0,
                false
        );
        SFMExplorerPresentationRegistry.Resolution fallback =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(withoutMetadata);
        assertEquals(SFMExplorerPresentationRegistry.GENERIC_FALLBACK_ID, fallback.contributorId());
        assertEquals("[D]", assertInstanceOf(
                SFMExplorerPresentation.MarkerIcon.class,
                fallback.presentation().icon()
        ).marker());

        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register(
                        "test:reference_override",
                        SFMSymbolReferenceExplorerPresenter.ORDER - 1,
                        candidate -> candidate.path().scheme().equals(SFMSymbolReferenceResultRepository.SCHEME)
                                ? Optional.of(marker(candidate.entry().label(), "[R]"))
                                : Optional.empty()
                )
                .register(
                        SFMSymbolReferenceExplorerPresenter.ID,
                        SFMSymbolReferenceExplorerPresenter.ORDER,
                        new SFMSymbolReferenceExplorerPresenter()
                )
                .build();
        SFMExplorerPresentationRegistry.Resolution overridden = registry.resolve(referenceRow(
                "symbol-references://result-1/invocation/",
                "Invocation (1)",
                true,
                SFMSymbolReferenceResultRepository.PresentationKind.HIERARCHY,
                Optional.empty()
        ));
        assertEquals("test:reference_override", overridden.contributorId());
        assertEquals("[R]", assertInstanceOf(
                SFMExplorerPresentation.MarkerIcon.class,
                overridden.presentation().icon()
        ).marker());
    }

    @Test
    public void unavailableReferenceThemeItemsRetainDeclaredFallbackAndNarration() {
        ResourceLocation unavailable = new ResourceLocation("test", "missing_reference_java_icon");
        SFMClientTheme defaults = SFMClientTheme.defaults();
        Map<String, SFMItemIcon> icons = new LinkedHashMap<>(defaults.fileIcons());
        icons.put(".java", new SFMItemIcon(unavailable, PAPER, "custom Java reference"));
        SFMClientTheme theme = new SFMClientTheme(
                defaults.colours(), defaults.sfmlSyntax(), icons, defaults.actionIcons()
        );
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register(
                        SFMSymbolReferenceExplorerPresenter.ID,
                        SFMSymbolReferenceExplorerPresenter.ORDER,
                        new SFMSymbolReferenceExplorerPresenter(() -> theme)
                )
                .build();

        SFMExplorerPresentationRegistry.Resolution resolution = registry.resolve(referenceRow(
                "symbol-references://result-1/invocation/q%2FUse.java/span-000001",
                "line 4:9–4:15  q.Use  [resolved]",
                false,
                SFMSymbolReferenceResultRepository.PresentationKind.JAVA_SOURCE,
                Optional.of("workspace://main/q/Use.java")
        ));
        SFMItemIcon icon = itemIcon(resolution);
        assertEquals(unavailable, icon.requestedItem());
        assertEquals(PAPER, SFMItemIconResolver.selectAvailableId(icon, PAPER::equals));
        assertEquals("custom Java reference", icon.accessibleLabel());
    }

    @Test
    public void orderedCustomPresentersRetainPrecedenceAroundTheFileDefault() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register("test:later", SFMFilePathExplorerPresenter.ORDER + 1,
                        row -> Optional.of(marker("later", "[L]")))
                .register(SFMFilePathExplorerPresenter.ID, SFMFilePathExplorerPresenter.ORDER,
                        new SFMFilePathExplorerPresenter())
                .register(SFMFileExtensionExplorerPresenter.ID, SFMFileExtensionExplorerPresenter.ORDER,
                        new SFMFileExtensionExplorerPresenter())
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
        assertEquals("sfm:file_extension", ordinary.contributorId());
        assertEquals(PAPER, assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                ordinary.presentation().icon()
        ).item().requestedItem());
    }

    @Test
    public void anEarlierCustomContributorCanOverrideTheDefaultJavaExtensionTheme() {
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register("test:java_override", SFMFileExtensionExplorerPresenter.ORDER - 1,
                        row -> row.path().extension().equalsIgnoreCase("java")
                                ? Optional.of(marker("custom Java", "[J]"))
                                : Optional.empty())
                .register(SFMFileExtensionExplorerPresenter.ID, SFMFileExtensionExplorerPresenter.ORDER,
                        new SFMFileExtensionExplorerPresenter())
                .register(SFMFilePathExplorerPresenter.ID, SFMFilePathExplorerPresenter.ORDER,
                        new SFMFilePathExplorerPresenter())
                .build();

        SFMExplorerPresentationRegistry.Resolution resolution = registry.resolve(row(
                "file:///C:/project/A.java", "A.java", false, 0, false
        ));
        assertEquals("test:java_override", resolution.contributorId());
        assertEquals("[J]", assertInstanceOf(
                SFMExplorerPresentation.MarkerIcon.class,
                resolution.presentation().icon()
        ).marker());
    }

    @Test
    public void missingJavaThemeItemFallsBackToPaperWithoutLosingAccessibleNarration() {
        ResourceLocation unavailable = new ResourceLocation("test", "missing_java_icon");
        SFMClientTheme defaults = SFMClientTheme.defaults();
        Map<String, SFMItemIcon> icons = new LinkedHashMap<>(defaults.fileIcons());
        icons.put(".java", new SFMItemIcon(unavailable, PAPER, "Java source"));
        SFMClientTheme theme = new SFMClientTheme(
                defaults.colours(), defaults.sfmlSyntax(), icons, defaults.actionIcons()
        );
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.builder()
                .register(SFMFileExtensionExplorerPresenter.ID, SFMFileExtensionExplorerPresenter.ORDER,
                        new SFMFileExtensionExplorerPresenter(() -> theme))
                .register(SFMFilePathExplorerPresenter.ID, SFMFilePathExplorerPresenter.ORDER,
                        new SFMFilePathExplorerPresenter(() -> theme))
                .build();

        SFMItemIcon icon = assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                registry.resolve(row("file:///C:/project/A.java", "A.java", false, 0, false))
                        .presentation().icon()
        ).item();
        assertEquals("Java source", icon.accessibleLabel());
        assertEquals(PAPER, SFMItemIconResolver.selectAvailableId(icon, PAPER::equals));
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
        assertEquals(expectedLabel, resolution.presentation().label());
        SFMItemIcon icon = assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        ).item();
        assertEquals(expectedItem, icon.requestedItem());
        assertEquals(expectedItem, SFMItemIconResolver.selectAvailableId(icon, expectedItem::equals));
    }

    private static SFMItemIcon itemIcon(SFMExplorerPresentationRegistry.Resolution resolution) {
        return assertInstanceOf(
                SFMExplorerPresentation.ItemIcon.class,
                resolution.presentation().icon()
        ).item();
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

    private static SFMExplorerProjection.Row referenceRow(
            String path,
            String label,
            boolean expandable,
            SFMSymbolReferenceResultRepository.PresentationKind kind,
            Optional<String> sourceAddress
    ) {
        SFMPath parsed = SFMPath.parse(path);
        TreeMap<String, SFMExplorerEntry.SortKey> sortKeys = new TreeMap<>();
        sortKeys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(label));
        sortKeys.put(SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available("stable-test-icon-key"));
        sortKeys.put(
                SFMSymbolReferenceResultRepository.METADATA_PRESENTATION_KIND,
                SFMExplorerEntry.SortKey.available(kind.wireName())
        );
        sourceAddress.ifPresent(address -> sortKeys.put(
                SFMSymbolReferenceResultRepository.METADATA_SOURCE_ADDRESS,
                SFMExplorerEntry.SortKey.available(address)
        ));
        SFMExplorerEntry entry = new SFMExplorerEntry(parsed, label, expandable, sortKeys, java.util.List.of());
        return new SFMExplorerProjection.Row(
                parsed,
                entry,
                0,
                false,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME)
        );
    }
}
