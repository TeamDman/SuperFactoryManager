package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.program.linting.FlowProgramLinter;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.program.linting.LabelLinter;
import ca.teamdman.sfm.common.program.linting.ResourcesProgramLinter;
import net.minecraft.util.ResourceLocation;

public class SFMLinters {

    public static void initialize() {
       prepareRegister(new LabelLinter(), "labelgun_linter");
       prepareRegister(new FlowProgramLinter(), "flow_linter");
       prepareRegister(new ResourcesProgramLinter(), "resources_linter");
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