package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.program.linting.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

public class SFMLinters {

    public static IForgeRegistry<IProgramLinter> registry;

    public static void initialize() {
        prepareRegister(new EachInIOWithoutPatternProgramLinter(), "flow");
        prepareRegister(new ResourcesProgramLinter(), "resources");
        prepareRegister(new LabelUsedInProgramButNotPresentProgramLinter(), "label_used_in_program_but_not_present");
        prepareRegister(new LabelPresentButNotUsedProgramLinter(), "label_present_but_not_used");
        prepareRegister(new LabelNotConnectedProgramLinter(), "label_not_connected");
        prepareRegister(new RoundRobinProgramLinter(), "round_robin");
        prepareRegister(new IncompleteIOProgramLinter(), "incomplete_io");
        prepareRegister(new NoSlotStatementProgramLinter(), "no_slot_statement");
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