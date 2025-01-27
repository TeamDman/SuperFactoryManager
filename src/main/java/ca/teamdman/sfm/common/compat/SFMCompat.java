package ca.teamdman.sfm.common.compat;

import ca.teamdman.sfm.common.resourcetype.ChemicalResourceType;
import com.google.common.collect.Maps;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SFMCompat {
    public static boolean isMekanismLoaded() {
        return ModList.get().getModContainerById("mekanism").isPresent();
    }

    public static List<BlockCapability<?, @Nullable Direction>> getCapabilities() {
        ArrayList<BlockCapability<?, @Nullable Direction>> capabilities = new ArrayList<>(List.of(
                Capabilities.ItemHandler.BLOCK,
                Capabilities.FluidHandler.BLOCK,
                Capabilities.EnergyStorage.BLOCK
        ));

        if (isMekanismLoaded()) {
            capabilities.add(ChemicalResourceType.CAP);
        }

        return capabilities;
    }

    public static Map<BlockCapability<?, ?>, ItemCapability<?, ?>> getCapabilityMap() {
        Map<BlockCapability<?, ?>, ItemCapability<?, ?>> capabilityMap = Maps.newIdentityHashMap();
        capabilityMap.put(Capabilities.ItemHandler.BLOCK, Capabilities.ItemHandler.ITEM);
        capabilityMap.put(Capabilities.FluidHandler.BLOCK, Capabilities.FluidHandler.ITEM);
        capabilityMap.put(Capabilities.EnergyStorage.BLOCK, Capabilities.EnergyStorage.ITEM);

        if (isMekanismLoaded()) {
            capabilityMap.put(ChemicalResourceType.CAP, mekanism.common.capabilities.Capabilities.CHEMICAL.item());
        }

        return capabilityMap;
    }

}
