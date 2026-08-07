package ca.teamdman.sfm.client.keybinding;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pure list state for the key-binding explorer.  Keeping this separate from
 * the screen makes sorting, filtering, and viewport preservation deterministic
 * and usable by headless tests.
 */
public final class SFMKeyBindingListModel {
    public enum SortColumn { NAME, BINDING_COUNT }
    public enum Direction { ASCENDING, DESCENDING }

    private final Function<ResourceLocation, List<SFMKeyBinding>> bindings;
    private List<ResourceLocation> actions = List.of();
    private String query = "";
    private ResourceLocation situationFilter;
    private SortColumn sortColumn = SortColumn.NAME;
    private Direction direction = Direction.ASCENDING;

    public SFMKeyBindingListModel(Function<ResourceLocation, List<SFMKeyBinding>> bindings) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    public void setActions(List<ResourceLocation> actions) {
        this.actions = List.copyOf(actions);
    }

    public List<ResourceLocation> actions() {
        return actions;
    }

    public String query() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query;
    }

    public Optional<ResourceLocation> situationFilter() {
        return Optional.ofNullable(situationFilter);
    }

    public void setSituationFilter(ResourceLocation situationFilter) {
        this.situationFilter = situationFilter;
    }

    public SortColumn sortColumn() {
        return sortColumn;
    }

    public Direction direction() {
        return direction;
    }

    public void toggleSort(SortColumn column) {
        if (sortColumn == column) {
            direction = direction == Direction.ASCENDING
                    ? Direction.DESCENDING
                    : Direction.ASCENDING;
        } else {
            sortColumn = column;
            direction = Direction.ASCENDING;
        }
    }

    public List<ResourceLocation> visibleActions(
            Function<ResourceLocation, String> title,
            Function<ResourceLocation, String> description
    ) {
        String needle = query.toLowerCase(Locale.ROOT).strip();
        Comparator<ResourceLocation> comparator = sortColumn == SortColumn.NAME
                ? Comparator.comparing((ResourceLocation id) -> title.apply(id).toLowerCase(Locale.ROOT))
                .thenComparing(ResourceLocation::toString)
                : Comparator.comparingInt((ResourceLocation id) -> filteredBindings(id).size())
                .thenComparing(id -> title.apply(id).toLowerCase(Locale.ROOT))
                .thenComparing(ResourceLocation::toString);
        if (direction == Direction.DESCENDING) comparator = comparator.reversed();
        return actions.stream()
                .filter(id -> needle.isEmpty()
                        || id.toString().toLowerCase(Locale.ROOT).contains(needle)
                        || title.apply(id).toLowerCase(Locale.ROOT).contains(needle)
                        || description.apply(id).toLowerCase(Locale.ROOT).contains(needle))
                .filter(id -> situationFilter == null || !filteredBindings(id).isEmpty())
                .sorted(comparator)
                .toList();
    }

    public List<SFMKeyBinding> filteredBindings(ResourceLocation actionId) {
        return bindings.apply(actionId).stream()
                .filter(binding -> situationFilter == null || binding.situationId().equals(situationFilter))
                .toList();
    }
}
