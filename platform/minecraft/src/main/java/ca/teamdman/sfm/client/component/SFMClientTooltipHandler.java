package ca.teamdman.sfm.client.component;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import ca.teamdman.sfm.common.block.ToughCableBlock;
import ca.teamdman.sfm.common.block.TunnelledCableBlock;
import ca.teamdman.sfm.common.block.TunnelledManagerBlock;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.component.SFMTooltipType;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.NetworkToolItem;
import ca.teamdman.sfm.common.item.PrintingPressBlockItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

import java.util.function.Consumer;

public class SFMClientTooltipHandler {
    public static void addToTooltip(
            SFMTooltipType type,
            Item.TooltipContext context,
            Consumer<Component> tooltipAdder,
            TooltipFlag flag,
            DataComponentGetter components
    ) {
        switch (type) {
            case DISK -> {
                String program = components.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), "");
                if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {
                    tooltipAdder.accept(SFMItemUtils.getRainbow(DiskItem.getProgramName(components).length()));
                    ProgramSyntaxHighlightingHelper.withSyntaxHighlighting(program, false)
                            .forEach(tooltipAdder);
                } else {
                    LabelPositionHolder.from(components).asHoverText()
                            .forEach(tooltipAdder);
                    DiskItem.getErrors(components)
                            .stream()
                            .map(Component::copy)
                            .map(line -> line.withStyle(ChatFormatting.RED))
                            .forEach(tooltipAdder);
                    DiskItem.getWarnings(components)
                            .stream()
                            .map(Component::copy)
                            .map(line -> line.withStyle(ChatFormatting.YELLOW))
                            .forEach(tooltipAdder);
                    if (!program.isEmpty()) {
                        SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(tooltipAdder);
                    }
                }
                if (!program.isEmpty()) {
                    tooltipAdder.accept(
                            DiskItem.DISK_EDIT_IN_HAND_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY)
                    );
                }
            }
            case LABEL_GUN -> {
                if (SFMItemUtils.isClientAndMoreInfoKeyPressed()) {
                    Options options = Minecraft.getInstance().options;
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_TOGGLE_LABEL_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY),
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PULL_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PULL_MODIFIER_KEY),
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PUSH_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_TARGET_MANAGER_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY),
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_CONTIGUOUS_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PICK_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY),
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_NEXT_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_NEXT_LABEL_KEY)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_PREVIOUS_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_PREVIOUS_LABEL_KEY)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_SCROLL_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.LABEL_GUN_SCROLL_MODIFIER_KEY)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_CYCLE_VIEW_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CYCLE_LABEL_VIEW_KEY)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                    tooltipAdder.accept(
                            LabelGunItem.LABEL_GUN_ITEM_TOOLTIP_GUI_REMINDER.getComponent(
                                    SFMKeyMappings.getKeyDisplay(options.keyUse)
                            ).withStyle(ChatFormatting.GRAY)
                    );
                } else {
                    SFMItemUtils.appendMoreInfoKeyReminderTextIfOnClient(tooltipAdder);
                    LabelPositionHolder.from(components).asHoverText().forEach(tooltipAdder);
                }
            }
            case NETWORK_TOOL -> {
                tooltipAdder.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_1.getComponent().withStyle(ChatFormatting.GRAY));
                tooltipAdder.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_2.getComponent().withStyle(ChatFormatting.GRAY));
                tooltipAdder.accept(
                        NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_3
                                .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CONTAINER_INSPECTOR_KEY))
                                .withStyle(ChatFormatting.AQUA)
                );
                tooltipAdder.accept(
                        NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_8
                                .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY))
                                .withStyle(ChatFormatting.AQUA)
                );
                tooltipAdder.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_4.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltipAdder.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_5.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltipAdder.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_6.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltipAdder.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_7.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case FORM -> {
                var reference = components.getOrDefault(SFMDataComponents.FORM_REFERENCE.get(), ca.teamdman.sfm.common.component.ItemStackBox.EMPTY).stack();
                if (!reference.isEmpty()) {
                    for (Component component : reference.getTooltipLines(context, null, flag)) {
                        tooltipAdder.accept(component);
                    }
                }
            }
            case PRINTING_PRESS -> tooltipAdder.accept(PrintingPressBlockItem.PRINTING_PRESS_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
            case WATER_TANK -> {
                tooltipAdder.accept(WaterTankBlock.WATER_TANK_ITEM_TOOLTIP_1.getComponent().withStyle(ChatFormatting.GRAY));
                tooltipAdder.accept(WaterTankBlock.WATER_TANK_ITEM_TOOLTIP_2.getComponent().withStyle(ChatFormatting.GRAY));
            }
            case TOUGH_CABLE -> tooltipAdder.accept(ToughCableBlock.TOUGH_CABLE_ITEM_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
            case TUNNELLED_MANAGER -> tooltipAdder.accept(TunnelledManagerBlock.TUNNELLED_MANAGER_ITEM_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
            case TUNNELLED_CABLE -> tooltipAdder.accept(TunnelledCableBlock.TUNNELLED_CABLE_ITEM_TOOLTIP.getComponent().withStyle(ChatFormatting.GRAY));
        }
    }
}
