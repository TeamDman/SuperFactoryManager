package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolUsePacket;
import ca.teamdman.sfm.common.registry.registration.SFMCreativeTabs;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.BlockPosSet;
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
import java.util.stream.Stream;

public class NetworkToolItem extends Item {

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_1 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.1",
            () -> "Shows cables through walls when held."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_2 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.2",
            () -> "Right click a block face to view diagnostic info."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_3 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.3",
            () -> "You might not need this, don't forget you can press %s in an inventory to toggle the inspector."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_4 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.4",
            () -> "Place in off-hand with block in main hand and right-click cable to set facade."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_5 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.5",
            () -> "Ctrl-click to facade contiguously."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_6 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.6",
            () -> "Alt-click to facade matching block across the network."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_7 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.7",
            () -> "Ctrl-alt-click to facade entire network."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM_TOOLTIP_8 = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId() + ".tooltip.8",
            () -> "Hold %s and right-click a block to attune the tool to that position."
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry NETWORK_TOOL_ITEM = new LocalizationEntry(
            () -> SFMItems.NETWORK_TOOL.get().getDescriptionId(),
            () -> "Network Tool"
    );

    public NetworkToolItem() {

        super(new Item.Properties().stacksTo(1).tab(SFMCreativeTabs.MAIN));
    }

    @Override
    public InteractionResult onItemUseFirst(
            ItemStack stack,
            UseOnContext ctx
    ) {

        var level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (level.isClientSide && player != null) {
            boolean pickBlock = SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY);
            ServerboundNetworkToolUsePacket msg = new ServerboundNetworkToolUsePacket(
                    ctx.getHand(),
                    ctx.getClickedPos(),
                    ctx.getClickedFace(),
                    pickBlock
            );
            SFMPackets.sendToServer(msg);
            if (pickBlock) {
                // we don't want to toggle the overlay if we're using pick-block
                NetworkToolKeyMappingHandler.setExternalDebounce();
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

        lines.add(NETWORK_TOOL_ITEM_TOOLTIP_1.getComponent().withStyle(ChatFormatting.GRAY));
        lines.add(NETWORK_TOOL_ITEM_TOOLTIP_2.getComponent().withStyle(ChatFormatting.GRAY));
        lines.add(
                NETWORK_TOOL_ITEM_TOOLTIP_3
                        .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CONTAINER_INSPECTOR_KEY))
                        .withStyle(ChatFormatting.AQUA)
        );
        lines.add(
                NETWORK_TOOL_ITEM_TOOLTIP_8
                        .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY))
                        .withStyle(ChatFormatting.AQUA)
        );
        lines.add(NETWORK_TOOL_ITEM_TOOLTIP_4.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(NETWORK_TOOL_ITEM_TOOLTIP_5.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(NETWORK_TOOL_ITEM_TOOLTIP_6.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(NETWORK_TOOL_ITEM_TOOLTIP_7.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
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
        regenerateCablePositions(pStack, pLevel, pPlayer);

        // Remove the data stored by older versions of the mod
        pStack.getOrCreateTag().remove("networks");
    }

    public static void regenerateCablePositions(
            ItemStack pStack,
            Level pLevel,
            Player pPlayer
    ) {
        // Initialize with default capacity
        // We don't know how many *unique* positions we are going to see
        BlockPosSet cablePositions = new BlockPosSet();
        BlockPosSet capabilityProviderPositions = new BlockPosSet();

        // Find the networks and track the positions
        for (CableNetwork cableNetwork : (Iterable<CableNetwork>) getNetworksForOverlay(
                pStack,
                pLevel,
                pPlayer
        )::iterator) {
            cablePositions.addAll(cableNetwork.getCablePositionsRaw());
            capabilityProviderPositions.addAll(cableNetwork.getCapabilityProviderPositionsRaw());
        }

        // Update the item data
        setCablePositions(pStack, cablePositions);
        setCapabilityProviderPositions(pStack, capabilityProviderPositions);
    }

    public static boolean getOverlayEnabled(ItemStack stack) {

        return getOverlayMode(stack) != NetworkToolOverlayMode.HIDDEN;
    }

    /**
     * Returns the current enum mode for the network tool item.
     */
    public static NetworkToolOverlayMode getOverlayMode(ItemStack stack) {

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("sfm:network_tool_overlay_disabled") && tag.getBoolean("sfm:network_tool_overlay_disabled")) {
            return NetworkToolOverlayMode.HIDDEN;
        }

        int ordinal = tag.getInt("sfm:network_tool_overlay_mode");
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

    public static void setSelectedNetworkBlockPos(
            ItemStack stack,
            BlockPos pos
    ) {

        setOverlayMode(stack, NetworkToolOverlayMode.SHOW_SELECTED_NETWORK);
        stack.getOrCreateTag().put("sfm:selected_network_block_pos", NbtUtils.writeBlockPos(pos));
    }

    @Nullable
    public static BlockPos getSelectedNetworkBlockPos(ItemStack stack) {

        return stack.getOrCreateTag().contains("sfm:selected_network_block_pos")
               ? NbtUtils.readBlockPos(stack.getOrCreateTag().getCompound("sfm:selected_network_block_pos"))
               : null;
    }

    public static void setCablePositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

        stack.getOrCreateTag().put(
                "sfm:cable_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static BlockPosSet getCablePositions(ItemStack stack) {

        if (stack.getOrCreateTag().get("sfm:cable_positions") instanceof ByteArrayTag byteArrayTag) {
            // new format
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        // fallback to the old format
        return stack.getOrCreateTag().getList("sfm:cable_positions", 10).stream()
                .map(CompoundTag.class::cast)
                .map(NbtUtils::readBlockPos)
                .collect(BlockPosSet.collector());
    }

    public static void setCapabilityProviderPositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

        stack.getOrCreateTag().put(
                "sfm:capability_provider_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
    }

    public static BlockPosSet getCapabilityProviderPositions(ItemStack stack) {

        if (stack.getOrCreateTag().get("sfm:capability_provider_positions") instanceof ByteArrayTag byteArrayTag) {
            // new format
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        // fallback to the old format
        return stack.getOrCreateTag().getList("sfm:capability_provider_positions", 10).stream()
                .map(CompoundTag.class::cast)
                .map(NbtUtils::readBlockPos)
                .collect(BlockPosSet.collector());
    }

    protected static Stream<CableNetwork> getNetworksForOverlay(
            ItemStack pStack,
            Level pLevel,
            Player pPlayer
    ) {

        final long maxDistance = 128;

        BlockPos blockPos = getOverlayMode(pStack) == NetworkToolOverlayMode.SHOW_SELECTED_NETWORK
                            ? getSelectedNetworkBlockPos(pStack)
                            : null;

        if (blockPos != null) {
            return CableNetworkManager.getOrRegisterNetworkFromCablePosition(pLevel, blockPos).stream();
        } else {
            return CableNetworkManager.getNetworksInRange(pLevel, pPlayer.blockPosition(), maxDistance);
        }
    }

    /**
     * Sets the view mode in NBT.
     */
    protected static void setOverlayMode(
            ItemStack stack,
            NetworkToolOverlayMode mode
    ) {

        stack.getOrCreateTag().putInt("sfm:network_tool_overlay_mode", mode.ordinal());
        if (mode != NetworkToolOverlayMode.SHOW_SELECTED_NETWORK) {
            stack.getOrCreateTag().remove("sfm:selected_network_block_pos");
        }

        // remove the data stored by older versions of the mod
        stack.getOrCreateTag().remove("sfm:network_tool_overlay_disabled");
    }

    public enum NetworkToolOverlayMode {
        SHOW_ALL,
        SHOW_SELECTED_NETWORK,
        HIDDEN
    }

}
