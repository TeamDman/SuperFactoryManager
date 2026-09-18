package ca.teamdman.sfm.common.value;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Constructs the flat first-version value emitted by a Touch Display press. */
public final class SFMTouchValue {
    public static final String SCHEMA = "sfm:touch@1";

    private SFMTouchValue() {
    }

    public static SFMValue.ObjectValue press(
            ResourceLocation dimension,
            BlockPos position,
            Direction face,
            double u,
            double v,
            long contentRevision,
            SFMValue state
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(state, "state");

        LinkedHashMap<String, SFMValue> fields = new LinkedHashMap<>();
        fields.put("schema", SFMValue.of(SCHEMA));
        fields.put("dimension", SFMValue.of(dimension.toString()));
        fields.put("x", SFMValue.of(position.getX()));
        fields.put("y", SFMValue.of(position.getY()));
        fields.put("z", SFMValue.of(position.getZ()));
        fields.put("face", SFMValue.of(face.getName()));
        fields.put("u", unitCoordinate(u, "u"));
        fields.put("v", unitCoordinate(v, "v"));
        fields.put("action", SFMValue.of("press"));
        fields.put("contentRevision", SFMValue.of(contentRevision));
        fields.put("state", state);

        SFMValue.ObjectValue value = (SFMValue.ObjectValue) SFMValue.object(fields);
        SFMValueJsonCodec.encode(value);
        return value;
    }

    private static SFMValue unitCoordinate(double coordinate, String name) {
        if (!Double.isFinite(coordinate) || coordinate < 0.0d || coordinate > 1.0d) {
            throw new IllegalArgumentException("Touch " + name + " must be finite and within [0, 1]");
        }
        return SFMValue.of(coordinate);
    }
}
