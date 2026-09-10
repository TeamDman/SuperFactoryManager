package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewExactCoverageTests {
    @Test
    void aOneByteApprovalDoesNotApproveTheWholeChangedMethod() throws Exception {
        SFMReleaseReviewV1 original = fixture();
        SFMReleaseReviewV1.ReviewUnit unit = original.reviewUnits().stream()
                .filter(value -> value.id().equals("unit:src/Cafe.java:value")).findFirst().orElseThrow();
        int start = unit.afterRanges().get(0).startByte();
        var comment = original.reviewSession().comments().stream()
                .filter(value -> value.id().equals("human:approved-value")).findFirst().orElseThrow();
        var partial = new SFMReviewSessionV2.Comment(comment.id(), comment.text(), comment.provenance(),
                new SFMReviewSessionV2.CommittedReviewTarget(literal(original,
                        new Range(unit.afterDocumentRevisionId().orElseThrow(), start, start + 1))));
        SFMReleaseReviewV1 document = withComments(original, original.reviewSession().comments().stream()
                .map(value -> value.id().equals(comment.id()) ? partial : value).toList());

        assertEquals(List.of(unit.id()), SFMReleaseReviewKernel.query(document, "#approved").reviewUnitIds(),
                "A raw tag query may find the unit touched by the annotation");
        assertFalse(SFMReleaseReviewKernel.query(document, "effective(#approved)").reviewUnitIds().contains(unit.id()),
                "A one-byte approval must not approve the rest of the changed method");
        assertTrue(SFMReleaseReviewKernel.query(document, "remaining").reviewUnitIds().contains(unit.id()));
    }

    @Test
    void fullyApprovingAfterDoesNotApproveBeforeOrCountAsSuspended() throws Exception {
        var report = SFMReleaseReviewKernel.completion(fixture());
        var coverage = report.surfaceCoverage().stream().filter(value -> value.reviewUnitId().equals("unit:src/Cafe.java:value"))
                .findFirst().orElseThrow();
        assertEquals(List.of(new Range("1.19.2:before:src/Cafe.java", 35, 60)), coverage.remaining());
        assertEquals(0, report.approvedEffective());
        assertEquals(0, report.suspended(), "An exact partial approval is not stale or suspended");
    }

    @Test
    void independentOverlappingApprovalsUnionButDoNotFillOneByteGaps() throws Exception {
        String before = "1.19.2:before:src/Cafe.java";
        String after = "1.19.2:after:src/Cafe.java";
        var ranges = new ArrayList<>(List.of(new Range(before, 35, 60),
                new Range(after, 35, 45), new Range(after, 40, 45), new Range(after, 46, 60)));
        var partial = withApprovals(fixture(), ranges);
        var coverage = SFMReleaseReviewKernel.completion(partial).surfaceCoverage().stream()
                .filter(value -> value.reviewUnitId().equals("unit:src/Cafe.java:value")).findFirst().orElseThrow();
        assertEquals(List.of(new Range(after, 45, 46)), coverage.remaining());
        assertFalse(coverage.fullyApproved());
        assertEquals(49, coverage.approved().stream().mapToInt(range -> range.endByte() - range.startByte()).sum());
        ranges.add(new Range(after, 45, 46));
        var full = withApprovals(fixture(), ranges);
        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(full, "effective(#approved)").reviewUnitIds());
        assertEquals(List.of("unit:src/Other.java:file"), SFMReleaseReviewKernel.query(full, "remaining").reviewUnitIds());
    }

    @Test
    void zeroWidthAndWrongSnapshotApprovalsCannotEraseRemainingBytes() throws Exception {
        var document = withApprovals(fixture(), List.of(new Range("1.19.2:after:src/Cafe.java", 45, 45)));
        assertTrue(SFMReleaseReviewKernel.query(document, "effective(#approved)").reviewUnitIds().isEmpty());
        var report = SFMReleaseReviewKernel.completion(document);
        assertEquals(50, report.surfaceCoverage().stream().filter(value -> value.reviewUnitId().equals("unit:src/Cafe.java:value"))
                .findFirst().orElseThrow().remaining().stream().mapToInt(range -> range.endByte() - range.startByte()).sum());
    }

    @Test
    void differenceWitnessesMatchAnIndependentByteMembershipOracle() throws Exception {
        var document = fixture();
        String after = "1.19.2:after:src/Cafe.java";
        var random = new java.util.Random(0x5f_20260905L);
        for (int sample = 0; sample < 100; sample++) {
            boolean[] covered = new boolean[25];
            List<Range> approvals = new ArrayList<>();
            for (int interval = 0; interval < 10; interval++) {
                int left = random.nextInt(26), right = random.nextInt(26);
                int start = Math.min(left, right), end = Math.max(left, right);
                approvals.add(new Range(after, 35 + start, 35 + end));
                Arrays.fill(covered, start, end, true);
            }
            var coverage = SFMReleaseReviewCoverage.evaluate(document, approvals, List.of()).stream()
                    .filter(value -> value.reviewUnitId().equals("unit:src/Cafe.java:value")).findFirst().orElseThrow();
            for (int index = 0; index < 25; index++) {
                int offset = 35 + index;
                assertEquals(!covered[index], coverage.remaining().stream().anyMatch(range ->
                        range.documentRevisionId().equals(after) && range.startByte() <= offset && offset < range.endByte()),
                        "sample=" + sample + " byte=" + offset);
            }
        }
    }

    @Test
    void remainingExplorerExposesTheExactGapAsANavigablePinnedLeaf() throws Exception {
        var document = withApprovals(fixture(), List.of(new Range("1.19.2:before:src/Cafe.java", 35, 60),
                new Range("1.19.2:after:src/Cafe.java", 35, 45), new Range("1.19.2:after:src/Cafe.java", 46, 60)));
        var query = ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel.releaseQuery(document, "remaining");
        var row = query.root().children().stream().filter(value -> value.id().equals("release/unit/unit:src/Cafe.java:value"))
                .findFirst().orElseThrow();
        assertTrue(row.label().startsWith("Cafe.java · before [35,60) bytes → after [35,60) bytes · "),
                "the first visible text must distinguish hunks in the same file with exact byte coordinates");
        var gapGroup = row.children().stream().filter(value -> value.id().endsWith("/remaining")).findFirst().orElseThrow();
        assertTrue(gapGroup.label().contains("49/50 source bytes approved"));
        assertTrue(gapGroup.label().contains("before 25/25 bytes effectively approved"));
        assertTrue(gapGroup.label().contains("after 24/25 bytes effectively approved"));
        assertEquals(1, gapGroup.children().size());
        assertTrue(gapGroup.children().get(0).label().startsWith("Unreviewed after"));
        var leaf = gapGroup.children().get(0).leaf();
        assertEquals("1.19.2:after:src/Cafe.java", leaf.documentRevisionId().orElseThrow());
        assertEquals(new SFMReleaseReviewV1.Utf8Range(45, 46), leaf.targetRange().orElseThrow());
        assertFalse(leaf.missing());
        assertEquals(SFMReleaseReviewKernel.query(document, "remaining").surfaceCoverage(),
                SFMReleaseReviewKernel.completion(document).surfaceCoverage());
    }

    static SFMReleaseReviewV1 withApprovals(SFMReleaseReviewV1 document, List<Range> ranges) {
        var template = document.reviewSession().comments().stream()
                .filter(value -> value.id().equals("human:approved-value")).findFirst().orElseThrow();
        List<SFMReviewSessionV2.Comment> comments = new ArrayList<>();
        document.reviewSession().comments().forEach(comment -> comments.add(comment.id().equals(template.id())
                ? new SFMReviewSessionV2.Comment(comment.id(), "Annotation retained without approval for this test.",
                comment.provenance(), comment.target()) : comment));
        for (int index = 0; index < ranges.size(); index++) {
            comments.add(new SFMReviewSessionV2.Comment("test:approval:" + index, "#approved Test-only interval",
                    template.provenance(), new SFMReviewSessionV2.CommittedReviewTarget(literal(document, ranges.get(index)))));
        }
        return withComments(document, comments);
    }

    static SFMReviewSessionV1.LiteralUtf8Range literal(SFMReleaseReviewV1 document, Range range) {
        var source = document.reviewSession().revisionLanes().stream()
                .flatMap(lane -> java.util.stream.Stream.concat(lane.before().documents().stream(), lane.after().documents().stream()))
                .filter(value -> value.id().equals(range.documentRevisionId())).findFirst().orElseThrow();
        byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
        return new SFMReviewSessionV1.LiteralUtf8Range(source.id(), range.startByte(), range.endByte(), source.sha256(),
                SFMReviewSessionV1Kernel.sha256(Arrays.copyOfRange(bytes, range.startByte(), range.endByte())));
    }

    static SFMReleaseReviewV1 fixture() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return SFMReleaseReviewV1Codec.parse(Files.readString(candidate));
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Cannot locate release-review fixture");
    }

    static SFMReleaseReviewV1 withComments(SFMReleaseReviewV1 document, List<SFMReviewSessionV2.Comment> comments) {
        var session = document.reviewSession();
        var changedSession = new SFMReviewSessionV2(session.schema(), session.id(), session.title(),
                session.coordinateSystem(), session.revisionLanes(), comments, session.styleRules(), session.completionPolicy());
        return new SFMReleaseReviewV1(document.schema(), changedSession, document.repositoryBindings(),
                document.corpusDocuments(), document.reviewUnits(), document.selectorBindings(), document.migrationReports(),
                document.namedQueries(), document.resumeState(), document.producerGenerations(), document.completionAttestations());
    }
}
