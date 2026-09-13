package ca.teamdman.sfm.common.program;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns resources materialized for exactly one trigger execution. */
public final class ProgramEphemeralResourceOwner {
    private final Set<ProgramEphemeralResource> resources = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean freed;

    public <T extends ProgramEphemeralResource> T own(T resource) {
        Objects.requireNonNull(resource);
        if (freed) {
            throw new IllegalStateException("Cannot add a resource to a freed execution owner");
        }
        resources.add(resource);
        return resource;
    }

    public boolean release(ProgramEphemeralResource resource) {
        if (!resources.remove(resource)) {
            return false;
        }
        resource.free();
        return true;
    }

    public void free() {
        if (freed) {
            return;
        }
        freed = true;
        var ownedResources = List.copyOf(resources);
        resources.clear();
        ownedResources.forEach(ProgramEphemeralResource::free);
    }

    public int size() {
        return resources.size();
    }

    public boolean isFreed() {
        return freed;
    }
}
