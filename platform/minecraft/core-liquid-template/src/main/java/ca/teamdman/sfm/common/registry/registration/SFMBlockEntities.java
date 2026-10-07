package ca.teamdman.sfm.common.registry.registration;


import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TestBarrelBlockEntity::new, SFMBlocks.TEST_BARREL.get())
                    .build(null)
{% when "26.1.2" %}
            registryName -> new BlockEntityType<>(
                    TestBarrelBlockEntity::new,
                    SFMBlocks.TEST_BARREL.get())
{% endcase %}
    );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ManagerBlockEntity>>
            MANAGER = REGISTERER.register(
            "manager",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(ManagerBlockEntity::new, SFMBlocks.MANAGER.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(ManagerBlockEntity::new, SFMBlocks.MANAGER.get())
{% endcase %}
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_manager %}
    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ClientManagerBlockEntity>>
            CLIENT_MANAGER = REGISTERER.register(
            "client_manager",
            () -> BlockEntityType.Builder
                    .of(ClientManagerBlockEntity::new, SFMBlocks.CLIENT_MANAGER.get())
                    .build(null)
    );

{% endif %}
{% endcase %}
    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<BufferBlockEntity>>
            BUFFER = REGISTERER.register(
            "buffer",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(BufferBlockEntity::new, SFMBlocks.BUFFER_BLOCK.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(BufferBlockEntity::new, SFMBlocks.BUFFER_BLOCK.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledManagerBlockEntity>>
            TUNNELLED_MANAGER = REGISTERER.register(
            "tunnelled_manager",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TunnelledManagerBlockEntity::new, SFMBlocks.TUNNELLED_MANAGER.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(TunnelledManagerBlockEntity::new, SFMBlocks.TUNNELLED_MANAGER.get())
{% endcase %}
    );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}

{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<CableFacadeBlockEntity>>
            CABLE_FACADE = REGISTERER.register(
            "cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(CableFacadeBlockEntity::new, SFMBlocks.CABLE_FACADE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(CableFacadeBlockEntity::new, SFMBlocks.CABLE_FACADE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<FancyCableFacadeBlockEntity>>
            FANCY_CABLE_FACADE = REGISTERER.register(
            "fancy_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(FancyCableFacadeBlockEntity::new, SFMBlocks.FANCY_CABLE_FACADE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(FancyCableFacadeBlockEntity::new, SFMBlocks.FANCY_CABLE_FACADE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<PrintingPressBlockEntity>>
            PRINTING_PRESS = REGISTERER.register(
            "printing_press",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(PrintingPressBlockEntity::new, SFMBlocks.PRINTING_PRESS.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(PrintingPressBlockEntity::new, SFMBlocks.PRINTING_PRESS.get())
{% endcase %}
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}
    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TouchDisplayBlockEntity>>
            TOUCH_DISPLAY = REGISTERER.register(
            "touch_display",
            () -> BlockEntityType.Builder
                    .of(TouchDisplayBlockEntity::new, SFMBlocks.TOUCH_DISPLAY.get())
                    .build(null)
    );

{% endif %}
{% endcase %}
    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<WaterTankBlockEntity>>
            WATER_TANK = REGISTERER.register(
            "water_tank",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(WaterTankBlockEntity::new, SFMBlocks.WATER_TANK.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(WaterTankBlockEntity::new, SFMBlocks.WATER_TANK.get())
{% endcase %}
    );


    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TestBarrelTankBlockEntity>>
            TEST_BARREL_TANK = REGISTERER.register(
            "test_barrel_tank",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TestBarrelTankBlockEntity::new, SFMBlocks.TEST_BARREL.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(TestBarrelTankBlockEntity::new, SFMBlocks.TEST_BARREL.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledCableBlockEntity>>
            TUNNELLED_CABLE = REGISTERER.register(
            "tunnelled_cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TunnelledCableBlockEntity::new, SFMBlocks.TUNNELLED_CABLE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(TunnelledCableBlockEntity::new, SFMBlocks.TUNNELLED_CABLE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledCableFacadeBlockEntity>>
            TUNNELLED_CABLE_FACADE = REGISTERER.register(
            "tunnelled_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TunnelledCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_CABLE_FACADE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(TunnelledCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_CABLE_FACADE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledFancyCableBlockEntity>>
            TUNNELLED_FANCY_CABLE = REGISTERER.register(
            "tunnelled_fancy_cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TunnelledFancyCableBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(TunnelledFancyCableBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<TunnelledFancyCableFacadeBlockEntity>>
            TUNNELLED_FANCY_CABLE_FACADE = REGISTERER.register(
            "tunnelled_fancy_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(TunnelledFancyCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(TunnelledFancyCableFacadeBlockEntity::new, SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ToughCableFacadeBlockEntity>>
            TOUGH_CABLE_FACADE = REGISTERER.register(
            "tough_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(ToughCableFacadeBlockEntity::new, SFMBlocks.TOUGH_CABLE_FACADE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(ToughCableFacadeBlockEntity::new, SFMBlocks.TOUGH_CABLE_FACADE.get())
{% endcase %}
    );

    public static final SFMRegistryObject<BlockEntityType<?>, BlockEntityType<ToughFancyCableFacadeBlockEntity>>
            TOUGH_FANCY_CABLE_FACADE = REGISTERER.register(
            "tough_fancy_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> BlockEntityType.Builder
                    .of(ToughFancyCableFacadeBlockEntity::new, SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get())
                    .build(null)
{% when "26.1.2" %}
            () -> new BlockEntityType<>(ToughFancyCableFacadeBlockEntity::new, SFMBlocks.TOUGH_FANCY_CABLE_FACADE.get())
{% endcase %}
    );
}
