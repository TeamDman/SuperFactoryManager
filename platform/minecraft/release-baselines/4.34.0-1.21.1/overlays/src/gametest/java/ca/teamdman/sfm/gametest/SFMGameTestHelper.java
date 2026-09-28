package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentEntry;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentKey;
import ca.teamdman.sfm.common.facade.FacadeData;
import ca.teamdman.sfm.common.facade.FacadeTextureMode;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class SFMGameTestHelper extends GameTestHelper {
    private static final long MAX_PROGRAM_RUN_MILLIS = Long.getLong(
            "sfm.gametest.maxProgramRunMillis",
            80L
    );

    public SFMGameTestHelper(
            GameTestHelper helper
    ) {

        super(helper.testInfo);
    }

    public void assertTrue(
            boolean condition,
            String message
    ) {

        if (!condition) {
            @SuppressWarnings("UnnecessaryLocalVariable")
            var toThrow = new GameTestAssertException(message);
            // Uncomment below for detailed location information
            // Note that the tests fail every tick using this until they succeed, so you will see logs that make things look like tests are failing if this is uncommented
//            SFM.LOGGER.error("Assertion failed: {}", message, toThrow);
            throw toThrow;
        }
    }

    @MCVersionDependentBehaviour
    public DamageSource getFellOutOfWorldDamageSource() {
        return getLevel().damageSources().fellOutOfWorld();
    }

    public Program compile(
            String code
    ) {

        AtomicReference<Program> rtn = new AtomicReference<>();

        new ProgramBuilder(code)
                .useCache(false)
                .build()
                .caseSuccess((program, metadata) -> rtn.set(program))
                .caseFailure(result -> {
                    throw new GameTestAssertException("Failed to compile program: " + result.metadata().errors()
                            .stream()
                            .map(Object::toString)
                            .reduce("", (a, b) -> a + "\n" + b));
                });
        return rtn.get();
    }

    public void assertManagerRunning(
            ManagerBlockEntity manager
    ) {

        this.assertTrue(manager.getDisk() != null, "No disk in manager");
        this.assertTrue(
                manager.getState() == ManagerBlockEntity.State.RUNNING,
                "Program did not start running " + DiskItem.getErrors(manager.getDisk())
        );
    }

    @MCVersionDependentBehaviour
    public SFMEnchantmentEntry createEnchantmentEntry(
            ResourceKey<Enchantment> id,
            int enchantmentLevel
    ) {

        return new SFMEnchantmentEntry(
                new SFMEnchantmentKey(getLevel().registryAccess(), id),
                enchantmentLevel
        );
    }

    @MCVersionDependentBehaviour
    public @NotNull SFMEnchantmentKey createEnchantmentKey(ResourceKey<Enchantment> enchantment) {
        return new SFMEnchantmentKey(getLevel().registryAccess(), enchantment);
    }

    @Override
    @MCVersionDependentBehaviour
    public <E extends Entity> E spawn(
            EntityType<E> type,
            BlockPos pos
    ) {

        return spawn(type, Vec3.atBottomCenterOf(pos));
    }

    @Override
    @MCVersionDependentBehaviour
    public <E extends Entity> E spawn(
            EntityType<E> type,
            Vec3 pos
    ) {

        ServerLevel level = getLevel();
        E entity = type.create(level);
        if (entity == null) {
            fail("Failed to spawn entity " + type);
            throw new IllegalStateException("Unreachable");
        }

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        Vec3 absoluteVec = absoluteVec(pos);
        entity.moveTo(absoluteVec.x, absoluteVec.y, absoluteVec.z, entity.getYRot(), entity.getXRot());

        if (entity instanceof Mob mob) {
            finalizeSummonedMobSpawn(mob, level);
        }

        level.addFreshEntity(entity);
        return entity;
    }

    @MCVersionDependentBehaviour
    private static @Nullable SpawnGroupData finalizeSummonedMobSpawn(
            Mob mob,
            ServerLevel level
    ) {

        return mob.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.MOB_SUMMONED,
                null
        );
    }

    public <CAP> CAP discoverCapability(
            SFMBlockCapabilityKind<CAP> capKind,
            BlockPos localPos,
            @Nullable Direction direction
    ) {

        SFMBlockCapabilityResult<CAP> found = SFMBlockCapabilityDiscovery.discoverCapabilityFromLevel(
                getLevel(),
                capKind,
                absolutePos(localPos),
                direction
        );
        this.assertTrue(found.isPresent(), "No " + capKind.getName() + " found at " + localPos);
        return found.unwrap();
    }

    public IFluidHandler getFluidHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return discoverCapability(
                SFMWellKnownCapabilities.FLUID_HANDLER,
                pos,
                direction
        );
    }

    public IItemHandler getItemHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return discoverCapability(
                SFMWellKnownCapabilities.ITEM_HANDLER,
                pos,
                direction
        );
    }

    @MCVersionDependentBehaviour
    public <T extends BlockEntity> T getBlockEntity(
            BlockPos pos,
            Class<T> type
    ) {

        BlockEntity blockEntity = getBlockEntity(pos);
        if (type.isInstance(blockEntity)) {
            return type.cast(blockEntity);
        }

        fail("Block entity was not an instance of " + type.getSimpleName() + ", got " + blockEntity, pos);
        throw new IllegalStateException("Unreachable");
    }

    public void setSignText(
            BlockPos signPos,
            Component... text
    ) {

        SignBlockEntity signBlockEntity = getBlockEntity(signPos, SignBlockEntity.class);
        if (text.length > 4) {
            fail("Text array was too long, max length is 4, got " + text.length, signPos);
            return;
        }
        var newText = signBlockEntity.getFrontText();
        for (int i = 0; i < text.length; i++) {
            newText = newText.setMessage(i, text[i]);
        }
        signBlockEntity.setText(newText, false);
        signBlockEntity.setText(newText, true);
    }

    public IEnergyStorage getEnergyStorage(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return discoverCapability(
                SFMWellKnownCapabilities.ENERGY,
                pos,
                direction
        );
    }

    public IItemHandler getItemHandler(
            BlockPos pos
    ) {

        return getItemHandler(pos, null);
    }

    public void succeedIfManagerDidThingWithoutLagging(
            ManagerBlockEntity manager,
            Runnable assertion
    ) {

        this.assertManagerRunning(manager);
        manager.addProgramHooks(new IProgramHooks() {
            @Override
            public void onProgramDidSomething(Duration elapsed) {
                // enqueue to run inside the game test harness
                SFMGameTestHelper.this.runAfterDelay(
                        0,
                        () -> {
                            assertion.run();
                            SFMGameTestHelper.this.assertTrue(
                                    elapsed.toMillis() < MAX_PROGRAM_RUN_MILLIS,
                                    "Program took too long to run: took " + NumberFormat
                                            .getInstance(Locale.getDefault())
                                            .format(elapsed.toNanos()) + "ns, max "
                                    + MAX_PROGRAM_RUN_MILLIS + "ms"
                            );
                            SFMGameTestHelper.this.succeed();
                        }
                );
            }
        });
    }

    /// Asserts an expression using labels from the disk inside a manager.
    /// Note that this should not be used in tests responsible for validating the correctness of the capability cache.
    public void assertExpr(
            ManagerBlockEntity manager,
            String exprString
    ) {

        BoolExpr expr = BoolExpr.from(exprString);
        ProgramContext programContext = new ProgramContext(
                new Program(new ASTBuilder(), "temp lol", List.of(), Set.of(), Set.of()),
                manager,
                ExecuteProgramBehaviour::new
        );
        boolean passed = expr.test(programContext);
        if (!passed) {
            List<BlockPos> positions = new ArrayList<>();
            expr.collectPositions(programContext, positions::add);
            positions.add(manager.getBlockPos());
            BlockPos failurePos = positions.get(0);
            throw new GameTestAssertPosException(
                    "Condition failed: " + exprString,
                    failurePos,
                    relativePos(failurePos),
                    this.getTick()
            );
        }
    }

    @Override
    public BlockPos relativePos(BlockPos pPos) {

        BlockPos blockpos = this.testInfo.getStructureBlockPos();
        Rotation rotation = this.testInfo.getRotation(); //.getRotated(Rotation.CLOCKWISE_180); // causes problems idk
        BlockPos blockpos1 = StructureTemplate.transform(pPos, Mirror.NONE, rotation, blockpos);
        return blockpos1.subtract(blockpos);
    }

    public void setFacade(
            BlockPos localBlockPos,
            BlockState mimicBlockState
    ) {

        if (!(getBlockEntity(localBlockPos, BlockEntity.class) instanceof IFacadeBlockEntity facadeBlockEntity)) {
            fail("Block entity was not a facade", localBlockPos);
            return;
        }

        facadeBlockEntity.updateFacadeData(new FacadeData(
                mimicBlockState,
                Direction.UP,
                FacadeTextureMode.FILL
        ));
    }

    public int count(
            Container inventory,
            @Nullable ItemLike item
    ) {

        return IntStream.range(0, inventory.getContainerSize())
                .mapToObj(inventory::getItem)
                .filter(stack -> item == null || stack.getItem() == item.asItem())
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public int count(
            IItemHandler inventory,
            @Nullable ItemLike item
    ) {

        return IntStream.range(0, inventory.getSlots())
                .mapToObj(inventory::getStackInSlot)
                .filter(stack -> item == null || stack.getItem() == item.asItem())
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int count(
            SFMGameTestHelper helper,
            Container inventory,
            ItemStack comparisonStack
    ) {

        return IntStream.range(0, inventory.getContainerSize())
                .mapToObj(inventory::getItem)
                .filter(stack -> SFMItemUtils.isSameItemSameTags(stack, comparisonStack))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public int count(
            IItemHandler inventory,
            ItemStack comparisonStack
    ) {

        return IntStream.range(0, inventory.getSlots())
                .mapToObj(inventory::getStackInSlot)
                .filter(stack -> SFMItemUtils.isSameItemSameTags(stack, comparisonStack))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public static int count(
            SFMGameTestHelper helper,
            Container inventory
    ) {

        return helper.count(inventory, (ItemLike) null);
    }

    public static int count(
            SFMGameTestHelper helper,
            IItemHandler inventory
    ) {

        return helper.count(inventory, (ItemLike) null);
    }

    public void assertCount(
            IItemHandler inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, item);
        assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public void assertCount(
            Container inventory,
            @Nullable ItemLike item,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, item);
        assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public void assertCount(
            Container inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message
    ) {

        int actualCount = count(this, inventory, comparisonStack);
        assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public void assertCount(
            IItemHandler inventory,
            ItemStack comparisonStack,
            int expectedCount,
            String message
    ) {

        int actualCount = count(inventory, comparisonStack);
        assertTrue(
                actualCount == expectedCount,
                message + ": expected " + expectedCount + " but got " + actualCount
        );
    }

    public void assertCount(
            IItemHandler inventory,
            int expectedCount,
            String message
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message);
    }

    public void assertCount(
            Container inventory,
            int expectedCount,
            String message
    ) {

        assertCount(inventory, (ItemLike) null, expectedCount, message);
    }

    public void assertCount(
            AtomicReference<?> ref,
            int expectedCount,
            String message
    ) {

        var inventory = ref.get();
        if (inventory instanceof Container container) {
            assertCount(container, expectedCount, message);
        } else if (inventory instanceof IItemHandler itemHandler) {
            assertCount(itemHandler, expectedCount, message);
        } else {
            throw new IllegalArgumentException("Expected either a Container or IItemHandler but got "
                                               + inventory.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends TileEntityMekanism> T getAndPrepMekTile(BlockPos mekanismPos) {
        var tile = getBlockEntity(mekanismPos);
        if (tile instanceof TileEntityConfigurableMachine mek) {
            set_all_io(mek.getConfig());
            return (T) mek;
//        } else if (tile instanceof TileEntityBin bin) {
        }
        return (T) tile;
    }

    public static void set_all_io(TileComponentConfig config) {
        for (TransmissionType type : TransmissionType.values()) {
            ConfigInfo info = config.getConfig(type);
            if (info != null) {
                for (RelativeSide side : RelativeSide.values()) {
                    info.setDataType(DataType.INPUT_OUTPUT, side);
                    config.sideChanged(type, side);
                }
            }
        }
    }

}
