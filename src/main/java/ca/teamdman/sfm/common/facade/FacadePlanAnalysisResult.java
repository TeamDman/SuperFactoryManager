package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;

@Desugar
public record FacadePlanAnalysisResult(
        Map<FacadeData, Integer> facadeDataToCount,
        Map<Block, Integer> unfacadedCount,
        Set<BlockPos> positions
) {
    public static FacadePlanAnalysisResult analyze(World level, Set<BlockPos> positions) {
        Object2IntOpenHashMap<FacadeData> facadeDataToCount = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<Block> unfacadedCount = new Object2IntOpenHashMap<>();
        for (BlockPos position : positions) {
            if (level.getTileEntity(position) instanceof IFacadeBlockEntity blockEntity) {
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
        int lowX, lowY, lowZ;
        int highX, highY, highZ;

        var iterator = positions.iterator();

        if (!iterator.hasNext()) {
            return false;
        }

        var pos = iterator.next();
        lowX = highX = pos.getX();
        lowY = highY = pos.getY();
        lowZ = highZ = pos.getZ();
        while (iterator.hasNext()) {
            pos = iterator.next();
            lowX = Math.min(lowX, pos.getX());
            highX = Math.max(highX, pos.getX());
            lowY = Math.min(lowY, pos.getY());
            highY = Math.max(highY, pos.getY());
            lowZ = Math.min(lowZ, pos.getZ());
            highZ = Math.max(highZ, pos.getZ());
            if (highX - lowX > 8 || highY - lowY > 8 || highZ - lowZ > 10) {
                return true;
            }
        }

        return false;
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
        return facadeDataToCount.values().stream().mapToInt(i -> i).sum() + unfacadedCount.values().stream().mapToInt(i -> i).sum();
    }
}
