package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.common.component.ItemStackBox;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.function.Consumer;

public class FormItem extends Item implements TooltipProvider {
    @SFMLocalizationDatagen
    public static final LocalizationEntry FORM_ITEM = new LocalizationEntry(
            () -> SFMItems.FORM.get().getDescriptionId(),
            () -> "Printing Form"
    );

    public FormItem(Properties properties) {

        super(properties);
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
        return getBorrowedReferenceFromForm((DataComponentGetter) stack);
    }

    public static ItemStack getBorrowedReferenceFromForm(DataComponentGetter components) {
        return components.getOrDefault(SFMDataComponents.FORM_REFERENCE.get(), ItemStackBox.EMPTY).stack();
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
    public void addToTooltip(TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        var reference = components.getOrDefault(SFMDataComponents.FORM_REFERENCE.get(), ca.teamdman.sfm.common.component.ItemStackBox.EMPTY).stack();
        if (!reference.isEmpty()) {
            for (Component component : reference.getTooltipLines(context, null, flag)) {
                consumer.accept(component);
            }
        }
    }
}
