package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;

/** Static program declaration. Session and executable selection are deliberately absent. */
public record TouchDisplayTerminalBinding(ClientProgramIdentity owner, BlockPos display, ResourceLocation channel, boolean input) {
    public static final SFMValueSchema SCHEMA = SFMValueSchema.object(Map.of(
            "x", required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            "y", required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            "z", required(SFMValueSchema.integer(Integer.MIN_VALUE, Integer.MAX_VALUE)),
            "channel", required(SFMValueSchema.string(1, 256)), "input", required(SFMValueSchema.bool())), false);

    public TouchDisplayTerminalBinding {
        Objects.requireNonNull(owner); Objects.requireNonNull(display); Objects.requireNonNull(channel);
        display = display.immutable();
    }

    public static TouchDisplayTerminalBinding parse(ClientProgramIdentity owner, SFMValue value) {
        if (SCHEMA.validate(value).isPresent()) throw new IllegalArgumentException("Invalid terminal declaration");
        var fields = ((SFMValue.ObjectValue) value).fields();
        return new TouchDisplayTerminalBinding(owner, new BlockPos(integer(fields, "x"), integer(fields, "y"), integer(fields, "z")),
                new ResourceLocation(((SFMValue.StringValue) fields.get("channel")).value()),
                ((SFMValue.BooleanValue) fields.get("input")).value());
    }

    public SFMValue value() {
        return SFMValue.object(Map.of("x", SFMValue.of(display.getX()), "y", SFMValue.of(display.getY()),
                "z", SFMValue.of(display.getZ()), "channel", SFMValue.of(channel.toString()), "input", SFMValue.of(input)));
    }

    private static int integer(Map<String, SFMValue> fields, String name) { return Math.toIntExact(((SFMValue.LongValue) fields.get(name)).value()); }
    private static SFMValueSchema.Field required(SFMValueSchema schema) { return SFMValueSchema.Field.required(schema); }
}
