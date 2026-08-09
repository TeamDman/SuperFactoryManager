package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.util.BlockPosSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;

public record FacadePlanAnalysisResult(
        Map<FacadeData, Integer> facadeDataToCount,

        Map<Block, Integer> unfacadedCount,

        BlockPosSet positions
) {
    public static FacadePlanAnalysisResult analyze(
            Level level,
            BlockPosSet positions
    ) {

        Object2IntOpenHashMap<FacadeData> facadeDataToCount = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<Block> unfacadedCount = new Object2IntOpenHashMap<>();
        for (BlockPos position : positions.blockPosIterator()) {
            if (level.getBlockEntity(position) instanceof IFacadeBlockEntity blockEntity) {
                FacadeData facadeData = blockEntity.getFacadeData();
                facadeDataToCount.put(facadeData, facadeDataToCount.getInt(facadeData) + 1);
            } else {
                Block block = level.getBlockState(position).getBlock();
                unfacadedCount.put(block, unfacadedCount.getInt(block) + 1);
            }
        }
        return new FacadePlanAnalysisResult(facadeDataToCount, unfacadedCount, positions);
    }

    public boolean coversBigArea() {

        BoundingBox boundingBox = positions.boundingBox();
        if (boundingBox == null) {
            return false;
        } else {
            return boundingBox.getXSpan() > 8 || boundingBox.getYSpan() > 8 || boundingBox.getZSpan() > 10;
        }
    }

    public boolean affectingMany() {

        return facadeDataToCount.values().stream().mapToInt(i -> i).sum() > 10;
    }

    public boolean affectingManyUnique() {

        return facadeDataToCount.size() > 1;
    }

    public boolean shouldWarn() {

        return affectingMany() || affectingManyUnique() || coversBigArea();
    }

    public int countAffected() {

        return facadeDataToCount.values().stream().mapToInt(i -> i).sum() + unfacadedCount
                .values()
                .stream()
                .mapToInt(i -> i)
                .sum();
    }

}
