package ca.teamdman.sfm;

import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;

/**
 * Allows for disabling certain performance tweaks for analysis and debugging purposes
 */
public class SFMPerformanceTweaks {
    public static final boolean OBJECT_POOL_VALIDATION = SFMEnvironmentUtils.isInIDE();
    public static final boolean OBJECT_POOL_ENABLED = true;
    public static final boolean REGEX_CACHE_ENABLED = true;
    public static final boolean REGEX_PREDICATE_OPTIMIZATION = true;
}
