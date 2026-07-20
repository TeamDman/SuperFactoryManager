package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.label.LabelGunPlanTargets;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.function.Supplier;

/** The immutable result of one label-target discovery operation. */
public final class SFMLabelDiscoveryHandle {
    private final SFMBlockPosSetHandle positions;
    private final SFMBlockPosSetHandle skippedPositions;

    SFMLabelDiscoveryHandle(Supplier<LabelGunPlanTargets> loader) {

        LazyTargets targets = new LazyTargets(loader);
        positions = new SFMBlockPosSetHandle(() -> targets.get().positions());
        skippedPositions = new SFMBlockPosSetHandle(() -> targets.get().warnBecauseNoCableNeighbour());
    }

    @LuaFunction
    public final SFMBlockPosSetHandle positions() {

        return positions;
    }

    @LuaFunction
    public final SFMBlockPosSetHandle skippedPositions() {

        return skippedPositions;
    }

    private static final class LazyTargets {
        private final Supplier<LabelGunPlanTargets> loader;
        private LabelGunPlanTargets targets;

        private LazyTargets(Supplier<LabelGunPlanTargets> loader) {

            this.loader = loader;
        }

        private LabelGunPlanTargets get() {

            if (targets == null) {
                targets = loader.get();
            }
            return targets;
        }
    }
}
