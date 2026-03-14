package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.program.ProgramBehaviour;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.SimulateExploreAllPathsProgramBehaviour;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_OUTPUT_RESOURCE_TYPE_NOT_FOUND_IN_INPUTS;
import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNUSED_INPUT_LABEL;

@SuppressWarnings("rawtypes")
public class GatherWarningsProgramBehaviour extends SimulateExploreAllPathsProgramBehaviour {
    private final List<Pair<ExecutionPath, List<Pair<ExecutionPathElement, TranslatableContents>>>> sharedMultiverseWarningsByPath;

    private final ProblemTracker tracker;

    private final List<Pair<ExecutionPathElement, TranslatableContents>> warnings = new ArrayList<>();

    private final Multimap<ResourceType, LabelExpression> resourceTypesInputted = HashMultimap.create();

    private final Set<ResourceType> resourceTypesOutputted = new HashSet<>();

    public GatherWarningsProgramBehaviour(ProblemTracker tracker) {

        this.tracker = tracker;
        this.sharedMultiverseWarningsByPath = new ArrayList<>();
    }

    public GatherWarningsProgramBehaviour(
            List<ExecutionPath> seenPaths,
            ExecutionPath currentPath,
            AtomicReference<BigInteger> triggerPathCount,
            ProblemTracker tracker,
            List<Pair<ExecutionPath, List<Pair<ExecutionPathElement, TranslatableContents>>>> sharedMultiverseWarningsByPath,
            List<Pair<ExecutionPathElement, TranslatableContents>> warnings
    ) {

        super(seenPaths, currentPath, triggerPathCount);
        this.warnings.addAll(warnings);
        this.tracker = tracker;
        this.sharedMultiverseWarningsByPath = sharedMultiverseWarningsByPath;
    }


    @Override
    public ProgramBehaviour fork() {

        return new GatherWarningsProgramBehaviour(
                this.seenPaths,
                this.currentPath,
                this.triggerPathCount,
                this.tracker,
                this.sharedMultiverseWarningsByPath,
                this.warnings
        );
    }

    @Override
    public void onInputStatementExecution(
            ProgramContext context,
            InputStatement inputStatement
    ) {

        super.onInputStatementExecution(context, inputStatement);
        Set<? extends ResourceType<?, ?, ?>> inputtingResourceTypes = inputStatement
                .getReferencedIOResourceIds()
                .map(ResourceIdentifier::getResourceType)
                .collect(Collectors.toSet());
        for (LabelExpression labelExpression : inputStatement.resourceAccess().labelExpressions()) {
            for (ResourceType resourceType : inputtingResourceTypes) {
                resourceTypesInputted.put(resourceType, labelExpression);
            }
        }
    }

    @Override
    public void onOutputStatementExecution(
            ProgramContext context,
            OutputStatement outputStatement
    ) {

        super.onOutputStatementExecution(context, outputStatement);


        // identify resource types being outputted
        Set<ResourceType> seekingResourceTypes = outputStatement
                .getReferencedIOResourceIds()
                .map(ResourceIdentifier::getResourceType)
                .collect(Collectors.toSet());

        for (ResourceType resourceType : seekingResourceTypes) {
            if (!resourceTypesInputted.containsKey(resourceType)) {
                // if the resource type was never inputted, warn

                warnings.add(Pair.of(
                        getLatestPathElement(),
                        PROGRAM_WARNING_OUTPUT_RESOURCE_TYPE_NOT_FOUND_IN_INPUTS.get(
                                outputStatement,
                                context.program().astBuilder().getLineColumnForNode(outputStatement),
                                resourceType.displayAsCode()
                        )
                ));
            }
        }


        // track what we have outputted, so we can find what we input and never use
        resourceTypesOutputted.addAll(seekingResourceTypes);
    }

    @Override
    public void onInputStatementForgetTransform(
            ProgramContext context,
            InputStatement old,
            InputStatement next
    ) {

        super.onInputStatementForgetTransform(context, old, next);

        /*
        INPUT stick FROM a,b
        FORGET a - (item::,a) going out of scope, warn a is never used
        OUTPUT TO chest
        */


        // Identify labels that are no longer active
        Set<LabelExpression> oldLabels = new HashSet<>(old.resourceAccess().labelExpressions());
        Set<LabelExpression> newLabels = new HashSet<>(next.resourceAccess().labelExpressions());
        Set<LabelExpression> removedLabelExpressions = new HashSet<>(oldLabels);
        removedLabelExpressions.removeAll(newLabels);

        // Identify resource types being dropped
        Set<? extends ResourceType<?, ?, ?>> droppingResourceTypes = old
                .getReferencedIOResourceIds()
                .map(ResourceIdentifier::getResourceType)
                .collect(Collectors.toSet());

        warnUnusedInputLabelExpressions(context, old, removedLabelExpressions, droppingResourceTypes);
    }

    @Override
    public void terminatePathAndBeginAnew() {
        // save the path and its warnings
        sharedMultiverseWarningsByPath.add(Pair.of(currentPath, new ArrayList<>(warnings)));

        // default path push and clear
        super.terminatePathAndBeginAnew();

        // clear warnings to start fresh on this new path
        warnings.clear();
    }

    @Override
    public void onInputStatementDropped(
            ProgramContext context,
            InputStatement inputStatement
    ) {

        super.onInputStatementDropped(context, inputStatement);

        /*
        INPUT stick FROM a
        -- input never used
        */

        // identify resource types being dropped
        Set<? extends ResourceType<?, ?, ?>> resourceTypesBeingDropped = inputStatement
                .getReferencedIOResourceIds()
                .map(ResourceIdentifier::getResourceType)
                .collect(Collectors.toSet());

        // identify the expressions being dropped
        Set<LabelExpression> labelExpressionsToBeDropped = new HashSet<>(inputStatement.resourceAccess().labelExpressions());

        warnUnusedInputLabelExpressions(
                context,
                inputStatement,
                labelExpressionsToBeDropped,
                resourceTypesBeingDropped
        );
    }

    @Override
    public void onProgramFinished(
            ProgramContext context,
            SFMLProgram program
    ) {

        super.onProgramFinished(context, program);
        // we need to calculate what warnings were present in ALL paths

        // for each warning in each path
        // ensure it has occurred in all other paths

        Set<Pair<ExecutionPathElement, TranslatableContents>> toWarn = new HashSet<>();

        // first pass - add all warnings
        for (var path : sharedMultiverseWarningsByPath) {
            toWarn.addAll(path.getSecond());
        }

        // second pass - remove warnings where a branch exists where the statement is present but the warning is not
        var iterator = toWarn.iterator();
        while (iterator.hasNext()) {
            var seekPair = iterator.next();
            var seekStatement = seekPair.getFirst();
            var seekWarning = seekPair.getSecond();
            for (var histPair : sharedMultiverseWarningsByPath) {
                // we only want to return warnings where 'all' paths have the warning
                // not all paths execute the statement and add it to history
                // only paths that execute the statement but do not generate the warning should remove the warning
                boolean pathContainsStatement = histPair
                        .getFirst()
                        .stream()
                        .anyMatch(element -> element.equals(seekStatement));
                boolean pathContainsWarning = histPair
                        .getSecond()
                        .stream()
                        .anyMatch(pair -> pair.getSecond().equals(seekWarning));
                if (pathContainsStatement && !pathContainsWarning) {
                    iterator.remove();
                    break;
                }
            }
        }

        // return deduplicated warnings
        for (
                TranslatableContents warning : toWarn
                .stream()
                .map(Pair::getSecond)
                .collect(Collectors.toSet())
        ) {
            if (tracker.add(warning).isSaturated()) {
                break;
            }
        }
    }

    private void warnUnusedInputLabelExpressions(
            ProgramContext context,
            InputStatement old,
            Set<LabelExpression> removedLabelExpressions,
            Set<? extends ResourceType<?, ?, ?>> droppingResourceTypes
    ) {

        for (LabelExpression labelExpression : removedLabelExpressions) {
            for (ResourceType resourceType : droppingResourceTypes) {
                // if the label was never used, warn
                if (!resourceTypesOutputted.contains(resourceType)) {
                    ExecutionPathElement offendingNode = getPathElementForNode(old);
                    if (offendingNode == null) {
                        SFM.LOGGER.warn("Failed to find node for element during warning generation: {}", old);
                    }

                    warnings.add(Pair.of(
                            offendingNode,
                            PROGRAM_WARNING_UNUSED_INPUT_LABEL.get(
                                    old,
                                    context.program().astBuilder().getLineColumnForNode(old),
                                    resourceType.displayAsCode(),
                                    labelExpression,
                                    resourceType.displayAsCode()
                            )
                    ));
                }
                // mark as no longer active
                resourceTypesInputted.remove(resourceType, labelExpression);
            }
        }
    }

}

/*
Consider the following smelly program

NAME "a smelly program"

EVERY 20 TICKS DO
    INPUT fluid:: FROM tank1
    OUTPUT TO tank2
    -- missing fluid output

    FORGET

    INPUT FROM tank1
    OUTPUT fluid:: TO tank2
    -- missing fluid input

    FORGET

    INPUT FROM chest
    INPUT fluid:: FROM tank1
    OUTPUT TO chest
    -- missing fluid output

    FORGET

    IF chest HAS > 0 stone THEN
        INPUT FROM chest
        INPUT fluid:: FROM tank
    END
    OUTPUT TO chest
    -- missing fluid input
END
EVERY 20 TICKS DO
    OUTPUT TO chest
    -- missing input

    FORGET

    INPUT FROM chest
    -- missing output
END
EVERY 20 TICKS DO
    INPUT 5 FROM a,b,c
    OUTPUT 1 to z1
    FORGET b,c
    OUTPUT TO z2
    -- no output uses input from b,c
END


We must check for the following:
- INPUT without corresponding OUTPUT for all resource types
- OUTPUT without corresponding INPUT for all resource types

We should assume all if-statement blocks and else blocks are valid as we cannot know.

We should ensure each trigger is considered separately.

We should ensure FORGET statements are respected.

 */
