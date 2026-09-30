package ca.teamdman.sfm.common.localization;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/// Used for automatic discovery during SFM datagen.
@Retention(value = RUNTIME)
@Target(value = FIELD)
public @interface SFMLocalizationDatagen {

}
