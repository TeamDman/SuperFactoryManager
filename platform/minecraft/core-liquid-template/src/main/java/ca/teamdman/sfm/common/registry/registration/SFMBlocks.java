package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraftforge.eventbus.api.IEventBus;
{% when "1.20", "1.20.1" %}
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraftforge.eventbus.api.IEventBus;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.bus.api.IEventBus;
{% endcase %}


public class SFMBlocks {
    public static final SFMDeferredRegister<Block> REGISTERER =
            new SFMDeferredRegisterBuilder<Block>()
                    .namespace(SFM.MOD_ID)
                    .registry(SFMWellKnownRegistries.BLOCKS.registryKey())
                    .build();

    public static final SFMRegistryObject<Block, ManagerBlock> MANAGER
            =
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            REGISTERER.register("manager", ManagerBlock::new);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_manager %}

    public static final SFMRegistryObject<Block, ClientManagerBlock> CLIENT_MANAGER
            = REGISTERER.register("client_manager", ClientManagerBlock::new);
{% endif %}
{% endcase %}
{% when "26.1.2" %}
            REGISTERER.register("manager", registryName ->
                    new ManagerBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                    ));
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static final SFMRegistryObject<Block, BufferBlock> BUFFER_BLOCK =
            REGISTERER.register(
                    "buffer", () -> new BufferBlock(
                            BlockBehaviour.Properties
                                    .of(Material.PISTON)
                                    .destroyTime(1.5f)
                                    .sound(SoundType.METAL),
                            BufferBlockTier.MaxUnit
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static final SFMRegistryObject<Block,BufferBlock> BUFFER_BLOCK = REGISTERER.register(
            "buffer", () -> new BufferBlock(
                    BlockBehaviour.Properties.of()
                                    .destroyTime(1.5f)
                                    .sound(SoundType.METAL),
                            BufferBlockTier.MaxUnit
{% when "26.1.2" %}
    public static final SFMRegistryObject<Block,BufferBlock> BUFFER_BLOCK = REGISTERER.register(
            "buffer", registryName -> new BufferBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                            .destroyTime(1.5f)
                            .sound(SoundType.METAL),
                    BufferBlockTier.MaxUnit
{% endcase %}
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledManagerBlock> TUNNELLED_MANAGER
            =
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            REGISTERER.register("tunnelled_manager", TunnelledManagerBlock::new);
{% when "26.1.2" %}
            REGISTERER.register("tunnelled_manager", registryName -> new TunnelledManagerBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(SFMWellKnownRegistries.BLOCKS.registryKey(), registryName))
            ));
{% endcase %}

    public static final SFMRegistryObject<Block, PrintingPressBlock> PRINTING_PRESS
            =
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            REGISTERER.register("printing_press", PrintingPressBlock::new);
{% when "26.1.2" %}
            REGISTERER.register("printing_press", registryName -> new PrintingPressBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}
    public static final SFMRegistryObject<Block, TouchDisplayBlock> TOUCH_DISPLAY
            =
            REGISTERER.register("touch_display", TouchDisplayBlock::new);

{% endif %}
{% endcase %}
    public static final SFMRegistryObject<Block, WaterTankBlock> WATER_TANK
            =
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            REGISTERER.register("water_tank", WaterTankBlock::new);
{% when "26.1.2" %}
            REGISTERER.register("water_tank", registryName -> new WaterTankBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));
{% endcase %}

    public static final SFMRegistryObject<Block, TestBarrelBlock> TEST_BARREL
            =
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            REGISTERER.register("test_barrel", TestBarrelBlock::new);
{% when "26.1.2" %}
            REGISTERER.register("test_barrel", registryName -> new TestBarrelBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));
{% endcase %}

    public static final SFMRegistryObject<Block, TestBarrelTankBlock> TEST_BARREL_TANK // TODO: remove this one
            =
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            REGISTERER.register("test_barrel_tank", TestBarrelTankBlock::new);
{% when "26.1.2" %}
            REGISTERER.register("test_barrel_tank", registryName -> new TestBarrelTankBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            ));
{% endcase %}

    // TODO: pull out properties from other block constructors to enable mutating in inheriting class constructors

    public static final SFMRegistryObject<Block, CableBlock> CABLE =
            REGISTERER.register(
                    "cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new CableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new CableBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
{% when "26.1.2" %}
                    registryName -> new CableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, CableFacadeBlock> CABLE_FACADE =
            REGISTERER.register(
                    "cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new CableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new CableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
{% when "26.1.2" %}
                    registryName -> new CableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableBlock> FANCY_CABLE =
            REGISTERER.register(
                    "fancy_cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new FancyCableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new FancyCableBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
{% when "26.1.2" %}
                    registryName -> new FancyCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, FancyCableFacadeBlock> FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "fancy_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new FancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new FancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
                            .instrument(NoteBlockInstrument.BASS)
{% when "26.1.2" %}
                    registryName -> new FancyCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                                    .instrument(NoteBlockInstrument.BASS)
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    // Tough variants
    public static final SFMRegistryObject<Block, ToughCableBlock> TOUGH_CABLE =
            REGISTERER.register(
                    "tough_cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new ToughCableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.STONE, MaterialColor.COLOR_BLACK)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new ToughCableBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new ToughCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(10f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughCableFacadeBlock> TOUGH_CABLE_FACADE =
            REGISTERER.register(
                    "tough_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new ToughCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.STONE, MaterialColor.COLOR_BLACK)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new ToughCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new ToughCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(10f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughFancyCableBlock> TOUGH_FANCY_CABLE =
            REGISTERER.register(
                    "tough_fancy_cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new ToughFancyCableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.STONE, MaterialColor.COLOR_BLACK)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new ToughFancyCableBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new ToughFancyCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .requiresCorrectToolForDrops()
                                    .explosionResistance(1200.0F)
                                    .destroyTime(5f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, ToughFancyCableFacadeBlock> TOUGH_FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "tough_fancy_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new ToughFancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.STONE, MaterialColor.COLOR_BLACK)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new ToughFancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new ToughFancyCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new TunnelledCableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new TunnelledCableBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new TunnelledCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledCableFacadeBlock> TUNNELLED_CABLE_FACADE =
            REGISTERER.register(
                    "tunnelled_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new TunnelledCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new TunnelledCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new TunnelledCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledFancyCableBlock> TUNNELLED_FANCY_CABLE =
            REGISTERER.register(
                    "tunnelled_fancy_cable",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new TunnelledFancyCableBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new TunnelledFancyCableBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new TunnelledFancyCableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static final SFMRegistryObject<Block, TunnelledFancyCableFacadeBlock> TUNNELLED_FANCY_CABLE_FACADE =
            REGISTERER.register(
                    "tunnelled_fancy_cable_facade",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                    () -> new TunnelledFancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of(Material.METAL)
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    () -> new TunnelledFancyCableFacadeBlock(
                            BlockBehaviour.Properties
                                    .of()
{% when "26.1.2" %}
                    registryName -> new TunnelledFancyCableFacadeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                                    .destroyTime(1f)
                                    .sound(SoundType.METAL)
                    )
            );

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

}
