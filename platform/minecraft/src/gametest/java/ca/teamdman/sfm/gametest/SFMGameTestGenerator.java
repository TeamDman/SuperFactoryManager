package ca.teamdman.sfm.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Classes annotated with this that extend {@link SFMGameTestGeneratorBase} will have their
 * {@link SFMGameTestGeneratorBase#generateTests} method invoked to produce game test definitions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SFMGameTestGenerator {
}
