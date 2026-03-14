package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.timing.SFMInstant;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static ca.teamdman.sfm.common.blockentity.ManagerBlockEntity.TICK_TIME_HISTORY_SIZE;
import static ca.teamdman.sfm.common.net.ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH;

/// Use {@link SFMLProgramBuilder} to get a {@link SFMLProgram} from a {@link String}
public record SFMLProgram(
        SfmlAstBuilder astBuilder,

        String name,

        List<Trigger> triggers,

        List<LogExpression> topLevelLogExpressions,

        Set<String> referencedLabels,

        Set<ResourceIdentifier<?, ?, ?>> referencedResources
) implements Tickable {
    /**
     * This comes from {@link java.io.DataOutputStream#writeUTF(String, DataOutput)}
     * and {@link NetworkHooks#openScreen(ServerPlayer, MenuProvider, Consumer)}
     */
    @SuppressWarnings("JavadocReference")
    public static final int MAX_PROGRAM_LENGTH = 32600 // from openScreen
                                                 - 8 * TICK_TIME_HISTORY_SIZE
                                                 - MAX_LOG_LEVEL_NAME_LENGTH
                                                 - 1 // manager state enum
                                                 - 8; // block pos

    public static final int MAX_LABEL_LENGTH = 256;

    /**
     * Create a context and tick the program.
     *
     * @return {@code true} if a trigger entered its body
     */
    public boolean tick(ManagerBlockEntity manager) {

        var context = ProgramContext.of(this, manager, new ExecuteProgramBehaviour());

        // log if there are unprocessed redstone pulses
        int unprocessedRedstonePulseCount = manager.getUnprocessedRedstonePulseCount();
        if (unprocessedRedstonePulseCount > 0) {
            manager.logger.debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_WITH_REDSTONE_COUNT.get(
                    unprocessedRedstonePulseCount)));
        }


        tick(context);

        manager.clearRedstonePulseQueue();

        return context.didSomething().booleanValue();
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        List<SfmlAstNode> children = new java.util.ArrayList<>();
        children.addAll(topLevelLogExpressions);
        children.addAll(triggers);
        return children;
    }

    @Override
    public void tick(ProgramContext context) {

        LimitedInputSlotObjectPool.checkInvariant();
        LimitedOutputSlotObjectPool.checkInvariant();

        for (Trigger trigger : triggers) {
            // Only process triggers that should tick
            if (!trigger.shouldTick(context)) {
                continue;
            }

            // Set flag and log on first trigger
            if (!context.didSomething().booleanValue()) {
                context.didSomething().setTrue();

                context.logger().trace(getTraceLogWriter(context));

                context.logger().debug(debug -> debug.accept(LocalizationKeys.LOG_PROGRAM_TICK.get()));
            }

            // Log pretty triggers
            if (triggers instanceof ToStringCondensed ss) {

                context.logger()
                        .debug(x -> x.accept(LocalizationKeys.LOG_PROGRAM_TICK_TRIGGER_STATEMENT.get(
                                ss.toStringCondensed())));
            }

            // Start stopwatch
            SFMInstant start = SFMInstant.now();

            // Perform tick

            if (context.behaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
                int maxConditionCount = SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.maxIfStatementsInTriggerBeforeSimulationIsntAllowed);
                int conditionCount = trigger.getConditionCount();
                if (conditionCount <= maxConditionCount) {
                    int numPossibleStates = (int) Math.max(1, Math.pow(2, conditionCount));
                    for (int i = 0; i < numPossibleStates; i++) {
                        ProgramContext forkedContext = context.fork();
                        trigger.tick(forkedContext);
                        forkedContext.free();

                        ((SimulateExploreAllPathsProgramBehaviour) forkedContext.behaviour()).terminatePathAndBeginAnew();
                    }
                } else {

                    context.logger().warn(LocalizationKeys.PROGRAM_WARNING_TOO_MANY_CONDITIONS.get(
                            trigger.toString(),
                            conditionCount,
                            maxConditionCount
                    ));
                }
                simulation.prepareNextTrigger();
            } else {
                ProgramContext forkedContext = context.fork();
                trigger.tick(forkedContext);
                forkedContext.free();
            }

            // End stopwatch
            Duration elapsed = start.elapsed();

            // Log trigger time

            context.logger().info(x -> x.accept(LocalizationKeys.PROGRAM_TICK_TRIGGER_TIME_MS.get(
                    elapsed.toMillis(),
                    trigger.toString()
            )));
        }

        LimitedInputSlotObjectPool.checkInvariant();
        LimitedOutputSlotObjectPool.checkInvariant();

        if (context.behaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onProgramFinished(context, this);
        }
    }

    public int getConditionIndex(IfStatement ifStatement) {

        for (Trigger trigger : triggers) {
            int conditionIndex = trigger.getConditionIndex(ifStatement);
            if (conditionIndex != -1) {
                return conditionIndex;
            }
        }
        return -1;
    }

    @Override
    public String toString() {

        var rtn = new StringBuilder();
        rtn.append("NAME \"").append(name).append("\"\n");
        for (LogExpression logExpr : topLevelLogExpressions) {
            rtn.append(logExpr).append("\n");
        }
        for (Trigger trigger : triggers) {
            rtn.append(trigger).append("\n");
        }
        return rtn.toString();
    }

    private static @NotNull Consumer<Consumer<TranslatableContents>> getTraceLogWriter(ProgramContext context) {

        return trace -> {
            trace.accept(LocalizationKeys.LOG_CABLE_NETWORK_DETAILS_HEADER_1.get());
            trace.accept(LocalizationKeys.LOG_CABLE_NETWORK_DETAILS_HEADER_2.get());

            Level level = context.manager()
                    .getLevel();
            //noinspection DataFlowIssue

            context.network()
                    .getCablePositions()
                    .map(pos -> "- "
                                + pos.toString()
                                + " "
                                + level
                                        .getBlockState(
                                                pos))
                    .forEach(body -> trace.accept(LocalizationKeys.LOG_CABLE_NETWORK_DETAILS_BODY.get(
                            body)));
            trace.accept(LocalizationKeys.LOG_CABLE_NETWORK_DETAILS_HEADER_3.get());
            //noinspection DataFlowIssue

            context.network()
                    .getCapabilityProviderPositions()
                    .map(pos -> "- " + pos.toString() + " " + level
                            .getBlockState(pos))
                    .forEach(body -> trace.accept(LocalizationKeys.LOG_CABLE_NETWORK_DETAILS_BODY.get(
                            body)));
            trace.accept(LocalizationKeys.LOG_CABLE_NETWORK_DETAILS_FOOTER.get());

            trace.accept(LocalizationKeys.LOG_LABEL_POSITION_HOLDER_DETAILS_HEADER.get());
            //noinspection DataFlowIssue

            context.labelPositionHolder()
                    .labels()
                    .forEach((label, positions) -> positions
                            .stream()
                            .map(
                                    pos -> "- "
                                           + label
                                           + ": "
                                           + pos.toString()
                                           + " "
                                           + level
                                                   .getBlockState(
                                                           pos)

                            )
                            .forEach(body -> trace.accept(LocalizationKeys.LOG_LABEL_POSITION_HOLDER_DETAILS_BODY.get(
                                    body))));
            trace.accept(LocalizationKeys.LOG_LABEL_POSITION_HOLDER_DETAILS_FOOTER.get());
            trace.accept(LocalizationKeys.LOG_PROGRAM_CONTEXT.get(context.toString()));
        };
    }

    public static class ListErrorListener extends BaseErrorListener {
        private final List<String> errors;

        public ListErrorListener(List<String> errors) {

            this.errors = errors;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e
        ) {

            errors.add("line " + line + ":" + charPositionInLine + " " + msg);
        }

    }

}
