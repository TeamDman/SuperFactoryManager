package ca.teamdman.sfm.common.event_bus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.UnknownNullability;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
@SuppressWarnings("removal")
public class SFMEventBus {
    public static final IEventBus GAME_BUS = NeoForge.EVENT_BUS;

    public static @UnknownNullability IEventBus MOD_BUS = null;

    public static IEventBus getEventBus(EventBusSubscriber.Bus busType) {

        if (busType == EventBusType.MOD) {
            return MOD_BUS;
        } else if (busType == EventBusType.GAME) {
            return GAME_BUS;
        } else {
            throw new IllegalArgumentException("Invalid busType: " + busType);
        }
    }

    public static class EventBusType {

        public static final EventBusSubscriber.Bus MOD = EventBusSubscriber.Bus.MOD;

        public static final EventBusSubscriber.Bus GAME = EventBusSubscriber.Bus.GAME;

    }

}
