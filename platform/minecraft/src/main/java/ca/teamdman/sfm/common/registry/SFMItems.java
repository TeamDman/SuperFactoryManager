package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.item.*;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

public class SFMItems {


    public static DiskItem DISK_ITEM;
    public static LabelGunItem LABEL_GUN_ITEM;
    public static NetworkToolItem NETWORK_TOOL_ITEM;
    public static Item BUFFER_ITEM;

    public static final List<Item> ITEM_BLOCKS = new ArrayList<>();
    public static final List<Item> CREATIVE_TAB_ITEMS = new ArrayList<>();

    public static void initialize() {
        SFMItems.LABEL_GUN_ITEM = prepareRegister(new LabelGunItem(), "labelgun");
        SFMItems.DISK_ITEM = prepareRegister(new DiskItem(), "disk");
        SFMItems.NETWORK_TOOL_ITEM = prepareRegister(new NetworkToolItem(), "network_tool");

        registerItemBlocks();
    }

    private static <T extends Item> T prepareRegister(T item, String name) {
        item.setRegistryName(SFM.MOD_ID, name).setTranslationKey(SFM.LOCALIZATION_KEY + "." + name);
        CREATIVE_TAB_ITEMS.add(item);
        return register(item);
    }

    private static <T extends Item> T register(T item) {
        CommonProxy.registryPrimer.register(item);
        return item;
    }

    private static void registerItemBlocks() {
        ITEM_BLOCKS.forEach(item -> {
            register(item);
            CREATIVE_TAB_ITEMS.add(item);
        });
    }
}