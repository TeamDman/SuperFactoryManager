package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
{% if features.packet_computation %}
{% else %}
import ca.teamdman.sfml.ast.InputStatement;
{% endif %}
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.level.Level;

{% if features.packet_computation %}
{% else %}
import java.util.ArrayList;
{% endif %}
import java.util.List;
import java.util.Objects;

public class ProgramContext {
    private final Program PROGRAM;

    private final ManagerBlockEntity MANAGER;

    private final CableNetwork NETWORK;

{% if features.packet_computation %}
    private final ProgramExecutionScope EXECUTION_SCOPE;
{% else %}
    private final List<InputStatement> INPUTS = new ArrayList<>();
{% endif %}

    private final Level LEVEL;

    private final ProgramBehaviour BEHAVIOUR;

    private final int REDSTONE_PULSES;

    private final LabelPositionHolder LABEL_POSITIONS;

    private final TranslatableLogger LOGGER;

    private boolean did_something = false;

    private ProgramContext(
            Program program,
            ManagerBlockEntity manager,
            CableNetwork network,
            Level level,
            int redstonePulses,
            ProgramBehaviour executionBehaviour,
            LabelPositionHolder labelPositions,
            TranslatableLogger logger
    ) {

        this.PROGRAM = program;
        this.MANAGER = manager;
        this.NETWORK = network;
        this.LEVEL = level;
        this.REDSTONE_PULSES = redstonePulses;
        this.BEHAVIOUR = executionBehaviour;
        this.LABEL_POSITIONS = labelPositions;
        this.LOGGER = logger;
{% if features.packet_computation %}
        this.EXECUTION_SCOPE = new ProgramExecutionScope();
{% else %}
{% endif %}
    }

    public ProgramContext(
            Program program,
            ManagerBlockEntity manager,
            ProgramBehaviour executionBehaviour
    ) {

        this.PROGRAM = program;
        this.MANAGER = manager;
        //noinspection OptionalGetWithoutIsPresent // program shouldn't be ticking if the network is bad
        NETWORK = CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(MANAGER)
                .get();
        assert MANAGER.getLevel() != null;
        LEVEL = MANAGER.getLevel();
        REDSTONE_PULSES = MANAGER.getUnprocessedRedstonePulseCount();
        BEHAVIOUR = executionBehaviour;
{% case minecraft_version %}
{% when "26.1.2" %}
        LABEL_POSITIONS = LabelPositionHolder.from(manager.getDisk());
{% else %}
        LABEL_POSITIONS = LabelPositionHolder.from(Objects.requireNonNull(manager.getDisk()));
{% endcase %}
        LOGGER = manager.logger;
{% if features.packet_computation %}
        EXECUTION_SCOPE = new ProgramExecutionScope();
{% else %}
{% endif %}
    }

    private ProgramContext(ProgramContext other) {

        PROGRAM = other.PROGRAM;
        MANAGER = other.MANAGER;
        NETWORK = other.NETWORK;
        LEVEL = other.LEVEL;
        REDSTONE_PULSES = other.REDSTONE_PULSES;
        BEHAVIOUR = other.BEHAVIOUR.fork();
{% if features.packet_computation %}
        EXECUTION_SCOPE = new ProgramExecutionScope();
{% else %}
        INPUTS.addAll(other.INPUTS);
{% endif %}
        did_something = other.did_something;
        LABEL_POSITIONS = other.LABEL_POSITIONS;
        LOGGER = other.LOGGER;
    }

    public Level getLevel() {

        return LEVEL;
    }

    public boolean didSomething() {

        return did_something;
    }

    public void setDidSomething(boolean value) {

        this.did_something = value;
    }

    public static ProgramContext createSimulationContext(
            Program program,
            LabelPositionHolder labelPositionHolder,
            int redstonePulses,
            SimulateExploreAllPathsProgramBehaviour behaviour
    ) {
        //noinspection DataFlowIssue // simulation mode must be able to run without world access
        return new ProgramContext(
                program,
                null,
                null,
                null,
                redstonePulses,
                behaviour,
                labelPositionHolder,
                new TranslatableLogger("simulated" + Objects.hash(program, labelPositionHolder, behaviour))
        );
    }

{% if features.packet_computation %}
    /** World-free execution context used by focused runtime ownership tests. */
    static ProgramContext createDetachedTestContext(
            Program program,
            ProgramBehaviour behaviour
    ) {
        return new ProgramContext(
                program,
                null,
                null,
                null,
                0,
                behaviour,
                LabelPositionHolder.empty(),
                new TranslatableLogger("detached-test-" + System.identityHashCode(behaviour))
        );
    }

{% else %}
{% endif %}
    public static ProgramContext createSimulationContext(
            Program program,
            ManagerBlockEntity manager,
            CableNetwork network,
            LabelPositionHolder labelPositionHolder,
            int redstonePulses,
            SimulateExploreAllPathsProgramBehaviour behaviour
    ) {
        //noinspection DataFlowIssue // simulation mode must be able to run without world access
        return new ProgramContext(
                program,
                manager,
                network,
                manager.getLevel(),
                redstonePulses,
                behaviour,
                labelPositionHolder,
                new TranslatableLogger("simulated" + Objects.hash(program, labelPositionHolder, behaviour))
        );
    }

    public LabelPositionHolder getLabelPositionHolder() {

        return LABEL_POSITIONS;
    }

    public ProgramBehaviour getBehaviour() {

        return BEHAVIOUR;
    }

    public Program getProgram() {

        return PROGRAM;
    }

    /**
{% if features.packet_computation %}
     * Create an isolated context for one trigger execution or simulated path.
{% else %}
     * Copy the context, used in branch investigation.
{% endif %}
     * <p>
{% if features.packet_computation %}
     * Runtime inputs, variables, and ephemeral resources are deliberately not
     * copied from the parent context.
{% else %}
     * This does not fork input statement state.
{% endif %}
     *
     * @return shallow copy of this context
     */
    public ProgramContext fork() {

        return new ProgramContext(this);
    }

    public int getRedstonePulses() {

        return REDSTONE_PULSES;
    }

    public void free() {

{% if features.packet_computation %}
        EXECUTION_SCOPE.free();
{% else %}
        INPUTS.forEach(InputStatement::freeSlots);
{% endif %}
    }


    public ManagerBlockEntity getManager() {

        return MANAGER;
    }

    public TranslatableLogger getLogger() {

        return LOGGER;
    }

{% if features.packet_computation %}
    public void addInput(ProgramInputSource input) {
{% else %}
    public void addInput(InputStatement input) {
{% endif %}

{% if features.packet_computation %}
        EXECUTION_SCOPE.addInput(input);
{% else %}
        INPUTS.add(input);
{% endif %}
    }

{% if features.packet_computation %}
    public List<ProgramInputSource> getInputs() {
{% else %}
    public List<InputStatement> getInputs() {
{% endif %}

{% if features.packet_computation %}
        return EXECUTION_SCOPE.activeInputs();
{% else %}
        return INPUTS;
{% endif %}
    }

{% if features.packet_computation %}
    public void replaceInputs(List<? extends ProgramInputSource> inputs) {
        EXECUTION_SCOPE.replaceInputs(inputs);
    }
{% else %}
{% endif %}

{% if features.packet_computation %}
    public ProgramVariableEnvironment getVariableEnvironment() {
        return EXECUTION_SCOPE.variables();
    }

    public ProgramEphemeralResourceOwner getEphemeralResourceOwner() {
        return EXECUTION_SCOPE.ephemeralResources();
    }

    public ProgramExecutionScope getExecutionScope() {
        return EXECUTION_SCOPE;
    }


{% else %}
{% endif %}
    public CableNetwork getNetwork() {

        return NETWORK;
    }

    @Override
    public String toString() {

        return "ProgramContext{" +
               "PROGRAM=" + PROGRAM +
               ", MANAGER=" + MANAGER +
               ", NETWORK=" + NETWORK +
{% if features.packet_computation %}
               ", INPUTS=" + EXECUTION_SCOPE.activeInputs() +
{% else %}
               ", INPUTS=" + INPUTS +
{% endif %}
               ", LEVEL=" + LEVEL +
               ", EXECUTION_POLICY=" + BEHAVIOUR +
               ", REDSTONE_PULSES=" + REDSTONE_PULSES +
               ", LABEL_POSITIONS=" + LABEL_POSITIONS +
               ", did_something=" + did_something +
               '}';
    }

}
