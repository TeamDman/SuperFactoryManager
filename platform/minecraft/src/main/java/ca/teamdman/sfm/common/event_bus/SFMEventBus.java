package ca.teamdman.sfm.common.event_bus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.UnknownNullability;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
@SuppressWarnings("removal")
public class SFMEventBus {
    public static final IEventBus GAME_BUS = NeoForge.EVENT_BUS;

    public static @UnknownNullability IEventBus MOD_BUS = null;
}
