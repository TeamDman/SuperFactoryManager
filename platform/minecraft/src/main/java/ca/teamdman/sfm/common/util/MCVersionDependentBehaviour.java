package ca.teamdman.sfm.common.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// The SFM source code has variations between the different Minecraft versions it concurrently supports.
/// SFM uses a git strategy where there is a 1.19.2 branch, a 1.19.4 branch, etc.
///
/// To minimize the entropy/drift between these branches, we isolate the version-dependent code units.
/// We tag such code units with this annotation for easy identification.
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.METHOD,
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.LOCAL_VARIABLE,
        ElementType.TYPE_USE
})
public @interface MCVersionDependentBehaviour {
}
