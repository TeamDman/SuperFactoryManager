package ca.teamdman.sfm.client.integration;

import ca.teamdman.sfm.common.compat.SFMModCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;

@JEIPlugin
public class PackagedAutoJEIShortcut implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        if (SFMModCompat.isModLoaded("packagedauto")) {
            IRecipeTransferRegistry recipeTransferRegistry = registry.getRecipeTransferRegistry();
            IRecipeTransferHandlerHelper transferHelper = registry.getJeiHelpers().recipeTransferHandlerHelper();
            recipeTransferRegistry.addRecipeTransferHandler(
                    new PackageRecipeTransferHandler(transferHelper),
                    "packagedauto:package_recipe"
            );
        }
    }

}


