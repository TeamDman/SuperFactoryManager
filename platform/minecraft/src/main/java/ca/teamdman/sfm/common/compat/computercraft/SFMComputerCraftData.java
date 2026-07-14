package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts SFM domain state into deterministic tables supported by CC:Tweaked's Lua conversion.
 */
final class SFMComputerCraftData {
    static final int MAX_LABELS = 16;
    static final int MAX_POSITIONS_PER_LABEL = 64;
    static final int MAX_TEXT_CHARACTERS = 8_192;

    private SFMComputerCraftData() {

    }

    static Map<String, Object> managerDetails(ManagerBlockEntity manager) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("position", positionDetails(manager.getBlockPos()));
        result.put("state", manager.getStateReadOnly().name().toLowerCase(Locale.ROOT));

        ItemStack disk = manager.getDisk();
        if (disk != null) {
            result.put("disk", diskDetails(disk));
        }
        return result;
    }

    static Map<String, Object> diskDetails(ItemStack disk) {

        Map<String, Object> result = new LinkedHashMap<>();
        putBoundedText(result, "name", DiskItem.getProgramNameReadOnly(disk));
        putBoundedText(result, "program", DiskItem.getProgramStringReadOnly(disk));
        putLabelDetails(result, LabelPositionHolder.fromReadOnly(disk));
        return result;
    }

    static void putLabelDetails(
            Map<String, Object> result,
            LabelPositionHolder labels
    ) {

        LabelDetails labelDetails = labelDetails(labels);
        result.put("labels", labelDetails.values());
        result.put("labelsTruncated", labelDetails.truncated());
    }

    static void putBoundedText(
            Map<String, Object> result,
            String key,
            String value
    ) {

        StringDetail detail = boundedText(value);
        result.put(key, detail.value());
        result.put(key + "Truncated", detail.truncated());
    }

    private static LabelDetails labelDetails(LabelPositionHolder labels) {

        Map<String, Object> result = new TreeMap<>();
        boolean truncated = false;
        int serializedLabelCount = 0;
        for (Map.Entry<String, ca.teamdman.sfm.common.util.BlockPosSet> entry : new TreeMap<>(labels.labels()).entrySet()) {
            String label = entry.getKey();
            if (label.length() > MAX_TEXT_CHARACTERS || serializedLabelCount >= MAX_LABELS) {
                truncated = true;
                continue;
            }
            var positions = entry.getValue();
            List<Map<String, Integer>> serializedPositions = positions
                    .blockPosIterator()
                    .stream()
                    .map(BlockPos::immutable)
                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                    .limit(MAX_POSITIONS_PER_LABEL)
                    .map(SFMComputerCraftData::positionDetails)
                    .toList();
            result.put(label, serializedPositions);
            serializedLabelCount++;
            if (positions.size() > MAX_POSITIONS_PER_LABEL) {
                truncated = true;
            }
        }
        return new LabelDetails(result, truncated);
    }

    private static StringDetail boundedText(String value) {

        return value.length() <= MAX_TEXT_CHARACTERS
               ? new StringDetail(value, false)
               : new StringDetail(value.substring(0, MAX_TEXT_CHARACTERS), true);
    }

    static Map<String, Object> itemSummary(ItemStack stack) {

        if (stack.isEmpty()) return Map.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        result.put("count", stack.getCount());
        return result;
    }

    static Map<String, Integer> positionDetails(BlockPos pos) {

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("x", pos.getX());
        result.put("y", pos.getY());
        result.put("z", pos.getZ());
        return result;
    }

    private record LabelDetails(Map<String, Object> values, boolean truncated) {

    }

    private record StringDetail(String value, boolean truncated) {

    }
}
