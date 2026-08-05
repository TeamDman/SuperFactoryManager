package ca.teamdman.sfm.gametest.puppet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record SFMGamePuppetViewportSelection(Kind kind, SFMGamePuppetViewportVariant exact) {
    public enum Kind { DECLARED, PREFERRED, EXACT }

    public static SFMGamePuppetViewportSelection parse(String raw) {
        String value = raw == null || raw.isBlank() ? "declared" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("declared")) return new SFMGamePuppetViewportSelection(Kind.DECLARED, null);
        if (value.equals("preferred")) return new SFMGamePuppetViewportSelection(Kind.PREFERRED, null);
        if (!value.matches("[0-9]+x[0-9]+@(auto|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Invalid viewport selection '" + raw + "'; expected declared, preferred, or WIDTHxHEIGHT@auto|SCALE");
        }
        String[] halves = value.split("@", 2);
        String[] size = halves[0].split("x", 2);
        int scale = halves[1].equals("auto") ? 0 : Integer.parseInt(halves[1]);
        return new SFMGamePuppetViewportSelection(Kind.EXACT, new SFMGamePuppetViewportVariant(Integer.parseInt(size[0]), Integer.parseInt(size[1]), scale));
    }

    public List<SFMGamePuppetViewportVariant> resolve(SFMGamePuppetViewportProfile profile, int currentWidth, int currentHeight) {
        if (profile == SFMGamePuppetViewportProfile.CURRENT) {
            if (kind == Kind.EXACT && (exact.width() != currentWidth || exact.height() != currentHeight)) {
                throw new IllegalArgumentException("Puppet does not declare responsive viewport support");
            }
            return List.of(kind == Kind.EXACT ? exact : new SFMGamePuppetViewportVariant(currentWidth, currentHeight, 0));
        }
        if (kind == Kind.PREFERRED) return List.of(profile.preferred());
        if (kind == Kind.EXACT) {
            boolean sizeSupported = profile.acceptedExactSizes().stream().anyMatch(size -> size[0] == exact.width() && size[1] == exact.height());
            if (!sizeSupported) throw new IllegalArgumentException("Viewport " + exact.id() + " is not supported by profile " + profile);
            return List.of(exact);
        }
        List<SFMGamePuppetViewportVariant> result = new ArrayList<>();
        for (int[] size : profile.requestedSizes()) {
            result.add(new SFMGamePuppetViewportVariant(size[0], size[1], 0));
        }
        return List.copyOf(result);
    }
}
