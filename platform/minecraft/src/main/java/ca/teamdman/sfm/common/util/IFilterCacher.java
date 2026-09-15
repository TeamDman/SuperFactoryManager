package ca.teamdman.sfm.common.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;

public interface IFilterCacher {
    void setPredicate(IntPredicate predicate);
    @Nullable
    IntPredicate getPredicate();
}
