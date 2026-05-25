package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.concurrent.atomic.AtomicReference;

public class SFMGameTestMethodHelpers {

    public static void assertTrue(
            boolean condition,
            String message
    ) {

        if (!condition) {
            @SuppressWarnings("UnnecessaryLocalVariable")
            var toThrow = new GameTestAssertException(Component.literal(message), 0);
            // Uncomment below for detailed location information
            // Note that the tests fail every tick using this until they succeed, so you will see logs that make things look like tests are failing if this is uncommented
//            SFM.LOGGER.error("Assertion failed: {}", message, toThrow);
            throw toThrow;
        }
    }

    public static Program compile(String code) {

        AtomicReference<Program> rtn = new AtomicReference<>();

        new ProgramBuilder(code)
                .useCache(false)
                .build()
                .caseSuccess((program, metadata) -> rtn.set(program))
                .caseFailure(result -> {
                    throw new GameTestAssertException(Component.literal("Failed to compile program: " + result.metadata().errors()
                            .stream()
                            .map(Object::toString)
                            .reduce("", (a, b) -> a + "\n" + b)), 0);
                });
        return rtn.get();
    }

    public static void assertManagerRunning(ManagerBlockEntity manager) {

        SFMGameTestMethodHelpers.assertTrue(manager.getDisk() != null, "No disk in manager");
        SFMGameTestMethodHelpers.assertTrue(
                manager.getState() == ManagerBlockEntity.State.RUNNING,
                "Program did not start running " + DiskItem.getErrors(manager.getDisk())
        );
    }

    public static ResourceHandler<ItemResource> getItemHandler(
            GameTestHelper helper,
            BlockPos pos
    ) {
        BlockPos worldPos = helper.absolutePos(pos);
        var found = helper
                .getLevel()
                .getCapability(Capabilities.Item.BLOCK, worldPos, Direction.DOWN);
        SFMGameTestMethodHelpers.assertTrue(found != null, "No item handler found at " + worldPos);
        return found;
    }

    @SuppressWarnings("unchecked")
    public static <T extends TileEntityMekanism> T getAndPrepMekTile(GameTestHelper helper, BlockPos mekanismPos) {
        var tile = helper.getBlockEntity(mekanismPos, TileEntityConfigurableMachine.class);
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
