package ca.teamdman.sfm.gametest;

import net.minecraft.gametest.framework.TestFunction;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Marks only TestFunction objects created by SFM, including puppet-owned copies. */
public final class SFMGameTestFunctionOwnership {
    private static final Set<TestFunction> OWNED = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>())
    );

    private SFMGameTestFunctionOwnership() {}

    public static TestFunction mark(TestFunction function) {
        OWNED.add(function);
        return function;
    }

    public static boolean isOwned(TestFunction function) {
        return OWNED.contains(function);
    }
}
