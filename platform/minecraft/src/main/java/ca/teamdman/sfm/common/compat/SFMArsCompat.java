package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.resourcetype.SourceResourceType;

public class SFMArsCompat {
    public static void registerResourceTypes(SFMDeferredRegister<ResourceType<?, ?, ?>> types) {
        types.register("source", SourceResourceType::new);
    }
}