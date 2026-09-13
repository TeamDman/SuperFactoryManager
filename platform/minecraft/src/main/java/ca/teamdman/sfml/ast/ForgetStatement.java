package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramInputSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ForgetStatement(
        Set<Label> labelToForget
) implements Statement {
    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_FORGET_STATEMENT = new LocalizationEntry(
            "log.sfm.statement.tick.forget",
            "FORGET %s"
    );

    @Override
    public void tick(ProgramContext context) {

        List<ProgramInputSource> newInputs = new ArrayList<>();
        for (ProgramInputSource inputSource : context.getInputs()) {
            ProgramInputSource retainedSource = inputSource.forget(context, labelToForget);
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

        return "FORGET " + labelToForget.stream().map(Objects::toString).collect(Collectors.joining(", "));
    }

}
