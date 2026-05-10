package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolUsePacket;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntFunction;
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

    public NetworkToolItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(
            ItemStack stack,
            UseOnContext ctx
    ) {

        var level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (level.isClientSide() && player != null) {
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
    public void inventoryTick(
            ItemStack pStack,
            ServerLevel pLevel,
            Entity pEntity,
            @Nullable EquipmentSlot slot
    ) {

        if (pLevel.isClientSide()) return;
        if (!(pEntity instanceof Player pPlayer)) return;
        boolean isInHand = pStack == pPlayer.getMainHandItem() || pStack == pPlayer.getOffhandItem();
        if (!isInHand) return;
        boolean shouldRefresh = pEntity.tickCount % 20 == 0;
        if (!shouldRefresh) return;
        regenerateCablePositions(pStack, pLevel, pPlayer);

        // Remove the data stored by older versions of the mod
//        pStack.remove(SFMDataComponents.NETWORKS);
//        pStack.getOrCreateTag().remove("networks");
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
        return stack.getOrDefault(SFMDataComponents.NETWORK_TOOL_OVERLAY_MODE, NetworkToolOverlayMode.SHOW_ALL);
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
        stack.set(SFMDataComponents.NETWORK_TOOL_SELECTED_BLOCK_POS, pos);
    }

    @Nullable
    public static BlockPos getSelectedNetworkBlockPos(ItemStack stack) {
        return stack.get(SFMDataComponents.NETWORK_TOOL_SELECTED_BLOCK_POS);
    }

    public static void setCablePositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

        stack.set(SFMDataComponents.CABLE_POSITIONS, CompressedBlockPosSet.from(positions));
    }

    public static BlockPosSet getCablePositions(ItemStack stack) {
        return stack.getOrDefault(SFMDataComponents.CABLE_POSITIONS, new CompressedBlockPosSet()).into();
    }

    public static void setCapabilityProviderPositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

        stack.set(SFMDataComponents.CAPABILITY_POSITIONS, CompressedBlockPosSet.from(positions));
    }

    public static BlockPosSet getCapabilityProviderPositions(ItemStack stack) {
        return stack.getOrDefault(SFMDataComponents.CAPABILITY_POSITIONS, new CompressedBlockPosSet()).into();
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
        stack.set(SFMDataComponents.NETWORK_TOOL_OVERLAY_MODE, mode);
        if (mode != NetworkToolOverlayMode.SHOW_SELECTED_NETWORK) {
            stack.remove(SFMDataComponents.NETWORK_TOOL_SELECTED_BLOCK_POS);
        }
        stack.remove(SFMDataComponents.OVERLAY_ENABLED);
    }

    public enum NetworkToolOverlayMode implements StringRepresentable {
        SHOW_ALL,
        SHOW_SELECTED_NETWORK,
        HIDDEN
        ;

        public static final com.mojang.serialization.Codec<NetworkToolOverlayMode> CODEC = StringRepresentable.fromEnum(NetworkToolOverlayMode::values);
        public static final IntFunction<NetworkToolOverlayMode> BY_ID = ByIdMap.continuous(
                NetworkToolOverlayMode::ordinal,
                values(),
                ByIdMap.OutOfBoundsStrategy.WRAP
        );
        public static final StreamCodec<io.netty.buffer.ByteBuf, NetworkToolOverlayMode> STREAM_CODEC =
                ByteBufCodecs.idMapper(BY_ID, NetworkToolOverlayMode::ordinal);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

}
