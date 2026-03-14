package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.render.FormItemExtensions;
import ca.teamdman.sfm.common.registry.SFMCreativeTabs;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class FormItem extends Item {
    public FormItem() {
        super(new Item.Properties().tab(SFMCreativeTabs.TAB));
    }

    public static ItemStack createFormFromReference(ItemStack stack) {
        var formStack = new ItemStack(SFMItems.FORM_ITEM.get());
        formStack.getOrCreateTag().put("reference", stack.serializeNBT());
        formStack.setCount(stack.getCount());
        return formStack;
    }

    @MCVersionDependentBehaviour
    public static ItemStack getBorrowedReferenceFromForm(ItemStack stack) {
        // Before data components, this always creates a copied value.
        return ItemStack.of(stack.getOrCreateTag().getCompound("reference"));
    }


    @MCVersionDependentBehaviour
    public static ItemStack getCopiedReferenceFromForm(ItemStack stack) {
        // Before data components, we always receive a copied value from this function.
        return getBorrowedReferenceFromForm(stack);
    }

    @MCVersionDependentBehaviour // 1.21 this gets replaced with RegisterClientExtensionsEvent
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new FormItemExtensions());
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            @Nullable Level pLevel,
            List<Component> pTooltipComponents,
            TooltipFlag pIsAdvanced
    ) {
        if (pStack.hasTag()) {
            var reference = getBorrowedReferenceFromForm(pStack);
            if (!reference.isEmpty()) {
                pTooltipComponents.addAll(reference.getTooltipLines(null, pIsAdvanced));
            }
        }
    }
}
