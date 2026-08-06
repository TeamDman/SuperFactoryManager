package ca.teamdman.sfm.client.screen.workspace;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;

/** Host-owned identity catalog for typed panel recipes. */
public final class SFMPanelReopenCatalog {
    private final IdentityHashMap<SFMScreenPanel, SFMPanelReopenRecipe> recipes = new IdentityHashMap<>();

    public void register(SFMScreenPanel panel, SFMPanelReopenRecipe recipe) {
        recipes.put(Objects.requireNonNull(panel), Objects.requireNonNull(recipe));
    }

    public boolean contains(SFMScreenPanel panel) {
        return recipes.containsKey(panel);
    }

    public Optional<SFMPanelReopenRecipe> recipeFor(SFMScreenPanel panel) {
        return Optional.ofNullable(recipes.get(panel));
    }

    /** Creates but does not attach a fresh panel, retaining the same immutable recipe. */
    public Optional<ReopenedPanel> reopen(SFMScreenPanel source) {
        SFMPanelReopenRecipe recipe = recipes.get(source);
        if (recipe == null) return Optional.empty();
        SFMScreenPanel reopened = recipe.reopen();
        if (reopened == source || recipes.containsKey(reopened)) {
            throw new IllegalStateException(
                    "Panel recipe must return a fresh unattached instance: " + recipe.sceneTypeId());
        }
        return Optional.of(new ReopenedPanel(reopened, recipe));
    }

    public void remove(SFMScreenPanel panel) {
        recipes.remove(panel);
    }

    public void clear() {
        recipes.clear();
    }

    public record ReopenedPanel(SFMScreenPanel panel, SFMPanelReopenRecipe recipe) {
        public ReopenedPanel {
            Objects.requireNonNull(panel);
            Objects.requireNonNull(recipe);
        }
    }
}
