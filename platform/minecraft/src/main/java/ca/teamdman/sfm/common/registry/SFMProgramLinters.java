package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.compat.SFMMekanismCompat;
import ca.teamdman.sfm.common.compat.SFMModCompat;
import ca.teamdman.sfm.common.program.linting.*;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;

@SuppressWarnings("unused")
public class SFMProgramLinters {
    public static final ResourceKey<Registry<IProgramLinter>> REGISTRY_ID
            = SFMResourceLocation.createSFMRegistryKey("program_linters");

    private static final SFMDeferredRegister<IProgramLinter> REGISTERER =
            new SFMDeferredRegisterBuilder<IProgramLinter>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<IProgramLinter, EachInIOWithoutPatternProgramLinter>
            FLOW = REGISTERER.register(
            "flow",
            EachInIOWithoutPatternProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, ResourcesProgramLinter>
            RESOURCE = REGISTERER.register(
            "resources",
            ResourcesProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, LabelUsedInProgramButNotPresentProgramLinter>
            LABEL_USED_IN_PROGRAM_BUT_NOT_PRESENT_PROGRAM_LINTER = REGISTERER.register(
            "label_used_in_program_but_not_present",
            LabelUsedInProgramButNotPresentProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, LabelPresentButNotUsedProgramLinter>
            LABEL_PRESENT_BUT_NOT_USED_IN_PROGRAM_LINTER = REGISTERER.register(
            "label_present_but_not_used",
            LabelPresentButNotUsedProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, LabelNotConnectedProgramLinter>
            LABEL_NOT_CONNECTED_PROGRAM_LINTER = REGISTERER.register(
            "label_not_connected",
            LabelNotConnectedProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, RoundRobinProgramLinter>
            ROUND_ROBIN_PROGRAM_LINTER = REGISTERER.register(
            "round_robin",
            RoundRobinProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, IncompleteIOProgramLinter>
            INCOMPLETE_IO_PROGRAM_LINTER = REGISTERER.register(
            "incomplete_io",
            IncompleteIOProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, NoEligibleSlotsProgramLinter>
            NO_SLOT_STATEMENT_PROGRAM_LINTER = REGISTERER.register(
            "no_slot_statement",
            NoEligibleSlotsProgramLinter::new
    );

    public static final SFMRegistryObject<IProgramLinter, ForgetStatementNoMatchesProgramLinter>
            FORGET_STATEMENT_NO_MATCH_PROGRAM_LINTER = REGISTERER.register(
            "forget_statement_no_match",
            ForgetStatementNoMatchesProgramLinter::new
    );

    static {
        if (SFMModCompat.isMekanismLoaded()) {
            SFMMekanismCompat.registerProgramLinters(REGISTERER);
        }
    }

    public static SFMRegistryWrapper<IProgramLinter> registry() {

        return REGISTERER.registry();
    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
