package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.program.RegexCache;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class SFMGameTestCommand {
    private SFMGameTestCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("test")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("run")
                        .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    var source = ctx.getSource();
                                    var wildcardPattern = StringArgumentType.getString(ctx, "pattern");
                                    return runTestsByWildcard(source, wildcardPattern);
                                })));
    }

    private static int runTestsByWildcard(CommandSourceStack source, String wildcardPattern) {
        var matcher = RegexCache.buildPredicate(wildcardToRegex(wildcardPattern));
        List<TestFunction> matchingTests = GameTestRegistry
                .getAllTestFunctions()
                .stream()
                .filter(testFunction -> matcher.test(testFunction.getTestName()))
                .toList();

        if (matchingTests.isEmpty()) {
            SFMCommandUtils.sendSuccess(source, () -> Component.literal("No tests matched pattern: " + wildcardPattern));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos sourcePos = new BlockPos(source.getPosition());
        int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, sourcePos).getY();
        BlockPos startPos = new BlockPos(sourcePos.getX(), surfaceY, sourcePos.getZ() + 3);

        GameTestRunner.clearMarkers(level);
        runTests(matchingTests, startPos, level);

        SFMCommandUtils.sendSuccess(
                source,
                () -> Component.literal("Running " + matchingTests.size() + " tests matching '" + wildcardPattern + "'")
        );
        return SINGLE_SUCCESS;
    }

    @MCVersionDependentBehaviour
    private static void runTests(List<TestFunction> matchingTests, BlockPos startPos, ServerLevel level) {
        GameTestRunner.runTests(
                matchingTests,
                startPos,
                Rotation.NONE,
                level,
                GameTestTicker.SINGLETON,
                8
        );
    }

    private static String wildcardToRegex(String wildcardPattern) {
        return wildcardPattern.replace("*", ".*");
    }
}