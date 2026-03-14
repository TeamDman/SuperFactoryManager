package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.eventbus.api.IEventBus;


public class SFMBlocks {
    public static final SFMDeferredRegister<Block> REGISTERER =
            new SFMDeferredRegisterBuilder<Block>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.BLOCKS.registryKey())
                    .build();

    public static final SFMRegistryObject<Block, ManagerBlock> MANAGER_BLOCK
            =
            REGISTERER.register("manager", ManagerBlock::new);

    public static final SFMRegistryObject<Block, BufferBlock> BUFFER_BLOCK =
            REGISTERER.register(
                    "buffer", () -> new BufferBlock(
                            BlockBehaviour.Properties
                                    .of(Material.PISTON)
                                    .destroyTime(1.5f)
                                    .sound(SoundType.METAL),
                            BufferBlockTier.MaxUnit
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledManagerBlock> TUNNELLED_MANAGER_BLOCK
            =
            REGISTERER.register("tunnelled_manager", TunnelledManagerBlock::new);

    public static final SFMRegistryObject<Block, PrintingPressBlock> PRINTING_PRESS_BLOCK
            =
            REGISTERER.register("printing_press", PrintingPressBlock::new);

    public static final SFMRegistryObject<Block, WaterTankBlock> WATER_TANK_BLOCK
            =
            REGISTERER.register("water_tank", WaterTankBlock::new);

    public static final SFMRegistryObject<Block, TestBarrelBlock> TEST_BARREL_BLOCK
            =
            REGISTERER.register("test_barrel", TestBarrelBlock::new);

    public static final SFMRegistryObject<Block, TestBarrelTankBlock> TEST_BARREL_TANK_BLOCK // TODO: remove this one
            =
            REGISTERER.register("test_barrel_tank", TestBarrelTankBlock::new);

    // TODO: pull out properties from other block constructors to enable mutating in inheriting class constructors

    public static final SFMRegistryObject<Block, CableBlock> CABLE_BLOCK =
            REGISTERER.register(
                    "cable",
                    () -> new CableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, CableFacadeBlock> CABLE_FACADE_BLOCK =
            REGISTERER.register(
                    "cable_facade",
                    () -> new CableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableBlock> FANCY_CABLE_BLOCK =
            REGISTERER.register(
                    "fancy_cable",
                    () -> new FancyCableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableFacadeBlock> FANCY_CABLE_FACADE_BLOCK =
            REGISTERER.register(
                    "fancy_cable_facade",
                    () -> new FancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
