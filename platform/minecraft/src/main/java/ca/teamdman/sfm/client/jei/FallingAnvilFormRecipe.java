package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;

public class FallingAnvilFormRecipe extends FallingAnvilRecipe {
    public final PrintingPressRecipe PARENT;

    public FallingAnvilFormRecipe(PrintingPressRecipe parent) {
        this.PARENT = parent;
    }
}
