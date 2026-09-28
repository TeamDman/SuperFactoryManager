package ca.teamdman.sfm.common.registry.registration;


import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;

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
            () -> BlockEntityType.Builder
                    .of(TestBarrelBlockEntity::new, SFMBlocks.TEST_BARREL.get())
                    .build(null)
    );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ManagerBlockEntity>>
            MANAGER = REGISTERER.register(
            "manager",
            () -> BlockEntityType.Builder
                    .of(ManagerBlockEntity::new, SFMBlocks.MANAGER.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<BufferBlockEntity>>
            BUFFER = REGISTERER.register(
            "buffer",
            () -> BlockEntityType.Builder
                    .of(BufferBlockEntity::new, SFMBlocks.BUFFER_BLOCK.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledManagerBlockEntity>>
            TUNNELLED_MANAGER = REGISTERER.register(
            "tunnelled_manager",
            () -> BlockEntityType.Builder
                    .of(TunnelledManagerBlockEntity::new, SFMBlocks.TUNNELLED_MANAGER.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<CableFacadeBlockEntity>>
            CABLE_FACADE = REGISTERER.register(
            "cable_facade",
            () -> BlockEntityType.Builder
                    .of(CableFacadeBlockEntity::new, SFMBlocks.CABLE_FACADE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<FancyCableFacadeBlockEntity>>
            FANCY_CABLE_FACADE = REGISTERER.register(
            "fancy_cable_facade",
            () -> BlockEntityType.Builder
                    .of(FancyCableFacadeBlockEntity::new, SFMBlocks.FANCY_CABLE_FACADE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<PrintingPressBlockEntity>>
            PRINTING_PRESS = REGISTERER.register(
            "printing_press",
            () -> BlockEntityType.Builder
                    .of(PrintingPressBlockEntity::new, SFMBlocks.PRINTING_PRESS.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<WaterTankBlockEntity>>
            WATER_TANK = REGISTERER.register(
            "water_tank",
            () -> BlockEntityType.Builder
                    .of(WaterTankBlockEntity::new, SFMBlocks.WATER_TANK.get())
                    .build(null)
    );


    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TestBarrelTankBlockEntity>>
            TEST_BARREL_TANK = REGISTERER.register(
            "test_barrel_tank",
            () -> BlockEntityType.Builder
                    .of(TestBarrelTankBlockEntity::new, SFMBlocks.TEST_BARREL.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledCableBlockEntity>>
            TUNNELLED_CABLE = REGISTERER.register(
            "tunnelled_cable",
            () -> BlockEntityType.Builder
                    .of(TunnelledCableBlockEntity::new, SFMBlocks.TUNNELLED_CABLE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledCableFacadeBlockEntity>>
            TUNNELLED_CABLE_FACADE = REGISTERER.register(
            "tunnelled_cable_facade",
            () -> BlockEntityType.Builder
                    .of(TunnelledCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_CABLE_FACADE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledFancyCableBlockEntity>>
            TUNNELLED_FANCY_CABLE = REGISTERER.register(
            "tunnelled_fancy_cable",
            () -> BlockEntityType.Builder
                    .of(TunnelledFancyCableBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledFancyCableFacadeBlockEntity>>
            TUNNELLED_FANCY_CABLE_FACADE = REGISTERER.register(
            "tunnelled_fancy_cable_facade",
            () -> BlockEntityType.Builder
                    .of(TunnelledFancyCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ToughCableFacadeBlockEntity>>
            TOUGH_CABLE_FACADE = REGISTERER.register(
            "tough_cable_facade",
            () -> BlockEntityType.Builder
                    .of(ToughCableFacadeBlockEntity::new, SFMBlocks.TOUGH_CABLE_FACADE.get())
                    .build(null)
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ToughFancyCableFacadeBlockEntity>>
            TOUGH_FANCY_CABLE_FACADE = REGISTERER.register(
            "tough_fancy_cable_facade",
            () -> BlockEntityType.Builder
                    .of(ToughFancyCableFacadeBlockEntity::new, SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get())
                    .build(null)
    );
}
