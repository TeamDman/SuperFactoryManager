package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import java.util.*;

/** Cross-check the linear coordinate scan against the existing coordinate contract. */
public class DecorationProbe {
    public static void main(String[] args) {
        String text = "a😀é\r\nb\n終";
        var offsets = new TreeSet<>(List.of(0, 1, 5, 7, 9, 10, 11, 14));
        var positions = SFMReleaseReviewDiffDecorations.positions(text, offsets);
        for (int offset : offsets) if (!positions.get(offset).equals(SFMTextDocumentRange.positionAtByteOffset(text, offset)))
            throw new AssertionError("Coordinate differs at " + offset);
        for (int invalid : List.of(2, 8, 15)) {
            try { SFMReleaseReviewDiffDecorations.positions(text, new TreeSet<>(List.of(invalid))); throw new AssertionError("Accepted " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
        String large = "é😀\r\n".repeat(60_000);
        var endpoints = new TreeSet<Integer>();
        for (int i = 0; i <= 60_000; i++) endpoints.add(i * 8);
        long started = System.nanoTime();
        var many = SFMReleaseReviewDiffDecorations.positions(large, endpoints);
        if (many.size() != 60_001 || !many.get(480_000).equals(SFMTextDocumentRange.positionAtByteOffset(large, 480_000)))
            throw new AssertionError("Long coordinate scan differs");
        System.out.println("PASS UTF-8/astral/CRLF boundaries and 60001 endpoints; elapsed_ms=" + (System.nanoTime()-started)/1_000_000);
    }
}
