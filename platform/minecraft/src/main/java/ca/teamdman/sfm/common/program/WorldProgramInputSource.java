package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.IOStatement;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelAccess;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Execution-local slot/cache state for one labelled world input statement. */
public final class WorldProgramInputSource implements ProgramInputSource {
    private final InputStatement statement;
    private @Nullable ArrayDeque<LimitedInputSlot<?, ?, ?>> limitedInputSlotsCache;

    public WorldProgramInputSource(InputStatement statement) {
        this.statement = statement;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
    ) {
        context.getLogger().debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS.get(
                statement.toStringPretty())));

        if (limitedInputSlotsCache != null) {
            context.getLogger().trace(x -> x.accept(InputStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_HIT.get()));
            limitedInputSlotsCache.forEach(slotConsumer);
            return;
        }

        context.getLogger().trace(x -> x.accept(InputStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_MISS.get()));
        limitedInputSlotsCache = new ArrayDeque<>(27);
        Consumer<LimitedInputSlot<?, ?, ?>> cachingConsumer = slot -> {
            limitedInputSlotsCache.add(slot);
            slotConsumer.accept(slot);
        };

        if (!statement.each()) {
            context.getLogger().debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH.get()));
            List<IInputResourceTracker> inputTrackers = statement.resourceLimits().createInputTrackers();
            for (var resourceType : statement.resourceLimits().getReferencedResourceTypes()) {
                logResourceType(context, resourceType);
                resourceType.forEachCapability(
                        context,
                        statement.labelAccess(),
                        (label, pos, direction, cap) -> gatherSlotsForCapability(
                                context,
                                (ResourceType<Object, Object, Object>) resourceType,
                                label,
                                pos,
                                direction,
                                cap,
                                inputTrackers,
                                cachingConsumer
                        )
                );
            }
            return;
        }

        context.getLogger().debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_EACH.get()));
        for (var resourceType : statement.resourceLimits().getReferencedResourceTypes()) {
            logResourceType(context, resourceType);
            resourceType.forEachCapability(
                    context,
                    statement.labelAccess(),
                    (label, pos, direction, cap) -> gatherSlotsForCapability(
                            context,
                            (ResourceType<Object, Object, Object>) resourceType,
                            label,
                            pos,
                            direction,
                            cap,
                            statement.resourceLimits().createInputTrackers(),
                            cachingConsumer
                    )
            );
        }
    }

    @Override
    public @Nullable ProgramInputSource forget(
            ProgramContext context,
            ProgramInputForgetRequest request
    ) {
        Set<Label> labels = request.allInputs()
                            ? Set.copyOf(statement.labelAccess().labels())
                            : request.labels();
        var retainedLabels = statement.labelAccess().labels().stream()
                .filter(label -> !labels.contains(label))
                .toList();
        InputStatement retainedStatement = new InputStatement(
                new LabelAccess(
                        retainedLabels,
                        statement.labelAccess().sides(),
                        statement.labelAccess().slots(),
                        statement.labelAccess().roundRobin()
                ),
                statement.resourceLimits(),
                statement.each(),
                statement.selection(),
                statement.bindingName().orElse(null)
        );
        if (!(context.getBehaviour() instanceof ExecuteProgramBehaviour)) {
            context.getProgram().astBuilder().setLocationFromOtherNode(retainedStatement, statement);
        }
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onInputStatementForgetTransform(context, statement, retainedStatement);
        }

        freeSlotsIf(slot -> labels.contains(slot.label));
        WorldProgramInputSource retainedSource = new WorldProgramInputSource(retainedStatement);
        transferSlotsTo(retainedSource);
        if (retainedLabels.isEmpty()) {
            retainedSource.free();
            return null;
        }
        return retainedSource;
    }

    @Override
    public Optional<InputStatement> inputStatement() {
        return Optional.of(statement);
    }

    @Override
    public void free() {
        if (limitedInputSlotsCache != null) {
            LimitedInputSlotObjectPool.release(limitedInputSlotsCache);
            limitedInputSlotsCache = null;
        }
    }

    private void freeSlotsIf(Predicate<LimitedInputSlot<?, ?, ?>> condition) {
        if (limitedInputSlotsCache == null) {
            return;
        }
        Iterator<LimitedInputSlot<?, ?, ?>> iterator = limitedInputSlotsCache.iterator();
        while (iterator.hasNext()) {
            LimitedInputSlot<?, ?, ?> slot = iterator.next();
            if (condition.test(slot)) {
                iterator.remove();
                LimitedInputSlotObjectPool.release(slot);
            }
        }
        if (limitedInputSlotsCache.isEmpty()) {
            limitedInputSlotsCache = null;
        }
    }

    private void transferSlotsTo(WorldProgramInputSource other) {
        if (limitedInputSlotsCache != null) {
            other.limitedInputSlotsCache = new ArrayDeque<>(limitedInputSlotsCache);
        }
        limitedInputSlotsCache = null;
    }

    private void logResourceType(
            ProgramContext context,
            ResourceType<?, ?, ?> resourceType
    ) {
        context.getLogger().debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                resourceType.displayAsCapabilityClass(),
                resourceType.displayAsCapabilityClass()
        )));
    }

    private <STACK, ITEM, CAP> void gatherSlotsForCapability(
            ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type,
            Label label,
            net.minecraft.core.BlockPos pos,
            net.minecraft.core.Direction direction,
            CAP capability,
            List<IInputResourceTracker> trackers,
            Consumer<LimitedInputSlot<?, ?, ?>> acceptor
    ) {
        context.getLogger().debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_RANGE.get(
                statement.labelAccess().slots())));
        for (int slot = 0; slot < type.getSlots(capability); slot++) {
            int finalSlot = slot;
            if (!statement.labelAccess().slots().contains(slot)) {
                context.getLogger().debug(x -> x.accept(
                        IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_NOT_IN_RANGE.get(finalSlot)));
                continue;
            }
            STACK stack = type.getStackInSlot(capability, slot);
            if (type.isEmpty(stack)) {
                context.getLogger().debug(x -> x.accept(
                        IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE.get(
                                finalSlot,
                                stack
                        )));
                continue;
            }
            for (IInputResourceTracker tracker : trackers) {
                if (tracker.matchesCapabilityType(capability) && tracker.matchesStack(stack)) {
                    context.getLogger().debug(x -> x.accept(
                            IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_CREATED.get(
                                    finalSlot,
                                    stack,
                                    tracker.toString()
                            )));
                    acceptor.accept(LimitedInputSlotObjectPool.acquire(
                            label,
                            pos,
                            direction,
                            slot,
                            capability,
                            tracker,
                            stack,
                            type
                    ));
                }
            }
        }
    }

}
