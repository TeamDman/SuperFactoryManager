package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.util.BlockPosIterator;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UnusedReturnValue")
public record LabelPositionHolder(Map<String, BlockPosSet> labels) {
    private final static WeakHashMap<ItemStack, LabelPositionHolder> CACHE = new WeakHashMap<>();

    private LabelPositionHolder() {

        this(new HashMap<>());
    }

    private LabelPositionHolder(LabelPositionHolder other) {

        this();
        other.labels().forEach((key, value) -> this.labels().put(key, new BlockPosSet(value)));
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
                stack, s -> {
                    var tag = stack.getOrCreateTag().getCompound("sfm:labels");
                    return deserialize(tag);
                }
        );
    }

    public static LabelPositionHolder empty() {

        return new LabelPositionHolder();
    }

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

    public LabelPositionHolder save(ItemStack stack) {

        stack.getOrCreateTag().put("sfm:labels", serialize());
        CACHE.put(stack, new LabelPositionHolder(this));
        return this;
    }

    public static void clear(ItemStack stack) {

        stack.getOrCreateTag().remove("sfm:labels");
        CACHE.remove(stack);
    }

    public CompoundTag serialize() {

        var tag = new CompoundTag();
        for (var entry : labels().entrySet()) {
            String label = entry.getKey();
            ByteArrayTag positionsTag = CompressedBlockPosSet.from(entry.getValue()).asTag();
            tag.put(label, positionsTag);
        }
        return tag;
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
        rtn.add(LocalizationKeys.DISK_ITEM_TOOLTIP_LABEL_HEADER
                        .getComponent()
                        .withStyle(ChatFormatting.UNDERLINE));
        for (var entry : labels().entrySet()) {
            rtn.add(LocalizationKeys.DISK_ITEM_TOOLTIP_LABEL.getComponent(
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
