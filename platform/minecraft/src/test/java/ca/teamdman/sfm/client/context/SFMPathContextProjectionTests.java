package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMPathContextProjectionTests {
    @Test
    void pathProjectionPreservesCanonicalPathRootAndRole() {
        SFMPath root = SFMPath.parse("file:///D:/repo/");
        SFMPath path = SFMPath.parse("file:///D:/repo/src/A.java");
        SFMContextPathProjection projection = new SFMContextPathProjection(
                path,
                Optional.of(root),
                SFMContextPathProjection.NodeKind.FILE,
                "explorer-navigation"
        );

        assertEquals("file:///D:/repo/src/A.java", projection.path().canonical());
        assertEquals(Optional.of(root), projection.authorizedRoot());
        assertEquals(SFMContextPathProjection.NodeKind.FILE, projection.nodeKind());
        assertEquals("explorer-navigation", projection.role());
    }

    @Test
    void directorySeedsItselfWhileFileSeedsItsParentWithinAuthority() {
        SFMPath authorizedRoot = SFMPath.parse("file:///D:/repo/");
        SFMPath directory = SFMPath.parse("file:///D:/repo/src");
        SFMPath file = SFMPath.parse("file:///D:/repo/src/A.java");

        SFMContextPathProjection directoryProjection = new SFMContextPathProjection(
                directory,
                Optional.of(authorizedRoot),
                SFMContextPathProjection.NodeKind.DIRECTORY,
                "selected"
        );
        SFMContextPathProjection fileProjection = new SFMContextPathProjection(
                file,
                Optional.of(authorizedRoot),
                SFMContextPathProjection.NodeKind.FILE,
                "selected"
        );

        assertEquals(Optional.of(directory), directoryProjection.defaultSearchRoot());
        assertEquals(Optional.of(directory), fileProjection.defaultSearchRoot());
    }

    @Test
    void rootDerivationNeverGuessesShapeOrEscapesTheAuthorizedRoot() {
        SFMPath authorizedRoot = SFMPath.parse("file:///D:/repo/src/");
        SFMPath outside = SFMPath.parse("file:///D:/other/A.java");
        SFMPath inside = SFMPath.parse("file:///D:/repo/src/A.java");

        assertTrue(new SFMContextPathProjection(
                inside,
                Optional.of(authorizedRoot),
                SFMContextPathProjection.NodeKind.UNKNOWN,
                "selected"
        ).defaultSearchRoot().isEmpty());
        assertTrue(new SFMContextPathProjection(
                inside,
                Optional.empty(),
                SFMContextPathProjection.NodeKind.FILE,
                "selected"
        ).defaultSearchRoot().isEmpty());
        assertTrue(new SFMContextPathProjection(
                outside,
                Optional.of(authorizedRoot),
                SFMContextPathProjection.NodeKind.FILE,
                "selected"
        ).defaultSearchRoot().isEmpty());
    }

    @Test
    void originIdentityDoesNotDependOnProjectedPath() {
        SFMContextOriginId id = new SFMContextOriginId("explorer", "17", "navigation");
        SFMContextPathProjection before = new SFMContextPathProjection(
                SFMPath.parse("file:///D:/repo/A.java"), Optional.empty(), "selected"
        );
        SFMContextPathProjection after = new SFMContextPathProjection(
                SFMPath.parse("file:///D:/repo/B.java"), Optional.empty(), "selected"
        );

        assertEquals(id, new SFMContextContribution(id, SFMContextGenerationEvidence.INITIAL, before).originId());
        assertEquals(id, new SFMContextContribution(id, new SFMContextGenerationEvidence(1, 0, 1, 1), after).originId());
        assertThrows(IllegalArgumentException.class, () -> new SFMContextOriginId("explorer", "17", "bad/id"));
    }
}
