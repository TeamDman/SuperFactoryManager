package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.blockentity.CommonFacadeBlockEntity;
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
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.IProgramHooks;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfml.ast.ASTBuilder;
import ca.teamdman.sfml.ast.BoolExpr;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
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

    public ResourceHandler<FluidResource> getFluidResourceHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return discoverCapability(
                SFMWellKnownCapabilities.FLUID_HANDLER,
                pos,
                direction
        );
    }

    public IFluidHandler getFluidHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return IFluidHandler.of(getFluidResourceHandler(pos, direction));
    }

    public ResourceHandler<ItemResource> getItemResourceHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return discoverCapability(
                SFMWellKnownCapabilities.ITEM_HANDLER,
                pos,
                direction
        );
    }

    public IItemHandler getItemHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return IItemHandler.of(getItemResourceHandler(pos, direction));
    }

    public void setSignText(
            BlockPos signPos,
            Component... text
    ) {

        SignBlockEntity signBlockEntity = getBlockEntity(signPos, SignBlockEntity.class);
/*        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            fail("Block entity was not an instance of SignBlockEntity, got " + blockEntity, signPos);
            return;
        }*/
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

    public EnergyHandler getEnergyResourceHandler(
            BlockPos pos,
            @Nullable Direction direction
    ) {

        return discoverCapability(
                SFMWellKnownCapabilities.ENERGY,
                pos,
                direction
        );
    }

    public IEnergyStorage getEnergyStorage(
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return IEnergyStorage.of(getEnergyResourceHandler(pos, direction));
    }

    public ResourceHandler<ItemResource> getItemResourceHandler(
            BlockPos pos
    ) {

        return getItemResourceHandler(pos, null);
    }

    public IItemHandler getItemHandler(BlockPos pos) {
        return IItemHandler.of(getItemResourceHandler(pos));
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
                    Component.literal("Condition failed: " + exprString),
                    failurePos,
                    relativePos(failurePos),
                    ((int) this.getTick())
            );
        }
    }

    @Override
    public BlockPos relativePos(BlockPos pPos) {

        BlockPos blockpos = this.testInfo.getTestOrigin();
        Rotation rotation = this.testInfo.getRotation(); //.getRotated(Rotation.CLOCKWISE_180); // causes problems idk
        BlockPos blockpos1 = StructureTemplate.transform(pPos, Mirror.NONE, rotation, blockpos);
        return blockpos1.subtract(blockpos);
    }

    public void setFacade(
            BlockPos localBlockPos,
            BlockState mimicBlockState
    ) {

        if (!(getBlockEntity(localBlockPos, CommonFacadeBlockEntity.class) instanceof IFacadeBlockEntity facadeBlockEntity)) {
            fail("Block entity was not a facade", localBlockPos);
            return;
        }

        facadeBlockEntity.updateFacadeData(new FacadeData(
                mimicBlockState,
                Direction.UP,
                FacadeTextureMode.FILL
        ));
    }
}
