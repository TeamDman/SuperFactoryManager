package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramInputForgetRequest;
import ca.teamdman.sfm.common.program.ProgramInputSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ForgetStatement(
        Set<Label> labelToForget,
        boolean allInputs
) implements Statement {
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

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_FORGET_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.forget",
            "FORGET %s"
    );

    @Override
    public void tick(ProgramContext context) {

        ProgramInputForgetRequest request = allInputs
                                            ? ProgramInputForgetRequest.all()
                                            : ProgramInputForgetRequest.labels(labelToForget);
        List<ProgramInputSource> newInputs = new ArrayList<>();
        for (ProgramInputSource inputSource : context.getInputs()) {
            ProgramInputSource retainedSource = inputSource.forget(context, request);
            if (retainedSource != null) {
                newInputs.add(retainedSource);
            }
        }
        context.replaceInputs(newInputs);
        context.getLogger().debug(x -> x.accept(LOG_PROGRAM_TICK_FORGET_STATEMENT.get(
                labelToForget.stream().map(Objects::toString).collect(Collectors.joining(", "))
        )));
    }

    @Override
    public String toString() {
        if (allInputs) {
            return "FORGET";
        }
        return "FORGET " + labelToForget.stream().map(Objects::toString).collect(Collectors.joining(", "));
    }

}
