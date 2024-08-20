package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.item.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SFMItems {
    public static final CreativeModeTab TAB = new CreativeModeTab(SFM.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(SFMBlocks.MANAGER_BLOCK.get());
        }
    };
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SFM.MOD_ID);
    public static final RegistryObject<Item> MANAGER_ITEM = register("manager", SFMBlocks.MANAGER_BLOCK);
    public static final RegistryObject<Item> CABLE_ITEM = register("cable", SFMBlocks.CABLE_BLOCK);
    public static final RegistryObject<Item> CABLE_BLOCK_ITEM = register("cable_block", SFMBlocks.CABLE_BLOCK_BLOCK);
    public static final RegistryObject<Item> PRINTING_PRESS_ITEM = ITEMS.register(
            "printing_press",
            PrintingPressBlockItem::new
    );
    //    public static final  RegistryObject<Item>   BATTERY_ITEM    = register("battery", SFMBlocks.BATTERY_BLOCK);
    public static final RegistryObject<Item> WATER_TANK_ITEM = register("water_tank", SFMBlocks.WATER_TANK_BLOCK);
    public static final RegistryObject<Item> DISK_ITEM = ITEMS.register("disk", DiskItem::new);
    public static final RegistryObject<Item> LABEL_GUN_ITEM = ITEMS.register(
            "labelgun",
            LabelGunItem::new
    ); // TODO: rename on a major version update to label_gun
    public static final RegistryObject<Item> NETWORK_TOOL_ITEM = ITEMS.register("network_tool", NetworkToolItem::new);

    public static final RegistryObject<Item> FORM_ITEM = ITEMS.register("form", FormItem::new);
    public static final RegistryObject<Item> EXPERIENCE_SHARD_ITEM = ITEMS.register("xp_shard", ExperienceShard::new);
    public static final RegistryObject<Item> EXPERIENCE_GOOP_ITEM = ITEMS.register("xp_goop", ExperienceGoop::new);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private static RegistryObject<Item> register(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB)));
    }
}
