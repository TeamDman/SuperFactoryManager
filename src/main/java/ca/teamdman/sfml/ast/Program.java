package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.util.SFMLabelNBTHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record Program(
        String name,
        List<Trigger> triggers,
        Set<String> referencedLabels
) implements ASTNode {
    public static final int MAX_PROGRAM_LENGTH = 8096;

    public void addWarnings(ItemStack disk) {
        var warnings = new ArrayList<String>();
        for (String label : referencedLabels) {
            var isUsed = SFMLabelNBTHelper
                    .getLabelPositions(disk, label)
                    .findAny()
                    .isPresent();
            if (!isUsed) {
                warnings.add("There are no blocks labelled with \"" + label + "\".");
            }
        }
        DiskItem.setWarnings(disk, warnings);
    }

    public void tick(ProgramContext context) {
        for (Trigger t : triggers) {
            if (t.shouldTick(context)) {
//                var start = System.nanoTime();
                t.tick(context.fork());
//                var end  = System.nanoTime();
//                var diff = end - start;
//                SFM.LOGGER.debug("Took {}ms ({}us)", diff / 1000000, diff);
            }
        }
    }

    public Set<String> getReferencedLabels() {
        return referencedLabels;
    }
}
