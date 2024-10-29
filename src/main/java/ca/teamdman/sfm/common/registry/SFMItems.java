package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class SFMItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, SFM.MOD_ID);
    public static final Supplier<Item> MANAGER_ITEM = register("manager", SFMBlocks.MANAGER_BLOCK);
    public static final Supplier<Item> CABLE_ITEM = register("cable", SFMBlocks.CABLE_BLOCK);
    //    public static final  Supplier<Item>   BATTERY_ITEM    = register("battery", SFMBlocks.BATTERY_BLOCK);
    public static final Supplier<Item> WATER_TANK_ITEM = register("water_tank", SFMBlocks.WATER_TANK_BLOCK);
    public static final Supplier<Item> PROXY_ITEM = register("proxy", SFMBlocks.PROXY_BLOCK);
    public static final Supplier<Item> DISK_ITEM = ITEMS.register("disk", DiskItem::new);
    public static final Supplier<Item> LABEL_GUN_ITEM = ITEMS.register(
            "labelgun",
            LabelGunItem::new
    ); // TODO: rename on a major version update to label_gun
    public static final Supplier<Item> NETWORK_TOOL_ITEM = ITEMS.register("network_tool", NetworkToolItem::new);

    public static final Supplier<Item> PRINTING_PRESS_ITEM = ITEMS.register(
            "printing_press",
            PrintingPressBlockItem::new
    );

    public static final Supplier<Item> FORM_ITEM = ITEMS.register("form", FormItem::new);
    public static final Supplier<Item> EXPERIENCE_SHARD_ITEM = ITEMS.register("xp_shard", ExperienceShard::new);
    public static final Supplier<Item> EXPERIENCE_GOOP_ITEM = ITEMS.register("xp_goop", ExperienceGoop::new);



    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private static Supplier<Item> register(String name, Supplier<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void populateMainCreativeTab(
            @SuppressWarnings("unused") CreativeModeTab.ItemDisplayParameters params,
            CreativeModeTab.Output output
    ) {
        output.acceptAll(SFMItems.ITEMS
                                 .getEntries()
                                 .stream()
                                 .map(Supplier::get)
                                 .map(ItemStack::new)
                                 .toList());
    }
}
