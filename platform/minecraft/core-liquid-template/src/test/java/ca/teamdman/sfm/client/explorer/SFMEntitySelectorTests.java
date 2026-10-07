package ca.teamdman.sfm.client.explorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMEntitySelectorTests {
    @Test
    public void explorerSelectorsRoundTripWithoutImplicitNarrowing() {
        SFMEntitySelector selector = SFMEntitySelector.parse(
                SFMEntitySelector.Domain.EXPLORER,
                "difference(all,union(id(explorer-1),focused))"
        );
        assertEquals("difference(all,union(id(explorer-1),focused))", selector.canonical());
        assertEquals(selector, SFMEntitySelector.parse(selector.domain(), selector.canonical()));
        assertInstanceOf(SFMEntitySelector.Difference.class, selector.node());
        assertFalse(selector.isExactIdentity());
    }

    @Test
    public void exactAndNamedValuesUseStrictPercentEncoding() {
        SFMEntitySelector exact = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EXPLORER,
                "explorer with spaces"
        );
        assertEquals("id(explorer%20with%20spaces)", exact.canonical());
        assertTrue(exact.isExactIdentity());

        SFMEntitySelector named = SFMEntitySelector.parse(
                SFMEntitySelector.Domain.SELECTION,
                "name(review%20selection)"
        );
        assertEquals("name(review%20selection)", named.canonical());
    }

    @Test
    public void domainSpecificNodesFailClosed() {
        SFMParseException nameFailure = assertThrows(
                SFMParseException.class,
                () -> SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "name(wrong-domain)")
        );
        assertEquals("selector.name-unsupported", nameFailure.code());

        SFMParseException focusFailure = assertThrows(
                SFMParseException.class,
                () -> SFMEntitySelector.parse(SFMEntitySelector.Domain.SELECTION, "focused")
        );
        assertEquals("selector.focus-unsupported", focusFailure.code());
    }

    @Test
    public void malformedSelectorsFailClosed() {
        assertThrows(
                SFMParseException.class,
                () -> SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "union()")
        );
        assertThrows(
                SFMParseException.class,
                () -> SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "difference(all)")
        );
        assertThrows(
                SFMParseException.class,
                () -> SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "id(raw space)")
        );
    }

    @Test
    public void durableBoundariesRejectNoncanonicalAliasesThroughOneSharedParser() {
        SFMEntitySelector normalized = SFMEntitySelector.parse(
                SFMEntitySelector.Domain.EXPLORER,
                "UNION(all,focused)"
        );
        assertEquals("union(all,focused)", normalized.canonical());

        SFMParseException failure = assertThrows(
                SFMParseException.class,
                () -> SFMEntitySelector.parseCanonical(
                        SFMEntitySelector.Domain.EXPLORER,
                        "UNION(all,focused)"
                )
        );
        assertEquals("selector.noncanonical", failure.code());
        assertEquals(
                normalized,
                SFMEntitySelector.parseCanonical(
                        SFMEntitySelector.Domain.EXPLORER,
                        normalized.canonical()
                )
        );
    }
}
