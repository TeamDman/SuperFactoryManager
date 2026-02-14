package ca.teamdman.sfm.common.util;


import org.jetbrains.annotations.NotNull;

public interface NonNullConsumer<T> {
    void accept(@NotNull T t);
}
