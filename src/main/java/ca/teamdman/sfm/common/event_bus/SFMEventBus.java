package ca.teamdman.sfm.common.event_bus;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.jetbrains.annotations.UnknownNullability;

/// Used to reduce {@link ca.teamdman.sfm.common.util.MCVersionDependentBehaviour}.
public class SFMEventBus {
    public static final EventBus GAME_BUS = MinecraftForge.EVENT_BUS;

    public static @UnknownNullability EventBus MOD_BUS = null;

    public static EventBus getEventBus(EventBusType busType) {

        if (busType == EventBusType.MOD) {
            return MOD_BUS;
        } else if (busType == EventBusType.GAME) {
            return GAME_BUS;
        } else {
            throw new IllegalArgumentException("Invalid busType: " + busType);
        }
    }

    public enum EventBusType {
        MOD(),
        GAME();
    }

}
