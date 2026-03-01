package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.tutorial.chamber.Move1StackDirectTutorialTestChamberDefinition;
import ca.teamdman.sfm.common.tutorial.chamber.SFMTutorialTestChamberDefinition;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

public class SFMTutorialTestChambers {
    public static final ResourceLocation STARTING_CHAMBER_ID = new ResourceLocation(SFM.MOD_ID, "move_1_stack_direct");

    public static final ResourceKey<Registry<SFMTutorialTestChamberDefinition>> REGISTRY_ID
            = SFMResourceLocation.createSFMRegistryKey("tutorial_test_chamber");

    private static final SFMDeferredRegister<SFMTutorialTestChamberDefinition> REGISTERER =
            new SFMDeferredRegisterBuilder<SFMTutorialTestChamberDefinition>()
                    .namespace(SFM.MOD_ID)
                    .registry(REGISTRY_ID)
                    .createNewRegistry()
                    .build();

    public static final SFMRegistryObject<SFMTutorialTestChamberDefinition, Move1StackDirectTutorialTestChamberDefinition>
            MOVE_1_STACK_DIRECT = REGISTERER.register(
            "move_1_stack_direct",
            Move1StackDirectTutorialTestChamberDefinition::new
    );

    public static SFMRegistryWrapper<SFMTutorialTestChamberDefinition> registry() {
        return REGISTERER.registry();
    }

    public static void register(IEventBus bus) {
        REGISTERER.register(bus);
    }
}