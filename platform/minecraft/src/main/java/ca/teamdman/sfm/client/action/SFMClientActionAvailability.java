package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SFMClientActionAvailability<T> {
    private final @Nullable T target;
    private final @Nullable Component unavailableReason;

    private SFMClientActionAvailability(
            @Nullable T target,
            @Nullable Component unavailableReason
    ) {
        this.target = target;
        this.unavailableReason = unavailableReason;
    }

    public static <T> SFMClientActionAvailability<T> available(T target) {
        return new SFMClientActionAvailability<>(Objects.requireNonNull(target), null);
    }

    public static <T> SFMClientActionAvailability<T> unavailable(Component reason) {
        return new SFMClientActionAvailability<>(null, Objects.requireNonNull(reason));
    }

    public boolean isAvailable() {
        return target != null;
    }

    public T target() {
        if (target == null) {
            throw new IllegalStateException("Unavailable client action has no target");
        }
        return target;
    }

    public Component unavailableReason() {
        if (unavailableReason == null) {
            throw new IllegalStateException("Available client action has no unavailable reason");
        }
        return unavailableReason;
    }
}
