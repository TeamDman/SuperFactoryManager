/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package ca.teamdman.sfm.common.registry.internal;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProviderContainer;
import ca.teamdman.sfm.common.program.linting.IProgramLinter;
import ca.teamdman.sfm.common.registry.*;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.List;

/**
 * This class was originally copied from the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: PrimerEventHandler
 * Created by HellFirePvP
 * Date: 13.07.2019 / 09:06
 */
public class PrimerEventHandler {

    private final InternalRegistryPrimer registry;

    public PrimerEventHandler(InternalRegistryPrimer registry) {
        this.registry = registry;
    }


    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        registry.wipe(event.getGenericType());
        SFMItems.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        registry.wipe(event.getGenericType());
        SFMBlocks.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerResourceTypes(RegistryEvent.Register<ResourceTypeContainer> event) {
        registry.wipe(event.getGenericType());
        SFMResourceTypes.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        registry.wipe(event.getGenericType());
        SFMRecipes.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerLinters(RegistryEvent.Register<IProgramLinter> event) {
        registry.wipe(event.getGenericType());
        SFMProgramLinters.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerCapabilityProviders(RegistryEvent.Register<SFMBlockCapabilityProviderContainer> event) {
        registry.wipe(event.getGenericType());
        SFMGlobalBlockCapabilityProviders.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    private <T extends IForgeRegistryEntry<T>> void fillRegistry(Class<T> registrySuperType, IForgeRegistry<T> forgeRegistry) {
        List<?> entries = registry.getEntries(registrySuperType);
        if (entries != null) {
            entries.forEach((e) -> forgeRegistry.register((T) e));
        }
    }

}
