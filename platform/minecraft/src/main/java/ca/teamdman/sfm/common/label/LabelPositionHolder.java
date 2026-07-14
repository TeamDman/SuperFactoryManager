package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.BlockPosIterator;
import ca.teamdman.sfm.common.util.BlockPosSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UnusedReturnValue")
public record LabelPositionHolder(Map<String, BlockPosSet> labels) {
    @SFMLocalizationDatagen
    public static final LocalizationEntry DISK_ITEM_TOOLTIP_LABEL_HEADER = new LocalizationEntry(
            () -> SFMItems.DISK.get().getDescriptionId() + ".tooltip.label_section.header",
            () -> "Labels"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DISK_ITEM_TOOLTIP_LABEL = new LocalizationEntry(
            () -> SFMItems.DISK.get().getDescriptionId() + ".tooltip.label_section.entry",
            () -> " - %s: %d blocks"
    );


    public static final StreamCodec<FriendlyByteBuf, LabelPositionHolder> STREAM_CODEC = StreamCodec.ofMember(
            LabelPositionHolder::encode,
            LabelPositionHolder::decode
    );

    public static final MapCodec<LabelPositionHolder> CODEC =
            RecordCodecBuilder.mapCodec(
                    builder -> builder.group(
                            Codec.unboundedMap(
                                    Codec.STRING,
                                    BlockPos.CODEC.listOf().xmap(HashSet::new, ArrayList::new)
                            ).fieldOf("labels").forGetter(labelPositionHolder -> {
                                Map<String, HashSet<BlockPos>> rtn = new HashMap<>();
                                Map<String, BlockPosSet> data = labelPositionHolder.labels();
                                for (Map.Entry<String, BlockPosSet> entry : data.entrySet()) {
                                    HashSet<BlockPos> positions = new HashSet<>();
                                    entry.getValue().blockPosIterator().forEach(pos -> positions.add(pos.immutable()));
                                    rtn.put(entry.getKey(), positions);
                                }
                                return rtn;
                            })
                    ).apply(
                            builder, data -> {
                                Map<String, BlockPosSet> map = new HashMap<>();
                                data.forEach((key, value) -> map.put(key, new BlockPosSet(value)));
                                return new LabelPositionHolder(map);
                            }
                    )
            );

    private final static WeakHashMap<ItemStack, LabelPositionHolder> CACHE = new WeakHashMap<>();

    private LabelPositionHolder() {

        this(new HashMap<>());
    }

    private LabelPositionHolder(LabelPositionHolder other) {

        this();
        other.labels().forEach((key, value) -> this.labels().put(key, new BlockPosSet(value)));
    }

    public static void encode(
            LabelPositionHolder labelPositionHolder,
            FriendlyByteBuf friendlyByteBuf
    ) {

        friendlyByteBuf.writeVarInt(labelPositionHolder.labels().size());
        for (Map.Entry<String, BlockPosSet> entry : labelPositionHolder.labels().entrySet()) {
            String label = entry.getKey();
            BlockPosSet positions = entry.getValue();
            friendlyByteBuf.writeUtf(label);
            friendlyByteBuf.writeVarInt(positions.size());
            positions.blockPosIterator().forEach(friendlyByteBuf::writeBlockPos);
        }
    }

    public static LabelPositionHolder decode(FriendlyByteBuf friendlyByteBuf) {

        LabelPositionHolder rtn = LabelPositionHolder.empty();
        int size = friendlyByteBuf.readVarInt();
        for (int i = 0; i < size; i++) {
            String label = friendlyByteBuf.readUtf();
            int positionsSize = friendlyByteBuf.readVarInt();
            BlockPosSet positions = new BlockPosSet();
            for (int j = 0; j < positionsSize; j++) {
                positions.add(friendlyByteBuf.readBlockPos());
            }
            rtn.labels().put(label, positions);
        }
        return rtn;
    }

    /**
     * Get the label position holder for this disk.
     * <p>
     * Saves it in the cache for faster future lookups.
     * <p>
     * This mutably borrows the cache entry. Call toOwned if you want a copy you can modify without affecting the cache.
     */
    public static LabelPositionHolder from(ItemStack stack) {
        // TODO: make this return an immutable copy instead of mutably borrowing the cache entry
        return CACHE.computeIfAbsent(
                stack,
                s -> from((DataComponentGetter) stack)
        );
    }

    public static LabelPositionHolder from(DataComponentGetter components) {
        LabelPositionHolder immutableLabelPositionHolder = components.get(SFMDataComponents.LABEL_POSITION_HOLDER.get());
        if (immutableLabelPositionHolder == null) {
            return new LabelPositionHolder();
        }
        return new LabelPositionHolder(immutableLabelPositionHolder);
    }

    /**
     * Returns an owned label view without mutating the label cache.
     */
    public static LabelPositionHolder fromReadOnly(ItemStack stack) {

        var cached = CACHE.get(stack);
        if (cached != null) {
            return cached.toOwned();
        }
        return from((DataComponentGetter) stack);
    }

    public static LabelPositionHolder empty() {

        return new LabelPositionHolder();
    }

    public LabelPositionHolder save(ItemStack stack) {

        LabelPositionHolder copy = new LabelPositionHolder(this);
        stack.set(SFMDataComponents.LABEL_POSITION_HOLDER, copy);
        CACHE.put(stack, copy);
        return this;
    }

    public static void clear(ItemStack stack) {

        stack.remove(SFMDataComponents.LABEL_POSITION_HOLDER);
        CACHE.remove(stack);
    }

    public boolean contains(
            String label,
            BlockPos pos
    ) {

        BlockPosSet positionsForLabel = this.labels().get(label);
        if (positionsForLabel == null) {
            return false;
        } else {
            return positionsForLabel.contains(pos);
        }
    }

    public BlockPosSet getPositions(String label) {

        return labels().getOrDefault(label, new BlockPosSet());
    }

    public BlockPosSet getPositionsMut(String label) {

        return labels().computeIfAbsent(label, s -> new BlockPosSet());
    }

    public LabelPositionHolder addAll(
            String label,
            Collection<BlockPos> positions
    ) {

        if (label.isBlank()) return this;
        getPositionsMut(label).addAllPositions(positions);
        return this;
    }

    public LabelPositionHolder addAll(
            String label,
            BlockPosIterator positions
    ) {

        if (label.isBlank()) return this;
        BlockPosSet positionsForLabel = getPositionsMut(label);
        positions.forEachLong(positionsForLabel::add);
        return this;
    }

    public LabelPositionHolder addReferencedLabel(String label) {

        getPositionsMut(label);
        return this;
    }

    public List<Component> asHoverText() {

        var rtn = new ArrayList<Component>();
        if (labels().isEmpty()) return rtn;
        rtn.add(DISK_ITEM_TOOLTIP_LABEL_HEADER
                        .getComponent()
                        .withStyle(ChatFormatting.UNDERLINE));
        for (var entry : labels().entrySet()) {
            rtn.add(DISK_ITEM_TOOLTIP_LABEL.getComponent(
                    entry.getKey(),
                    entry.getValue().size()
            ).withStyle(ChatFormatting.GRAY));
        }
        return rtn;
    }

    public String toDebugString() {

        int total = 0;
        StringBuilder rtn = new StringBuilder();
        for (var entry : labels().entrySet()) {
            rtn
                    .append("-- * ")
                    .append(entry.getKey())
                    .append(" - ")
                    .append(entry.getValue().size())
                    .append(" positions\n");
            total += entry.getValue().size();
        }
        return "-- LabelPositionHolder - " + total + " total labels\n" + rtn;
    }

    @SuppressWarnings("unused")
    public LabelPositionHolder removeAll(BlockPos blockPos) {

        labels().values().forEach(list -> list.remove(blockPos));
        return this;
    }

    public LabelPositionHolder removeAll(long blockPosLong) {

        labels().values().forEach(list -> list.remove(blockPosLong));
        return this;
    }

    public LabelPositionHolder prune() {

        labels().entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return this;
    }

    public LabelPositionHolder clear() {

        labels().clear();
        return this;
    }

    public LabelPositionHolder add(
            String label,
            BlockPos position
    ) {

        if (label.isBlank()) return this;
        getPositionsMut(label).add(position);
        return this;
    }

    public LabelPositionHolder remove(
            String label,
            BlockPos pos
    ) {

        getPositionsMut(label).remove(pos);
        return this;
    }

    public LabelPositionHolder remove(
            String label,
            long blockPosLong
    ) {

        getPositionsMut(label).remove(blockPosLong);
        return this;
    }

    public LabelPositionHolder removeIf(BiPredicate<String, BlockPos> predicate) {

        labels().forEach((label, positions) -> positions.removeIfPosition(pos -> predicate.test(label, pos)));
        return this;
    }

    public LabelPositionHolder removeIf(Predicate<String> predicate) {

        labels().keySet().removeIf(predicate);
        return this;
    }

    public LabelPositionHolder forEach(BiConsumer<String, BlockPos> consumer) {

        labels().forEach((label, positions) -> positions
                .blockPosIterator()
                .forEach(pos -> consumer.accept(label, pos.immutable())));
        return this;
    }

    @Override
    public String toString() {

        return "LabelPositionHolder{size=" + labels().values().stream().mapToInt(Set::size).sum() + "; " +
               labels()
                       .entrySet()
                       .stream()
                       .map(entry -> entry.getKey() + "=" + entry.getValue().size())
                       .collect(Collectors.joining(", ")) +
               "}";
    }

    public LabelPositionHolder toOwned() {

        return new LabelPositionHolder(this);
    }

    public Set<String> getLabels(BlockPos pos) {

        return labels().entrySet().stream()
                .filter(entry -> entry.getValue().contains(pos))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Set<String> getLabels(long blockPosLong) {

        return labels().entrySet().stream()
                .filter(entry -> entry.getValue().contains(blockPosLong))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean isEmpty() {

        return labels().isEmpty();
    }

}
