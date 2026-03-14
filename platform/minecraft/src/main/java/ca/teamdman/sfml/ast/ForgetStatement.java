package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.LOG_PROGRAM_TICK_FORGET_STATEMENT;

/// @param labelExpressionsToForget if empty, forgets all expressions instead of none.
public record ForgetStatement(
        Set<LabelExpression> labelExpressionsToForget
) implements Tickable {
    @Override
    public void tick(ProgramContext context) {
        // Create temporary holder for new input statements
        List<InputStatement> newInputs = new ArrayList<>();

        // Create the predicate choosing which label expressions are removed
        Predicate<LabelExpression> shouldRemoveLabelExpression;
        if (this.labelExpressionsToForget.isEmpty()) {
            shouldRemoveLabelExpression = labelExpression -> true;
        } else {
            shouldRemoveLabelExpression = this.labelExpressionsToForget::contains;
        }

        // Create the predicate choosing which label expressions are retained
        Predicate<LabelExpression> shouldKeepLabelExpression = shouldRemoveLabelExpression.negate();

        // Mutate each input statement to remove the label expressions we want to forget
        for (InputStatement oldInputStatement : context.inputs()) {
            // Identify the expressions to retain
            var labelExpressionsToKeep = oldInputStatement
                    .resourceAccess()
                    .labelExpressions()
                    .stream()
                    .filter(shouldKeepLabelExpression)
                    .toList();

            // Create a new input statement using the new label expressions
            InputStatement newInputStatement = new InputStatement(
                    new ResourceAccess(
                            labelExpressionsToKeep,
                            oldInputStatement.resourceAccess().roundRobin(), oldInputStatement.resourceAccess().sides(),
                            oldInputStatement.resourceAccess().slots()
                    ),
                    oldInputStatement.resourceLimits(),
                    oldInputStatement.each()
            );

            // Update the line and column information if we aren't in execute mode
            if (!(context.behaviour() instanceof ExecuteProgramBehaviour)) {
                /// This is a waste when running the program.
                /// Only needed for {@link ca.teamdman.sfm.common.program.linting.GatherWarningsProgramBehaviour}.
                /// Will allow it for other non-execute behaviours just to avoid trouble.

                context.program().astBuilder().setLocationFromOtherNode(newInputStatement, oldInputStatement);
            }

            // Always fire change event when simulating, even if the new statement has no label expressions
            if (context.behaviour() instanceof SimulateExploreAllPathsProgramBehaviour simulation) {
                simulation.onInputStatementForgetTransform(context, oldInputStatement, newInputStatement);
            }

            // Free slots that come from label expressions that we are forgetting
            oldInputStatement.freeSlotsIf(slot -> labelExpressionsToForget.contains(slot.labelExpression));

            // Transfer ownership of remaining slots to the new input statement to ensure they are freed exactly once
            oldInputStatement.transferSlotsTo(newInputStatement);

            if (labelExpressionsToKeep.isEmpty()) {
                // Free slots and drop the statement if the statement is now empty
                oldInputStatement.freeSlots();
            } else {
                // Track the new input statement
                newInputs.add(newInputStatement);
            }
        }

        // Remove all old input statements
        context.inputs().clear();

        // Track the new input statements
        context.inputs().addAll(newInputs);

        // Log the forget statement
        context.logger().debug(x -> x.accept(LOG_PROGRAM_TICK_FORGET_STATEMENT.get(
                labelExpressionsToForget.stream().map(Objects::toString).collect(Collectors.joining(", "))
        )));
    }

    @Override
    public String toString() {
        return "FORGET " + labelExpressionsToForget.stream().map(Objects::toString).collect(Collectors.joining(", "));
    }

    @Override
    public List<SfmlAstNode> getChildNodes() {

        return List.of();
    }

}
