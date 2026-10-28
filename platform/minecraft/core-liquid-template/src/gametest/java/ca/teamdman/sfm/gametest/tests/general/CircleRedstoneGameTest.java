package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.items.IItemHandler;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.items.IItemHandler;
{% endcase %}

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"DataFlowIssue"})
@SFMGameTest
public class CircleRedstoneGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

{% if features.gametest_infrastructure_invariants %}
        return "5x3x5";
{% else %}
        return "5x2x5";
{% endif %}
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
{% if features.gametest_infrastructure_invariants %}
                helper.setBlock(new BlockPos(x, 1, z).above(), SFMBlocks.CABLE.get());
{% else %}
                helper.setBlock(new BlockPos(x, 1, z), SFMBlocks.CABLE.get());
{% endif %}
            }
        }

{% if features.gametest_infrastructure_invariants %}
        BlockPos barrelPos = new BlockPos(2, 2, 0).above();
{% else %}
        BlockPos barrelPos = new BlockPos(2, 2, 0);
{% endif %}
        helper.setBlock(barrelPos, SFMBlocks.TEST_BARREL.get());
        IItemHandler barrelHandler = helper.getItemHandler(barrelPos);
        barrelHandler.insertItem(0, new ItemStack(Items.DIRT, 64), false);

{% if features.gametest_infrastructure_invariants %}
        BlockPos manager1Pos = new BlockPos(1, 2, 1).above();
{% else %}
        BlockPos manager1Pos = new BlockPos(1, 2, 1);
{% endif %}
        helper.setBlock(manager1Pos, SFMBlocks.MANAGER.get());
{% if features.gametest_infrastructure_invariants %}
        BlockPos manager2Pos = new BlockPos(3, 2, 3).above();
{% else %}
        BlockPos manager2Pos = new BlockPos(3, 2, 3);
{% endif %}
        helper.setBlock(manager2Pos, SFMBlocks.MANAGER.get());

        // Set up manager1
        ManagerBlockEntity manager1 = helper.getBlockEntity(manager1Pos, ManagerBlockEntity.class);
        manager1.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager1.setProgram("""
                                       EVERY REDSTONE PULSE DO
                                           INPUT FROM barrel SLOTS 0
                                           OUTPUT TO barrel SLOTS 1
                                       END
                                   """.stripTrailing().stripIndent());

        // Set up manager2
        ManagerBlockEntity manager2 = helper.getBlockEntity(manager2Pos, ManagerBlockEntity.class);
        manager2.setItem(0, new ItemStack(SFMItems.DISK.get()));
        manager2.setProgram("""
                                       EVERY REDSTONE PULSE DO
                                           INPUT FROM barrel SLOTS 1
                                           OUTPUT TO barrel SLOTS 0
                                       END
                                   """.stripTrailing().stripIndent());

        // Set labels
        LabelPositionHolder.empty()
                .add("barrel", helper.absolutePos(barrelPos))
                .save(Objects.requireNonNull(manager1.getDisk()));
        LabelPositionHolder.empty()
                .add("barrel", helper.absolutePos(barrelPos))
                .save(Objects.requireNonNull(manager2.getDisk()));

        BlockState repeaterFacingEast = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.EAST)
                .setValue(RepeaterBlock.DELAY, 4);
        BlockState repeaterFacingSouth = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.SOUTH)
                .setValue(RepeaterBlock.DELAY, 4);
        BlockState repeaterFacingWest = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.WEST)
                .setValue(RepeaterBlock.DELAY, 4);
        BlockState repeaterFacingNorth = Blocks.REPEATER.defaultBlockState()
                .setValue(RepeaterBlock.FACING, Direction.NORTH)
                .setValue(RepeaterBlock.DELAY, 4);

{% if features.gametest_infrastructure_invariants %}
        helper.setBlock(new BlockPos(2, 2, 1).above(), repeaterFacingEast);
        helper.setBlock(new BlockPos(3, 2, 2).above(), repeaterFacingSouth);
        helper.setBlock(new BlockPos(2, 2, 3).above(), repeaterFacingWest);
        helper.setBlock(new BlockPos(1, 2, 2).above(), repeaterFacingNorth);
{% else %}
        helper.setBlock(new BlockPos(2, 2, 1), repeaterFacingEast);
        helper.setBlock(new BlockPos(3, 2, 2), repeaterFacingSouth);
        helper.setBlock(new BlockPos(2, 2, 3), repeaterFacingWest);
        helper.setBlock(new BlockPos(1, 2, 2), repeaterFacingNorth);
{% endif %}

        List<BlockPos> redstoneDustPositions = List.of(
{% if features.gametest_infrastructure_invariants %}
                new BlockPos(3, 2, 1).above(),
                new BlockPos(1, 2, 3).above()
{% else %}
                new BlockPos(3, 2, 1),
                new BlockPos(1, 2, 3)
{% endif %}
        );
        for (BlockPos pos : redstoneDustPositions) {
            helper.setBlock(pos, Blocks.REDSTONE_WIRE.defaultBlockState());
        }

{% if features.gametest_infrastructure_invariants %}
        BlockPos buttonPos = new BlockPos(1, 2, 4).above();
{% else %}
        BlockPos buttonPos = new BlockPos(1, 2, 4);
{% endif %}
        BlockState buttonState = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACING, Direction.NORTH)
                .setValue(ButtonBlock.FACE, AttachFace.FLOOR);
        helper.setBlock(buttonPos, buttonState);
        helper.pressButton(buttonPos);

        // Check for alternation
        AtomicInteger countSlot0 = new AtomicInteger(0);
        AtomicInteger countSlot1 = new AtomicInteger(0);
        for (int i = 1; i <= 100; i++) {
            helper.runAfterDelay(i, () -> {
                boolean slot0Full = barrelHandler.getStackInSlot(0).getCount() == 64 && barrelHandler.getStackInSlot(1).isEmpty();
                boolean slot1Full = barrelHandler.getStackInSlot(1).getCount() == 64 && barrelHandler.getStackInSlot(0).isEmpty();
                if (slot0Full) {
                    countSlot0.incrementAndGet();
                }
                if (slot1Full) {
                    countSlot1.incrementAndGet();
                }
                if (slot0Full && slot1Full) {
                    helper.fail("Alternation failed: both slots full");
                }
                if (countSlot0.get() >= 20 && countSlot1.get() >= 20) {
                    helper.succeed();
                }
            });
        }
    }

}
