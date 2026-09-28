package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraftforge.eventbus.api.IEventBus;


public class SFMBlocks {
    public static final SFMDeferredRegister<Block> REGISTERER =
            new SFMDeferredRegisterBuilder<Block>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.BLOCKS.registryKey())
                    .build();

    public static final SFMRegistryObject<Block, ManagerBlock> MANAGER
            =
            REGISTERER.register("manager", ManagerBlock::new);

    public static final SFMRegistryObject<Block,BufferBlock> BUFFER_BLOCK = REGISTERER.register(
            "buffer", () -> new BufferBlock(
                    BlockBehaviour.Properties.of()
                                    .destroyTime(1.5f)
                                    .sound(SoundType.METAL),
                            BufferBlockTier.MaxUnit
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledManagerBlock> TUNNELLED_MANAGER
            =
            REGISTERER.register("tunnelled_manager", TunnelledManagerBlock::new);

    public static final SFMRegistryObject<Block, PrintingPressBlock> PRINTING_PRESS
            =
            REGISTERER.register("printing_press", PrintingPressBlock::new);

    public static final SFMRegistryObject<Block, WaterTankBlock> WATER_TANK
            =
            REGISTERER.register("water_tank", WaterTankBlock::new);

    public static final SFMRegistryObject<Block, TestBarrelBlock> TEST_BARREL
            =
            REGISTERER.register("test_barrel", TestBarrelBlock::new);

    public static final SFMRegistryObject<Block, TestBarrelTankBlock> TEST_BARREL_TANK // TODO: remove this one
            =
            REGISTERER.register("test_barrel_tank", TestBarrelTankBlock::new);

    // TODO: pull out properties from other block constructors to enable mutating in inheriting class constructors

    public static final SFMRegistryObject<Block, CableBlock> CABLE =
            REGISTERER.register(
                    "cable",
                    () -> new CableBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, CableFacadeBlock> CABLE_FACADE =
            REGISTERER.register(
                    "cable_facade",
                    () -> new CableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableBlock> FANCY_CABLE =
            REGISTERER.register(
                    "fancy_cable",
                    () -> new FancyCableBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableFacadeBlock> FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "fancy_cable_facade",
                    () -> new FancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    // Tough variants
    public static final SFMRegistryObject<Block, ToughCableBlock> TOUGH_CABLE =
            REGISTERER.register(
                    "tough_cable",
                    () -> new ToughCableBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(10f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughCableFacadeBlock> TOUGH_CABLE_FACADE =
            REGISTERER.register(
                    "tough_cable_facade",
                    () -> new ToughCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(10f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughFancyCableBlock> TOUGH_FANCY_CABLE =
            REGISTERER.register(
                    "tough_fancy_cable",
                    () -> new ToughFancyCableBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(5f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughFancyCableFacadeBlock> TOUGH_FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "tough_fancy_cable_facade",
                    () -> new ToughFancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(5f)
                                    .sound(SoundType.METAL)
                    )
            );

    // Tunnelled variants
    public static final SFMRegistryObject<Block, TunnelledCableBlock> TUNNELLED_CABLE =
            REGISTERER.register(
                    "tunnelled_cable",
                    () -> new TunnelledCableBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledCableFacadeBlock> TUNNELLED_CABLE_FACADE =
            REGISTERER.register(
                    "tunnelled_cable_facade",
                    () -> new TunnelledCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledFancyCableBlock> TUNNELLED_FANCY_CABLE =
            REGISTERER.register(
                    "tunnelled_fancy_cable",
                    () -> new TunnelledFancyCableBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledFancyCableFacadeBlock> TUNNELLED_FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "tunnelled_fancy_cable_facade",
                    () -> new TunnelledFancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
