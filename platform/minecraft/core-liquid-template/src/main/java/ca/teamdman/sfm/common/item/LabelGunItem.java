package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.ClientLabelGunWarningHelper;
import ca.teamdman.sfm.client.handler.LabelGunKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMItemUtils;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
{% endcase %}
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
import net.minecraft.core.component.DataComponentGetter;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% else %}
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
{% endcase %}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.world.InteractionResultHolder;
{% else %}
{% endcase %}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
import org.jspecify.annotations.NonNull;
{% endcase %}

import java.util.Comparator;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import java.util.List;
{% when "1.21", "1.21.1" %}
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
{% else %}
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
public class LabelGunItem extends Item {
{% else %}
public class LabelGunItem extends Item implements TooltipProvider {
{% endcase %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.toggle_label_reminder",
            () -> "%s a block to toggle the active label on it."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.push_reminder",
            () -> "%s a Factory Manager to push labels to it."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.pull_reminder",
            () -> "%s + %s a Factory Manager to pull labels from it."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.clear_reminder",
            () -> "%s + %s a block to remove labels from it."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.pick_reminder",
            () -> "%s + %s a block to pick the active label from it."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.contiguous_reminder",
            () -> "Hold %s to perform changes against contiguous blocks touching cables."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.cycle_view_reminder",
            () -> "Press %s to cycle label view."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.next_reminder",
            () -> "Press %s to select next label."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.previous_reminder",
            () -> "Press %s to select previous label."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.scroll_reminder",
            () -> "%s + mouse wheel to select next/previous label."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.gui_reminder",
            () -> "%s the air to open GUI."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".tooltip.target_manager_reminder",
            () -> "%s + %s to label a Factory Manager itself."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM_NAME_WITH_LABEL = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId() + ".with_label",
            () -> "Label Gun: \"%s\""
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_ITEM = new LocalizationEntry(
            () -> SFMItems.LABEL_GUN.get().getDescriptionId(),
            () -> "Label Gun"
    );

    public LabelGunItem(Properties properties) {

        super(properties);
    }

    public static void setActiveLabel(
            ItemStack stack,
            @Nullable String label
    ) {

        if (label == null || label.isEmpty()) {
            clearActiveLabel(stack);
        } else {
            LabelPositionHolder.from(stack).addReferencedLabel(label).save(stack);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
            stack.getOrCreateTag().putString("sfm:active_label", label);
{% else %}
            stack.set(SFMDataComponents.ACTIVE_LABEL, label);

{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void clearActiveLabel(
            ItemStack gun
    ) {

        gun.getOrCreateTag().remove("sfm:active_label");
    }

{% else %}
{% endcase %}
    public static String getActiveLabel(ItemStack stack) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        //noinspection DataFlowIssue
        return !stack.hasTag() ? "" : stack.getTag().getString("sfm:active_label");
{% else %}

        return stack.getOrDefault(SFMDataComponents.ACTIVE_LABEL, "");
{% endcase %}
    }

    public static String getNextLabel(
            ItemStack gun,
            int change
    ) {

        var labels = LabelPositionHolder
                .from(gun)
                .labels()
                .keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (labels.isEmpty()) return "";
        var currentLabel = getActiveLabel(gun);

        int currentLabelIndex = 0;
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equals(currentLabel)) {
                currentLabelIndex = i;
                break;
            }
        }

        int nextLabelIndex = currentLabelIndex + change;
        // ensure going negative wraps around
        nextLabelIndex = ((nextLabelIndex % labels.size()) + labels.size()) % labels.size();

        return labels.get(nextLabelIndex);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% if features.label_readonly_access %}
    /**
     * Returns the current enum mode for the label gun item.
     */
    public static LabelGunViewMode getViewMode(ItemStack stack) {

        int ordinal = stack.getOrCreateTag().getInt("sfm:label_gun_view_mode");
        return getViewModeFromOrdinal(ordinal);
    }

    /**
     * Reads the view mode without creating NBT on an otherwise blank label gun.
     */
    public static LabelGunViewMode getViewModeReadOnly(ItemStack stack) {

        var tag = stack.getTag();
        int ordinal = tag == null ? 0 : tag.getInt("sfm:label_gun_view_mode");
        return getViewModeFromOrdinal(ordinal);
    }

    private static LabelGunViewMode getViewModeFromOrdinal(int ordinal) {

        // fallback if out of bounds or missing
        if (ordinal < 0 || ordinal >= LabelGunViewMode.values().length) {
            return LabelGunViewMode.SHOW_ALL;
        }
        return LabelGunViewMode.values()[ordinal];
    }

{% else %}
    /**
     * Returns the current enum mode for the label gun item.
     */
    public static LabelGunViewMode getViewMode(ItemStack stack) {

        int ordinal = stack.getOrCreateTag().getInt("sfm:label_gun_view_mode");
        // fallback if out of bounds or missing
        if (ordinal < 0 || ordinal >= LabelGunViewMode.values().length) {
            return LabelGunViewMode.SHOW_ALL;
        }
        return LabelGunViewMode.values()[ordinal];
    }

{% endif %}
{% else %}

    public static void clearActiveLabel(
            ItemStack gun
    ) {

        gun.remove(SFMDataComponents.ACTIVE_LABEL);
    }

{% if features.label_readonly_access %}
    /**
     * Returns the current enum mode for the label gun item.
     */
    public static LabelGunViewMode getViewMode(ItemStack stack) {

        return stack.getOrDefault(SFMDataComponents.LABEL_GUN_VIEW_MODE, LabelGunViewMode.SHOW_ALL);
    }

    /**
     * Reads the view mode without creating NBT on an otherwise blank label gun.
     */
    public static LabelGunViewMode getViewModeReadOnly(ItemStack stack) {

        return getViewMode(stack);
    }

{% else %}
    /**
     * Returns the current enum mode for the label gun item.
     */
    public static LabelGunViewMode getViewMode(ItemStack stack) {

        return stack.getOrDefault(SFMDataComponents.LABEL_GUN_VIEW_MODE, LabelGunViewMode.SHOW_ALL);
    }

{% endif %}
{% endcase %}
    /**
     * Sets the view mode in NBT.
     */
    public static void setViewMode(
            ItemStack stack,
            LabelGunViewMode mode
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        stack.getOrCreateTag().putInt("sfm:label_gun_view_mode", mode.ordinal());
{% else %}
        stack.set(SFMDataComponents.LABEL_GUN_VIEW_MODE, mode);
{% endcase %}
    }

    public static void cycleViewMode(ItemStack stack) {

        LabelGunViewMode current = getViewMode(stack);
        int nextOrdinal = (current.ordinal() + 1) % LabelGunViewMode.values().length;
        setViewMode(stack, LabelGunViewMode.values()[nextOrdinal]);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
    public static void clearAll(ItemStack stack) {

        LabelPositionHolder.clear(stack);
        LabelGunItem.setActiveLabel(stack, null);
    }

{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public InteractionResult onItemUseFirst(
{% else %}
    public @NonNull InteractionResult onItemUseFirst(
{% endcase %}
            ItemStack gun,
            UseOnContext ctx
    ) {

        var level = ctx.getLevel();
        Player player = ctx.getPlayer();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (level.isClientSide && player != null) {
{% else %}
        if (level.isClientSide() && player != null) {
{% endcase %}
            boolean pickBlock = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY);
            boolean contiguous = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY);
            boolean clear = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_CLEAR_MODIFIER_KEY);
            boolean pull = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY);
            boolean targetManager = SFMKeyMappings.isKeyDown(SFMKeyMappings.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY);
            ServerboundLabelGunUsePacket msg = new ServerboundLabelGunUsePacket(
                    ctx.getHand(),
                    ctx.getClickedPos(),
                    contiguous,
                    pickBlock,
                    clear,
                    pull,
                    targetManager
            );
            ClientLabelGunWarningHelper.sendLabelGunUsePacketFromClientWithConfirmationIfNecessary(msg, player);
            if (pickBlock) {
                // we don't want to toggle the overlay if we're using pick-block
                LabelGunKeyMappingHandler.setExternalDebounce();
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> lines,
            TooltipFlag detail
{% when "1.21", "1.21.1" %}
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext pContext,
            List<Component> lines,
            TooltipFlag pTooltipFlag
{% else %}
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
        var stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            SFMScreenChangeHelpers.showLabelGunScreen(stack, hand);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.tooltip_mode_override %}
        if (SFMItemUtils.isClientAndMoreInfoRequested()) {
{% else %}
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed()) {
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed()) {
{% else %}
    @Override
    public Component getName(ItemStack stack) {

        var name = getActiveLabel(stack);
        if (name.isEmpty()) return super.getName(stack);
        return LABEL_GUN_ITEM_NAME_WITH_LABEL
                .getComponent(name)
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public void addToTooltip(TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        if (SFMItemUtils.isClientAndMoreInfoKeyPressed()) {
{% endcase %}
            Options options = Minecraft.getInstance().options;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_CLEAR_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_NEXT_LABEL_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PREVIOUS_LABEL_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_SCROLL_MODIFIER_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CYCLE_LABEL_VIEW_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER.getComponent(
{% else %}
            consumer.accept(
                    LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER.getComponent(
{% endcase %}
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
        } else {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
            lines.addAll(LabelPositionHolder.from(stack).asHoverText());
{% else %}
            SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(consumer);
            LabelPositionHolder.from(components).asHoverText().forEach(consumer);
{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {

        var stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            SFMScreenChangeHelpers.showLabelGunScreen(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {

        var name = getActiveLabel(stack);
        if (name.isEmpty()) return super.getName(stack);
        return LABEL_GUN_ITEM_NAME_WITH_LABEL
                .getComponent(name)
                .withStyle(ChatFormatting.AQUA);
    }

    public static void clearAll(ItemStack stack) {

        LabelPositionHolder.clear(stack);
        LabelGunItem.setActiveLabel(stack, null);
    }

    public enum LabelGunViewMode {
{% when "1.21", "1.21.1" %}
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {

        var stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            SFMScreenChangeHelpers.showLabelGunScreen(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {

        var name = getActiveLabel(stack);
        if (name.isEmpty()) return super.getName(stack);
        return LABEL_GUN_ITEM_NAME_WITH_LABEL
                .getComponent(name)
                .withStyle(ChatFormatting.AQUA);
    }

    public static void clearAll(ItemStack stack) {

        LabelPositionHolder.clear(stack);
        LabelGunItem.setActiveLabel(stack, null);
    }

    public enum LabelGunViewMode implements StringRepresentable {
{% else %}
    public enum LabelGunViewMode implements StringRepresentable {
{% endcase %}
        SHOW_ALL,
        SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        SHOW_ONLY_TARGETED_BLOCK,
{% else %}
        SHOW_ONLY_TARGETED_BLOCK;

        public static final Codec<LabelGunViewMode> CODEC = StringRepresentable.fromEnum(LabelGunViewMode::values);

        public static final IntFunction<LabelGunViewMode> BY_ID = ByIdMap.continuous(
                LabelGunViewMode::ordinal,
                values(),
                ByIdMap.OutOfBoundsStrategy.WRAP
        );

        public static final StreamCodec<ByteBuf, LabelGunViewMode> STREAM_CODEC = ByteBufCodecs.idMapper(
                BY_ID,
                LabelGunViewMode::ordinal
        );

        @Override
        public String getSerializedName() {

            return name().toLowerCase(Locale.ROOT);
        }
{% endcase %}
    }

}
