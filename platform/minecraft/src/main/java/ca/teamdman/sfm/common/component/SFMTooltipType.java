package ca.teamdman.sfm.common.component;

import ca.teamdman.sfm.common.block.ToughCableBlock;
import ca.teamdman.sfm.common.block.TunnelledCableBlock;
import ca.teamdman.sfm.common.block.TunnelledManagerBlock;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.item.PrintingPressBlockItem;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

import java.util.function.Consumer;

public enum SFMTooltipType implements StringRepresentable {
    DISK("disk"),
    LABEL_GUN("label_gun"),
    NETWORK_TOOL("network_tool"),
    FORM("form"),
    PRINTING_PRESS("printing_press"),
    WATER_TANK("water_tank"),
    TOUGH_CABLE("tough_cable"),
    TUNNELLED_MANAGER("tunnelled_manager"),
    TUNNELLED_CABLE("tunnelled_cable");

    public static final Codec<SFMTooltipType> CODEC = StringRepresentable.fromEnum(SFMTooltipType::values);
    private final String name;

    SFMTooltipType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag flag, DataComponentGetter components) {
        if (SFMEnvironmentUtils.isClient()) {
            ca.teamdman.sfm.client.component.SFMClientTooltipHandler.addToTooltip(this, context, tooltipAdder, flag, components);
            return;
        }

        // Fallback for server (though TooltipProvider is usually client-side only,
        // some basic tooltips might still be needed if accessed on server)
        switch (this) {
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
