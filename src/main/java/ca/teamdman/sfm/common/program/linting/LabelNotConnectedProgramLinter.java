package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;

public class LabelNotConnectedProgramLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter {
    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labels,
            @Nullable ManagerBlockEntity manager,
            ProblemTracker tracker
    ) {

        var level = manager != null ? manager.getLevel() : null;
        if (level == null) return;
        int before = tracker.size();

        CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .ifPresent(network -> {
                    outer:
                    for (Map.Entry<String, BlockPosSet> entry : labels.labels().entrySet()) {
                        String label = entry.getKey();
                        BlockPosSet positions = entry.getValue();
                        for (BlockPos pos : positions.blockPosIterator()) {
                            if (network.isAdjacentToCable(pos)) {
                                if (!SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, pos)) {
                                    // a label is properly connected but doesn't support any capabilities
                                    // TODO: make this only check for the capabilities being used
                                    if (tracker.add(PROGRAM_WARNING_CONNECTED_BUT_NOT_VIABLE_LABEL.get(
                                            label,
                                            String.format("[%d,%d,%d]", pos.getX(), pos.getY(), pos.getZ())
                                    )).isSaturated()) {
                                        break outer;
                                    }
                                }
                            } else {
                                // a label is being used without being connected by cables
                                if (tracker.add(PROGRAM_WARNING_DISCONNECTED_LABEL.get(
                                        label,
                                        String.format("[%d,%d,%d]", pos.getX(), pos.getY(), pos.getZ())
                                )).isSaturated()) {
                                    break outer;
                                }
                            }
                        }
                    }
                });

        if (tracker.size() > before) {
            tracker.add(PROGRAM_REMINDER_PUSH_LABELS.get());
        }
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            World level,
            ItemStack disk
    ) {
        // remove labels not connected via cables
        CableNetwork cableNetwork = CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .orElse(null);
        if (cableNetwork == null) return;
        labels.removeIf((label, pos) -> !cableNetwork.isAdjacentToCable(pos));

        // remove labels with no viable capability provider
        // TODO: make this only attend to the exact list of resource types being queried for instead of all resource types
        labels.removeIf((label, pos) -> !SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, pos));

    }

}
