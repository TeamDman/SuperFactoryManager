package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReviewEvidenceExport;
import ca.teamdman.sfm.client.review.release_review.SFMReviewEvidenceExportService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewEvidenceExportActionTests {
    @Test void previewGrammarRequiresSourceDestinationAndPolicy() {
        var dispatcher = new CommandDispatcher<SFMClientActionSource>();
        var node = LiteralArgumentBuilder.<SFMClientActionSource>literal("export");
        new SFMReviewEvidenceExportAction(SFMReviewEvidenceExportAction.Kind.PREVIEW).configureCommandNode(node);
        dispatcher.register(node);
        var complete = dispatcher.parse("export \"D:/review folder/original.json\" \"D:/review folder/copy.json\" portable", null);
        assertTrue(complete.getExceptions().isEmpty());
        assertFalse(complete.getReader().canRead());
        assertNotNull(complete.getContext().getCommand());
        var incomplete = dispatcher.parse("export \"D:/review folder/original.json\" \"D:/review folder/copy.json\"", null);
        assertNull(incomplete.getContext().getCommand());
    }

    @Test void reportDistinguishesEvidencePortabilityFromCurrentSourceAvailability() {
        var ledger = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewLedgerV3.create("report", "Report",
                java.util.List.of(new ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewLedgerV3.TargetLane(
                        "main", "sfm", ".", "1".repeat(40), java.util.Optional.empty(), java.util.List.of("."), java.util.List.of(), true)));
        var prepared = new SFMReviewEvidenceExport.Prepared("sha256:test", SFMReviewEvidenceExport.Policy.VERIFIED_GIT_REFERENCES,
                1000, 700, 50, 1, 2, "", ledger);
        String report = SFMReviewEvidenceExportAction.report(new SFMReviewEvidenceExportService.Preview(
                Path.of("original.json"), Path.of("copy.json"), prepared, true));
        assertTrue(report.contains("Git-dependent documents: 2"));
        assertTrue(report.contains("Comment evidence self-contained: false"));
        assertTrue(report.contains("Current source coverage still requires the original sources"));
        assertTrue(report.contains("Original comments, approval identities and active review are unchanged"));
        assertTrue(report.contains("Original bytes: 1000"));
        assertTrue(report.contains("Output bytes: 700"));
        assertTrue(report.contains("Separate Git storage references: 0"));
    }
}
