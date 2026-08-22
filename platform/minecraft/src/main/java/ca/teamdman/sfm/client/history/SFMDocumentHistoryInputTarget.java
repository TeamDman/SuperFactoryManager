package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;

import java.util.Optional;

/** Pre-dispatch raw-input sink implemented by temporal document hosts. */
public interface SFMDocumentHistoryInputTarget {
    void recordDocumentRawInput(
            long clientTick,
            SFMDocumentHistoryContract.RawEventKind kind,
            String source,
            String code,
            Optional<String> text,
            int modifiers,
            boolean consumed,
            boolean delivered
    );
}
