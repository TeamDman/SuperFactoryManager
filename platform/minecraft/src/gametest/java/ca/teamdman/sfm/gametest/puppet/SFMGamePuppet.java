package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.gametest.SFMGameTestDefinition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class that declares a client game-puppet scenario through its
 * {@code public static void run(SFMGamePuppetHelper)} method.
 *
 * The canonical puppet id is derived from the class name, in the same way
 * {@link SFMGameTestDefinition#testName()} derives a test id from its
 * definition class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SFMGamePuppet {
    SFMGamePuppetViewportProfile viewportProfile() default SFMGamePuppetViewportProfile.CURRENT;

    /** Whole-puppet watchdog; individual actions retain their own timeouts. */
    int timeoutTicks() default 20 * 2 * 60;
}
