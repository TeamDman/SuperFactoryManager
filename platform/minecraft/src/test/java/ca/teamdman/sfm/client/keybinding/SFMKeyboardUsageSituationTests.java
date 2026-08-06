package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMKeyboardUsageSituationTests {
    @Test
    void builtInsHaveStableDeepestFirstAncestry() {
        SFMKeyboardUsageSituationCatalog catalog = new SFMKeyboardUsageSituationCatalog(
                SFMKeyboardUsageSituations.builtIns());

        assertEquals(List.of(
                        SFMKeyboardUsageSituations.TERMINAL,
                        SFMKeyboardUsageSituations.DEFAULT,
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL),
                catalog.ancestry(SFMKeyboardUsageSituations.TERMINAL));
        assertEquals(List.of(
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL),
                catalog.ancestry(SFMKeyboardUsageSituations.WORKSPACE));
    }

    @Test
    void contributorParentsKeepDeclaredOrderAndOverlapWhenAContextCanContainBoth() {
        ResourceLocation contributor = id("contributor");
        ResourceLocation unrelated = id("unrelated");
        ResourceLocation sibling = id("sibling");
        ResourceLocation joined = id("joined");
        Map<ResourceLocation, SFMKeyboardUsageSituation> values = new LinkedHashMap<>(
                SFMKeyboardUsageSituations.builtIns());
        values.put(contributor, situation(SFMKeyboardUsageSituations.DEFAULT));
        values.put(unrelated, situation());
        values.put(sibling, situation());
        values.put(joined, situation(contributor, sibling));
        SFMKeyboardUsageSituationCatalog catalog = new SFMKeyboardUsageSituationCatalog(values);

        assertEquals(List.of(
                        contributor,
                        SFMKeyboardUsageSituations.DEFAULT,
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL),
                catalog.ancestry(contributor));
        assertTrue(catalog.canOverlap(contributor, SFMKeyboardUsageSituations.WORKSPACE));
        assertTrue(catalog.canOverlap(contributor, sibling));
        assertFalse(catalog.canOverlap(contributor, unrelated));
        SFMKeyboardUsageSituationCatalog.ActiveAncestry joinedAncestry = catalog.resolve(joined);
        assertEquals(List.of(
                        joined,
                        contributor,
                        sibling,
                        SFMKeyboardUsageSituations.DEFAULT,
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL),
                joinedAncestry.situations());
        assertEquals(1, joinedAncestry.depths().get(contributor));
        assertEquals(1, joinedAncestry.depths().get(sibling));
        assertEquals(2, joinedAncestry.depths().get(SFMKeyboardUsageSituations.DEFAULT));
    }

    @Test
    void ancestryCyclesAreRejected() {
        ResourceLocation first = id("first");
        ResourceLocation second = id("second");
        assertThrows(IllegalStateException.class, () ->
                new SFMKeyboardUsageSituationCatalog(Map.of(
                        first, situation(second),
                        second, situation(first))));
    }

    @Test
    void unknownContributedParentsAreRejected() {
        ResourceLocation child = id("child");
        ResourceLocation missing = id("missing");

        assertThrows(IllegalArgumentException.class, () ->
                new SFMKeyboardUsageSituationCatalog(Map.of(child, situation(missing))));
    }

    @Test
    void matchIdentityUsesHostIdentityAndFocusRevisions() {
        Object firstHost = new String("same");
        Object equalButDistinctHost = new String("same");
        var first = context(firstHost, 1, 2).matchIdentity();

        assertNotEquals(first, context(equalButDistinctHost, 1, 2).matchIdentity());
        assertNotEquals(first, context(firstHost, 2, 2).matchIdentity());
        assertNotEquals(first, context(firstHost, 1, 3).matchIdentity());
        assertEquals(first, context(firstHost, 1, 2).matchIdentity());
    }

    private static SFMKeyboardUsageContextSnapshot context(Object host, long workspace, long element) {
        return new SFMKeyboardUsageContextSnapshot(
                host,
                () -> true,
                null,
                id("element"),
                workspace,
                element,
                List.of(SFMKeyboardUsageSituations.DEFAULT,
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL));
    }

    private static SFMKeyboardUsageSituation situation(ResourceLocation... parents) {
        return new SFMKeyboardUsageSituation(
                Component.literal("Test"),
                Component.literal("Test situation"),
                List.of(parents));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("test", path);
    }
}
