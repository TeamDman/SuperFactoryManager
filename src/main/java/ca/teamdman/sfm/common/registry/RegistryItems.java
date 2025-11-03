
/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package ca.teamdman.sfm.common.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.ExperienceGoopItem;
import ca.teamdman.sfm.common.item.ExperienceShardItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

import static ca.teamdman.sfm.common.lib.ItemsSFM.*;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RegistryItems
 * Created by HellFirePvP
 * Date: 28.06.2017 / 18:40
 */
public class RegistryItems {

    protected static final List<Item> ITEM_BLOCKS = new ArrayList<>();

    public static void initialize() {
        LABEL_GUN = prepareRegister(new LabelGunItem());
        DISK_ITEM = prepareRegister(new DiskItem());
        NETWORK_TOOL_ITEM = prepareRegister(new NetworkToolItem());
        FORM_ITEM = prepareRegister(new FormItem());
        EXPERIENCE_SHARD_ITEM = prepareRegister(new ExperienceShardItem());
        EXPERIENCE_GOOP_ITEM = prepareRegister(new ExperienceGoopItem());

        registerItemBlocks();
    }

    private static <T extends Item> T prepareRegister(T item) {
        String name = item.getClass().getSimpleName().toLowerCase();
        item.setRegistryName(SFM.MOD_ID, name).setTranslationKey(SFM.MOD_ID + '.' + name);

        return register(item);
    }

    private static <T extends Item> T register(T item) {
        CommonProxy.registryPrimer.register(item);

        return item;
    }

    private static void registerItemBlocks() {
        ITEM_BLOCKS.forEach(RegistryItems::register);
    }


}

