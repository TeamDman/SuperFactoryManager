package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;

import java.util.Optional;

/** Optional focused-panel adapter for copying an already-published semantic map into a snapshot. */
public interface SFMSymbolInspectionEvidenceSource {
    Optional<SFMSymbolInspectionSnapshot.SemanticEvidence> captureSymbolInspectionEvidence(
            SFMContextDocumentProjection document,
            SFMSymbolInspectionSnapshot.CapturedPoint point
    );
}
