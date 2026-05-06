package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.component.ItemStackBox;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public class FormItem extends Item {
    @SFMLocalizationDatagen
    public static final LocalizationEntry FORM_ITEM = new LocalizationEntry(
            () -> SFMItems.FORM.get().getDescriptionId(),
            () -> "Printing Form"
    );

    public FormItem() {

        super(new Item.Properties());
    }

    public static ItemStack createFormFromReference(ItemStack stack) {

        // Immutability: create a copy of the stack we received by reference
        stack = stack.copy();

        // Create the form stack
        var formStack = new ItemStack(SFMItems.FORM.get());

        // Set the inner item
        formStack.set(SFMDataComponents.FORM_REFERENCE, new ItemStackBox(stack));

        // Set the stack size
        formStack.setCount(stack.getCount());

        // Return the result
        return formStack;
    }

    @MCVersionDependentBehaviour
    public static ItemStack getBorrowedReferenceFromForm(ItemStack stack) {
        return stack.getOrDefault(SFMDataComponents.FORM_REFERENCE, ItemStackBox.EMPTY).stack();
    }

    @MCVersionDependentBehaviour
    public static ItemStack getCopiedReferenceFromForm(ItemStack stack) {
        return getBorrowedReferenceFromForm(stack).copy();
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            TooltipContext pContext,
            TooltipDisplay pTooltipDisplay,
            Consumer<Component> pTooltipComponents,
            TooltipFlag pTooltipFlag
    ) {
        var reference = getBorrowedReferenceFromForm(pStack);
        if (!reference.isEmpty()) {
            for (Component component : reference.getTooltipLines(pContext, null, pTooltipFlag)) {
                pTooltipComponents.accept(component);
            }
        }
    }

}
