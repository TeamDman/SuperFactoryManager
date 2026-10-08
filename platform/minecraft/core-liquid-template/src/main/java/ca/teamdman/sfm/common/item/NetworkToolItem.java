package ca.teamdman.sfm.common.item;

import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundNetworkToolUsePacket;
{% case minecraft_version %}
{% when '1.19.2' %}
import ca.teamdman.sfm.common.registry.registration.SFMCreativeTabs;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
{% when '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.core.component.DataComponentGetter;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
{% when '26.1.2' %}
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
{% endcase %}
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.entity.EquipmentSlot;
{% endcase %}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.item.component.TooltipProvider;
{% endcase %}
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import java.util.List;
{% when '1.21', '1.21.1' %}
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
{% when '26.1.2' %}
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
{% endcase %}
import java.util.stream.Stream;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
public class NetworkToolItem extends Item {
{% when '26.1.2' %}
public class NetworkToolItem extends Item implements TooltipProvider {
{% endcase %}

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

{% case minecraft_version %}
{% when '1.19.2' %}
    public NetworkToolItem() {
        super(new Item.Properties().stacksTo(1).tab(SFMCreativeTabs.MAIN));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public NetworkToolItem() {
        super(new Item.Properties().stacksTo(1));
{% when '26.1.2' %}
    public NetworkToolItem(Properties properties) {
        super(properties.stacksTo(1));
{% endcase %}
    }

    @Override
    public InteractionResult onItemUseFirst(
            ItemStack stack,
            UseOnContext ctx
    ) {

        var level = ctx.getLevel();
        Player player = ctx.getPlayer();
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (level.isClientSide && player != null) {
{% when '26.1.2' %}
        if (level.isClientSide() && player != null) {
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
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

{% when '1.21', '1.21.1' %}
    @Override
    public void appendHoverText(
            ItemStack pStack,
            TooltipContext pContext,
            List<Component> lines,
            TooltipFlag pTooltipFlag
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

{% when '26.1.2' %}
{% endcase %}
    @Override
    public void inventoryTick(
            ItemStack pStack,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            Level pLevel,
{% when '26.1.2' %}
            ServerLevel pLevel,
{% endcase %}
            Entity pEntity,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            int pSlotId,
            boolean pIsSelected
{% when '26.1.2' %}
            @Nullable EquipmentSlot slot
{% endcase %}
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (pLevel.isClientSide) return;
{% when '26.1.2' %}
        if (pLevel.isClientSide()) return;
{% endcase %}
        if (!(pEntity instanceof Player pPlayer)) return;
        boolean isInHand = pStack == pPlayer.getMainHandItem() || pStack == pPlayer.getOffhandItem();
        if (!isInHand) return;
        boolean shouldRefresh = pEntity.tickCount % 20 == 0;
        if (!shouldRefresh) return;
        regenerateCablePositions(pStack, pLevel, pPlayer);

        // Remove the data stored by older versions of the mod
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        pStack.getOrCreateTag().remove("networks");
{% when '1.21', '1.21.1', '26.1.2' %}
//        pStack.remove(SFMDataComponents.NETWORKS);
//        pStack.getOrCreateTag().remove("networks");
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

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
{% when '1.21', '1.21.1', '26.1.2' %}
        return stack.getOrDefault(SFMDataComponents.NETWORK_TOOL_OVERLAY_MODE, NetworkToolOverlayMode.SHOW_ALL);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        stack.getOrCreateTag().put("sfm:selected_network_block_pos", NbtUtils.writeBlockPos(pos));
{% when '1.21', '1.21.1', '26.1.2' %}
        stack.set(SFMDataComponents.NETWORK_TOOL_SELECTED_BLOCK_POS, pos);
{% endcase %}
    }

    @Nullable
    public static BlockPos getSelectedNetworkBlockPos(ItemStack stack) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return stack.getOrCreateTag().contains("sfm:selected_network_block_pos")
               ? NbtUtils.readBlockPos(stack.getOrCreateTag().getCompound("sfm:selected_network_block_pos"))
               : null;
{% when '1.21', '1.21.1', '26.1.2' %}
        return stack.get(SFMDataComponents.NETWORK_TOOL_SELECTED_BLOCK_POS);
{% endcase %}
    }

    public static void setCablePositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        stack.getOrCreateTag().put(
                "sfm:cable_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
{% when '1.21', '1.21.1', '26.1.2' %}
        stack.set(SFMDataComponents.CABLE_POSITIONS, CompressedBlockPosSet.from(positions));
{% endcase %}
    }

    public static BlockPosSet getCablePositions(ItemStack stack) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

        if (stack.getOrCreateTag().get("sfm:cable_positions") instanceof ByteArrayTag byteArrayTag) {
            // new format
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        // fallback to the old format
        return stack.getOrCreateTag().getList("sfm:cable_positions", 10).stream()
                .map(CompoundTag.class::cast)
                .map(NbtUtils::readBlockPos)
                .collect(BlockPosSet.collector());
{% when '1.21', '1.21.1', '26.1.2' %}
        return stack.getOrDefault(SFMDataComponents.CABLE_POSITIONS, new CompressedBlockPosSet()).into();
{% endcase %}
    }

    public static void setCapabilityProviderPositions(
            ItemStack stack,
            BlockPosSet positions
    ) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        stack.getOrCreateTag().put(
                "sfm:capability_provider_positions",
                CompressedBlockPosSet.from(positions).asTag()
        );
{% when '1.21', '1.21.1', '26.1.2' %}
        stack.set(SFMDataComponents.CAPABILITY_POSITIONS, CompressedBlockPosSet.from(positions));
{% endcase %}
    }

    public static BlockPosSet getCapabilityProviderPositions(ItemStack stack) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

        if (stack.getOrCreateTag().get("sfm:capability_provider_positions") instanceof ByteArrayTag byteArrayTag) {
            // new format
            return CompressedBlockPosSet.from(byteArrayTag).into();
        }
        // fallback to the old format
        return stack.getOrCreateTag().getList("sfm:capability_provider_positions", 10).stream()
                .map(CompoundTag.class::cast)
                .map(NbtUtils::readBlockPos)
                .collect(BlockPosSet.collector());
{% when '1.21', '1.21.1', '26.1.2' %}
        return stack.getOrDefault(SFMDataComponents.CAPABILITY_POSITIONS, new CompressedBlockPosSet()).into();
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

        stack.getOrCreateTag().putInt("sfm:network_tool_overlay_mode", mode.ordinal());
{% when '1.21', '1.21.1', '26.1.2' %}
        stack.set(SFMDataComponents.NETWORK_TOOL_OVERLAY_MODE, mode);
{% endcase %}
        if (mode != NetworkToolOverlayMode.SHOW_SELECTED_NETWORK) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            stack.getOrCreateTag().remove("sfm:selected_network_block_pos");
{% when '1.21', '1.21.1', '26.1.2' %}
            stack.remove(SFMDataComponents.NETWORK_TOOL_SELECTED_BLOCK_POS);
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}

        // remove the data stored by older versions of the mod
        stack.getOrCreateTag().remove("sfm:network_tool_overlay_disabled");
{% when '1.21', '1.21.1', '26.1.2' %}
        stack.remove(SFMDataComponents.OVERLAY_ENABLED);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public enum NetworkToolOverlayMode {
{% when '1.21', '1.21.1' %}
    public enum NetworkToolOverlayMode implements StringRepresentable {
{% when '26.1.2' %}
    @Override
    public void addToTooltip(TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_1.getComponent().withStyle(ChatFormatting.GRAY));
        consumer.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_2.getComponent().withStyle(ChatFormatting.GRAY));
        consumer.accept(
                NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_3
                        .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.CONTAINER_INSPECTOR_KEY))
                        .withStyle(ChatFormatting.AQUA)
        );
        consumer.accept(
                NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_8
                        .getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY))
                        .withStyle(ChatFormatting.AQUA)
        );
        consumer.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_4.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        consumer.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_5.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        consumer.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_6.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
        consumer.accept(NetworkToolItem.NETWORK_TOOL_ITEM_TOOLTIP_7.getComponent().withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public enum NetworkToolOverlayMode implements StringRepresentable {
{% endcase %}
        SHOW_ALL,
        SHOW_SELECTED_NETWORK,
        HIDDEN
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
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
{% endcase %}
    }

}
