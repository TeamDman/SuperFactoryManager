/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package ca.teamdman.sfm.common.registry.internal;

import hellfirepvp.modularmachinery.common.crafting.ComponentType;
import hellfirepvp.modularmachinery.common.crafting.adapter.RecipeAdapter;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.crafting.tooltip.RequirementTip;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryComponentTypes;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import hellfirepvp.modularmachinery.common.registry.RegistryRecipeAdapters;
import hellfirepvp.modularmachinery.common.registry.RegistryRegistries;
import hellfirepvp.modularmachinery.common.registry.RegistryRequirementTips;
import hellfirepvp.modularmachinery.common.registry.RegistryRequirementTypes;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.List;

/**
 * This class was copied from the Modular Machinery Mod
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
    public void registerRegistries(RegistryEvent.NewRegistry event) {
        RegistryRegistries.buildRegistries();
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        registry.wipe(event.getGenericType());
        SFMBlocks.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        registry.wipe(event.getGenericType());
        RegistryBlocks.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerAdapters(RegistryEvent.Register<RecipeAdapter> event) {
        registry.wipe(event.getGenericType());
        RegistryRecipeAdapters.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerComponentTypes(RegistryEvent.Register<ComponentType> event) {
        registry.wipe(event.getGenericType());
        RegistryComponentTypes.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerRequirementTips(RegistryEvent.Register<RequirementTip> event) {
        registry.wipe(event.getGenericType());
        RegistryRequirementTips.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    @SubscribeEvent
    public void registerComponentRequirementTypes(RegistryEvent.Register event) {
        //Class filter in ASMEventHandler can't cope with wildcard typed registries
        //So we wildcard allow every registry event to pass into here and check ourselves instead.
        if (RequirementType.class != event.getGenericType()) {
            return;
        }
        registry.wipe(event.getGenericType());
        RegistryRequirementTypes.initialize();
        fillRegistry(event.getRegistry().getRegistrySuperType(), event.getRegistry());
    }

    private <T extends IForgeRegistryEntry<T>> void fillRegistry(Class<T> registrySuperType, IForgeRegistry<T> forgeRegistry) {
        List<?> entries = registry.getEntries(registrySuperType);
        if (entries != null) {
            entries.forEach((e) -> forgeRegistry.register((T) e));
        }
    }

}
