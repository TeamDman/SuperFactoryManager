package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentEntry;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentKey;
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.ast.SfmlAstBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SFMGameTestHelper extends GameTestHelper {

    public SFMGameTestHelper(
            GameTestHelper helper
    ) {

        super(helper.testInfo);
    }

    @MCVersionDependentBehaviour
    public SFMEnchantmentEntry createEnchantmentEntry(
            Enchantment enchantment,
            int enchantmentLevel
    ) {

        return new SFMEnchantmentEntry(
                new SFMEnchantmentKey(enchantment),
                enchantmentLevel
        );
    }

    @MCVersionDependentBehaviour
    public @NotNull SFMEnchantmentKey createEnchantmentKey(Enchantment enchantment) {
        return new SFMEnchantmentKey(enchantment);
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
        SFMGameTestMethodHelpers.assertTrue(found.isPresent(), "No " + capKind.getName() + " found at " + localPos);
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

    public void setSignText(
            BlockPos signPos,
            Component... text
    ) {

        BlockEntity blockEntity = getBlockEntity(signPos);
        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            fail("Block entity was not an instance of SignBlockEntity, got " + blockEntity, signPos);
            return;
        }
        if (text.length > 4) {
            fail("Text array was too long, max length is 4, got " + text.length, signPos);
            return;
        }
        for (int i = 0; i < text.length; i++) {
            signBlockEntity.setMessage(i, text[i]);
        }
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

        SFMGameTestMethodHelpers.assertManagerRunning(manager);
        manager.addProgramHooks(new IProgramHooks() {
            @Override
            public void onProgramDidSomething(Duration elapsed) {
                // enqueue to run inside the game test harness
                SFMGameTestHelper.this.runAfterDelay(
                        0,
                        () -> {
                            assertion.run();
                            SFMGameTestMethodHelpers.assertTrue(
                                    elapsed.toMillis() < 80,
                                    "Program took too long to run: took " + NumberFormat
                                            .getInstance(Locale.getDefault())
                                            .format(elapsed.toNanos()) + "ns"
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
        ProgramContext programContext = ProgramContext.of(
                new SFMLProgram(new SfmlAstBuilder(), "temp lol", List.of(), List.of(), Set.of(), Set.of()),
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

}
