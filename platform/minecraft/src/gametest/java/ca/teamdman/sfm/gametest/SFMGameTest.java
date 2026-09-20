package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.util.SFMDist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Classes annotated with this are discovered as SFM GameTests on the declared
 * physical distributions. The default supports both integrated-client and
 * dedicated-server runners.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SFMGameTest {
    SFMDist[] value() default {SFMDist.CLIENT, SFMDist.DEDICATED_SERVER};
}
