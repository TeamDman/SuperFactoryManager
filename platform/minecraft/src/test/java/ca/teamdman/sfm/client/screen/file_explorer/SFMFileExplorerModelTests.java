package ca.teamdman.sfm.client.screen.file_explorer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMFileExplorerModelTests {
    @Test
    public void directoryExpansionAndParentNavigationArePureModelOperations() {
        SFMFileExplorerModel model = readyModel();
        assertEquals(List.of("src", "README"), visibleNames(model));

        model.expandSelection();
        assertEquals(List.of("src", "Main.java", "grammar.g4", "README"), visibleNames(model));

        model.selectNext();
        model.collapseSelectionOrSelectParent();
        assertEquals("src", model.selection().orElseThrow().entry().name());

        model.collapseSelectionOrSelectParent();
        assertEquals(List.of("src", "README"), visibleNames(model));
    }

    @Test
    public void activationOnlyProducesIntentForFiles() {
        SFMFileExplorerModel model = readyModel();
        assertTrue(model.activateSelection().isEmpty());
        model.selectLast();
        SFMFileExplorerModel.OpenIntent intent = model.activateSelection().orElseThrow();
        assertEquals("fixture", intent.sourceName());
        assertEquals("README", intent.entry().path());
    }

    @Test
    public void loadingEmptyAndErrorStatesRemainDistinct() {
        MutableSource source = new MutableSource(SFMFileExplorerSnapshot.loading("Indexing"));
        SFMFileExplorerModel model = new SFMFileExplorerModel(source);
        model.reload();
        assertEquals("Indexing", model.stateDescription());

        source.snapshot = SFMFileExplorerSnapshot.ready(List.of());
        model.reload();
        assertEquals("This source is empty", model.stateDescription());

        source.snapshot = SFMFileExplorerSnapshot.error("Permission denied");
        model.reload();
        assertEquals("Permission denied", model.stateDescription());
        assertFalse(model.selection().isPresent());
    }

    @Test
    public void narrationIncludesTextualFileKindAndPosition() {
        SFMFileExplorerModel model = readyModel();
        model.selectNext();
        assertEquals(
                "README, file without extension, item 2 of 2",
                model.selectedNarration(SFMFilePresentationRegistry.createDefault())
        );
    }

    @Test
    public void thousandFileGroupsRemainNavigableAsAFlatVisibleProjection() {
        SFMFileExplorerEntry firstGroup = numberedDirectory("0000-0999", 0, 999);
        SFMFileExplorerEntry secondGroup = numberedDirectory("1000-1999", 1000, 1001);
        SFMFileExplorerModel model = new SFMFileExplorerModel(new MutableSource(
                SFMFileExplorerSnapshot.ready(List.of(firstGroup, secondGroup))
        ));
        model.reload();
        assertEquals(List.of("0000-0999", "1000-1999"), visibleNames(model));

        model.expandSelection();
        assertEquals(1002, model.visibleEntries().size());
        model.selectLast();
        assertEquals("1000-1999", model.selection().orElseThrow().entry().name());
        model.expandSelection();
        model.selectNext();
        assertEquals(1004, model.visibleEntries().size());
        assertEquals("1000.txt", model.selection().orElseThrow().entry().name());
        assertEquals("1001.txt", model.visibleEntries().get(1003).entry().name());
    }

    private static SFMFileExplorerModel readyModel() {
        SFMFileExplorerEntry src = SFMFileExplorerEntry.directory("src", "src", List.of(
                SFMFileExplorerEntry.file("src/Main.java", "Main.java"),
                SFMFileExplorerEntry.file("src/grammar.g4", "grammar.g4")
        ));
        MutableSource source = new MutableSource(SFMFileExplorerSnapshot.ready(List.of(
                src,
                SFMFileExplorerEntry.file("README", "README")
        )));
        SFMFileExplorerModel model = new SFMFileExplorerModel(source);
        model.reload();
        return model;
    }

    private static List<String> visibleNames(SFMFileExplorerModel model) {
        return model.visibleEntries().stream().map(row -> row.entry().name()).toList();
    }

    private static SFMFileExplorerEntry numberedDirectory(
            String name,
            int first,
            int last
    ) {
        List<SFMFileExplorerEntry> files = IntStream.rangeClosed(first, last)
                .mapToObj(value -> {
                    String fileName = "%04d.txt".formatted(value);
                    return SFMFileExplorerEntry.file(name + "/" + fileName, fileName);
                })
                .toList();
        return SFMFileExplorerEntry.directory(name, name, files);
    }

    private static final class MutableSource implements SFMFileExplorerSource {
        private SFMFileExplorerSnapshot snapshot;

        private MutableSource(SFMFileExplorerSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public String displayName() {
            return "fixture";
        }

        @Override
        public SFMFileExplorerSnapshot snapshot() {
            return snapshot;
        }
    }
}
