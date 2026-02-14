package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.util.NonNullConsumer;
import org.jetbrains.annotations.Nullable;

public class SFMBlockCapabilityResult<CAP> {
    private final @Nullable CAP capability;

    private SFMBlockCapabilityResult(@Nullable CAP capability) {
        this.capability = capability;
    }

    public static <CAP> SFMBlockCapabilityResult<CAP> of(@Nullable CAP capability) {
        return new SFMBlockCapabilityResult<>(capability);
    }

    public static <CAP> SFMBlockCapabilityResult<CAP> empty() {
        return new SFMBlockCapabilityResult<>(null);
    }

    @Nullable
    public CAP inner() {
        return capability;
    }

    public CAP unwrap() {
        if (capability == null) {
            throw new IllegalStateException();
        }
        return capability;
    }

    public boolean isPresent() {
        return capability != null;
    }

    /// If this is not present, the listener is called immediately.
    public void addInvalidationListener(NonNullConsumer<SFMBlockCapabilityResult<CAP>> listener) {
        // We don't have capability invalidation in forge 1.12 yet :'(
    }
}
