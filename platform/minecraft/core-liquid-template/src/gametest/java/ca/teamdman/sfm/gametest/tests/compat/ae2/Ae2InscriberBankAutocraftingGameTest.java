package ca.teamdman.sfm.gametest.tests.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.MachineSource;
import appeng.parts.reporting.CraftingTerminalPart;
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Real AE2 processing jobs feed a separate chest and SFM manager for each permanent-press bank.
 * All printing managers use the same program; only the press-free assembly bank routes by item.
 * Five eight-by-eight machine walls share one AE2 grid and one creative energy cell. Each wall
 * receives power through a glass-cable feed beneath its bottom-right machine. Adjacent Inscribers
 * carry that power through the wall without requiring channels. No printed circuits or processors
 * are seeded, and presses are not processing-pattern inputs. All seven jobs are submitted
 * together: four iron-block press-copy recipes and three complete processor recipes share
 * the printing banks, input chests, and unchanged generic printing program.
 */
@SFMGameTest
public class Ae2InscriberBankAutocraftingGameTest extends SFMGameTestDefinition {
    private static final int BANK_COUNT = 5;
    private static final int WALL_SIDE = 8;
    private static final int MACHINES_PER_BANK = WALL_SIDE * WALL_SIDE;
    private static final int REQUEST_COUNT = MACHINES_PER_BANK * 4;
    private static final int PRESS_COPY_COUNT = MACHINES_PER_BANK * 2;
    private static final int PATTERN_BATCH = MACHINES_PER_BANK;
    private static final int PROCESSOR_JOB_COUNT = 3;
    private static final int PRESS_COPY_JOB_COUNT = 4;
    private static final int CPU_COUNT = PROCESSOR_JOB_COUNT + PRESS_COPY_JOB_COUNT;
    private static final int FIRST_BANK_Z = 3;
    private static final int BANK_STRIDE = 3;
    private static final int SFM_TRUNK_X = 9;
    private static final int AE_BACKBONE_X = 12;
    private static final int LAST_MACHINE_Z = FIRST_BANK_Z + (BANK_COUNT - 1) * BANK_STRIDE + 1;
    private static final BlockPos POWER = new BlockPos(AE_BACKBONE_X, 2, 1);
    private static final BlockPos CONTROLLER = new BlockPos(AE_BACKBONE_X, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(AE_BACKBONE_X + 1, 2, 1);
    private static final BlockPos TERMINAL = new BlockPos(AE_BACKBONE_X - 1, 3, 1);

    // The bank's permanent press and its AE2 pattern determine what gets printed. No per-item
    // filter or per-press program is required. Horizontal extraction exposes only the output;
    // unsided extraction would also take the permanent press out of slot 0.
    private static final String PRINT_PROGRAM = """
            NAME "permanent press bank"
            EVERY 20 TICKS DO
                INPUT FROM ingredients
                OUTPUT 1 TO EACH machine SLOTS 2
                FORGET
                INPUT FROM machine WEST SIDE
                OUTPUT TO result_interface TOP SIDE
            END
            """.stripIndent();

    private static final String ASSEMBLY_PROGRAM = """
            NAME "processor assembly bank"
            EVERY 20 TICKS DO
                INPUT printed_logic_processor, printed_engineering_processor, printed_calculation_processor FROM ingredients
                OUTPUT 1 TO EACH machine SLOTS 0
                FORGET
                INPUT printed_silicon FROM ingredients
                OUTPUT 1 TO EACH machine SLOTS 1
                FORGET
                INPUT redstone FROM ingredients
                OUTPUT 1 TO EACH machine SLOTS 2
                FORGET
                INPUT FROM machine WEST SIDE
                OUTPUT TO result_interface TOP SIDE
            END
            """.stripIndent();

    @Override
    public String template() {
        return "15x11x27";
    }

    @Override
    public int maxTicks() {
        return 20 * 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        List<BankSpec> specs = List.of(
                new BankSpec("silicon", AEItems.SILICON_PRESS.asItem(), AEItems.SILICON.asItem(),
                        AEItems.SILICON_PRINT.asItem(), null),
                new BankSpec("logic", AEItems.LOGIC_PROCESSOR_PRESS.asItem(), Items.GOLD_INGOT,
                        AEItems.LOGIC_PROCESSOR_PRINT.asItem(), AEItems.LOGIC_PROCESSOR.asItem()),
                new BankSpec("engineering", AEItems.ENGINEERING_PROCESSOR_PRESS.asItem(), Items.DIAMOND,
                        AEItems.ENGINEERING_PROCESSOR_PRINT.asItem(), AEItems.ENGINEERING_PROCESSOR.asItem()),
                new BankSpec("calculation", AEItems.CALCULATION_PROCESSOR_PRESS.asItem(), AEItems.CERTUS_QUARTZ_CRYSTAL.asItem(),
                        AEItems.CALCULATION_PROCESSOR_PRINT.asItem(), AEItems.CALCULATION_PROCESSOR.asItem()),
                new BankSpec("assembly", null, null, null, null)
        );
        helper.setBlock(POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CONTROLLER, AEBlocks.CONTROLLER.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        for (int z = 1; z <= LAST_MACHINE_Z + 1; z++) {
            placeCable(helper, new BlockPos(AE_BACKBONE_X, 3, z), true);
        }
        placeCable(helper, TERMINAL, false);
        CraftingTerminalPart terminal = PartHelper.setPart(helper.getLevel(), helper.absolutePos(TERMINAL),
                Direction.NORTH, null, AEParts.CRAFTING_TERMINAL.asItem());
        helper.assertTrue(terminal != null, "Could not attach the inspectable ME Crafting Terminal");
        for (int z = 1; z < LAST_MACHINE_Z; z++) {
            helper.setBlock(new BlockPos(SFM_TRUNK_X, 2, z), SFMBlocks.CABLE.get());
        }
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE, DriveBlockEntity.class);
        helper.assertTrue(drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack()).isEmpty(),
                "AE2 drive rejected storage cell");
        for (int cpu = 0; cpu < CPU_COUNT; cpu++) {
            BlockPos cpuPos = new BlockPos(AE_BACKBONE_X + 1, 3, 1 + cpu * 2);
            helper.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_64K.block());
            for (int y = 1; y <= 6; y++) {
                helper.setBlock(cpuPos.above(y), AEBlocks.CRAFTING_ACCELERATOR.block());
            }
        }

        List<Bank> banks = new ArrayList<>();
        for (int index = 0; index < specs.size(); index++) {
            BankSpec spec = specs.get(index);
            int z = FIRST_BANK_Z + index * BANK_STRIDE;
            BlockPos chest = new BlockPos(SFM_TRUNK_X + 1, 2, z + 1);
            BlockPos providerPos = new BlockPos(SFM_TRUNK_X + 2, 2, z + 1);
            BlockPos resultInterface = providerPos.above();
            BlockPos managerPos = new BlockPos(SFM_TRUNK_X + 1, 2, z);
            helper.setBlock(chest, Blocks.CHEST);
            helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(resultInterface, AEBlocks.INTERFACE.block());
            // One power spur into each wall. Keeping it beneath the chest leaves the chest
            // lid and the inspection aisle open; the machine wall itself carries AE power.
            placeCable(helper, new BlockPos(AE_BACKBONE_X, 2, z + 1), false);
            for (int x = WALL_SIDE; x <= AE_BACKBONE_X; x++) {
                placeCable(helper, new BlockPos(x, 1, z + 1), false);
            }
            PatternProviderBlockEntity provider = helper.getBlockEntity(providerPos, PatternProviderBlockEntity.class);
            provider.setPushDirection(Direction.WEST);
            if (spec.press() != null) {
                addPattern(helper, provider, new Item[]{spec.input()}, spec.printed());
                addPattern(helper, provider, new Item[]{Items.IRON_BLOCK}, spec.press());
            } else {
                for (BankSpec processor : specs) {
                    if (processor.processor() != null) {
                        addPattern(helper, provider,
                                new Item[]{processor.printed(), AEItems.SILICON_PRINT.asItem(), Items.REDSTONE},
                                processor.processor());
                    }
                }
            }

            for (int x = 1; x < AE_BACKBONE_X; x++) {
                if (x != managerPos.getX()) {
                    helper.setBlock(new BlockPos(x, 2, z), SFMBlocks.CABLE.get());
                }
            }
            helper.setBlock(new BlockPos(SFM_TRUNK_X + 2, 3, z), SFMBlocks.CABLE.get());
            List<BlockPos> machines = new ArrayList<>();
            for (int y = 2; y < 2 + WALL_SIDE; y++) {
                for (int x = 1; x <= WALL_SIDE; x++) {
                    BlockPos machine = new BlockPos(x, y, z + 1);
                    helper.setBlock(machine.relative(Direction.NORTH), SFMBlocks.CABLE.get());
                    helper.setBlock(machine, AEBlocks.INSCRIBER.block());
                    InscriberBlockEntity inscriber = helper.getBlockEntity(machine, InscriberBlockEntity.class);
                    inscriber.setOrientation(Direction.NORTH, Direction.UP);
                    if (spec.press() != null) {
                        helper.assertTrue(helper.getItemHandler(machine)
                                        .insertItem(0, new ItemStack(spec.press()), false).isEmpty(),
                                spec.name() + " could not install permanent press at " + machine);
                    }
                    var upgrades = inscriber.getUpgrades();
                    for (int slot = 0; slot < upgrades.size(); slot++) {
                        upgrades.insertItem(slot, AEItems.SPEED_CARD.stack(), false);
                    }
                    machines.add(machine);
                }
            }
            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            LabelPositionHolder labels = LabelPositionHolder.empty()
                    .add("ingredients", helper.absolutePos(chest))
                    .add("result_interface", helper.absolutePos(resultInterface));
            for (BlockPos machine : machines) {
                labels.add("machine", helper.absolutePos(machine));
            }
            labels.save(manager.getDisk());
            manager.setProgram(spec.press() == null ? ASSEMBLY_PROGRAM : PRINT_PROGRAM);
            helper.setBlock(managerPos.above(), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
            helper.setSignText(managerPos.above(), Component.literal(spec.name() + " bank"));
            banks.add(new Bank(spec, chest, resultInterface, provider, manager, List.copyOf(machines)));
        }
        helper.assertTrue(banks.size() == BANK_COUNT && banks.stream().allMatch(bank -> bank.machines().size() == MACHINES_PER_BANK),
                "Expected five complete eight-by-eight Inscriber walls");
        assertSingleCreativeCell(helper);
        State state = new State(List.copyOf(banks), terminal);
        observeRouting(helper, state);
        helper.runAfterDelay(1, () -> awaitReady(helper, state, 200));
    }

    private static void placeCable(SFMGameTestHelper helper, BlockPos pos, boolean dense) {
        var cable = dense
                ? PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null,
                        AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT))
                : PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null,
                        AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        helper.assertTrue(cable != null,
                "Could not place AE2 cable at " + pos);
    }

    private static void addPattern(SFMGameTestHelper helper, PatternProviderBlockEntity provider,
                                   Item[] inputs, Item output) {
        GenericStack[] encodedInputs = new GenericStack[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            encodedInputs[i] = new GenericStack(AEItemKey.of(inputs[i]), PATTERN_BATCH);
        }
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(encodedInputs,
                new GenericStack[]{new GenericStack(AEItemKey.of(output), PATTERN_BATCH)});
        helper.assertTrue(provider.getLogic().getPatternInv().addItems(encoded).isEmpty(),
                "Pattern provider rejected batch pattern for " + output);
    }

    private static void awaitReady(SFMGameTestHelper helper, State state, int remaining) {
        IGrid grid = state.banks.get(0).provider().getMainNode().getGrid();
        boolean ready = grid != null && grid.getCraftingService().getCpus().size() == CPU_COUNT
                        && grid.getEnergyService().isNetworkPowered()
                        && grid.getMachines(InscriberBlockEntity.class).size() == BANK_COUNT * MACHINES_PER_BANK
                        && grid.getMachines(CreativeEnergyCellBlockEntity.class).size() == 1
                        && state.terminal.getMainNode().isActive() && state.terminal.getMainNode().getGrid() == grid;
        for (Bank bank : state.banks) {
            ready &= bank.provider().getMainNode().isActive()
                     && helper.getBlockEntity(bank.resultInterface(), InterfaceBlockEntity.class).getMainNode().isActive();
            if (grid != null && bank.spec().press() != null) {
                ready &= grid.getCraftingService().isCraftable(AEItemKey.of(bank.spec().printed()))
                         && grid.getCraftingService().isCraftable(AEItemKey.of(bank.spec().press()));
            }
            if (grid != null && bank.spec().processor() != null) {
                ready &= grid.getCraftingService().isCraftable(AEItemKey.of(bank.spec().processor()));
            }
            for (BlockPos machine : bank.machines()) {
                var node = helper.getBlockEntity(machine, InscriberBlockEntity.class).getMainNode();
                ready &= node.getGrid() == grid && node.isActive() && node.isPowered();
            }
        }
        if (!ready) {
            helper.assertTrue(remaining > 0, "AE2 wall network did not become ready: connectedInscribers="
                    + (grid == null ? 0 : grid.getMachines(InscriberBlockEntity.class).size())
                    + "/" + BANK_COUNT * MACHINES_PER_BANK + ", terminal=" + state.terminal.getMainNode().isActive()
                    + ", powered=" + (grid != null && grid.getEnergyService().isNetworkPowered()) + " " + describe(state));
            helper.runAfterDelay(1, () -> awaitReady(helper, state, remaining - 1));
            return;
        }
        state.grid = grid;
        state.source = new MachineSource(state.banks.get(0).provider());
        for (Bank bank : state.banks) {
            helper.assertManagerRunning(bank.manager());
            if (bank.spec().press() != null) {
                seed(helper, state, bank.spec().input(), bank.spec().processor() == null ? REQUEST_COUNT * 3 : REQUEST_COUNT);
                helper.assertTrue(stored(state, bank.spec().printed()) == 0, "Printed circuits were pre-seeded");
                helper.assertTrue(stored(state, bank.spec().press()) == 0, "Copied presses were pre-seeded");
            }
            if (bank.spec().processor() != null) {
                helper.assertTrue(stored(state, bank.spec().processor()) == 0, "Processors were pre-seeded");
            }
        }
        seed(helper, state, Items.REDSTONE, REQUEST_COUNT * 3);
        seed(helper, state, Items.IRON_BLOCK, PRESS_COPY_COUNT * PRESS_COPY_JOB_COUNT);
        // Calculate every request before any processing starts. Enough independent CPUs
        // accept all seven jobs together, with no recipe-phase boundary or extra inputs.
        for (Bank bank : state.banks) {
            if (bank.spec().press() != null) {
                state.jobs.add(new Job(bank.spec(), true, state.grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source, AEItemKey.of(bank.spec().press()),
                        PRESS_COPY_COUNT, CalculationStrategy.REPORT_MISSING_ITEMS)));
            }
            if (bank.spec().processor() != null) {
                state.jobs.add(new Job(bank.spec(), false, state.grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), () -> state.source, AEItemKey.of(bank.spec().processor()),
                        REQUEST_COUNT, CalculationStrategy.REPORT_MISSING_ITEMS)));
            }
        }
        helper.runAfterDelay(1, () -> submitPlans(helper, state, 200));
    }

    private static void seed(SFMGameTestHelper helper, State state, Item item, long amount) {
        helper.assertTrue(state.grid.getStorageService().getInventory().insert(AEItemKey.of(item), amount,
                        Actionable.MODULATE, state.source) == amount,
                "AE2 storage rejected raw ingredients: " + item);
    }

    private static void submitPlans(SFMGameTestHelper helper, State state, int remaining) {
        if (state.jobs.stream().anyMatch(job -> !job.plan().isDone())) {
            helper.assertTrue(remaining > 0, "AE2 plan calculations timed out");
            helper.runAfterDelay(1, () -> submitPlans(helper, state, remaining - 1));
            return;
        }
        List<ICraftingPlan> plans = new ArrayList<>();
        try {
            for (Job job : state.jobs) {
                ICraftingPlan plan = job.plan().get();
                validatePlan(helper, job, plan);
                plans.add(plan);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            helper.fail("Interrupted calculating AE2 plan: " + e);
            return;
        } catch (ExecutionException e) {
            helper.fail("AE2 plan failed: " + e.getCause());
            return;
        }
        helper.assertTrue(plans.size() == CPU_COUNT, "Expected all seven AE2 plans before submitting any job");
        long tick = helper.getLevel().getGameTime();
        for (ICraftingPlan plan : plans) {
            var result = state.grid.getCraftingService().submitJob(plan, null, null, true, state.source);
            helper.assertTrue(result.successful(), "AE2 rejected concurrent job: "
                    + result.errorCode() + " " + result.errorDetail());
            state.submitted++;
            helper.assertTrue(helper.getLevel().getGameTime() == tick, "AE2 jobs were split across different ticks");
        }
        helper.assertTrue(state.grid.getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count() == CPU_COUNT,
                "All seven AE2 jobs must be active together");
        helper.assertTrue(state.productionCycles.isEmpty() && state.copyProductionCycles.isEmpty(),
                "Processing started before all seven jobs were submitted");
        state.submissionTick = tick;
        SFM.LOGGER.info("SFM_AE2_INSCRIBER_JOBS_SUBMITTED jobs={} copyJobs=4 processorJobs=3 activeCpus=7 tick={}",
                state.submitted, tick);
        helper.runAfterDelay(20, () -> awaitCompletion(helper, state, 140));
    }

    private static void validatePlan(SFMGameTestHelper helper, Job job, ICraftingPlan plan) {
        helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(),
                "AE2 plan has missing ingredients for " + job.spec().name() + ": " + plan.missingItems());
        // Copies execute only the iron-block recipe; processor requests execute all three
        // stages. No press is a pattern ingredient and no crafted result is seeded.
        Set<AEItemKey> expectedStages = job.copy()
                ? new HashSet<>(Set.of(AEItemKey.of(job.spec().press())))
                : new HashSet<>(Set.of(AEItemKey.of(job.spec().printed()),
                        AEItemKey.of(AEItems.SILICON_PRINT.asItem()), AEItemKey.of(job.spec().processor())));
        helper.assertTrue(plan.patternTimes().size() == expectedStages.size(),
                "Wrong processing stages in " + job.spec().name() + " plan: " + plan.patternTimes());
        for (var stage : plan.patternTimes().entrySet()) {
            GenericStack output = stage.getKey().getPrimaryOutput();
            helper.assertTrue(expectedStages.remove(output.what()) && output.amount() == PATTERN_BATCH
                             && stage.getValue() == (job.copy() ? PRESS_COPY_COUNT : REQUEST_COUNT) / PATTERN_BATCH,
                    "Wrong processing stage or batch count for " + job.spec().name() + ": " + stage);
            if (job.copy()) {
                var inputs = stage.getKey().getInputs();
                helper.assertTrue(inputs.length == 1 && inputs[0].getMultiplier() == PATTERN_BATCH
                                 && inputs[0].getPossibleInputs().length == 1
                                 && inputs[0].getPossibleInputs()[0].what().equals(AEItemKey.of(Items.IRON_BLOCK))
                                 && inputs[0].getRemainingKey(inputs[0].getPossibleInputs()[0].what()) == null,
                        "Press-copy pattern must consume only a 64-item iron-block batch");
            }
        }
        helper.assertTrue(expectedStages.isEmpty(), "AE2 omitted processing stages: " + expectedStages);
    }

    private static void observeRouting(SFMGameTestHelper helper, State state) {
        if (state.finished) {
            return;
        }
        for (Bank bank : state.banks) {
            IItemHandler chest = helper.getItemHandler(bank.chest());
            int chestCount = 0;
            boolean ironPresent = false;
            boolean printingMaterialPresent = false;
            for (int slot = 0; slot < chest.getSlots(); slot++) {
                ItemStack stack = chest.getStackInSlot(slot);
                helper.assertTrue(stack.isEmpty() || validInput(state, bank.spec(), stack.getItem()),
                        "Wrong ingredient routed to " + bank.spec().name() + " chest: " + stack);
                chestCount += stack.getCount();
                ironPresent |= stack.is(Items.IRON_BLOCK);
                printingMaterialPresent |= bank.spec().input() != null && stack.is(bank.spec().input());
            }
            if (ironPresent && printingMaterialPresent) {
                state.mixedChestBanks.add(bank.spec().name());
            }
            state.largestChestBatch.merge(bank.spec().name(), chestCount, Math::max);
            for (BlockPos machine : bank.machines()) {
                if (state.grid != null) {
                    assertMachinePowered(helper, state, machine);
                }
                IItemHandler inventory = helper.getItemHandler(machine);
                ItemStack top = inventory.getStackInSlot(0);
                ItemStack bottom = inventory.getStackInSlot(1);
                ItemStack middle = inventory.getStackInSlot(2);
                ItemStack output = inventory.getStackInSlot(3);
                if (bank.spec().press() != null) {
                    helper.assertTrue(top.is(bank.spec().press()) && top.getCount() == 1,
                            bank.spec().name() + " original press was removed or changed at " + machine);
                    helper.assertTrue(bottom.isEmpty(), "Unexpected bottom ingredient in " + bank.spec().name());
                    helper.assertTrue(middle.isEmpty() || (validInput(state, bank.spec(), middle.getItem()) && middle.getCount() == 1),
                            "Wrong printing material in " + bank.spec().name() + " at " + machine + ": " + middle);
                    helper.assertTrue(output.isEmpty() || ((output.is(bank.spec().printed()) || output.is(bank.spec().press()))
                                                          && output.getCount() == 1),
                            "Wrong printed output in " + bank.spec().name() + ": " + output);
                } else {
                    helper.assertTrue(top.isEmpty() || state.banks.stream().anyMatch(
                                    b -> b.spec().processor() != null && top.is(b.spec().printed())),
                            "Wrong processor print in assembly: " + top);
                    helper.assertTrue(bottom.isEmpty() || bottom.is(AEItems.SILICON_PRINT.asItem()),
                            "Wrong silicon print in assembly: " + bottom);
                    helper.assertTrue(middle.isEmpty() || middle.is(Items.REDSTONE), "Wrong middle assembly ingredient: " + middle);
                    helper.assertTrue(output.isEmpty() || state.banks.stream().anyMatch(
                                    b -> b.spec().processor() != null && output.is(b.spec().processor())),
                            "Unexpected assembly output: " + output);
                }
                boolean hasOutput = !output.isEmpty();
                if (hasOutput && !state.outputPresent.getOrDefault(machine, false)) {
                    (bank.spec().press() != null && output.is(bank.spec().press())
                            ? state.copyProductionCycles : state.productionCycles).merge(machine, 1, Integer::sum);
                }
                state.outputPresent.put(machine, hasOutput);
            }
        }
        helper.runAfterDelay(1, () -> observeRouting(helper, state));
    }

    private static boolean validInput(State state, BankSpec spec, Item item) {
        if (spec.press() != null) {
            return item == spec.input() || item == Items.IRON_BLOCK;
        }
        return item == Items.REDSTONE || item == AEItems.SILICON_PRINT.asItem()
               || state.banks.stream().anyMatch(bank -> bank.spec().processor() != null && item == bank.spec().printed());
    }

    private static void awaitCompletion(SFMGameTestHelper helper, State state, int remaining) {
        boolean busy = state.grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
        boolean complete = state.banks.stream().filter(bank -> bank.spec().processor() != null)
                .allMatch(bank -> stored(state, bank.spec().processor()) == REQUEST_COUNT);
        complete &= state.banks.stream().filter(bank -> bank.spec().press() != null)
                .allMatch(bank -> stored(state, bank.spec().press()) == PRESS_COPY_COUNT);
        if (busy || !complete) {
            helper.assertTrue(remaining > 0, "Inscriber autocrafting did not finish: busy=" + busy + " " + describe(state));
            helper.runAfterDelay(20, () -> awaitCompletion(helper, state, remaining - 1));
            return;
        }
        helper.assertTrue(state.submitted == CPU_COUNT && state.submissionTick > 0,
                "Not all four press-copy and three processor jobs were submitted together");
        helper.assertTrue(!state.mixedChestBanks.isEmpty(), "No printing bank queued iron blocks and printing material together");
        assertSingleCreativeCell(helper);
        helper.assertTrue(state.grid.getMachines(CreativeEnergyCellBlockEntity.class).size() == 1
                         && state.grid.getMachines(InscriberBlockEntity.class).size() == BANK_COUNT * MACHINES_PER_BANK,
                "Inscribers no longer share the single-cell AE2 network");
        helper.assertTrue(PartHelper.getPart(helper.getLevel(), helper.absolutePos(TERMINAL), Direction.NORTH)
                         instanceof CraftingTerminalPart
                         && state.terminal.getMainNode().isActive() && state.terminal.getMainNode().getGrid() == state.grid,
                "The inspectable ME Crafting Terminal is missing or disconnected");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(TERMINAL.relative(Direction.NORTH))).isAir(),
                "The crafting terminal's front aisle is obstructed");
        var sfmNetwork = CableNetworkManager.getOrRegisterNetworkFromManagerPosition(state.banks.get(0).manager()).orElseThrow();
        for (Bank bank : state.banks) {
            helper.assertManagerRunning(bank.manager());
            helper.assertTrue(CableNetworkManager.getOrRegisterNetworkFromManagerPosition(bank.manager()).orElseThrow() == sfmNetwork,
                    bank.spec().name() + " manager is disconnected from the shared SFM trunk");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(bank.chest().above())).isAir(),
                    bank.spec().name() + " chest cannot be opened");
            // The plan asserts the exact 64-item batch executions. Chest maxima are diagnostic:
            // SFM can distribute an entire batch before this tick's chest observation runs.
            helper.assertTrue(!bank.provider().getLogic().isBusy(), bank.spec().name() + " provider is still busy");
            assertEmpty(helper, helper.getItemHandler(bank.chest()), bank.spec().name() + " chest");
            assertEmpty(helper, helper.getItemHandler(bank.resultInterface(), Direction.UP), bank.spec().name() + " return interface");
            if (bank.spec().press() != null) {
                helper.assertTrue(stored(state, bank.spec().input()) == 0, "Unconsumed raw material for " + bank.spec().name());
                helper.assertTrue(stored(state, bank.spec().printed()) == 0, "Unconsumed print for " + bank.spec().name());
                helper.assertTrue(stored(state, bank.spec().press()) == PRESS_COPY_COUNT,
                        "Wrong copied press count for " + bank.spec().name());
                helper.assertTrue(bank.machines().stream().mapToInt(pos -> state.copyProductionCycles.getOrDefault(pos, 0)).sum()
                                 == PRESS_COPY_COUNT, "Wrong observed copied press total for " + bank.spec().name());
            }
            for (BlockPos machine : bank.machines()) {
                assertMachinePowered(helper, state, machine);
                helper.assertTrue(state.productionCycles.getOrDefault(machine, 0) >= 2,
                        bank.spec().name() + " machine did not produce multiple batches at " + machine + ": "
                        + state.productionCycles.getOrDefault(machine, 0));
                IItemHandler inventory = helper.getItemHandler(machine);
                if (bank.spec().press() != null) {
                    helper.assertTrue(state.copyProductionCycles.getOrDefault(machine, 0) >= 1,
                            "Machine did not contribute to press copying at " + machine);
                    ItemStack press = inventory.getStackInSlot(0);
                    helper.assertTrue(press.is(bank.spec().press()) && press.getCount() == 1,
                            "Original permanent press missing at completion: " + machine);
                }
                for (int slot = bank.spec().press() == null ? 0 : 1; slot < inventory.getSlots(); slot++) {
                    helper.assertTrue(inventory.getStackInSlot(slot).isEmpty(),
                            "Unfinished work in " + bank.spec().name() + " at " + machine + " slot " + slot);
                }
            }
        }
        helper.assertTrue(stored(state, Items.REDSTONE) == 0, "Unconsumed redstone");
        helper.assertTrue(stored(state, Items.IRON_BLOCK) == 0, "Unconsumed iron blocks");
        state.finished = true;
        SFM.LOGGER.info("SFM_AE2_INSCRIBER_BANK_SUCCESS submitted={} pressCopies={}/{}/{}/{} processors={}/{}/{} "
                       + "walls={}x{} banks={} machines={} creativeCells=1 terminal=crafting submissionTick={} "
                       + "mixedChestBanks={} copyProduction={} production={} maxChestItems={}",
                state.submitted, PRESS_COPY_COUNT, PRESS_COPY_COUNT, PRESS_COPY_COUNT, PRESS_COPY_COUNT,
                REQUEST_COUNT, REQUEST_COUNT, REQUEST_COUNT, WALL_SIDE, WALL_SIDE, BANK_COUNT,
                BANK_COUNT * MACHINES_PER_BANK, state.submissionTick, state.mixedChestBanks,
                productionSummary(state, state.copyProductionCycles), productionSummary(state, state.productionCycles),
                state.largestChestBatch);
        helper.succeed();
    }

    private static void assertSingleCreativeCell(SFMGameTestHelper helper) {
        int count = 0;
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 11; y++) {
                for (int z = 0; z < 27; z++) {
                    if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)))
                            .is(AEBlocks.CREATIVE_ENERGY_CELL.block())) {
                        count++;
                    }
                }
            }
        }
        helper.assertTrue(count == 1, "Expected exactly one creative AE energy cell in the entire fixture, found " + count);
    }

    private static void assertMachinePowered(SFMGameTestHelper helper, State state, BlockPos machine) {
        var node = helper.getBlockEntity(machine, InscriberBlockEntity.class).getMainNode();
        helper.assertTrue(node.getGrid() == state.grid && node.isActive() && node.isPowered(),
                "Inscriber lost the shared cable-fed AE2 power grid at " + machine);
    }

    private static String productionSummary(State state, Map<BlockPos, Integer> productionCycles) {
        StringBuilder result = new StringBuilder();
        for (Bank bank : state.banks) {
            var cycles = bank.machines().stream().mapToInt(pos -> productionCycles.getOrDefault(pos, 0)).summaryStatistics();
            result.append(bank.spec().name()).append("{machines=").append(cycles.getCount())
                    .append(",minCycles=").append(cycles.getMin()).append(",maxCycles=").append(cycles.getMax())
                    .append(",total=").append(cycles.getSum()).append("} ");
        }
        return result.toString().stripTrailing();
    }

    private static void assertEmpty(SFMGameTestHelper helper, IItemHandler inventory, String name) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            helper.assertTrue(inventory.getStackInSlot(slot).isEmpty(), name + " still contains items");
        }
    }

    private static long stored(State state, Item item) {
        return state.grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(item));
    }

    private static String describe(State state) {
        StringBuilder result = new StringBuilder();
        for (Bank bank : state.banks) {
            result.append(bank.spec().name()).append("{manager=").append(bank.manager().getState());
            if (state.grid != null && bank.spec().processor() != null) {
                result.append(", stored=").append(stored(state, bank.spec().processor()));
            }
            if (state.grid != null && bank.spec().press() != null) {
                result.append(", copies=").append(stored(state, bank.spec().press()));
            }
            result.append("} ");
        }
        return result.append("copyProduction=").append(productionSummary(state, state.copyProductionCycles))
                .append(" production=").append(productionSummary(state, state.productionCycles)).toString();
    }

    private record BankSpec(String name, Item press, Item input, Item printed, Item processor) {}

    private record Bank(BankSpec spec, BlockPos chest, BlockPos resultInterface,
                        PatternProviderBlockEntity provider, ManagerBlockEntity manager, List<BlockPos> machines) {}

    private record Job(BankSpec spec, boolean copy, Future<ICraftingPlan> plan) {}

    private static final class State {
        private final List<Bank> banks;
        private final CraftingTerminalPart terminal;
        private final List<Job> jobs = new ArrayList<>();
        private final Map<BlockPos, Boolean> outputPresent = new HashMap<>();
        private final Map<BlockPos, Integer> productionCycles = new HashMap<>();
        private final Map<BlockPos, Integer> copyProductionCycles = new HashMap<>();
        private final Set<String> mixedChestBanks = new HashSet<>();
        private final Map<String, Integer> largestChestBatch = new HashMap<>();
        private IGrid grid;
        private MachineSource source;
        private int submitted;
        private long submissionTick;
        private boolean finished;

        private State(List<Bank> banks, CraftingTerminalPart terminal) {
            this.banks = banks;
            this.terminal = terminal;
        }
    }
}
