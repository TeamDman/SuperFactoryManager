package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A read-only set of {@link ResourceIdentifier} objects.
 * Do NOT modify this after creation since the {@link this#referencedResourceTypes} will become inaccurate.
 */
public final class ResourceIdSet implements ASTNode {
    public static final ResourceIdSet EMPTY = new ResourceIdSet(List.of());
    public static final ResourceIdSet MATCH_ALL = new ResourceIdSet(List.of(ResourceIdentifier.MATCH_ALL));
    private final ResourceIdentifier<?, ?, ?>[] resourceIds;
    private @NotNull ResourceType<?,?,?> @Nullable [] referencedResourceTypes = null;

    public ResourceIdSet(ResourceIdentifier<?, ?, ?>[] resourceIds) {
        this.resourceIds = resourceIds;
    }

    public ResourceIdSet(Collection<ResourceIdentifier<?, ?, ?>> contents) {
        this(contents.toArray(new ResourceIdentifier[0]));
    }

    /**
     * See also: {@link ResourceLimits#getReferencedResourceTypes()}
     */
    public ResourceType<?,?,?>[] getReferencedResourceTypes() {
        if (referencedResourceTypes == null) {
            var found = new LinkedHashSet<>(SFMResourceTypes.getResourceTypeCount());
            for (ResourceIdentifier<?, ?, ?> resourceId : resourceIds) {
                found.add(resourceId.getResourceType());
            }
            //noinspection SuspiciousToArrayCall
            referencedResourceTypes = found.toArray(new ResourceType[0]);
        }
        return referencedResourceTypes;
    }

    public boolean couldMatchMoreThanOne() {
        return size() > 1 || stream().anyMatch(ResourceIdentifier::usesRegex);
    }

    public int size() {
        return resourceIds.length;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public @Nullable ResourceIdentifier<?, ?, ?> getMatchingFromStack(Object stack) {
        for (ResourceIdentifier<?, ?, ?> entry : resourceIds) {
            if (entry.matchesStack(stack)) {
                return entry;
            }
        }
        return null;
    }

    public boolean noneMatchStack(Object stack) {
        return getMatchingFromStack(stack) == null;
    }

    public boolean anyMatchResourceLocation(ResourceLocation location) {
        return this.stream().anyMatch(x -> x.matchesResourceLocation(location));
    }

    @Override
    public String toString() {
        return "ResourceIdSet{" +
               this.stream().map(ResourceIdentifier::toString).collect(Collectors.joining(", ")) +
               '}';
    }

    public String toStringCondensed() {
        return this.stream().map(ResourceIdentifier::toStringCondensed).collect(Collectors.joining(" OR "));
    }

    public Stream<ResourceIdentifier<?,?,?>> stream() {
        return Arrays.stream(resourceIds);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ResourceIdSet) obj;
        return Arrays.equals(this.resourceIds, that.resourceIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash((Object[]) resourceIds);
    }

}