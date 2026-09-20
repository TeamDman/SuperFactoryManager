package ca.teamdman.sfm.client.program;

import java.util.*;

/** Bounded plain-text projection shared by the panel and tests; never an execution input. */
public final class ClientProgramConsentReview {
    public enum View { SCOPE, SOURCE, PREVIOUS, DIFF, HISTORY }
    private ClientProgramConsentReview() {}

    public static Optional<ClientProgramConsentStore.Snapshot> previous(
            ClientProgramConsentStore store, ClientProgramConsentStore.Snapshot selected
    ) {
        return store.atLocation(selected.identity()).stream()
                .filter(s -> !s.identity().equals(selected.identity()) && s.evidence().isPresent())
                .max(Comparator.comparingLong(s -> s.evidence().orElseThrow().observedAt()));
    }

    public static List<String> lines(ClientProgramConsentStore.Snapshot selected,
                                     Optional<ClientProgramConsentStore.Snapshot> previous, View view) {
        var identity = selected.identity();
        List<String> lines = new ArrayList<>();
        if (view == View.SCOPE) {
            lines.add("Exact world: " + identity.world().serverEndpoint() + " / " + identity.world().worldId());
            lines.add("Dimension: " + identity.dimension());
            lines.add("Manager: " + identity.managerPosition().toShortString());
            lines.add("Runtime: " + identity.runtimeRevision());
            lines.add("Source SHA-256: " + identity.sourceSha256());
            lines.add("Binding SHA-256: " + identity.bindingSha256());
            lines.add("Requested capabilities:");
            identity.requestedCapabilities().stream().map(Object::toString).sorted().forEach(lines::add);
            selected.evidence().ifPresent(e -> {
                lines.add("Observed versions: " + new TreeMap<>(e.versions()));
                lines.add("Exact label bindings:");
                lines.addAll(List.of(e.bindings().split("\\R", -1)));
            });
            previous.ifPresent(old -> {
                lines.add("Capability changes from other remembered revision:");
                identity.requestedCapabilities().stream().filter(c -> !old.identity().requestedCapabilities().contains(c))
                        .sorted().forEach(c -> lines.add("+ " + c));
                old.identity().requestedCapabilities().stream().filter(c -> !identity.requestedCapabilities().contains(c))
                        .sorted().forEach(c -> lines.add("- " + c));
            });
        } else if (view == View.HISTORY) {
            for (var change : selected.history()) {
                var decision = change.decision();
                lines.add(change.capability() + " : " + decision.state());
                lines.add("At " + new Date(decision.decidedAt()) + "; expires "
                        + time(decision.expiresAt()) + "; retry " + time(decision.retryAfter()));
                lines.add("Versions: " + new TreeMap<>(change.versionContext()));
            }
            if (lines.isEmpty()) lines.add("No decisions yet.");
        } else {
            String current = selected.evidence().map(ClientProgramConsentStore.Evidence::source).orElse("No source evidence");
            String old = previous.flatMap(ClientProgramConsentStore.Snapshot::evidence)
                    .map(ClientProgramConsentStore.Evidence::source).orElse("");
            if (view == View.SOURCE) lines.addAll(numbered(current));
            else if (view == View.PREVIOUS) {
                lines.add(previous.isPresent() ? "Other remembered revision (not a new grant):" : "No other remembered revision.");
                lines.addAll(numbered(old));
            } else {
                lines.add("Changed segment: - remembered revision / + selected revision");
                lines.addAll(diff(old, current));
            }
        }
        return List.copyOf(lines);
    }

    private static String time(Long millis) { return millis == null ? "none" : new Date(millis).toString(); }
    private static List<String> numbered(String text) {
        String[] lines = text.split("\\R", -1);
        List<String> result = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) result.add((i + 1) + " | " + lines[i]);
        return result;
    }

    /** Linear changed-segment diff avoids quadratic work on the 64 KiB source bound. */
    public static List<String> diff(String before, String after) {
        String[] old = before.split("\\R", -1), next = after.split("\\R", -1);
        int prefix = 0, suffix = 0;
        while (prefix < old.length && prefix < next.length && old[prefix].equals(next[prefix])) prefix++;
        while (suffix < old.length - prefix && suffix < next.length - prefix
                && old[old.length - 1 - suffix].equals(next[next.length - 1 - suffix])) suffix++;
        List<String> lines = new ArrayList<>();
        if (prefix == old.length && prefix == next.length) return List.of("Sources are identical.");
        if (prefix > 0) lines.add("  " + prefix + " unchanged leading lines");
        for (int i = prefix; i < old.length - suffix; i++) lines.add("- " + (i + 1) + " | " + old[i]);
        for (int i = prefix; i < next.length - suffix; i++) lines.add("+ " + (i + 1) + " | " + next[i]);
        if (suffix > 0) lines.add("  " + suffix + " unchanged trailing lines");
        return List.copyOf(lines);
    }
}
