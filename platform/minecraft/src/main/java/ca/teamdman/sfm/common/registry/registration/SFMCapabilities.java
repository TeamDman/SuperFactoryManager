package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.RedstoneSignalStorage;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;


public class SFMCapabilities {



    public static void register() {
         CapabilityManager.INSTANCE.register(IRedstoneSignalStorage.class, new Capability.IStorage<>() {
             @Override
             public NBTBase writeNBT(Capability<IRedstoneSignalStorage> capability, IRedstoneSignalStorage instance, EnumFacing side) {
                 return new NBTTagInt(instance.getStoredAmount());
             }

             @Override
             public void readNBT(Capability<IRedstoneSignalStorage> capability, IRedstoneSignalStorage instance, EnumFacing side, NBTBase nbt) {
                 if (nbt instanceof NBTTagInt nbtTagInt) {
                     instance.setStoredAmount(nbtTagInt.getInt());
                 }
             }
         }, () -> new RedstoneSignalStorage(0, 15));
    }
}
