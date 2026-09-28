package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.ClientLabelGunWarningHelper;
import ca.teamdman.sfm.client.handler.LabelGunKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class LabelGunItem extends Item {
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
            stack.getOrCreateTag().putString("sfm:active_label", label);
        }
    }

    public static void clearActiveLabel(
            ItemStack gun
    ) {

        gun.getOrCreateTag().remove("sfm:active_label");
    }

    public static String getActiveLabel(ItemStack stack) {
        //noinspection DataFlowIssue
        return !stack.hasTag() ? "" : stack.getTag().getString("sfm:active_label");
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

    /**
     * Sets the view mode in NBT.
     */
    public static void setViewMode(
            ItemStack stack,
            LabelGunViewMode mode
    ) {

        stack.getOrCreateTag().putInt("sfm:label_gun_view_mode", mode.ordinal());
    }

    public static void cycleViewMode(ItemStack stack) {

        LabelGunViewMode current = getViewMode(stack);
        int nextOrdinal = (current.ordinal() + 1) % LabelGunViewMode.values().length;
        setViewMode(stack, LabelGunViewMode.values()[nextOrdinal]);
    }

    @Override
    public InteractionResult onItemUseFirst(
            ItemStack gun,
            UseOnContext ctx
    ) {

        var level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (level.isClientSide && player != null) {
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
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> lines,
            TooltipFlag detail
    ) {

        if (SFMItemUtils.isClientAndMoreInfoKeyPressed()) {
            Options options = Minecraft.getInstance().options;
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_CLEAR_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY),
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_NEXT_LABEL_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PREVIOUS_LABEL_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_SCROLL_MODIFIER_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CYCLE_LABEL_VIEW_KEY)
                    ).withStyle(ChatFormatting.GRAY)
            );
            lines.add(
                    LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER.getComponent(
                            SFMKeyMappings.getKeyDisplay(options.keyUse)
                    ).withStyle(ChatFormatting.GRAY)
            );
        } else {
            SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(lines);
            lines.addAll(LabelPositionHolder.from(stack).asHoverText());
        }
    }

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
        SHOW_ALL,
        SHOW_ONLY_ACTIVE_LABEL_AND_TARGETED_BLOCK,
        SHOW_ONLY_TARGETED_BLOCK,
    }

}
