package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.logging.TranslatableLogger;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// A common group of objects used in the execution of a {@link SFMLProgram}.
///
/// The {@link #inputs} list must be mutable; caution against using {@link Stream#toList()} which uses {@link java.util.Collections#unmodifiableList(List)}.
public record ProgramContext(
        TranslatableLogger logger,

        SFMLProgram program,

        LabelPositionHolder labelPositionHolder,

        @Nullable ManagerBlockEntity manager,

        @Nullable CableNetwork network,

        @Nullable Level level,

        List<InputStatement> inputs,

        ProgramBehaviour behaviour,

        MutableInt unhandledRedstonePulseCount,

        MutableBoolean didSomething

) {
    private ProgramContext(ProgramContext other) {

        this(
                other.logger, other.program,
                other.labelPositionHolder, other.manager,
                other.network,
                other.level, new ArrayList<>(other.inputs),
                other.behaviour,
                new MutableInt(other.unhandledRedstonePulseCount().intValue()),
                new MutableBoolean(other.didSomething.booleanValue())
        );
    }

    public static ProgramContext of(
            SFMLProgram program,
            ManagerBlockEntity manager,
            ProgramBehaviour executionBehaviour
    ) {

        //noinspection OptionalGetWithoutIsPresent // program shouldn't be ticking if the network is bad
        CableNetwork network = CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .get();
        Level level = manager.getLevel();
        assert level != null;

        int unhandledRedstonePulseCount = manager.getUnprocessedRedstonePulseCount();
        LabelPositionHolder labelPositionHolder = LabelPositionHolder.from(Objects.requireNonNull(manager.getDisk()));
        TranslatableLogger logger = manager.logger;
        return new ProgramContext(
                logger, program,
                labelPositionHolder, manager,
                network,
                level,
                new ArrayList<>(),
                executionBehaviour,
                new MutableInt(unhandledRedstonePulseCount),
                new MutableBoolean(false)
        );
    }

    public static ProgramContext createSimulationContext(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            int redstonePulses,
            SimulateExploreAllPathsProgramBehaviour behaviour
    ) {
        return new ProgramContext(
                new TranslatableLogger("simulated@" + Objects.hash(program, labelPositionHolder, behaviour)),
                program,
                labelPositionHolder,
                null,
                null,
                null,
                new ArrayList<>(),
                behaviour,
                new MutableInt(redstonePulses),
                new MutableBoolean(false)
        );
    }

    /// Copy this context, this is used in branch investigation.
    /// This MUST NOT be used when {@link #inputs} is not empty.
    public ProgramContext fork() {

        ProgramContext programContext = new ProgramContext(this);
        if (!programContext.inputs().isEmpty()) {
            SFM.LOGGER.error("Forking program context with non-empty inputs list has occurred, this will result in a double-free causing desynchronizing in the SFM object pool!");
            if (SFMEnvironmentUtils.isInIDE()) {
                throw new IllegalStateException("Forking program context with non-empty inputs list");
            }
        }
        return programContext;
    }

    public void free() {

        inputs.forEach(InputStatement::freeSlots);
    }


    public void addInput(InputStatement input) {

        inputs.add(input);
    }

}
