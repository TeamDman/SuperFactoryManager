package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolUsePacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTUtil;
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

import java.util.List;
import java.util.stream.Stream;

public class NetworkToolItem extends Item {
    public NetworkToolItem() {

        super();
        setMaxStackSize(1);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (!world.isRemote) return EnumActionResult.SUCCESS;
        boolean pickBlock = SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY);
        ServerboundNetworkToolUsePacket msg = new ServerboundNetworkToolUsePacket(
                hand,
                pos,
                side,
                pickBlock
        );
        SFMPackets.sendToServer(msg);
        if (pickBlock) {
            // we don't want to toggle the overlay if we're using pick-block
            NetworkToolKeyMappingHandler.setExternalDebounce();
        }
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
        lines.add(
            LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_8
                .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY))
                .setStyle(new Style().setColor(TextFormatting.AQUA)).getFormattedText()
        );
        var purple = new Style().setColor(TextFormatting.LIGHT_PURPLE);
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_4.getComponent().setStyle(purple).getFormattedText());
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_5.getComponent().setStyle(purple).getFormattedText());
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_6.getComponent().setStyle(purple).getFormattedText());
        lines.add(LocalizationKeys.NETWORK_TOOL_ITEM_TOOLTIP_7.getComponent().setStyle(purple).getFormattedText());
    }


    @Override
    public void onUpdate(ItemStack pStack, World pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        if (pLevel.isRemote) return;
        if (!(pEntity instanceof EntityPlayer pPlayer)) return;
        boolean isInHand = pStack == pPlayer.getHeldItemMainhand() || pStack == pPlayer.getHeldItemOffhand();
        if (!isInHand) return;
        boolean shouldRefresh = pEntity.ticksExisted % 20 == 0;
        if (!shouldRefresh) return;
        regenerateCablePositions(pStack, pLevel, pPlayer);

    }

    public static void regenerateCablePositions(ItemStack pStack, World pLevel, EntityPlayer pPlayer) {
        // Initialize with default capacity
        // We don't know how many *unique* positions we are going to see
        BlockPosSet cablePositions = new BlockPosSet();
        BlockPosSet capabilityProviderPositions = new BlockPosSet();

        // Find the networks and track the positions
        for (CableNetwork cableNetwork : (Iterable<CableNetwork>) getNetworksForOverlay(pStack, pLevel, pPlayer)::iterator) {
            cablePositions.addAll(cableNetwork.getCablePositionsRaw());
            capabilityProviderPositions.addAll(cableNetwork.getCapabilityProviderPositionsRaw());
        }

        // Update the item data
        setCablePositions(pStack, cablePositions);
        setCapabilityProviderPositions(pStack, capabilityProviderPositions);
    }

    protected static Stream<CableNetwork> getNetworksForOverlay(
            ItemStack pStack,
            World pLevel,
            EntityPlayer pPlayer
    ) {
        final long maxDistance = 128;

        BlockPos blockPos = getOverlayMode(pStack) == NetworkToolOverlayMode.SHOW_SELECTED_NETWORK
                ? getSelectedNetworkBlockPos(pStack)
                : null;

        if (blockPos != null) {
            var net = CableNetworkManager.getOrRegisterNetworkFromCablePosition(pLevel, blockPos);
            return net.map(Stream::of).orElseGet(Stream::empty);
        } else {
            return CableNetworkManager.getNetworksInRange(pLevel, pPlayer.getPosition(), maxDistance);
        }
    }

    public static boolean getOverlayEnabled(ItemStack stack) {

        return getOverlayMode(stack) != NetworkToolOverlayMode.HIDDEN;
    }

    /**
     * Returns the current enum mode for the label gun item.
     */
    public static NetworkToolOverlayMode getOverlayMode(ItemStack stack) {
        if (stack.getTagCompound() == null) {
            return NetworkToolOverlayMode.SHOW_ALL;
        }
        int ordinal = stack.getTagCompound().getInteger("sfm:network_tool_overlay_mode");
        // fallback if out of bounds or missing
        if (ordinal < 0 || ordinal >= NetworkToolOverlayMode.values().length) {
            return NetworkToolOverlayMode.SHOW_ALL;
        }
        return NetworkToolOverlayMode.values()[ordinal];
    }


    public static void cycleOverlayMode(ItemStack stack) {
        NetworkToolOverlayMode current = getOverlayMode(stack);
        NetworkToolOverlayMode newMode = current == NetworkToolOverlayMode.SHOW_ALL
                ? NetworkToolOverlayMode.HIDDEN
                : NetworkToolOverlayMode.SHOW_ALL;
        setOverlayMode(stack, newMode);
    }

    public static void setSelectedNetworkBlockPos(ItemStack stack, BlockPos pos) {
        setOverlayMode(stack, NetworkToolOverlayMode.SHOW_SELECTED_NETWORK);
        stack.setTagInfo("sfm:selected_network_block_pos", NBTUtil.createPosTag(pos));
    }

    @Nullable
    public static BlockPos getSelectedNetworkBlockPos(ItemStack stack) {
        var compound = stack.getSubCompound("sfm:selected_network_block_pos");
        return compound != null ? NBTUtil.getPosFromTag(compound) : null;
    }

    public static void setCablePositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

        stack.setTagInfo(
                "sfm:cable_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static BlockPosSet getCablePositions(ItemStack stack) {

        if (stack.getTagCompound() != null
                && stack.getTagCompound().getTag("sfm:cable_positions") instanceof NBTTagByteArray byteArrayTag) {
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        return new BlockPosSet();
    }

    public static void setCapabilityProviderPositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

        stack.setTagInfo(
                "sfm:capability_provider_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static BlockPosSet getCapabilityProviderPositions(ItemStack stack) {

        if (stack.getTagCompound() != null
                && stack.getTagCompound().getTag("sfm:capability_provider_positions") instanceof NBTTagByteArray byteArrayTag) {
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        return new BlockPosSet();
    }

    public enum NetworkToolOverlayMode {
        SHOW_ALL,
        SHOW_SELECTED_NETWORK,
        HIDDEN
    }

    /**
     * Sets the view mode in NBT.
     */
    protected static void setOverlayMode(
            ItemStack stack,
            NetworkToolOverlayMode mode
    ) {

        stack.setTagInfo("sfm:network_tool_overlay_mode",new NBTTagInt(mode.ordinal()));
        assert stack.getTagCompound() != null;
        if (mode != NetworkToolOverlayMode.SHOW_SELECTED_NETWORK) {
            stack.getTagCompound().removeTag("sfm:selected_network_block_pos");
        }

        // remove the data stored by older versions of the mod
        stack.getTagCompound().removeTag("network_tool_overlay_disabled");
    }

}
