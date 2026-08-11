package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProgramContext {
    private final Program PROGRAM;

    private final ManagerBlockEntity MANAGER;

    private final CableNetwork NETWORK;

    private final List<InputStatement> INPUTS = new ArrayList<>();

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
        LABEL_POSITIONS = LabelPositionHolder.from(Objects.requireNonNull(manager.getDisk()));
        LOGGER = manager.logger;
    }

    private ProgramContext(ProgramContext other) {

        PROGRAM = other.PROGRAM;
        MANAGER = other.MANAGER;
        NETWORK = other.NETWORK;
        LEVEL = other.LEVEL;
        REDSTONE_PULSES = other.REDSTONE_PULSES;
        BEHAVIOUR = other.BEHAVIOUR.fork();
        INPUTS.addAll(other.INPUTS);
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
     * Copy the context, used in branch investigation.
     * <p>
     * This does not fork input statement state.
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

        INPUTS.forEach(InputStatement::freeSlots);
    }


    public ManagerBlockEntity getManager() {

        return MANAGER;
    }

    public TranslatableLogger getLogger() {

        return LOGGER;
    }

    public void addInput(InputStatement input) {

        INPUTS.add(input);
    }

    public List<InputStatement> getInputs() {

        return INPUTS;
    }


    public CableNetwork getNetwork() {

        return NETWORK;
    }

    @Override
    public String toString() {

        return "ProgramContext{" +
               "PROGRAM=" + PROGRAM +
               ", MANAGER=" + MANAGER +
               ", NETWORK=" + NETWORK +
               ", INPUTS=" + INPUTS +
               ", LEVEL=" + LEVEL +
               ", EXECUTION_POLICY=" + BEHAVIOUR +
               ", REDSTONE_PULSES=" + REDSTONE_PULSES +
               ", LABEL_POSITIONS=" + LABEL_POSITIONS +
               ", did_something=" + did_something +
               '}';
    }

}
