package ca.teamdman.sfm.client.screen.color;

import java.util.ArrayList;
import java.util.List;

/** Pure mutable editing state. Callback/lifecycle policy belongs to the panel host. */
public final class SFMColorInputModel {
    public enum Resolution { EDITING, CONFIRMED, CANCELLED }

    private final SFMArgbColor initial;
    private final List<SFMArgbColor> recent = new ArrayList<>();
    private SFMArgbColor current;
    private SFMArgbColor.HexOrder hexOrder = SFMArgbColor.HexOrder.ARGB;
    private Resolution resolution = Resolution.EDITING;

    public SFMColorInputModel(SFMArgbColor initial, List<SFMArgbColor> recent) {
        this.initial = initial;
        this.current = initial;
        for (int i = recent.size() - 1; i >= 0; i--) addRecent(recent.get(i));
    }

    public SFMArgbColor initial() { return initial; }
    public SFMArgbColor current() { return current; }
    public List<SFMArgbColor> recent() { return List.copyOf(recent); }
    public SFMArgbColor.HexOrder hexOrder() { return hexOrder; }
    public Resolution resolution() { return resolution; }

    public void setCurrent(SFMArgbColor colour) {
        requireEditing();
        current = colour;
    }

    public void setHueSaturation(double hue, double saturation) {
        SFMArgbColor.Hsv hsv = current.toHsv();
        setCurrent(SFMArgbColor.fromHsv(current.alpha(), hue, saturation, hsv.value()));
    }

    public void setValue(double value) {
        SFMArgbColor.Hsv hsv = current.toHsv();
        setCurrent(SFMArgbColor.fromHsv(current.alpha(), hsv.hue(), hsv.saturation(), value));
    }

    public void adjustChannel(int channel, int delta) {
        setCurrent(switch (channel) {
            case 0 -> current.withAlpha(current.alpha() + delta);
            case 1 -> current.withRed(current.red() + delta);
            case 2 -> current.withGreen(current.green() + delta);
            case 3 -> current.withBlue(current.blue() + delta);
            default -> throw new IllegalArgumentException("Channel must be A/R/G/B index 0..3");
        });
    }

    public void toggleHexOrder() {
        requireEditing();
        hexOrder = hexOrder == SFMArgbColor.HexOrder.ARGB
                ? SFMArgbColor.HexOrder.RGBA
                : SFMArgbColor.HexOrder.ARGB;
    }

    public void applyHex(String text) { setCurrent(SFMArgbColor.parseHex(text, hexOrder)); }
    public void selectRecent(int index) { setCurrent(recent.get(index)); }
    public void reset() { setCurrent(initial); }

    public SFMArgbColor confirm() {
        requireEditing();
        resolution = Resolution.CONFIRMED;
        addRecent(current);
        return current;
    }

    public void cancel() {
        if (resolution == Resolution.EDITING) resolution = Resolution.CANCELLED;
    }

    private void addRecent(SFMArgbColor colour) {
        recent.remove(colour);
        recent.add(0, colour);
        while (recent.size() > 8) recent.remove(recent.size() - 1);
    }

    private void requireEditing() {
        if (resolution != Resolution.EDITING) throw new IllegalStateException("Colour input is already resolved");
    }
}
