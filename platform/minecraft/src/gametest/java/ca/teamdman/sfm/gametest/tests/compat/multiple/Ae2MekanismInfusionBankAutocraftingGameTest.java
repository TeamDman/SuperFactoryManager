package ca.teamdman.sfm.gametest.tests.compat.multiple;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.MachineSource;
import appeng.parts.reporting.AbstractTerminalPart;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismInfuseTypes;
import mekanism.common.registries.MekanismItems;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.resource.ResourceType;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.factory.TileEntityMetallurgicInfuserFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Exercises the scalable AE2 + SFM + Mekanism ultimate-infusing-factory topology.
 *
 * <p>AE2 12.9.9 separates the legacy interface's two responsibilities. Each infusion bank therefore
 * has an ME Interface that stocks its precursor and a Pattern Provider that emits precursor-free
 * processing recipes into the same bank's chest. One SFM manager keeps an eight-by-eight wall of
 * ultimate infusing factories stocked with that bank's infusion chemical, distributes processing inputs, and returns
 * results to the provider.
 * All bank managers share one cable trunk with a dedicated SFM power manager, which distributes
 * energy from one creative energy cube to every factory without Mekanism power cables.</p>
 */
@SuppressWarnings("DataFlowIssue")
@SFMGameTest
public class Ae2MekanismInfusionBankAutocraftingGameTest extends SFMGameTestDefinition {
    private static final int BANK_COUNT = 8;
    private static final int MACHINE_GRID_SIDE = 8;
    private static final int MACHINES_PER_BANK = MACHINE_GRID_SIDE * MACHINE_GRID_SIDE;
    private static final int REQUESTED_OUTPUT_COUNT = 64;
    private static final int SEEDED_ITEM_COUNT = 50_000;
    private static final int CRAFTING_CPU_COUNT = 10;
    private static final int CRAFTING_COPROCESSOR_LAYERS = 6;
    private static final int MEKANISM_CONFIGURATION_DELAY_TICKS = 1;
    private static final int READY_ATTEMPTS = 200;
    private static final int POWER_READY_ATTEMPTS = 100;
    private static final int PLAN_ATTEMPTS = 200;
    private static final int COMPLETION_ATTEMPTS = 480;
    private static final int COMPLETION_POLL_TICKS = 20;

    private static final int FIRST_BANK_Z = 3;
    private static final int BANK_Z_STRIDE = 3;
    private static final int AE_BACKBONE_X = 12;
    private static final int SFM_TRUNK_X = 9;

    private static final BlockPos AE_POWER_POS = new BlockPos(AE_BACKBONE_X, 2, 1);
    private static final BlockPos AE_CONTROLLER_POS = new BlockPos(AE_BACKBONE_X, 2, 2);
    private static final BlockPos DRIVE_POS = new BlockPos(AE_BACKBONE_X + 1, 2, 1);
    private static final BlockPos TERMINAL_POS = new BlockPos(AE_BACKBONE_X - 1, 3, 1);
    private static final BlockPos FACTORY_POWER_MANAGER_POS = new BlockPos(SFM_TRUNK_X + 1, 2, 1);
    private static final BlockPos FACTORY_POWER_CUBE_POS = new BlockPos(SFM_TRUNK_X + 1, 2, 2);

    @Override
    public String template() {
        return "15x11x27";
    }

    @Override
    public int maxTicks() {
        return 20 * 520;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        List<BankSpec> specs = createBankSpecs();
        helper.assertTrue(specs.size() == BANK_COUNT, "Expected exactly " + BANK_COUNT + " infusion banks");

        AeRuntime ae = createAe2Network(helper, specs);
        createSfmTrunk(helper);
        List<BankRuntime> banks = createSfmInfusionBanks(helper, specs, ae.providers());
        helper.assertTrue(
                banks.stream().allMatch(bank -> bank.machines().size() == MACHINES_PER_BANK),
                "Expected exactly " + MACHINES_PER_BANK + " ultimate infusing factories in every bank"
        );
        ManagerBlockEntity powerManager = createFactoryPowerManager(helper, banks);
        List<CraftRequest> requests = createCraftRequests(banks);
        helper.assertTrue(
                CRAFTING_CPU_COUNT >= requests.size(),
                "Expected at least one AE2 crafting CPU per top-level job: cpus=" + CRAFTING_CPU_COUNT
                + ", jobs=" + requests.size()
        );
        TestState state = new TestState(ae, banks, powerManager, requests);

        // Mekanism sideChanged sends a custom tile-update packet immediately. During an integrated
        // GameTest, configuring a freshly placed tile in this same construction tick races the
        // vanilla block-entity update on the client and produces one "no valid tile" warning per
        // sideChanged call. Reacquire and configure the tiles after placement has had a full tick
        // to propagate, then give the resulting configuration updates a tick before starting work.
        helper.runAfterDelay(MEKANISM_CONFIGURATION_DELAY_TICKS, () -> {
            configureMekanismTiles(helper, banks);
            helper.runAfterDelay(1, () -> waitForGridAndSeedStorage(helper, state, READY_ATTEMPTS));
        });
    }

    private static List<BankSpec> createBankSpecs() {
        Item osmiumIngot = MekanismItems.PROCESSED_RESOURCES
                .get(ResourceType.INGOT, PrimaryResource.OSMIUM)
                .asItem();

        return List.of(
                new BankSpec(
                        "redstone",
                        MekanismItems.ENRICHED_REDSTONE.asItem(),
                        MekanismInfuseTypes.REDSTONE.get(),
                        List.of(
                                recipe(osmiumIngot, MekanismItems.BASIC_CONTROL_CIRCUIT.asItem()),
                                recipe(Items.IRON_INGOT, MekanismItems.INFUSED_ALLOY.asItem())
                        )
                ),
                new BankSpec(
                        "diamond",
                        MekanismItems.ENRICHED_DIAMOND.asItem(),
                        MekanismInfuseTypes.DIAMOND.get(),
                        List.of(
                                recipe(MekanismItems.INFUSED_ALLOY.asItem(), MekanismItems.REINFORCED_ALLOY.asItem()),
                                recipe(MekanismItems.OBSIDIAN_DUST.asItem(), MekanismItems.REFINED_OBSIDIAN_DUST.asItem())
                        )
                ),
                new BankSpec(
                        "carbon",
                        MekanismItems.ENRICHED_CARBON.asItem(),
                        MekanismInfuseTypes.CARBON.get(),
                        List.of(
                                recipe(Items.IRON_INGOT, MekanismItems.ENRICHED_IRON.asItem()),
                                recipe(MekanismItems.ENRICHED_IRON.asItem(), MekanismItems.STEEL_DUST.asItem())
                        )
                ),
                new BankSpec(
                        "refined obsidian",
                        MekanismItems.ENRICHED_OBSIDIAN.asItem(),
                        MekanismInfuseTypes.REFINED_OBSIDIAN.get(),
                        List.of()
                ),
                new BankSpec(
                        "fungi",
                        Items.RED_MUSHROOM,
                        MekanismInfuseTypes.FUNGI.get(),
                        List.of(recipe(Items.DIRT, Items.MYCELIUM))
                ),
                new BankSpec(
                        "gold",
                        MekanismItems.ENRICHED_GOLD.asItem(),
                        MekanismInfuseTypes.GOLD.get(),
                        List.of(recipe(Items.NETHERITE_SCRAP, 4, MekanismItems.NETHERITE_DUST.asItem(), 1))
                ),
                new BankSpec(
                        "biomass",
                        MekanismItems.BIO_FUEL.asItem(),
                        MekanismInfuseTypes.BIO.get(),
                        List.of(recipe(Items.DIRT, Items.PODZOL))
                ),
                new BankSpec(
                        "tin",
                        MekanismItems.ENRICHED_TIN.asItem(),
                        MekanismInfuseTypes.TIN.get(),
                        List.of(recipe(Items.COPPER_INGOT, 3, MekanismItems.BRONZE_INGOT.asItem(), 4))
                )
        );
    }

    private static ProcessingRecipe recipe(Item input, Item output) {
        return recipe(input, 1, output, 1);
    }

    private static ProcessingRecipe recipe(Item input, int inputCount, Item output, int outputCount) {
        return new ProcessingRecipe(input, inputCount, output, outputCount);
    }

    private static AeRuntime createAe2Network(SFMGameTestHelper helper, List<BankSpec> specs) {
        helper.setBlock(AE_POWER_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        // The terminal, drive, ten accelerated CPUs, eight interfaces, and eight providers exceed the
        // eight-channel limit of an ad-hoc network. A controller enables the dense backbone's
        // 32 channels and makes the fixture representative of the intended player build.
        helper.setBlock(AE_CONTROLLER_POS, AEBlocks.CONTROLLER.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());

        for (int z = 1; z <= 25; z++) {
            placeDenseCable(helper, new BlockPos(AE_BACKBONE_X, 3, z));
        }

        placeGlassCable(helper, TERMINAL_POS);
        AbstractTerminalPart terminal = PartHelper.setPart(
                helper.getLevel(),
                helper.absolutePos(TERMINAL_POS),
                Direction.NORTH,
                null,
                AEParts.TERMINAL.asItem()
        );
        helper.assertTrue(terminal != null, "Could not attach the ME Terminal to the AE2 backbone");
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_AE2_INFUSION_FIXTURE terminal_type={}", terminal.getClass().getName());

        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS, DriveBlockEntity.class);
        for (int cell = 0; cell < 3; cell++) {
            ItemStack remainder = drive.getInternalInventory().addItems(AEItems.ITEM_CELL_64K.stack());
            helper.assertTrue(remainder.isEmpty(), "AE2 drive rejected item storage cell " + cell);
        }

        for (int cpu = 0; cpu < CRAFTING_CPU_COUNT; cpu++) {
            BlockPos storagePos = new BlockPos(AE_BACKBONE_X + 1, 3, 1 + cpu * 2);
            helper.setBlock(storagePos, AEBlocks.CRAFTING_STORAGE_256K.block());
            for (int layer = 1; layer <= CRAFTING_COPROCESSOR_LAYERS; layer++) {
                helper.setBlock(storagePos.above(layer), AEBlocks.CRAFTING_ACCELERATOR.block());
            }
        }

        List<PatternProviderBlockEntity> providers = new ArrayList<>(specs.size());
        List<InterfaceBlockEntity> interfaces = new ArrayList<>(specs.size());
        for (int bankIndex = 0; bankIndex < specs.size(); bankIndex++) {
            BankSpec spec = specs.get(bankIndex);
            BlockPos providerPos = providerPos(bankIndex);
            BlockPos interfacePos = interfacePos(bankIndex);

            helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(interfacePos, AEBlocks.INTERFACE.block());

            PatternProviderBlockEntity provider = helper.getBlockEntity(providerPos, PatternProviderBlockEntity.class);
            provider.setPushDirection(Direction.WEST);
            for (ProcessingRecipe recipe : spec.recipes()) {
                // One AE2 pattern operation represents 64 underlying Mekanism recipe executions,
                // preserving ratios while feeding each 64-machine bank in a single provider push.
                ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[]{new GenericStack(
                                AEItemKey.of(recipe.input()),
                                (long) recipe.inputCount() * 64
                        )},
                        new GenericStack[]{new GenericStack(
                                AEItemKey.of(recipe.output()),
                                (long) recipe.outputCount() * 64
                        )}
                );
                ItemStack remainder = provider.getLogic().getPatternInv().addItems(encoded);
                helper.assertTrue(
                        remainder.isEmpty(),
                        "Pattern provider for " + spec.name() + " rejected pattern for " + recipe.output()
                );
            }

            InterfaceBlockEntity stockInterface = helper.getBlockEntity(interfacePos, InterfaceBlockEntity.class);
            var stockConfig = stockInterface.getInterfaceLogic().getConfig();
            // Each slot has its own stocking target; one configured slot cannot feed the whole bank quickly.
            for (int slot = 0; slot < stockConfig.size(); slot++) {
                long configured = stockConfig.insert(
                        slot,
                        AEItemKey.of(spec.precursor()),
                        64,
                        Actionable.MODULATE
                );
                helper.assertTrue(
                        configured == 64,
                        "Could not configure " + spec.name() + " interface slot " + (slot + 1)
                        + " to stock 64 precursor items"
                );
            }

            providers.add(provider);
            interfaces.add(stockInterface);
        }
        return new AeRuntime(terminal, List.copyOf(providers), List.copyOf(interfaces));
    }

    private static void placeDenseCable(SFMGameTestHelper helper, BlockPos localPos) {
        var cable = PartHelper.setPart(
                helper.getLevel(),
                helper.absolutePos(localPos),
                null,
                null,
                AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT)
        );
        helper.assertTrue(cable != null, "Could not place AE2 dense cable at " + localPos);
    }

    private static void placeGlassCable(SFMGameTestHelper helper, BlockPos localPos) {
        var cable = PartHelper.setPart(
                helper.getLevel(),
                helper.absolutePos(localPos),
                null,
                null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)
        );
        helper.assertTrue(cable != null, "Could not place AE2 glass cable at " + localPos);
    }

    private static List<BankRuntime> createSfmInfusionBanks(
            SFMGameTestHelper helper,
            List<BankSpec> specs,
            List<PatternProviderBlockEntity> providers
    ) {
        List<BankRuntime> banks = new ArrayList<>(specs.size());
        for (int bankIndex = 0; bankIndex < specs.size(); bankIndex++) {
            BankSpec spec = specs.get(bankIndex);
            BlockPos managerPos = managerPos(bankIndex);
            BlockPos ingredientsPos = ingredientsPos(bankIndex);
            BlockPos stockInterfacePos = interfacePos(bankIndex);
            BlockPos patternProviderPos = providerPos(bankIndex);
            BlockPos tankPos = new BlockPos(SFM_TRUNK_X, 3, bankZ(bankIndex));
            List<BlockPos> machines = machinePositions(bankIndex);

            helper.setBlock(ingredientsPos, Blocks.CHEST);
            for (int x = 1; x <= 11; x++) {
                if (x != managerPos.getX()) {
                    helper.setBlock(new BlockPos(x, 2, bankZ(bankIndex)), SFMBlocks.CABLE.get());
                }
            }
            // Each bank is an eight-wide by eight-high infusing-factory wall. A matching cable wall
            // immediately behind it joins every machine to the shared trunk without relying on
            // machines touching one another for SFM connectivity.
            for (BlockPos machinePos : machines) {
                helper.setBlock(machinePos.relative(Direction.NORTH), SFMBlocks.CABLE.get());
            }
            // Rise once from the main SFM cable row to reach the stocking interface. The
            // interface itself now occupies the AE branch position above the pattern provider,
            // leaving literal air above the ingredients chest so players can open it.
            helper.setBlock(new BlockPos(11, 3, bankZ(bankIndex)), SFMBlocks.CABLE.get());

            // Buffer the bank's infusion above the shared trunk, immediately beside the cable
            // wall. The top accepts recovered infusion and the bottom supplies the balancing
            // pass, matching the manager program's explicit side contract.
            helper.setBlock(tankPos, MekanismBlocks.ULTIMATE_CHEMICAL_TANK.getBlock());

            for (BlockPos machinePos : machines) {
                helper.setBlock(machinePos, MekanismBlocks.getFactory(FactoryTier.ULTIMATE, FactoryType.INFUSING).getBlock());
            }

            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

            LabelPositionHolder labels = LabelPositionHolder.empty()
                    .add("tank", helper.absolutePos(tankPos))
                    .add("ingredients", helper.absolutePos(ingredientsPos))
                    .add("stock_interface", helper.absolutePos(stockInterfacePos))
                    .add("pattern_provider", helper.absolutePos(patternProviderPos));
            for (BlockPos machinePos : machines) {
                labels.add("machine", helper.absolutePos(machinePos));
            }
            labels.save(manager.getDisk());
            manager.setProgram(createManagerProgram(spec));

            BlockPos signPos = managerPos.above();
            helper.setBlock(
                    signPos,
                    Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8)
            );
            helper.setSignText(signPos, Component.literal(spec.name()));

            banks.add(new BankRuntime(
                    spec,
                    manager,
                    ingredientsPos,
                    tankPos,
                    stockInterfacePos,
                    patternProviderPos,
                    providers.get(bankIndex),
                    machines
            ));
        }
        return List.copyOf(banks);
    }

    private static void createSfmTrunk(SFMGameTestHelper helper) {
        int lastBankZ = bankZ(BANK_COUNT - 1);
        for (int z = 1; z <= lastBankZ; z++) {
            helper.setBlock(new BlockPos(SFM_TRUNK_X, 2, z), SFMBlocks.CABLE.get());
        }
    }

    private static ManagerBlockEntity createFactoryPowerManager(
            SFMGameTestHelper helper,
            List<BankRuntime> banks
    ) {
        helper.setBlock(FACTORY_POWER_CUBE_POS, MekanismBlocks.CREATIVE_ENERGY_CUBE.getBlock());

        helper.setBlock(FACTORY_POWER_MANAGER_POS, SFMBlocks.MANAGER.get());
        ManagerBlockEntity powerManager = helper.getBlockEntity(
                FACTORY_POWER_MANAGER_POS,
                ManagerBlockEntity.class
        );
        powerManager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        LabelPositionHolder labels = LabelPositionHolder.empty()
                .add("power", helper.absolutePos(FACTORY_POWER_CUBE_POS));
        banks.stream()
                .flatMap(bank -> bank.machines().stream())
                .map(helper::absolutePos)
                .forEach(machinePos -> labels.add("machine", machinePos));
        labels.save(powerManager.getDisk());
        powerManager.setProgram("""
                NAME "infusion factory power"
                EVERY 5 TICKS DO
                    INPUT forge_energy:forge:energy FROM power NORTH SIDE
                    OUTPUT forge_energy:forge:energy TO machine NORTH SIDE
                END
                """.stripIndent());

        BlockPos signPos = FACTORY_POWER_MANAGER_POS.above();
        helper.setBlock(
                signPos,
                Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8)
        );
        helper.setSignText(signPos, Component.literal("factory power"));
        return powerManager;
    }

    private static void configureMekanismTiles(SFMGameTestHelper helper, List<BankRuntime> banks) {
        for (BankRuntime bank : banks) {
            TileEntityChemicalTank tank = helper.getBlockEntity(
                    bank.tankPos(),
                    TileEntityChemicalTank.class
            );
            RelativeSide tankTop = RelativeSide.fromDirections(tank.getDirection(), Direction.UP);
            RelativeSide tankBottom = RelativeSide.fromDirections(tank.getDirection(), Direction.DOWN);
            configureTransmission(
                    tank.getConfig(),
                    TransmissionType.INFUSION,
                    Map.of(tankTop, DataType.INPUT, tankBottom, DataType.OUTPUT)
            );

            for (BlockPos machinePos : bank.machines()) {
                TileEntityMetallurgicInfuserFactory infuser = helper.getBlockEntity(
                        machinePos,
                        TileEntityMetallurgicInfuserFactory.class
                );
                helper.assertTrue(
                        infuser.tier == FactoryTier.ULTIMATE && infuser.tier.processes == 9,
                        bank.spec().name() + " machine is not a nine-process ultimate infusing factory"
                );
                configureInfuser(infuser);
                if (!infuser.isSorting()) {
                    infuser.toggleSorting();
                }
                infuser.getComponent().addUpgrades(Upgrade.SPEED, Upgrade.SPEED.getMax());
                infuser.getComponent().addUpgrades(Upgrade.ENERGY, Upgrade.ENERGY.getMax());
            }
        }

        TileEntityEnergyCube energyCube = helper.getBlockEntity(
                FACTORY_POWER_CUBE_POS,
                TileEntityEnergyCube.class
        );
        configureEnergyOutput(energyCube, Direction.NORTH);
        energyCube.setEnergy(0, EnergyCubeTier.CREATIVE.getMaxEnergy());
    }

    private static void configureEnergyOutput(TileEntityEnergyCube energyCube, Direction outputDirection) {
        energyCube.setFacing(outputDirection);
        RelativeSide outputSide = RelativeSide.fromDirections(energyCube.getDirection(), outputDirection);
        configureTransmission(
                energyCube.getConfig(),
                TransmissionType.ENERGY,
                Map.of(outputSide, DataType.OUTPUT)
        );
    }

    private static void configureInfuser(TileEntityMetallurgicInfuserFactory infuser) {
        Direction facing = infuser.getDirection();
        RelativeSide itemInputSide = RelativeSide.fromDirections(facing, Direction.SOUTH);
        RelativeSide energyInputSide = RelativeSide.fromDirections(facing, Direction.NORTH);
        RelativeSide topSide = RelativeSide.fromDirections(facing, Direction.UP);
        RelativeSide bottomSide = RelativeSide.fromDirections(facing, Direction.DOWN);

        configureTransmission(
                infuser.getConfig(),
                TransmissionType.ITEM,
                Map.of(
                        topSide, DataType.EXTRA,
                        itemInputSide, DataType.INPUT,
                        bottomSide, DataType.OUTPUT
                )
        );
        configureTransmission(
                infuser.getConfig(),
                TransmissionType.INFUSION,
                Map.of(topSide, DataType.INPUT_OUTPUT)
        );
        configureTransmission(
                infuser.getConfig(),
                TransmissionType.ENERGY,
                Map.of(energyInputSide, DataType.INPUT)
        );
    }

    private static void configureTransmission(
            TileComponentConfig component,
            TransmissionType transmission,
            Map<RelativeSide, DataType> nonEmptySides
    ) {
        ConfigInfo config = component.getConfig(transmission);
        List<RelativeSide> changedSides = new ArrayList<>();
        for (RelativeSide side : RelativeSide.values()) {
            DataType desired = nonEmptySides.getOrDefault(side, DataType.NONE);
            if (config.getDataType(side) != desired) {
                config.setDataType(desired, side);
                changedSides.add(side);
            }
        }
        // sideChanged invalidates capabilities and notifies neighbours, so preserve it for each
        // real transition while avoiding packets for sides that already had the desired mode.
        for (RelativeSide side : changedSides) {
            component.sideChanged(transmission, side);
        }
    }

    private static String createManagerProgram(BankSpec spec) {
        // On 1.19.2 the resource-type spelling is infusion::. Newer branches alias it to chemical::.
        return """
                NAME "%s infusion bank"
                EVERY 20 TICKS DO
                    IF tank HAS LT 100 infusion:: THEN
                        INPUT FROM stock_interface BOTTOM SIDE
                        OUTPUT RETAIN 16 TO EACH machine TOP SIDE
                        FORGET
                    END

                    INPUT infusion:: FROM tank BOTTOM SIDE
                    INPUT RETAIN 640 infusion:: FROM EACH machine TOP SIDE
                    OUTPUT RETAIN 640 infusion:: TO EACH machine TOP SIDE
                    OUTPUT infusion:: TO tank TOP SIDE
                    FORGET

                    INPUT FROM ingredients
                    OUTPUT RETAIN 18 TO EACH machine SOUTH SIDE
                    FORGET

                    INPUT FROM machine BOTTOM SIDE
                    OUTPUT TO pattern_provider TOP SIDE
                END
                """.formatted(spec.name()).stripIndent();
    }

    private static int recipeInputBatchSize(BankSpec spec) {
        int batchSize = spec.recipes().stream()
                .mapToInt(ProcessingRecipe::inputCount)
                .max()
                .orElse(1);
        boolean allRecipesUseSameBatchSize = spec.recipes().stream()
                .allMatch(recipe -> recipe.inputCount() == batchSize);
        if (!allRecipesUseSameBatchSize) {
            throw new IllegalArgumentException(
                    "Bank " + spec.name() + " mixes processing input batch sizes; use resource-specific routing"
            );
        }
        return batchSize;
    }

    private static List<CraftRequest> createCraftRequests(List<BankRuntime> banks) {
        List<CraftRequest> requests = new ArrayList<>();
        for (BankRuntime bank : banks) {
            for (ProcessingRecipe recipe : bank.spec().recipes()) {
                requests.add(new CraftRequest(bank, recipe.output(), REQUESTED_OUTPUT_COUNT));
            }
        }
        return List.copyOf(requests);
    }

    private static void waitForGridAndSeedStorage(
            SFMGameTestHelper helper,
            TestState state,
            int attemptsRemaining
    ) {
        IGrid grid = state.ae().providers().get(0).getMainNode().getGrid();
        boolean providersActive = state.ae().providers().stream().allMatch(provider -> provider.getMainNode().isActive());
        boolean interfacesActive = state.ae().interfaces().stream().allMatch(iface -> iface.getMainNode().isActive());
        boolean terminalActive = state.ae().terminal().getMainNode().isActive();
        boolean enoughCpus = grid != null && grid.getCraftingService().getCpus().size() >= CRAFTING_CPU_COUNT;
        boolean everyOutputCraftable = grid != null && state.requests().stream().allMatch(
                request -> grid.getCraftingService().isCraftable(AEItemKey.of(request.output()))
        );

        if (grid == null || !providersActive || !interfacesActive || !terminalActive || !enoughCpus || !everyOutputCraftable) {
            if (attemptsRemaining <= 0) {
                helper.fail(
                        "AE2 network did not become ready: providers=" + providersActive
                        + ", interfaces=" + interfacesActive
                        + ", terminal=" + terminalActive
                        + ", cpus=" + (grid == null ? 0 : grid.getCraftingService().getCpus().size())
                        + ", craftable=" + everyOutputCraftable
                );
                return;
            }
            helper.runAfterDelay(1, () -> waitForGridAndSeedStorage(helper, state, attemptsRemaining - 1));
            return;
        }

        state.grid = grid;
        state.source = new MachineSource(state.ae().terminal());
        seedAeStorage(helper, state);
        helper.assertManagerRunning(state.powerManager());
        waitForFactoryPower(helper, state, POWER_READY_ATTEMPTS);
    }

    private static void waitForFactoryPower(
            SFMGameTestHelper helper,
            TestState state,
            int attemptsRemaining
    ) {
        boolean everyMachinePowered = state.banks().stream()
                .flatMap(bank -> bank.machines().stream())
                .map(machinePos -> helper.getBlockEntity(machinePos, TileEntityMetallurgicInfuserFactory.class))
                .noneMatch(infuser -> infuser.getEnergy(0).isZero());
        if (!everyMachinePowered) {
            if (attemptsRemaining <= 0) {
                helper.fail(
                        "The shared SFM power manager did not energize every ultimate infusing factory\n"
                        + describeFactory(helper, state)
                );
                return;
            }
            helper.runAfterDelay(5, () -> waitForFactoryPower(helper, state, attemptsRemaining - 1));
            return;
        }

        state.pending.addAll(state.requests());
        helper.runAfterDelay(1, () -> startNextCraftCalculation(helper, state));
    }

    private static void seedAeStorage(SFMGameTestHelper helper, TestState state) {
        Map<Item, Long> seedCounts = new LinkedHashMap<>();
        Set<Item> producedItems = new HashSet<>();
        for (BankRuntime bank : state.banks()) {
            seedCounts.put(bank.spec().precursor(), (long) SEEDED_ITEM_COUNT);
            for (ProcessingRecipe recipe : bank.spec().recipes()) {
                producedItems.add(recipe.output());
            }
        }
        for (BankRuntime bank : state.banks()) {
            for (ProcessingRecipe recipe : bank.spec().recipes()) {
                if (!producedItems.contains(recipe.input())) {
                    seedCounts.put(recipe.input(), (long) SEEDED_ITEM_COUNT);
                }
            }
        }

        for (Map.Entry<Item, Long> seed : seedCounts.entrySet()) {
            long inserted = state.grid.getStorageService().getInventory().insert(
                    AEItemKey.of(seed.getKey()),
                    seed.getValue(),
                    Actionable.MODULATE,
                    state.source
            );
            helper.assertTrue(
                    inserted == seed.getValue(),
                    "AE2 storage accepted only " + inserted + " of " + seed.getValue() + " " + seed.getKey()
            );
        }

    }

    private static void startNextCraftCalculation(SFMGameTestHelper helper, TestState state) {
        if (state.pending.isEmpty()) {
            helper.runAfterDelay(
                    COMPLETION_POLL_TICKS,
                    () -> waitForFactoryCompletion(helper, state, COMPLETION_ATTEMPTS)
            );
            return;
        }

        boolean cpuAvailable = state.grid.getCraftingService().getCpus().stream().anyMatch(cpu -> !cpu.isBusy());
        if (!cpuAvailable) {
            // AE2 does not queue a top-level job when every crafting CPU is occupied. Keep the
            // request pending until either accelerated CPU becomes available; the GameTest's
            // maxTicks remains the outer bound if an earlier job never completes.
            helper.runAfterDelay(1, () -> startNextCraftCalculation(helper, state));
            return;
        }

        state.current = state.pending.removeFirst();
        state.planFuture = state.grid.getCraftingService().beginCraftingCalculation(
                helper.getLevel(),
                () -> state.source,
                AEItemKey.of(state.current.output()),
                state.current.amount(),
                CalculationStrategy.REPORT_MISSING_ITEMS
        );
        helper.runAfterDelay(1, () -> waitForPlanAndSubmit(helper, state, PLAN_ATTEMPTS));
    }

    private static void waitForPlanAndSubmit(
            SFMGameTestHelper helper,
            TestState state,
            int attemptsRemaining
    ) {
        if (!state.planFuture.isDone()) {
            if (attemptsRemaining <= 0) {
                helper.fail("AE2 did not calculate the plan for " + state.current.output());
                return;
            }
            helper.runAfterDelay(1, () -> waitForPlanAndSubmit(helper, state, attemptsRemaining - 1));
            return;
        }

        ICraftingPlan plan;
        try {
            plan = state.planFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            helper.fail("Interrupted while calculating " + state.current.output() + ": " + e);
            return;
        } catch (ExecutionException e) {
            helper.fail("AE2 plan calculation failed for " + state.current.output() + ": " + e.getCause());
            return;
        }
        helper.assertTrue(
                plan.missingItems().isEmpty(),
                "AE2 reports missing inputs for " + state.current.output() + ": " + plan.missingItems()
        );

        ICraftingSubmitResult result = state.grid.getCraftingService().submitJob(
                plan,
                null,
                null,
                true,
                state.source
        );
        helper.assertTrue(
                result.successful(),
                "AE2 rejected the 64-item job for " + state.current.output() + ": "
                + result.errorCode() + " " + result.errorDetail()
        );
        state.submitted.add(state.current);
        helper.runAfterDelay(1, () -> startNextCraftCalculation(helper, state));
    }

    private static void waitForFactoryCompletion(
            SFMGameTestHelper helper,
            TestState state,
            int attemptsRemaining
    ) {
        boolean craftingBusy = state.grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
        boolean outputsPresent = state.requests().stream().allMatch(
                request -> storedCount(state.grid, request.output()) >= request.amount()
        );
        if (craftingBusy || !outputsPresent) {
            if (attemptsRemaining <= 0) {
                helper.fail(
                        "Infusion-bank factory did not complete: craftingBusy=" + craftingBusy
                        + ", outputsPresent=" + outputsPresent
                        + "\n" + describeFactory(helper, state)
                );
                return;
            }
            helper.runAfterDelay(
                    COMPLETION_POLL_TICKS,
                    () -> waitForFactoryCompletion(helper, state, attemptsRemaining - 1)
            );
            return;
        }

        assertSuccessfulFactory(helper, state);
        helper.succeed();
    }

    private static void assertSuccessfulFactory(SFMGameTestHelper helper, TestState state) {
        helper.assertTrue(
                state.submitted.size() == state.requests().size(),
                "Submitted " + state.submitted.size() + " of " + state.requests().size() + " requested jobs"
        );
        helper.assertTrue(
                PartHelper.getPart(
                        helper.getLevel(),
                        helper.absolutePos(TERMINAL_POS),
                        Direction.NORTH
                ) instanceof AbstractTerminalPart,
                "The inspectable ME Terminal is no longer attached"
        );

        for (BankRuntime bank : state.banks()) {
            helper.assertManagerRunning(bank.manager());
            helper.assertTrue(
                    helper.getLevel().getBlockState(helper.absolutePos(bank.ingredientsPos().above())).isAir(),
                    bank.spec().name() + " ingredients chest is obstructed from above"
            );
            var stockConfig = helper
                    .getBlockEntity(bank.stockInterfacePos(), InterfaceBlockEntity.class)
                    .getInterfaceLogic()
                    .getConfig();
            helper.assertTrue(stockConfig.size() > 0, bank.spec().name() + " interface has no stocking slots");
            for (int slot = 0; slot < stockConfig.size(); slot++) {
                GenericStack configured = stockConfig.getStack(slot);
                helper.assertTrue(
                        configured != null
                        && configured.what().equals(AEItemKey.of(bank.spec().precursor()))
                        && configured.amount() == 64,
                        bank.spec().name() + " interface slot " + (slot + 1)
                        + " is not configured to stock 64 precursor items"
                );
            }
            helper.assertTrue(
                    !bank.provider().getLogic().isBusy(),
                    bank.spec().name() + " pattern provider still has unfinished processing work"
            );
            helper.assertTrue(
                    bankHasExpectedInfusion(helper, bank),
                    bank.spec().name() + " manager never stocked its expected infusion type"
            );
            helper.assertTrue(
                    bankContainsOnlyExpectedInfusion(helper, bank),
                    bank.spec().name() + " bank contains an unexpected infusion type"
            );
        }

        helper.assertManagerRunning(state.powerManager());
        var sharedNetwork = CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(state.powerManager())
                .orElseThrow();
        for (BankRuntime bank : state.banks()) {
            helper.assertTrue(
                    CableNetworkManager.getOrRegisterNetworkFromManagerPosition(bank.manager()).orElseThrow()
                    == sharedNetwork,
                    bank.spec().name() + " manager is not connected to the shared SFM trunk"
            );
        }
    }

    private static boolean bankHasExpectedInfusion(SFMGameTestHelper helper, BankRuntime bank) {
        for (BlockPos machinePos : bank.machines()) {
            TileEntityMetallurgicInfuserFactory infuser = helper.getBlockEntity(
                    machinePos,
                    TileEntityMetallurgicInfuserFactory.class
            );
            if (!infuser.getInfusionTank().getStack().isEmpty()
                && infuser.getInfusionTank().getStack().getType().equals(bank.spec().infusionType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean bankContainsOnlyExpectedInfusion(SFMGameTestHelper helper, BankRuntime bank) {
        for (BlockPos machinePos : bank.machines()) {
            TileEntityMetallurgicInfuserFactory infuser = helper.getBlockEntity(
                    machinePos,
                    TileEntityMetallurgicInfuserFactory.class
            );
            if (!infuser.getInfusionTank().getStack().isEmpty()
                && !infuser.getInfusionTank().getStack().getType().equals(bank.spec().infusionType())) {
                return false;
            }
        }
        return true;
    }

    private static long storedCount(IGrid grid, Item item) {
        return grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(item));
    }

    private static String describeFactory(SFMGameTestHelper helper, TestState state) {
        StringBuilder result = new StringBuilder("powerManager=")
                .append(state.powerManager().getState())
                .append(", powerSource=")
                .append(helper.getBlockEntity(FACTORY_POWER_CUBE_POS, TileEntityEnergyCube.class).getEnergy(0))
                .append(", outputs={");
        for (int i = 0; i < state.requests().size(); i++) {
            CraftRequest request = state.requests().get(i);
            if (i > 0) {
                result.append(", ");
            }
            result.append(request.output()).append('=').append(storedCount(state.grid, request.output()));
        }
        result.append("}, banks=[");
        for (int bankIndex = 0; bankIndex < state.banks().size(); bankIndex++) {
            BankRuntime bank = state.banks().get(bankIndex);
            if (bankIndex > 0) {
                result.append(", ");
            }
            IItemHandler stock = helper.getItemHandler(bank.stockInterfacePos(), Direction.DOWN);
            IItemHandler ingredients = helper.getItemHandler(bank.ingredientsPos());
            int matchingInfusers = 0;
            int fullyStockedInfusers = 0;
            int emptyInfusers = 0;
            int wrongInfusers = 0;
            int poweredInfusers = 0;
            long totalInfusion = 0;
            for (BlockPos machinePos : bank.machines()) {
                TileEntityMetallurgicInfuserFactory infuser = helper.getBlockEntity(
                        machinePos,
                        TileEntityMetallurgicInfuserFactory.class
                );
                if (!infuser.getEnergy(0).isZero()) {
                    poweredInfusers++;
                }
                if (infuser.getInfusionTank().getStack().isEmpty()) {
                    emptyInfusers++;
                    continue;
                }
                if (!infuser.getInfusionTank().getStack().getType().equals(bank.spec().infusionType())) {
                    wrongInfusers++;
                    continue;
                }
                matchingInfusers++;
                long amount = infuser.getInfusionTank().getStack().getAmount();
                totalInfusion += amount;
                if (amount >= 100) {
                    fullyStockedInfusers++;
                }
            }
            result.append(bank.spec().name())
                    .append("{manager=").append(bank.manager().getState())
                    .append(", interfacePrecursor=").append(helper.count(stock, bank.spec().precursor()))
                    .append(", ingredients=").append(helper.count(ingredients, (Item) null))
                    .append(", providerBusy=").append(bank.provider().getLogic().isBusy())
                    .append(", infusionSummary={matching=").append(matchingInfusers)
                    .append(", atLeast100=").append(fullyStockedInfusers)
                    .append(", empty=").append(emptyInfusers)
                    .append(", wrong=").append(wrongInfusers)
                    .append(", total=").append(totalInfusion)
                    .append(", powered=").append(poweredInfusers).append('/').append(bank.machines().size())
                    .append("}}");
        }
        return result.append(']').toString();
    }

    private static int bankZ(int bankIndex) {
        return FIRST_BANK_Z + bankIndex * BANK_Z_STRIDE;
    }

    private static BlockPos managerPos(int bankIndex) {
        return new BlockPos(SFM_TRUNK_X + 1, 2, bankZ(bankIndex));
    }

    private static List<BlockPos> machinePositions(int bankIndex) {
        int z = bankZ(bankIndex) + 1;
        List<BlockPos> machines = new ArrayList<>(MACHINES_PER_BANK);
        for (int yOffset = 0; yOffset < MACHINE_GRID_SIDE; yOffset++) {
            for (int distanceFromTrunk = 1; distanceFromTrunk <= MACHINE_GRID_SIDE; distanceFromTrunk++) {
                machines.add(new BlockPos(SFM_TRUNK_X - distanceFromTrunk, 2 + yOffset, z));
            }
        }
        return List.copyOf(machines);
    }

    private static BlockPos ingredientsPos(int bankIndex) {
        return new BlockPos(10, 2, bankZ(bankIndex) + 1);
    }

    private static BlockPos providerPos(int bankIndex) {
        return new BlockPos(11, 2, bankZ(bankIndex) + 1);
    }

    private static BlockPos interfacePos(int bankIndex) {
        return new BlockPos(11, 3, bankZ(bankIndex) + 1);
    }

    private record ProcessingRecipe(Item input, int inputCount, Item output, int outputCount) {
    }

    private record BankSpec(String name, Item precursor, InfuseType infusionType, List<ProcessingRecipe> recipes) {
    }

    private record AeRuntime(
            AbstractTerminalPart terminal,
            List<PatternProviderBlockEntity> providers,
            List<InterfaceBlockEntity> interfaces
    ) {
    }

    private record BankRuntime(
            BankSpec spec,
            ManagerBlockEntity manager,
            BlockPos ingredientsPos,
            BlockPos tankPos,
            BlockPos stockInterfacePos,
            BlockPos patternProviderPos,
            PatternProviderBlockEntity provider,
            List<BlockPos> machines
    ) {
    }

    private record CraftRequest(BankRuntime bank, Item output, long amount) {
    }

    private static final class TestState {
        private final AeRuntime ae;
        private final List<BankRuntime> banks;
        private final ManagerBlockEntity powerManager;
        private final List<CraftRequest> requests;
        private final ArrayDeque<CraftRequest> pending = new ArrayDeque<>();
        private final List<CraftRequest> submitted = new ArrayList<>();
        private IGrid grid;
        private MachineSource source;
        private CraftRequest current;
        private Future<ICraftingPlan> planFuture;

        private TestState(
                AeRuntime ae,
                List<BankRuntime> banks,
                ManagerBlockEntity powerManager,
                List<CraftRequest> requests
        ) {
            this.ae = ae;
            this.banks = banks;
            this.powerManager = powerManager;
            this.requests = requests;
        }

        private AeRuntime ae() {
            return ae;
        }

        private List<BankRuntime> banks() {
            return banks;
        }

        private ManagerBlockEntity powerManager() {
            return powerManager;
        }

        private List<CraftRequest> requests() {
            return requests;
        }
    }
}
