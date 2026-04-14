package ca.teamdman.sfm.common.command.draw;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.List;

public record DrawManagerProgramCard(
        BlockPos managerPos,
        ManagerBlockEntity.State state,
        String diskName,
        String programString,
        List<String> detailLines,
        List<String> warningLines,
        List<String> errorLines,
        List<String> astLines
) {
}