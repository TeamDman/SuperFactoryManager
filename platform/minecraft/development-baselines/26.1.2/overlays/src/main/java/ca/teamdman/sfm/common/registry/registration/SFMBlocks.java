package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.bus.api.IEventBus;


public class SFMBlocks {
    public static final SFMDeferredRegister<Block> REGISTERER =
            new SFMDeferredRegisterBuilder<Block>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.BLOCKS.registryKey())
                    .build();

    public static final SFMRegistryObject<Block, ManagerBlock> MANAGER
            =
            REGISTERER.register("manager", registryName ->
                    new ManagerBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                    ));

    public static final SFMRegistryObject<Block,BufferBlock> BUFFER_BLOCK = REGISTERER.register(
            "buffer", registryName -> new BufferBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                            .destroyTime(1.5f)
                            .sound(SoundType.METAL),
                    BufferBlockTier.MaxUnit
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledManagerBlock> TUNNELLED_MANAGER
            =
            REGISTERER.register("tunnelled_manager", registryName -> new TunnelledManagerBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(SFMWellKnownRegistries.BLOCKS.registryKey(), registryName))
            ));

    public static final SFMRegistryObject<Block, PrintingPressBlock> PRINTING_PRESS
            =
            REGISTERER.register("printing_press", registryName -> new PrintingPressBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));

    public static final SFMRegistryObject<Block, WaterTankBlock> WATER_TANK
            =
            REGISTERER.register("water_tank", registryName -> new WaterTankBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));

    public static final SFMRegistryObject<Block, TestBarrelBlock> TEST_BARREL
            =
            REGISTERER.register("test_barrel", registryName -> new TestBarrelBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));

    public static final SFMRegistryObject<Block, TestBarrelTankBlock> TEST_BARREL_TANK // TODO: remove this one
            =
            REGISTERER.register("test_barrel_tank", registryName -> new TestBarrelTankBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));

    // TODO: pull out properties from other block constructors to enable mutating in inheriting class constructors

    public static final SFMRegistryObject<Block, CableBlock> CABLE =
            REGISTERER.register(
                    "cable",
                    registryName -> new CableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, CableFacadeBlock> CABLE_FACADE =
            REGISTERER.register(
                    "cable_facade",
                    registryName -> new CableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableBlock> FANCY_CABLE =
            REGISTERER.register(
                    "fancy_cable",
                    registryName -> new FancyCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableFacadeBlock> FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "fancy_cable_facade",
                    registryName -> new FancyCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    // Tough variants
    public static final SFMRegistryObject<Block, ToughCableBlock> TOUGH_CABLE =
            REGISTERER.register(
                    "tough_cable",
                    registryName -> new ToughCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(10f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughCableFacadeBlock> TOUGH_CABLE_FACADE =
            REGISTERER.register(
                    "tough_cable_facade",
                    registryName -> new ToughCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(10f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughFancyCableBlock> TOUGH_FANCY_CABLE =
            REGISTERER.register(
                    "tough_fancy_cable",
                    registryName -> new ToughFancyCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(5f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughFancyCableFacadeBlock> TOUGH_FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "tough_fancy_cable_facade",
                    registryName -> new ToughFancyCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
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
                    registryName -> new TunnelledCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledCableFacadeBlock> TUNNELLED_CABLE_FACADE =
            REGISTERER.register(
                    "tunnelled_cable_facade",
                    registryName -> new TunnelledCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledFancyCableBlock> TUNNELLED_FANCY_CABLE =
            REGISTERER.register(
                    "tunnelled_fancy_cable",
                    registryName -> new TunnelledFancyCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledFancyCableFacadeBlock> TUNNELLED_FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "tunnelled_fancy_cable_facade",
                    registryName -> new TunnelledFancyCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
