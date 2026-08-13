package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionEngine;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/** Pure pre-publication bounds shared with the Rust {@code sfm} control protocol. */
final class SFMExplorerControlBounds {
    /** Leaves ample room below Vox's 1 MiB frame bound for compact-wire structure and optional evidence. */
    static final int MAX_REQUIRED_RESPONSE_ESTIMATE_BYTES = 384 * 1024;
    static final int MAX_VISIBLE_PATH_ESTIMATE_BYTES = 128 * 1024;
    static final int MAX_TARGET_MESSAGE_BYTES = 256;
    static final int MAX_FEEDBACK_MESSAGE_BYTES = 512;
    private static final int RESPONSE_BASE_ESTIMATE_BYTES = 64 * 1024;
    private static final int TARGET_STRUCTURE_ESTIMATE_BYTES = 256;
    private static final int COLLECTION_ENTRY_ESTIMATE_BYTES = 8;

    private SFMExplorerControlBounds() {
    }

    static void validatePrepared(SFMExplorerActionEngine.PreparedAction prepared) {
        Objects.requireNonNull(prepared, "prepared");
        int predictedTargets = prepared.createsExplorerOnPublish()
                ? 1
                : prepared.preparedTargets().size();
        if (predictedTargets > SFMClientControlServer.MAX_EXPLORER_TARGET_RESULTS) {
            throw new IllegalArgumentException("explorer action would return too many target results");
        }

        long responseEstimate = RESPONSE_BASE_ESTIMATE_BYTES;

        if (prepared.createsExplorerOnPublish()) {
            SFMExplorerActionRequest.RootAdd add = (SFMExplorerActionRequest.RootAdd) prepared.request().operation();
            validateCanonicalPath(add.path(), "created explorer root");
            responseEstimate += TARGET_STRUCTURE_ESTIMATE_BYTES + estimatedTextBytes(add.path().canonical());
            validateResponseEstimate(responseEstimate);
            return;
        }

        for (SFMExplorerActionEngine.PreparedTarget target : prepared.preparedTargets()) {
            validateBoundedText(
                    target.id().value(),
                    1,
                    SFMClientControlServer.MAX_EXPLORER_SETTING_BYTES,
                    "explorer id"
            );
            validateSnapshot(target.snapshot(), prepared.request().operation());
            responseEstimate += TARGET_STRUCTURE_ESTIMATE_BYTES;
            responseEstimate += estimatedTextBytes(target.id().value());
            responseEstimate += estimatedTextBytes(target.snapshot().location().canonical());
            for (SFMPath root : prospectiveRoots(target.snapshot(), prepared.request().operation())) {
                responseEstimate += COLLECTION_ENTRY_ESTIMATE_BYTES + estimatedTextBytes(root.canonical());
            }
            validateResponseEstimate(responseEstimate);
        }
    }

    static void validateSnapshot(
            SFMExplorerSession.Snapshot snapshot,
            SFMExplorerActionRequest.Operation operation
    ) {
        int prospectiveRootCount = snapshot.roots().size();
        if (operation instanceof SFMExplorerActionRequest.RootAdd add
                && !snapshot.roots().contains(add.path())) {
            prospectiveRootCount++;
        }
        if (prospectiveRootCount > SFMClientControlServer.MAX_EXPLORER_ROOTS_PER_TARGET) {
            throw new IllegalArgumentException("explorer action would exceed the per-target root bound");
        }
        validateBoundedText(
                snapshot.location().canonical(),
                1,
                SFMClientControlServer.MAX_EXPLORER_PATH_BYTES,
                "explorer location expression"
        );
        snapshot.roots().forEach(root -> validateCanonicalPath(root, "explorer root"));
        if (operation instanceof SFMExplorerActionRequest.RootAdd add) {
            validateCanonicalPath(add.path(), "prospective explorer root");
        }
    }

    static boolean isBoundedCanonicalPath(String value) {
        try {
            SFMPath parsed = SFMPath.parse(value);
            return parsed.canonical().equals(value)
                    && boundedText(value, 1, SFMClientControlServer.MAX_EXPLORER_PATH_BYTES);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static VisiblePathBudget visiblePathBudget() {
        return new VisiblePathBudget(MAX_VISIBLE_PATH_ESTIMATE_BYTES);
    }

    static String boundedEvidenceText(String value) {
        return boundedEvidenceText(value, SFMClientControlServer.MAX_EXPLORER_EVIDENCE_TEXT_BYTES);
    }

    static String boundedEvidenceText(String value, int maximumBytes) {
        Objects.requireNonNull(value, "value");
        if (maximumBytes < 0 || maximumBytes > SFMClientControlServer.MAX_EXPLORER_EVIDENCE_TEXT_BYTES) {
            throw new IllegalArgumentException("evidence text byte bound is invalid");
        }
        StringBuilder result = new StringBuilder(Math.min(
                value.length(),
                maximumBytes
        ));
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) codePoint = ' ';
            String encoded = new String(Character.toChars(codePoint));
            int encodedBytes = encoded.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + encodedBytes > maximumBytes) break;
            result.append(encoded);
            bytes += encodedBytes;
        }
        return result.toString();
    }

    static final class VisiblePathBudget {
        private int remainingBytes;

        private VisiblePathBudget(int remainingBytes) {
            this.remainingBytes = remainingBytes;
        }

        boolean tryInclude(String canonicalPath) {
            if (!isBoundedCanonicalPath(canonicalPath)) return false;
            int estimatedBytes = COLLECTION_ENTRY_ESTIMATE_BYTES + estimatedTextBytes(canonicalPath);
            if (estimatedBytes > remainingBytes) return false;
            remainingBytes -= estimatedBytes;
            return true;
        }
    }

    private static void validateCanonicalPath(SFMPath path, String label) {
        String canonical = Objects.requireNonNull(path, "path").canonical();
        validateBoundedText(canonical, 1, SFMClientControlServer.MAX_EXPLORER_PATH_BYTES, label);
        if (!SFMPath.parse(canonical).equals(path)) {
            throw new IllegalArgumentException(label + " is not canonical");
        }
    }

    private static Collection<SFMPath> prospectiveRoots(
            SFMExplorerSession.Snapshot snapshot,
            SFMExplorerActionRequest.Operation operation
    ) {
        if (!(operation instanceof SFMExplorerActionRequest.RootAdd add)
                || snapshot.roots().contains(add.path())) {
            return snapshot.roots();
        }
        TreeSet<SFMPath> roots = new TreeSet<>(snapshot.roots());
        roots.add(add.path());
        return new ArrayList<>(roots);
    }

    private static int estimatedTextBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validateResponseEstimate(long responseEstimate) {
        if (responseEstimate > MAX_REQUIRED_RESPONSE_ESTIMATE_BYTES) {
            throw new IllegalArgumentException(
                    "explorer action would exceed the aggregate control response budget"
            );
        }
    }

    private static void validateBoundedText(String value, int minimum, int maximum, String label) {
        if (!boundedText(value, minimum, maximum)) {
            throw new IllegalArgumentException(label + " is outside the control protocol bounds");
        }
    }

    private static boolean boundedText(String value, int minimum, int maximum) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return bytes >= minimum
                && bytes <= maximum
                && value.codePoints().noneMatch(Character::isISOControl);
    }
}
