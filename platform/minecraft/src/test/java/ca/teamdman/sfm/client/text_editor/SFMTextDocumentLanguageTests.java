package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextDocumentLanguageTests {
    @Test
    void pathLanguageBecomesExplicitBeforeTheRendererSeesTheDocument() {
        SFMTextDocumentLanguage javaLanguage = SFMTextDocumentLanguage.fromPath(
                SFMPath.parse("review://document/revision/src/Example.java")
        );
        SFMTextDocumentLanguage reviewJson = SFMTextDocumentLanguage.fromPath(
                SFMPath.parse("file:///D:/repo/release.sfm-review.json")
        );
        SFMTextDocumentLanguage diff = SFMTextDocumentLanguage.fromPath(
                SFMPath.parse("review-surface://generated/cache/text-diff.diff")
        );

        assertEquals("java", javaLanguage.id());
        assertEquals(java.util.Optional.of("java"), javaLanguage.remoteWorkerLanguage());
        assertEquals(SFMTextDocumentLanguage.json(), reviewJson);
        assertEquals(java.util.Optional.of("json"), reviewJson.remoteWorkerLanguage());
        assertEquals(SFMTextDocumentLanguage.diff(), diff);
        assertTrue(diff.remoteWorkerLanguage().isEmpty());
    }

    @Test
    void onlySfmlUsesTheLocalSfmlHighlighterAndUnknownLanguagesRemainNeutral() {
        assertTrue(SFMTextDocumentLanguage.sfml().usesLocalSfmlHighlighting());
        assertEquals(
                SFMTextDocumentLanguage.Highlighting.REMOTE_WORKER,
                SFMTextDocumentLanguage.declared("markdown").highlighting()
        );
        assertEquals(
                SFMTextDocumentLanguage.Highlighting.NEUTRAL,
                SFMTextDocumentLanguage.declared("unsupported-fixture").highlighting()
        );
    }

    @Test
    void repositoryLanguagesUseCanonicalWorkerIdsIncludingVirtualReviewSources() {
        String[][] cases = {{"cli.rs", "rust"}, {"build.gradle", "groovy"},
                {"README.md", "markdown"}, {"data.json", "json"}, {"install.ps1", "powershell"},
                {"main.ts", "typescript"}, {"Cargo.toml", "toml"}};
        for (String[] value : cases) {
            var language = SFMTextDocumentLanguage.fromPath(SFMPath.parse("review://source/revision/" + value[0]));
            assertEquals(value[1], language.id());
            assertEquals(java.util.Optional.of(value[1]), language.remoteWorkerLanguage());
            assertEquals(language, SFMTextDocumentLanguage.declared(value[1]));
        }
    }

    @Test
    void snapshotsPreserveLanguageAcrossSavedAndSemanticProjections() {
        String text = "class Example {}\n";
        SFMPath root = SFMPath.parse("review://document/revision/");
        SFMPath path = SFMPath.parse("review://document/revision/src/Example.java");
        SFMTextDocumentSnapshot snapshot = SFMTextDocumentSnapshot.pinned(
                path,
                root,
                text,
                SFMTextDocumentSnapshot.literal(text).sha256().orElseThrow(),
                java.util.Optional.empty(),
                java.util.Optional.empty()
        );

        assertEquals(SFMTextDocumentLanguage.java(), snapshot.language());
        assertEquals(snapshot.language(), snapshot.withSavedText(text + "// changed\n").language());
    }
}
