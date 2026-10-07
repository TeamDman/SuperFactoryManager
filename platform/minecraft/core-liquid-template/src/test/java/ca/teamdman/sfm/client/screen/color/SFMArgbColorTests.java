package ca.teamdman.sfm.client.screen.color;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMArgbColorTests {
    @Test
    void argbAndRgbaHexRoundTripWithoutLosingAlpha() {
        SFMArgbColor colour = new SFMArgbColor(0x7F12ABEF);
        assertEquals("#7F12ABEF", colour.toHex(SFMArgbColor.HexOrder.ARGB));
        assertEquals("#12ABEF7F", colour.toHex(SFMArgbColor.HexOrder.RGBA));
        assertEquals(colour, SFMArgbColor.parseHex("7f12abef", SFMArgbColor.HexOrder.ARGB));
        assertEquals(colour, SFMArgbColor.parseHex("#12ABEF7F", SFMArgbColor.HexOrder.RGBA));
        assertEquals(new SFMArgbColor(0xFF12ABEF), SFMArgbColor.parseHex("12ABEF", SFMArgbColor.HexOrder.RGBA));
    }

    @Test
    void hsvPrimariesAndRoundTripAreStable() {
        assertEquals(new SFMArgbColor(0xFFFF0000), SFMArgbColor.fromHsv(255, 0D, 1D, 1D));
        assertEquals(new SFMArgbColor(0xFF00FF00), SFMArgbColor.fromHsv(255, 1D / 3D, 1D, 1D));
        assertEquals(new SFMArgbColor(0xFF0000FF), SFMArgbColor.fromHsv(255, 2D / 3D, 1D, 1D));
        SFMArgbColor colour = new SFMArgbColor(0xA13B91D0);
        SFMArgbColor.Hsv hsv = colour.toHsv();
        assertEquals(colour, SFMArgbColor.fromHsv(colour.alpha(), hsv.hue(), hsv.saturation(), hsv.value()));
    }

    @Test
    void invalidHexIsDiagnosableAndChannelsClamp() {
        assertThrows(IllegalArgumentException.class, () -> SFMArgbColor.parseHex("#12345", SFMArgbColor.HexOrder.ARGB));
        assertThrows(IllegalArgumentException.class, () -> SFMArgbColor.parseHex("#GG000000", SFMArgbColor.HexOrder.ARGB));
        assertEquals(new SFMArgbColor(0xFF00FF00), SFMArgbColor.of(999, -2, 999, 0));
    }
}
