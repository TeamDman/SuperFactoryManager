package ca.teamdman.sfm.common.item;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import ca.teamdman.sfm.client.render.FormItemExtensions;
{% else %}
import ca.teamdman.sfm.common.component.ItemStackBox;
{% endcase %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% case minecraft_version %}
{% when "1.19.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMCreativeTabs;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.21", "1.21.1" %}
{% when "1.20.3", "1.20.4" %}
import net.minecraft.nbt.CompoundTag;
{% else %}
import net.minecraft.core.component.DataComponentGetter;
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
{% when "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
{% when "1.21", "1.21.1" %}
{% else %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import java.util.List;
import java.util.function.Consumer;
{% when "1.21", "1.21.1" %}
import java.util.List;
{% else %}
import java.util.function.Consumer;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
public class FormItem extends Item {
{% else %}
public class FormItem extends Item implements TooltipProvider {
{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry FORM_ITEM = new LocalizationEntry(
            () -> SFMItems.FORM.get().getDescriptionId(),
            () -> "Printing Form"
    );

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public FormItem() {
{% else %}
    public FormItem(Properties properties) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2" %}
        super(new Item.Properties().tab(SFMCreativeTabs.MAIN));
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super(new Item.Properties());
{% else %}
        super(properties);
{% endcase %}
    }

    public static ItemStack createFormFromReference(ItemStack stack) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}
        // Immutability: create a copy of the stack we received by reference
        stack = stack.copy();

        // Create the form stack
{% endcase %}
        var formStack = new ItemStack(SFMItems.FORM.get());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2" %}
        formStack.getOrCreateTag().put("reference", stack.serializeNBT());
{% when "1.20.3", "1.20.4" %}
        formStack.getOrCreateTag().put("reference", stack.save(new CompoundTag()));
{% else %}

        // Set the inner item
        formStack.set(SFMDataComponents.FORM_REFERENCE, new ItemStackBox(stack));

        // Set the stack size
{% endcase %}
        formStack.setCount(stack.getCount());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}

        // Return the result
{% endcase %}
        return formStack;
    }

    @MCVersionDependentBehaviour
    public static ItemStack getBorrowedReferenceFromForm(ItemStack stack) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        // Before data components, this always creates a copied value.
        return ItemStack.of(stack.getOrCreateTag().getCompound("reference"));
{% when "1.21", "1.21.1" %}
        return stack.getOrDefault(SFMDataComponents.FORM_REFERENCE, ItemStackBox.EMPTY).stack();
{% else %}
        return getBorrowedReferenceFromForm((DataComponentGetter) stack);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2" %}
{% if features.form_readonly_access %}
    /**
     * Reads the form reference without creating NBT on an otherwise blank form.
     */
    @MCVersionDependentBehaviour
    public static ItemStack getReferenceFromFormReadOnly(ItemStack stack) {

        var tag = stack.getTag();
        return tag == null ? ItemStack.EMPTY : ItemStack.of(tag.getCompound("reference"));
    }

{% endif %}
{% when "1.20.3", "1.20.4" %}
{% if features.form_readonly_access %}
    /**
     * Reads the form reference without creating NBT on an otherwise blank form.
     */
    @MCVersionDependentBehaviour
    public static ItemStack getReferenceFromFormReadOnly(ItemStack stack) {

        var tag = stack.getTag();
        return tag == null ? ItemStack.EMPTY : ItemStack.of(tag.getCompound("reference"));
    }

{% endif %}
{% when "1.21", "1.21.1" %}
{% if features.form_readonly_access %}
    /**
     * Reads the form reference without creating data on an otherwise blank form.
     */
    @MCVersionDependentBehaviour
    public static ItemStack getReferenceFromFormReadOnly(ItemStack stack) {

        return getBorrowedReferenceFromForm(stack);
    }

{% endif %}
{% else %}
    public static ItemStack getBorrowedReferenceFromForm(DataComponentGetter components) {
        return components.getOrDefault(SFMDataComponents.FORM_REFERENCE.get(), ItemStackBox.EMPTY).stack();
    }

{% if features.form_readonly_access %}
    /**
     * Reads the form reference without creating data on an otherwise blank form.
     */
    @MCVersionDependentBehaviour
    public static ItemStack getReferenceFromFormReadOnly(ItemStack stack) {

        return getBorrowedReferenceFromForm(stack);
    }

{% endif %}
{% endcase %}
    @MCVersionDependentBehaviour
    public static ItemStack getCopiedReferenceFromForm(ItemStack stack) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        // Before data components, we always receive a copied value from this function.
        return getBorrowedReferenceFromForm(stack);
{% else %}
        return getBorrowedReferenceFromForm(stack).copy();
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    @MCVersionDependentBehaviour // 1.21 this gets replaced with RegisterClientExtensionsEvent
{% else %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
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
{% when "1.21", "1.21.1" %}
    public void appendHoverText(
            ItemStack pStack,
            TooltipContext pContext,
            List<Component> pTooltipComponents,
            TooltipFlag pTooltipFlag
    ) {
        var reference = getBorrowedReferenceFromForm(pStack);
        if (!reference.isEmpty()) {
            pTooltipComponents.addAll(reference.getTooltipLines(pContext, null, pTooltipFlag));
{% else %}
    public void addToTooltip(
            TooltipContext context,
            Consumer<Component> consumer,
            TooltipFlag flag,
            DataComponentGetter components
    ) {
        var reference = components.getOrDefault(SFMDataComponents.FORM_REFERENCE.get(), ca.teamdman.sfm.common.component.ItemStackBox.EMPTY).stack();
        if (!reference.isEmpty()) {
            for (Component component : reference.getTooltipLines(context, null, flag)) {
                consumer.accept(component);
            }
{% endcase %}
        }
    }
}
