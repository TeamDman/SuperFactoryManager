package ca.teamdman.sfm.gametest.declarative;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.ast.SfmlAstBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;



public class SFMDeclarativeTestBuilder {
    private final SFMGameTestHelper helper;
    private final SFMTestSpec spec;
    private int conditionIndex = 0;

    public SFMDeclarativeTestBuilder(
            SFMGameTestHelper helper,
            SFMTestSpec spec
    ) {
        this.helper = helper;
        this.spec = spec;
    }

    public void run() {
        BlockPos managerPos = calculateManagerPos();
        placeBlocks(managerPos);
        ManagerBlockEntity manager = setupManager(managerPos);
        labelBlocks(managerPos, manager);
        runPreConditions(manager);
        helper.succeedIfManagerDidThingWithoutLagging(
                manager,
                () -> runPostConditions(manager)
        );
        // todo: chaos
    }

    private @NotNull BlockPos calculateManagerPos() {
        return BoundingBox.encapsulatingPositions(
                        spec.blocks()
                                .stream()
                                .map(SFMTestBlockEntitySpec::posRelativeToManager)
                                .toList()
                )
                .map(bounds -> {
                    int x = bounds.getXSpan();
                    int y = bounds.getYSpan();
                    int z = bounds.getZSpan();
                    return new BlockPos(x / 2, y / 2 + 2, z / 2);
                })
                .orElse(new BlockPos(0, 1, 0));
    }

    private void placeBlocks(BlockPos managerPos) {
        for (SFMTestBlockEntitySpec<?> blockDef : spec.blocks()) {
            placeBlock(blockDef, managerPos);
        }
    }

    private <T extends BlockEntity> void placeBlock(
            SFMTestBlockEntitySpec<T> def,
            BlockPos managerPos
    ) {
        BlockPos blockPos = managerPos.offset(def.posRelativeToManager());
        helper.setBlock(blockPos, def.block());
        if (def.blockEntityConfigurer() != null) {
            BlockEntity be = helper.getBlockEntity(blockPos);
            //noinspection unchecked
            Objects.requireNonNull(def.blockEntityConfigurer()).accept((T) be);
        }
    }

    private ManagerBlockEntity setupManager(BlockPos managerPos) {
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        if (helper.getBlockEntity(managerPos) instanceof ManagerBlockEntity manager) {
            manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
            manager.setProgram(spec.program());
//            manager.setLogLevel(Level.DEBUG);
            return manager;
        } else {
            throw new GameTestAssertException("Manager block entity not found!");
        }
    }

    private void runPreConditions(ManagerBlockEntity manager) {
        runConditions(manager, "Pre-condition", spec.preConditions());
    }

    private void runPostConditions(ManagerBlockEntity manager) {
        runConditions(manager, "Post-condition", spec.postConditions());
    }

    private void runConditions(
            ManagerBlockEntity manager,
            String conditionKind,
            List<String> conditions
    ) {
        if (conditions.isEmpty()) return;
        List<BoolExpr> expressions = conditions.stream().map(BoolExpr::from).toList();
        ProgramContext programContext = ProgramContext.of(
                new SFMLProgram(new SfmlAstBuilder(), "temp lol", List.of(), List.of(), Set.of(), Set.of()),
                manager,
                ExecuteProgramBehaviour::new
        );
        for (BoolExpr expr : expressions) {
            boolean passed = expr.test(programContext);
            if (!passed) {
                helper.fail(conditionKind + " " + conditionIndex + " failed: " + expr);
            }
            conditionIndex++;
        }
    }

    private void labelBlocks(
            BlockPos managerPos,
            ManagerBlockEntity manager
    ) {
        LabelPositionHolder labelHolder = LabelPositionHolder.empty();
        for (SFMTestBlockEntitySpec<?> def : spec.blocks()) {
            // Actually add the label to the holder
            BlockPos absolutePos = helper.absolutePos(managerPos.offset(def.posRelativeToManager()));
            labelHolder.add(def.label(), absolutePos);
        }
        labelHolder.save(Objects.requireNonNull(manager.getDisk()));

    }
}
