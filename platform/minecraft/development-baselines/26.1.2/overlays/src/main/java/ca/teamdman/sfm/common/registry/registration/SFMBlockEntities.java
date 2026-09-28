package ca.teamdman.sfm.common.registry.registration;


import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;

@SuppressWarnings("DataFlowIssue")
public final class SFMBlockEntities {
    private static final SFMDeferredRegister<BlockEntityType<?>> REGISTERER =
            new SFMDeferredRegisterBuilder<BlockEntityType<?>>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.BLOCK_ENTITY_TYPES.registryKey())
                    .build();

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TestBarrelBlockEntity>>
            TEST_BARREL = REGISTERER.register(
            "test_barrel",
            registryName -> new BlockEntityType<>(
                    TestBarrelBlockEntity::new,
                    SFMBlocks.TEST_BARREL.get())
    );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ManagerBlockEntity>>
            MANAGER = REGISTERER.register(
            "manager",
            () -> new BlockEntityType<>(ManagerBlockEntity::new, SFMBlocks.MANAGER.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<BufferBlockEntity>>
            BUFFER = REGISTERER.register(
            "buffer",
            () -> new BlockEntityType<>(BufferBlockEntity::new, SFMBlocks.BUFFER_BLOCK.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledManagerBlockEntity>>
            TUNNELLED_MANAGER = REGISTERER.register(
            "tunnelled_manager",
            () -> new BlockEntityType<>(TunnelledManagerBlockEntity::new, SFMBlocks.TUNNELLED_MANAGER.get())
    );
    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<CableFacadeBlockEntity>>
            CABLE_FACADE = REGISTERER.register(
            "cable_facade",
            () -> new BlockEntityType<>(CableFacadeBlockEntity::new, SFMBlocks.CABLE_FACADE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<FancyCableFacadeBlockEntity>>
            FANCY_CABLE_FACADE = REGISTERER.register(
            "fancy_cable_facade",
            () -> new BlockEntityType<>(FancyCableFacadeBlockEntity::new, SFMBlocks.FANCY_CABLE_FACADE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<PrintingPressBlockEntity>>
            PRINTING_PRESS = REGISTERER.register(
            "printing_press",
            () -> new BlockEntityType<>(PrintingPressBlockEntity::new, SFMBlocks.PRINTING_PRESS.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<WaterTankBlockEntity>>
            WATER_TANK = REGISTERER.register(
            "water_tank",
            () -> new BlockEntityType<>(WaterTankBlockEntity::new, SFMBlocks.WATER_TANK.get())
    );


    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TestBarrelTankBlockEntity>>
            TEST_BARREL_TANK = REGISTERER.register(
            "test_barrel_tank",
            () -> new BlockEntityType<>(TestBarrelTankBlockEntity::new, SFMBlocks.TEST_BARREL.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledCableBlockEntity>>
            TUNNELLED_CABLE = REGISTERER.register(
            "tunnelled_cable",
            () -> new BlockEntityType<>(TunnelledCableBlockEntity::new, SFMBlocks.TUNNELLED_CABLE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledCableFacadeBlockEntity>>
            TUNNELLED_CABLE_FACADE = REGISTERER.register(
            "tunnelled_cable_facade",
            () -> new BlockEntityType<>(TunnelledCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_CABLE_FACADE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledFancyCableBlockEntity>>
            TUNNELLED_FANCY_CABLE = REGISTERER.register(
            "tunnelled_fancy_cable",
            () -> new BlockEntityType<>(TunnelledFancyCableBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledFancyCableFacadeBlockEntity>>
            TUNNELLED_FANCY_CABLE_FACADE = REGISTERER.register(
            "tunnelled_fancy_cable_facade",
            () -> new BlockEntityType<>(TunnelledFancyCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ToughCableFacadeBlockEntity>>
            TOUGH_CABLE_FACADE = REGISTERER.register(
            "tough_cable_facade",
            () -> new BlockEntityType<>(ToughCableFacadeBlockEntity::new, SFMBlocks.TOUGH_CABLE_FACADE.get())
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ToughFancyCableFacadeBlockEntity>>
            TOUGH_FANCY_CABLE_FACADE = REGISTERER.register(
            "tough_fancy_cable_facade",
            () -> new BlockEntityType<>(ToughFancyCableFacadeBlockEntity::new, SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get())
    );
}
