package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.item.*;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.LabelLinter;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RegistryLinters {

    public static void initialize() {
       prepareRegister(new LabelLinter(), "labelgun");

        registerItemBlocks();
    }

    private static <T extends IProgramLinter> T prepareRegister(T linter, String name) {
        linter.setRegistryName(new ResourceLocation(SFM.MOD_ID, name));//.setTranslationKey(SFM.MOD_ID + "." + name);
        return register(linter);
    }

    private static <T extends IProgramLinter> T register(T item) {
        CommonProxy.registryPrimer.register(item);
        return item;
    }

}