package vswe.superfactory.registry;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.GameData;

import com.google.common.collect.Lists;

import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.*;
import vswe.superfactory.tiles.*;

@Mod.EventBusSubscriber(modid = SuperFactoryManager.MODID)
@GameRegistry.ObjectHolder(SuperFactoryManager.MODID)
public final class ModBlocks {

    public static List<Block> blocks;

    private ModBlocks() {}

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        blocks = Lists.newArrayList(

        );
        blocks.forEach(event.getRegistry()::register);
    }

    public static void addRecipes() {
        // GameData.register_impl(new ClusterUpgradeRecipe()); // TODO: fix
        GameData.register_impl(
                new ClusterRecipe(new ResourceLocation(SuperFactoryManager.UNLOCALIZED_START + "clusterrecipe")));
    }
}
