package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.value.SFMValue;

import java.util.Objects;
import java.util.function.Consumer;

/** Shared validation immediately before a decoded value crosses an effect boundary. */
public final class SFMPacketValueDispatch {
    private SFMPacketValueDispatch() {
    }

    public enum Result {
        DISPATCHED,
        EFFECTS_DISABLED,
        UNSUPPORTED_CODEC_VERSION
    }

    public static Result dispatch(
            boolean effectsAllowed,
            SFMPacketValueEnvelope envelope,
            Consumer<SFMValue> receiver
    ) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(receiver, "receiver");
        if (!effectsAllowed) {
            return Result.EFFECTS_DISABLED;
        }
        return envelope.currentValue().map(value -> {
            receiver.accept(value);
            return Result.DISPATCHED;
        }).orElse(Result.UNSUPPORTED_CODEC_VERSION);
    }
}
