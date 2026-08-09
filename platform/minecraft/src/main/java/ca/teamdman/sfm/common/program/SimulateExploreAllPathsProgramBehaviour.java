package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.*;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class SimulateExploreAllPathsProgramBehaviour implements ProgramBehaviour {
    protected List<ExecutionPath> seenPaths = new ArrayList<>();
    protected ExecutionPath currentPath = new ExecutionPath();
    protected AtomicReference<BigInteger> triggerPathCount = new AtomicReference<>(BigInteger.ZERO);

    public SimulateExploreAllPathsProgramBehaviour() {
    }

    public SimulateExploreAllPathsProgramBehaviour(
            List<ExecutionPath> seenPaths,
            ExecutionPath currentPath,
            AtomicReference<BigInteger> triggerPathCount
    ) {
        this.seenPaths = seenPaths;
        this.currentPath = currentPath.fork();
        this.triggerPathCount = triggerPathCount;
    }

    public void terminatePathAndBeginAnew() {
        seenPaths.add(currentPath);
        currentPath = new ExecutionPath();
        triggerPathCount.set(triggerPathCount.get().add(BigInteger.ONE));
    }

    public BigInteger getTriggerPathCount() {
        return triggerPathCount.get();
    }

    public void prepareNextTrigger() {
        triggerPathCount.set(BigInteger.ZERO);
    }

    public void pushPathElement(ExecutionPathElement statement) {
        currentPath.history.add(statement);
    }

    public @Nullable ExecutionPathElement getLatestPathElement() {
        if (currentPath.history.isEmpty()) {
            return null;
        }
        return currentPath.history.get(currentPath.history.size() - 1);
    }

    public @Nullable ExecutionPathElement getPathElementForNode(ASTNode node) {
        var iterator = currentPath.history.listIterator(currentPath.history.size());
        while (iterator.hasPrevious()) {
            var element = iterator.previous();
            if (element instanceof Branch branch && branch.ifStatement == node) {
                return element;
            } else if (element instanceof IO io && io.statement == node) {
                return element;
            }
        }
        return null;
    }

    public void onOutputStatementExecution(
            ProgramContext context,
            OutputStatement outputStatement
    ) {
        pushPathElement(new SimulateExploreAllPathsProgramBehaviour.IO(outputStatement));
    }

    public void onInputStatementExecution(
            ProgramContext context,
            InputStatement inputStatement
    ) {
        pushPathElement(new SimulateExploreAllPathsProgramBehaviour.IO(inputStatement));
    }

    public void onInputStatementForgetTransform(
            ProgramContext context,
            InputStatement old,
            InputStatement next
    ) {
    }

    public void onInputStatementDropped(
            ProgramContext context,
            InputStatement inputStatement
    ) {
    }


    public void onTriggerDropped(
            ProgramContext context,
            @SuppressWarnings("unused") Trigger trigger
    ) {
        context.getInputs().forEach(inputStatement -> onInputStatementDropped(context, inputStatement));
    }

    @Override
    public ProgramBehaviour fork() {
        return new SimulateExploreAllPathsProgramBehaviour(this.seenPaths, this.currentPath, this.triggerPathCount);
    }

    public ExecutionPath getCurrentPath() {
        return currentPath;
    }

    public List<ExecutionPath> getSeenPaths() {
        return seenPaths;
    }

    public int[] getSeenIOStatementCountForEachPath() {
        return seenPaths
                .stream()
                .mapToInt(path -> (int) path.history.stream().filter(IO.class::isInstance).count())
                .toArray();
    }

    public void onProgramFinished(
            ProgramContext context,
            Program program
    ) {

    }


    public enum IOKind {
        INPUT, OUTPUT
    }

    public interface ExecutionPathElement {
    }

    public record ExecutionPath(
            List<ExecutionPathElement> history
    ) {
        public ExecutionPath() {
            this(new ArrayList<>());
        }

        public ExecutionPath fork() {
            return new ExecutionPath(new ArrayList<>(history));
        }

        public Stream<ExecutionPathElement> stream() {
            return history.stream();
        }

        public Stream<Branch> streamBranches() {
            return history.stream().filter(Branch.class::isInstance).map(Branch.class::cast);
        }
    }

    public record Branch(
            IfStatement ifStatement,
            boolean wasTrue
    ) implements ExecutionPathElement {
    }

    public record IO(
            IOStatement statement,
            IOKind kind,
            ResourceType<?,?,?>[] usedResourceTypes,
            Set<Label> usedLabels
    ) implements ExecutionPathElement {
        public IO(IOStatement statement) {
            //noinspection DataFlowIssue
            this(
                    statement,
                    statement instanceof InputStatement
                    ? IOKind.INPUT
                    : (statement instanceof OutputStatement ? IOKind.OUTPUT : null),
                    statement.resourceLimits().getReferencedResourceTypes(),
                    new HashSet<>(statement.labelAccess().labels())
            );
            if (kind == null) {
                throw new IllegalArgumentException("Unknown IO statement type: " + statement);
            }
        }
    }
}
