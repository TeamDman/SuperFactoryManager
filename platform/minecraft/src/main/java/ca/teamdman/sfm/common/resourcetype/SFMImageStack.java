package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.image.SFMImageSnapshot;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** A zero-or-one image resource stack; the snapshot itself is immutable. */
public final class SFMImageStack {
    public static final SFMImageStack EMPTY = new SFMImageStack(null, SFMValue.nullValue());

    private final @Nullable SFMImageSnapshot snapshot;
    private final SFMValue interactionState;

    private SFMImageStack(@Nullable SFMImageSnapshot snapshot, SFMValue interactionState) {
        this.snapshot = snapshot;
        this.interactionState = Objects.requireNonNull(interactionState, "interactionState");
        SFMValueJsonCodec.encode(interactionState);
    }

    public static SFMImageStack of(SFMImageSnapshot snapshot) {
        return of(snapshot, SFMValue.nullValue());
    }

    /** State travels with the image so a display can commit both atomically. */
    public static SFMImageStack of(SFMImageSnapshot snapshot, SFMValue interactionState) {
        return new SFMImageStack(Objects.requireNonNull(snapshot, "snapshot"), interactionState);
    }

    public Optional<SFMImageSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public boolean isEmpty() {
        return snapshot == null;
    }

    public SFMValue interactionState() {
        return interactionState;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SFMImageStack that
               && Objects.equals(snapshot, that.snapshot)
               && interactionState.equals(that.interactionState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshot, interactionState);
    }

    @Override
    public String toString() {
        return snapshot == null ? "SFMImageStack[empty]" : "SFMImageStack[sha256=" + snapshot.sha256() + "]";
    }
}
