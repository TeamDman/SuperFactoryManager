package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.ResourceLocationException;
{% when '26.1.2' %}
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.resources.ResourceKey;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
{% endcase %}
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// resourceTypeName resourceNamespace, resourceTypeName name, resource resourceNamespace, resource name
// sfm:item:minecraft:stone
public class ResourceIdentifier<STACK, ITEM, CAP> implements ASTNode, ToStringCondensed {

    public static final ResourceIdentifier<?, ?, ?> MATCH_ALL = new ResourceIdentifier<>(
            ".*",
            ".*"
    );
    private static final Map<ResourceIdentifier<?, ?, ?>, List<ResourceIdentifier<?, ?, ?>>> expansionCache = new Object2ObjectOpenHashMap<>();
    public final String resourceTypeNamespace;
    public final String resourceTypeName;
    public final String resourceNamespace;
    public final String resourceName;
    private final Predicate<String> resourceNamespacePredicate;
    private final Predicate<String> resourceNamePredicate;
    private @Nullable ResourceType<STACK, ITEM, CAP> resourceTypeCache = null;

    public ResourceIdentifier(
            String resourceTypeNamespace,
            String resourceTypeName,
            String resourceNamespace,
            String resourceName
    ) {
        // prevent crash on ctrl+space on "Gas::" (capital)
        // we could throw an exception and let it get bubbled to the user
        // but why bother when we know lowercasing it fixes it
        resourceTypeNamespace = resourceTypeNamespace.toLowerCase(Locale.ROOT);
        resourceTypeName = resourceTypeName.toLowerCase(Locale.ROOT);

        var check = List.of("fe", "rf", "energy", "power");
        if (resourceTypeNamespace.equals("sfm") && check.contains(resourceTypeName)) {
            resourceTypeName = "forge_energy";
        }
        this.resourceTypeNamespace = resourceTypeNamespace;
        this.resourceTypeName = resourceTypeName;
        this.resourceNamespace = resourceNamespace;
        this.resourceName = resourceName;
        this.resourceNamespacePredicate = RegexCache.buildPredicate(resourceNamespace);
        this.resourceNamePredicate = RegexCache.buildPredicate(resourceName);
    }

    public ResourceIdentifier(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation resourceTypeKey,
            ResourceLocation resourceKey
{% when '26.1.2' %}
            Identifier resourceTypeKey,
            Identifier resourceKey
{% endcase %}
    ) {
        this(
                resourceTypeKey.getNamespace(),
                resourceTypeKey.getPath(),
                resourceKey.getNamespace(),
                resourceKey.getPath()
        );
    }

    public ResourceIdentifier(
            ResourceKey<ResourceType<STACK,ITEM,CAP>> resourceTypeKey,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation resourceKey
{% when '26.1.2' %}
            Identifier resourceKey
{% endcase %}
    ) {
        this(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                resourceTypeKey.location().getNamespace(),
                resourceTypeKey.location().getPath(),
{% when '26.1.2' %}
                resourceTypeKey.identifier().getNamespace(),
                resourceTypeKey.identifier().getPath(),
{% endcase %}
                resourceKey.getNamespace(),
                resourceKey.getPath()
        );
    }

    public ResourceIdentifier(
            ResourceKey<ResourceType<STACK,ITEM,CAP>> resourceTypeKey,
            ResourceKey<?> resourceKey
    ) {
        this(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                resourceTypeKey.location().getNamespace(),
                resourceTypeKey.location().getPath(),
                resourceKey.location().getNamespace(),
                resourceKey.location().getPath()
{% when '26.1.2' %}
                resourceTypeKey.identifier().getNamespace(),
                resourceTypeKey.identifier().getPath(),
                resourceKey.identifier().getNamespace(),
                resourceKey.identifier().getPath()
{% endcase %}
        );
    }

    public ResourceIdentifier(String value) {
        this(SFM.MOD_ID, "item", ".*", value);
    }

    public ResourceIdentifier(
            String namespace,
            String value
    ) {
        this(SFM.MOD_ID, "item", namespace, value);
    }

    public ResourceIdentifier(
            String typeName,
            String resourceNamespace,
            String resourceName
    ) {
        this(SFM.MOD_ID, typeName, resourceNamespace, resourceName);
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean matchesResourceLocation(ResourceLocation stackId) {
{% when '26.1.2' %}
    public boolean matchesIdentifier(Identifier stackId) {
{% endcase %}
        return resourceNamePredicate.test(stackId.getPath()) && resourceNamespacePredicate.test(stackId.getNamespace());
    }

    public static <STACK, ITEM, CAP> ResourceIdentifier<STACK, ITEM, CAP> fromString(String string) {
        var parts = string.split(":");
        if (parts.length == 1) {
            return new ResourceIdentifier<>(parts[0]);
        } else if (parts.length == 2) {
            return new ResourceIdentifier<>(parts[0], parts[1]);
        } else if (parts.length == 3) {
            return new ResourceIdentifier<>(parts[0], parts[1], parts[2]);
        } else if (parts.length == 4) {
            return new ResourceIdentifier<>(parts[0], parts[1], parts[2], parts[3]);
        } else {
            throw new IllegalArgumentException("bad resource id: " + string);
        }
    }

    public void assertValid() throws IllegalArgumentException {
        try {
            if (RegexCache.isRegexPattern(this.resourceNamespace)) {
                Pattern.compile(this.resourceNamespace);
            }
            if (RegexCache.isRegexPattern(this.resourceName)) {
                Pattern.compile(this.resourceName);
            }
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid resource identifier pattern \""
                                               + this
                                               + "\" - "
                                               + e.getMessage());
        }
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public Optional<ResourceLocation> getLocation() {
{% when '26.1.2' %}
    public Optional<Identifier> getLocation() {
{% endcase %}
        try {
            return Optional.of(SFMResourceLocation.fromNamespaceAndPath(resourceNamespace, resourceName));
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        } catch (ResourceLocationException e) {
{% when '26.1.2' %}
        } catch (IdentifierException e) {
{% endcase %}
            return Optional.empty();
        }
    }

    public boolean matchesStack(Object other) {
        ResourceType<STACK, ITEM, CAP> resourceType = getResourceType();
        return resourceType != null && resourceType.matchesStack(this, other);
    }

    public List<ResourceIdentifier<STACK, ITEM, CAP>> expand() {
        try {
            if (this.getResourceType() == SFMResourceTypes.FORGE_ENERGY.get())
                return List.of(new ResourceIdentifier<>(
                        this.resourceTypeNamespace,
                        this.resourceTypeName,
                        "forge",
                        "energy"
                ));
            if (expansionCache.containsKey(this)) {
                //noinspection unchecked,rawtypes
                return (List<ResourceIdentifier<STACK, ITEM, CAP>>) (List) expansionCache.get(this);
            }
            ResourceType<STACK, ITEM, CAP> resourceType = getResourceType();
            if (resourceType == null) {
                // user may be using inspection on a resource type that doesn't exist
                return List.of(this);
            }
            List<ResourceIdentifier<STACK, ITEM, CAP>> rtn = resourceType.getRegistryKeys()
                    .stream()
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    .filter(this::matchesResourceLocation)
{% when '26.1.2' %}
                    .filter(this::matchesIdentifier)
{% endcase %}
                    .map(e -> new ResourceIdentifier<STACK, ITEM, CAP>(
                            resourceTypeNamespace,
                            resourceTypeName,
                            e.getNamespace(),
                            e.getPath()
                    )).toList();
            //noinspection unchecked,rawtypes
            expansionCache.put(this, (List) rtn);
            return rtn;
        } catch (NotImplementedException e) {
            // some resource types like energy don't actually have a registry
            // the check we do above for forge_energy doesn't easily work for mekanism energy because
            // the mekanism resource types aren't stored in deferred register fields
            // for now, lets just not crash the game at least
            return List.of(this);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        } catch (ResourceLocationException e) {
{% when '26.1.2' %}
        } catch (IdentifierException e) {
{% endcase %}
            // user may have ctrl+space inspection on an invalid resource identifier
            // item*::stone
            // the script should give a compile error but that doesn't prevent the inspection, so we catch here
            return List.of(this);
        }
    }

    public void setResourceTypeCache(@Nullable ResourceType<STACK, ITEM, CAP> resourceTypeCache) {
        this.resourceTypeCache = resourceTypeCache;
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public ResourceLocation getResourceTypeId() {
{% when '26.1.2' %}
    public Identifier getResourceTypeId() {
{% endcase %}
        return SFMResourceLocation.fromNamespaceAndPath(resourceTypeNamespace, resourceTypeName);
    }

    public @Nullable ResourceType<STACK, ITEM, CAP> getResourceType() {
        if (resourceTypeCache == null) {
            //noinspection unchecked
            setResourceTypeCache((ResourceType<STACK, ITEM, CAP>) SFMResourceTypes.fastLookup(getResourceTypeId()));
        }
        return resourceTypeCache;
    }

    @Override
    public String toString() {
        return resourceTypeNamespace + ":" + resourceTypeName + ":" + resourceNamespace + ":" + resourceName;
    }

    @Override
    public String toStringCondensed() {
        boolean isRegexNamespace = RegexCache.isRegexPattern(resourceNamespace);
        boolean isRegexNamespaceMatchAll = resourceNamespace.equals(".*");
        boolean isRegexName = RegexCache.isRegexPattern(resourceName);
        boolean isRegexNameMatchAll = resourceName.equals(".*");
        boolean isSFMMod = resourceTypeNamespace.equals(SFM.MOD_ID);
        boolean isItemType = resourceTypeName.equals("item");
        boolean isForgeEnergyType = resourceTypeName.equals("forge_energy") && getLocation()
                .filter(rl -> rl.equals(SFMResourceLocation.fromNamespaceAndPath("forge", "energy")))
                .isPresent();
        String resourceNamespaceAlias = isForgeEnergyType ? "fe" : resourceNamespace;
        boolean shouldQuoteResult = false;

        StringBuilder rtn = new StringBuilder();
        if (!isSFMMod) {
            rtn.append(resourceTypeNamespace).append(":");
        }
        if (!isItemType) {
            rtn.append(resourceTypeName).append(":");
        }
        if (isRegexNamespaceMatchAll) {
            if (!isItemType) {
                rtn.append(":");
            }
        } else {
            rtn.append(resourceNamespaceAlias).append(":");
            if (isRegexNamespace) {
                shouldQuoteResult = true;
            }
        }
        if (!isRegexNameMatchAll) {
            rtn.append(resourceName);
            if (isRegexName) {
                shouldQuoteResult = true;
            }
        }
        if (shouldQuoteResult) {
            return "\"" + rtn + "\"";
        } else {
            return rtn.toString();
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceIdentifier<?, ?, ?> that = (ResourceIdentifier<?, ?, ?>) o;
        return Objects.equals(resourceTypeNamespace, that.resourceTypeNamespace)
               && Objects.equals(resourceTypeName, that.resourceTypeName)
               && Objects.equals(resourceNamespace, that.resourceNamespace)
               && Objects.equals(resourceName, that.resourceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceTypeNamespace, resourceTypeName, resourceNamespace, resourceName);
    }

    public boolean usesRegex() {
        return RegexCache.isRegexPattern(resourceNamespace) || RegexCache.isRegexPattern(resourceName);
    }
}
