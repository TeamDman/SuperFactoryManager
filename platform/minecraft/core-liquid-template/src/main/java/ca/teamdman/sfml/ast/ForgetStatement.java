package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
{% if features.packet_computation %}
{% else %}
import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
{% endif %}
import ca.teamdman.sfm.common.program.ProgramContext;
{% if features.packet_computation %}
import ca.teamdman.sfm.common.program.ProgramInputForgetRequest;
import ca.teamdman.sfm.common.program.ProgramInputSource;
{% else %}
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
{% endif %}

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ForgetStatement(
{% if features.packet_computation %}
        Set<Label> labelToForget,
        boolean allInputs
{% else %}
        Set<Label> labelToForget
{% endif %}
) implements Statement {
{% if features.packet_computation %}
    public ForgetStatement(Set<Label> labelToForget) {
        this(Set.copyOf(labelToForget), false);
    }

    public ForgetStatement {
        labelToForget = Set.copyOf(labelToForget);
        if (allInputs && !labelToForget.isEmpty()) {
            throw new IllegalArgumentException("Bare FORGET cannot also name labels");
        }
    }

    public static ForgetStatement allInputsStatement() {
        return new ForgetStatement(Set.of(), true);
    }

{% else %}
{% endif %}
    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_FORGET_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.forget",
            "FORGET %s"
    );

    @Override
    public void tick(ProgramContext context) {

{% if features.packet_computation %}
        ProgramInputForgetRequest request = allInputs
                                            ? ProgramInputForgetRequest.all()
                                            : ProgramInputForgetRequest.labels(labelToForget);
        List<ProgramInputSource> newInputs = new ArrayList<>();
        for (ProgramInputSource inputSource : context.getInputs()) {
            ProgramInputSource retainedSource = inputSource.forget(context, request);
            if (retainedSource != null) {
                newInputs.add(retainedSource);
{% else %}
        List<InputStatement> newInputs = new ArrayList<>();
        for (InputStatement oldInputStatement : context.getInputs()) {
            var newLabels = oldInputStatement.labelAccess().labels().stream()
                    .filter(label -> !this.labelToForget.contains(label))
                    .toList();

            // always fire event from old to new, even if new has no labels
            InputStatement newInputStatement = new InputStatement(
                    new LabelAccess(
                            newLabels,
                            oldInputStatement.labelAccess().sides(),
                            oldInputStatement.labelAccess().slots(),
                            oldInputStatement.labelAccess().roundRobin()
                    ),
                    oldInputStatement.resourceLimits(),
                    oldInputStatement.each()
            );
            if (!(context.getBehaviour() instanceof ExecuteProgramBehaviour)) {
                /// This is a waste when running the program.
                /// Only needed for {@link ca.teamdman.sfm.common.program.linting.GatherWarningsProgramBehaviour}.
                /// Will allow it for other non-execute behaviours just to avoid trouble.
                context.getProgram().astBuilder().setLocationFromOtherNode(newInputStatement, oldInputStatement);
            }
            if (context.getBehaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
                simulation.onInputStatementForgetTransform(context, oldInputStatement, newInputStatement);
            }
            // this could be a set instead of list contains check, but whatever. Should be small
            oldInputStatement.freeSlotsIf(slot -> labelToForget.contains(slot.label));
            oldInputStatement.transferSlotsTo(newInputStatement);

            if (newLabels.isEmpty()) {
                oldInputStatement.freeSlots();
            } else {
                newInputs.add(newInputStatement);
{% endif %}
            }
        }
{% if features.packet_computation %}
        context.replaceInputs(newInputs);
{% else %}
        context.getInputs().clear();
        context.getInputs().addAll(newInputs);
{% endif %}
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_FORGET_STATEMENT.get(
                labelToForget.stream().map(Objects::toString).collect(Collectors.joining(", "))
        )));
    }

    @Override
    public String toString() {
{% if features.packet_computation %}
        if (allInputs) {
            return "FORGET";
        }
{% else %}

{% endif %}
        return "FORGET " + labelToForget.stream().map(Objects::toString).collect(Collectors.joining(", "));
    }

}
