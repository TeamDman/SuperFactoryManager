package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.*;
{% if features.packet_computation %}
{% else %}
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
{% endif %}

{% if features.packet_computation %}
{% else %}
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
{% endif %}
import java.util.Objects;
{% if features.packet_computation %}
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
{% else %}
import java.util.function.Consumer;
import java.util.function.Predicate;
{% endif %}
import java.util.stream.Collectors;

public final class InputStatement implements IOStatement {
    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_MISS = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.cache_miss",
            "Statement cache miss - this is the first time this statement is being gathered"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_HIT = new LocalizationEntry(
            "log.sfm.statement.tick.io.gather_slots.cache_hit",
            "Cache hit - this statement has already gathered slots"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_INPUT_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.input",
            "%s"
    );

    private final LabelAccess labelAccess;

    private final ResourceLimits resourceLimits;

    private final boolean each;

{% if features.packet_computation %}
    private final ProgramInputSelection selection;
{% else %}
    private @Nullable ArrayDeque<LimitedInputSlot<?, ?, ?>> limitedInputSlotsCache = null;
{% endif %}

{% if features.packet_computation %}
    private final String bindingName;

{% else %}
{% endif %}
    public InputStatement(
            LabelAccess labelAccess,
            ResourceLimits resourceLimits,
            boolean each
    ) {

{% if features.packet_computation %}
        this(labelAccess, resourceLimits, each, ProgramInputSelection.ANY, null);
    }

    public InputStatement(
            LabelAccess labelAccess,
            ResourceLimits resourceLimits,
            boolean each,
            ProgramInputSelection selection,
            String bindingName
    ) {

{% else %}
{% endif %}
        this.labelAccess = labelAccess;
        this.resourceLimits = resourceLimits;
        this.each = each;
{% if features.packet_computation %}
        this.selection = Objects.requireNonNull(selection);
        this.bindingName = bindingName;
{% else %}
{% endif %}
    }

    @Override
    public void tick(ProgramContext context) {

{% if features.packet_computation %}
        ProgramInputSource inputSource = new WorldProgramInputSource(this);
        if (selection != ProgramInputSelection.ANY) {
            inputSource = new FilteringProgramInputSource(inputSource, selection);
        }
        context.addInput(inputSource);
{% else %}
        context.addInput(this);
{% endif %}
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_INPUT_STATEMENT.get(toString())));

        // Track simulation
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
{% if features.packet_computation %}
            if (bindingName != null) {
                context.getVariableEnvironment().setRelation(bindingName, ProgramRelation.EMPTY);
{% else %}
            simulation.onInputStatementExecution(context, this);
        }
    }

    @SuppressWarnings({"unchecked"}) // basically impossible to make this method generic safe
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
    ) {

        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS.get(toStringPretty())));

        // do we have a cached result?
        if (limitedInputSlotsCache != null) {
            // log cache hit
            context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_HIT.get()));
            // return cached results
            for (var slot : limitedInputSlotsCache) {
                slotConsumer.accept(slot);
{% endif %}
            }
{% if features.packet_computation %}
            simulation.onInputStatementExecution(context, this);
{% else %}
{% endif %}
            return;
        }

{% if features.packet_computation %}
        if (bindingName != null) {
            List<ProgramRelationRow> rows = new ArrayList<>();
            for (ProgramResourceObservation observation : ProgramResourceObserver.observe(
                    context,
                    List.of(inputSource),
                    (resourceType, stack) -> true
            )) {
                for (long occurrence = 0; occurrence < observation.amount(); occurrence++) {
                    ProgramResourceValue resourceValue = ProgramResourceValue.fromObservation(observation);
                    rows.add(new ProgramRelationRow(
                            ProgramOccurrenceId.create(),
                            selection.bind(resourceValue.resourceType(), resourceValue.stack())
                    ));
                }
{% else %}
        // log cache miss
        context.getLogger().trace(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_CACHE_MISS.get()));

        // prepare the cache state
        limitedInputSlotsCache = new ArrayDeque<>(27);

        // monkey-patch the result acceptor to update the cache before returning results
        {
            var original = slotConsumer;
            slotConsumer = slot -> {
                limitedInputSlotsCache.add(slot);
                original.accept(slot);
            };
        }

        if (!each) {
            // log not each
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_NOT_EACH.get()));

            // create a single matcher to be shared by all capabilities
            List<IInputResourceTracker> inputTrackers = resourceLimits.createInputTrackers();
            for (var resourceType : resourceLimits.getReferencedResourceTypes()) { // TODO: Fix #166
                // log gather for resource type
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                resourceType.displayAsCapabilityClass(),
                                resourceType.displayAsCapabilityClass()
                        )));

                // gather slots for each capability found for positions tagged by a provided label
                Consumer<LimitedInputSlot<?, ?, ?>> finalSlotConsumer = slotConsumer;
                // TODO: fix #166 forEachCapability advances the round robin when it should be shared between resource types
                resourceType.forEachCapability(
                        context, labelAccess, (label, pos, direction, cap) -> gatherSlotsForCap(
                                context,
                                (ResourceType<Object, Object, Object>) resourceType,
                                label, pos, direction, cap,
                                inputTrackers,
                                finalSlotConsumer
                        )
                );
            }
        } else {
            // log yes each
            context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_EACH.get()));

            for (var resourceType : resourceLimits.getReferencedResourceTypes()) {
                // log gather for resource type
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_FOR_RESOURCE_TYPE.get(
                                resourceType.displayAsCapabilityClass(),
                                resourceType.displayAsCapabilityClass()
                        )));

                // gather slots for each capability found for positions tagged by a provided label
                Consumer<LimitedInputSlot<?, ?, ?>> finalSlotConsumer = slotConsumer;
                resourceType.forEachCapability(
                        context, labelAccess, (label, pos, direction, cap) -> {
                            List<IInputResourceTracker> inputTrackers = resourceLimits.createInputTrackers();
                            gatherSlotsForCap(
                                    context,
                                    (ResourceType<Object, Object, Object>) resourceType,
                                    label, pos, direction, cap,
                                    inputTrackers,
                                    finalSlotConsumer
                            );
                        }
                );
{% endif %}
            }
{% if features.packet_computation %}
            context.getVariableEnvironment().setRelation(bindingName, new ProgramRelation(rows));
{% else %}
{% endif %}
        }
    }

    @Override
    public String toString() {

        StringBuilder rtn = new StringBuilder();
        rtn.append("INPUT ");
{% if features.packet_computation %}
        if (selection != ProgramInputSelection.ANY) rtn.append(selection.toSource()).append(" ");
{% else %}
{% endif %}
        String limits = resourceLimits.toStringCondensed(Limit.MAX_QUANTITY_NO_RETENTION);
        if (!limits.isEmpty()) rtn.append(limits).append(" ");
        rtn.append("FROM ");
        if (each) rtn.append("EACH ");
        rtn.append(labelAccess);
{% if features.packet_computation %}
        if (bindingName != null) rtn.append(" AS ").append(bindingName);
{% else %}
{% endif %}
        return rtn.toString();
    }

    @Override
    public String toStringPretty() {

        StringBuilder sb = new StringBuilder();
        sb.append("INPUT");
{% if features.packet_computation %}
        if (selection != ProgramInputSelection.ANY) sb.append(" ").append(selection.toSource());
{% else %}
{% endif %}
        String rls = resourceLimits.toStringCondensed(Limit.MAX_QUANTITY_NO_RETENTION);
        if (rls.lines().count() > 1) {
            sb.append("\n");
            sb.append(rls.lines().map(s -> "  " + s).collect(Collectors.joining("\n")));
            sb.append("\n");
        } else if (!rls.isEmpty()) {
            sb.append(" ");
            sb.append(rls);
            sb.append(" ");
        } else {
            sb.append(" ");
        }
        sb.append("FROM ");
        sb.append(each ? "EACH " : "");
        sb.append(labelAccess);
{% if features.packet_computation %}
        if (bindingName != null) sb.append(" AS ").append(bindingName);
{% else %}
{% endif %}
        return sb.toString();
    }

    @Override
    public LabelAccess labelAccess() {

        return labelAccess;
    }

    @Override
    public ResourceLimits resourceLimits() {

        return resourceLimits;
    }

    @Override
    public boolean each() {

        return each;
    }

{% if features.packet_computation %}
    public ProgramInputSelection selection() {
        return selection;
    }

    public Optional<String> bindingName() {
        return Optional.ofNullable(bindingName);
    }

{% else %}
{% endif %}
    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (InputStatement) obj;
        return Objects.equals(this.labelAccess, that.labelAccess) && Objects.equals(
                this.resourceLimits,
                that.resourceLimits
{% if features.packet_computation %}
        ) && this.each == that.each
          && Objects.equals(this.selection, that.selection)
          && Objects.equals(this.bindingName, that.bindingName);
{% else %}
        ) && this.each == that.each;
{% endif %}
    }

    @Override
    public int hashCode() {

{% if features.packet_computation %}
        return Objects.hash(labelAccess, resourceLimits, each, selection, bindingName);
{% else %}
        return Objects.hash(labelAccess, resourceLimits, each);
    }

    /**
     * Release the slots acquired by this statement
     * </p>
     * This was separated from {@link OutputStatement#tick(ProgramContext)} because we need input statements
     * to keep their counts when used by multiple output statements.
     */
    public void freeSlots() {

        if (limitedInputSlotsCache != null) {
            LimitedInputSlotObjectPool.release(limitedInputSlotsCache);
            limitedInputSlotsCache = null;
        }
    }

    public void freeSlotsIf(Predicate<LimitedInputSlot<?, ?, ?>> condition) {

        if (limitedInputSlotsCache != null) {
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
    }

    public void transferSlotsTo(InputStatement other) {

        if (limitedInputSlotsCache != null) {
            if (other.limitedInputSlotsCache == null) {
                other.limitedInputSlotsCache = new ArrayDeque<>();
            }
            other.limitedInputSlotsCache.addAll(limitedInputSlotsCache);
        }
        limitedInputSlotsCache = null;
    }

    private <STACK, ITEM, CAP> void gatherSlotsForCap(
            ProgramContext context,
            ResourceType<STACK, ITEM, CAP> type,
            Label label,
            BlockPos pos,
            Direction direction,
            CAP capability,
            List<IInputResourceTracker> trackers,
            Consumer<LimitedInputSlot<?, ?, ?>> acceptor
    ) {

        context
                .getLogger()
                .debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_RANGE.get(
                        labelAccess.slots())));
        for (int slot = 0; slot < type.getSlots(capability); slot++) {
            int finalSlot = slot;
            if (labelAccess.slots().contains(slot)) {
                STACK stack = type.getStackInSlot(capability, slot);
                if (shouldCreateSlot(type, stack)) {
                    for (IInputResourceTracker tracker : trackers) {
                        if (tracker.matchesCapabilityType(capability) && tracker.matchesStack(stack)) {
                            context
                                    .getLogger()
                                    .debug(x -> x.accept(IOStatement.LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_CREATED.get(
                                            finalSlot,
                                            stack,
                                            tracker.toString()
                                    )));
                            acceptor.accept(LimitedInputSlotObjectPool.acquire(
                                    label, pos, direction, slot, capability,
                                    tracker,
                                    stack,
                                    type
                            ));
                        }
                    }
                } else {
                    context
                            .getLogger()
                            .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_SHOULD_NOT_CREATE.get(
                                    finalSlot,
                                    stack
                            )));
                }
            } else {
                context
                        .getLogger()
                        .debug(x -> x.accept(LOG_PROGRAM_TICK_IO_STATEMENT_GATHER_SLOTS_SLOT_NOT_IN_RANGE.get(finalSlot)));
            }
        }
    }

    private <STACK, ITEM, CAP> boolean shouldCreateSlot(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        // make sure there are items to move
        return !type.isEmpty(stack);
{% endif %}
    }

}
