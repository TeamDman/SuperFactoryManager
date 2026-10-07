package ca.teamdman.sfm.gametest;

{% if features.gametest_sides %}
import ca.teamdman.sfm.common.util.SFMDist;

{% endif %}
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
{% if features.gametest_sides %}
 * Classes annotated with this are discovered as SFM GameTests on the declared
 * physical distributions. The default supports both integrated-client and
 * dedicated-server runners.
{% else %}
 * Classes annotated with this will have their methods scanned
{% endif %}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SFMGameTest {
{% if features.gametest_sides %}
    SFMDist[] value() default {SFMDist.CLIENT, SFMDist.DEDICATED_SERVER};
{% endif %}
}
