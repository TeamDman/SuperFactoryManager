package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.component.ItemStackBox;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

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

    /**
     * Reads the form reference without creating NBT on an otherwise blank form.
     */
    @MCVersionDependentBehaviour
    public static ItemStack getReferenceFromFormReadOnly(ItemStack stack) {

        var tag = stack.getTag();
        return tag == null ? ItemStack.EMPTY : ItemStack.of(tag.getCompound("reference"));
    }

    @MCVersionDependentBehaviour
    public static ItemStack getCopiedReferenceFromForm(ItemStack stack) {
        return getBorrowedReferenceFromForm(stack).copy();
    }

    @Override
    public void appendHoverText(
            ItemStack pStack,
            TooltipContext pContext,
            List<Component> pTooltipComponents,
            TooltipFlag pTooltipFlag
    ) {
        var reference = getBorrowedReferenceFromForm(pStack);
        if (!reference.isEmpty()) {
            pTooltipComponents.addAll(reference.getTooltipLines(pContext, null, pTooltipFlag));
        }
    }

}
