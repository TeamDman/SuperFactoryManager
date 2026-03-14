package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.count;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

public abstract class SFMTestBuilder {
    protected final SFMGameTestHelper helper;
    protected @Nullable ManagerBlockEntity manager;
    protected Map<String, IItemHandler> chests = new HashMap<>();
    protected Map<String, BlockPos> positions = new HashMap<>();
    protected LabelPositionHolder labelHolder = LabelPositionHolder.empty();
    protected @Nullable String program;
    protected List<Runnable> postConditions = new ArrayList<>();
    protected List<Runnable> preConditions = new ArrayList<>();

    public SFMTestBuilder(SFMGameTestHelper helper) {
        this.helper = helper;
    }

    public SFMTestBuilder setProgram(String program) {
        // need to strip to ensure no indent despite java multiline string usage
        this.program = program.stripTrailing().stripIndent();
        return this;
    }

    public SFMTestBuilder preContents(
            String name,
            List<ItemStack> stacks
    ) {
        preConditions.add(() -> {
            IItemHandler chest = chests.get(name);
            if (chest == null) {
                throw new IllegalArgumentException("Chest not found: " + name);
            }
            for (int i = 0; i < stacks.size(); i++) {
                chest.insertItem(i, stacks.get(i), false);
            }
        });
        return this;
    }

    public SFMTestBuilder postContents(
            String name,
            List<ItemStack> expected
    ) {
        postConditions.add(() -> {
            IItemHandler chest = chests.get(name);
            if (chest == null) {
                throw new IllegalArgumentException("Chest not found: " + name);
            }
            for (int i = 0; i < chest.getSlots(); i++) {
                ItemStack expectedStack = i < expected.size() ? expected.get(i) : ItemStack.EMPTY;
                ItemStack actualStack = chest.getStackInSlot(i);
                assertTrue(
                        expectedStack.isEmpty() && actualStack.isEmpty() || SFMItemUtils.isSameItemSameAmount(
                                expectedStack,
                                actualStack
                        ),
                        String.format("Expected %s in chest %s slot %d, but found %s",
                                      expectedStack, name, i, actualStack
                        )
                );
            }
        });
        return this;
    }

    public void run() {
        setupStructure(BlockPos.ZERO);
        for (Runnable preCondition : preConditions) {
            preCondition.run();
        }
        assert manager != null;
        labelHolder.save(Objects.requireNonNull(manager.getDisk()));
        helper.succeedIfManagerDidThingWithoutLagging(
                manager,
                () -> {
                    // first, assertions as normal
                    {
                        for (Runnable assertion : postConditions) {
                            assertion.run();
                        }
                    }

                    // second, move an item and ensure that the assertions fail somewhere
                    {
                        // shuffle the candidate chests
                        ArrayList<Map.Entry<String, IItemHandler>> chests = new ArrayList<>(this.chests.entrySet());
                        Collections.shuffle(chests);

                        // find a chest with something we can take
                        Map.Entry<String, IItemHandler> source = null;
                        for (int i = 0; i < chests.size(); i++) {
                            IItemHandler chest = chests.get(i).getValue();
                            if (count(chest, (ItemLike) null) > 0) {
                                source = chests.remove(i);
                                break;
                            }
                        }
                        assertTrue(
                                source != null,
                                "Chaos failed to find an item to move?? What is this test doing that there's no items??"
                        );

                        // find a chest to move it to
                        Map.Entry<String, IItemHandler> dest = chests.remove(0);

                        // take out an item
                        ItemStack taken = ItemStack.EMPTY;
                        int takenSlot = -1;
                        int destSlot = -1;
                        for (int trySourceSlot = 0; trySourceSlot < source.getValue().getSlots(); trySourceSlot++) {
                            taken = source.getValue().extractItem(trySourceSlot, 1, false);
                            if (!taken.isEmpty()) {
                                takenSlot = trySourceSlot;
                                break;
                            }
                        }
                        assertTrue(
                                !taken.isEmpty(),
                                "Chaos failed to take an item from "
                                + source.getKey()
                                + " slot "
                                + takenSlot
                        );

                        // insert the item
                        for (int tryDestSlot = 0; tryDestSlot < dest.getValue().getSlots(); tryDestSlot++) {
                            ItemStack remainder = dest.getValue().insertItem(tryDestSlot, taken, false);
                            if (remainder.isEmpty()) {
                                taken = taken.copy(); // because we pass the ownership, we should copy before we call extract which will mutate
                                destSlot = tryDestSlot;
                                break;
                            }
                        }

                        // assert a move occurred
                        assertTrue(
                                destSlot != -1,
                                "Chaos failed to insert the taken item, took "
                                + taken
                                + " from "
                                + source.getKey()
                                + " slot "
                                + takenSlot
                                + " to put in"
                                + dest.getKey()
                        );

                        // assert that the assertions fail
                        boolean tripped = false;
                        for (Runnable assertion : postConditions) {
                            try {
                                assertion.run();
                            } catch (GameTestAssertException e) {
                                tripped = true;
                                break;
                            }
                        }
                        assertTrue(
                                tripped,
                                "Assertions did not fail after chaos, moved "
                                + taken
                                + " from "
                                + source.getKey()
                                + " slot "
                                + takenSlot
                                + " to "
                                + dest.getKey()
                        );

                        // take out the moved item
                        ItemStack undo = dest.getValue().extractItem(destSlot, taken.getCount(), false);
                        assertTrue(
                                 SFMItemUtils.isSameItemSameAmount(undo, taken),
                                "Chaos failed to take the moved item, took "
                                + undo
                                + " from "
                                + dest.getKey()
                                + " slot "
                                + destSlot
                                + " instead of "
                                + taken
                        );

                        // put the item back
                        ItemStack remainder = source.getValue().insertItem(takenSlot, taken, false);
                        assertTrue(
                                remainder.isEmpty(),
                                "Chaos failed to put the taken item back, took "
                                + taken
                                + " from "
                                + source.getKey()
                                + " slot "
                                + takenSlot
                        );

                    }
                }
        );
    }

    protected void addChest(
            String name,
            BlockPos pos
    ) {
        helper.setBlock(pos, SFMBlocks.TEST_BARREL_BLOCK.get());
        IItemHandler chest = helper.getItemHandler(pos);
        chests.put(name, chest);
        positions.put(name, pos);
        labelHolder.add(name, helper.absolutePos(pos));
    }

    @SuppressWarnings("SameParameterValue")
    protected abstract void setupStructure(BlockPos offset);

    protected void setupManager(BlockPos offset) {
        BlockPos managerPos = new BlockPos(1, 2, 0).offset(offset);
        helper.setBlock(managerPos, SFMBlocks.MANAGER_BLOCK.get());
        manager = (ManagerBlockEntity) helper.getBlockEntity(managerPos);
        assertTrue(manager != null, "Manager not found");
        manager.setItem(0, new ItemStack(SFMItems.DISK_ITEM.get()));
        assertTrue(program != null, "Program not set");
        manager.setProgram(program.stripTrailing().stripIndent());
    }
}
