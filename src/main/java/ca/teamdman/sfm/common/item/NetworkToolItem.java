package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolUsePacket;
import ca.teamdman.sfm.common.registry.SFMCreativeTabs;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NetworkToolItem extends Item {
    public NetworkToolItem() {
        super(new Item.Properties().stacksTo(1).tab(SFMCreativeTabs.TAB));
    }

    @Override
    public InteractionResult onItemUseFirst(
            ItemStack stack,
            UseOnContext pContext
    ) {
        if (!pContext.getLevel().isClientSide) return InteractionResult.SUCCESS;
        SFMPackets.sendToServer(new ServerboundNetworkToolUsePacket(
                pContext.getClickedPos(),
                pContext.getClickedFace()
        ));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> lines,
            TooltipFlag detail
    ) {
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_1.getComponent().withStyle(ChatFormatting.GRAY));
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_2.getComponent().withStyle(ChatFormatting.GRAY));
        lines.add(
                LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_3
                        .getComponent(SFMKeyMappings.CONTAINER_INSPECTOR_KEY.get().getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.AQUA)
        );
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_4.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_5.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_6.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_7.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public void inventoryTick(
            ItemStack pStack,
            Level pLevel,
            Entity pEntity,
            int pSlotId,
            boolean pIsSelected
    ) {
        if (pLevel.isClientSide) return;
        if (!(pEntity instanceof Player pPlayer)) return;
        boolean isInHand = pStack == pPlayer.getMainHandItem() || pStack == pPlayer.getOffhandItem();
        if (!isInHand) return;
        boolean shouldRefresh = pEntity.tickCount % 20 == 0;
        if (!shouldRefresh) return;
        final long maxDistance = 128;
        Set<BlockPos> cablePositions = CableNetworkManager
                .getNetworksInRange(pLevel, pEntity.blockPosition(), maxDistance)
                .flatMap(CableNetwork::getCablePositions)
                .collect(Collectors.toSet());
        setCablePositions(pStack, cablePositions);

        Set<BlockPos> capabilityProviderPositions = CableNetworkManager
                .getNetworksInRange(pLevel, pEntity.blockPosition(), maxDistance)
                .flatMap(CableNetwork::getCapabilityProviderPositions)
                .collect(Collectors.toSet());
        setCapabilityProviderPositions(pStack, capabilityProviderPositions);

        // remove the data stored by older versions of the mod
        pStack.getOrCreateTag().remove("networks");
    }


    public static boolean getOverlayEnabled(ItemStack stack) {
        return !stack.getOrCreateTag().getBoolean("sfm:network_tool_overlay_disabled");
    }

    public static void setOverlayEnabled(
            ItemStack stack,
            boolean value
    ) {
        if (value) {
            stack.getOrCreateTag().remove("sfm:network_tool_overlay_disabled");
        } else {
            stack.getOrCreateTag().putBoolean("sfm:network_tool_overlay_disabled", true);
        }
    }

    public static void setCablePositions(
            ItemStack stack,
            Set<BlockPos> positions
    ) {
        stack.getOrCreateTag().put(
                "sfm:cable_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static Set<BlockPos> getCablePositions(ItemStack stack) {
        if (stack.getOrCreateTag().get("sfm:cable_positions") instanceof ByteArrayTag byteArrayTag) {
            // new format
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        // fallback to old format
        return stack.getOrCreateTag().getList("sfm:cable_positions", 10).stream()
                .map(CompoundTag.class::cast)
                .map(NbtUtils::readBlockPos)
                .collect(Collectors.toSet());
    }

    public static void setCapabilityProviderPositions(
            ItemStack stack,
            Set<BlockPos> positions
    ) {
        stack.getOrCreateTag().put(
                "sfm:capability_provider_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static Set<BlockPos> getCapabilityProviderPositions(ItemStack stack) {
        if (stack.getOrCreateTag().get("sfm:capability_provider_positions") instanceof ByteArrayTag byteArrayTag) {
            // new format
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        // fallback to old format
        return stack.getOrCreateTag().getList("sfm:capability_provider_positions", 10).stream()
                .map(CompoundTag.class::cast)
                .map(NbtUtils::readBlockPos)
                .collect(Collectors.toSet());
    }
}
