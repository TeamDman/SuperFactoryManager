package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMExplorerCompactionPresetTests {
    @Test void deterministicPortableRoundTripPreservesInertExactPathsAndIndependentCopies() {
        var missing = SFMPath.parse("file:///C:/old/missing");
        var value = new SFMExplorerCompaction.Options(true, Map.of(missing, false));
        String json = SFMExplorerCompactionPreset.encode(value);
        var restored = SFMExplorerCompactionPreset.decode(json);
        assertEquals(value, restored);
        assertEquals(json, SFMExplorerCompactionPreset.encode(restored));
        var first = restored.override(List.of(missing), Optional.empty());
        assertTrue(first.overrides().isEmpty());
        assertEquals(Map.of(missing, false), restored.overrides());
        assertTrue(restored.allows(SFMPath.parse("file:///C:/renamed/missing")), "no retarget on rename");
    }
    @Test void rejectsMalformedUnknownDuplicateNestedAndOversizedInputsWithoutEvaluation() {
        String base = SFMExplorerCompactionPreset.encode(SFMExplorerCompaction.Options.defaults());
        for (String bad : List.of("{}", base + "{}", base.replace("true", "\"true\""),
                base.replace("\"overrides\":{}", "\"overrides\":[],\"unknown\":false"),
                base.replace("\"enabled\":true", "\"enabled\":true,\"enabled\":false"),
                base.replace("/1", "/2"), "[".repeat(10000), " ".repeat(SFMExplorerCompactionPreset.MAX_CHARACTERS + 1)))
            assertThrows(IllegalArgumentException.class, () -> SFMExplorerCompactionPreset.decode(bad));
    }
}
