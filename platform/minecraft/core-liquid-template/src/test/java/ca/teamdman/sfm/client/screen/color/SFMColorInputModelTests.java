package ca.teamdman.sfm.client.screen.color;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SFMColorInputModelTests {
    private static final SFMArgbColor INITIAL = new SFMArgbColor(0xFF3366CC);

    @Test
    void editsChannelsHsvHexRecentAndReset() {
        SFMArgbColor recent = new SFMArgbColor(0x8044CC22);
        SFMColorInputModel model = new SFMColorInputModel(INITIAL, List.of(recent));
        model.adjustChannel(0, -16);
        assertEquals(239, model.current().alpha());
        model.setHueSaturation(0.5D, 1D);
        assertEquals(0.5D, model.current().toHsv().hue(), 0.01D);
        model.setValue(0.25D);
        assertEquals(0.25D, model.current().toHsv().value(), 0.01D);
        model.toggleHexOrder();
        model.applyHex("#10203040");
        assertEquals(new SFMArgbColor(0x40102030), model.current());
        model.selectRecent(0);
        assertEquals(recent, model.current());
        model.reset();
        assertEquals(INITIAL, model.current());
    }

    @Test
    void confirmationReturnsTypedValueAndPromotesBoundedRecent() {
        SFMColorInputModel model = new SFMColorInputModel(INITIAL, List.of(
                new SFMArgbColor(1), new SFMArgbColor(2), new SFMArgbColor(3), new SFMArgbColor(4),
                new SFMArgbColor(5), new SFMArgbColor(6), new SFMArgbColor(7), new SFMArgbColor(8)
        ));
        SFMArgbColor selected = new SFMArgbColor(0xCC778899);
        model.setCurrent(selected);
        assertEquals(selected, model.confirm());
        assertEquals(SFMColorInputModel.Resolution.CONFIRMED, model.resolution());
        assertEquals(selected, model.recent().get(0));
        assertEquals(8, model.recent().size());
        assertThrows(IllegalStateException.class, model::reset);
    }

    @Test
    void cancellationIsIdempotentAndDoesNotChangeCurrent() {
        SFMColorInputModel model = new SFMColorInputModel(INITIAL, List.of());
        model.cancel();
        model.cancel();
        assertEquals(SFMColorInputModel.Resolution.CANCELLED, model.resolution());
        assertEquals(INITIAL, model.current());
    }
}
