package ca.teamdman.sfm.common.event_bus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.UnknownNullability;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
public class SFMEventBus {
    public static final IEventBus GAME_BUS = NeoForge.EVENT_BUS;

    public static @UnknownNullability IEventBus MOD_BUS = null;

    public static IEventBus getEventBus(Bus busType) {

        if (busType == EventBusType.MOD) {
            return MOD_BUS;
        } else if (busType == EventBusType.GAME) {
            return GAME_BUS;
        } else {
            throw new IllegalArgumentException("Invalid busType: " + busType);
        }
    }

    public static class EventBusType {

        public static final Bus MOD = Bus.MOD;

        public static final Bus GAME = Bus.FORGE;

    }

}
