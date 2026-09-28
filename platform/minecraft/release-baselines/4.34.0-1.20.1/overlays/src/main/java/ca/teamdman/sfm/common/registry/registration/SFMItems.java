package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.*;
import ca.teamdman.sfm.common.registry.SFMDeferredRegister;
import ca.teamdman.sfm.common.registry.SFMDeferredRegisterBuilder;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

public class SFMItems {
    public static final SFMDeferredRegister<Item> REGISTERER = new SFMDeferredRegisterBuilder<Item>()
            .namespace(SFM.MOD_ID)
            .registry(SFMWellKnownRegistries.ITEMS.registryKey())
            .build();

    public static final SFMRegistryObject<Item, BlockItem> MANAGER
            = register("manager", SFMBlocks.MANAGER);

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

    public static final SFMRegistryObject<Item, BlockItem> TOUGH_CABLE = register(
            "tough_cable",
            SFMBlocks.TOUGH_CABLE
    );

    public static final SFMRegistryObject<Item, BlockItem> TOUGH_FANCY_CABLE = register(
            "tough_fancy_cable",
            SFMBlocks.TOUGH_FANCY_CABLE
    );

    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_CABLE = register(
            "tunnelled_cable",
            SFMBlocks.TUNNELLED_CABLE
    );

    public static final SFMRegistryObject<Item, BlockItem> TUNNELLED_FANCY_CABLE = register(
            "tunnelled_fancy_cable",
            SFMBlocks.TUNNELLED_FANCY_CABLE
    );

    public static final SFMRegistryObject<Item, PrintingPressBlockItem> PRINTING_PRESS
            = REGISTERER.register(
            "printing_press",
            PrintingPressBlockItem::new
    );

    public static final SFMRegistryObject<Item, BlockItem> WATER_TANK
            = register(
            "water_tank",
            SFMBlocks.WATER_TANK
    );

    public static final SFMRegistryObject<Item, DiskItem> DISK
            = REGISTERER.register("disk", DiskItem::new);

    public static final SFMRegistryObject<Item, LabelGunItem> LABEL_GUN
            = REGISTERER.register(
            "labelgun",
            () -> new LabelGunItem(
                    new Item.Properties()
                            .stacksTo(1)

            )
    );

    public static final SFMRegistryObject<Item, NetworkToolItem> NETWORK_TOOL
            = REGISTERER.register(
            "network_tool",
            NetworkToolItem::new
    );

    public static final SFMRegistryObject<Item, FormItem> FORM
            = REGISTERER.register("form", FormItem::new);

    public static final SFMRegistryObject<Item, ExperienceShardItem> EXPERIENCE_SHARD
            = REGISTERER.register(
            "xp_shard",
            ExperienceShardItem::new
    );

    public static final SFMRegistryObject<Item, ExperienceGoopItem> EXPERIENCE_GOOP
            = REGISTERER.register(
            "xp_goop",
            ExperienceGoopItem::new
    );

    public static SFMRegistryObject<Item, BlockItem> BUFFER = null;

    static {
        if (SFMEnvironmentUtils.isInIDE()) {
            BUFFER = register("buffer", SFMBlocks.BUFFER_BLOCK);
        }
    }

    public static void register(IEventBus bus) {

        REGISTERER.register(bus);
    }

    private static SFMRegistryObject<Item, BlockItem> register(
            String name,
            SFMRegistryObject<Block, ? extends Block> block
    ) {

        return REGISTERER.register(
                name,
                () -> new BlockItem(block.get(), new Item.Properties())
        );
    }

}
