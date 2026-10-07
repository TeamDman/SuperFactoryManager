package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraftforge.eventbus.api.IEventBus;
{% when "1.20", "1.20.1" %}
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.bus.api.IEventBus;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import java.util.function.Supplier;

{% endcase %}
public class SFMItems {
{% case minecraft_version %}
{% when "1.19.2" %}
    private static final SFMDeferredRegister<Item> REGISTRY = new SFMDeferredRegisterBuilder<Item>()
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public static final SFMDeferredRegister<Item> REGISTERER = new SFMDeferredRegisterBuilder<Item>()
{% endcase %}
            .namespace(SFM.MOD_ID)
            .registry(SFMWellKnownRegistries.ITEMS.registryKey())
            .build();

    public static final SFMRegistryObject<Item, BlockItem> MANAGER
            = register("manager", SFMBlocks.MANAGER);

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.client_manager %}
    public static final SFMRegistryObject<Item, BlockItem> CLIENT_MANAGER
            = register("client_manager", SFMBlocks.CLIENT_MANAGER);

{% endif %}
{% endcase %}
    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_MANAGER
            = register(
            "tunnelled_manager",
            SFMBlocks.TUNNELLED_MANAGER
    );

    public static final SFMRegistryObject<Item, BlockItem> CABLE
            = register("cable", SFMBlocks.CABLE);

    public static final SFMRegistryObject<Item, BlockItem> FANCY_CABLE
            = register(
            "fancy_cable",
            SFMBlocks.FANCY_CABLE
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static final SFMRegistryObject<Item, BlockItem> TOUGH_CABLE = register(
{% when "26.1.2" %}
    public static final SFMRegistryObject<Item, BlockItem> TOUGH_CABLE =
            register(
{% endcase %}
            "tough_cable",
            SFMBlocks.TOUGH_CABLE
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static final SFMRegistryObject<Item, BlockItem> TOUGH_FANCY_CABLE = register(
{% when "26.1.2" %}
    public static final SFMRegistryObject<Item, BlockItem> TOUGH_FANCY_CABLE =
            register(
{% endcase %}
            "tough_fancy_cable",
            SFMBlocks.TOUGH_FANCY_CABLE
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_CABLE = register(
{% when "26.1.2" %}
    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_CABLE =
            register(
{% endcase %}
            "tunnelled_cable",
            SFMBlocks.TUNNELLED_CABLE
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_FANCY_CABLE = register(
{% when "26.1.2" %}
    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_FANCY_CABLE =
            register(
{% endcase %}
            "tunnelled_fancy_cable",
            SFMBlocks.TUNNELLED_FANCY_CABLE
    );

    public static final SFMRegistryObject<Item, PrintingPressBlockItem> PRINTING_PRESS
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register(
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            = REGISTERER.register(
{% endcase %}
            "printing_press",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            PrintingPressBlockItem::new
{% when "26.1.2" %}
            registryName -> new PrintingPressBlockItem(
                    new Item.Properties()
                            .useBlockDescriptionPrefix()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            )
{% endcase %}
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.touch_display %}
    public static final SFMRegistryObject<Item, BlockItem> TOUCH_DISPLAY
            = register("touch_display", SFMBlocks.TOUCH_DISPLAY);

{% endif %}
{% endcase %}
    public static final SFMRegistryObject<Item, BlockItem> WATER_TANK
            = register(
            "water_tank",
            SFMBlocks.WATER_TANK
    );

    public static final SFMRegistryObject<Item, DiskItem> DISK
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register("disk", DiskItem::new);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            = REGISTERER.register("disk", DiskItem::new);
{% when "26.1.2" %}
            = REGISTERER.register(
                    "disk",
            registryName -> new DiskItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            )
    );
{% endcase %}

    public static final SFMRegistryObject<Item, LabelGunItem> LABEL_GUN
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register(
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            = REGISTERER.register(
{% endcase %}
            "labelgun",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            () -> new LabelGunItem(
{% when "26.1.2" %}
            registryName -> new LabelGunItem(
{% endcase %}
                    new Item.Properties()
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
{% endcase %}
                            .stacksTo(1)
{% case minecraft_version %}
{% when "1.19.2" %}
                            .tab(SFMCreativeTabs.MAIN)
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}

{% endcase %}
            )
    );

    public static final SFMRegistryObject<Item, NetworkToolItem> NETWORK_TOOL
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register(
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            = REGISTERER.register(
{% endcase %}
            "network_tool",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            NetworkToolItem::new
{% when "26.1.2" %}
            registryName -> new NetworkToolItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            )
{% endcase %}
    );

    public static final SFMRegistryObject<Item, FormItem> FORM
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register("form", FormItem::new);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            = REGISTERER.register("form", FormItem::new);
{% when "26.1.2" %}
            = REGISTERER.register(
            "form",
            registryName -> new FormItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            )
    );
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.packet_values %}
    public static final SFMRegistryObject<Item, PacketItem> PACKET
            = REGISTRY.register("packet", PacketItem::new);

{% endif %}
{% when "1.19.4" %}
{% if features.packet_values %}
    public static final SFMRegistryObject<Item, PacketItem> PACKET
            = REGISTERER.register("packet", PacketItem::new);

{% endif %}
{% endcase %}
    public static final SFMRegistryObject<Item, ExperienceShardItem> EXPERIENCE_SHARD
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register(
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            = REGISTERER.register(
{% endcase %}
            "xp_shard",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            ExperienceShardItem::new
{% when "26.1.2" %}
            registryName -> new ExperienceShardItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            )
{% endcase %}
    );

    public static final SFMRegistryObject<Item, ExperienceGoopItem> EXPERIENCE_GOOP
{% case minecraft_version %}
{% when "1.19.2" %}
            = REGISTRY.register(
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            = REGISTERER.register(
{% endcase %}
            "xp_goop",
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            ExperienceGoopItem::new
{% when "26.1.2" %}
            registryName -> new ExperienceGoopItem(
                    new Item.Properties()
                            .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
            )
{% endcase %}
    );

    public static SFMRegistryObject<Item, BlockItem> BUFFER = null;

    static {
        if (SFMEnvironmentUtils.isInIDE()) {
            BUFFER = register("buffer", SFMBlocks.BUFFER_BLOCK);
        }
    }

    public static void register(IEventBus bus) {

{% case minecraft_version %}
{% when "1.19.2" %}
        REGISTRY.register(bus);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        REGISTERER.register(bus);
{% endcase %}
    }

    private static SFMRegistryObject<Item, BlockItem> register(
            String name,
            SFMRegistryObject<Block, ? extends Block> block
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
        return register(name, block, new Item.Properties());
    }
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
        return REGISTRY.register(
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return REGISTERER.register(
{% when "26.1.2" %}
    private static SFMRegistryObject<Item, BlockItem> register(
            String name,
            SFMRegistryObject<Block, ? extends Block> block,
            Item.Properties properties
    ) {

        return REGISTERER.register(
{% endcase %}
                name,
{% case minecraft_version %}
{% when "1.19.2" %}
                () -> new BlockItem(block.get(), new Item.Properties().tab(SFMCreativeTabs.MAIN))
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                () -> new BlockItem(block.get(), new Item.Properties())
{% when "26.1.2" %}
                registryName -> new BlockItem(
                        block.get(),
                        properties
                                .useBlockDescriptionPrefix()
                                .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                )
{% endcase %}
        );
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
    private static SFMRegistryObject<Item, BlockItem> register(
            String name,
            SFMRegistryObject<Block, ? extends Block> block,
            Supplier<Item.Properties> properties
    ) {

        return REGISTERER.register(
                name,
                registryName -> new BlockItem(
                        block.get(),
                        properties.get()
                                .useBlockDescriptionPrefix()
                                .setId(ResourceKey.create(REGISTERER.registry().registryKey(), registryName))
                )
        );
    }

{% endcase %}
}
