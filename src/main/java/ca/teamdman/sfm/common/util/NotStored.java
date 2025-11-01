package ca.teamdman.sfm.common.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Signals that it's fine to pass a MutableBlockPos here.
/// A BlockPos passed here will not be stored as a reference beyond the method call.
/// BlockPos::asLong may be used which is fine because that doesn't persistently borrow the parameter by reference.
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER})
public @interface NotStored {
}
