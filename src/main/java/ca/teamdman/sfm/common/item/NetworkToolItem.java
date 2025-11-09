package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolUsePacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NetworkToolItem extends Item {
    public NetworkToolItem() {
        super();
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote) return EnumActionResult.SUCCESS;
        SFMPackets.sendToServer(new ServerboundNetworkToolUsePacket(
                pos,
                side
        ));
        return EnumActionResult.SUCCESS;
    }

        @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(
            ItemStack stack,
            @Nullable World level,
            List<String> lines,
            ITooltipFlag detail
    ) {
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_1.getComponent().setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText());
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_2.getComponent().setStyle(new Style().setColor(TextFormatting.GRAY)).getFormattedText());
        lines.add(
                LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_3
                        .getComponent(SFMKeyMappings.CONTAINER_INSPECTOR_KEY.getDisplayName())
                        .setStyle(new Style().setColor(TextFormatting.AQUA)).getFormattedText()
        );
    }


    @Override
    public void onUpdate(ItemStack pStack, World pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        if (pLevel.isRemote) return;
        if (!(pEntity instanceof EntityPlayer pPlayer)) return;
        boolean isInHand = pStack == pPlayer.getHeldItemMainhand() || pStack == pPlayer.getHeldItemOffhand();
        if (!isInHand) return;
        boolean shouldRefresh = pEntity.ticksExisted % 20 == 0;
        if (!shouldRefresh) return;
        final long maxDistance = 128;
        Set<BlockPos> cablePositions = CableNetworkManager
                .getNetworksInRange(pLevel, pEntity.getPosition(), maxDistance)
                .flatMap(CableNetwork::getCablePositions)
                .collect(Collectors.toSet());
        setCablePositions(pStack, cablePositions);

        Set<BlockPos> capabilityProviderPositions = CableNetworkManager
                .getNetworksInRange(pLevel, pEntity.getPosition(), maxDistance)
                .flatMap(CableNetwork::getCapabilityProviderPositions)
                .collect(Collectors.toSet());
        setCapabilityProviderPositions(pStack, capabilityProviderPositions);
    }


    public static boolean getOverlayEnabled(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            return true;
        }
        return !stack.getTagCompound().getBoolean("sfm:network_tool_overlay_disabled");
    }

    public static void setOverlayEnabled(
            ItemStack stack,
            boolean value
    ) {
        if (value) {
            stack.removeSubCompound("sfm:network_tool_overlay_disabled");
        } else {
            stack.setTagInfo("sfm:network_tool_overlay_disabled", new NBTTagByte((byte)1));
        }
    }

    public static void setCablePositions(
            ItemStack stack,
            Set<BlockPos> positions
    ) {
        stack.setTagInfo(
                "sfm:cable_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static Set<BlockPos> getCablePositions(ItemStack stack) {
        if (stack.getTagCompound() != null
                && stack.getTagCompound().getTag("sfm:cable_positions") instanceof NBTTagByteArray byteArrayTag) {
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        return Collections.emptySet();
    }

    public static void setCapabilityProviderPositions(
            ItemStack stack,
            Set<BlockPos> positions
    ) {
        stack.setTagInfo(
                "sfm:capability_provider_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static Set<BlockPos> getCapabilityProviderPositions(ItemStack stack) {
        if (stack.getTagCompound() != null
                && stack.getTagCompound().getTag("sfm:capability_provider_positions") instanceof NBTTagByteArray byteArrayTag) {
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        return Collections.emptySet();
    }
}
