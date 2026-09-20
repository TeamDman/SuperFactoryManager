package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** One immutable focus/situation snapshot used for an ordered input dispatch. */
public record SFMKeyboardUsageContextSnapshot(
        @Nullable Object originatingHost,
        BooleanSupplier originatingHostIsCurrent,
        @Nullable SFMWorkspacePanelId originatingPanelId,
        @Nullable ResourceLocation originatingElementId,
        long workspaceFocusRevision,
        long elementFocusRevision,
        List<ResourceLocation> activeSituations,
        Map<ResourceLocation, Integer> situationDepths
) {
    public SFMKeyboardUsageContextSnapshot {
        Objects.requireNonNull(originatingHostIsCurrent);
        activeSituations = List.copyOf(activeSituations);
        situationDepths = Map.copyOf(situationDepths);
        if (activeSituations.isEmpty()) {
            throw new IllegalArgumentException("A keyboard usage context needs at least one situation");
        }
        if (!situationDepths.keySet().containsAll(activeSituations)) {
            throw new IllegalArgumentException("Every active situation needs a specificity depth");
        }
    }

    public SFMKeyboardUsageContextSnapshot(
            @Nullable Object originatingHost,
            BooleanSupplier originatingHostIsCurrent,
            @Nullable SFMWorkspacePanelId originatingPanelId,
            @Nullable ResourceLocation originatingElementId,
            long workspaceFocusRevision,
            long elementFocusRevision,
            List<ResourceLocation> activeSituations
    ) {
        this(
                originatingHost,
                originatingHostIsCurrent,
                originatingPanelId,
                originatingElementId,
                workspaceFocusRevision,
                elementFocusRevision,
                activeSituations,
                indexedDepths(activeSituations));
    }

    public static SFMKeyboardUsageContextSnapshot global(
            @Nullable Object host,
            BooleanSupplier hostIsCurrent
    ) {
        return new SFMKeyboardUsageContextSnapshot(
                host,
                hostIsCurrent,
                null,
                null,
                0,
                0,
                List.of(SFMKeyboardUsageSituations.GLOBAL),
                Map.of(SFMKeyboardUsageSituations.GLOBAL, 0));
    }

    public static SFMKeyboardUsageContextSnapshot testing(ResourceLocation... situations) {
        return new SFMKeyboardUsageContextSnapshot(null, () -> true, null, null, 0, 0, List.of(situations));
    }

    public boolean isCurrent() {
        return originatingHostIsCurrent.getAsBoolean();
    }

    public int specificity(ResourceLocation situationId) {
        return situationDepths.getOrDefault(situationId, -1);
    }

    public MatchIdentity matchIdentity() {
        return new MatchIdentity(
                originatingHost,
                originatingPanelId,
                originatingElementId,
                workspaceFocusRevision,
                elementFocusRevision,
                activeSituations,
                situationDepths);
    }

    public SFMClientActionContext actionContext() {
        return new SFMClientActionContext(
                originatingHost,
                originatingHostIsCurrent,
                originatingPanelId);
    }

    public record MatchIdentity(
            @Nullable Object host,
            @Nullable SFMWorkspacePanelId panelId,
            @Nullable ResourceLocation elementId,
            long workspaceFocusRevision,
            long elementFocusRevision,
            List<ResourceLocation> situations,
            Map<ResourceLocation, Integer> situationDepths
    ) {
        public MatchIdentity {
            situations = List.copyOf(situations);
            situationDepths = Map.copyOf(situationDepths);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MatchIdentity that)) return false;
            return host == that.host
                    && workspaceFocusRevision == that.workspaceFocusRevision
                    && elementFocusRevision == that.elementFocusRevision
                    && Objects.equals(panelId, that.panelId)
                    && Objects.equals(elementId, that.elementId)
                    && situations.equals(that.situations)
                    && situationDepths.equals(that.situationDepths);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(host);
            result = 31 * result + Objects.hashCode(panelId);
            result = 31 * result + Objects.hashCode(elementId);
            result = 31 * result + Long.hashCode(workspaceFocusRevision);
            result = 31 * result + Long.hashCode(elementFocusRevision);
            result = 31 * result + situations.hashCode();
            return 31 * result + situationDepths.hashCode();
        }
    }

    private static Map<ResourceLocation, Integer> indexedDepths(List<ResourceLocation> situations) {
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < situations.size(); index++) {
            result.putIfAbsent(situations.get(index), index);
        }
        return result;
    }
}
