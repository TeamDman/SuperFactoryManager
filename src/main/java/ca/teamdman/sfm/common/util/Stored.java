package ca.teamdman.sfm.common.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A BlockPos passed here may be stored as a reference beyond the scope of the method.
/// If passed a BlockPos.MutableBlockPos, expect pain and suffering.
/// The semantics of this are to prevent MutableBlockPos from being passed here.
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.PARAMETER })
public @interface Stored {}
