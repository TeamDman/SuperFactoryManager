//package vswe.superfactory.wrappers;
//
//import net.minecraft.entity.passive.EntityHorse;
//import net.minecraft.registry.Items;
//import net.minecraft.inventory.IInventory;
//import net.minecraft.item.ItemStack;
//import net.minecraftforge.fml.relauncher.ReflectionHelper;z
//
//public class InventoryWrapperHorse extends InventoryWrapper
//{
//    private EntityHorse horse;
//
//    public InventoryWrapperHorse(EntityHorse horse)
//    {
//        super((IInventory) ReflectionHelper.getPrivateValue(EntityHorse.class, horse, 15));
//        this.horse = horse;
//    }
//
//    @Override
//    public boolean isItemValidForSlot(int i, ItemStack itemstack)
//    {
//        //empty stacks)
//        if (!horse.isTame() || itemstack == null)
//        {
//            return super.isItemValidForSlot(i, itemstack);
//
//            //saddle
//        } else if (i == 0)
//        {
//            return itemstack.getItem() == Items.SADDLE;
//
//            //armor
////        }else if(i == 1 && horse.getType().isHorse()) {
////            return HorseType.(itemstack.getItem());
//
//            //chest
//        } else
//        {
//            return i > 1 && horse.isChested();
//        }
//
//    }
//}
