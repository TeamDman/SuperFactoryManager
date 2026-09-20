package ca.teamdman.sfm.client.action;

import java.util.Comparator;
import java.util.List;

/**
 * Explicit disposition for SFM-owned handlers that cannot all be converted
 * into one action per physical input.  Keeping this inventory in code makes
 * the remaining parameterized input surface reviewable instead of silently
 * treating every raw callback as a semantic shortcut.
 */
public final class SFMKeyboardNavigationInventory {
    public enum Disposition {
        SEMANTIC_ACTION,
        PARAMETERIZED_INPUT,
        VANILLA_WIDGET,
        LEGACY_EXEMPTION
    }

    public record Entry(String owner, String handler, Disposition disposition, String reason) {
        public Entry {
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner is required");
            if (handler == null || handler.isBlank()) throw new IllegalArgumentException("handler is required");
            if (disposition == null) throw new IllegalArgumentException("disposition is required");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        }

        public String line() {
            return String.join("|", owner, handler, disposition.name(), reason);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("ManagerScreen", "edit", Disposition.SEMANTIC_ACTION,
                    "sfm:manager/edit captures the active manager screen"),
            new Entry("SFMScreenMultiplexer", "workspace navigation", Disposition.SEMANTIC_ACTION,
                    "registered panel focus and maximize actions"),
            new Entry("SFMTerminalPanel", "keyboard and mouse input", Disposition.PARAMETERIZED_INPUT,
                    "terminal bytes, selection, and pointer coordinates are intrinsic input"),
            new Entry("SFMTextEditorV3Screen", "editor navigation and text input", Disposition.PARAMETERIZED_INPUT,
                    "document editing is a parameterized input surface"),
            new Entry("SFMDrawCanvasScreen", "canvas gestures and text input", Disposition.PARAMETERIZED_INPUT,
                    "canvas manipulation is a parameterized input surface"),
            new Entry("SFMCommandPaletteScreen", "search and choice navigation", Disposition.VANILLA_WIDGET,
                    "widgets own focus and semantic action submission"),
            new Entry("SFMFileExplorerPanel", "tree navigation", Disposition.PARAMETERIZED_INPUT,
                    "selection movement is contextual explorer input"),
            new Entry("SFMInputDiagnosticsScreen", "diagnostic key logging", Disposition.LEGACY_EXEMPTION,
                    "developer-only diagnostic surface has no product mutation"));

    private SFMKeyboardNavigationInventory() {
    }

    public static List<Entry> entries() {
        return ENTRIES.stream()
                .sorted(Comparator.comparing(Entry::owner).thenComparing(Entry::handler))
                .toList();
    }

    public static List<String> lines() {
        return entries().stream().map(Entry::line).toList();
    }
}
