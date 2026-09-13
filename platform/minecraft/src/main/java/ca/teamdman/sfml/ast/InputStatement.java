package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.*;

import java.util.Objects;
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

    public InputStatement(
            LabelAccess labelAccess,
            ResourceLimits resourceLimits,
            boolean each
    ) {

        this.labelAccess = labelAccess;
        this.resourceLimits = resourceLimits;
        this.each = each;
    }

    @Override
    public void tick(ProgramContext context) {

        context.addInput(new WorldProgramInputSource(this));
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_INPUT_STATEMENT.get(toString())));

        // Track simulation
        if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
            simulation.onInputStatementExecution(context, this);
        }
    }

    @Override
    public String toString() {

        StringBuilder rtn = new StringBuilder();
        rtn.append("INPUT ");
        String limits = resourceLimits.toStringCondensed(Limit.MAX_QUANTITY_NO_RETENTION);
        if (!limits.isEmpty()) rtn.append(limits).append(" ");
        rtn.append("FROM ");
        if (each) rtn.append("EACH ");
        rtn.append(labelAccess);
        return rtn.toString();
    }

    @Override
    public String toStringPretty() {

        StringBuilder sb = new StringBuilder();
        sb.append("INPUT");
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

    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (InputStatement) obj;
        return Objects.equals(this.labelAccess, that.labelAccess) && Objects.equals(
                this.resourceLimits,
                that.resourceLimits
        ) && this.each == that.each;
    }

    @Override
    public int hashCode() {

        return Objects.hash(labelAccess, resourceLimits, each);
    }

}
