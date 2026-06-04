package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.common.block.*;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.item.*;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class SFMTooltips {

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void itemTooltipEvent(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        switch (stack.getItem()) {
            case DiskItem item -> addTooltip(item, event);
            case LabelGunItem item -> addTooltip(item, event);
            case NetworkToolItem item -> addTooltip(item, event);
            case FormItem item -> addTooltip(item, event);
            case PrintingPressBlockItem item -> addTooltip(item, event);
            case BlockItem item when item.getBlock() instanceof WaterTankBlock block -> addTooltip(block, event);
            case BlockItem item when item.getBlock() instanceof ToughCableBlock block -> addTooltip(block, event);
            case BlockItem item when item.getBlock() instanceof ToughFancyCableBlock block -> addTooltip(block, event);
            case BlockItem item when item.getBlock() instanceof TunnelledCableBlock block -> addTooltip(block, event);
            case BlockItem item when item.getBlock() instanceof TunnelledFancyCableBlock block -> addTooltip(block, event);
            case BlockItem item when item.getBlock() instanceof TunnelledManagerBlock block -> addTooltip(block, event);

            default -> {}
        }
    }

    private static void addTooltip(TooltipProvider provider, ItemTooltipEvent event) {
        provider.addToTooltip(
                event.getContext(),
                event.getToolTip()::add,
                event.getFlags(),
                event.getItemStack()
        );
    }
}
