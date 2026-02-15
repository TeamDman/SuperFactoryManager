package ca.teamdman.sfm.client.integration;


import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.util.SFMASTUtils;
import ca.teamdman.sfml.ast.*;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import thelm.packagedauto.api.IPackagePattern;
import thelm.packagedauto.api.IRecipeInfo;
import thelm.packagedauto.integration.jei.category.PackageRecipeWrapper;

import java.lang.reflect.Field;

public class PackageRecipeTransferHandler implements IRecipeTransferHandler<ManagerContainerMenu> {
    private final IRecipeTransferHandlerHelper transferHelper;

    public PackageRecipeTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
        this.transferHelper = transferHelper;
    }

    public Class<ManagerContainerMenu> getContainerClass() {
        return ManagerContainerMenu.class;
    }

    public IRecipeTransferError transferRecipe(
            ManagerContainerMenu container,
            IRecipeLayout recipeLayout,
            EntityPlayer player,
            boolean maxTransfer,
            boolean doTransfer
    ) {
        IRecipeInfo recipeInfo;
        try {
            Field recipeField = recipeLayout.getClass().getDeclaredField("recipeWrapper");
            recipeField.setAccessible(true);
            Object recipe = recipeField.get(recipeLayout);
            if (!(recipe instanceof PackageRecipeWrapper)) {
                return this.transferHelper.createInternalError();
            }

            recipeInfo = ((PackageRecipeWrapper) recipe).recipe;
        } catch (Exception ignore) {
            return this.transferHelper.createInternalError();
        }

        if (doTransfer) {
            ResourceLocation itemResourceKey = new ResourceLocation("sfm", "item");

            int packager = 0;
            var sb = new StringBuilder();
            for (IPackagePattern recipe : recipeInfo.getPatterns()) {
                int slot = 0;
                for (var stack : recipe.getInputs()) {

                    OutputStatement outputStatement = SFMASTUtils.getOutputStatementForStack(
                            itemResourceKey,
                            SFMResourceTypes.ITEM,
                            stack,
                            "packager" + packager,
                            slot,
                            false,
                            null
                    );

                    sb.append(outputStatement.toStringPretty());
                    sb.append("\n");

                    slot++;
                }
                packager++;
                sb.append("\n");
            }

            for (int i = 0; i < packager; i++) {
                sb.append("FORGET\n\n");
                sb.append("INPUT FROM packager"  + i + "\n");
                sb.append("OUTPUT TO unpackager SLOTS "  + i + "\n");
            }


            SFMScreenChangeHelpers.showProgramEditScreen(sb.toString());
        }
        return null;

    }
}
