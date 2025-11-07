package ca.teamdman.sfm.common.capability;

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
    public CAP capability() {
        return capability;
    }

    public boolean isPresent() {
        return capability != null;
    }
}
