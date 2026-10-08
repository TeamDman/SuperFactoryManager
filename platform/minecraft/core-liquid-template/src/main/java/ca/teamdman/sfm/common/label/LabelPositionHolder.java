package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMDataComponents;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.BlockPosIterator;
import ca.teamdman.sfm.common.util.BlockPosSet;
{% if features.label_readonly_access %}
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
{% when "1.21", "1.21.1", "26.1.2" %}
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
{% endcase %}
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.nbt.*;
{% when "1.21", "1.21.1" %}
import net.minecraft.network.FriendlyByteBuf;
{% when "26.1.2" %}
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.FriendlyByteBuf;
{% endcase %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.network.codec.StreamCodec;
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}

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

{% endcase %}
    private final static WeakHashMap<ItemStack, LabelPositionHolder> CACHE = new WeakHashMap<>();

    private LabelPositionHolder() {

        this(new HashMap<>());
    }

    private LabelPositionHolder(LabelPositionHolder other) {

        this();
        other.labels().forEach((key, value) -> this.labels().put(key, new BlockPosSet(value)));
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
    public static void encode(
            LabelPositionHolder labelPositionHolder,
            FriendlyByteBuf friendlyByteBuf
    ) {
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
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

{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
                stack,
                s -> {
                    var tag = stack.getOrCreateTag().getCompound("sfm:labels");
                    return deserialize(tag);
                }
{% when "1.21", "1.21.1" %}
                stack,
                s -> {
                    LabelPositionHolder immutableLabelPositionHolder = stack.get(SFMDataComponents.LABEL_POSITION_HOLDER);
                    if (immutableLabelPositionHolder == null) {
                        return new LabelPositionHolder();
                    }
                    return new LabelPositionHolder(immutableLabelPositionHolder);
                }
{% when "26.1.2" %}
                stack,
                s -> from((DataComponentGetter) stack)
{% endcase %}
        );
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
    public static LabelPositionHolder from(DataComponentGetter components) {
        LabelPositionHolder immutableLabelPositionHolder = components.get(SFMDataComponents.LABEL_POSITION_HOLDER.get());
        if (immutableLabelPositionHolder == null) {
            return new LabelPositionHolder();
        }
        return new LabelPositionHolder(immutableLabelPositionHolder);
    }

{% endcase %}
{% if features.label_readonly_access %}
{% case minecraft_version %}
{% when "26.1.2" %}
    /**
     * Returns an owned label view without mutating the label cache.
     */
    @MCVersionDependentBehaviour // 1.21+ stores labels in item components
    public static LabelPositionHolder fromReadOnly(ItemStack stack) {

        var cached = CACHE.get(stack);
        if (cached != null) {
            return cached.toOwned();
        }
        return from((DataComponentGetter) stack);
    }

{% when "1.21", "1.21.1" %}
    /**
     * Returns an owned label view without mutating the label cache.
     */
    @MCVersionDependentBehaviour // 1.21+ stores labels in item components
    public static LabelPositionHolder fromReadOnly(ItemStack stack) {

        var cached = CACHE.get(stack);
        if (cached != null) {
            return cached.toOwned();
        }
        LabelPositionHolder labels = stack.get(SFMDataComponents.LABEL_POSITION_HOLDER);
        return labels == null ? empty() : labels.toOwned();
    }

{% else %}
    /**
     * Returns an owned label view without creating an item tag or mutating the label cache.
     */
    public static LabelPositionHolder fromReadOnly(ItemStack stack) {

        var cached = CACHE.get(stack);
        if (cached != null) {
            return cached.toOwned();
        }
        var tag = stack.getTag();
        return tag == null ? empty() : deserialize(tag.getCompound("sfm:labels"));
    }

{% endcase %}
{% endif %}
    public static LabelPositionHolder empty() {

        return new LabelPositionHolder();
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static LabelPositionHolder deserialize(CompoundTag tag) {

        var labels = LabelPositionHolder.empty();
        for (var label : tag.getAllKeys()) {
            Tag positionsTag = tag.get(label);
            assert positionsTag != null;
            int positionsTagType = tag.getTagType(label);
            if (positionsTagType == Tag.TAG_LIST) {
                ListTag positionsList = (ListTag) positionsTag;
                int elementType = positionsList.getElementType();
                if (elementType == Tag.TAG_LONG) {
                    // old: storing BlockPos as long
                    labels.addAll(
                            label,
                            positionsList.stream()
                                    .map(LongTag.class::cast)
                                    .mapToLong(LongTag::getAsLong)
                                    .mapToObj(BlockPos::of).collect(Collectors.toList())
                    );
                } else if (elementType == Tag.TAG_COMPOUND) {
                    // old: storing BlockPos as compound
                    // this was used in FTB Academy packs I think
                    labels.addAll(
                            label,
                            positionsList.stream()
                                    .map(CompoundTag.class::cast)
                                    .map(NbtUtils::readBlockPos)
                                    .collect(Collectors.toList())
                    );
                }
            } else if (positionsTagType == Tag.TAG_BYTE_ARRAY) {
                labels.addAll(
                        label,
                        CompressedBlockPosSet.from((ByteArrayTag) positionsTag).into().blockPosIterator()
                );
            }
        }
        return labels;
    }

{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
    public LabelPositionHolder save(ItemStack stack) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        stack.getOrCreateTag().put("sfm:labels", serialize());
        CACHE.put(stack, new LabelPositionHolder(this));
{% when "1.21", "1.21.1", "26.1.2" %}
        LabelPositionHolder copy = new LabelPositionHolder(this);
        stack.set(SFMDataComponents.LABEL_POSITION_HOLDER, copy);
        CACHE.put(stack, copy);
{% endcase %}
        return this;
    }

    public static void clear(ItemStack stack) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
        stack.getOrCreateTag().remove("sfm:labels");
{% when "1.21", "1.21.1", "26.1.2" %}
        stack.remove(SFMDataComponents.LABEL_POSITION_HOLDER);
{% endcase %}
        CACHE.remove(stack);
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public CompoundTag serialize() {

        var tag = new CompoundTag();
        for (var entry : labels().entrySet()) {
            String label = entry.getKey();
            ByteArrayTag positionsTag = CompressedBlockPosSet.from(entry.getValue()).asTag();
            tag.put(label, positionsTag);
        }
        return tag;
    }

{% when "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
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
